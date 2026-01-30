#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "建议用 sudo 运行以便读取服务状态: sudo bash $0" >&2
fi

FS_HOME="${FS_HOME:-/usr/local/freeswitch}"

echo "== systemd 服务状态 =="
if command -v systemctl >/dev/null 2>&1; then
  systemctl --no-pager -l status freeswitch || true
else
  echo "系统未检测到 systemctl，跳过。"
fi

echo
echo "== 进程检查 =="
if pgrep -a freeswitch >/dev/null 2>&1; then
  pgrep -a freeswitch
else
  echo "未找到 freeswitch 进程。"
fi

echo
echo "== fs_cli 连接测试 =="
if [[ -x "${FS_HOME}/bin/fs_cli" ]]; then
  # 默认 event socket 口令在 samples 配置中为 ClueCon，可按需修改 vars.xml / event_socket.conf.xml
  "${FS_HOME}/bin/fs_cli" -x "status" || true
else
  echo "未找到 fs_cli: ${FS_HOME}/bin/fs_cli"
fi
