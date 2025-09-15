# AviatorScript 除数为0错误处理解决方案

## 问题描述

在使用 AviatorScript 执行脚本时，如果进行除法运算时除数为0，会导致运行时错误。这是一个常见的数学运算异常，需要妥善处理以确保脚本的稳定运行。

## 解决方案

本项目提供了多种处理 AviatorScript 除数为0错误的方法：

### 1. 基本防护检查

```aviatorscript
## 在执行除法前检查除数
if (b != 0) {
    result = a / b;
} else {
    result = "错误：除数不能为0";
}
```

### 2. 使用安全除法函数

```aviatorscript
function safeDivide(a, b) {
    if (b == 0) {
        return "错误：除数为0";
    }
    return a / b;
}
```

### 3. 异常处理

```aviatorscript
function safeDivideWithException(a, b) {
    if (b == 0) {
        throw "除数为0异常：不能进行除法运算";
    }
    return a / b;
}
```

## 文件说明

- `division_by_zero_example.av` - 基础示例和测试用例
- `safe_math_utils.av` - 完整的安全数学运算工具库
- `README.md` - 使用说明和最佳实践

## 运行示例

### 运行基础示例
```bash
# 如果有 AviatorScript 运行环境
java -jar aviatorscript.jar division_by_zero_example.av
```

### 运行工具库示例
```bash
java -jar aviatorscript.jar safe_math_utils.av
```

## 最佳实践

### 1. 始终检查除数
在进行任何除法运算前，都应该检查除数是否为0：

```aviatorscript
## 推荐做法
if (denominator != 0) {
    result = numerator / denominator;
} else {
    ## 处理除数为0的情况
    result = defaultValue;  ## 或者抛出异常
}
```

### 2. 使用类型检查
确保参与运算的变量是数值类型：

```aviatorscript
function validateNumber(value) {
    return type(value) == "long" || type(value) == "double" || type(value) == "decimal";
}

if (validateNumber(a) && validateNumber(b) && b != 0) {
    result = a / b;
}
```

### 3. 处理浮点数精度问题
对于浮点数运算，考虑精度问题：

```aviatorscript
function floatEquals(a, b, epsilon) {
    if (epsilon == nil) {
        epsilon = 1e-10;
    }
    return math.abs(a - b) < epsilon;
}
```

### 4. 使用高精度计算
对于需要高精度的计算，使用 `decimal` 类型：

```aviatorscript
function safeDecimalDivide(a, b) {
    if (b == 0) {
        return decimal("0");
    }
    return decimal(a) / decimal(b);
}
```

## 常见错误场景

### 1. 直接除法运算
```aviatorscript
## 错误做法
result = a / b;  ## 如果b为0会报错
```

### 2. 数组元素除法
```aviatorscript
## 需要检查数组元素
for (i in array) {
    if (array[i] != 0) {
        result = total / array[i];
    }
}
```

### 3. 动态计算的除数
```aviatorscript
## 动态计算可能为0的情况
let divisor = calculateDivisor();  ## 可能返回0
if (divisor != 0) {
    result = dividend / divisor;
}
```

## 错误处理策略

### 1. 返回默认值
适合不需要中断流程的场景：
```aviatorscript
result = b != 0 ? a / b : 0;
```

### 2. 返回错误信息
适合需要明确错误提示的场景：
```aviatorscript
result = b != 0 ? a / b : "除数为0错误";
```

### 3. 抛出异常
适合需要中断执行的场景：
```aviatorscript
if (b == 0) {
    throw "除数为0异常";
}
```

### 4. 返回特殊值
适合数学计算的场景：
```aviatorscript
result = b != 0 ? a / b : double("NaN");
```

## 测试建议

1. **边界值测试**：测试除数为0的情况
2. **类型测试**：测试非数值类型的处理
3. **精度测试**：测试浮点数运算的精度
4. **批量测试**：测试批量运算中的异常处理

## 注意事项

1. AviatorScript 遵循 Java 的数学运算规则
2. 整数除法结果仍然是整数
3. 使用 `decimal` 类型可以获得更高的精度
4. 异常处理要符合业务逻辑需求
5. 考虑性能影响，避免过度检查

## 扩展功能

本项目还提供了：
- 安全的取模运算
- 安全的幂运算
- 安全的对数运算
- 批量安全运算
- 统计运算工具
- 数值验证工具

这些工具可以帮助您构建更加健壮的 AviatorScript 应用程序。