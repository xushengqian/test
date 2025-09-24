"""
API客户端使用示例
演示如何通过REST API与指标调度系统交互
"""
import asyncio
import httpx
import json
from datetime import datetime, timedelta


class MetricSchedulerClient:
    """指标调度器API客户端"""
    
    def __init__(self, base_url: str = "http://localhost:8000"):
        self.base_url = base_url
        self.client = httpx.AsyncClient(timeout=30.0)
    
    async def close(self):
        """关闭客户端"""
        await self.client.aclose()
    
    async def get_health(self):
        """获取健康状态"""
        response = await self.client.get(f"{self.base_url}/health")
        return response.json()
    
    async def get_system_status(self):
        """获取系统状态"""
        response = await self.client.get(f"{self.base_url}/status")
        return response.json()
    
    async def create_metric(self, metric_data: dict):
        """创建指标定义"""
        response = await self.client.post(
            f"{self.base_url}/metrics",
            json=metric_data
        )
        response.raise_for_status()
        return response.json()
    
    async def list_metrics(self, **params):
        """获取指标列表"""
        response = await self.client.get(
            f"{self.base_url}/metrics",
            params=params
        )
        return response.json()
    
    async def get_metric(self, metric_id: int):
        """获取指标详情"""
        response = await self.client.get(f"{self.base_url}/metrics/{metric_id}")
        return response.json()
    
    async def create_schedule(self, schedule_data: dict):
        """创建调度配置"""
        response = await self.client.post(
            f"{self.base_url}/schedules",
            json=schedule_data
        )
        response.raise_for_status()
        return response.json()
    
    async def list_schedules(self, **params):
        """获取调度配置列表"""
        response = await self.client.get(
            f"{self.base_url}/schedules",
            params=params
        )
        return response.json()
    
    async def submit_task(self, metric_id: int, parameters: dict = None):
        """提交手动任务"""
        data = {"metric_id": metric_id}
        if parameters:
            data["parameters"] = parameters
        
        response = await self.client.post(
            f"{self.base_url}/tasks/submit",
            params=data
        )
        response.raise_for_status()
        return response.json()
    
    async def list_tasks(self, **params):
        """获取任务列表"""
        response = await self.client.get(
            f"{self.base_url}/tasks",
            params=params
        )
        return response.json()
    
    async def get_task(self, task_id: str):
        """获取任务详情"""
        response = await self.client.get(f"{self.base_url}/tasks/{task_id}")
        return response.json()
    
    async def cancel_task(self, task_id: str):
        """取消任务"""
        response = await self.client.post(f"{self.base_url}/tasks/{task_id}/cancel")
        response.raise_for_status()
        return response.json()
    
    async def get_task_statistics(self, start_date: str = None, end_date: str = None):
        """获取任务统计"""
        params = {}
        if start_date:
            params["start_date"] = start_date
        if end_date:
            params["end_date"] = end_date
        
        response = await self.client.get(
            f"{self.base_url}/statistics/tasks",
            params=params
        )
        return response.json()
    
    async def get_node_statistics(self):
        """获取节点统计"""
        response = await self.client.get(f"{self.base_url}/statistics/nodes")
        return response.json()
    
    async def list_nodes(self, status: str = None):
        """获取工作节点列表"""
        params = {}
        if status:
            params["status"] = status
        
        response = await self.client.get(
            f"{self.base_url}/nodes",
            params=params
        )
        return response.json()
    
    async def create_datasource(self, datasource_data: dict):
        """创建数据源"""
        response = await self.client.post(
            f"{self.base_url}/datasources",
            json=datasource_data
        )
        response.raise_for_status()
        return response.json()
    
    async def list_datasources(self, is_active: bool = None):
        """获取数据源列表"""
        params = {}
        if is_active is not None:
            params["is_active"] = is_active
        
        response = await self.client.get(
            f"{self.base_url}/datasources",
            params=params
        )
        return response.json()


