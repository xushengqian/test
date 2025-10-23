import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * FieldStrategy.IGNORED 使用演示
 */
public class FieldStrategyDemo {
    
    public static void main(String[] args) {
        System.out.println("=== FieldStrategy.IGNORED 演示 ===\n");
        
        // 创建用户对象
        User user = new User(1L, "张三", "zhangsan@example.com", "secret123");
        user.setVersion(1);
        user.setUpdateTime(new java.util.Date());
        
        System.out.println("1. 原始用户对象:");
        System.out.println(user);
        System.out.println();
        
        // 演示字段处理
        System.out.println("2. 处理后的字段 (忽略IGNORED策略的字段):");
        Map<String, Object> processedFields = FieldProcessor.processObject(user);
        processedFields.forEach((key, value) -> 
            System.out.println("  " + key + " = " + value));
        System.out.println();
        
        // 获取所有被忽略的字段
        System.out.println("3. 被 FieldStrategy.IGNORED 忽略的字段:");
        List<String> ignoredFields = FieldProcessor.getIgnoredFields(User.class);
        ignoredFields.forEach(fieldName -> 
            System.out.println("  - " + fieldName));
        System.out.println();
        
        // 演示不同操作场景下的字段忽略
        System.out.println("4. 不同操作场景下的字段处理:");
        
        Field[] fields = User.class.getDeclaredFields();
        FieldIgnore.IgnoreType[] operations = {
            FieldIgnore.IgnoreType.INSERT,
            FieldIgnore.IgnoreType.UPDATE,
            FieldIgnore.IgnoreType.SELECT,
            FieldIgnore.IgnoreType.SERIALIZE
        };
        
        for (FieldIgnore.IgnoreType operation : operations) {
            System.out.println("  " + operation + " 操作中被忽略的字段:");
            for (Field field : fields) {
                if (FieldProcessor.shouldIgnoreInOperation(field, operation)) {
                    System.out.println("    - " + field.getName());
                }
            }
            System.out.println();
        }
        
        // 演示FieldStrategy的各种策略
        System.out.println("5. FieldStrategy 策略演示:");
        demonstrateFieldStrategies();
    }
    
    private static void demonstrateFieldStrategies() {
        System.out.println("  各种FieldStrategy对不同值的处理结果:");
        
        Object[] testValues = {null, "", "  ", "hello", 0, 1, 0.0, 1.5};
        FieldStrategy[] strategies = FieldStrategy.values();
        
        System.out.printf("  %-12s", "值\\策略");
        for (FieldStrategy strategy : strategies) {
            System.out.printf("%-12s", strategy.name());
        }
        System.out.println();
        
        for (Object value : testValues) {
            String valueStr = value == null ? "null" : 
                             value.toString().isEmpty() ? "\"\"" :
                             value.toString().trim().isEmpty() ? "\"  \"" :
                             value.toString();
            System.out.printf("  %-12s", valueStr);
            
            for (FieldStrategy strategy : strategies) {
                boolean shouldProcess = strategy.shouldProcess(value);
                System.out.printf("%-12s", shouldProcess ? "✓" : "✗");
            }
            System.out.println();
        }
    }
}