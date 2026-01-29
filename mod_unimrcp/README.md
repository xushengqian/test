## mod_unimrcp（模块骨架）

该目录下的 `mod_unimrcp` 旨在作为 **FreeSWITCH 模块骨架** 放入 FreeSWITCH 源码树中编译。

### 目录结构

- `src/mod_unimrcp.c`：模块入口与注册逻辑
- `conf/autoload_configs/unimrcp.conf.xml`：示例模块配置
- `conf/dialplan/default/unimrcp.xml`：示例 dialplan

### 集成到 FreeSWITCH（源码树内编译）

你可以把该目录复制到 FreeSWITCH 源码树，例如：

- `freeswitch-src/src/mod/applications/mod_unimrcp/`

然后：

1. 在 `freeswitch-src/src/mod/applications/Makefile.am` 增加一行：
   - `applications/mod_unimrcp`
2. 参考同目录下其他模块，为 `mod_unimrcp` 增加 `Makefile.am`（将 `src/mod_unimrcp.c` 编译成模块）
3. 在 FreeSWITCH 源码树执行构建与安装：
   - `./bootstrap.sh -j`
   - `./configure ...`
   - `make -j && make install`

安装后把 `conf/` 里的示例文件放到对应目录（或合并到你已有配置中），并确保加载模块。

### 已包含内容

- `src/mod_unimrcp.c`
  - 可加载的 FreeSWITCH 模块入口
  - 注册 dialplan 应用：`unimrcp`
  - 注册 fscli API：`unimrcp_status`

### 最小验证

- 在 `fscli` 中：
  - `load mod_unimrcp`
  - `unimrcp_status`

### 你需要补齐的内容（对接 UniMRCP）

骨架中没有引入 UniMRCP Client SDK。你通常需要：

- 引入 `unimrcpclient` 头文件与库
- 维护 MRCP profile/session/channel
- 将 FreeSWITCH 的音频流与 MRCP media 通道对接
- 提供 ASR/TTS 的 dialplan 语法与参数（如 profile、grammar、voice、timeouts 等）

