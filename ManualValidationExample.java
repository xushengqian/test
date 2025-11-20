import org.apache.commons.beanutils.BeanUtils;
import java.math.BigDecimal;

/**
 * 手动验证和转换示例
 */
public class ManualValidationExample {
    
    /**
     * 方案3: 手动验证后再设置值
     */
    public static void safeSetBigDecimal(Object bean, String propertyName, String value) {
        try {
            if (value != null && !value.trim().isEmpty()) {
                // 只有当值不为空时才进行转换
                BeanUtils.setProperty(bean, propertyName, value);
            } else {
                // 空值情况下，可以设置为null或默认值
                BeanUtils.setProperty(bean, propertyName, (BigDecimal) null);
                // 或者设置默认值: BeanUtils.setProperty(bean, propertyName, BigDecimal.ZERO);
            }
        } catch (Exception e) {
            System.err.println("设置属性失败: " + propertyName + ", 值: " + value);
            e.printStackTrace();
        }
    }
    
    /**
     * 方案4: 使用try-catch包装转换逻辑
     */
    public static BigDecimal convertToBigDecimal(String value, BigDecimal defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            System.err.println("无效的BigDecimal值: " + value + ", 使用默认值");
            return defaultValue;
        }
    }
    
    public static void main(String[] args) {
        // 测试手动验证
        TestBean bean = new TestBean();
        
        safeSetBigDecimal(bean, "amount", "");
        System.out.println("空字符串处理: " + bean.getAmount());
        
        safeSetBigDecimal(bean, "amount", "99.99");
        System.out.println("正常值处理: " + bean.getAmount());
        
        // 测试手动转换
        BigDecimal result1 = convertToBigDecimal("", BigDecimal.ZERO);
        BigDecimal result2 = convertToBigDecimal(null, BigDecimal.ZERO);
        BigDecimal result3 = convertToBigDecimal("123.45", BigDecimal.ZERO);
        
        System.out.println("转换结果1 (空字符串): " + result1);
        System.out.println("转换结果2 (null): " + result2);
        System.out.println("转换结果3 (正常值): " + result3);
    }
}

class TestBean {
    private BigDecimal amount;
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
