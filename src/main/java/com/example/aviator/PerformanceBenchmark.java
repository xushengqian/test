package com.example.aviator;

import com.googlecode.aviator.AviatorEvaluator;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * AviatorScript 性能基准测试
 * 使用 JMH 进行精确的性能测量
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class PerformanceBenchmark {
    
    private OptimizedAviatorExecutor optimizedExecutor;
    private ExpressionPool expressionPool;
    
    // 测试表达式
    private static final String SIMPLE_MATH = "1 + 2 * 3";
    private static final String COMPLEX_MATH = "sqrt(16) + pow(2, 3) * 5";
    private static final String STRING_EXPR = "string.length('hello') + 2";
    
    @Setup
    public void setup() {
        optimizedExecutor = OptimizedAviatorExecutor.getInstance();
        expressionPool = ExpressionPool.getInstance();
        
        // 预编译常用表达式
        optimizedExecutor.precompileExpressions(SIMPLE_MATH, COMPLEX_MATH, STRING_EXPR);
        
        // 初始化表达式池
        expressionPool.initPools(SIMPLE_MATH, COMPLEX_MATH, STRING_EXPR);
        
        // 预热
        expressionPool.warmUp(SIMPLE_MATH, COMPLEX_MATH, STRING_EXPR);
    }
    
    /**
     * 基准测试：原生 AviatorEvaluator 执行
     */
    @Benchmark
    public Object testNativeAviator() {
        return AviatorEvaluator.execute(SIMPLE_MATH, Collections.emptyMap());
    }
    
    /**
     * 基准测试：优化后的执行器
     */
    @Benchmark
    public Object testOptimizedExecutor() {
        return optimizedExecutor.executeWithoutParams(SIMPLE_MATH);
    }
    
    /**
     * 基准测试：表达式池执行
     */
    @Benchmark
    public Object testExpressionPool() {
        return expressionPool.execute(SIMPLE_MATH);
    }
    
    /**
     * 基准测试：复杂数学表达式 - 原生
     */
    @Benchmark
    public Object testComplexMathNative() {
        return AviatorEvaluator.execute(COMPLEX_MATH, Collections.emptyMap());
    }
    
    /**
     * 基准测试：复杂数学表达式 - 优化版
     */
    @Benchmark
    public Object testComplexMathOptimized() {
        return optimizedExecutor.executeWithoutParams(COMPLEX_MATH);
    }
    
    /**
     * 基准测试：复杂数学表达式 - 池化版
     */
    @Benchmark
    public Object testComplexMathPool() {
        return expressionPool.execute(COMPLEX_MATH);
    }
    
    /**
     * 基准测试：字符串表达式 - 原生
     */
    @Benchmark
    public Object testStringExprNative() {
        return AviatorEvaluator.execute(STRING_EXPR, Collections.emptyMap());
    }
    
    /**
     * 基准测试：字符串表达式 - 优化版
     */
    @Benchmark
    public Object testStringExprOptimized() {
        return optimizedExecutor.executeWithoutParams(STRING_EXPR);
    }
    
    /**
     * 基准测试：字符串表达式 - 池化版
     */
    @Benchmark
    public Object testStringExprPool() {
        return expressionPool.execute(STRING_EXPR);
    }
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(PerformanceBenchmark.class.getSimpleName())
                .build();
        
        new Runner(opt).run();
    }
}