#!/usr/bin/env bash
set -euo pipefail

# Fill these coordinates before running.
TAP_X=""
TAP_Y=""

# Optional:
#   --serial <device_id>
#   --package <package_name>
ARGS=("$@")

if [[ -z "$TAP_X" || -z "$TAP_Y" ]]; then
  echo "Please fill TAP_X and TAP_Y in this script before running." >&2
  exit 2
fi

sleep_between_steps() {
  sleep "$((2 + RANDOM % 3))"
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

extract_serial_args() {
  local serial=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --serial)
        serial="$2"
        shift 2
        ;;
      --package)
        shift 2
        ;;
      *)
        echo "Unknown argument: $1" >&2
        exit 2
        ;;
    esac
  done
  if [[ -n "$serial" ]]; then
    echo "-s $serial"
  fi
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ADB_BIN="$(resolve_adb)"
SERIAL_ARGS="$(extract_serial_args "${ARGS[@]}")"

"$SCRIPT_DIR/mobile-agent-spinner-start-dryrun.sh" "${ARGS[@]}"
sleep_between_steps

# Fill TAP_X/TAP_Y above. Example:
#   TAP_X="540"
#   TAP_Y="1600"
# shellcheck disable=SC2086
"$ADB_BIN" $SERIAL_ARGS shell input tap "$TAP_X" "$TAP_Y"
sleep_between_steps

"$SCRIPT_DIR/mobile-agent-spinner-stop-dryrun.sh" "${ARGS[@]}"
