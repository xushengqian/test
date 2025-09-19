package com.example.aviator;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.function.FunctionUtils;
import com.googlecode.aviator.runtime.type.AviatorDouble;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorString;

import java.math.BigDecimal;
import java.util.*;

/**
 * AviatorScript 表达式求值引擎示例
 * 
 * @author Generated
 * @version 1.0
 */
public class AviatorScriptDemo {
    
    public static void main(String[] args) {
        System.out.println("=== AviatorScript 表达式求值引擎示例 ===\n");
        
        // 基本表达式求值
        basicExpressionEvaluation();
        
        // 变量和函数使用
        variableAndFunctionUsage();
        
        // 复杂业务规则
        complexBusinessRules();
        
        // 自定义函数
        customFunctionExample();
        
        // 性能测试
        performanceTest();
        
        // 集合操作
        collectionOperations();
        
        // 正则表达式
        regexOperations();
    }
    
    /**
     * 基本表达式求值
     */
    private static void basicExpressionEvaluation() {
        System.out.println("1. 基本表达式求值:");
        
        // 数学运算
        System.out.println("数学运算:");
        System.out.println("2 + 3 * 4 = " + AviatorEvaluator.execute("2 + 3 * 4"));
        System.out.println("(2 + 3) * 4 = " + AviatorEvaluator.execute("(2 + 3) * 4"));
        System.out.println("10 / 3 = " + AviatorEvaluator.execute("10 / 3"));
        System.out.println("10 % 3 = " + AviatorEvaluator.execute("10 % 3"));
        System.out.println("2 ^ 3 = " + AviatorEvaluator.execute("2 ^ 3")); // 幂运算
        
        // 逻辑运算
        System.out.println("\n逻辑运算:");
        System.out.println("true && false = " + AviatorEvaluator.execute("true && false"));
        System.out.println("true || false = " + AviatorEvaluator.execute("true || false"));
        System.out.println("!true = " + AviatorEvaluator.execute("!true"));
        
        // 比较运算
        System.out.println("\n比较运算:");
        System.out.println("5 > 3 = " + AviatorEvaluator.execute("5 > 3"));
        System.out.println("5 >= 5 = " + AviatorEvaluator.execute("5 >= 5"));
        System.out.println("5 == 5 = " + AviatorEvaluator.execute("5 == 5"));
        System.out.println("5 != 3 = " + AviatorEvaluator.execute("5 != 3"));
        
        // 字符串操作
        System.out.println("\n字符串操作:");
        System.out.println("'Hello' + ' ' + 'World' = " + AviatorEvaluator.execute("'Hello' + ' ' + 'World'"));
        System.out.println("string.length('Hello') = " + AviatorEvaluator.execute("string.length('Hello')"));
        System.out.println("string.substring('Hello', 0, 3) = " + AviatorEvaluator.execute("string.substring('Hello', 0, 3)"));
        
        System.out.println();
    }
    
