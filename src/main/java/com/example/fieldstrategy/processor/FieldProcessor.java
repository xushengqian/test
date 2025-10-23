package com.example.fieldstrategy.processor;

import com.example.fieldstrategy.annotation.FieldConfig;
import com.example.fieldstrategy.enums.FieldStrategy;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 字段处理器，负责根据字段策略处理对象的序列化和反序列化
 * 
 * @author Example
 * @since 1.0.0
 */
@Slf4j
public class FieldProcessor {
    
    /**
     * 处理对象，根据字段策略返回应该被序列化的字段映射
     * 
     * @param object 要处理的对象
     * @param groups 激活的分组（可选）
     * @return 字段名到值的映射
     */
    public static Map<String, Object> processForSerialization(Object object, String... groups) {
        if (object == null) {
            return Collections.emptyMap();
        }
        
        Map<String, Object> result = new LinkedHashMap<>();
        Class<?> clazz = object.getClass();
        Set<String> activeGroups = new HashSet<>(Arrays.asList(groups));
        
        // 获取所有字段（包括父类的字段）
        List<Field> fields = getAllFields(clazz);
        
        for (Field field : fields) {
            field.setAccessible(true);
            
            try {
                FieldConfig config = field.getAnnotation(FieldConfig.class);
                FieldStrategy strategy = config != null ? config.strategy() : FieldStrategy.DEFAULT;
                
                // 检查分组
                if (config != null && config.groups().length > 0 && !activeGroups.isEmpty()) {
                    boolean groupMatch = Arrays.stream(config.groups())
                            .anyMatch(activeGroups::contains);
                    if (!groupMatch) {
                        continue;
                    }
                }
                
                Object value = field.get(object);
                
                // 根据策略判断是否应该序列化
                if (strategy.shouldSerialize(value)) {
                    String fieldName = getFieldName(field, config);
                    
                    // 处理加密
                    if (config != null && config.encrypted() && value != null) {
                        value = encrypt(value);
                    }
                    
                    result.put(fieldName, value);
                    log.debug("Field '{}' serialized with strategy: {}", fieldName, strategy);
                } else {
                    log.debug("Field '{}' skipped due to strategy: {}", field.getName(), strategy);
                }
                
            } catch (IllegalAccessException e) {
                log.error("Failed to access field: {}", field.getName(), e);
            }
        }
        
        return result;
    }
    