async def demonstrate_api_usage():
    """演示API使用"""
    client = MetricSchedulerClient()
    
    try:
        print("=== 指标调度系统API使用示例 ===\n")
        
        # 1. 检查系统健康状态
        print("1. 检查系统健康状态")
        health = await client.get_health()
        print(f"健康状态: {health['healthy']}")
        print(f"时间戳: {health['timestamp']}")
        print()
        
        # 2. 获取系统状态
        print("2. 获取系统状态")
        status = await client.get_system_status()
        print(f"调度器运行状态: {status['scheduler']['running']}")
        print(f"任务统计: {status['scheduler']['statistics']['tasks']}")
        print()
        
        # 3. 创建数据源
        print("3. 创建数据源")
        datasource_data = {
            "name": "api_example_db",
            "type": "mysql",
            "host": "localhost",
            "port": 3306,
            "database_name": "example_db",
            "username": "example_user",
            "password": "example_password",
            "max_connections": 5,
            "is_active": True
        }
        
        try:
            datasource = await client.create_datasource(datasource_data)
            print(f"创建数据源成功: {datasource['name']}")
        except httpx.HTTPStatusError as e:
            if e.response.status_code == 400:
                print("数据源已存在，跳过创建")
            else:
                raise
        print()
        
        # 4. 创建指标定义
        print("4. 创建指标定义")
        metric_data = {
            "name": "api_example_metric",
            "description": "API示例指标",
            "sql_template": """
                SELECT 
                    '{{ current_date }}' as date,
                    COUNT(*) as total_count,
                    AVG(value) as avg_value
                FROM example_table
                WHERE created_at >= '{{ start_date }}'
                  AND created_at < '{{ end_date }}'
            """,
            "data_source": "api_example_db",
            "category": "api_examples",
            "tags": {"created_by": "api_client", "example": True},
            "timeout_seconds": 300,
            "retry_count": 3,
            "is_active": True
        }
        
        try:
            metric = await client.create_metric(metric_data)
            print(f"创建指标成功: {metric['name']} (ID: {metric['id']})")
            metric_id = metric['id']
        except httpx.HTTPStatusError as e:
            if e.response.status_code == 400:
                print("指标已存在，获取现有指标")
                metrics = await client.list_metrics(limit=1)
                if metrics:
                    metric_id = metrics[0]['id']
                    print(f"使用现有指标: {metrics[0]['name']} (ID: {metric_id})")
                else:
                    print("没有找到可用的指标")
                    return
            else:
                raise
        print()
        
        # 5. 创建调度配置
        print("5. 创建调度配置")
        schedule_data = {
            "metric_id": metric_id,
            "cron_expression": "*/10 * * * *",  # 每10分钟执行一次
            "priority": 3,
            "max_concurrent": 1,
            "is_enabled": True,
            "parameters": {
                "current_date": "{{ today }}",
                "start_date": "{{ yesterday }}",
                "end_date": "{{ today }}"
            }
        }
        
        try:
            schedule = await client.create_schedule(schedule_data)
            print(f"创建调度配置成功: ID {schedule['id']}")
        except httpx.HTTPStatusError as e:
            if e.response.status_code == 400:
                print("调度配置可能已存在")
            else:
                raise
        print()
        
        # 6. 提交手动任务
        print("6. 提交手动任务")
        task_result = await client.submit_task(
            metric_id=metric_id,
            parameters={
                "current_date": datetime.now().strftime("%Y-%m-%d"),
                "start_date": (datetime.now() - timedelta(days=1)).strftime("%Y-%m-%d"),
                "end_date": datetime.now().strftime("%Y-%m-%d")
            }
        )
        task_id = task_result['task_id']
        print(f"提交任务成功: {task_id}")
        print()
        
        # 7. 查询任务状态
        print("7. 查询任务状态")
        await asyncio.sleep(2)  # 等待任务开始执行
        
        try:
            task_detail = await client.get_task(task_id)
            print(f"任务状态: {task_detail['status']}")
            print(f"计划执行时间: {task_detail['scheduled_time']}")
            if task_detail.get('start_time'):
                print(f"开始执行时间: {task_detail['start_time']}")
            if task_detail.get('end_time'):
                print(f"结束执行时间: {task_detail['end_time']}")
                print(f"执行耗时: {task_detail.get('duration_ms', 0)}ms")
        except httpx.HTTPStatusError as e:
            print(f"查询任务失败: {e}")
        print()
        
        # 8. 获取任务列表
        print("8. 获取最近的任务列表")
        tasks = await client.list_tasks(limit=5)
        print(f"找到 {len(tasks)} 个任务:")
        for task in tasks:
            print(f"  - {task['task_id']}: {task['status']} (指标ID: {task['metric_id']})")
        print()
        
        # 9. 获取统计信息
        print("9. 获取统计信息")
        task_stats = await client.get_task_statistics()
        print("任务统计:")
        print(f"  总任务数: {task_stats['total_tasks']}")
        print(f"  待执行: {task_stats['pending_tasks']}")
        print(f"  执行中: {task_stats['running_tasks']}")
        print(f"  成功: {task_stats['success_tasks']}")
        print(f"  失败: {task_stats['failed_tasks']}")
        
        node_stats = await client.get_node_statistics()
        print("节点统计:")
        print(f"  总节点数: {node_stats['total_nodes']}")
        print(f"  在线节点: {node_stats['online_nodes']}")
        print(f"  总容量: {node_stats['total_capacity']}")
        print(f"  当前负载: {node_stats['current_load']}")
        print()
        
        # 10. 获取工作节点信息
        print("10. 获取工作节点信息")
        nodes = await client.list_nodes()
        print(f"找到 {len(nodes)} 个工作节点:")
        for node in nodes:
            print(f"  - {node['node_id']}: {node['status']} "
                  f"({node['current_tasks']}/{node['max_concurrent_tasks']})")
        print()
        
        print("=== API使用示例完成 ===")
        
    except Exception as e:
        print(f"API示例执行失败: {e}")
        raise
    
    finally:
        await client.close()


