import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeParseExample {
    
    public static void main(String[] args) {
        String dateString = "2024-07-01";
        
        // ❌ 错误的方式 - 会导致 DateTimeParseException
        // LocalDateTime dateTime = LocalDateTime.parse(dateString);
        
        // ✅ 方法 1: 使用 LocalDate.parse() - 推荐方式
        LocalDate date = LocalDate.parse(dateString);
        System.out.println("解析为 LocalDate: " + date);
        
        // ✅ 方法 2: 如果需要 LocalDateTime，可以添加默认时间
        LocalDateTime dateTime1 = LocalDate.parse(dateString).atStartOfDay();
        System.out.println("解析为 LocalDateTime (00:00:00): " + dateTime1);
        
        // ✅ 方法 3: 使用 DateTimeFormatter 并指定默认时间
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDateTime dateTime2 = LocalDate.parse(dateString, formatter).atStartOfDay();
        System.out.println("使用格式化器解析: " + dateTime2);
        
        // ✅ 方法 4: 如果字符串包含时间部分，使用 LocalDateTime.parse()
        String dateTimeString = "2024-07-01T10:30:00";
        LocalDateTime dateTime3 = LocalDateTime.parse(dateTimeString);
        System.out.println("解析完整日期时间: " + dateTime3);
    }
}
