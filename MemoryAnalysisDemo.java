import java.util.*;
import java.util.concurrent.TimeUnit;

public class MemoryAnalysisDemo {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("启动内存分析演示程序...");
        
        // 创建大量不同类型的对象
        List<String> strings = new ArrayList<>();
        List<Integer> integers = new ArrayList<>();
        List<Object> objects = new ArrayList<>();
        Map<String, String> map = new HashMap<>();
        
        // 填充字符串对象
        for (int i = 0; i < 10000; i++) {
            strings.add("String_" + i + "_" + System.currentTimeMillis());
        }
        
        // 填充整数对象
        for (int i = 0; i < 5000; i++) {
            integers.add(i);
        }
        
        // 填充对象
        for (int i = 0; i < 8000; i++) {
            objects.add(new Object());
        }
        
        // 填充Map
        for (int i = 0; i < 3000; i++) {
            map.put("key_" + i, "value_" + i);
        }
        
        System.out.println("对象创建完成，程序将运行60秒以便分析...");
        System.out.println("请在新终端中运行以下命令：");
        System.out.println("1. 查看进程ID: jps");
        System.out.println("2. 查看对象直方图: jmap -histo <pid>");
        System.out.println("3. 只查看存活对象: jmap -histo:live <pid>");
        
        // 保持程序运行
        Thread.sleep(60000);
        
        System.out.println("程序结束");
    }
}