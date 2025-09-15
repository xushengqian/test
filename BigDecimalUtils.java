import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Objects;

/**
 * BigDecimal工具类
 * 提供常用的数值计算、比较、格式化等功能
 * 
 * @author Generated
 * @version 1.0
 */
public class BigDecimalUtils {
    
    // 默认精度
    private static final int DEFAULT_SCALE = 2;
    
    // 常用常量
    public static final BigDecimal ZERO = BigDecimal.ZERO;
    public static final BigDecimal ONE = BigDecimal.ONE;
    public static final BigDecimal TEN = BigDecimal.TEN;
    public static final BigDecimal HUNDRED = new BigDecimal("100");
    public static final BigDecimal THOUSAND = new BigDecimal("1000");
    
    // 默认舍入模式
    private static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP;
    
    /**
     * 私有构造函数，防止实例化
     */
    private BigDecimalUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * 创建BigDecimal对象
     * 
     * @param value 数值
     * @return BigDecimal对象
     */
    public static BigDecimal of(String value) {
        return new BigDecimal(value);
    }
    
    /**
     * 创建BigDecimal对象
     * 
     * @param value 数值
     * @return BigDecimal对象
     */
    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value);
    }
    
    /**
     * 创建BigDecimal对象
     * 
     * @param value 数值
     * @return BigDecimal对象
     */
    public static BigDecimal of(int value) {
        return new BigDecimal(value);
    }
    
    /**
     * 创建BigDecimal对象
     * 
     * @param value 数值
     * @return BigDecimal对象
     */
    public static BigDecimal of(long value) {
        return new BigDecimal(value);
    }
    
    /**
     * 安全加法运算
     * 
     * @param a 加数
     * @param b 被加数
     * @return 和
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        if (a == null) a = ZERO;
        if (b == null) b = ZERO;
        return a.add(b);
    }
    
    /**
     * 安全减法运算
     * 
     * @param a 被减数
     * @param b 减数
     * @return 差
     */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        if (a == null) a = ZERO;
        if (b == null) b = ZERO;
        return a.subtract(b);
    }
    
    /**
     * 安全乘法运算
     * 
     * @param a 乘数
     * @param b 被乘数
     * @return 积
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return ZERO;
        return a.multiply(b);
    }
    
    /**
     * 安全除法运算
     * 
     * @param a 被除数
     * @param b 除数
     * @return 商
     */
    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        return divide(a, b, DEFAULT_SCALE, DEFAULT_ROUNDING_MODE);
    }
    
    /**
     * 安全除法运算（指定精度）
     * 
     * @param a 被除数
     * @param b 除数
     * @param scale 精度
     * @return 商
     */
    public static BigDecimal divide(BigDecimal a, BigDecimal b, int scale) {
        return divide(a, b, scale, DEFAULT_ROUNDING_MODE);
    }
    
    /**
     * 安全除法运算（指定精度和舍入模式）
     * 
     * @param a 被除数
     * @param b 除数
     * @param scale 精度
     * @param roundingMode 舍入模式
     * @return 商
     */
    public static BigDecimal divide(BigDecimal a, BigDecimal b, int scale, RoundingMode roundingMode) {
        if (a == null) a = ZERO;
        if (b == null || b.compareTo(ZERO) == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return a.divide(b, scale, roundingMode);
    }
    
    /**
     * 计算百分比
     * 
     * @param value 数值
     * @param total 总数
     * @return 百分比
     */
    public static BigDecimal percentage(BigDecimal value, BigDecimal total) {
        if (total == null || total.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return multiply(divide(value, total, 4), HUNDRED);
    }
    
    /**
     * 计算折扣
     * 
     * @param originalPrice 原价
     * @param discountRate 折扣率（0-1之间的小数）
     * @return 折扣后价格
     */
    public static BigDecimal calculateDiscount(BigDecimal originalPrice, BigDecimal discountRate) {
        if (originalPrice == null || discountRate == null) return ZERO;
        return multiply(originalPrice, subtract(ONE, discountRate));
    }
    
    /**
     * 计算利息
     * 
     * @param principal 本金
     * @param rate 利率（年利率，如0.05表示5%）
     * @param time 时间（年）
     * @return 利息
     */
    public static BigDecimal calculateInterest(BigDecimal principal, BigDecimal rate, BigDecimal time) {
        if (principal == null || rate == null || time == null) return ZERO;
        return multiply(multiply(principal, rate), time);
    }
    
    /**
     * 比较两个数值大小
     * 
     * @param a 数值a
     * @param b 数值b
     * @return 比较结果：-1(a<b), 0(a=b), 1(a>b)
     */
    public static int compare(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }
    
    /**
     * 判断是否相等
     * 
     * @param a 数值a
     * @param b 数值b
     * @return 是否相等
     */
    public static boolean equals(BigDecimal a, BigDecimal b) {
        return compare(a, b) == 0;
    }
    
    /**
     * 判断a是否大于b
     * 
     * @param a 数值a
     * @param b 数值b
     * @return a是否大于b
     */
    public static boolean isGreaterThan(BigDecimal a, BigDecimal b) {
        return compare(a, b) > 0;
    }
    
    /**
     * 判断a是否大于等于b
     * 
     * @param a 数值a
     * @param b 数值b
     * @return a是否大于等于b
     */
    public static boolean isGreaterThanOrEqual(BigDecimal a, BigDecimal b) {
        return compare(a, b) >= 0;
    }
    
    /**
     * 判断a是否小于b
     * 
     * @param a 数值a
     * @param b 数值b
     * @return a是否小于b
     */
    public static boolean isLessThan(BigDecimal a, BigDecimal b) {
        return compare(a, b) < 0;
    }
    
    /**
     * 判断a是否小于等于b
     * 
     * @param a 数值a
     * @param b 数值b
     * @return a是否小于等于b
     */
    public static boolean isLessThanOrEqual(BigDecimal a, BigDecimal b) {
        return compare(a, b) <= 0;
    }
    
    /**
     * 判断是否为正数
     * 
     * @param value 数值
     * @return 是否为正数
     */
    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(ZERO) > 0;
    }
    
    /**
     * 判断是否为负数
     * 
     * @param value 数值
     * @return 是否为负数
     */
    public static boolean isNegative(BigDecimal value) {
        return value != null && value.compareTo(ZERO) < 0;
    }
    
    /**
     * 判断是否为零
     * 
     * @param value 数值
     * @return 是否为零
     */
    public static boolean isZero(BigDecimal value) {
        return value != null && value.compareTo(ZERO) == 0;
    }
    
    /**
     * 获取绝对值
     * 
     * @param value 数值
     * @return 绝对值
     */
    public static BigDecimal abs(BigDecimal value) {
        return value == null ? ZERO : value.abs();
    }
    
    /**
     * 获取相反数
     * 
     * @param value 数值
     * @return 相反数
     */
    public static BigDecimal negate(BigDecimal value) {
        return value == null ? ZERO : value.negate();
    }
    
    /**
     * 四舍五入到指定精度
     * 
     * @param value 数值
     * @param scale 精度
     * @return 四舍五入后的值
     */
    public static BigDecimal round(BigDecimal value, int scale) {
        return round(value, scale, DEFAULT_ROUNDING_MODE);
    }
    
    /**
     * 按指定模式舍入到指定精度
     * 
     * @param value 数值
     * @param scale 精度
     * @param roundingMode 舍入模式
     * @return 舍入后的值
     */
    public static BigDecimal round(BigDecimal value, int scale, RoundingMode roundingMode) {
        if (value == null) return ZERO;
        return value.setScale(scale, roundingMode);
    }
    
    /**
     * 格式化数值为货币格式
     * 
     * @param value 数值
     * @return 货币格式字符串
     */
    public static String formatCurrency(BigDecimal value) {
        if (value == null) return "¥0.00";
        DecimalFormat df = new DecimalFormat("¥#,##0.00");
        return df.format(value);
    }
    
    /**
     * 格式化数值为百分比格式
     * 
     * @param value 数值（如0.05表示5%）
     * @return 百分比格式字符串
     */
    public static String formatPercentage(BigDecimal value) {
        if (value == null) return "0%";
        DecimalFormat df = new DecimalFormat("0.00%");
        return df.format(value);
    }
    
    /**
     * 格式化数值为千分位格式
     * 
     * @param value 数值
     * @return 千分位格式字符串
     */
    public static String formatWithCommas(BigDecimal value) {
        if (value == null) return "0";
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return df.format(value);
    }
    
    /**
     * 格式化数值为指定格式
     * 
     * @param value 数值
     * @param pattern 格式模式
     * @return 格式化后的字符串
     */
    public static String format(BigDecimal value, String pattern) {
        if (value == null) return "0";
        DecimalFormat df = new DecimalFormat(pattern);
        return df.format(value);
    }
    
    /**
     * 获取最大值
     * 
     * @param values 数值数组
     * @return 最大值
     */
    public static BigDecimal max(BigDecimal... values) {
        if (values == null || values.length == 0) return ZERO;
        BigDecimal max = values[0];
        for (BigDecimal value : values) {
            if (value != null && value.compareTo(max) > 0) {
                max = value;
            }
        }
        return max;
    }
    
    /**
     * 获取最小值
     * 
     * @param values 数值数组
     * @return 最小值
     */
    public static BigDecimal min(BigDecimal... values) {
        if (values == null || values.length == 0) return ZERO;
        BigDecimal min = values[0];
        for (BigDecimal value : values) {
            if (value != null && value.compareTo(min) < 0) {
                min = value;
            }
        }
        return min;
    }
    
    /**
     * 计算平均值
     * 
     * @param values 数值数组
     * @return 平均值
     */
    public static BigDecimal average(BigDecimal... values) {
        if (values == null || values.length == 0) return ZERO;
        BigDecimal sum = ZERO;
        int count = 0;
        for (BigDecimal value : values) {
            if (value != null) {
                sum = add(sum, value);
                count++;
            }
        }
        return count == 0 ? ZERO : divide(sum, of(count));
    }
    
    /**
     * 计算总和
     * 
     * @param values 数值数组
     * @return 总和
     */
    public static BigDecimal sum(BigDecimal... values) {
        if (values == null || values.length == 0) return ZERO;
        BigDecimal sum = ZERO;
        for (BigDecimal value : values) {
            if (value != null) {
                sum = add(sum, value);
            }
        }
        return sum;
    }
    
    /**
     * 安全转换为字符串
     * 
     * @param value 数值
     * @return 字符串表示
     */
    public static String toString(BigDecimal value) {
        return value == null ? "0" : value.toString();
    }
    
    /**
     * 安全转换为double
     * 
     * @param value 数值
     * @return double值
     */
    public static double toDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }
    
    /**
     * 安全转换为int
     * 
     * @param value 数值
     * @return int值
     */
    public static int toInt(BigDecimal value) {
        return value == null ? 0 : value.intValue();
    }
    
    /**
     * 安全转换为long
     * 
     * @param value 数值
     * @return long值
     */
    public static long toLong(BigDecimal value) {
        return value == null ? 0L : value.longValue();
    }
}