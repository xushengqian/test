import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.converters.BigDecimalConverter;
import org.apache.commons.beanutils.converters.BigIntegerConverter;
import org.apache.commons.beanutils.converters.DoubleConverter;
import org.apache.commons.beanutils.converters.FloatConverter;
import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.commons.beanutils.converters.LongConverter;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 全局转换器配置 - 在应用启动时调用
 * 推荐在Spring项目的配置类或应用启动类中调用
 */
public class GlobalConverterConfig {
    
    /**
     * 初始化所有数字类型转换器，允许空值
     * 建议在应用启动时调用一次
     */
    public static void initConverters() {
        // BigDecimal: 空值返回null
        ConvertUtils.register(new BigDecimalConverter(null), BigDecimal.class);
        
        // BigInteger: 空值返回null
        ConvertUtils.register(new BigIntegerConverter(null), BigInteger.class);
        
        // Integer: 空值返回null
        ConvertUtils.register(new IntegerConverter(null), Integer.class);
        
        // Long: 空值返回null
        ConvertUtils.register(new LongConverter(null), Long.class);
        
        // Double: 空值返回null
        ConvertUtils.register(new DoubleConverter(null), Double.class);
        
        // Float: 空值返回null
        ConvertUtils.register(new FloatConverter(null), Float.class);
        
        System.out.println("数字类型转换器初始化完成");
    }
    
    /**
     * 初始化所有数字类型转换器，使用默认值
     */
    public static void initConvertersWithDefaults() {
        // BigDecimal: 空值返回0
        ConvertUtils.register(new BigDecimalConverter(BigDecimal.ZERO), BigDecimal.class);
        
        // BigInteger: 空值返回0
        ConvertUtils.register(new BigIntegerConverter(BigInteger.ZERO), BigInteger.class);
        
        // Integer: 空值返回0
        ConvertUtils.register(new IntegerConverter(0), Integer.class);
        
        // Long: 空值返回0
        ConvertUtils.register(new LongConverter(0L), Long.class);
        
        // Double: 空值返回0.0
        ConvertUtils.register(new DoubleConverter(0.0), Double.class);
        
        // Float: 空值返回0.0f
        ConvertUtils.register(new FloatConverter(0.0f), Float.class);
        
        System.out.println("数字类型转换器初始化完成（带默认值）");
    }
}
