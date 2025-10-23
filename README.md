# FieldStrategy.IGNORED 实现

这个项目演示了 `FieldStrategy.IGNORED` 的实现和使用方法。

## 核心组件

### 1. FieldStrategy 枚举
- `DEFAULT`: 默认策略，正常处理字段
- `NOT_NULL`: 只处理非空字段
- `NOT_EMPTY`: 只处理非空且非空字符串的字段
- **`IGNORED`**: 完全忽略字段，不参与任何操作
- `ALWAYS`: 总是处理字段
- `NOT_ZERO`: 只处理非零数值字段

### 2. @FieldIgnore 注解
用于标记需要特殊处理的字段，支持：
- 指定字段策略（strategy）
- 指定忽略的操作类型（INSERT、UPDATE、SELECT、SERIALIZE等）

### 3. FieldProcessor 处理器
提供字段处理的核心逻辑：
- `processObject()`: 根据策略处理对象字段
- `getIgnoredFields()`: 获取被忽略的字段列表
- `shouldIgnoreInOperation()`: 判断字段在特定操作中是否应被忽略

## 使用示例

```java
public class User {
    private Long id;
    private String username;
    
    // 完全忽略的字段
    @FieldIgnore(strategy = FieldStrategy.IGNORED)
    private String password;
    
    // 在特定操作中忽略的字段
    @FieldIgnore(value = {FieldIgnore.IgnoreType.UPDATE})
    private Date createTime;
}
```

## 运行演示

```bash
javac *.java
java FieldStrategyDemo
```

## 主要特性

- ✅ 支持多种字段处理策略
- ✅ 支持按操作类型忽略字段
- ✅ 支持完全忽略字段（FieldStrategy.IGNORED）
- ✅ 提供灵活的字段处理逻辑
- ✅ 包含完整的使用示例和测试