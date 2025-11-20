import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.converters.BigDecimalConverter;
import java.math.BigDecimal;

/**
 * BigDecimal 转换异常解决方案示例
 */
public class BigDecimalConverterExample {

    public static void main(String[] args) {
        // 解决方案1: 注册自定义转换器，允许空值
        setupBigDecimalConverter();
        
        // 测试
        TestBean bean = new TestBean();
        try {
            // 这不会抛出异常，因为我们已经配置了默认值
            BeanUtils.setProperty(bean, "amount", "");
            System.out.println("空字符串转换成功: " + bean.getAmount());
            
            BeanUtils.setProperty(bean, "amount", "123.45");
            System.out.println("正常值转换成功: " + bean.getAmount());
            
            BeanUtils.setProperty(bean, "amount", null);
            System.out.println("null值转换成功: " + bean.getAmount());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 方案1: 注册允许空值的BigDecimal转换器
     * 当输入为null或空字符串时，返回null而不是抛出异常
     */
    public static void setupBigDecimalConverter() {
        // 参数null表示当值为空时返回null
        BigDecimalConverter converter = new BigDecimalConverter(null);
        ConvertUtils.register(converter, BigDecimal.class);
    }
    
    /**
     * 方案2: 注册带默认值的BigDecimal转换器
     * 当输入为null或空字符串时，返回指定的默认值
     */
    public static void setupBigDecimalConverterWithDefault() {
        // 指定默认值为BigDecimal.ZERO
        BigDecimalConverter converter = new BigDecimalConverter(BigDecimal.ZERO);
        ConvertUtils.register(converter, BigDecimal.class);
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
