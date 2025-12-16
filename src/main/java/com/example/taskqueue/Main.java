package com.example.taskqueue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

public final class Main {
  public static void main(String[] args) {
    int totalTasks = 200;
    CountDownLatch done = new CountDownLatch(totalTasks);

    try (TaskQueue queue = TaskQueue.builder()
        .coreThreads(4)
        .maxThreads(8)
        .queueCapacity(50)
        .rejectionPolicy(TaskQueue.RejectionPolicy.CALLER_RUNS)
        .threadNamePrefix("biz-worker")
        .keepAlive(Duration.ofSeconds(10))
        .build()) {

      for (int i = 0; i < totalTasks; i++) {
        int id = i;
        queue.submit(() -> {
          try {
            // 模拟业务耗时
            Thread.sleep(ThreadLocalRandom.current().nextInt(10, 80));
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            done.countDown();
          }

          if (id % 50 == 0) {
            System.out.printf("task=%d backlog=%d active=%d thread=%s%n",
                id, queue.backlog(), queue.activeWorkers(), Thread.currentThread().getName());
          }
        });
      }

      // 等任务做完，再优雅停机（try-with-resources 会自动 close）
      try {
        done.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      queue.shutdownGracefully(Duration.ofSeconds(5));
      System.out.println("all done");
    }
  }
}
