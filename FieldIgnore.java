import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段忽略注解
 * 用于标记需要忽略的字段
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldIgnore {
    
    /**
     * 字段处理策略
     * @return 字段策略
     */
    FieldStrategy strategy() default FieldStrategy.IGNORED;
    
    /**
     * 忽略的场景
     * @return 忽略场景数组
     */
    IgnoreType[] value() default {IgnoreType.ALL};
    
    /**
     * 忽略类型枚举
     */
    enum IgnoreType {
        /**
         * 所有场景都忽略
         */
        ALL,
        
        /**
         * 插入时忽略
         */
        INSERT,
        
        /**
         * 更新时忽略
         */
        UPDATE,
        
        /**
         * 查询时忽略
         */
        SELECT,
        
        /**
         * 序列化时忽略
         */
        SERIALIZE
    }
}