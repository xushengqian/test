# mod_unimrcp（FreeSWITCH + UniMRCP）模块骨架

这个仓库提供一个 **FreeSWITCH `mod_unimrcp` 的最小可用模块骨架**（可加载、注册应用与 API），以及示例配置文件目录，方便你把 UniMRCP（ASR/TTS）能力接入 FreeSWITCH。

> 说明：这里提供的是“骨架/起步工程”。真正的 MRCP 会话、资源管理、音频流处理等逻辑，需要你在此基础上对接 UniMRCP Client SDK 继续实现。

## 目录结构

- `mod_unimrcp/src/mod_unimrcp.c`：模块入口（load/shutdown）、注册 dialplan app 与 fscli API
- `mod_unimrcp/conf/autoload_configs/unimrcp.conf.xml`：示例配置（模块参数占位）
- `mod_unimrcp/conf/dialplan/default/unimrcp.xml`：示例 dialplan（展示如何调用应用）

## 如何集成到 FreeSWITCH 源码树编译

该模块设计为放入 FreeSWITCH 源码树后参与编译（因为需要 FreeSWITCH 的头文件与构建系统）。

### 方式 A：直接复制到 FreeSWITCH modules 目录（推荐）

1. 将本仓库的 `mod_unimrcp/` 复制到 FreeSWITCH 源码树，例如：
   - `freeswitch-src/src/mod/applications/mod_unimrcp/`
2. 在 `freeswitch-src/src/mod/applications/Makefile.am` 中加入：
   - `applications/mod_unimrcp`
3. 为 `mod_unimrcp` 添加/调整对应的 `Makefile.am`（你可以参考 FreeSWITCH 现有模块写法）
4. 在 FreeSWITCH 源码树中执行构建：
   - `./bootstrap.sh -j`
   - `./configure ...`
   - `make -j && make install`

### 方式 B：按你的工程方式（CMake/外部构建）

如果你打算在 FreeSWITCH 之外独立构建 `.so`，需要你提供 FreeSWITCH 的 include/lib 路径，并处理符号导出与 ABI 兼容；本仓库目前不强制提供该方式的完整脚本，避免误导。

## 运行与验证（最小验证）

1. 放置配置：
   - `autoload_configs/unimrcp.conf.xml`
   - dialplan 示例可选
2. `modules.conf.xml` 中确保加载模块（或 fscli 手动加载）：
   - `load mod_unimrcp`
3. 在 `fscli` 中验证：
   - `load mod_unimrcp`
   - `unimrcp_status`

## 后续你通常会做的事（实现 UniMRCP 对接）

- 把 UniMRCP Client SDK（`unimrcpclient`）作为依赖引入构建
- 在模块里实现：
  - MRCP session/profile 管理
  - ASR/TTS 请求/事件回调
  - FreeSWITCH 音频与 MRCP media 的桥接
  - dialplan 应用参数解析与错误处理