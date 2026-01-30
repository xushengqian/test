#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "请使用 sudo 运行: sudo -E bash $0" >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FS_HOME="${FS_HOME:-/usr/local/freeswitch}"

if [[ ! -x "${FS_HOME}/bin/freeswitch" ]]; then
  echo "未找到可执行文件: ${FS_HOME}/bin/freeswitch" >&2
  echo "请先完成源码安装（scripts/20-build-from-source.sh），或设置正确的 FS_HOME。" >&2
  exit 1
fi

if ! id -u freeswitch >/dev/null 2>&1; then
  useradd --system --home "${FS_HOME}" --shell /usr/sbin/nologin freeswitch
fi

mkdir -p /etc/default
cat > /etc/default/freeswitch <<EOF
# FreeSWITCH systemd environment overrides
FS_HOME=${FS_HOME}
EOF

install -m 0644 "${REPO_ROOT}/systemd/freeswitch.service" /etc/systemd/system/freeswitch.service

chown -R freeswitch:freeswitch "${FS_HOME}"

systemctl daemon-reload

echo "systemd 已安装: /etc/systemd/system/freeswitch.service"
echo "可执行: systemctl enable --now freeswitch"
