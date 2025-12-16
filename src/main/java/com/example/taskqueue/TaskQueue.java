package com.example.taskqueue;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 一个“多线程任务队列”封装：
 * - 使用有界 BlockingQueue 实现排队
 * - 线程池负责并发执行
 * - 支持背压/拒绝策略
 * - 支持优雅停机
 */
public final class TaskQueue implements AutoCloseable {

  public enum RejectionPolicy {
    /** 直接抛异常，交由调用方处理 */
    ABORT,
    /** 在调用线程执行（强背压，会阻塞/变慢提交方） */
    CALLER_RUNS,
    /** 丢弃当前任务 */
    DISCARD,
    /** 丢弃队列头部最旧任务，再尝试提交 */
    DISCARD_OLDEST,
    /** 阻塞提交方直到入队（自定义策略） */
    BLOCK
  }

  private final ThreadPoolExecutor executor;
  private final BlockingQueue<Runnable> queue;

  private TaskQueue(ThreadPoolExecutor executor, BlockingQueue<Runnable> queue) {
    this.executor = executor;
    this.queue = queue;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * 提交任务（Runnable）。
   * - 注意：如果策略是 BLOCK，在队列满时会阻塞调用线程。
   */
  public void submit(Runnable task) {
    Objects.requireNonNull(task, "task");
    executor.execute(task);
  }

  /** 队列当前积压（不含正在执行的线程） */
  public int backlog() {
    return queue.size();
  }

  /** 正在执行的线程数（近似） */
  public int activeWorkers() {
    return executor.getActiveCount();
  }

  /**
   * 优雅停机：先 shutdown()，等待超时后再 shutdownNow()。
   */
  public void shutdownGracefully(Duration timeout) {
    Objects.requireNonNull(timeout, "timeout");
    executor.shutdown();
    try {
      boolean ok = executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!ok) {
        executor.shutdownNow();
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      executor.shutdownNow();
    }
  }

  @Override
  public void close() {
    shutdownGracefully(Duration.ofSeconds(10));
  }

  public static final class Builder {
    private int coreThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
    private int maxThreads = coreThreads;
    private int queueCapacity = 10_000;
    private Duration keepAlive = Duration.ofSeconds(30);
    private String threadNamePrefix = "task-queue";
    private boolean daemonThreads = false;
    private RejectionPolicy rejectionPolicy = RejectionPolicy.CALLER_RUNS;

    public Builder coreThreads(int v) {
      this.coreThreads = v;
      return this;
    }

    public Builder maxThreads(int v) {
      this.maxThreads = v;
      return this;
    }

    public Builder queueCapacity(int v) {
      this.queueCapacity = v;
      return this;
    }

    public Builder keepAlive(Duration v) {
      this.keepAlive = v;
      return this;
    }

    public Builder threadNamePrefix(String v) {
      this.threadNamePrefix = v;
      return this;
    }

    public Builder daemonThreads(boolean v) {
      this.daemonThreads = v;
      return this;
    }

    public Builder rejectionPolicy(RejectionPolicy v) {
      this.rejectionPolicy = v;
      return this;
    }

    public TaskQueue build() {
      if (coreThreads <= 0) {
        throw new IllegalArgumentException("coreThreads must be > 0");
      }
      if (maxThreads < coreThreads) {
        throw new IllegalArgumentException("maxThreads must be >= coreThreads");
      }
      if (queueCapacity <= 0) {
        throw new IllegalArgumentException("queueCapacity must be > 0");
      }
      Objects.requireNonNull(keepAlive, "keepAlive");
      Objects.requireNonNull(threadNamePrefix, "threadNamePrefix");
      Objects.requireNonNull(rejectionPolicy, "rejectionPolicy");

      // 用有界队列，避免 OOM
      BlockingQueue<Runnable> q = new ArrayBlockingQueue<>(queueCapacity);

      RejectedExecutionHandler handler = switch (rejectionPolicy) {
        case ABORT -> new ThreadPoolExecutor.AbortPolicy();
        case CALLER_RUNS -> new ThreadPoolExecutor.CallerRunsPolicy();
        case DISCARD -> new ThreadPoolExecutor.DiscardPolicy();
        case DISCARD_OLDEST -> new ThreadPoolExecutor.DiscardOldestPolicy();
        case BLOCK -> new BlockThenRunPolicy(q);
      };

      ThreadPoolExecutor ex = new ThreadPoolExecutor(
          coreThreads,
          maxThreads,
          keepAlive.toMillis(),
          TimeUnit.MILLISECONDS,
          q,
          new NamedThreadFactory(threadNamePrefix, daemonThreads),
          handler
      );

      // 如果 maxThreads > coreThreads，允许核心线程也按 keepAlive 回收
      ex.allowCoreThreadTimeOut(maxThreads > coreThreads);

      return new TaskQueue(ex, q);
    }
  }

  /**
   * 自定义拒绝策略：当队列满时，阻塞提交方直到能入队。
   * 适合“必须处理且不想丢任务”的场景（但会把压力传回上游）。
   */
  static final class BlockThenRunPolicy implements RejectedExecutionHandler {
    private final BlockingQueue<Runnable> queue;

    BlockThenRunPolicy(BlockingQueue<Runnable> queue) {
      this.queue = queue;
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
      if (executor.isShutdown()) {
        throw new RejectedExecutionException("Executor already shutdown");
      }
      try {
        // 阻塞直到队列有空间
        queue.put(r);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new RejectedExecutionException("Interrupted while waiting to enqueue", ie);
      }
    }
  }
}
