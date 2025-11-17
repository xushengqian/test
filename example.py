"""
并行指标调度器使用示例
"""
import time
import random
from metric import MetricTask
from scheduler import ParallelMetricScheduler


def create_sample_metrics():
    """创建示例指标任务"""
    
    # 指标1：基础指标，无依赖
    def metric1():
        time.sleep(1)
        return {"value": 100, "timestamp": time.time()}
    
    # 指标2：依赖指标1
    def metric2():
        time.sleep(0.5)
        return {"value": 200, "timestamp": time.time()}
    
    # 指标3：依赖指标1和指标2
    def metric3():
        time.sleep(0.8)
        return {"value": 300, "timestamp": time.time()}
    
    # 指标4：独立指标，高优先级
    def metric4():
        time.sleep(0.3)
        return {"value": 400, "timestamp": time.time()}
    
    # 指标5：独立指标
    def metric5():
        time.sleep(0.6)
        return {"value": 500, "timestamp": time.time()}
    
    tasks = [
        MetricTask(
            metric_id="metric_1",
            name="基础指标1",
            execute_func=metric1,
            priority=1
        ),
        MetricTask(
            metric_id="metric_2",
            name="指标2（依赖指标1）",
            execute_func=metric2,
            priority=2,
            dependencies=["metric_1"]
        ),
        MetricTask(
            metric_id="metric_3",
            name="指标3（依赖指标1和2）",
            execute_func=metric3,
            priority=3,
            dependencies=["metric_1", "metric_2"]
        ),
        MetricTask(
            metric_id="metric_4",
            name="高优先级指标4",
            execute_func=metric4,
            priority=10  # 高优先级
        ),
        MetricTask(
            metric_id="metric_5",
            name="指标5",
            execute_func=metric5,
            priority=1
        ),
    ]
    
    return tasks


def main():
    """主函数"""
    print("=" * 60)
    print("并行指标调度器示例")
    print("=" * 60)
    
    # 创建调度器，设置最大并行数为3
    scheduler = ParallelMetricScheduler(max_workers=3, enable_priority=True)
    
    # 创建示例指标任务
    tasks = create_sample_metrics()
    
    # 提交所有任务
    print(f"\n提交 {len(tasks)} 个指标任务...")
    scheduler.submit_batch(tasks)
    
    # 等待所有任务完成
    print("\n等待所有任务完成...")
    start_time = time.time()
    results = scheduler.wait_all()
    total_time = time.time() - start_time
    
    # 打印结果
    print("\n" + "=" * 60)
    print("执行结果汇总")
    print("=" * 60)
    
    for metric_id, result in results.items():
        status_icon = "✓" if result.status.value == "completed" else "✗"
        print(f"\n{status_icon} {result.metric_id}:")
        print(f"  状态: {result.status.value}")
        print(f"  执行时间: {result.execution_time:.2f}秒")
        if result.status.value == "completed":
            print(f"  结果: {result.result}")
        else:
            print(f"  错误: {result.error}")
    
    print("\n" + "=" * 60)
    print(f"总耗时: {total_time:.2f}秒")
    print(f"并行度: {scheduler.max_workers}")
    print("=" * 60)
    
    # 关闭调度器
    scheduler.shutdown()


if __name__ == "__main__":
    main()