    /**
     * 处理反序列化，根据字段策略设置对象的字段值
     * 
     * @param target 目标对象
     * @param data 数据映射
     * @param groups 激活的分组（可选）
     */
    public static void processForDeserialization(Object target, Map<String, Object> data, String... groups) {
        if (target == null || data == null || data.isEmpty()) {
            return;
        }
        
        Class<?> clazz = target.getClass();
        Set<String> activeGroups = new HashSet<>(Arrays.asList(groups));
        
        // 创建字段名到字段的映射
        Map<String, Field> fieldMap = createFieldMap(clazz);
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String dataFieldName = entry.getKey();
            Object value = entry.getValue();
            
            // 查找对应的字段
            Field field = findFieldByNameOrAlias(fieldMap, dataFieldName);
            
            if (field == null) {
                log.debug("No matching field found for: {}", dataFieldName);
                continue;
            }
            
            field.setAccessible(true);
            FieldConfig config = field.getAnnotation(FieldConfig.class);
            FieldStrategy strategy = config != null ? config.strategy() : FieldStrategy.DEFAULT;
            
            // 检查分组
            if (config != null && config.groups().length > 0 && !activeGroups.isEmpty()) {
                boolean groupMatch = Arrays.stream(config.groups())
                        .anyMatch(activeGroups::contains);
                if (!groupMatch) {
                    continue;
                }
            }
            
            // 根据策略判断是否应该反序列化
            if (strategy.shouldDeserialize()) {
                try {
                    // 处理解密
                    if (config != null && config.encrypted() && value != null) {
                        value = decrypt(value);
                    }
                    
                    // 类型转换
                    Object convertedValue = convertValue(value, field.getType());
                    field.set(target, convertedValue);
                    log.debug("Field '{}' deserialized with strategy: {}", field.getName(), strategy);
                } catch (IllegalAccessException e) {
                    log.error("Failed to set field: {}", field.getName(), e);
                }
            } else {
                log.debug("Field '{}' skipped during deserialization due to strategy: {}", field.getName(), strategy);
            }
        }
    }
    
    /**
     * 获取字段的详细信息
     * 
     * @param object 对象
     * @return 字段信息列表
     */
    public static List<FieldInfo> getFieldsInfo(Object object) {
        if (object == null) {
            return Collections.emptyList();
        }
        
        Class<?> clazz = object.getClass();
        List<Field> fields = getAllFields(clazz);
        
        return fields.stream().map(field -> {
            field.setAccessible(true);
            FieldConfig config = field.getAnnotation(FieldConfig.class);
            
            FieldInfo info = new FieldInfo();
            info.setFieldName(field.getName());
            info.setFieldType(field.getType().getSimpleName());
            info.setStrategy(config != null ? config.strategy() : FieldStrategy.DEFAULT);
            info.setAlias(config != null && !config.alias().isEmpty() ? config.alias() : null);
            info.setDescription(config != null && !config.description().isEmpty() ? config.description() : null);
            info.setEncrypted(config != null && config.encrypted());
            info.setGroups(config != null ? Arrays.asList(config.groups()) : Collections.emptyList());
            
            try {
                info.setCurrentValue(field.get(object));
            } catch (IllegalAccessException e) {
                info.setCurrentValue(null);
            }
            
            return info;
        }).collect(Collectors.toList());
    }
    
    // 辅助方法
    
    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        
        return fields;
    }
    
    private static String getFieldName(Field field, FieldConfig config) {
        if (config != null && !config.alias().isEmpty()) {
            return config.alias();
        }
        return field.getName();
    }
    
    private static Map<String, Field> createFieldMap(Class<?> clazz) {
        Map<String, Field> fieldMap = new HashMap<>();
        List<Field> fields = getAllFields(clazz);
        
        for (Field field : fields) {
            fieldMap.put(field.getName(), field);
            
            FieldConfig config = field.getAnnotation(FieldConfig.class);
            if (config != null && !config.alias().isEmpty()) {
                fieldMap.put(config.alias(), field);
            }
        }
        
        return fieldMap;
    }
    
    private static Field findFieldByNameOrAlias(Map<String, Field> fieldMap, String name) {
        return fieldMap.get(name);
    }
    
    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null || targetType.isInstance(value)) {
            return value;
        }
        
        // 简单的类型转换
        if (targetType == String.class) {
            return value.toString();
        }
        if (targetType == Integer.class || targetType == int.class) {
            return Integer.valueOf(value.toString());
        }
        if (targetType == Long.class || targetType == long.class) {
            return Long.valueOf(value.toString());
        }
        if (targetType == Double.class || targetType == double.class) {
            return Double.valueOf(value.toString());
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.valueOf(value.toString());
        }
        
        return value;
    }
    
    private static Object encrypt(Object value) {
        // 简单的加密示例（实际应用中应使用真正的加密算法）
        if (value instanceof String) {
            return Base64.getEncoder().encodeToString(((String) value).getBytes());
        }
        return value;
    }
    
    private static Object decrypt(Object value) {
        // 简单的解密示例（实际应用中应使用真正的解密算法）
        if (value instanceof String) {
            try {
                return new String(Base64.getDecoder().decode((String) value));
            } catch (Exception e) {
                return value;
            }
        }
        return value;
    }
    
    /**
     * 字段信息内部类
     */
    public static class FieldInfo {
        private String fieldName;
        private String fieldType;
        private FieldStrategy strategy;
        private String alias;
        private String description;
        private boolean encrypted;
        private List<String> groups;
        private Object currentValue;
        
        // Getters and Setters
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        
        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }
        
        public FieldStrategy getStrategy() { return strategy; }
        public void setStrategy(FieldStrategy strategy) { this.strategy = strategy; }
        
        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public boolean isEncrypted() { return encrypted; }
        public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }
        
        public List<String> getGroups() { return groups; }
        public void setGroups(List<String> groups) { this.groups = groups; }
        
        public Object getCurrentValue() { return currentValue; }
        public void setCurrentValue(Object currentValue) { this.currentValue = currentValue; }
        
        @Override
        public String toString() {
            return String.format("FieldInfo{name='%s', type='%s', strategy=%s, alias='%s', encrypted=%s}",
                    fieldName, fieldType, strategy, alias, encrypted);
        }
    }
}