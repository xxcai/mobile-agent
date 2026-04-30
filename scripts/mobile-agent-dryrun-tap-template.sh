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
COMMON_SCRIPT_ARGS=()
SERIAL_ARGS=""

sleep_between_steps() {
  local seconds="${1:-}"
  if [[ -z "$seconds" ]]; then
    seconds="$((4 + RANDOM % 3))"
  fi
  sleep "$seconds"
}

log_step() {
  printf '[mobile-agent-dryrun] %s\n' "$*"
}

adb_tap() {
  local x="$1"
  local y="$2"
  log_step "tap x=$x y=$y"
  # shellcheck disable=SC2086
  "$ADB_BIN" $SERIAL_ARGS shell input tap "$x" "$y"
}

adb_text() {
  local text="$1"
  local adb_text="${text// /%s}"
  log_step "input text=$text"
  # shellcheck disable=SC2086
  "$ADB_BIN" $SERIAL_ARGS shell input text "$adb_text"
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

parse_common_args() {
  local serial=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --serial)
        serial="$2"
        COMMON_SCRIPT_ARGS+=(--serial "$2")
        shift 2
        ;;
      --package)
        COMMON_SCRIPT_ARGS+=(--package "$2")
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
    SERIAL_ARGS="-s $serial"
  fi
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ADB_BIN="$(resolve_adb)"
parse_common_args "${ARGS[@]}"

run_middle_actions() {
  # Fill your adb actions here. Keep sleep_between_steps between actions.
  #
  # Examples:
  # shellcheck disable=SC2086
  # "$ADB_BIN" $SERIAL_ARGS shell input tap 540 1600
  # sleep_between_steps
  # adb_text "take notes"
  # sleep_between_steps
  sleep_between_steps
  adb_tap 75 200
  sleep_between_steps
  adb_tap 400 1000
  sleep_between_steps
  adb_tap 425 2250
  sleep_between_steps
  adb_tap 180 1950
  sleep_between_steps
  adb_tap 500 1200
  sleep_between_steps
  adb_text "Hello World"
}

log_step "start dryrun"
"$SCRIPT_DIR/mobile-agent-spinner-start-dryrun.sh" "${ARGS[@]}"

# App-side start flow is asynchronous:
# fill input -> wait 2s -> simulate send -> wait 2s -> collapse -> show spinner/glow.
# Wait long enough before running the external adb actions.
log_step "wait for start flow to collapse container"
sleep 6

log_step "run middle adb actions"
run_middle_actions
sleep_between_steps

log_step "stop dryrun"
"$SCRIPT_DIR/mobile-agent-spinner-stop-dryrun.sh" "${ARGS[@]}"
