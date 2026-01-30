#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "请使用 sudo 运行: sudo -E bash $0" >&2
  exit 1
fi

FS_REF="${FS_REF:-master}"
FS_PREFIX="${FS_PREFIX:-/usr/local/freeswitch}"
FS_SRC_DIR="${FS_SRC_DIR:-/usr/local/src/freeswitch}"
JOBS="${JOBS:-$(nproc)}"

echo "将从源码构建 FreeSWITCH"
echo "- FS_REF=${FS_REF}"
echo "- FS_PREFIX=${FS_PREFIX}"
echo "- FS_SRC_DIR=${FS_SRC_DIR}"
echo "- JOBS=${JOBS}"

mkdir -p "$(dirname "${FS_SRC_DIR}")"

if [[ ! -d "${FS_SRC_DIR}/.git" ]]; then
  git clone https://github.com/signalwire/freeswitch.git "${FS_SRC_DIR}"
fi

cd "${FS_SRC_DIR}"
git fetch --all --tags
git checkout "${FS_REF}"

./bootstrap.sh -j

./configure --prefix="${FS_PREFIX}"

make -j "${JOBS}"
make install

# 安装示例配置（conf/）与默认音频资源（可按需跳过）
make samples
make cd-sounds-install
make cd-moh-install

echo "安装完成。二进制位于: ${FS_PREFIX}/bin/freeswitch"
echo "下一步建议: sudo -E bash scripts/30-systemd-install.sh"