async def demonstrate_task_management():
    """演示任务管理功能"""
    client = MetricSchedulerClient()
    
    try:
        print("=== 任务管理示例 ===\n")
        
        # 获取第一个指标用于测试
        metrics = await client.list_metrics(limit=1)
        if not metrics:
            print("没有找到可用的指标")
            return
        
        metric_id = metrics[0]['id']
        print(f"使用指标: {metrics[0]['name']} (ID: {metric_id})")
        
        # 提交多个任务
        print("\n提交多个测试任务...")
        task_ids = []
        for i in range(3):
            result = await client.submit_task(
                metric_id=metric_id,
                parameters={"batch_id": f"test_batch_{i}"}
            )
            task_ids.append(result['task_id'])
            print(f"提交任务 {i+1}: {result['task_id']}")
        
        # 等待任务开始执行
        await asyncio.sleep(3)
        
        # 查询任务状态
        print("\n查询任务状态...")
        for task_id in task_ids:
            try:
                task = await client.get_task(task_id)
                print(f"{task_id}: {task['status']}")
            except httpx.HTTPStatusError:
                print(f"{task_id}: 查询失败")
        
        # 取消第一个任务（如果还在执行）
        if task_ids:
            print(f"\n尝试取消任务: {task_ids[0]}")
            try:
                result = await client.cancel_task(task_ids[0])
                print(f"取消结果: {result['message']}")
            except httpx.HTTPStatusError as e:
                print(f"取消失败: {e.response.json()}")
        
        print("\n=== 任务管理示例完成 ===")
        
    except Exception as e:
        print(f"任务管理示例执行失败: {e}")
        raise
    
    finally:
        await client.close()


async def main():
    """主函数"""
    print("启动API客户端示例...\n")
    
    try:
        # 基本API使用示例
        await demonstrate_api_usage()
        
        print("\n" + "="*50 + "\n")
        
        # 任务管理示例
        await demonstrate_task_management()
        
    except Exception as e:
        print(f"示例执行失败: {e}")
        return 1
    
    return 0


if __name__ == "__main__":
    exit_code = asyncio.run(main())
    exit(exit_code)