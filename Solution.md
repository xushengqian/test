# 解决 Apache Commons BeanUtils BigDecimal 转换异常

## 错误信息
```
org.apache.commons.beanutils.ConversionException: No value specified for 'BigDecimal'
```

## 问题原因
当使用 Apache Commons BeanUtils 将空字符串或 null 值转换为 `BigDecimal` 类型时，默认的转换器会抛出此异常。

## 解决方案

### 方案1: 注册允许null值的转换器（推荐）
在应用启动时（如Spring Boot的@PostConstruct或初始化方法中）注册自定义转换器：

```java
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.converters.BigDecimalConverter;
import java.math.BigDecimal;

// 允许null值
BigDecimalConverter converter = new BigDecimalConverter(null);
ConvertUtils.register(converter, BigDecimal.class);
```

### 方案2: 使用默认值
如果业务逻辑需要默认值而不是null：

```java
// 使用BigDecimal.ZERO作为默认值
BigDecimalConverter converter = new BigDecimalConverter(BigDecimal.ZERO);
ConvertUtils.register(converter, BigDecimal.class);
```

### 方案3: 转换前检查空值
在调用BeanUtils之前手动处理：

```java
public BigDecimal safeConvert(String value) {
    if (value == null || value.trim().isEmpty()) {
        return BigDecimal.ZERO; // 或返回null
    }
    return new BigDecimal(value);
}
```

### 方案4: 自定义转换器
创建完全自定义的转换器类：

```java
public class NullSafeBigDecimalConverter extends BigDecimalConverter {
    public NullSafeBigDecimalConverter() {
        super(null);
    }
    
    @Override
    public Object convert(Class type, Object value) {
        if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
            return null; // 或返回BigDecimal.ZERO
        }
        return super.convert(type, value);
    }
}

// 注册
ConvertUtils.register(new NullSafeBigDecimalConverter(), BigDecimal.class);
```

## 在Spring Boot中的应用

如果使用Spring Boot，可以在配置类中注册：

```java
@Configuration
public class BeanUtilsConfig {
    
    @PostConstruct
    public void configureBeanUtils() {
        BigDecimalConverter converter = new BigDecimalConverter(null);
        ConvertUtils.register(converter, BigDecimal.class);
    }
}
```

## 注意事项
- 方案1和方案2需要在应用启动时全局注册，会影响所有使用BeanUtils的地方
- 方案3需要在每个使用的地方手动处理，但更灵活
- 根据业务需求选择返回null还是默认值（如BigDecimal.ZERO）
