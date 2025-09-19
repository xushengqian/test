package com.example.aviator;

import com.googlecode.aviator.AviatorEvaluator;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * JMH基准测试类，用于比较优化前后的性能
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class AviatorBenchmark {
    
    private OptimizedAviatorExecutor optimizedExecutor;
    private String simpleExpression = "1 + 2 * 3";
    private String complexExpression = "let a = 10; let b = 20; a * b + 100";
    private String stringExpression = "'Hello' + ' ' + 'World'";
    private String logicExpression = "(1 > 0) && (2 < 3) || (4 == 4)";
    private String mathExpression = "math.sqrt(16) + math.pow(2, 3)";
    
    @Setup
    public void setup() {
        optimizedExecutor = new OptimizedAviatorExecutor(true, false, 1000, 10000);
        
        // 预热
        String[] warmupExpressions = {
            simpleExpression,
            complexExpression,
            stringExpression,
            logicExpression,
            mathExpression
        };
        optimizedExecutor.warmUp(warmupExpressions);
    }
    
    /**
     * 测试原始AviatorScript执行（无优化）
     */
    @Benchmark
    public Object testOriginalSimple() {
        return AviatorEvaluator.execute(simpleExpression);
    }
    
    @Benchmark
    public Object testOriginalComplex() {
        return AviatorEvaluator.execute(complexExpression);
    }
    
    @Benchmark
    public Object testOriginalString() {
        return AviatorEvaluator.execute(stringExpression);
    }
    
    @Benchmark
    public Object testOriginalLogic() {
        return AviatorEvaluator.execute(logicExpression);
    }
    
    @Benchmark
    public Object testOriginalMath() {
        return AviatorEvaluator.execute(mathExpression);
    }
    
    /**
     * 测试优化后的执行器
     */
    @Benchmark
    public Object testOptimizedSimple() {
        return optimizedExecutor.execute(simpleExpression);
    }
    
    @Benchmark
    public Object testOptimizedComplex() {
        return optimizedExecutor.execute(complexExpression);
    }
    
    @Benchmark
    public Object testOptimizedString() {
        return optimizedExecutor.execute(stringExpression);
    }
    
    @Benchmark
    public Object testOptimizedLogic() {
        return optimizedExecutor.execute(logicExpression);
    }
    
    @Benchmark
    public Object testOptimizedMath() {
        return optimizedExecutor.execute(mathExpression);
    }
    
    /**
     * 测试批量执行
     */
    @Benchmark
    public Object[] testBatchExecution() {
        String[] expressions = {
            simpleExpression,
            complexExpression,
            stringExpression,
            logicExpression,
            mathExpression
        };
        return optimizedExecutor.executeBatch(expressions);
    }
    
    /**
     * 主方法，运行基准测试
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(AviatorBenchmark.class.getSimpleName())
            .build();
        
        new Runner(opt).run();
    }
}