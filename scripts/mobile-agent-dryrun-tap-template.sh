#!/usr/bin/env bash
set -euo pipefail

# Template flow:
#   1. Start MobileAgent dryrun. If --message is provided, it will fill the
#      input, wait 2s, simulate the send UI, wait 2s, collapse the container,
#      then start spinner/glow.
#   2. Run your custom adb input actions in run_middle_actions().
#   3. Stop spinner/glow.
#
# Supported passthrough args:
#   --serial <device_id>
#   --package <package_name>
#   --message <text>
#   --message-base64 <base64_text>
ARGS=("$@")

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
      --message|--message-base64)
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

run_middle_actions() {
  # Fill your adb actions here. Keep sleep_between_steps between actions.
  #
  # Examples:
  # shellcheck disable=SC2086
  # "$ADB_BIN" $SERIAL_ARGS shell input tap 540 1600
  # sleep_between_steps
  # "$SCRIPT_DIR/mobile-agent-clipboard-paste-dryrun.sh" "${ARGS[@]}" --text "明天休假"
  # sleep_between_steps
  :
}

"$SCRIPT_DIR/mobile-agent-spinner-start-dryrun.sh" "${ARGS[@]}"
sleep_between_steps

run_middle_actions
sleep_between_steps

"$SCRIPT_DIR/mobile-agent-spinner-stop-dryrun.sh" "${ARGS[@]}"
