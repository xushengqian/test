package com.example.queue;

/**
 * 任务接口
 * 所有需要在线程池中执行的任务都应该实现此接口
 */
@FunctionalInterface
public interface Task {
    /**
     * 执行任务
     * @throws Exception 任务执行过程中可能抛出的异常
     */
    void execute() throws Exception;
}
