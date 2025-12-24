import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalExample {
    public static void main(String[] args) {
        // 示例1: 使用 HALF_UP 进行四舍五入（最常用）
        BigDecimal num1 = new BigDecimal("3.14159");
        BigDecimal result1 = num1.setScale(2, RoundingMode.HALF_UP);
        System.out.println("示例1 - 四舍五入: " + num1 + " -> " + result1);
        // 输出: 3.14159 -> 3.14

        BigDecimal num2 = new BigDecimal("3.145");
        BigDecimal result2 = num2.setScale(2, RoundingMode.HALF_UP);
        System.out.println("示例1 - 四舍五入: " + num2 + " -> " + result2);
        // 输出: 3.145 -> 3.15

        // 示例2: 使用不同的舍入模式
        BigDecimal num3 = new BigDecimal("3.145");
        
        // HALF_UP: 四舍五入（标准）
        System.out.println("HALF_UP: " + num3.setScale(2, RoundingMode.HALF_UP));
        
        // HALF_DOWN: 五舍六入
        System.out.println("HALF_DOWN: " + num3.setScale(2, RoundingMode.HALF_DOWN));
        
        // CEILING: 向上舍入（正数）
        System.out.println("CEILING: " + num3.setScale(2, RoundingMode.CEILING));
        
        // FLOOR: 向下舍入
        System.out.println("FLOOR: " + num3.setScale(2, RoundingMode.FLOOR));

        // 示例3: 实际应用 - 金额计算
        BigDecimal price = new BigDecimal("99.996");
        BigDecimal quantity = new BigDecimal("2");
        BigDecimal total = price.multiply(quantity);
        BigDecimal finalTotal = total.setScale(2, RoundingMode.HALF_UP);
        System.out.println("\n金额计算示例:");
        System.out.println("单价: " + price);
        System.out.println("数量: " + quantity);
        System.out.println("总价（未舍入）: " + total);
        System.out.println("总价（四舍五入保留两位小数）: " + finalTotal);

        // 示例4: 使用字符串构造 BigDecimal（推荐方式，避免精度问题）
        BigDecimal num4 = new BigDecimal("123.456789");
        BigDecimal result4 = num4.setScale(2, RoundingMode.HALF_UP);
        System.out.println("\n字符串构造示例: " + num4 + " -> " + result4);
    }
}
