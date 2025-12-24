"""
线程池线程数设置示例 (Python)

线程池大小的设置原则：
1. CPU密集型任务：线程数 = CPU核心数 + 1
2. IO密集型任务：线程数 = CPU核心数 * (1 + IO等待时间/CPU计算时间)
3. 混合型任务：需要根据实际情况调整
"""

import os
import time
import concurrent.futures
import threading
from multiprocessing import cpu_count


class ThreadPoolExample:
    """线程池示例类"""
    
    def __init__(self):
        self.cpu_cores = cpu_count()
        print(f"CPU核心数: {self.cpu_cores}")
    
    def cpu_intensive_task(self, n):
        """CPU密集型任务示例"""
        result = sum(i * i for i in range(n))
        return result
    
    def io_intensive_task(self, duration):
        """IO密集型任务示例"""
        time.sleep(duration)  # 模拟IO等待
        return f"IO任务完成，耗时 {duration} 秒"
    
    def cpu_intensive_example(self):
        """CPU密集型任务线程池示例"""
        # CPU密集型：线程数 = CPU核心数 + 1
        thread_pool_size = self.cpu_cores + 1
        
        print(f"\n=== CPU密集型任务示例 ===")
        print(f"线程池大小: {thread_pool_size}")
        
        with concurrent.futures.ThreadPoolExecutor(max_workers=thread_pool_size) as executor:
            # 提交CPU密集型任务
            futures = [
                executor.submit(self.cpu_intensive_task, 1000000)
                for _ in range(10)
            ]
            
            # 获取结果
            results = []
            for future in concurrent.futures.as_completed(futures):
                try:
                    result = future.result()
                    results.append(result)
                    print(f"任务完成，结果: {result}")
                except Exception as e:
                    print(f"任务执行出错: {e}")
    
    def io_intensive_example(self):
        """IO密集型任务线程池示例"""
        # IO密集型：线程数 = CPU核心数 * 2 到 CPU核心数 * 4
        thread_pool_size = self.cpu_cores * 2
        
        print(f"\n=== IO密集型任务示例 ===")
        print(f"线程池大小: {thread_pool_size}")
        
        with concurrent.futures.ThreadPoolExecutor(max_workers=thread_pool_size) as executor:
            # 提交IO密集型任务
            futures = [
                executor.submit(self.io_intensive_task, 1)
                for _ in range(20)
            ]
            
            # 获取结果
            for i, future in enumerate(concurrent.futures.as_completed(futures)):
                try:
                    result = future.result()
                    print(f"任务 {i+1}: {result}")
                except Exception as e:
                    print(f"任务执行出错: {e}")
    
    def mixed_task_example(self):
        """混合型任务示例"""
        # 混合型任务：根据实际情况调整，通常设置为 CPU核心数 * 1.5 到 CPU核心数 * 2
        thread_pool_size = int(self.cpu_cores * 1.5)
        
        print(f"\n=== 混合型任务示例 ===")
        print(f"线程池大小: {thread_pool_size}")
        
        def mixed_task(task_id, task_type):
            """混合型任务"""
            if task_type == 'cpu':
                return self.cpu_intensive_task(500000)
            else:
                return self.io_intensive_task(0.5)
        
        with concurrent.futures.ThreadPoolExecutor(max_workers=thread_pool_size) as executor:
            tasks = []
            for i in range(15):
                task_type = 'cpu' if i % 2 == 0 else 'io'
                future = executor.submit(mixed_task, i, task_type)
                tasks.append((i, future))
            
            for task_id, future in tasks:
                try:
                    result = future.result()
                    print(f"任务 {task_id} 完成: {result}")
                except Exception as e:
                    print(f"任务 {task_id} 执行出错: {e}")
    
    def custom_thread_pool_example(self):
        """自定义线程池示例（使用ThreadPoolExecutor的更多参数）"""
        print(f"\n=== 自定义线程池示例 ===")
        
        # 创建自定义线程池
        executor = concurrent.futures.ThreadPoolExecutor(
            max_workers=self.cpu_cores * 2,
            thread_name_prefix="CustomPool"
        )
        
        def task_with_name(task_id):
            thread_name = threading.current_thread().name
            print(f"任务 {task_id} 在线程 {thread_name} 中执行")
            time.sleep(0.5)
            return f"任务 {task_id} 完成"
        
        # 提交任务
        futures = [executor.submit(task_with_name, i) for i in range(10)]
        
        # 获取结果
        for future in concurrent.futures.as_completed(futures):
            try:
                result = future.result()
                print(result)
            except Exception as e:
                print(f"任务执行出错: {e}")
        
        # 关闭线程池
        executor.shutdown(wait=True)
    
    def process_pool_example(self):
        """进程池示例（适合CPU密集型任务）"""
        print(f"\n=== 进程池示例（CPU密集型）===")
        print(f"进程池大小: {self.cpu_cores}")
        
        # 对于CPU密集型任务，使用进程池可能更合适（避免GIL限制）
        with concurrent.futures.ProcessPoolExecutor(max_workers=self.cpu_cores) as executor:
            futures = [
                executor.submit(self.cpu_intensive_task, 1000000)
                for _ in range(10)
            ]
            
            for i, future in enumerate(concurrent.futures.as_completed(futures)):
                try:
                    result = future.result()
                    print(f"进程任务 {i+1} 完成，结果: {result}")
                except Exception as e:
                    print(f"任务执行出错: {e}")


def main():
    """主函数"""
    print("=" * 50)
    print("线程池线程数设置示例")
    print("=" * 50)
    
    example = ThreadPoolExample()
    
    # 运行各种示例
    example.cpu_intensive_example()
    example.io_intensive_example()
    example.mixed_task_example()
    example.custom_thread_pool_example()
    example.process_pool_example()
    
    print("\n" + "=" * 50)
    print("所有示例执行完成")
    print("=" * 50)


if __name__ == "__main__":
    main()
