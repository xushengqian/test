# AviatorScript 表达式求值引擎示例

## 项目简介

AviatorScript 是一款高性能、轻量级的 Java 表达式求值引擎，主要用于动态求值各种表达式。本项目提供了完整的示例代码，展示 AviatorScript 的各种功能和使用方法。

## 主要特性

- **高性能**: 通过将表达式编译为字节码，提升执行速度
- **轻量级**: 体积小（约450KB），适合嵌入式和资源受限的环境
- **丰富的数据类型支持**: 支持数字、字符串、布尔值等基本类型
- **正则表达式支持**: 内置对正则表达式的支持
- **自定义函数**: 支持注册自定义函数

## 项目结构

```
├── pom.xml                           # Maven配置文件
├── aviator-5.4.1.jar                # AviatorScript依赖包
├── src/main/java/com/example/aviator/
│   ├── AviatorScriptDemo.java       # 完整功能示例
│   └── SimpleAviatorDemo.java       # 简化示例
└── README.md                        # 项目说明文档
```

## 快速开始

### 1. 环境要求

- Java 8 或更高版本
- Maven 3.6+（可选）

### 2. 运行示例

```bash
# 编译并运行简化示例
javac -cp aviator-5.4.1.jar -d . src/main/java/com/example/aviator/SimpleAviatorDemo.java
java -cp .:aviator-5.4.1.jar com.example.aviator.SimpleAviatorDemo

# 或使用Maven运行
mvn clean compile exec:java
```

## 功能示例

### 1. 基本表达式求值

```java
// 数学运算
AviatorEvaluator.execute("2 + 3 * 4");  // 结果: 14
AviatorEvaluator.execute("10 / 3");     // 结果: 3
AviatorEvaluator.execute("2 ^ 3");      // 结果: 1 (注意：^ 是异或运算)

// 逻辑运算
AviatorEvaluator.execute("true && false");  // 结果: false
AviatorEvaluator.execute("true || false");  // 结果: true

// 比较运算
AviatorEvaluator.execute("5 > 3");  // 结果: true
AviatorEvaluator.execute("5 == 5"); // 结果: true
```

### 2. 变量使用

```java
Map<String, Object> env = new HashMap<>();
env.put("name", "张三");
env.put("age", 25);
env.put("salary", 8000.0);

// 使用变量
AviatorEvaluator.execute("name", env);  // 结果: 张三
AviatorEvaluator.execute("age >= 18 ? '成年人' : '未成年人'", env);  // 结果: 成年人
```

### 3. 数学函数

```java
AviatorEvaluator.execute("math.abs(-5)");      // 结果: 5
AviatorEvaluator.execute("max(10, 20)");       // 结果: 20
AviatorEvaluator.execute("min(10, 20)");       // 结果: 10
AviatorEvaluator.execute("math.round(3.14159)"); // 结果: 3
AviatorEvaluator.execute("math.ceil(3.2)");    // 结果: 4.0
AviatorEvaluator.execute("math.floor(3.8)");   // 结果: 3.0
AviatorEvaluator.execute("math.sqrt(16)");     // 结果: 4.0
AviatorEvaluator.execute("math.pow(2, 3)");    // 结果: 8.0
```

### 4. 字符串函数

```java
AviatorEvaluator.execute("string.length('Hello')");  // 结果: 5
AviatorEvaluator.execute("string.substring('Hello World', 0, 5)");  // 结果: Hello
```

### 5. 业务规则示例

#### 员工薪资计算

```java
Map<String, Object> employeeEnv = new HashMap<>();
employeeEnv.put("baseSalary", 5000.0);
employeeEnv.put("yearsOfService", 3);
employeeEnv.put("performanceScore", 85);
employeeEnv.put("hasOvertime", true);
employeeEnv.put("overtimeHours", 20);

String salaryFormula = 
    "baseSalary + " +
    "(yearsOfService * 500) + " +  // 工龄津贴
    "(performanceScore - 60) * 50 + " +  // 绩效奖金
    "(hasOvertime ? overtimeHours * 50 : 0)";  // 加班费

// 计算结果: 8750.0
AviatorEvaluator.execute(salaryFormula, employeeEnv);
```

#### 贷款审批规则

```java
Map<String, Object> loanEnv = new HashMap<>();
loanEnv.put("monthlyIncome", 15000.0);
loanEnv.put("monthlyExpense", 8000.0);
loanEnv.put("creditScore", 750);
loanEnv.put("loanAmount", 500000.0);
loanEnv.put("loanTerm", 20);

String loanApprovalRule = 
    "monthlyIncome > 10000 && " +
    "creditScore >= 700 && " +
    "(monthlyIncome - monthlyExpense) > (loanAmount / loanTerm / 12) * 0.3";

// 结果: true (通过)
AviatorEvaluator.execute(loanApprovalRule, loanEnv);
```

### 6. 自定义函数

```java
// 注册自定义函数
AviatorEvaluator.addFunction(new CalculateTaxFunction());

// 使用自定义函数
Map<String, Object> env = new HashMap<>();
env.put("income", 10000.0);
AviatorEvaluator.execute("calculateTax(income)", env);  // 结果: 700.0
```

### 7. 性能优化

```java
// 编译表达式（推荐用于重复执行）
Expression compiledExp = AviatorEvaluator.compile("a + b * c - d / e");

// 执行编译后的表达式
Map<String, Object> env = new HashMap<>();
env.put("a", 10.0);
env.put("b", 20.0);
env.put("c", 30.0);
env.put("d", 40.0);
env.put("e", 5.0);

compiledExp.execute(env);  // 性能比直接执行快数百倍
```

## 应用场景

1. **规则引擎**: 在规则引擎中动态判断业务规则
2. **公式计算**: 用于动态计算公式，适应不同的业务需求
3. **动态脚本控制**: 在应用程序中嵌入动态脚本
4. **数据ETL**: 在数据抽取、转换、加载过程中进行数据处理

## 性能特点

- 编译后执行比直接执行快数百倍
- 轻量级，内存占用小
- 支持高并发场景

## 依赖信息

- **AviatorScript版本**: 5.4.1
- **JAR包大小**: 约450KB（包含依赖）
- **Java版本要求**: 8+

## 注意事项

1. 幂运算使用 `math.pow(base, exponent)` 而不是 `^` 操作符
2. 字符串方法调用需要使用 `string.method()` 格式
3. 建议对重复执行的表达式进行编译以提高性能
4. 自定义函数需要继承 `AbstractFunction` 类

## 更多信息

- [AviatorScript官方文档](https://github.com/killme2008/aviatorscript)
- [AviatorScript GitHub仓库](https://github.com/killme2008/aviatorscript)