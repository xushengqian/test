import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 简化的AviatorScript优化示例
 * 展示优化策略的核心思想，不依赖外部库
 */
public class SimpleAviatorExample {
    
    // 模拟表达式缓存
    private static final Map<String, CompiledExpression> expressionCache = new ConcurrentHashMap<>();
    
    // 执行统计
    private static final AtomicLong totalExecutions = new AtomicLong(0);
    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong cacheMisses = new AtomicLong(0);
    
    public static void main(String[] args) {
        System.out.println("=== Java AviatorScript 优化示例 ===");
        System.out.println();
        
        // 测试表达式
        String[] testExpressions = {
            "1 + 2 * 3",
            "Math.sin(3.14159 / 2)",
            "Math.pow(2, 10)",
            "Math.sqrt(16)",
            "Math.log(Math.E)"
        };
        
        // 预热缓存
        System.out.println("1. 预热表达式缓存...");
        warmupCache(testExpressions);
        
        // 执行表达式
        System.out.println("\n2. 执行表达式:");
        for (String expr : testExpressions) {
            try {
                Object result = executeExpression(expr);
                System.out.printf("   %s = %s%n", expr, result);
            } catch (Exception e) {
                System.err.printf("   执行失败: %s - %s%n", expr, e.getMessage());
            }
        }
        
        // 性能测试
        System.out.println("\n3. 性能测试:");
        performanceTest();
        
        // 显示统计信息
        System.out.println("\n4. 统计信息:");
        showStatistics();
    }
    
    /**
     * 预热缓存
     */
    private static void warmupCache(String[] expressions) {
        for (String expr : expressions) {
            try {
                compileExpression(expr);
                System.out.printf("   预热: %s%n", expr);
            } catch (Exception e) {
                System.err.printf("   预热失败: %s - %s%n", expr, e.getMessage());
            }
        }
    }
    
    /**
     * 执行表达式（带缓存优化）
     */
    private static Object executeExpression(String expression) {
        totalExecutions.incrementAndGet();
        
        // 从缓存获取编译后的表达式
        CompiledExpression compiled = getOrCompileExpression(expression);
        
        // 执行表达式
        return compiled.execute();
    }
    
    /**
     * 获取或编译表达式
     */
    private static CompiledExpression getOrCompileExpression(String expression) {
        CompiledExpression compiled = expressionCache.get(expression);
        
        if (compiled != null) {
            cacheHits.incrementAndGet();
            return compiled;
        }
        
        cacheMisses.incrementAndGet();
        compiled = compileExpression(expression);
        expressionCache.put(expression, compiled);
        return compiled;
    }
    
    /**
     * 编译表达式
     */
    private static CompiledExpression compileExpression(String expression) {
        // 这里模拟AviatorScript的编译过程
        // 实际实现中会调用 AviatorEvaluator.compile()
        
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        
        // 简单的表达式解析和编译
        return new CompiledExpression(expression);
    }
    
    /**
     * 性能测试
     */
    private static void performanceTest() {
        String expression = "Math.sin(3.14159 / 2)";
        int iterations = 100000;
        
        System.out.printf("   执行 %d 次表达式: %s%n", iterations, expression);
        
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            executeExpression(expression);
        }
        long endTime = System.nanoTime();
        
        double totalTimeMs = (endTime - startTime) / 1_000_000.0;
        double avgTimeUs = totalTimeMs * 1000 / iterations;
        
        System.out.printf("   总耗时: %.2f ms%n", totalTimeMs);
        System.out.printf("   平均耗时: %.2f μs%n", avgTimeUs);
        System.out.printf("   每秒执行次数: %.0f%n", iterations * 1000.0 / totalTimeMs);
    }
    
    /**
     * 显示统计信息
     */
    private static void showStatistics() {
        long total = cacheHits.get() + cacheMisses.get();
        double hitRate = total > 0 ? (double) cacheHits.get() / total * 100 : 0.0;
        
        System.out.printf("   总执行次数: %d%n", totalExecutions.get());
        System.out.printf("   缓存命中次数: %d%n", cacheHits.get());
        System.out.printf("   缓存未命中次数: %d%n", cacheMisses.get());
        System.out.printf("   缓存命中率: %.2f%%%n", hitRate);
        System.out.printf("   缓存大小: %d%n", expressionCache.size());
    }
    
    /**
     * 编译后的表达式
     */
    private static class CompiledExpression {
        private final String expression;
        private final long compileTime;
        
        public CompiledExpression(String expression) {
            this.expression = expression;
            this.compileTime = System.currentTimeMillis();
        }
        
        public Object execute() {
            // 这里模拟表达式执行
            // 实际实现中会调用编译后的表达式对象
            
            try {
                // 简单的数学表达式计算
                if (expression.equals("1 + 2 * 3")) {
                    return 7;
                } else if (expression.equals("Math.sin(3.14159 / 2)")) {
                    return Math.sin(3.14159 / 2);
                } else if (expression.equals("Math.pow(2, 10)")) {
                    return Math.pow(2, 10);
                } else if (expression.equals("Math.sqrt(16)")) {
                    return Math.sqrt(16);
                } else if (expression.equals("Math.log(Math.E)")) {
                    return Math.log(Math.E);
                } else {
                    // 对于其他表达式，返回一个模拟值
                    return "计算结果: " + expression;
                }
            } catch (Exception e) {
                throw new RuntimeException("表达式执行失败: " + e.getMessage(), e);
            }
        }
        
        public long getCompileTime() {
            return compileTime;
        }
    }
}