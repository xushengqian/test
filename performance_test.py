#!/usr/bin/env python3
"""
性能测试脚本
测试FreeSWITCH音转文系统在高负载下的表现
"""

import asyncio
import aiohttp
import time
import json
import logging
import statistics
from typing import List, Dict, Any
import random
import string

# 设置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class PerformanceTester:
    def __init__(self, base_url="http://localhost:8000"):
        self.base_url = base_url
        self.session = None
        self.results = []
    
    async def __aenter__(self):
        self.session = aiohttp.ClientSession()
        return self
    
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self.session:
            await self.session.close()
    
    async def test_concurrent_calls(self, num_calls: int = 10):
        """测试并发通话"""
        logger.info(f"开始测试 {num_calls} 个并发通话...")
        
        tasks = []
        for i in range(num_calls):
            phone_number = f"138{random.randint(10000000, 99999999)}"
            task = asyncio.create_task(self._make_call(phone_number, f"agent_{i}"))
            tasks.append(task)
        
        start_time = time.time()
        results = await asyncio.gather(*tasks, return_exceptions=True)
        end_time = time.time()
        
        successful_calls = [r for r in results if isinstance(r, dict) and r.get('status') == 'started']
        failed_calls = [r for r in results if isinstance(r, Exception) or (isinstance(r, dict) and r.get('status') != 'started')]
        
        logger.info(f"并发通话测试完成:")
        logger.info(f"  成功: {len(successful_calls)}")
        logger.info(f"  失败: {len(failed_calls)}")
        logger.info(f"  总耗时: {end_time - start_time:.2f}秒")
        logger.info(f"  平均耗时: {(end_time - start_time) / num_calls:.2f}秒/通话")
        
        return {
            "total_calls": num_calls,
            "successful": len(successful_calls),
            "failed": len(failed_calls),
            "total_time": end_time - start_time,
            "avg_time_per_call": (end_time - start_time) / num_calls,
            "calls_per_second": num_calls / (end_time - start_time)
        }
    
    async def test_websocket_connections(self, num_connections: int = 20):
        """测试WebSocket连接"""
        logger.info(f"开始测试 {num_connections} 个WebSocket连接...")
        
        connections = []
        start_time = time.time()
        
        try:
            # 建立连接
            for i in range(num_connections):
                call_id = f"test_call_{i}_{int(time.time())}"
                ws_url = f"ws://localhost:8000/ws/{call_id}"
                
                try:
                    ws = await self.session.ws_connect(ws_url)
                    connections.append(ws)
                except Exception as e:
                    logger.error(f"连接 {i} 失败: {e}")
            
            connect_time = time.time() - start_time
            logger.info(f"建立了 {len(connections)} 个WebSocket连接，耗时 {connect_time:.2f}秒")
            
            # 发送测试数据
            test_data = b"test audio data" * 100  # 模拟音频数据
            send_start = time.time()
            
            for ws in connections:
                await ws.send_bytes(test_data)
            
            send_time = time.time() - send_start
            logger.info(f"发送测试数据完成，耗时 {send_time:.2f}秒")
            
            # 等待一段时间
            await asyncio.sleep(5)
            
            # 关闭连接
            for ws in connections:
                await ws.close()
            
            total_time = time.time() - start_time
            
            return {
                "total_connections": num_connections,
                "successful_connections": len(connections),
                "connect_time": connect_time,
                "send_time": send_time,
                "total_time": total_time
            }
            
        except Exception as e:
            logger.error(f"WebSocket测试失败: {e}")
            return {"error": str(e)}
    
    async def test_api_performance(self, num_requests: int = 100):
        """测试API性能"""
        logger.info(f"开始测试 {num_requests} 个API请求...")
        
        # 测试健康检查接口
        health_times = []
        for i in range(num_requests):
            start = time.time()
            try:
                async with self.session.get(f"{self.base_url}/api/health") as resp:
                    await resp.json()
                health_times.append(time.time() - start)
            except Exception as e:
                logger.error(f"健康检查请求 {i} 失败: {e}")
        
        # 测试通话列表接口
        calls_times = []
        for i in range(num_requests):
            start = time.time()
            try:
                async with self.session.get(f"{self.base_url}/api/calls") as resp:
                    await resp.json()
                calls_times.append(time.time() - start)
            except Exception as e:
                logger.error(f"通话列表请求 {i} 失败: {e}")
        
        return {
            "health_check": {
                "total_requests": len(health_times),
                "avg_response_time": statistics.mean(health_times) if health_times else 0,
                "min_response_time": min(health_times) if health_times else 0,
                "max_response_time": max(health_times) if health_times else 0,
                "p95_response_time": self._percentile(health_times, 95) if health_times else 0
            },
            "calls_list": {
                "total_requests": len(calls_times),
                "avg_response_time": statistics.mean(calls_times) if calls_times else 0,
                "min_response_time": min(calls_times) if calls_times else 0,
                "max_response_time": max(calls_times) if calls_times else 0,
                "p95_response_time": self._percentile(calls_times, 95) if calls_times else 0
            }
        }
    
    async def test_system_limits(self):
        """测试系统限制"""
        logger.info("开始测试系统限制...")
        
        # 测试并发通话限制
        logger.info("测试并发通话限制...")
        concurrent_results = []
        for num_calls in [1, 3, 5, 8, 10, 15, 20]:
            result = await self.test_concurrent_calls(num_calls)
            concurrent_results.append({
                "num_calls": num_calls,
                "success_rate": result["successful"] / result["total_calls"] * 100,
                "calls_per_second": result["calls_per_second"]
            })
            await asyncio.sleep(2)  # 等待系统恢复
        
        # 测试WebSocket连接限制
        logger.info("测试WebSocket连接限制...")
        ws_results = []
        for num_connections in [5, 10, 20, 30, 50]:
            result = await self.test_websocket_connections(num_connections)
            if "error" not in result:
                ws_results.append({
                    "num_connections": num_connections,
                    "success_rate": result["successful_connections"] / result["total_connections"] * 100,
                    "connect_time": result["connect_time"]
                })
            await asyncio.sleep(2)
        
        return {
            "concurrent_calls": concurrent_results,
            "websocket_connections": ws_results
        }
    
    async def _make_call(self, phone_number: str, agent_id: str) -> Dict[str, Any]:
        """发起单个通话"""
        try:
            data = {
                "phone_number": phone_number,
                "agent_id": agent_id
            }
            
            async with self.session.post(
                f"{self.base_url}/api/calls/start",
                data=data
            ) as resp:
                result = await resp.json()
                if resp.status == 200:
                    return result
                else:
                    return {"error": result.get("detail", "Unknown error")}
        except Exception as e:
            return {"error": str(e)}
    
    def _percentile(self, data: List[float], percentile: int) -> float:
        """计算百分位数"""
        if not data:
            return 0
        sorted_data = sorted(data)
        index = int(len(sorted_data) * percentile / 100)
        return sorted_data[min(index, len(sorted_data) - 1)]
    
    async def run_full_test(self):
        """运行完整性能测试"""
        logger.info("🚀 开始完整性能测试...")
        
        test_results = {}
        
        # 1. API性能测试
        logger.info("1. API性能测试")
        test_results["api_performance"] = await self.test_api_performance(50)
        
        # 2. 并发通话测试
        logger.info("2. 并发通话测试")
        test_results["concurrent_calls"] = await self.test_concurrent_calls(10)
        
        # 3. WebSocket连接测试
        logger.info("3. WebSocket连接测试")
        test_results["websocket_connections"] = await self.test_websocket_connections(20)
        
        # 4. 系统限制测试
        logger.info("4. 系统限制测试")
        test_results["system_limits"] = await self.test_system_limits()
        
        # 5. 系统健康检查
        logger.info("5. 系统健康检查")
        try:
            async with self.session.get(f"{self.base_url}/api/health") as resp:
                test_results["system_health"] = await resp.json()
        except Exception as e:
            test_results["system_health"] = {"error": str(e)}
        
        # 保存测试结果
        with open("performance_test_results.json", "w", encoding="utf-8") as f:
            json.dump(test_results, f, indent=2, ensure_ascii=False)
        
        logger.info("✅ 性能测试完成！结果已保存到 performance_test_results.json")
        
        # 打印摘要
        self._print_summary(test_results)
        
        return test_results
    
    def _print_summary(self, results: Dict[str, Any]):
        """打印测试结果摘要"""
        print("\n" + "="*50)
        print("📊 性能测试结果摘要")
        print("="*50)
        
        # API性能
        if "api_performance" in results:
            api = results["api_performance"]
            print(f"\n🔗 API性能:")
            print(f"  健康检查 - 平均响应时间: {api['health_check']['avg_response_time']:.3f}秒")
            print(f"  通话列表 - 平均响应时间: {api['calls_list']['avg_response_time']:.3f}秒")
        
        # 并发通话
        if "concurrent_calls" in results:
            calls = results["concurrent_calls"]
            print(f"\n📞 并发通话:")
            print(f"  成功通话: {calls['successful']}/{calls['total_calls']}")
            print(f"  成功率: {calls['successful']/calls['total_calls']*100:.1f}%")
            print(f"  通话/秒: {calls['calls_per_second']:.2f}")
        
        # WebSocket连接
        if "websocket_connections" in results:
            ws = results["websocket_connections"]
            print(f"\n🔌 WebSocket连接:")
            print(f"  成功连接: {ws['successful_connections']}/{ws['total_connections']}")
            print(f"  连接时间: {ws['connect_time']:.3f}秒")
        
        # 系统健康
        if "system_health" in results and "error" not in results["system_health"]:
            health = results["system_health"]
            print(f"\n💚 系统健康:")
            print(f"  状态: {health['status']}")
            if "performance" in health:
                perf = health["performance"]
                print(f"  WebSocket连接数: {perf.get('websocket_connections', 0)}")
                print(f"  活跃通话数: {perf.get('active_calls', 0)}")
        
        print("\n" + "="*50)

async def main():
    """主函数"""
    async with PerformanceTester() as tester:
        await tester.run_full_test()

if __name__ == "__main__":
    asyncio.run(main())