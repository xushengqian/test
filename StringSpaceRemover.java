/**
 * 演示Java中去除字符串全部空格的各种方法
 */
public class StringSpaceRemover {
    
    public static void main(String[] args) {
        // 测试字符串，包含各种类型的空白字符
        String testString = "  Hello   World  Java   编程   测试  ";
        String complexString = "Java\t编程\n语言\r学习 空格    测试";
        
        System.out.println("原始字符串: \"" + testString + "\"");
        System.out.println("原始字符串长度: " + testString.length());
        System.out.println("----------------------------------------");
        
        // 方法1: 使用 replace() 方法 - 只能去除普通空格
        String result1 = removeSpacesByReplace(testString);
        System.out.println("方法1 - replace(): \"" + result1 + "\"");
        System.out.println("结果长度: " + result1.length());
        System.out.println();
        
        // 方法2: 使用 replaceAll() 方法与正则表达式 - 去除所有空白字符
        String result2 = removeSpacesByRegex(testString);
        System.out.println("方法2 - replaceAll(\"\\\\s\", \"\"): \"" + result2 + "\"");
        System.out.println("结果长度: " + result2.length());
        System.out.println();
        
        // 方法3: 使用 replaceAll() 去除所有类型的空白字符
        String result3 = removeAllWhitespace(testString);
        System.out.println("方法3 - replaceAll(\"\\\\s+\", \"\"): \"" + result3 + "\"");
        System.out.println("结果长度: " + result3.length());
        System.out.println();
        
        // 方法4: 使用 StringBuilder 手动去除空格
        String result4 = removeSpacesByStringBuilder(testString);
        System.out.println("方法4 - StringBuilder: \"" + result4 + "\"");
        System.out.println("结果长度: " + result4.length());
        System.out.println();
        
        // 方法5: 使用 Java 8 Stream API
        String result5 = removeSpacesByStream(testString);
        System.out.println("方法5 - Stream API: \"" + result5 + "\"");
        System.out.println("结果长度: " + result5.length());
        System.out.println();
        
        System.out.println("========================================");
        System.out.println("复杂字符串测试（包含制表符、换行符等）:");
        System.out.println("原始字符串: \"" + complexString + "\"");
        System.out.println("----------------------------------------");
        
        // 对复杂字符串使用不同方法
        System.out.println("方法1 - replace(): \"" + removeSpacesByReplace(complexString) + "\"");
        System.out.println("方法2 - replaceAll(): \"" + removeSpacesByRegex(complexString) + "\"");
        System.out.println("方法3 - replaceAll(\\s+): \"" + removeAllWhitespace(complexString) + "\"");
        
        System.out.println("\n========================================");
        System.out.println("性能测试:");
        performanceTest();
    }
    
    /**
     * 方法1: 使用 replace() 方法
     * 注意：只能去除普通空格字符，不能去除制表符、换行符等
     */
    public static String removeSpacesByReplace(String str) {
        return str.replace(" ", "");
    }
    
    /**
     * 方法2: 使用 replaceAll() 方法与正则表达式
     * \\s 匹配所有空白字符（空格、制表符、换行符等）
     */
    public static String removeSpacesByRegex(String str) {
        return str.replaceAll("\\s", "");
    }
    
    /**
     * 方法3: 使用 replaceAll() 去除连续的空白字符
     * \\s+ 匹配一个或多个连续的空白字符
     */
    public static String removeAllWhitespace(String str) {
        return str.replaceAll("\\s+", "");
    }
    
    /**
     * 方法4: 使用 StringBuilder 手动去除空格
     * 性能较好，适合处理大量数据
     */
    public static String removeSpacesByStringBuilder(String str) {
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    /**
     * 方法5: 使用 Java 8 Stream API
     * 函数式编程风格
     */
    public static String removeSpacesByStream(String str) {
        return str.chars()
                .filter(c -> !Character.isWhitespace(c))
                .collect(StringBuilder::new,
                        StringBuilder::appendCodePoint,
                        StringBuilder::append)
                .toString();
    }
    
    /**
     * 性能测试：比较不同方法的执行时间
     */
    public static void performanceTest() {
        String testStr = "这 是 一 个 测 试 字 符 串 用 于 性 能 测 试";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append(testStr).append(" ");
        }
        String longString = sb.toString();
        
        int iterations = 1000;
        
        // 测试 replace()
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            removeSpacesByReplace(longString);
        }
        long end = System.nanoTime();
        System.out.println("replace() 方法耗时: " + (end - start) / 1_000_000 + " ms");
        
        // 测试 replaceAll()
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            removeSpacesByRegex(longString);
        }
        end = System.nanoTime();
        System.out.println("replaceAll() 方法耗时: " + (end - start) / 1_000_000 + " ms");
        
        // 测试 StringBuilder
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            removeSpacesByStringBuilder(longString);
        }
        end = System.nanoTime();
        System.out.println("StringBuilder 方法耗时: " + (end - start) / 1_000_000 + " ms");
        
        // 测试 Stream API
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            removeSpacesByStream(longString);
        }
        end = System.nanoTime();
        System.out.println("Stream API 方法耗时: " + (end - start) / 1_000_000 + " ms");
    }
}