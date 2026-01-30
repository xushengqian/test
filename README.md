# FreeSWITCH 初始安装与启动（Ubuntu/Debian）

这个仓库提供一套 **可复制粘贴** 的 FreeSWITCH 初始安装/编译、systemd 启动、以及基本验证脚本，适用于 Ubuntu/Debian（其他发行版可参考思路调整依赖）。

## 目录结构

- `scripts/`：一键脚本（安装依赖、源码编译、安装 systemd、验证）
- `systemd/`：systemd service 模板

## 方式 A：源码编译安装（推荐用于可控环境）

### 1) 安装编译依赖

```bash
sudo bash scripts/10-install-deps-ubuntu.sh
```

### 2) 拉取源码并编译安装

默认安装前缀为 `/usr/local/freeswitch`，你也可以通过环境变量修改。

```bash
# 可选：指定分支/Tag/提交（默认 master）
export FS_REF=master

# 可选：指定安装目录（默认 /usr/local/freeswitch）
export FS_PREFIX=/usr/local/freeswitch

sudo -E bash scripts/20-build-from-source.sh
```

### 3) 安装并启用 systemd 服务

```bash
# 可选：指定 FS_HOME（默认 /usr/local/freeswitch）
export FS_HOME=/usr/local/freeswitch

sudo -E bash scripts/30-systemd-install.sh
sudo systemctl enable --now freeswitch
```

### 4) 验证

```bash
sudo bash scripts/40-verify.sh
```

## 常见路径说明（源码安装）

- FreeSWITCH home: `/usr/local/freeswitch`
- 可执行文件: `/usr/local/freeswitch/bin/freeswitch`
- CLI: `/usr/local/freeswitch/bin/fs_cli`

## 说明与约定

- 脚本默认使用 `bash`，并开启 `set -euo pipefail`。
- 脚本会尽量做到 **幂等**（重复执行不会破坏环境）。
- 如果你需要启用更丰富的模块/依赖（如 Postgres、ODBC、H.264、VAD 等），可以在安装依赖脚本中按需扩展。

## 最小配置与排查

见 `docs/minimal-config-and-verify.md`。