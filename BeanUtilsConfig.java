package com.example.config;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.ConvertUtilsBean;
import com.example.converter.BigDecimalConverter;
import java.math.BigDecimal;

/**
 * BeanUtils 配置类
 * 注册自定义转换器以处理 BigDecimal 转换问题
 */
public class BeanUtilsConfig {

    private static volatile BeanUtilsBean beanUtilsBean;
    private static final Object lock = new Object();

    /**
     * 获取配置好的 BeanUtilsBean 实例
     * 该实例已注册自定义的 BigDecimal 转换器
     * 
     * @return 配置好的 BeanUtilsBean 实例
     */
    public static BeanUtilsBean getConfiguredBeanUtils() {
        if (beanUtilsBean == null) {
            synchronized (lock) {
                if (beanUtilsBean == null) {
                    ConvertUtilsBean convertUtils = new ConvertUtilsBean();
                    // 注册自定义 BigDecimal 转换器
                    convertUtils.register(new BigDecimalConverter(), BigDecimal.class);
                    beanUtilsBean = new BeanUtilsBean(convertUtils);
                }
            }
        }
        return beanUtilsBean;
    }

    /**
     * 配置默认的 BeanUtils，使其使用自定义转换器
     * 调用此方法后，使用 BeanUtils.copyProperties 等静态方法时会使用自定义转换器
     */
    public static void configureDefaultBeanUtils() {
        ConvertUtilsBean convertUtils = new ConvertUtilsBean();
        convertUtils.register(new BigDecimalConverter(), BigDecimal.class);
        BeanUtilsBean.setInstance(new BeanUtilsBean(convertUtils));
    }
}
