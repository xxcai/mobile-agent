#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/release-assets.sh <tag> <owner/repo> [notes]

示例:
  ./scripts/release-assets.sh v0.1.0-demo my-org/mobile-agent
  ./scripts/release-assets.sh v0.1.0-demo my-org/mobile-agent "Demo build"

环境变量:
  ROOT_DIR            可选，覆盖项目根目录。默认取当前脚本的上一级目录。
  CORE_ASSET_NAME     可选，agent-core 上传到 GitHub Release 后的文件名。默认: agent-core.aar
  ANDROID_ASSET_NAME  可选，agent-android 上传到 GitHub Release 后的文件名。默认: agent-android.aar
  BENCHMARK_ASSET_NAME 可选，benchmark-android 上传到 GitHub Release 后的文件名。默认: benchmark-android.aar

前置条件:
  - 本机已安装 gh，并已执行 gh auth login
  - 项目根目录下可以执行 ./gradlew

脚本行为:
  1. 构建 :agent-core、:agent-android 和 :benchmark-android 的 debug AAR
  2. 如果指定 tag 已存在，则覆盖上传 assets
  3. 如果指定 tag 不存在，则创建 release 并上传 assets

常见命令:
  ./scripts/release-assets.sh v0.1.0-demo your-org/mobile-agent
  ./scripts/release-assets.sh v0.1.0-demo your-org/mobile-agent "Demo 阶段联调包"
  CORE_ASSET_NAME=agent-core-demo.aar ANDROID_ASSET_NAME=agent-android-demo.aar BENCHMARK_ASSET_NAME=benchmark-android-demo.aar ./scripts/release-assets.sh v0.1.0-demo your-org/mobile-agent
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

TAG="${1:-}"
REPO="${2:-}"
NOTES="${3:-Demo build}"

if [[ -z "$TAG" || -z "$REPO" ]]; then
  usage >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="${ROOT_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
CORE_AAR="$ROOT_DIR/agent-core/build/outputs/aar/agent-core-debug.aar"
ANDROID_AAR="$ROOT_DIR/agent-android/build/outputs/aar/agent-android-debug.aar"
BENCHMARK_AAR="$ROOT_DIR/benchmark-android/build/outputs/aar/benchmark-android-debug.aar"
CORE_ASSET_NAME="${CORE_ASSET_NAME:-agent-core.aar}"
ANDROID_ASSET_NAME="${ANDROID_ASSET_NAME:-agent-android.aar}"
BENCHMARK_ASSET_NAME="${BENCHMARK_ASSET_NAME:-benchmark-android.aar}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_file() {
  if [[ ! -f "$1" ]]; then
    echo "Expected file not found: $1" >&2
    exit 1
  fi
}

require_cmd gh

cd "$ROOT_DIR"

./gradlew :agent-core:assembleDebug :agent-android:assembleDebug :benchmark-android:assembleDebug

require_file "$CORE_AAR"
require_file "$ANDROID_AAR"
require_file "$BENCHMARK_AAR"

if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  gh release upload "$TAG" \
    "$CORE_AAR#$CORE_ASSET_NAME" \
    "$ANDROID_AAR#$ANDROID_ASSET_NAME" \
    "$BENCHMARK_AAR#$BENCHMARK_ASSET_NAME" \
    --repo "$REPO" \
    --clobber
else
  gh release create "$TAG" \
    "$CORE_AAR#$CORE_ASSET_NAME" \
    "$ANDROID_AAR#$ANDROID_ASSET_NAME" \
    "$BENCHMARK_AAR#$BENCHMARK_ASSET_NAME" \
    --repo "$REPO" \
    --title "$TAG" \
    --notes "$NOTES"
fi

echo "Uploaded assets to GitHub release:"
echo "  repo: $REPO"
echo "  tag: $TAG"
echo "  core: $CORE_ASSET_NAME"
echo "  android: $ANDROID_ASSET_NAME"
echo "  benchmark: $BENCHMARK_ASSET_NAME"
