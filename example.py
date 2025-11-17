"""
指标调度并行执行示例
"""
import time
import random
from metric import Metric, MetricStatus
from scheduler import ParallelMetricScheduler


def example_metric_1():
    """示例指标1：计算简单值"""
    time.sleep(0.5)  # 模拟计算耗时
    return {"value": 100, "type": "metric1"}


def example_metric_2():
    """示例指标2：计算另一个值"""
    time.sleep(0.3)
    return {"value": 200, "type": "metric2"}


def example_metric_3():
    """示例指标3：依赖前两个指标的结果"""
    time.sleep(0.2)
    return {"value": 300, "type": "metric3", "depends_on": ["metric1", "metric2"]}


def example_metric_4():
    """示例指标4：可能失败的任务"""
    time.sleep(0.1)
    if random.random() < 0.3:  # 30% 概率失败
        raise ValueError("随机失败")
    return {"value": 400, "type": "metric4"}


def example_metric_5():
    """示例指标5：长时间运行的任务"""
    time.sleep(1.0)
    return {"value": 500, "type": "metric5"}


def main():
    """主函数：演示并行指标调度"""
    print("=" * 60)
    print("并行指标调度示例")
    print("=" * 60)
    
    # 创建指标列表
    metrics = [
        Metric(
            id="metric1",
            name="指标1",
            func=example_metric_1,
            priority=1
        ),
        Metric(
            id="metric2",
            name="指标2",
            func=example_metric_2,
            priority=2
        ),
        Metric(
            id="metric3",
            name="指标3（依赖1和2）",
            func=example_metric_3,
            priority=1,
            dependencies=["metric1", "metric2"]
        ),
        Metric(
            id="metric4",
            name="指标4（可能失败）",
            func=example_metric_4,
            priority=0
        ),
        Metric(
            id="metric5",
            name="指标5",
            func=example_metric_5,
            priority=1,
            timeout=2.0  # 2秒超时
        ),
    ]
    
    # 创建调度器（最大4个并行工作线程）
    scheduler = ParallelMetricScheduler(max_workers=4)
    
    print(f"\n开始执行 {len(metrics)} 个指标...")
    print(f"最大并行数: {scheduler.max_workers}")
    print("-" * 60)
    
    start_time = time.time()
    
    # 执行调度
    results = scheduler.schedule(metrics)
    
    end_time = time.time()
    total_duration = end_time - start_time
    
    # 打印结果
    print("\n执行结果:")
    print("-" * 60)
    for metric_id, result in results.items():
        status_icon = "✓" if result.status == MetricStatus.COMPLETED else "✗"
        print(f"{status_icon} {metric_id}:")
        print(f"  状态: {result.status.value}")
        if result.status == MetricStatus.COMPLETED:
            print(f"  结果: {result.result}")
        if result.error:
            print(f"  错误: {result.error}")
        if result.duration:
            print(f"  耗时: {result.duration:.3f}秒")
        print()
    
    print("-" * 60)
    print(f"总耗时: {total_duration:.3f}秒")
    
    # 统计
    completed = sum(1 for r in results.values() if r.status == MetricStatus.COMPLETED)
    failed = sum(1 for r in results.values() if r.status == MetricStatus.FAILED)
    print(f"成功: {completed}, 失败: {failed}")
    
    # 关闭调度器
    scheduler.shutdown()
    
    print("=" * 60)


if __name__ == "__main__":
    main()
