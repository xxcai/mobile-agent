#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/sync-session-debug-transcripts.sh [count] [--dest <local_dir>] [--package <package_name>]

说明:
  - 默认同步设备上最新 1 份 session debug transcript
  - 可传 count 指定同步最新 N 份
  - 默认包名: com.hh.agent
  - 默认目标目录: ./build/session-debug-transcripts

示例:
  ./scripts/sync-session-debug-transcripts.sh
  ./scripts/sync-session-debug-transcripts.sh 3
  ./scripts/sync-session-debug-transcripts.sh 5 --dest ./tmp/transcripts
  ./scripts/sync-session-debug-transcripts.sh --package com.hh.agent.debug
EOF
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "缺少命令: $1" >&2
    exit 1
  fi
}

split_tool_events() {
  local transcript_dir="$1"
  local events_file="${transcript_dir}/events.jsonl"
  local output_dir="${transcript_dir}/tool-events"

  if [[ ! -f "$events_file" ]]; then
    return 0
  fi

  if ! command -v python3 >/dev/null 2>&1; then
    echo "跳过 tool 事件拆分，缺少命令: python3" >&2
    return 0
  fi

  rm -rf "$output_dir"
  mkdir -p "$output_dir"

  python3 - "$events_file" "$output_dir" <<'PY'
import json
import os
import re
import sys

events_file = sys.argv[1]
output_dir = sys.argv[2]

counter = 0

def slugify(value: str) -> str:
    value = (value or "").strip()
    value = re.sub(r"\s+", "_", value)
    value = re.sub(r"[^0-9A-Za-z_\-\u4e00-\u9fff]+", "_", value)
    value = re.sub(r"_+", "_", value).strip("_")
    return value or "unknown"

with open(events_file, "r", encoding="utf-8") as handle:
    for raw_line in handle:
        line = raw_line.strip()
        if not line:
            continue
        try:
            event = json.loads(line)
        except Exception:
            continue
        event_type = event.get("type")
        if event_type not in ("tool_use", "tool_result"):
            continue

        counter += 1
        payload = event.get("payload") or {}
        tool_name = payload.get("name") or payload.get("id") or "tool"
        filename = f"{counter:03d}_{event_type}_{slugify(str(tool_name))}.json"
        path = os.path.join(output_dir, filename)
        with open(path, "w", encoding="utf-8") as out:
            json.dump(event, out, ensure_ascii=False, indent=2)
PY
}

is_positive_int() {
  [[ "$1" =~ ^[1-9][0-9]*$ ]]
}

COUNT="1"
PACKAGE_NAME="com.hh.agent"
DEST_DIR=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --dest)
      DEST_DIR="${2:-}"
      shift 2
      ;;
    --package)
      PACKAGE_NAME="${2:-}"
      shift 2
      ;;
    *)
      if is_positive_int "$1"; then
        COUNT="$1"
        shift
      else
        echo "未知参数: $1" >&2
        usage >&2
        exit 1
      fi
      ;;
  esac
done

require_cmd adb

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DEST_DIR="${DEST_DIR:-$ROOT_DIR/build/session-debug-transcripts}"
DEVICE_ROOT="/sdcard/Android/data/${PACKAGE_NAME}/files/.icraw/debug-transcripts"

mkdir -p "$DEST_DIR"

ALL_DIRS=()
while IFS= read -r line; do
  ALL_DIRS+=("$line")
done < <(
  adb shell "if [ -d '$DEVICE_ROOT' ]; then ls -1 '$DEVICE_ROOT'; fi" \
    | tr -d '\r' \
    | sed '/^$/d' \
    | sort -r
)

if [[ "${#ALL_DIRS[@]}" -eq 0 ]]; then
  echo "设备上未找到 transcript 目录: $DEVICE_ROOT" >&2
  exit 1
fi

if (( COUNT > ${#ALL_DIRS[@]} )); then
  COUNT="${#ALL_DIRS[@]}"
fi

echo "设备 transcript 根目录: $DEVICE_ROOT"
echo "本地目标目录: $DEST_DIR"
echo "同步份数: $COUNT"

for ((i = 0; i < COUNT; i++)); do
  dir_name="${ALL_DIRS[$i]}"
  src="${DEVICE_ROOT}/${dir_name}"
  dest="${DEST_DIR}/${dir_name}"

  rm -rf "$dest"
  echo "同步: $dir_name"
  adb pull "$src" "$dest" >/dev/null
  split_tool_events "$dest"
done

echo "完成。已同步 ${COUNT} 份 transcript 到 ${DEST_DIR}"
