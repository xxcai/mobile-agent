#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/sync-to-worktree.sh [--config <config_path>] [--target <worktree_path>]

说明:
  - 默认配置文件: scripts/worktree-copy-files.conf
  - 配置文件按行列出要复制的文件或目录，路径相对于仓库根目录
  - 空行和以 # 开头的行会被忽略
  - 未指定 --target 时，自动选择最近创建的 worktree 目录

示例:
  ./scripts/sync-to-worktree.sh
  ./scripts/sync-to-worktree.sh --target ../mobile-agent-worktree
  ./scripts/sync-to-worktree.sh --config ./scripts/worktree-copy-files.conf --target ../mobile-agent-worktree
EOF
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "缺少命令: $1" >&2
    exit 1
  fi
}

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

stat_birth_or_mtime() {
  local path="$1"

  if stat -f '%B' "$path" >/dev/null 2>&1; then
    local birth
    birth="$(stat -f '%B' "$path")"
    if [[ "$birth" != "0" ]]; then
      printf '%s' "$birth"
      return 0
    fi
    stat -f '%m' "$path"
    return 0
  fi

  if stat -c '%W' "$path" >/dev/null 2>&1; then
    local birth
    birth="$(stat -c '%W' "$path")"
    if [[ "$birth" != "-1" && "$birth" != "0" ]]; then
      printf '%s' "$birth"
      return 0
    fi
    stat -c '%Y' "$path"
    return 0
  fi

  echo "无法获取目录时间戳: $path" >&2
  exit 1
}

resolve_latest_worktree() {
  local current_root="$1"
  local best_path=""
  local best_ts="-1"
  local candidate=""

  while IFS= read -r line; do
    if [[ "$line" == worktree\ * ]]; then
      candidate="${line#worktree }"
      if [[ "$candidate" == "$current_root" ]]; then
        continue
      fi

      local ts
      ts="$(stat_birth_or_mtime "$candidate")"
      if (( ts > best_ts )); then
        best_ts="$ts"
        best_path="$candidate"
      fi
    fi
  done < <(git worktree list --porcelain)

  if [[ -z "$best_path" ]]; then
    echo "未找到可用的目标 worktree。请通过 --target 显式指定。" >&2
    exit 1
  fi

  printf '%s\n' "$best_path"
}

TARGET_PATH=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --config)
      CONFIG_PATH="${2:-}"
      shift 2
      ;;
    --target)
      TARGET_PATH="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "未知参数: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

require_cmd git
require_cmd rsync

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIG_PATH="${CONFIG_PATH:-$SCRIPT_DIR/worktree-copy-files.conf}"

cd "$ROOT_DIR"

if ! git rev-parse --show-toplevel >/dev/null 2>&1; then
  echo "当前目录不是 git 仓库。" >&2
  exit 1
fi

ROOT_DIR="$(git rev-parse --show-toplevel)"

if [[ "$CONFIG_PATH" != /* ]]; then
  CONFIG_PATH="$ROOT_DIR/$CONFIG_PATH"
fi

if [[ ! -f "$CONFIG_PATH" ]]; then
  echo "配置文件不存在: $CONFIG_PATH" >&2
  exit 1
fi

if [[ -z "$TARGET_PATH" ]]; then
  TARGET_PATH="$(resolve_latest_worktree "$ROOT_DIR")"
else
  if [[ "$TARGET_PATH" != /* ]]; then
    TARGET_PATH="$ROOT_DIR/$TARGET_PATH"
  fi
fi

if [[ ! -d "$TARGET_PATH" ]]; then
  echo "目标目录不存在: $TARGET_PATH" >&2
  exit 1
fi

echo "仓库根目录: $ROOT_DIR"
echo "配置文件: $CONFIG_PATH"
echo "目标 worktree: $TARGET_PATH"

copied_count=0

while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
  line="$(trim "$raw_line")"

  if [[ -z "$line" || "${line:0:1}" == "#" ]]; then
    continue
  fi

  src="$ROOT_DIR/$line"
  dest_parent="$TARGET_PATH/$(dirname "$line")"

  if [[ ! -e "$src" ]]; then
    echo "跳过，不存在: $line" >&2
    continue
  fi

  mkdir -p "$dest_parent"

  if [[ -d "$src" ]]; then
    rsync -a "$src/" "$TARGET_PATH/$line/"
  else
    rsync -a "$src" "$dest_parent/"
  fi

  echo "已复制: $line"
  copied_count=$((copied_count + 1))
done < "$CONFIG_PATH"

echo "完成，共复制 ${copied_count} 项。"
