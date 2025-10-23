package com.example.fieldstrategy.enums;

/**
 * 字段策略枚举，定义字段在序列化/反序列化过程中的处理策略
 * 
 * @author Example
 * @since 1.0.0
 */
public enum FieldStrategy {
    
    /**
     * 默认策略：字段正常参与序列化和反序列化
     */
    DEFAULT("默认策略", "字段正常参与序列化和反序列化"),
    
    /**
     * 忽略策略：字段在序列化和反序列化过程中被完全忽略
     * 使用场景：
     * - 敏感信息（如密码、token等）
     * - 临时计算字段
     * - 仅供内部使用的字段
     */
    IGNORED("忽略策略", "字段在序列化和反序列化过程中被完全忽略"),
    
    /**
     * 只读策略：字段仅在序列化时输出，反序列化时被忽略
     * 使用场景：
     * - 计算属性
     * - 只读的系统生成字段（如创建时间、ID等）
     */
    READ_ONLY("只读策略", "字段仅在序列化时输出，反序列化时被忽略"),
    
    /**
     * 只写策略：字段仅在反序列化时接收，序列化时被忽略
     * 使用场景：
     * - 密码字段（接收但不返回）
     * - 一次性使用的字段
     */
    WRITE_ONLY("只写策略", "字段仅在反序列化时接收，序列化时被忽略"),
    
    /**
     * 非空策略：字段仅在非空时参与序列化
     * 使用场景：
     * - 可选字段
     * - 减少JSON体积
     */
    NOT_NULL("非空策略", "字段仅在非空时参与序列化"),
    
    /**
     * 非空非空白策略：字段仅在非空且非空白时参与序列化
     * 使用场景：
     * - 字符串字段的严格控制
     */
    NOT_EMPTY("非空非空白策略", "字段仅在非空且非空白时参与序列化");
    
    private final String name;
    private final String description;
    
    FieldStrategy(String name, String description) {
        this.name = name;
        this.description = description;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 判断字段是否应该被序列化
     * 
     * @param value 字段值
     * @return true if the field should be serialized
     */
    public boolean shouldSerialize(Object value) {
        switch (this) {
            case IGNORED:
            case WRITE_ONLY:
                return false;
            case NOT_NULL:
                return value != null;
            case NOT_EMPTY:
                if (value == null) return false;
                if (value instanceof String) {
                    return !((String) value).trim().isEmpty();
                }
                return true;
            case DEFAULT:
            case READ_ONLY:
            default:
                return true;
        }
    }
    
    /**
     * 判断字段是否应该被反序列化
     * 
     * @return true if the field should be deserialized
     */
    public boolean shouldDeserialize() {
        switch (this) {
            case IGNORED:
            case READ_ONLY:
                return false;
            case DEFAULT:
            case WRITE_ONLY:
            case NOT_NULL:
            case NOT_EMPTY:
            default:
                return true;
        }
    }
}