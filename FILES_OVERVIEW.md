# 文件清单和说明

## 📁 项目结构

```
/workspace/
├── README.md                    # 主文档（详细说明和最佳实践）
├── QUICK_REFERENCE.md           # 快速参考指南
├── FILES_OVERVIEW.md            # 本文件（文件清单）
├── install.sh                   # 自动安装脚本
├── setup_database.sql           # 数据库初始化脚本
├── .gitignore                   # Git忽略文件
│
├── transfer_to_agent.lua        # 完整的转接脚本
├── transfer_simple.lua          # 简化示例脚本
├── agent_utils.lua              # 工具函数库
├── transfer_example.lua         # 使用示例
├── test_agent_status.lua        # 测试脚本
│
└── dialplan_example.xml         # Dialplan配置示例
```

## 📄 核心脚本文件

### 1. transfer_to_agent.lua
**用途**: 完整的生产级转接脚本

**功能**:
- ✅ 转接前检查坐席注册状态
- ✅ 智能重试机制
- ✅ 详细的错误处理和日志
- ✅ 根据失败原因分类处理
- ✅ 播放语音提示

**使用场景**: 生产环境直接使用

**调用方式**:
```xml
<action application="set" data="agent_number=1001"/>
<action application="lua" data="transfer_to_agent.lua"/>
```

---

### 2. transfer_simple.lua
**用途**: 简化示例，展示核心判断逻辑

**功能**:
- 📝 方法1: bridge后检查hangup_cause
- 📝 方法2: 使用originate API
- 📝 方法3: 先检查注册再转接（推荐）

**使用场景**: 学习和理解转接判断机制

**调用方式**:
```xml
<action application="lua" data="transfer_simple.lua"/>
```

---

### 3. agent_utils.lua
**用途**: 可复用的工具函数库

**提供的函数**:
- `isRegistered()` - 检查坐席注册状态
- `isBusy()` - 检查坐席是否在通话
- `getStatus()` - 获取综合状态
- `transfer()` - 带状态检查的转接
- `smartTransfer()` - 智能转接（自动选择可用坐席）
- `getAvailableAgents()` - 获取可用坐席列表
- `playTransferPrompt()` - 播放转接提示音
- `playFailurePrompt()` - 播放失败提示音
- `logToDatabase()` - 记录日志到数据库
- `getCallcenterStatus()` - 获取callcenter模块状态

**使用场景**: 在其他Lua脚本中引用

**调用方式**:
```lua
local AgentUtils = require("agent_utils")
local success, result = AgentUtils.transfer(session, "1001", 30)
```

---

### 4. transfer_example.lua
**用途**: 展示如何使用agent_utils工具库

**包含的示例**:
- 示例1: 单个坐席转接
- 示例2: 智能转接到多个坐席
- 示例3: 检查状态后再决定
- 示例4: 带重试的转接

**使用场景**: 学习和参考

**调用方式**:
```xml
<action application="set" data="transfer_mode=smart"/>
<action application="lua" data="transfer_example.lua"/>
```

---

### 5. test_agent_status.lua
**用途**: 测试坐席状态检查功能

**测试项目**:
- 测试1: 坐席注册状态检查
- 测试2: 坐席通话状态检查
- 测试3: 综合状态检查
- 测试4: Sofia配置状态
- 测试5: 转接模拟测试
- 测试6: 性能测试

**使用场景**: 调试和验证

**调用方式**:
```bash
fs_cli -x "luarun test_agent_status.lua"
```

---

## 📄 配置文件

### 6. dialplan_example.xml
**用途**: Dialplan配置示例

**包含内容**:
- 基本转接配置
- 带条件判断的转接
- 转接失败处理

**使用方式**: 复制到FreeSWITCH的dialplan目录并修改

---

## 📄 数据库文件

### 7. setup_database.sql
**用途**: 初始化数据库表

**创建的表**:
- `transfer_logs` - 转接日志表
- `agent_status` - 坐席状态表
- `agent_statistics` - 坐席统计视图

**使用方式**:
```bash
sqlite3 /var/lib/freeswitch/freeswitch.db < setup_database.sql
```

---

## 📄 安装和文档

### 8. install.sh
**用途**: 自动化安装脚本

**功能**:
- 自动检测FreeSWITCH安装路径
- 备份已有文件
- 安装Lua脚本
- 可选安装Dialplan配置
- 可选初始化数据库
- 运行测试

**使用方式**:
```bash
sudo ./install.sh           # 交互式安装
sudo ./install.sh -y        # 自动安装
sudo ./install.sh -s        # 仅安装脚本
```

---

### 9. README.md
**用途**: 详细的项目文档

**包含内容**:
- 问题描述和解决方案
- 三种判断方法详解
- 关键挂断原因代码表
- 使用步骤和最佳实践
- 常见问题解答
- 调试技巧
- 扩展功能示例

---

### 10. QUICK_REFERENCE.md
**用途**: 快速参考指南

**包含内容**:
- 核心代码片段
- 挂断原因速查表
- 常用API命令
- 必须设置的变量
- 调试技巧
- 常见问题快速解决

---

### 11. FILES_OVERVIEW.md
**用途**: 本文件，提供项目文件清单

---

## 🚀 快速开始指南

### 新手使用流程

1. **阅读文档**
   ```bash
   cat README.md
   cat QUICK_REFERENCE.md
   ```

2. **安装脚本**
   ```bash
   sudo ./install.sh
   ```

3. **测试功能**
   ```bash
   fs_cli -x "luarun test_agent_status.lua"
   ```

4. **配置Dialplan**
   - 编辑 `dialplan_example.xml`
   - 复制到FreeSWITCH配置目录
   - 重载配置: `fs_cli -x "reloadxml"`

5. **测试转接**
   - 拨打配置的号码
   - 观察日志输出

---

## 📚 推荐学习路径

### 初学者
1. 阅读 `QUICK_REFERENCE.md`
2. 查看 `transfer_simple.lua` 理解基本原理
3. 运行 `test_agent_status.lua` 测试环境
4. 使用 `transfer_to_agent.lua` 进行实际转接

### 进阶使用
1. 学习 `agent_utils.lua` 工具库
2. 参考 `transfer_example.lua` 实现自定义逻辑
3. 根据业务需求修改和扩展

### 生产部署
1. 审查和测试所有脚本
2. 配置数据库记录日志
3. 设置监控和告警
4. 准备应急预案

---

## 🔧 维护和扩展

### 日志位置
- FreeSWITCH日志: `/var/log/freeswitch/freeswitch.log`
- 自定义日志: 可配置写入数据库

### 性能优化
- 复用API对象
- 缓存坐席状态
- 批量数据库操作

### 常见修改
1. **修改坐席列表**: 编辑 `agent_list` 变量
2. **修改超时时间**: 修改 `call_timeout` 变量
3. **修改重试次数**: 修改 `max_retry` 变量
4. **自定义提示音**: 修改音频文件路径

---

## 📞 技术支持

如有问题，请参考：
1. `README.md` 的常见问题章节
2. `QUICK_REFERENCE.md` 的调试技巧
3. FreeSWITCH官方文档

---

## 📝 版本信息

- 版本: 1.0.0
- 最后更新: 2025-12-01
- 兼容性: FreeSWITCH 1.6+

---

## 📄 许可证

本项目代码可自由使用和修改。
