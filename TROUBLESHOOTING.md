# FreeSWITCH Troubleshooting: NORMAL_UNSPECIFIED

## 概述

**NORMAL_UNSPECIFIED** 是 FreeSWITCH 中常见的挂断原因（Hangup Cause），对应的 **Q.850 Cause Code 是 31**。

这就好比是在说：“通话结束了，是一个正常的错误，但没有更具体的分类。”

在 SIP 协议中，这通常映射为 **480 Temporarily Unavailable**，但在某些情况下也可能映射为其他 4xx 或 5xx 错误。

---

## 常见原因

虽然名字叫 "Normal"（正常），但它通常意味着**呼叫失败**。以下是最常见的原因：

1.  **路由失败 / 用户离线**
    -   FreeSWITCH 尝试呼叫一个注册用户，但该用户未注册或不可达。
    -   网关（Gateway）返回了 480 或类似的拒绝响应。

2.  **编解码器（Codec）协商失败**
    -   主叫方和被叫方没有共同支持的音频/视频编码（例如，一方只支持 G729，另一方只支持 PCMA）。
    -   这是非常常见的原因，特别是当两端网络环境差异较大时。

3.  **Dialplan 配置错误**
    -   Dialplan 执行到了末尾，但没有建立任何 Bridge，也没有显式挂断，系统默认返回 NORMAL_UNSPECIFIED。
    -   或者 Bridge 动作失败（例如 bridge 字符串格式错误）。

4.  **ACL / 防火墙 / 鉴权拒绝**
    -   呼叫被 ACL (Access Control List) 拒绝。
    -   上游运营商因为余额不足或认证失败拒绝了呼叫，并没有给出具体的错误码，只是断开了连接。

---

## 排查步骤

要找到根本原因，必须查看 FreeSWITCH 的详细日志或 SIP 抓包。

### 1. 使用 fs_cli 查看日志

进入 FreeSWITCH 命令行界面：

```bash
fs_cli
```

开启详细的 SIP 跟踪和调试日志：

```bash
# 开启 SIP 消息跟踪（查看具体的 SIP 交互）
sofia global siptrace on

# 开启详细日志级别
console loglevel debug
```

**在此状态下重现呼叫**，然后观察日志。

### 2. 在日志中搜索什么？

重点关注挂断前的最后几行日志。

**检查 SIP 响应：**
查找 `488 Not Acceptable Here`（通常暗示 Codec 问题）或 `480 Temporarily Unavailable`。

**检查 Codec 协商：**
搜索类似 `Codec` 或 `SDP` 的关键字，查看是否有一方提供的 SDP 中没有包含预期的编码。

**检查 Dialplan 执行流程：**
确认 Dialplan 是否正确进入了 `bridge` 应用程序。如果日志显示 `Executing [xxx] bridge` 之后立即挂断，说明 Bridge 失败。

### 3. 使用 tcpdump/tshark 抓包

如果日志看不出问题，抓取网络包是终极手段。

```bash
# 抓取端口 5060 (SIP) 和 RTP 端口范围（假设是 16384-32768）
tcpdump -i eth0 -n -s 0 -w call_debug.pcap port 5060 or portrange 16384-32768
```

使用 **Wireshark** 打开生成的 `call_debug.pcap`，使用 "Telephony -> VoIP Calls" 功能分析呼叫流程，查看是谁先发起的 BYE 或 Cancel，以及携带的原因码。

---

## 解决方案建议

-   **如果是 Codec 问题**：
    -   检查 `vars.xml` 或 SIP Profile 中的 `inbound-codec-prefs` 和 `outbound-codec-prefs`。
    -   确保 `PCMA` (G711a) 和 `PCMU` (G711u) 被包含在内作为兜底方案。
    -   尝试开启 `late-negotiation`（后期协商）。

-   **如果是路由问题**：
    -   检查 `sofia status profile internal` 确认用户是否已注册。
    -   检查 `bridge` 语句中的目标 IP 或网关名称是否正确。

-   **如果是超时**：
    -   增加 `call_timeout` 变量的值。
    -   检查网络连通性（Ping, Traceroute）。

---

## 总结

`NORMAL_UNSPECIFIED` 只是一个通用的“结果”，不是“原因”。你必须通过日志（Log）和抓包（Packet Capture）来还原“过程”，才能找到真正的症结所在。
