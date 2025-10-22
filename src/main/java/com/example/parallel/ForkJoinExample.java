package com.example.parallel;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * Fork/Join框架并行执行示例
 */
public class ForkJoinExample {

    public static void main(String[] args) {
        System.out.println("===== Fork/Join框架并行执行示例 =====\n");
        
        // 1. 使用RecursiveTask计算数组求和
        System.out.println("1. 使用RecursiveTask并行计算数组求和:");
        recursiveTaskExample();
        
        // 2. 使用RecursiveAction并行处理数组
        System.out.println("\n2. 使用RecursiveAction并行处理数组:");
        recursiveActionExample();
        
        // 3. 使用并行流（基于Fork/Join）
        System.out.println("\n3. 使用并行流处理大数据集:");
        parallelStreamExample();
        
        // 4. 自定义ForkJoinPool控制并行度
        System.out.println("\n4. 自定义ForkJoinPool控制并行度:");
        customForkJoinPoolExample();
        
        // 5. 实际应用 - 并行归并排序
        System.out.println("\n5. 实际应用 - 并行归并排序:");
        parallelMergeSortExample();
    }
    
    /**
     * 使用RecursiveTask计算数组求和
     */
    private static void recursiveTaskExample() {
        int[] numbers = IntStream.rangeClosed(1, 1000000).toArray();
        
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        SumTask task = new SumTask(numbers, 0, numbers.length);
        
        long startTime = System.currentTimeMillis();
        Long sum = forkJoinPool.invoke(task);
        long endTime = System.currentTimeMillis();
        
        System.out.println("并行计算结果: " + sum);
        System.out.println("计算耗时: " + (endTime - startTime) + "ms");
        
        // 对比串行计算
        startTime = System.currentTimeMillis();
        long serialSum = 0;
        for (int number : numbers) {
            serialSum += number;
        }
        endTime = System.currentTimeMillis();
        
        System.out.println("串行计算结果: " + serialSum);
        System.out.println("串行耗时: " + (endTime - startTime) + "ms");
    }
    
    /**
     * 使用RecursiveAction并行处理数组
     */
    private static void recursiveActionExample() {
        int[] numbers = new int[10000];
        Arrays.fill(numbers, 1);
        
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        ArrayProcessorAction action = new ArrayProcessorAction(numbers, 0, numbers.length);
        
        System.out.println("处理前数组样本: " + Arrays.toString(Arrays.copyOf(numbers, 10)));
        
        forkJoinPool.invoke(action);
        
        System.out.println("处理后数组样本: " + Arrays.toString(Arrays.copyOf(numbers, 10)));
        System.out.println("数组总和: " + Arrays.stream(numbers).sum());
    }
    
    /**
     * 使用并行流处理大数据集
     */
    private static void parallelStreamExample() {
        List<Integer> numbers = IntStream.rangeClosed(1, 10000000)
            .boxed()
            .toList();
        
        // 并行流处理
        long startTime = System.currentTimeMillis();
        long parallelSum = numbers.parallelStream()
            .mapToLong(Integer::longValue)
            .filter(n -> n % 2 == 0) // 只处理偶数
            .map(n -> n * 2) // 每个数字乘以2
            .sum();
        long endTime = System.currentTimeMillis();
        
        System.out.println("并行流处理结果: " + parallelSum);
        System.out.println("并行流耗时: " + (endTime - startTime) + "ms");
        
        // 串行流处理对比
        startTime = System.currentTimeMillis();
        long serialSum = numbers.stream()
            .mapToLong(Integer::longValue)
            .filter(n -> n % 2 == 0)
            .map(n -> n * 2)
            .sum();
        endTime = System.currentTimeMillis();
        
        System.out.println("串行流处理结果: " + serialSum);
        System.out.println("串行流耗时: " + (endTime - startTime) + "ms");
    }
    
    /**
     * 自定义ForkJoinPool控制并行度
     */
    private static void customForkJoinPoolExample() {
        // 创建自定义并行度的ForkJoinPool
        int parallelism = 4; // 使用4个线程
        ForkJoinPool customThreadPool = new ForkJoinPool(parallelism);
        
        try {
            List<Integer> numbers = IntStream.rangeClosed(1, 100).boxed().toList();
            
            // 在自定义线程池中执行并行流操作
            Integer sum = customThreadPool.submit(() ->
                numbers.parallelStream()
                    .peek(n -> System.out.println(Thread.currentThread().getName() + " 处理: " + n))
                    .reduce(0, Integer::sum)
            ).get();
            
            System.out.println("自定义线程池计算结果: " + sum);
            System.out.println("使用的并行度: " + parallelism);
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            customThreadPool.shutdown();
        }
    }
    
