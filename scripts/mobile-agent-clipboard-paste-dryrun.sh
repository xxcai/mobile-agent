#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.hh.agent"
SERIAL=""
TEXT=""
TEXT_BASE64=""
TAP_X=""
TAP_Y=""
ACTION="com.hh.agent.DEBUG_SET_CLIPBOARD"

usage() {
  cat >&2 <<'EOF'
Usage:
  scripts/mobile-agent-clipboard-paste-dryrun.sh --text "明天休假" [--tap X Y] [--serial DEVICE] [--package PACKAGE]
  scripts/mobile-agent-clipboard-paste-dryrun.sh --text-base64 BASE64 [--tap X Y] [--serial DEVICE] [--package PACKAGE]

Flow:
  1. Optionally tap the target input field.
  2. Set clipboard through MobileAgent debug receiver.
  3. Send adb paste keyevent.
EOF
}

resolve_adb() {
  if [[ -n "${ADB:-}" ]]; then
    echo "$ADB"
    return
  fi
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return
  fi
  if [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]]; then
    echo "$ANDROID_HOME/platform-tools/adb"
    return
  fi
  if [[ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]]; then
    echo "$HOME/Library/Android/sdk/platform-tools/adb"
    return
  fi
  echo "adb not found. Set ADB=/path/to/adb or add adb to PATH." >&2
  exit 127
}

sleep_between_steps() {
  sleep "$((2 + RANDOM % 3))"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --text)
      TEXT="$2"
      shift 2
      ;;
    --text-base64)
      TEXT_BASE64="$2"
      shift 2
      ;;
    --tap)
      TAP_X="$2"
      TAP_Y="$3"
      shift 3
      ;;
    --package)
      PACKAGE="$2"
      shift 2
      ;;
    --serial)
      SERIAL="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 2
      ;;
  esac
done

if [[ -z "$TEXT" && -z "$TEXT_BASE64" ]]; then
  echo "Missing required --text or --text-base64." >&2
  usage
  exit 2
fi
if [[ -n "$TEXT" && -n "$TEXT_BASE64" ]]; then
  echo "Use only one of --text or --text-base64." >&2
  usage
  exit 2
fi
if [[ -n "$TAP_X" && -z "$TAP_Y" ]]; then
  echo "--tap requires X and Y." >&2
  exit 2
fi

ADB_BIN="$(resolve_adb)"
ADB_CMD=("$ADB_BIN")
if [[ -n "$SERIAL" ]]; then
  ADB_CMD+=( -s "$SERIAL" )
fi

if [[ -n "$TAP_X" ]]; then
  "${ADB_CMD[@]}" shell input tap "$TAP_X" "$TAP_Y"
  sleep_between_steps
fi

if [[ -n "$TEXT_BASE64" ]]; then
  "${ADB_CMD[@]}" shell am broadcast -a "$ACTION" -p "$PACKAGE" --es text_base64 "$TEXT_BASE64"
else
  "${ADB_CMD[@]}" shell am broadcast -a "$ACTION" -p "$PACKAGE" --es text "$TEXT"
fi
sleep_between_steps

"${ADB_CMD[@]}" shell input keyevent 279
