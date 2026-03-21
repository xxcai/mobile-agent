#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  bash scripts/sync-planning-to-main-worktree.sh

说明:
  - 假设当前目录是一个 git worktree
  - 将当前目录下的 .planning/ 同步到主仓目录对应的 worktree
  - 如果目标位置已存在同名文件，则直接报错退出，不覆盖
EOF
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "缺少命令: $1" >&2
    exit 1
  fi
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

require_cmd git

CURRENT_ROOT="$(git rev-parse --show-toplevel)"
CURRENT_ROOT="${CURRENT_ROOT%/}"
SOURCE_DIR="$CURRENT_ROOT/.planning"

if [[ ! -d "$SOURCE_DIR" ]]; then
  echo "当前 worktree 下不存在 .planning 目录: $SOURCE_DIR" >&2
  exit 1
fi

PRIMARY_ROOT=""

while IFS= read -r line; do
  if [[ "$line" == worktree\ * ]]; then
    candidate="${line#worktree }"
    candidate="${candidate%/}"
    if [[ -z "$PRIMARY_ROOT" ]]; then
      PRIMARY_ROOT="$candidate"
      break
    fi
  fi
done < <(git worktree list --porcelain)

TARGET_ROOT="$PRIMARY_ROOT"

if [[ -z "$TARGET_ROOT" ]]; then
  echo "未找到主 worktree 目录。" >&2
  exit 1
fi

if [[ "$TARGET_ROOT" == "$CURRENT_ROOT" ]]; then
  echo "当前目录已经是主 worktree，无需同步。" >&2
  exit 1
fi

TARGET_DIR="$TARGET_ROOT/.planning"

while IFS= read -r src_path; do
  rel_path="${src_path#"$SOURCE_DIR"/}"
  target_path="$TARGET_DIR/$rel_path"

  if [[ -e "$target_path" ]]; then
    echo "发现冲突文件，已停止同步: $target_path" >&2
    echo "请手动更新主目录中的对应文件。" >&2
    exit 1
  fi
done < <(find "$SOURCE_DIR" -type f)

mkdir -p "$TARGET_DIR"

while IFS= read -r src_path; do
  rel_path="${src_path#"$SOURCE_DIR"/}"
  target_path="$TARGET_DIR/$rel_path"
  target_parent="$(dirname "$target_path")"

  mkdir -p "$target_parent"
  cp "$src_path" "$target_path"
  echo "已复制: .planning/$rel_path"
done < <(find "$SOURCE_DIR" -type f | sort)

echo "完成，已同步到: $TARGET_DIR"
