#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "请使用 sudo 运行: sudo bash $0" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive

apt-get update

# 说明：
# - 这里选择“足够编译并跑起来”的常用依赖集合
# - 你可以按需要增加数据库/编解码/额外模块依赖
apt-get install -y --no-install-recommends \
  ca-certificates \
  curl \
  git \
  gnupg \
  lsb-release \
  build-essential \
  pkg-config \
  autoconf \
  automake \
  libtool \
  cmake \
  yasm \
  nasm \
  python3 \
  libssl-dev \
  zlib1g-dev \
  libedit-dev \
  libsqlite3-dev \
  libcurl4-openssl-dev \
  libpcre3-dev \
  libspeexdsp-dev \
  libopus-dev \
  libsndfile1-dev \
  libldns-dev \
  libjpeg-dev

echo "依赖安装完成。接下来可运行: sudo -E bash scripts/20-build-from-source.sh"
