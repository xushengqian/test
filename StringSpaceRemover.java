/**
 * Java字符串去除全部空格的示例程序
 * 演示多种去除字符串中所有空格的方法
 */
public class StringSpaceRemover {
    
    /**
     * 方法1: 使用 replace() 方法去除所有空格
     * 这是最简单和常用的方法
     */
    public static String removeSpacesWithReplace(String str) {
        if (str == null) {
            return null;
        }
        return str.replace(" ", "");
    }
    
    /**
     * 方法2: 使用 replaceAll() 方法配合正则表达式
     * 可以去除所有类型的空白字符（空格、制表符、换行符等）
     */
    public static String removeAllWhitespaces(String str) {
        if (str == null) {
            return null;
        }
        return str.replaceAll("\\s+", "");
    }
    
    /**
     * 方法3: 只去除普通空格，使用正则表达式
     */
    public static String removeSpacesWithRegex(String str) {
        if (str == null) {
            return null;
        }
        return str.replaceAll(" ", "");
    }
    
    /**
     * 方法4: 使用 StringBuilder 手动遍历
     * 性能较好，适合处理大量字符串
     */
    public static String removeSpacesWithStringBuilder(String str) {
        if (str == null) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c != ' ') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    /**
     * 方法5: 使用 Java 8 Stream API
     * 函数式编程风格
     */
    public static String removeSpacesWithStream(String str) {
        if (str == null) {
            return null;
        }
        
        return str.chars()
                  .filter(c -> c != ' ')
                  .collect(StringBuilder::new, 
                          StringBuilder::appendCodePoint, 
                          StringBuilder::append)
                  .toString();
    }
    
    /**
     * 方法6: 去除所有空白字符（包括空格、制表符、换行符等）
     * 使用 StringBuilder 实现
     */
    public static String removeAllWhitespacesManually(String str) {
        if (str == null) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    /**
     * 主方法 - 测试所有去除空格的方法
     */
    public static void main(String[] args) {
        // 测试字符串
        String testString = "  Hello   World  Java  Programming  ";
        String testStringWithTabs = "Hello\tWorld\nJava\rProgramming";
        
        System.out.println("=== Java 字符串去除空格示例 ===\n");
        
        System.out.println("原始字符串: \"" + testString + "\"");
        System.out.println("字符串长度: " + testString.length());
        System.out.println();
        
        // 测试方法1: replace()
        String result1 = removeSpacesWithReplace(testString);
        System.out.println("方法1 - replace(): \"" + result1 + "\"");
        System.out.println("结果长度: " + result1.length());
        System.out.println();
        
        // 测试方法2: replaceAll() 去除所有空白字符
        String result2 = removeAllWhitespaces(testString);
        System.out.println("方法2 - replaceAll(\\s+): \"" + result2 + "\"");
        System.out.println("结果长度: " + result2.length());
        System.out.println();
        
        // 测试方法3: replaceAll() 只去除空格
        String result3 = removeSpacesWithRegex(testString);
        System.out.println("方法3 - replaceAll(空格): \"" + result3 + "\"");
        System.out.println("结果长度: " + result3.length());
        System.out.println();
        
        // 测试方法4: StringBuilder
        String result4 = removeSpacesWithStringBuilder(testString);
        System.out.println("方法4 - StringBuilder: \"" + result4 + "\"");
        System.out.println("结果长度: " + result4.length());
        System.out.println();
        
        // 测试方法5: Stream API
        String result5 = removeSpacesWithStream(testString);
        System.out.println("方法5 - Stream API: \"" + result5 + "\"");
        System.out.println("结果长度: " + result5.length());
        System.out.println();
        
        // 测试方法6: 手动去除所有空白字符
        String result6 = removeAllWhitespacesManually(testString);
        System.out.println("方法6 - 手动去除所有空白: \"" + result6 + "\"");
        System.out.println("结果长度: " + result6.length());
        System.out.println();
        
        // 测试包含制表符和换行符的字符串
        System.out.println("=== 测试包含制表符和换行符的字符串 ===");
        System.out.println("原始字符串: \"" + testStringWithTabs.replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r") + "\"");
        
        String result7 = removeAllWhitespaces(testStringWithTabs);
        System.out.println("去除所有空白字符后: \"" + result7 + "\"");
        System.out.println();
        
        // 性能比较示例
        System.out.println("=== 性能建议 ===");
        System.out.println("1. 对于简单的空格去除，推荐使用 replace(\" \", \"\")");
        System.out.println("2. 对于去除所有空白字符，推荐使用 replaceAll(\"\\\\s+\", \"\")");
        System.out.println("3. 对于大量字符串处理，StringBuilder 方法性能更好");
        System.out.println("4. Stream API 方法代码简洁但性能相对较低");
    }
}