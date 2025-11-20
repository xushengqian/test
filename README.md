# BigDecimal 转换异常解决方案

## 问题
```
org.apache.commons.beanutils.ConversionException: No value specified for 'BigDecimal'
```

## 快速解决方案

在应用启动时添加以下代码：

```java
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.converters.BigDecimalConverter;
import java.math.BigDecimal;

// 允许空值转换，返回 null
ConvertUtils.register(new BigDecimalConverter(null), BigDecimal.class);
```

## 文件说明

- **解决方案说明.md** - 详细的问题分析和解决方案
- **BigDecimalConverterExample.java** - 基本示例代码
- **ManualValidationExample.java** - 手动验证方案
- **GlobalConverterConfig.java** - 全局配置工具类
- **SpringBootExample.java** - Spring Boot集成示例
- **pom.xml** - Maven依赖配置

## 使用建议

1. 如果是 **Spring Boot 项目**，查看 `SpringBootExample.java`
2. 如果需要 **全局配置**，查看 `GlobalConverterConfig.java`
3. 完整说明请查看 `解决方案说明.md`