    /**
     * 变量和函数使用
     */
    private static void variableAndFunctionUsage() {
        System.out.println("2. 变量和函数使用:");
        
        // 创建变量环境
        Map<String, Object> env = new HashMap<>();
        env.put("name", "张三");
        env.put("age", 25);
        env.put("salary", 8000.0);
        env.put("isMarried", false);
        
        // 使用变量
        System.out.println("变量使用:");
        System.out.println("name = " + AviatorEvaluator.execute("name", env));
        System.out.println("age = " + AviatorEvaluator.execute("age", env));
        System.out.println("salary = " + AviatorEvaluator.execute("salary", env));
        
        // 条件表达式
        System.out.println("\n条件表达式:");
        System.out.println("age >= 18 ? '成年人' : '未成年人' = " + 
                         AviatorEvaluator.execute("age >= 18 ? '成年人' : '未成年人'", env));
        
        // 数学函数
        System.out.println("\n数学函数:");
        System.out.println("math.abs(-5) = " + AviatorEvaluator.execute("math.abs(-5)"));
        System.out.println("max(10, 20) = " + AviatorEvaluator.execute("max(10, 20)"));
        System.out.println("min(10, 20) = " + AviatorEvaluator.execute("min(10, 20)"));
        System.out.println("math.round(3.14159) = " + AviatorEvaluator.execute("math.round(3.14159)"));
        System.out.println("math.ceil(3.2) = " + AviatorEvaluator.execute("math.ceil(3.2)"));
        System.out.println("math.floor(3.8) = " + AviatorEvaluator.execute("math.floor(3.8)"));
        System.out.println("math.sqrt(16) = " + AviatorEvaluator.execute("math.sqrt(16)"));
        System.out.println("math.pow(2, 3) = " + AviatorEvaluator.execute("math.pow(2, 3)"));
        
        // 字符串函数
        System.out.println("\n字符串函数:");
        System.out.println("string.length('Hello') = " + AviatorEvaluator.execute("string.length('Hello')"));
        System.out.println("string.substring('Hello World', 0, 5) = " + 
                         AviatorEvaluator.execute("string.substring('Hello World', 0, 5)"));
        System.out.println("string.indexOf('Hello World', 'World') = " + 
                         AviatorEvaluator.execute("string.indexOf('Hello World', 'World')"));
        System.out.println("string.replace('Hello World', 'World', 'Aviator') = " + 
                         AviatorEvaluator.execute("string.replace('Hello World', 'World', 'Aviator')"));
        System.out.println("string.upperCase('hello') = " + AviatorEvaluator.execute("string.upperCase('hello')"));
        System.out.println("string.lowerCase('HELLO') = " + AviatorEvaluator.execute("string.lowerCase('HELLO')"));
        
        System.out.println();
    }
    
    /**
     * 复杂业务规则
     */
    private static void complexBusinessRules() {
        System.out.println("3. 复杂业务规则:");
        
        // 员工薪资计算规则
        Map<String, Object> employeeEnv = new HashMap<>();
        employeeEnv.put("baseSalary", 5000.0);
        employeeEnv.put("yearsOfService", 3);
        employeeEnv.put("performanceScore", 85);
        employeeEnv.put("hasOvertime", true);
        employeeEnv.put("overtimeHours", 20);
        
        // 薪资计算公式
        String salaryFormula = 
            "baseSalary + " +
            "(yearsOfService * 500) + " +  // 工龄津贴
            "(performanceScore - 60) * 50 + " +  // 绩效奖金
            "(hasOvertime ? overtimeHours * 50 : 0)";  // 加班费
        
        System.out.println("员工薪资计算:");
        System.out.println("基本工资: " + employeeEnv.get("baseSalary"));
        System.out.println("工龄: " + employeeEnv.get("yearsOfService") + "年");
        System.out.println("绩效分数: " + employeeEnv.get("performanceScore"));
        System.out.println("加班小时: " + employeeEnv.get("overtimeHours"));
        System.out.println("计算后薪资: " + AviatorEvaluator.execute(salaryFormula, employeeEnv));
        
        // 贷款审批规则
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
        
        System.out.println("\n贷款审批规则:");
        System.out.println("月收入: " + loanEnv.get("monthlyIncome"));
        System.out.println("月支出: " + loanEnv.get("monthlyExpense"));
        System.out.println("信用分数: " + loanEnv.get("creditScore"));
        System.out.println("贷款金额: " + loanEnv.get("loanAmount"));
        System.out.println("审批结果: " + ((Boolean)AviatorEvaluator.execute(loanApprovalRule, loanEnv) ? "通过" : "拒绝"));
        
        // 商品折扣规则
        Map<String, Object> productEnv = new HashMap<>();
        productEnv.put("originalPrice", 1000.0);
        productEnv.put("memberLevel", "VIP");
        productEnv.put("quantity", 3);
        productEnv.put("isHoliday", true);
        
        String discountFormula = 
            "originalPrice * " +
            "(memberLevel == 'VIP' ? 0.8 : " +
            "(memberLevel == 'Gold' ? 0.85 : 0.9)) * " +
            "(quantity >= 3 ? 0.95 : 1.0) * " +
            "(isHoliday ? 0.9 : 1.0)";
        
        System.out.println("\n商品折扣计算:");
        System.out.println("原价: " + productEnv.get("originalPrice"));
        System.out.println("会员等级: " + productEnv.get("memberLevel"));
        System.out.println("购买数量: " + productEnv.get("quantity"));
        System.out.println("是否节假日: " + productEnv.get("isHoliday"));
        System.out.println("最终价格: " + AviatorEvaluator.execute(discountFormula, productEnv));
        
        System.out.println();
    }
    
