# 贡献指南

感谢您对FreeSWITCH + MRCP实时语音流项目的关注！

## 如何贡献

### 报告问题

如果您发现了bug或有功能建议：

1. 检查现有的Issues，确保问题未被报告
2. 创建新Issue，包含：
   - 清晰的标题和描述
   - 复现步骤
   - 预期行为和实际行为
   - 环境信息（操作系统、Python版本等）
   - 相关日志或错误信息

### 提交代码

1. **Fork项目**
   ```bash
   git clone https://github.com/your-username/freeswitch-mrcp-stream.git
   cd freeswitch-mrcp-stream
   ```

2. **创建特性分支**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **进行更改**
   - 遵循现有代码风格
   - 添加必要的测试
   - 更新文档

4. **提交更改**
   ```bash
   git add .
   git commit -m "添加：您的功能描述"
   ```

5. **推送到GitHub**
   ```bash
   git push origin feature/your-feature-name
   ```

6. **创建Pull Request**

## 代码规范

### Python代码风格

遵循PEP 8规范：

```python
# 好的示例
def process_audio_stream(audio_data: bytes, sample_rate: int = 16000) -> bytes:
    """
    处理音频流
    
    Args:
        audio_data: 原始音频数据
        sample_rate: 采样率
        
    Returns:
        处理后的音频数据
    """
    # 实现逻辑
    pass

# 不好的示例
def processAudioStream(audioData,sampleRate=16000):
    pass
```

### 文档字符串

使用Google风格的文档字符串：

```python
def function_name(param1: type1, param2: type2) -> return_type:
    """
    简短描述（一行）
    
    更详细的描述（如果需要）
    
    Args:
        param1: 参数1的描述
        param2: 参数2的描述
        
    Returns:
        返回值的描述
        
    Raises:
        ValueError: 什么情况下抛出
    """
```

### 提交信息格式

使用清晰的提交信息：

```
<类型>: <简短描述>

<详细描述>（可选）

<关联的Issue>（可选）
```

类型：
- `添加`: 新功能
- `修复`: Bug修复
- `文档`: 文档更新
- `优化`: 性能优化
- `重构`: 代码重构
- `测试`: 添加测试
- `配置`: 配置文件更改

示例：
```
添加: 实现WebSocket音频流传输

- 添加AudioWebSocketServer类
- 支持多客户端连接
- 实现音频数据广播

关联 #123
```

## 测试

在提交PR之前，请确保：

1. **运行所有测试**
   ```bash
   python3 -m pytest tests/
   ```

2. **运行代码检查**
   ```bash
   flake8 src/
   black src/ --check
   ```

3. **测试示例代码**
   ```bash
   python3 quickstart.py --test-all
   ```

## 添加新功能

1. **计划功能**
   - 在Issue中讨论功能设计
   - 获得维护者的反馈

2. **实现功能**
   - 编写代码
   - 添加单元测试
   - 更新文档

3. **创建示例**
   - 在`examples/`目录添加使用示例
   - 更新README.md

## 文档

### 更新README

如果您的更改影响到用户使用方式，请更新README.md：

- 功能描述
- 配置说明
- 使用示例
- API文档

### 添加注释

为复杂的逻辑添加注释：

```python
# 使用线性插值进行重采样
# ratio表示新采样率与原采样率的比值
ratio = target_rate / self.sample_rate
```

## 性能考虑

- 避免阻塞操作
- 使用异步I/O
- 合理使用缓冲区
- 注意内存管理

## 安全

- 不要提交敏感信息（密码、密钥等）
- 验证所有外部输入
- 使用参数化查询
- 遵循安全最佳实践

## 问题和支持

- 技术问题：提交Issue
- 功能建议：提交Feature Request
- 安全问题：私下联系维护者

## 行为准则

- 尊重所有贡献者
- 保持建设性的讨论
- 欢迎新手
- 专注于技术问题

## 许可证

通过贡献代码，您同意您的贡献将按照项目的MIT许可证进行许可。

---

再次感谢您的贡献！
