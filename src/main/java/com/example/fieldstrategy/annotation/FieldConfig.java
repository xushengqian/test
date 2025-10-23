package com.example.fieldstrategy.annotation;

import com.example.fieldstrategy.enums.FieldStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段配置注解，用于标记字段的处理策略
 * 
 * @author Example
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldConfig {
    
    /**
     * 字段处理策略
     * 
     * @return 字段策略，默认为 DEFAULT
     */
    FieldStrategy strategy() default FieldStrategy.DEFAULT;
    
    /**
     * 字段别名（用于序列化时的字段名）
     * 
     * @return 字段别名，默认为空（使用原字段名）
     */
    String alias() default "";
    
    /**
     * 字段描述
     * 
     * @return 字段描述信息
     */
    String description() default "";
    
    /**
     * 是否加密
     * 
     * @return true表示字段需要加密处理
     */
    boolean encrypted() default false;
    
    /**
     * 字段分组（用于按组控制序列化）
     * 
     * @return 分组名称数组
     */
    String[] groups() default {};
}