    /**
     * 自定义函数示例
     */
    private static void customFunctionExample() {
        System.out.println("4. 自定义函数示例:");
        
        // 注册自定义函数
        AviatorEvaluator.addFunction(new CalculateTaxFunction());
        AviatorEvaluator.addFunction(new FormatCurrencyFunction());
        AviatorEvaluator.addFunction(new CalculateDistanceFunction());
        
        // 使用自定义函数
        Map<String, Object> env = new HashMap<>();
        env.put("income", 10000.0);
        env.put("amount", 1234.56);
        env.put("lat1", 39.9042);
        env.put("lon1", 116.4074);
        env.put("lat2", 31.2304);
        env.put("lon2", 121.4737);
        
        System.out.println("自定义函数使用:");
        System.out.println("calculateTax(10000) = " + AviatorEvaluator.execute("calculateTax(income)", env));
        System.out.println("formatCurrency(1234.56) = " + AviatorEvaluator.execute("formatCurrency(amount)", env));
        System.out.println("calculateDistance(39.9042, 116.4074, 31.2304, 121.4737) = " + 
                         AviatorEvaluator.execute("calculateDistance(lat1, lon1, lat2, lon2)", env) + " km");
        
        System.out.println();
    }
    
    /**
     * 性能测试
     */
    private static void performanceTest() {
        System.out.println("5. 性能测试:");
        
        String expression = "a + b * c - d / e";
        Map<String, Object> env = new HashMap<>();
        env.put("a", 10.0);
        env.put("b", 20.0);
        env.put("c", 30.0);
        env.put("d", 40.0);
        env.put("e", 5.0);
        
        // 编译表达式
        Expression compiledExp = AviatorEvaluator.compile(expression);
        
        int iterations = 100000;
        
        // 测试直接执行
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            AviatorEvaluator.execute(expression, env);
        }
        long directTime = System.currentTimeMillis() - startTime;
        
