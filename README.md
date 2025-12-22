# mini-redis（实现 `SETNX`）

这是一个极简 Redis-like 服务器（RESP2 协议），重点实现 `SETNX`，并提供 `GET/DEL/EXISTS/PING` 方便验证。

## 运行

```bash
python3 server.py --host 127.0.0.1 --port 6380
```

用 `redis-cli` 连接（本机需安装 redis-cli）：

```bash
redis-cli -p 6380 PING
redis-cli -p 6380 SETNX mykey hello
redis-cli -p 6380 SETNX mykey world
redis-cli -p 6380 GET mykey
```

`SETNX` 返回：
- `1`：成功设置（key 原本不存在）
- `0`：未设置（key 已存在）

## 测试

```bash
python3 -m unittest -v
```