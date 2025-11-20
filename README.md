# BeanUtils BigDecimal 转换异常解决方案

## 问题描述

在使用 Apache Commons BeanUtils 时，当尝试将空值（null）或空字符串转换为 `BigDecimal` 类型时，会抛出以下异常：

```
org.apache.commons.beanutils.ConversionException: No value specified for 'BigDecimal'
```

## 解决方案

本项目提供了一个自定义的 `BigDecimalConverter` 转换器，可以优雅地处理空值和空字符串的转换。

## 文件说明

- **BigDecimalConverter.java**: 自定义 BigDecimal 转换器，处理 null 和空字符串
- **BeanUtilsConfig.java**: BeanUtils 配置类，用于注册自定义转换器
- **ExampleUsage.java**: 使用示例代码

## 使用方法

### 方法 1: 使用配置好的 BeanUtilsBean 实例（推荐）

```java
import com.example.config.BeanUtilsConfig;
import org.apache.commons.beanutils.BeanUtilsBean;

// 获取配置好的 BeanUtilsBean
BeanUtilsBean beanUtils = BeanUtilsConfig.getConfiguredBeanUtils();

// 使用它来复制属性
Map<String, Object> source = new HashMap<>();
source.put("amount", "");  // 空字符串不会抛出异常
source.put("price", null); // null 值不会抛出异常

TargetBean target = new TargetBean();
beanUtils.populate(target, source); // 安全执行，不会抛出异常
```

### 方法 2: 配置默认 BeanUtils（全局配置）

在应用启动时（如 Spring Boot 的 `@PostConstruct` 或初始化方法中）调用：

```java
import com.example.config.BeanUtilsConfig;

// 在应用启动时调用一次
BeanUtilsConfig.configureDefaultBeanUtils();

// 之后可以直接使用 BeanUtils 的静态方法
Map<String, Object> source = new HashMap<>();
source.put("amount", "");
TargetBean target = new TargetBean();
BeanUtils.populate(target, source); // 现在不会抛出异常
```

### 方法 3: 直接使用转换器

```java
import com.example.converter.BigDecimalConverter;
import java.math.BigDecimal;

BigDecimalConverter converter = new BigDecimalConverter();

// 转换空值
BigDecimal result1 = converter.convert(BigDecimal.class, null); // 返回 null

// 转换空字符串
BigDecimal result2 = converter.convert(BigDecimal.class, ""); // 返回 null

// 转换有效值
BigDecimal result3 = converter.convert(BigDecimal.class, "123.45"); // 返回 BigDecimal(123.45)
```

## 特性

- ✅ 安全处理 `null` 值
- ✅ 安全处理空字符串 `""`
- ✅ 安全处理只包含空格的字符串
- ✅ 支持标准数字字符串转换
- ✅ 支持 Number 类型自动转换
- ✅ 提供清晰的错误信息

## 依赖要求

确保项目中包含以下依赖：

```xml
<dependency>
    <groupId>commons-beanutils</groupId>
    <artifactId>commons-beanutils</artifactId>
    <version>1.9.4</version>
</dependency>
```

或 Gradle:

```gradle
implementation 'commons-beanutils:commons-beanutils:1.9.4'
```

## 注意事项

1. 如果使用 Spring Boot，建议在 `@Configuration` 类中调用 `BeanUtilsConfig.configureDefaultBeanUtils()`
2. 转换器会将空值和空字符串转换为 `null`，而不是抛出异常
3. 无效的数字字符串仍会抛出 `IllegalArgumentException`，但会提供更清晰的错误信息

## 运行示例

编译并运行示例代码：

```bash
javac -cp ".:commons-beanutils-1.9.4.jar" com/example/**/*.java
java -cp ".:commons-beanutils-1.9.4.jar" com.example.example.ExampleUsage
```
