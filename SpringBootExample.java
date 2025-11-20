import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.annotation.PostConstruct;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.converters.BigDecimalConverter;
import java.math.BigDecimal;

/**
 * Spring Boot 项目中的配置示例
 */
@SpringBootApplication
public class SpringBootExample {
    
    public static void main(String[] args) {
        SpringApplication.run(SpringBootExample.class, args);
    }
    
    /**
     * 方法1: 使用@PostConstruct在应用启动时初始化
     */
    @PostConstruct
    public void init() {
        // 注册BigDecimal转换器，允许空值
        ConvertUtils.register(new BigDecimalConverter(null), BigDecimal.class);
        System.out.println("BeanUtils转换器已初始化");
    }
}

/**
 * 方法2: 使用Configuration类
 */
@Configuration
class BeanUtilsConfig {
    
    @Bean
    public ConverterInitializer converterInitializer() {
        return new ConverterInitializer();
    }
    
    static class ConverterInitializer {
        public ConverterInitializer() {
            // 在Bean构造时初始化转换器
            ConvertUtils.register(new BigDecimalConverter(null), BigDecimal.class);
            System.out.println("通过Configuration类初始化BeanUtils转换器");
        }
    }
}
