package com.example.example;

import org.apache.commons.beanutils.BeanUtils;
import com.example.config.BeanUtilsConfig;
import java.math.BigDecimal;
import java.util.Map;
import java.util.HashMap;

/**
 * 使用示例：演示如何解决 BigDecimal 转换异常
 */
public class ExampleUsage {

    // 示例目标类
    public static class TargetBean {
        private BigDecimal amount;
        private BigDecimal price;
        private String name;

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "TargetBean{amount=" + amount + ", price=" + price + ", name='" + name + "'}";
        }
    }

    /**
     * 方法 1: 使用配置好的 BeanUtilsBean 实例
     */
    public static void method1_UsingConfiguredBeanUtils() {
        System.out.println("=== 方法 1: 使用配置好的 BeanUtilsBean ===");
        
        Map<String, Object> source = new HashMap<>();
        source.put("amount", "");  // 空字符串
        source.put("price", null); // null 值
        source.put("name", "测试");

        TargetBean target = new TargetBean();

        try {
            // 使用配置好的 BeanUtilsBean
            BeanUtilsConfig.getConfiguredBeanUtils().populate(target, source);
            System.out.println("成功: " + target);
        } catch (Exception e) {
            System.out.println("失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 方法 2: 配置默认 BeanUtils（推荐用于全局配置）
     */
    public static void method2_ConfigureDefaultBeanUtils() {
        System.out.println("\n=== 方法 2: 配置默认 BeanUtils ===");
        
        // 在应用启动时调用一次
        BeanUtilsConfig.configureDefaultBeanUtils();

        Map<String, Object> source = new HashMap<>();
        source.put("amount", "123.45");
        source.put("price", "");  // 空字符串
        source.put("name", "测试");

        TargetBean target = new TargetBean();

        try {
            // 现在可以直接使用 BeanUtils 的静态方法
            BeanUtils.populate(target, source);
            System.out.println("成功: " + target);
        } catch (Exception e) {
            System.out.println("失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 方法 3: 直接使用转换器
     */
    public static void method3_DirectConverter() {
        System.out.println("\n=== 方法 3: 直接使用转换器 ===");
        
        BigDecimalConverter converter = new BigDecimalConverter();
        
        // 测试各种情况
        Object[] testValues = {null, "", "   ", "123.45", "0", BigDecimal.ZERO};
        
        for (Object value : testValues) {
            try {
                BigDecimal result = converter.convert(BigDecimal.class, value);
                System.out.println("值: '" + value + "' -> BigDecimal: " + result);
            } catch (Exception e) {
                System.out.println("值: '" + value + "' -> 错误: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        method1_UsingConfiguredBeanUtils();
        method2_ConfigureDefaultBeanUtils();
        method3_DirectConverter();
    }
}
