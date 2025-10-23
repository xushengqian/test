/**
 * 字段策略枚举
 * 用于定义字段在不同操作中的处理策略
 */
public enum FieldStrategy {
    
    /**
     * 默认策略 - 正常处理字段
     */
    DEFAULT,
    
    /**
     * 非空策略 - 只有当字段值非空时才处理
     */
    NOT_NULL,
    
    /**
     * 非空且非空字符串策略 - 字段值非空且不为空字符串时才处理
     */
    NOT_EMPTY,
    
    /**
     * 忽略策略 - 完全忽略该字段，不参与任何操作
     * 使用场景：
     * 1. 数据库字段不需要更新
     * 2. 序列化时需要排除的字段
     * 3. 临时字段或计算字段
     */
    IGNORED,
    
    /**
     * 总是处理策略 - 无论字段值如何都会处理
     */
    ALWAYS,
    
    /**
     * 非零策略 - 数值字段非零时才处理
     */
    NOT_ZERO;
    
    /**
     * 判断字段是否应该被忽略
     * @return 如果是IGNORED策略返回true，否则返回false
     */
    public boolean isIgnored() {
        return this == IGNORED;
    }
    
    /**
     * 判断字段是否需要根据值进行条件处理
     * @param value 字段值
     * @return 是否应该处理该字段
     */
    public boolean shouldProcess(Object value) {
        switch (this) {
            case IGNORED:
                return false;
            case DEFAULT:
            case ALWAYS:
                return true;
            case NOT_NULL:
                return value != null;
            case NOT_EMPTY:
                return value != null && !value.toString().trim().isEmpty();
            case NOT_ZERO:
                if (value instanceof Number) {
                    return ((Number) value).doubleValue() != 0.0;
                }
                return value != null;
            default:
                return true;
        }
    }
}