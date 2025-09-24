#!/usr/bin/env python3
"""
企业指标调度系统负载测试脚本
"""

import asyncio
import aiohttp
import time
import json
import sys
from typing import List, Dict, Any
from dataclasses import dataclass
from concurrent.futures import ThreadPoolExecutor
import statistics


@dataclass
class TestResult:
    """测试结果"""
    success: bool
    duration: float
    status_code: int
    error: str = ""


class LoadTester:
    """负载测试器"""
    
    def __init__(self, base_url: str = "http://localhost:8000"):
        self.base_url = base_url
        self.results: List[TestResult] = []
    
    async def single_request(self, session: aiohttp.ClientSession, url: str, method: str = "GET", data: Dict = None) -> TestResult:
        """单个请求"""
        start_time = time.time()
        
        try:
            if method.upper() == "GET":
                async with session.get(url) as response:
                    await response.text()
                    duration = time.time() - start_time
                    return TestResult(
                        success=response.status == 200,
                        duration=duration,
                        status_code=response.status
                    )
            elif method.upper() == "POST":
                async with session.post(url, json=data) as response:
                    await response.text()
                    duration = time.time() - start_time
                    return TestResult(
                        success=response.status == 200,
                        duration=duration,
                        status_code=response.status
                    )
        except Exception as e:
            duration = time.time() - start_time
            return TestResult(
                success=False,
                duration=duration,
                status_code=0,
                error=str(e)
            )
    
    async def load_test_endpoint(self, endpoint: str, concurrent_users: int, requests_per_user: int, method: str = "GET", data: Dict = None):
        """负载测试单个端点"""
        print(f"🚀 开始负载测试: {endpoint}")
        print(f"   并发用户: {concurrent_users}")
        print(f"   每用户请求数: {requests_per_user}")
        print(f"   总请求数: {concurrent_users * requests_per_user}")
        
        url = f"{self.base_url}{endpoint}"
        
        async with aiohttp.ClientSession() as session:
            tasks = []
            
            # 创建所有任务
            for user in range(concurrent_users):
                for request in range(requests_per_user):
                    task = self.single_request(session, url, method, data)
                    tasks.append(task)
            
            # 执行所有任务
            start_time = time.time()
            results = await asyncio.gather(*tasks)
            total_duration = time.time() - start_time
            
            # 统计结果
            successful_requests = sum(1 for r in results if r.success)
            failed_requests = len(results) - successful_requests
            
            response_times = [r.duration for r in results if r.success]
            
            if response_times:
                avg_response_time = statistics.mean(response_times)
                min_response_time = min(response_times)
                max_response_time = max(response_times)
                p95_response_time = statistics.quantiles(response_times, n=20)[18]  # 95th percentile
            else:
                avg_response_time = min_response_time = max_response_time = p95_response_time = 0
            
            requests_per_second = len(results) / total_duration
            
            print(f"✅ 测试完成:")
            print(f"   总耗时: {total_duration:.2f}s")
            print(f"   成功请求: {successful_requests}")
            print(f"   失败请求: {failed_requests}")
            print(f"   成功率: {(successful_requests/len(results)*100):.1f}%")
            print(f"   平均响应时间: {avg_response_time*1000:.2f}ms")
            print(f"   最小响应时间: {min_response_time*1000:.2f}ms")
            print(f"   最大响应时间: {max_response_time*1000:.2f}ms")
            print(f"   95%响应时间: {p95_response_time*1000:.2f}ms")
            print(f"   QPS: {requests_per_second:.2f}")
            
            return {
                "endpoint": endpoint,
                "total_requests": len(results),
                "successful_requests": successful_requests,
                "failed_requests": failed_requests,
                "success_rate": successful_requests/len(results)*100,
                "total_duration": total_duration,
                "avg_response_time_ms": avg_response_time*1000,
                "min_response_time_ms": min_response_time*1000,
                "max_response_time_ms": max_response_time*1000,
                "p95_response_time_ms": p95_response_time*1000,
                "qps": requests_per_second
            }
    
    async def comprehensive_load_test(self):
        """综合负载测试"""
        print("🧪 开始综合负载测试...")
        print("=" * 60)
        
        # 测试场景
        test_scenarios = [
            {
                "name": "健康检查",
                "endpoint": "/health",
                "concurrent_users": 50,
                "requests_per_user": 10,
                "method": "GET"
            },
            {
                "name": "企业列表查询",
                "endpoint": "/api/v1/metrics/companies",
                "concurrent_users": 20,
                "requests_per_user": 5,
                "method": "GET"
            },
            {
                "name": "指标定义查询",
                "endpoint": "/api/v1/metrics/definitions",
                "concurrent_users": 20,
                "requests_per_user": 5,
                "method": "GET"
            },
            {
                "name": "系统统计查询",
                "endpoint": "/api/v1/system/stats",
                "concurrent_users": 10,
                "requests_per_user": 3,
                "method": "GET"
            },
            {
                "name": "监控仪表板",
                "endpoint": "/api/v1/monitoring/dashboard",
                "concurrent_users": 10,
                "requests_per_user": 3,
                "method": "GET"
            }
        ]
        
        results = []
        
        for scenario in test_scenarios:
            print(f"\n📊 测试场景: {scenario['name']}")
            print("-" * 40)
            
            result = await self.load_test_endpoint(
                endpoint=scenario["endpoint"],
                concurrent_users=scenario["concurrent_users"],
                requests_per_user=scenario["requests_per_user"],
                method=scenario["method"],
                data=scenario.get("data")
            )
            
            result["scenario_name"] = scenario["name"]
            results.append(result)
            
            # 等待一下，避免对系统造成过大压力
            await asyncio.sleep(2)
        
        # 输出总结
        print("\n" + "=" * 60)
        print("📈 负载测试总结:")
        print("=" * 60)
        
        total_requests = sum(r["total_requests"] for r in results)
        total_successful = sum(r["successful_requests"] for r in results)
        total_failed = sum(r["failed_requests"] for r in results)
        overall_success_rate = (total_successful / total_requests * 100) if total_requests > 0 else 0
        
        print(f"总请求数: {total_requests}")
        print(f"成功请求: {total_successful}")
        print(f"失败请求: {total_failed}")
        print(f"总体成功率: {overall_success_rate:.1f}%")
        print()
        
        print("各场景详细结果:")
        print("-" * 60)
        for result in results:
            print(f"{result['scenario_name']:20} | "
                  f"成功率: {result['success_rate']:6.1f}% | "
                  f"平均响应: {result['avg_response_time_ms']:7.2f}ms | "
                  f"QPS: {result['qps']:6.2f}")
        
        return results
    
    async def stress_test_metric_execution(self):
        """指标执行压力测试"""
        print("\n🔥 开始指标执行压力测试...")
        print("=" * 60)
        
        # 首先获取可用的企业和指标
        async with aiohttp.ClientSession() as session:
            # 获取企业列表
            async with session.get(f"{self.base_url}/api/v1/metrics/companies?limit=5") as response:
                if response.status != 200:
                    print("❌ 无法获取企业列表")
                    return
                companies = await response.json()
            
            # 获取指标列表
            async with session.get(f"{self.base_url}/api/v1/metrics/definitions?limit=3") as response:
                if response.status != 200:
                    print("❌ 无法获取指标列表")
                    return
                metrics = await response.json()
        
        if not companies or not metrics:
            print("❌ 没有可用的企业或指标")
            return
        
        print(f"📊 使用 {len(companies)} 家企业和 {len(metrics)} 个指标进行测试")
        
        # 创建测试数据
        test_data = []
        for company in companies:
            for metric in metrics:
                test_data.append({
                    "company_id": company["id"],
                    "metric_id": metric["id"],
                    "execution_params": {"load_test": True}
                })
        
        print(f"🚀 准备执行 {len(test_data)} 个指标计算任务")
        
        # 执行压力测试
        concurrent_users = 5  # 降低并发数，避免过度压力
        requests_per_user = 2
        
        async with aiohttp.ClientSession() as session:
            tasks = []
            
            for user in range(concurrent_users):
                for request in range(requests_per_user):
                    # 随机选择一个测试数据
                    data = test_data[request % len(test_data)]
                    task = self.single_request(
                        session, 
                        f"{self.base_url}/api/v1/metrics/execute",
                        "POST",
                        data
                    )
                    tasks.append(task)
            
            start_time = time.time()
            results = await asyncio.gather(*tasks)
            total_duration = time.time() - start_time
            
            # 统计结果
            successful_requests = sum(1 for r in results if r.success)
            failed_requests = len(results) - successful_requests
            
            print(f"✅ 指标执行压力测试完成:")
            print(f"   总耗时: {total_duration:.2f}s")
            print(f"   成功请求: {successful_requests}")
            print(f"   失败请求: {failed_requests}")
            print(f"   成功率: {(successful_requests/len(results)*100):.1f}%")
            
            if successful_requests > 0:
                response_times = [r.duration for r in results if r.success]
                avg_response_time = statistics.mean(response_times)
                print(f"   平均响应时间: {avg_response_time*1000:.2f}ms")


async def main():
    """主函数"""
    base_url = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8000"
    
    print("🚀 企业指标调度系统负载测试")
    print(f"📡 测试地址: {base_url}")
    print()
    
    tester = LoadTester(base_url)
    
    # 检查系统是否可用
    print("⏳ 检查系统状态...")
    async with aiohttp.ClientSession() as session:
        try:
            async with session.get(f"{base_url}/health") as response:
                if response.status != 200:
                    print("❌ 系统不可用")
                    sys.exit(1)
                print("✅ 系统状态正常")
        except Exception as e:
            print(f"❌ 无法连接到系统: {e}")
            sys.exit(1)
    
    try:
        # 执行综合负载测试
        await tester.comprehensive_load_test()
        
        # 执行指标执行压力测试
        await tester.stress_test_metric_execution()
        
        print("\n🎉 负载测试完成！")
        
    except KeyboardInterrupt:
        print("\n⚠️  测试被用户中断")
    except Exception as e:
        print(f"\n❌ 测试过程中发生错误: {e}")
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())