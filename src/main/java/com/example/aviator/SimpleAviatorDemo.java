package com.example.aviator;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.function.FunctionUtils;
import com.googlecode.aviator.runtime.type.AviatorDouble;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorString;

import java.util.*;

/**
 * AviatorScript 简化示例
 * 展示核心功能和使用方法
 */
public class SimpleAviatorDemo {
    
    public static void main(String[] args) {
        System.out.println("=== AviatorScript 简化示例 ===\n");
        
        // 基本表达式求值
        basicExpressions();
        
        // 变量使用
        variableUsage();
        
        // 业务规则示例
        businessRules();
        
        // 自定义函数
        customFunctions();
        
        // 性能测试
        performanceTest();
    }
    
    /**
     * 基本表达式求值
     */
    private static void basicExpressions() {
        System.out.println("1. 基本表达式求值:");
        
        // 数学运算
        System.out.println("数学运算:");
        System.out.println("2 + 3 * 4 = " + AviatorEvaluator.execute("2 + 3 * 4"));
        System.out.println("(2 + 3) * 4 = " + AviatorEvaluator.execute("(2 + 3) * 4"));
        System.out.println("10 / 3 = " + AviatorEvaluator.execute("10 / 3"));
        System.out.println("10 % 3 = " + AviatorEvaluator.execute("10 % 3"));
        System.out.println("2 ^ 3 = " + AviatorEvaluator.execute("2 ^ 3"));
        
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
        System.out.println("string.substring('Hello World', 0, 5) = " + AviatorEvaluator.execute("string.substring('Hello World', 0, 5)"));
        
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
        
        System.out.println();
    }
    
    /**
     * 变量使用
     */
    private static void variableUsage() {
        System.out.println("2. 变量使用:");
        
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
        
        // 复杂表达式
        System.out.println("\n复杂表达式:");
        System.out.println("salary > 5000 && age >= 18 ? '高收入成年人' : '其他' = " + 
                         AviatorEvaluator.execute("salary > 5000 && age >= 18 ? '高收入成年人' : '其他'", env));
        
        System.out.println();
    }
    
    /**
     * 业务规则示例
     */
    private static void businessRules() {
        System.out.println("3. 业务规则示例:");
        
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
    private static void customFunctions() {
        System.out.println("4. 自定义函数示例:");
        
        // 注册自定义函数
        AviatorEvaluator.addFunction(new CalculateTaxFunction());
        AviatorEvaluator.addFunction(new FormatCurrencyFunction());
        
        // 使用自定义函数
        Map<String, Object> env = new HashMap<>();
        env.put("income", 10000.0);
        env.put("amount", 1234.56);
        
        System.out.println("自定义函数使用:");
        System.out.println("calculateTax(10000) = " + AviatorEvaluator.execute("calculateTax(income)", env));
        System.out.println("formatCurrency(1234.56) = " + AviatorEvaluator.execute("formatCurrency(amount)", env));
        
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
}