#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/download-agent-aars.sh <owner/repo> [version] [download_dir]

参数说明:
  <owner/repo>   必填，GitHub 仓库，格式为 owner/repo
  [version]      可选，默认 latest。可传 latest 或具体 tag，例如 v0.1.0-demo
  [download_dir] 可选，默认 ./libs/mobile-agent

示例:
  ./scripts/download-agent-aars.sh your-org/mobile-agent
  ./scripts/download-agent-aars.sh your-org/mobile-agent latest
  ./scripts/download-agent-aars.sh your-org/mobile-agent v0.1.0-demo
  ./scripts/download-agent-aars.sh your-org/mobile-agent v0.1.0-demo ./vendor/mobile-agent

环境变量:
  GITHUB_TOKEN   可选。访问私有仓库时用于下载 Release assets

脚本行为:
  1. 从 GitHub Release 下载 agent-core.aar 和 agent-android.aar
  2. 下载到指定目录
  3. 不修改任何 Gradle 配置，不自动添加依赖

兼容性:
  - macOS: 可直接用 bash 执行
  - Windows: 可用 Git Bash 执行

注意:
  1. 业务项目若要使用下载后的 AAR，需要自行配置 flatDir 或 files(...)
  2. 三方依赖也需要业务项目自行声明
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

REPO="${1:-}"
VERSION="${2:-latest}"
DOWNLOAD_DIR="${3:-./libs/mobile-agent}"
ASSETS=("agent-core.aar" "agent-android.aar")

if [[ -z "$REPO" ]]; then
  usage >&2
  exit 1
fi

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "缺少命令: $1" >&2
    exit 1
  fi
}

download_with_curl() {
  local url="$1"
  local output="$2"

  if [[ -n "${GITHUB_TOKEN:-}" ]]; then
    curl -fL \
      -H "Authorization: Bearer ${GITHUB_TOKEN}" \
      -H "Accept: application/octet-stream" \
      -o "$output" \
      "$url"
  else
    curl -fL \
      -H "Accept: application/octet-stream" \
      -o "$output" \
      "$url"
  fi
}

require_cmd curl
mkdir -p "$DOWNLOAD_DIR"

if [[ "$VERSION" == "latest" ]]; then
  BASE_URL="https://github.com/${REPO}/releases/latest/download"
else
  BASE_URL="https://github.com/${REPO}/releases/download/${VERSION}"
fi

for asset in "${ASSETS[@]}"; do
  target="${DOWNLOAD_DIR%/}/${asset}"
  url="${BASE_URL}/${asset}"
  echo "Downloading ${url}"
  download_with_curl "$url" "$target"
  echo "Saved to ${target}"
done

echo "下载完成:"
echo "  repo: ${REPO}"
echo "  version: ${VERSION}"
echo "  dir: ${DOWNLOAD_DIR}"
