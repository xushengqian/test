package com.example.fieldstrategy.serializer;

import com.example.fieldstrategy.processor.FieldProcessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;

/**
 * JSON序列化器，集成字段策略处理
 * 
 * @author Example
 * @since 1.0.0
 */
@Slf4j
public class JsonSerializer {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    static {
        // 配置ObjectMapper
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    /**
     * 将对象序列化为JSON字符串，应用字段策略
     * 
     * @param object 要序列化的对象
     * @param groups 激活的分组
     * @return JSON字符串
     */
    public static String serialize(Object object, String... groups) {
        if (object == null) {
            return "null";
        }
        
        try {
            // 使用字段处理器处理对象
            Map<String, Object> processedData = FieldProcessor.processForSerialization(object, groups);
            
            // 转换为JSON
            String json = objectMapper.writeValueAsString(processedData);
            log.info("Successfully serialized object of type: {}", object.getClass().getSimpleName());
            return json;
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object", e);
            throw new RuntimeException("Serialization failed", e);
        }
    }
    
    /**
     * 将JSON字符串反序列化为对象，应用字段策略
     * 
     * @param json JSON字符串
     * @param clazz 目标类
     * @param groups 激活的分组
     * @param <T> 对象类型
     * @return 反序列化后的对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(String json, Class<T> clazz, String... groups) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        
        try {
            // 先解析为Map
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            
            // 创建目标对象实例
            T instance = clazz.getDeclaredConstructor().newInstance();
            
            // 使用字段处理器填充对象
            FieldProcessor.processForDeserialization(instance, data, groups);
            
            log.info("Successfully deserialized to type: {}", clazz.getSimpleName());
            return instance;
            
        } catch (IOException e) {
            log.error("Failed to deserialize JSON", e);
            throw new RuntimeException("Deserialization failed", e);
        } catch (Exception e) {
            log.error("Failed to create instance of: {}", clazz.getName(), e);
            throw new RuntimeException("Instance creation failed", e);
        }
    }
    
    /**
     * 美化打印JSON
     * 
     * @param object 对象
     * @param groups 分组
     * @return 格式化的JSON字符串
     */
    public static String prettyPrint(Object object, String... groups) {
        String json = serialize(object, groups);
        try {
            Object jsonObject = objectMapper.readValue(json, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        } catch (Exception e) {
            return json;
        }
    }
    
    /**
     * 将对象转换为Map
     * 
     * @param object 对象
     * @param groups 分组
     * @return Map表示
     */
    public static Map<String, Object> toMap(Object object, String... groups) {
        return FieldProcessor.processForSerialization(object, groups);
    }
    
    /**
     * 从Map创建对象
     * 
     * @param data 数据Map
     * @param clazz 目标类
     * @param groups 分组
     * @param <T> 对象类型
     * @return 对象实例
     */
    public static <T> T fromMap(Map<String, Object> data, Class<T> clazz, String... groups) {
        try {
            T instance = clazz.getDeclaredConstructor().newInstance();
            FieldProcessor.processForDeserialization(instance, data, groups);
            return instance;
        } catch (Exception e) {
            log.error("Failed to create instance from map", e);
            throw new RuntimeException("Object creation from map failed", e);
        }
    }
}