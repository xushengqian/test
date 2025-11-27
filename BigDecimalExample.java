import java.math.BigDecimal;

public class BigDecimalExample {
    public static void main(String[] args) {
        // 示例：去掉 BigDecimal 后面的0
        
        // 示例 1: 小数末尾有0
        BigDecimal decimal1 = new BigDecimal("123.4500");
        BigDecimal stripped1 = decimal1.stripTrailingZeros();
        System.out.println("原始值: " + decimal1);        // 输出: 123.4500
        System.out.println("去掉后面的0: " + stripped1);  // 输出: 123.45
        System.out.println();
        
        // 示例 2: 整数末尾有0
        BigDecimal decimal2 = new BigDecimal("100.00");
        BigDecimal stripped2 = decimal2.stripTrailingZeros();
        System.out.println("原始值: " + decimal2);        // 输出: 100.00
        System.out.println("去掉后面的0: " + stripped2);  // 输出: 1E+2
        System.out.println();
        
        // 示例 3: 如果不想要科学计数法表示，使用 toPlainString()
        BigDecimal decimal3 = new BigDecimal("100.00");
        String plainString = decimal3.stripTrailingZeros().toPlainString();
        System.out.println("原始值: " + decimal3);              // 输出: 100.00
        System.out.println("去掉后面的0（普通格式）: " + plainString);  // 输出: 100
        System.out.println();
        
        // 示例 4: 没有末尾0的情况
        BigDecimal decimal4 = new BigDecimal("123.456");
        BigDecimal stripped4 = decimal4.stripTrailingZeros();
        System.out.println("原始值: " + decimal4);        // 输出: 123.456
        System.out.println("去掉后面的0: " + stripped4);  // 输出: 123.456
        System.out.println();
        
        // 示例 5: 只有0的情况
        BigDecimal decimal5 = new BigDecimal("0.00");
        BigDecimal stripped5 = decimal5.stripTrailingZeros();
        System.out.println("原始值: " + decimal5);        // 输出: 0.00
        System.out.println("去掉后面的0: " + stripped5);  // 输出: 0
        System.out.println();
        
        // 示例 6: 大量末尾0
        BigDecimal decimal6 = new BigDecimal("1.230000000");
        BigDecimal stripped6 = decimal6.stripTrailingZeros();
        System.out.println("原始值: " + decimal6);        // 输出: 1.230000000
        System.out.println("去掉后面的0: " + stripped6);  // 输出: 1.23
    }
}
