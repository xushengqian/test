#!/usr/bin/env python3
"""
FreeSWITCH 压力测试工具
用于测试 FreeSWITCH 服务器的性能极限
"""

import asyncio
import aiohttp
import time
import random
import argparse
import json
from datetime import datetime
import statistics

class FreeSwitchStressTest:
    def __init__(self, host="localhost", port=8021, password="ClueCon"):
        self.host = host
        self.port = port
        self.password = password
        self.api_url = f"http://{host}:{port}/api"
        self.stats = {
            'total_calls': 0,
            'successful_calls': 0,
            'failed_calls': 0,
            'call_durations': [],
            'setup_times': [],
            'teardown_times': []
        }
        
    async def make_call(self, session, from_number, to_number, duration=20):
        """模拟一个呼叫"""
        call_uuid = f"test-{random.randint(1000000, 9999999)}"
        start_time = time.time()
        
        try:
            # 发起呼叫
            originate_cmd = (
                f"originate {{origination_uuid={call_uuid},"
                f"origination_caller_id_number={from_number}}} "
                f"sofia/internal/{to_number}@{self.host} &park"
            )
            
            # 通过 API 执行命令
            async with session.post(
                self.api_url,
                data={'command': originate_cmd},
                auth=aiohttp.BasicAuth('freeswitch', self.password)
            ) as response:
                if response.status == 200:
                    setup_time = time.time() - start_time
                    self.stats['setup_times'].append(setup_time)
                    
                    # 保持呼叫
                    await asyncio.sleep(duration)
                    
                    # 挂断呼叫
                    hangup_start = time.time()
                    async with session.post(
                        self.api_url,
                        data={'command': f'uuid_kill {call_uuid}'},
                        auth=aiohttp.BasicAuth('freeswitch', self.password)
                    ) as hangup_response:
                        teardown_time = time.time() - hangup_start
                        self.stats['teardown_times'].append(teardown_time)
                    
                    total_duration = time.time() - start_time
                    self.stats['call_durations'].append(total_duration)
                    self.stats['successful_calls'] += 1
                    return True
                else:
                    self.stats['failed_calls'] += 1
                    return False
                    
        except Exception as e:
            print(f"呼叫失败: {e}")
            self.stats['failed_calls'] += 1
            return False
        finally:
            self.stats['total_calls'] += 1
    
    async def run_concurrent_calls(self, num_calls, duration, rate):
        """并发运行多个呼叫"""
        connector = aiohttp.TCPConnector(limit=1000)
        async with aiohttp.ClientSession(connector=connector) as session:
            tasks = []
            
            for i in range(num_calls):
                from_number = f"100{random.randint(1, 9999):04d}"
                to_number = f"200{random.randint(1, 9999):04d}"
                
                task = asyncio.create_task(
                    self.make_call(session, from_number, to_number, duration)
                )
                tasks.append(task)
                
                # 控制呼叫速率
                if rate > 0 and (i + 1) % rate == 0:
                    await asyncio.sleep(1)
            
            # 等待所有呼叫完成
            results = await asyncio.gather(*tasks)
            return results
    
    def print_statistics(self):
        """打印统计信息"""
        print("\n" + "=" * 60)
        print(" FreeSWITCH 压力测试结果")
        print("=" * 60)
        
        print(f"\n📊 呼叫统计:")
        print(f"  总呼叫数:        {self.stats['total_calls']}")
        print(f"  成功呼叫:        {self.stats['successful_calls']}")
        print(f"  失败呼叫:        {self.stats['failed_calls']}")
        success_rate = (self.stats['successful_calls'] / self.stats['total_calls'] * 100 
                       if self.stats['total_calls'] > 0 else 0)
        print(f"  成功率:          {success_rate:.2f}%")
        
        if self.stats['setup_times']:
            print(f"\n⏱️  时间统计:")
            print(f"  平均建立时间:    {statistics.mean(self.stats['setup_times']):.3f} 秒")
            print(f"  最快建立时间:    {min(self.stats['setup_times']):.3f} 秒")
            print(f"  最慢建立时间:    {max(self.stats['setup_times']):.3f} 秒")
        
        if self.stats['teardown_times']:
            print(f"  平均挂断时间:    {statistics.mean(self.stats['teardown_times']):.3f} 秒")
        
        if self.stats['call_durations']:
            print(f"  平均通话时长:    {statistics.mean(self.stats['call_durations']):.2f} 秒")
        
        print("\n" + "=" * 60)
    
    def save_results(self, filename="stress_test_results.json"):
        """保存测试结果"""
        results = {
            'timestamp': datetime.now().isoformat(),
            'statistics': {
                'total_calls': self.stats['total_calls'],
                'successful_calls': self.stats['successful_calls'],
                'failed_calls': self.stats['failed_calls'],
                'success_rate': (self.stats['successful_calls'] / self.stats['total_calls'] * 100 
                                if self.stats['total_calls'] > 0 else 0),
                'avg_setup_time': statistics.mean(self.stats['setup_times']) if self.stats['setup_times'] else 0,
                'avg_teardown_time': statistics.mean(self.stats['teardown_times']) if self.stats['teardown_times'] else 0,
                'avg_call_duration': statistics.mean(self.stats['call_durations']) if self.stats['call_durations'] else 0
            }
        }
        
        with open(filename, 'w') as f:
            json.dump(results, f, indent=2)
        print(f"\n结果已保存到: {filename}")

async def main():
    parser = argparse.ArgumentParser(description='FreeSWITCH 压力测试工具')
    parser.add_argument('--host', default='localhost',
                        help='FreeSWITCH 服务器地址')
    parser.add_argument('--port', type=int, default=8021,
                        help='Event Socket 端口')
    parser.add_argument('--password', default='ClueCon',
                        help='Event Socket 密码')
    parser.add_argument('--calls', type=int, default=100,
                        help='总呼叫数')
    parser.add_argument('--duration', type=int, default=20,
                        help='每个呼叫持续时间（秒）')
    parser.add_argument('--rate', type=int, default=10,
                        help='每秒发起的呼叫数（CPS）')
    parser.add_argument('--save', action='store_true',
                        help='保存测试结果到文件')
    
    args = parser.parse_args()
    
    print("=" * 60)
    print(" FreeSWITCH 压力测试工具")
    print("=" * 60)
    print(f"\n测试参数:")
    print(f"  服务器:          {args.host}:{args.port}")
    print(f"  总呼叫数:        {args.calls}")
    print(f"  呼叫持续时间:    {args.duration} 秒")
    print(f"  呼叫速率:        {args.rate} CPS")
    print(f"\n开始测试...")
    
    # 创建测试实例
    tester = FreeSwitchStressTest(args.host, args.port, args.password)
    
    # 记录开始时间
    start_time = time.time()
    
    # 运行压力测试
    await tester.run_concurrent_calls(args.calls, args.duration, args.rate)
    
    # 计算总耗时
    total_time = time.time() - start_time
    print(f"\n测试完成，总耗时: {total_time:.2f} 秒")
    
    # 打印统计
    tester.print_statistics()
    
    # 保存结果
    if args.save:
        tester.save_results()

if __name__ == "__main__":
    asyncio.run(main())