        // 测试编译后执行
        startTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            compiledExp.execute(env);
        }
        long compiledTime = System.currentTimeMillis() - startTime;
        
        System.out.println("执行次数: " + iterations);
        System.out.println("直接执行耗时: " + directTime + "ms");
        System.out.println("编译后执行耗时: " + compiledTime + "ms");
        System.out.println("性能提升: " + String.format("%.2f", (double)directTime / compiledTime) + "倍");
        
        System.out.println();
    }
    
    /**
     * 集合操作
     */
    private static void collectionOperations() {
        System.out.println("6. 集合操作:");
        
        Map<String, Object> env = new HashMap<>();
        
        // 数组操作
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
        env.put("numbers", numbers);
        
        System.out.println("数组操作:");
        System.out.println("numbers = " + numbers);
        System.out.println("numbers[0] = " + AviatorEvaluator.execute("numbers[0]", env));
        System.out.println("numbers.length = " + AviatorEvaluator.execute("numbers.length", env));
        System.out.println("numbers[1..3] = " + AviatorEvaluator.execute("numbers[1..3]", env));
        
        // Map操作
        Map<String, Object> person = new HashMap<>();
        person.put("name", "李四");
        person.put("age", 30);
        person.put("city", "北京");
        env.put("person", person);
        
        System.out.println("\nMap操作:");
        System.out.println("person = " + person);
        System.out.println("person.name = " + AviatorEvaluator.execute("person.name", env));
        System.out.println("person.age = " + AviatorEvaluator.execute("person.age", env));
        System.out.println("person.city = " + AviatorEvaluator.execute("person.city", env));
        
        // 集合函数
        System.out.println("\n集合函数:");
        System.out.println("seq.array(1,2,3,4,5) = " + AviatorEvaluator.execute("seq.array(1,2,3,4,5)"));
        System.out.println("seq.list(1,2,3,4,5) = " + AviatorEvaluator.execute("seq.list(1,2,3,4,5)"));
        System.out.println("seq.range(1, 10) = " + AviatorEvaluator.execute("seq.range(1, 10)"));
        System.out.println("seq.range(1, 10, 2) = " + AviatorEvaluator.execute("seq.range(1, 10, 2)"));
        
        System.out.println();
    }
    
    /**
     * 正则表达式操作
     */
    private static void regexOperations() {
        System.out.println("7. 正则表达式操作:");
        
        Map<String, Object> env = new HashMap<>();
        env.put("email", "user@example.com");
        env.put("phone", "13812345678");
        env.put("text", "Hello World 123");
        
        System.out.println("正则表达式匹配:");
        System.out.println("email = " + env.get("email"));
        System.out.println("email =~ /^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$/ = " + 
                         AviatorEvaluator.execute("email =~ /^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$/", env));
        
        System.out.println("\nphone = " + env.get("phone"));
        System.out.println("phone =~ /^1[3-9]\\d{9}$/ = " + 
                         AviatorEvaluator.execute("phone =~ /^1[3-9]\\d{9}$/", env));
        
        System.out.println("\ntext = " + env.get("text"));
        System.out.println("text =~ /\\d+/ = " + AviatorEvaluator.execute("text =~ /\\d+/", env));
        
        // 字符串替换
        System.out.println("\n字符串替换:");
        System.out.println("string.replaceAll('Hello 123 World 456', /\\d+/, 'XXX') = " + 
                         AviatorEvaluator.execute("string.replaceAll('Hello 123 World 456', /\\d+/, 'XXX')"));
        
        System.out.println();
    }
    
    /**
     * 自定义函数：计算税费
     */
    public static class CalculateTaxFunction extends AbstractFunction {
        @Override
        public String getName() {
            return "calculateTax";
        }
        
        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
            double income = FunctionUtils.getNumberValue(arg1, env).doubleValue();
            double tax = 0;
            
            if (income <= 3000) {
                tax = 0;
            } else if (income <= 12000) {
                tax = (income - 3000) * 0.1;
            } else if (income <= 25000) {
                tax = 900 + (income - 12000) * 0.2;
            } else if (income <= 35000) {
                tax = 900 + 2600 + (income - 25000) * 0.25;
            } else if (income <= 55000) {
                tax = 900 + 2600 + 2500 + (income - 35000) * 0.3;
            } else if (income <= 80000) {
                tax = 900 + 2600 + 2500 + 6000 + (income - 55000) * 0.35;
            } else {
                tax = 900 + 2600 + 2500 + 6000 + 8750 + (income - 80000) * 0.45;
            }
            
            return new AviatorDouble(tax);
        }
    }
    
    /**
     * 自定义函数：格式化货币
     */
    public static class FormatCurrencyFunction extends AbstractFunction {
        @Override
        public String getName() {
            return "formatCurrency";
        }
        
        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
            double amount = FunctionUtils.getNumberValue(arg1, env).doubleValue();
            return new AviatorString(String.format("¥%.2f", amount));
        }
    }
    
    /**
     * 自定义函数：计算两点间距离
     */
    public static class CalculateDistanceFunction extends AbstractFunction {
        @Override
        public String getName() {
            return "calculateDistance";
        }
        
        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1, AviatorObject arg2, 
                                 AviatorObject arg3, AviatorObject arg4) {
            double lat1 = FunctionUtils.getNumberValue(arg1, env).doubleValue();
            double lon1 = FunctionUtils.getNumberValue(arg2, env).doubleValue();
            double lat2 = FunctionUtils.getNumberValue(arg3, env).doubleValue();
            double lon2 = FunctionUtils.getNumberValue(arg4, env).doubleValue();
            
            // 使用Haversine公式计算距离
            double R = 6371; // 地球半径（公里）
            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);
            double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                      Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                      Math.sin(dLon/2) * Math.sin(dLon/2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
            double distance = R * c;
            
            return new AviatorDouble(distance);
        }
    }
}