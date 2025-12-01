import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 演示如何解决 DateTimeParseException 错误
 * 错误: Text '2024-07-01' could not be parsed: Unable to obtain LocalDateTime
 */
public class DateTimeParseExample {
    
    public static void main(String[] args) {
        String dateString = "2024-07-01";
        
        System.out.println("=== 解决方案1: 使用 LocalDate（推荐）===");
        solution1_UseLocalDate(dateString);
        
        System.out.println("\n=== 解决方案2: 添加时间部分到字符串 ===");
        solution2_AddTimeToString();
        
        System.out.println("\n=== 解决方案3: LocalDate 转换为 LocalDateTime ===");
        solution3_ConvertLocalDateToDateTime(dateString);
        
        System.out.println("\n=== 错误示例（会抛出异常）===");
        demonstrateError(dateString);
    }
    
    /**
     * 解决方案1: 使用 LocalDate 解析只包含日期的字符串
     */
    public static void solution1_UseLocalDate(String dateString) {
        // 方法1: 使用默认的ISO格式解析器
        LocalDate date1 = LocalDate.parse(dateString);
        System.out.println("方法1 - 默认解析: " + date1);
        
        // 方法2: 使用自定义格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate date2 = LocalDate.parse(dateString, formatter);
        System.out.println("方法2 - 自定义格式: " + date2);
    }
    
    /**
     * 解决方案2: 如果需要 LocalDateTime，在字符串中添加时间部分
     */
    public static void solution2_AddTimeToString() {
        // 方法1: 使用ISO格式（带T分隔符）
        String isoDateTime = "2024-07-01T00:00:00";
        LocalDateTime dateTime1 = LocalDateTime.parse(isoDateTime);
        System.out.println("方法1 - ISO格式: " + dateTime1);
        
        // 方法2: 使用空格分隔的格式
        String customDateTime = "2024-07-01 10:30:00";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime dateTime2 = LocalDateTime.parse(customDateTime, formatter);
        System.out.println("方法2 - 自定义格式: " + dateTime2);
    }
    
    /**
     * 解决方案3: 先解析为 LocalDate，然后转换为 LocalDateTime
     */
    public static void solution3_ConvertLocalDateToDateTime(String dateString) {
        LocalDate date = LocalDate.parse(dateString);
        
        // 方法1: 转换为当天的开始时间（00:00:00）
        LocalDateTime dateTime1 = date.atStartOfDay();
        System.out.println("方法1 - 当天开始: " + dateTime1);
        
        // 方法2: 指定具体时间
        LocalDateTime dateTime2 = date.atTime(10, 30, 0);
        System.out.println("方法2 - 指定时间: " + dateTime2);
        
        // 方法3: 使用 LocalTime 对象
        LocalDateTime dateTime3 = date.atTime(LocalTime.of(14, 45, 30));
        System.out.println("方法3 - 使用LocalTime: " + dateTime3);
        
        // 方法4: 转换为当天的结束时间（23:59:59.999999999）
        LocalDateTime dateTime4 = date.atTime(LocalTime.MAX);
        System.out.println("方法4 - 当天结束: " + dateTime4);
    }
    
    /**
     * 错误示例: 尝试将只包含日期的字符串解析为 LocalDateTime
     * 这会抛出 DateTimeParseException
     */
    public static void demonstrateError(String dateString) {
        try {
            // 这行代码会抛出异常！
            LocalDateTime dateTime = LocalDateTime.parse(dateString);
            System.out.println("不会执行到这里: " + dateTime);
        } catch (Exception e) {
            System.out.println("捕获到错误: " + e.getClass().getSimpleName());
            System.out.println("错误信息: " + e.getMessage());
        }
    }
}
