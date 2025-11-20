package com.example.converter;

import org.apache.commons.beanutils.Converter;
import java.math.BigDecimal;

/**
 * 自定义 BigDecimal 转换器
 * 解决 BeanUtils 在转换空值或空字符串到 BigDecimal 时抛出 ConversionException 的问题
 */
public class BigDecimalConverter implements Converter {

    /**
     * 将值转换为 BigDecimal
     * 
     * @param targetType 目标类型（应为 BigDecimal.class）
     * @param value 要转换的值
     * @return BigDecimal 对象，如果值为 null 或空字符串则返回 null
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T convert(Class<T> targetType, Object value) {
        // 如果目标类型不是 BigDecimal，返回 null
        if (targetType != BigDecimal.class) {
            return null;
        }

        // 如果值为 null，返回 null
        if (value == null) {
            return null;
        }

        // 如果是空字符串，返回 null
        if (value instanceof String) {
            String strValue = ((String) value).trim();
            if (strValue.isEmpty() || strValue.equalsIgnoreCase("null")) {
                return null;
            }
            try {
                return (T) new BigDecimal(strValue);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "无法将值 '" + value + "' 转换为 BigDecimal: " + e.getMessage(), e);
            }
        }

        // 如果已经是 BigDecimal，直接返回
        if (value instanceof BigDecimal) {
            return (T) value;
        }

        // 如果是数字类型，转换为 BigDecimal
        if (value instanceof Number) {
            return (T) BigDecimal.valueOf(((Number) value).doubleValue());
        }

        // 尝试通过字符串转换
        try {
            return (T) new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "无法将值 '" + value + "' (类型: " + value.getClass().getName() + 
                ") 转换为 BigDecimal: " + e.getMessage(), e);
        }
    }
}
