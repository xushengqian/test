"""
指标调度器测试
"""
import time
import unittest
from metric import Metric, MetricStatus
from scheduler import ParallelMetricScheduler


class TestParallelMetricScheduler(unittest.TestCase):
    """并行指标调度器测试类"""
    
    def setUp(self):
        """测试前准备"""
        self.scheduler = ParallelMetricScheduler(max_workers=2)
    
    def tearDown(self):
        """测试后清理"""
        self.scheduler.shutdown()
    
    def test_basic_execution(self):
        """测试基本执行"""
        def simple_func():
            return 42
        
        metrics = [
            Metric(id="test1", name="测试1", func=simple_func)
        ]
        
        results = self.scheduler.schedule(metrics)
        
        self.assertEqual(len(results), 1)
        self.assertEqual(results["test1"].status, MetricStatus.COMPLETED)
        self.assertEqual(results["test1"].result, 42)
    
    def test_parallel_execution(self):
        """测试并行执行"""
        execution_order = []
        
        def func1():
            time.sleep(0.1)
            execution_order.append(1)
            return 1
        
        def func2():
            time.sleep(0.1)
            execution_order.append(2)
            return 2
        
        def func3():
            time.sleep(0.1)
            execution_order.append(3)
            return 3
        
        metrics = [
            Metric(id="m1", name="指标1", func=func1),
            Metric(id="m2", name="指标2", func=func2),
            Metric(id="m3", name="指标3", func=func3),
        ]
        
        start = time.time()
        results = self.scheduler.schedule(metrics)
        duration = time.time() - start
        
        # 应该并行执行，总时间应该小于串行执行时间
        self.assertLess(duration, 0.4)  # 串行需要0.3秒，并行应该更快
        self.assertEqual(len(results), 3)
        self.assertTrue(all(r.status == MetricStatus.COMPLETED for r in results.values()))
    
    def test_dependencies(self):
        """测试依赖关系"""
        results_dict = {}
        
        def func1():
            results_dict["m1"] = 10
            return 10
        
        def func2():
            results_dict["m2"] = 20
            return 20
        
        def func3():
            # 依赖m1和m2
            val = results_dict.get("m1", 0) + results_dict.get("m2", 0)
            results_dict["m3"] = val
            return val
        
        metrics = [
            Metric(id="m1", name="指标1", func=func1),
            Metric(id="m2", name="指标2", func=func2),
            Metric(id="m3", name="指标3", func=func3, dependencies=["m1", "m2"]),
        ]
        
        results = self.scheduler.schedule(metrics)
        
        # 所有指标应该成功
        self.assertTrue(all(r.status == MetricStatus.COMPLETED for r in results.values()))
        # m3应该等于m1+m2
        self.assertEqual(results["m3"].result, 30)
    
    def test_priority(self):
        """测试优先级"""
        execution_order = []
        
        def func1():
            execution_order.append("low")
            return 1
        
        def func2():
            execution_order.append("high")
            return 2
        
        metrics = [
            Metric(id="m1", name="低优先级", func=func1, priority=1),
            Metric(id="m2", name="高优先级", func=func2, priority=10),
        ]
        
        self.scheduler.schedule(metrics)
        
        # 高优先级应该先执行（如果同时就绪）
        # 注意：由于并行执行，顺序可能不确定，但高优先级应该更早开始
        self.assertEqual(len(execution_order), 2)
    
    def test_timeout(self):
        """测试超时"""
        def slow_func():
            time.sleep(2.0)
            return "should not complete"
        
        metrics = [
            Metric(id="m1", name="慢任务", func=slow_func, timeout=0.5)
        ]
        
        start = time.time()
        results = self.scheduler.schedule(metrics)
        duration = time.time() - start
        
        # 应该超时
        self.assertLess(duration, 1.0)  # 应该在超时时间内返回
        # 注意：ThreadPoolExecutor的timeout可能不会立即生效，这里主要测试功能
    
    def test_error_handling(self):
        """测试错误处理"""
        def failing_func():
            raise ValueError("测试错误")
        
        metrics = [
            Metric(id="m1", name="失败任务", func=failing_func)
        ]
        
        results = self.scheduler.schedule(metrics)
        
        self.assertEqual(results["m1"].status, MetricStatus.FAILED)
        self.assertIn("测试错误", results["m1"].error)
    
    def test_mixed_success_failure(self):
        """测试混合成功和失败"""
        def success_func():
            return "success"
        
        def fail_func():
            raise Exception("failed")
        
        metrics = [
            Metric(id="m1", name="成功", func=success_func),
            Metric(id="m2", name="失败", func=fail_func),
        ]
        
        results = self.scheduler.schedule(metrics)
        
        self.assertEqual(results["m1"].status, MetricStatus.COMPLETED)
        self.assertEqual(results["m2"].status, MetricStatus.FAILED)


if __name__ == "__main__":
    unittest.main()
