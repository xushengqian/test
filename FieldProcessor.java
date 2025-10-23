import java.lang.reflect.Field;
import java.util.*;

/**
 * 字段处理器
 * 根据FieldStrategy处理对象字段
 */
public class FieldProcessor {
    
    /**
     * 处理对象，根据字段策略决定是否包含某些字段
     * @param obj 要处理的对象
     * @return 处理后的字段映射
     */
    public static Map<String, Object> processObject(Object obj) {
        if (obj == null) {
            return new HashMap<>();
        }
        
        Map<String, Object> result = new HashMap<>();
        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();
        
        for (Field field : fields) {
            field.setAccessible(true);
            
            try {
                Object value = field.get(obj);
                
                // 检查字段是否有FieldIgnore注解
                FieldIgnore fieldIgnore = field.getAnnotation(FieldIgnore.class);
                if (fieldIgnore != null) {
                    FieldStrategy strategy = fieldIgnore.strategy();
                    
                    // 如果策略是IGNORED，跳过该字段
                    if (strategy.isIgnored()) {
                        System.out.println("字段 '" + field.getName() + "' 被忽略 (FieldStrategy.IGNORED)");
                        continue;
                    }
                    
                    // 根据策略判断是否应该处理该字段
                    if (!strategy.shouldProcess(value)) {
                        System.out.println("字段 '" + field.getName() + "' 根据策略 " + strategy + " 被跳过");
                        continue;
                    }
                }
                
                // 对于没有注解或者注解策略不是IGNORED的字段，检查默认策略
                if (fieldIgnore == null) {
                    // 没有注解，使用默认策略处理
                    FieldStrategy defaultStrategy = FieldStrategy.DEFAULT;
                    if (!defaultStrategy.shouldProcess(value)) {
                        System.out.println("字段 '" + field.getName() + "' 根据默认策略被跳过");
                        continue;
                    }
                }
                
                result.put(field.getName(), value);
                
            } catch (IllegalAccessException e) {
                System.err.println("无法访问字段: " + field.getName());
            }
        }
        
        return result;
    }
    
    /**
     * 获取所有被忽略的字段名称
     * @param clazz 类对象
     * @return 被忽略的字段名称列表
     */
    public static List<String> getIgnoredFields(Class<?> clazz) {
        List<String> ignoredFields = new ArrayList<>();
        Field[] fields = clazz.getDeclaredFields();
        
        for (Field field : fields) {
            FieldIgnore fieldIgnore = field.getAnnotation(FieldIgnore.class);
            if (fieldIgnore != null && fieldIgnore.strategy().isIgnored()) {
                ignoredFields.add(field.getName());
            }
        }
        
        return ignoredFields;
    }
    
    /**
     * 检查字段是否应该在指定操作中被忽略
     * @param field 字段对象
     * @param ignoreType 忽略类型
     * @return 是否应该忽略
     */
    public static boolean shouldIgnoreInOperation(Field field, FieldIgnore.IgnoreType ignoreType) {
        FieldIgnore fieldIgnore = field.getAnnotation(FieldIgnore.class);
        if (fieldIgnore == null) {
            return false;
        }
        
        // 如果策略是IGNORED，总是忽略
        if (fieldIgnore.strategy().isIgnored()) {
            return true;
        }
        
        // 检查特定操作类型
        FieldIgnore.IgnoreType[] ignoreTypes = fieldIgnore.value();
        for (FieldIgnore.IgnoreType type : ignoreTypes) {
            if (type == FieldIgnore.IgnoreType.ALL || type == ignoreType) {
                return true;
            }
        }
        
        return false;
    }
}