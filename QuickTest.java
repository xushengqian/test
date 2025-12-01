import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 快速测试工具 - 一键测试所有上传方法
 */
public class QuickTest {

    public static void main(String[] args) {
        String uploadUrl = "http://localhost:8080/upload";
        
        // 检查是否提供了自定义URL
        if (args.length > 0) {
            uploadUrl = args[0];
        }
        
        System.out.println("=============================================");
        System.out.println("        Java HTTP 文件上传快速测试");
        System.out.println("=============================================");
        System.out.println("目标服务器: " + uploadUrl);
        System.out.println();
        System.out.println("提示: 请先启动 SimpleUploadServer");
        System.out.println("      运行命令: java SimpleUploadServer");
        System.out.println("=============================================\n");
        
        // 等待用户确认
        System.out.println("按回车键开始测试...");
        try {
            System.in.read();
        } catch (Exception e) {
            // 忽略
        }
        
        // 准备测试数据
        byte[] testData = createTestData();
        
        // 测试1: HttpURLConnection 基本上传
        System.out.println("\n【测试 1/5】使用 HttpURLConnection 上传");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        try {
            String result = HttpFormFileUploadExample.uploadFileFromBytes(
                uploadUrl, testData, "test-urlconnection.txt", "file"
            );
            System.out.println("✅ 成功");
            System.out.println(result);
        } catch (Exception e) {
            System.out.println("❌ 失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        sleep(1000);
        
        // 测试2: HttpURLConnection 带参数上传
        System.out.println("\n【测试 2/5】使用 HttpURLConnection 带参数上传");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        try {
            Map<String, String> params = new HashMap<>();
            params.put("userId", "test-user-123");
            params.put("category", "documents");
            params.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            String result = HttpFormFileUploadExample.uploadFileWithParams(
                uploadUrl, testData, "test-with-params.txt", "file", params
            );
            System.out.println("✅ 成功");
            System.out.println(result);
        } catch (Exception e) {
            System.out.println("❌ 失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        sleep(1000);
        
        // 测试3: Java 11+ HttpClient
        System.out.println("\n【测试 3/5】使用 Java HttpClient（Java 11+）");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        try {
            String result = HttpClientFileUploadExample.uploadFileUsingHttpClient(
                uploadUrl, testData, "test-httpclient.txt", "file"
            );
            System.out.println("✅ 成功");
            System.out.println(result);
        } catch (NoClassDefFoundError e) {
            System.out.println("❌ 失败: " + e.getMessage());
            System.out.println("提示: 需要 Java 11+ 版本");
        } catch (Exception e) {
            System.out.println("❌ 失败: " + e.getMessage());
        }
        
        sleep(1000);
        
        // 测试4: FileUploadUtils 工具类
        System.out.println("\n【测试 4/5】使用 FileUploadUtils 工具类");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        try {
            Map<String, String> params = new HashMap<>();
            params.put("source", "FileUploadUtils");
            params.put("testId", "004");
            
            FileUploadUtils.UploadResult result = FileUploadUtils.uploadBytes(
                uploadUrl, testData, "test-utils.txt", "file", params
            );
            System.out.println("✅ 成功");
            System.out.println(result);
        } catch (Exception e) {
            System.out.println("❌ 失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        sleep(1000);
        
        // 测试5: Base64 上传
        System.out.println("\n【测试 5/5】从 Base64 字符串上传");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        try {
            String base64 = Base64.getEncoder().encodeToString(testData);
            FileUploadUtils.UploadResult result = FileUploadUtils.uploadFromBase64(
                uploadUrl, base64, "test-base64.txt", "file"
            );
            System.out.println("✅ 成功");
            System.out.println(result);
        } catch (Exception e) {
            System.out.println("❌ 失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        // 测试总结
        System.out.println("\n\n=============================================");
        System.out.println("              测试完成");
        System.out.println("=============================================");
        System.out.println("如果所有测试都成功，说明你的环境配置正确！");
        System.out.println("现在可以在实际项目中使用这些方法了。");
        System.out.println("=============================================\n");
    }
    
    /**
     * 创建测试数据
     */
    private static byte[] createTestData() {
        StringBuilder sb = new StringBuilder();
        sb.append("Java HTTP 文件上传测试数据\n");
        sb.append("=".repeat(50)).append("\n");
        sb.append("时间: ").append(new Date()).append("\n");
        sb.append("测试ID: ").append(UUID.randomUUID()).append("\n");
        sb.append("\n");
        sb.append("文件内容测试:\n");
        sb.append("- 支持中文字符\n");
        sb.append("- 支持特殊符号: !@#$%^&*()_+-={}[]|:;<>?,./\n");
        sb.append("- 支持换行和空格\n");
        sb.append("\n");
        sb.append("这是一个完整的测试文件，用于验证 byte[] 上传功能。\n");
        sb.append("如果你看到这个内容，说明文件上传成功！\n");
        
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * 睡眠
     */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
