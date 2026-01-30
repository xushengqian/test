## 最小可用配置与验证（源码 samples 默认布局）

下面以源码安装到 `/usr/local/freeswitch` 为例（即 `FS_HOME=/usr/local/freeswitch`）。

### 1) 启动服务

```bash
sudo systemctl enable --now freeswitch
sudo systemctl status freeswitch --no-pager -l
```

### 2) 确认 event socket（用于 `fs_cli`）

FreeSWITCH 的 `fs_cli` 默认通过 event socket 连接。samples 配置通常默认口令为 `ClueCon`。

- 配置文件通常位于：
  - `${FS_HOME}/conf/autoload_configs/event_socket.conf.xml`

你可以检查/修改口令（示意）：

```xml
<param name="password" value="ClueCon"/>
```

修改后重启：

```bash
sudo systemctl restart freeswitch
```

### 3) 使用 fs_cli 验证

```bash
sudo /usr/local/freeswitch/bin/fs_cli -x "status"
sudo /usr/local/freeswitch/bin/fs_cli -x "sofia status"
```

### 4) 常见问题排查

- `fs_cli` 连接不上：
  - 确认 FreeSWITCH 正在运行
  - 确认 `event_socket.conf.xml` 已启用并口令正确
  - 查看日志：`${FS_HOME}/log/freeswitch.log`

- systemd 启动失败：
  - `journalctl -u freeswitch -n 200 --no-pager`
