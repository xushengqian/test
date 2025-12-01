import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * 实用的日期时间工具类示例
 * 处理各种日期字符串解析场景
 */
public class DateTimeUtilExample {
    
    // 常用的日期格式
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public static void main(String[] args) {
        // 测试各种场景
        System.out.println("=== 场景1: 解析日期字符串 ===");
        String dateStr = "2024-07-01";
        parseDate(dateStr).ifPresent(date -> 
            System.out.println("解析的日期: " + date));
        
        System.out.println("\n=== 场景2: 解析日期并转换为日期时间（当天开始）===");
        parseAsDateTime(dateStr).ifPresent(dateTime -> 
            System.out.println("转换的日期时间: " + dateTime));
        
        System.out.println("\n=== 场景3: 智能解析（自动识别日期或日期时间）===");
        smartParse("2024-07-01").ifPresent(dt -> 
            System.out.println("智能解析结果1: " + dt));
        smartParse("2024-07-01 15:30:00").ifPresent(dt -> 
            System.out.println("智能解析结果2: " + dt));
        
        System.out.println("\n=== 场景4: 安全解析（带错误处理）===");
        safeParseToDateTime("2024-07-01", LocalDateTime.now());
        safeParseToDateTime("invalid-date", LocalDateTime.now());
    }
    
    /**
     * 解析日期字符串为 LocalDate
     * @param dateString 日期字符串，格式: yyyy-MM-dd
     * @return Optional<LocalDate>
     */
    public static Optional<LocalDate> parseDate(String dateString) {
        try {
            LocalDate date = LocalDate.parse(dateString, DATE_FORMATTER);
            return Optional.of(date);
        } catch (DateTimeParseException e) {
            System.err.println("无法解析日期: " + dateString + " - " + e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * 解析日期字符串为 LocalDateTime（设置为当天00:00:00）
     * @param dateString 日期字符串，格式: yyyy-MM-dd
     * @return Optional<LocalDateTime>
     */
    public static Optional<LocalDateTime> parseAsDateTime(String dateString) {
        return parseDate(dateString).map(LocalDate::atStartOfDay);
    }
    
    /**
     * 智能解析：自动识别输入是日期还是日期时间
     * @param input 日期或日期时间字符串
     * @return Optional<LocalDateTime>
     */
    public static Optional<LocalDateTime> smartParse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return Optional.empty();
        }
        
        try {
            // 尝试解析为日期时间（带时间部分）
            if (input.contains(" ") || input.contains("T")) {
                if (input.contains("T")) {
                    // ISO格式
                    return Optional.of(LocalDateTime.parse(input));
                } else {
                    // 自定义格式
                    return Optional.of(LocalDateTime.parse(input, DATETIME_FORMATTER));
                }
            } else {
                // 只有日期，转换为日期时间（当天开始）
                LocalDate date = LocalDate.parse(input, DATE_FORMATTER);
                return Optional.of(date.atStartOfDay());
            }
        } catch (DateTimeParseException e) {
            System.err.println("无法解析: " + input + " - " + e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * 安全解析：带默认值的解析方法
     * @param dateString 日期字符串
     * @param defaultValue 解析失败时的默认值
     * @return LocalDateTime
     */
    public static LocalDateTime safeParseToDateTime(String dateString, LocalDateTime defaultValue) {
        try {
            LocalDate date = LocalDate.parse(dateString, DATE_FORMATTER);
            LocalDateTime result = date.atStartOfDay();
            System.out.println("成功解析: " + dateString + " -> " + result);
            return result;
        } catch (DateTimeParseException e) {
            System.out.println("解析失败，使用默认值: " + dateString + " -> " + defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 格式化日期时间为字符串
     */
    public static String formatDate(LocalDate date) {
        return date.format(DATE_FORMATTER);
    }
    
    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(DATETIME_FORMATTER);
    }
}
