import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.converters.BigDecimalConverter;
import java.math.BigDecimal;

/**
 * 解决 Apache Commons BeanUtils BigDecimal 转换异常
 * 
 * 错误: org.apache.commons.beanutils.ConversionException: No value specified for 'BigDecimal'
 * 
 * 原因: 当尝试将空字符串或null值转换为BigDecimal时，默认的转换器会抛出异常
 */
public class BigDecimalConversionExample {
    
    /**
     * 解决方案1: 注册自定义BigDecimal转换器，允许null值
     * 这是最推荐的解决方案
     */
    public static void registerNullSafeBigDecimalConverter() {
        // 创建一个允许null值的BigDecimal转换器
        BigDecimalConverter converter = new BigDecimalConverter(null);
        ConvertUtils.register(converter, BigDecimal.class);
    }
    
    /**
     * 解决方案2: 注册自定义BigDecimal转换器，使用默认值
     */
    public static void registerDefaultValueBigDecimalConverter() {
        // 使用BigDecimal.ZERO作为默认值
        BigDecimalConverter converter = new BigDecimalConverter(BigDecimal.ZERO);
        ConvertUtils.register(converter, BigDecimal.class);
    }
    
    /**
     * 解决方案3: 在转换前检查并处理空值
     */
    public static BigDecimal safeConvertToBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO; // 或者返回null，根据业务需求
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO; // 或者抛出异常，根据业务需求
        }
    }
    
    /**
     * 解决方案4: 使用自定义转换器类
     */
    public static class NullSafeBigDecimalConverter extends BigDecimalConverter {
        public NullSafeBigDecimalConverter() {
            super(null); // 允许null值
        }
        
        @Override
        public Object convert(Class type, Object value) {
            if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                return null; // 或者返回BigDecimal.ZERO
            }
            return super.convert(type, value);
        }
    }
    
    /**
     * 使用示例
     */
    public static void main(String[] args) {
        // 在应用启动时注册转换器（推荐在初始化代码中调用）
        registerNullSafeBigDecimalConverter();
        
        // 现在可以安全地转换空值
        try {
            // 示例：使用BeanUtils进行属性复制
            // BeanUtils.copyProperty(bean, "amount", ""); // 不会再抛出异常
            
            // 或者手动转换
            BigDecimal result1 = safeConvertToBigDecimal(null);
            BigDecimal result2 = safeConvertToBigDecimal("");
            BigDecimal result3 = safeConvertToBigDecimal("123.45");
            
            System.out.println("null -> " + result1);
            System.out.println("空字符串 -> " + result2);
            System.out.println("123.45 -> " + result3);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
