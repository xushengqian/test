# BigDecimal 去掉后面的0

## 方法说明

在 Java 中，可以使用 `stripTrailingZeros()` 方法去掉 BigDecimal 后面的0。

### 基本用法

```java
BigDecimal decimal = new BigDecimal("123.4500");
BigDecimal result = decimal.stripTrailingZeros();
// 结果: 123.45
```

### 注意事项

1. **科学计数法问题**：当去掉整数末尾的0时，可能会返回科学计数法表示
   ```java
   new BigDecimal("100.00").stripTrailingZeros()  // 返回 1E+2
   ```

2. **解决方案**：使用 `toPlainString()` 避免科学计数法
   ```java
   new BigDecimal("100.00").stripTrailingZeros().toPlainString()  // 返回 "100"
   ```

### 运行示例

查看 `BigDecimalExample.java` 文件，运行以下命令：

```bash
javac BigDecimalExample.java
java BigDecimalExample
```

### 常见场景

- **金额显示**：`金额.stripTrailingZeros().toPlainString()`
- **百分比显示**：去掉不必要的小数位
- **数据库存储**：节省存储空间