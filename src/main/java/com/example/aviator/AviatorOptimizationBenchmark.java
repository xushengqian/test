package com.example.aviator;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * AviatorScript 性能基准测试
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class AviatorOptimizationBenchmark {
    
    private AviatorOptimizer optimizer;
    private String[] testExpressions;
    
    @Setup(Level.Trial)
    public void setup() {
        optimizer = AviatorOptimizer.getInstance();
        
        // 测试表达式
        testExpressions = new String[]{
            "1 + 2 * 3",
            "Math.sin(3.14159 / 2)",
            "Math.pow(2, 10)",
            "Math.sqrt(16)",
            "Math.log(Math.E)",
            "Math.abs(-100)",
            "Math.max(10, 20)",
            "Math.min(10, 20)",
            "Math.round(3.7)",
            "Math.floor(3.7)",
            "Math.ceil(3.2)",
            "Math.random()",
            "System.currentTimeMillis()",
            "new java.util.Date()",
            "new java.util.Random().nextInt(100)"
        };
        
        // 预热缓存
        optimizer.warmupCache(testExpressions);
    }
    
    @Benchmark
    public Object testOptimizedExecution() {
        return optimizer.execute("1 + 2 * 3");
    }
    
    @Benchmark
    public Object testMathExpression() {
        return optimizer.execute("Math.sin(3.14159 / 2)");
    }
    
    @Benchmark
    public Object testComplexExpression() {
        return optimizer.execute("Math.pow(2, 10) + Math.sqrt(16)");
    }
    
    @Benchmark
    public Object testBatchExecution() {
        return optimizer.executeBatch(new String[]{
            "1 + 2 * 3",
            "Math.sin(3.14159 / 2)",
            "Math.pow(2, 10)"
        });
    }
    
    @Benchmark
    public Object testRandomExpression() {
        return optimizer.execute("Math.random() * 100");
    }
    
    @Benchmark
    public Object testTimeExpression() {
        return optimizer.execute("System.currentTimeMillis()");
    }
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(AviatorOptimizationBenchmark.class.getSimpleName())
                .build();
        
        new Runner(opt).run();
    }
}