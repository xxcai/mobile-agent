#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.hh.agent"
SERIAL=""
ACTION="com.hh.agent.DEBUG_AGENT_SPINNER_START"
MESSAGE=""
MESSAGE_BASE64=""

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

while [[ $# -gt 0 ]]; do
  case "$1" in
    --package)
      PACKAGE="$2"
      shift 2
      ;;
    --serial)
      SERIAL="$2"
      shift 2
      ;;
    --message)
      MESSAGE="$2"
      shift 2
      ;;
    --message-base64)
      MESSAGE_BASE64="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -n "$MESSAGE" && -n "$MESSAGE_BASE64" ]]; then
  echo "Use only one of --message or --message-base64." >&2
  exit 2
fi

ADB_BIN="$(resolve_adb)"
ADB_CMD=("$ADB_BIN")
if [[ -n "$SERIAL" ]]; then
  ADB_CMD+=( -s "$SERIAL" )
fi

if [[ -n "$MESSAGE_BASE64" ]]; then
  "${ADB_CMD[@]}" shell am broadcast -a "$ACTION" -p "$PACKAGE" --es text_base64 "$MESSAGE_BASE64"
elif [[ -n "$MESSAGE" ]]; then
  "${ADB_CMD[@]}" shell am broadcast -a "$ACTION" -p "$PACKAGE" --es text "$MESSAGE"
else
  "${ADB_CMD[@]}" shell am broadcast -a "$ACTION" -p "$PACKAGE"
fi
