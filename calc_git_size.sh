#!/usr/bin/env sh
set -eu

print_help() {
    cat <<'EOF'
用法: ./calc_git_size.sh [选项] [ROOT_DIR]

选项:
  -h, --help     显示本帮助信息并退出
  -H, --human    以人类可读的单位显示大小（需要 numfmt）

参数:
  ROOT_DIR       开始搜索的根目录，默认使用当前目录
EOF
}

HUMAN_READABLE=0
ROOT_DIR=""

while [ $# -gt 0 ]; do
    case "$1" in
        -h|--help)
            print_help
            exit 0
            ;;
        -H|--human)
            HUMAN_READABLE=1
            ;;
        *)
            if [ -z "$ROOT_DIR" ]; then
                ROOT_DIR=$1
            else
                echo "错误: 只允许提供一个根目录参数" >&2
                exit 1
            fi
            ;;
    esac
    shift
    done

if [ -z "$ROOT_DIR" ]; then
    ROOT_DIR=$(pwd)
fi

if ! ROOT_DIR_ABS=$(cd "$ROOT_DIR" 2>/dev/null && pwd); then
    echo "错误: 找不到目录 $ROOT_DIR" >&2
    exit 1
fi
ROOT_DIR=$ROOT_DIR_ABS

TMP_FILE=$(mktemp -t calc-git-size.XXXXXX)
cleanup() {
    rm -f "$TMP_FILE"
}
trap cleanup EXIT INT TERM

find "$ROOT_DIR" -type d -name ".git" -prune > "$TMP_FILE" 2>/dev/null || true

if [ ! -s "$TMP_FILE" ]; then
    echo "在 $ROOT_DIR 中未找到任何 .git 目录" >&2
    exit 1
fi

NUMFMT_AVAILABLE=0
if command -v numfmt >/dev/null 2>&1; then
    NUMFMT_AVAILABLE=1
fi

format_size() {
    bytes=$1
    if [ "$HUMAN_READABLE" -eq 1 ] && [ "$NUMFMT_AVAILABLE" -eq 1 ]; then
        numfmt --to=iec "$bytes" 2>/dev/null || printf "%sB" "$bytes"
    else
        printf "%sB" "$bytes"
    fi
}

TOTAL_BYTES=0
while IFS= read -r git_dir || [ -n "$git_dir" ]; do
    [ -z "$git_dir" ] && continue
    size_bytes=$(du -sb "$git_dir" 2>/dev/null | awk '{print $1}')
    [ -z "$size_bytes" ] && continue
    TOTAL_BYTES=$((TOTAL_BYTES + size_bytes))
    size_display=$(format_size "$size_bytes")
    repo_dir=$(dirname "$git_dir")
    printf "%s\t%s\n" "$size_display" "$repo_dir"
done < "$TMP_FILE"

total_display=$(format_size "$TOTAL_BYTES")
printf "Total\t%s\n" "$total_display"
