import java.math.BigDecimal;

public class BigDecimalExample {
    public static void main(String[] args) {
        // 示例：去掉 BigDecimal 后面的 0
        
        // 创建带有尾随零的 BigDecimal
        BigDecimal value1 = new BigDecimal("123.4500");
        BigDecimal value2 = new BigDecimal("100.00");
        BigDecimal value3 = new BigDecimal("50.0");
        BigDecimal value4 = new BigDecimal("123.456");
        
        // 使用 stripTrailingZeros() 方法去掉后面的 0
        BigDecimal result1 = value1.stripTrailingZeros();
        BigDecimal result2 = value2.stripTrailingZeros();
        BigDecimal result3 = value3.stripTrailingZeros();
        BigDecimal result4 = value4.stripTrailingZeros();
        
        System.out.println("原始值: " + value1 + " -> 去掉尾随零: " + result1);
        System.out.println("原始值: " + value2 + " -> 去掉尾随零: " + result2);
        System.out.println("原始值: " + value3 + " -> 去掉尾随零: " + result3);
        System.out.println("原始值: " + value4 + " -> 去掉尾随零: " + result4);
        
        // 如果需要转换为字符串并去掉科学计数法表示
        // 可以使用 toPlainString() 方法
        System.out.println("\n使用 toPlainString():");
        System.out.println("result1: " + result1.toPlainString());
        System.out.println("result2: " + result2.toPlainString());
        System.out.println("result3: " + result3.toPlainString());
        System.out.println("result4: " + result4.toPlainString());
    }
    
    /**
     * 工具方法：去掉 BigDecimal 后面的 0 并返回字符串
     */
    public static String removeTrailingZeros(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
