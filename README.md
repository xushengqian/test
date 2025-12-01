# Java DateTimeParseException 解决方案示例

这个仓库展示了如何解决常见的 Java 日期时间解析错误：

```
java.time.format.DateTimeParseException: Text '2024-07-01' could not be parsed: 
Unable to obtain LocalDateTime from TemporalAccessor
```

## 快速开始

### 运行示例

```bash
# 基础示例 - 展示所有解决方案
javac DateTimeParseExample.java && java DateTimeParseExample

# 实用工具示例 - 真实场景应用
javac DateTimeUtilExample.java && java DateTimeUtilExample
```

### 快速修复

如果你遇到这个错误，最简单的修复方法是：

```java
// ❌ 错误写法
LocalDateTime dateTime = LocalDateTime.parse("2024-07-01");

// ✅ 正确写法1 - 使用 LocalDate
LocalDate date = LocalDate.parse("2024-07-01");

// ✅ 正确写法2 - 转换为 LocalDateTime
LocalDateTime dateTime = LocalDate.parse("2024-07-01").atStartOfDay();

// ✅ 正确写法3 - 添加时间部分
LocalDateTime dateTime = LocalDateTime.parse("2024-07-01T00:00:00");
```

## 文件说明

- **DateTimeParseExample.java** - 基础示例，演示3种主要解决方案
- **DateTimeUtilExample.java** - 实用工具类，包含智能解析、安全解析等方法
- **日期解析错误解决方案.md** - 完整的文档和最佳实践指南

## 核心解决方案

### 1. 使用正确的类型（推荐）

```java
// 只需要日期 → 使用 LocalDate
LocalDate date = LocalDate.parse("2024-07-01");

// 需要日期和时间 → 先解析为 LocalDate，再转换
LocalDateTime dateTime = LocalDate.parse("2024-07-01").atStartOfDay();
```

### 2. 修改输入格式

```java
// 确保输入包含时间部分
LocalDateTime dateTime = LocalDateTime.parse("2024-07-01T00:00:00");
```

### 3. 使用智能解析（处理多种格式）

```java
public LocalDateTime smartParse(String input) {
    if (input.contains("T") || input.contains(" ")) {
        // 包含时间部分，直接解析
        return LocalDateTime.parse(input);
    } else {
        // 只有日期，转换为 LocalDateTime
        return LocalDate.parse(input).atStartOfDay();
    }
}
```

## 常见问题

**Q: 为什么会出现这个错误？**  
A: 因为 `LocalDateTime` 需要同时包含日期和时间信息，而 `"2024-07-01"` 只包含日期部分。

**Q: 我应该使用哪种解决方案？**  
A: 如果只需要日期，使用 `LocalDate`；如果需要日期时间，先解析为 `LocalDate` 再转换为 `LocalDateTime`。

**Q: 如何处理用户输入的日期？**  
A: 使用 try-catch 包裹解析代码，或使用 `Optional` 进行安全处理。参见 `DateTimeUtilExample.java`。

## 相关资源

- [Java 8 Date Time API 官方文档](https://docs.oracle.com/javase/8/docs/api/java/time/package-summary.html)
- [DateTimeFormatter 文档](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html)

## 许可证

本示例代码供学习参考使用。