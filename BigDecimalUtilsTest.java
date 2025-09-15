import java.math.BigDecimal;

/**
 * BigDecimalUtils测试类
 * 验证工具类的各种功能
 */
public class BigDecimalUtilsTest {
    
    public static void main(String[] args) {
        System.out.println("=== BigDecimalUtils 工具类测试 ===\n");
        
        // 测试基本运算
        testBasicOperations();
        
        // 测试比较方法
        testComparisonMethods();
        
        // 测试格式化方法
        testFormattingMethods();
        
        // 测试业务计算方法
        testBusinessCalculations();
        
        // 测试统计方法
        testStatisticalMethods();
        
        // 测试类型转换
        testTypeConversions();
    }
    
    /**
     * 测试基本运算
     */
    private static void testBasicOperations() {
        System.out.println("1. 基本运算测试:");
        
        BigDecimal a = BigDecimalUtils.of("10.5");
        BigDecimal b = BigDecimalUtils.of("3.2");
        
        System.out.println("a = " + a);
        System.out.println("b = " + b);
        System.out.println("a + b = " + BigDecimalUtils.add(a, b));
        System.out.println("a - b = " + BigDecimalUtils.subtract(a, b));
        System.out.println("a * b = " + BigDecimalUtils.multiply(a, b));
        System.out.println("a / b = " + BigDecimalUtils.divide(a, b));
        System.out.println("a / b (精度4) = " + BigDecimalUtils.divide(a, b, 4));
        System.out.println();
    }
    
    /**
     * 测试比较方法
     */
    private static void testComparisonMethods() {
        System.out.println("2. 比较方法测试:");
        
        BigDecimal a = BigDecimalUtils.of("10.5");
        BigDecimal b = BigDecimalUtils.of("3.2");
        BigDecimal c = BigDecimalUtils.of("10.5");
        
        System.out.println("a = " + a + ", b = " + b + ", c = " + c);
        System.out.println("a equals b: " + BigDecimalUtils.equals(a, b));
        System.out.println("a equals c: " + BigDecimalUtils.equals(a, c));
        System.out.println("a > b: " + BigDecimalUtils.isGreaterThan(a, b));
        System.out.println("a >= b: " + BigDecimalUtils.isGreaterThanOrEqual(a, b));
        System.out.println("a < b: " + BigDecimalUtils.isLessThan(a, b));
        System.out.println("a <= b: " + BigDecimalUtils.isLessThanOrEqual(a, b));
        System.out.println("a is positive: " + BigDecimalUtils.isPositive(a));
        System.out.println("a is zero: " + BigDecimalUtils.isZero(a));
        System.out.println();
    }
    
    /**
     * 测试格式化方法
     */
    private static void testFormattingMethods() {
        System.out.println("3. 格式化方法测试:");
        
        BigDecimal value = BigDecimalUtils.of("1234.5678");
        BigDecimal percentage = BigDecimalUtils.of("0.0525"); // 5.25%
        
        System.out.println("原值: " + value);
        System.out.println("货币格式: " + BigDecimalUtils.formatCurrency(value));
        System.out.println("千分位格式: " + BigDecimalUtils.formatWithCommas(value));
        System.out.println("百分比格式: " + BigDecimalUtils.formatPercentage(percentage));
        System.out.println("四舍五入(2位): " + BigDecimalUtils.round(value, 2));
        System.out.println("自定义格式: " + BigDecimalUtils.format(value, "#,##0.000"));
        System.out.println();
    }
    
    /**
     * 测试业务计算方法
     */
    private static void testBusinessCalculations() {
        System.out.println("4. 业务计算方法测试:");
        
        // 百分比计算
        BigDecimal value = BigDecimalUtils.of("25");
        BigDecimal total = BigDecimalUtils.of("200");
        System.out.println("百分比计算: " + value + "/" + total + " = " + 
                         BigDecimalUtils.percentage(value, total) + "%");
        
        // 折扣计算
        BigDecimal originalPrice = BigDecimalUtils.of("1000");
        BigDecimal discountRate = BigDecimalUtils.of("0.15"); // 15%折扣
        System.out.println("折扣计算: 原价" + originalPrice + ", 折扣率" + 
                         BigDecimalUtils.formatPercentage(discountRate) + 
                         ", 折后价: " + BigDecimalUtils.calculateDiscount(originalPrice, discountRate));
        
        // 利息计算
        BigDecimal principal = BigDecimalUtils.of("10000");
        BigDecimal rate = BigDecimalUtils.of("0.05"); // 5%年利率
        BigDecimal time = BigDecimalUtils.of("2"); // 2年
        System.out.println("利息计算: 本金" + principal + ", 利率" + 
                         BigDecimalUtils.formatPercentage(rate) + 
                         ", 时间" + time + "年, 利息: " + 
                         BigDecimalUtils.calculateInterest(principal, rate, time));
        System.out.println();
    }
    
    /**
     * 测试统计方法
     */
    private static void testStatisticalMethods() {
        System.out.println("5. 统计方法测试:");
        
        BigDecimal[] values = {
            BigDecimalUtils.of("10.5"),
            BigDecimalUtils.of("20.3"),
            BigDecimalUtils.of("15.7"),
            BigDecimalUtils.of("8.9"),
            BigDecimalUtils.of("12.1")
        };
        
        System.out.print("数值数组: ");
        for (BigDecimal value : values) {
            System.out.print(value + " ");
        }
        System.out.println();
        
        System.out.println("最大值: " + BigDecimalUtils.max(values));
        System.out.println("最小值: " + BigDecimalUtils.min(values));
        System.out.println("平均值: " + BigDecimalUtils.average(values));
        System.out.println("总和: " + BigDecimalUtils.sum(values));
        System.out.println();
    }
    
    /**
     * 测试类型转换
     */
    private static void testTypeConversions() {
        System.out.println("6. 类型转换测试:");
        
        BigDecimal value = BigDecimalUtils.of("123.456");
        
        System.out.println("原值: " + value);
        System.out.println("转字符串: " + BigDecimalUtils.toString(value));
        System.out.println("转double: " + BigDecimalUtils.toDouble(value));
        System.out.println("转int: " + BigDecimalUtils.toInt(value));
        System.out.println("转long: " + BigDecimalUtils.toLong(value));
        System.out.println("绝对值: " + BigDecimalUtils.abs(value));
        System.out.println("相反数: " + BigDecimalUtils.negate(value));
        System.out.println();
    }
    
    /**
     * 测试空值处理
     */
    private static void testNullHandling() {
        System.out.println("7. 空值处理测试:");
        
        BigDecimal nullValue = null;
        BigDecimal normalValue = BigDecimalUtils.of("100");
        
        System.out.println("null + 100 = " + BigDecimalUtils.add(nullValue, normalValue));
        System.out.println("100 + null = " + BigDecimalUtils.add(normalValue, nullValue));
        System.out.println("null * 100 = " + BigDecimalUtils.multiply(nullValue, normalValue));
        System.out.println("null is zero: " + BigDecimalUtils.isZero(nullValue));
        System.out.println("null is positive: " + BigDecimalUtils.isPositive(nullValue));
        System.out.println();
    }
}