    /**
     * 并行归并排序示例
     */
    private static void parallelMergeSortExample() {
        int[] array = IntStream.generate(() -> (int)(Math.random() * 1000))
            .limit(100000)
            .toArray();
        
        int[] arrayCopy = Arrays.copyOf(array, array.length);
        
        // 并行归并排序
        ForkJoinPool pool = new ForkJoinPool();
        long startTime = System.currentTimeMillis();
        pool.invoke(new MergeSortTask(array));
        long endTime = System.currentTimeMillis();
        
        System.out.println("并行归并排序耗时: " + (endTime - startTime) + "ms");
        System.out.println("排序后前10个元素: " + Arrays.toString(Arrays.copyOf(array, 10)));
        
        // 使用Arrays.sort对比（Tim排序）
        startTime = System.currentTimeMillis();
        Arrays.sort(arrayCopy);
        endTime = System.currentTimeMillis();
        
        System.out.println("Arrays.sort耗时: " + (endTime - startTime) + "ms");
    }
    
    /**
     * RecursiveTask实现 - 计算数组求和
     */
    static class SumTask extends RecursiveTask<Long> {
        private static final int THRESHOLD = 10000;
        private int[] numbers;
        private int start;
        private int end;
        
        public SumTask(int[] numbers, int start, int end) {
            this.numbers = numbers;
            this.start = start;
            this.end = end;
        }
        
        @Override
        protected Long compute() {
            int length = end - start;
            
            // 如果任务足够小，直接计算
            if (length <= THRESHOLD) {
                long sum = 0;
                for (int i = start; i < end; i++) {
                    sum += numbers[i];
                }
                return sum;
            }
            
            // 否则，分割任务
            int mid = start + length / 2;
            SumTask leftTask = new SumTask(numbers, start, mid);
            SumTask rightTask = new SumTask(numbers, mid, end);
            
            // Fork左任务
            leftTask.fork();
            
            // 在当前线程计算右任务
            Long rightResult = rightTask.compute();
            
            // Join左任务的结果
            Long leftResult = leftTask.join();
            
            // 合并结果
            return leftResult + rightResult;
        }
    }
    
    /**
     * RecursiveAction实现 - 并行处理数组元素
     */
    static class ArrayProcessorAction extends RecursiveAction {
        private static final int THRESHOLD = 1000;
        private int[] array;
        private int start;
        private int end;
        
        public ArrayProcessorAction(int[] array, int start, int end) {
            this.array = array;
            this.start = start;
            this.end = end;
        }
        
        @Override
        protected void compute() {
            if (end - start <= THRESHOLD) {
                // 直接处理
                for (int i = start; i < end; i++) {
                    array[i] = array[i] * 2; // 每个元素乘以2
                }
            } else {
                // 分割任务
                int mid = start + (end - start) / 2;
                ArrayProcessorAction left = new ArrayProcessorAction(array, start, mid);
                ArrayProcessorAction right = new ArrayProcessorAction(array, mid, end);
                
                // 并行执行
                invokeAll(left, right);
            }
        }
    }
    
    /**
     * 并行归并排序任务
     */
    static class MergeSortTask extends RecursiveAction {
        private static final int THRESHOLD = 1000;
        private int[] array;
        private int start;
        private int end;
        private int[] temp;
        
        public MergeSortTask(int[] array) {
            this(array, 0, array.length, new int[array.length]);
        }
        
        private MergeSortTask(int[] array, int start, int end, int[] temp) {
            this.array = array;
            this.start = start;
            this.end = end;
            this.temp = temp;
        }
        
        @Override
        protected void compute() {
            if (end - start <= THRESHOLD) {
                // 使用插入排序处理小数组
                insertionSort(array, start, end);
            } else {
                int mid = start + (end - start) / 2;
                
                MergeSortTask left = new MergeSortTask(array, start, mid, temp);
                MergeSortTask right = new MergeSortTask(array, mid, end, temp);
                
                invokeAll(left, right);
                
                merge(array, start, mid, end, temp);
            }
        }
        
        private void insertionSort(int[] arr, int start, int end) {
            for (int i = start + 1; i < end; i++) {
                int key = arr[i];
                int j = i - 1;
                while (j >= start && arr[j] > key) {
                    arr[j + 1] = arr[j];
                    j--;
                }
                arr[j + 1] = key;
            }
        }
        
        private void merge(int[] arr, int start, int mid, int end, int[] temp) {
            System.arraycopy(arr, start, temp, start, end - start);
            
            int i = start;
            int j = mid;
            int k = start;
            
            while (i < mid && j < end) {
                if (temp[i] <= temp[j]) {
                    arr[k++] = temp[i++];
                } else {
                    arr[k++] = temp[j++];
                }
            }
            
            while (i < mid) {
                arr[k++] = temp[i++];
            }
        }
    }
}