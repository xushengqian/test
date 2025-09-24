#!/usr/bin/env python3
"""
企业指标调度系统API测试脚本
"""

import requests
import json
import time
import sys
from typing import Dict, Any


class MetricsAPITester:
    """API测试器"""
    
    def __init__(self, base_url: str = "http://localhost:8000"):
        self.base_url = base_url
        self.session = requests.Session()
    
    def test_health(self) -> bool:
        """测试健康检查"""
        print("🏥 测试健康检查...")
        try:
            response = self.session.get(f"{self.base_url}/health")
            if response.status_code == 200:
                print("✅ 健康检查通过")
                return True
            else:
                print(f"❌ 健康检查失败: {response.status_code}")
                return False
        except Exception as e:
            print(f"❌ 健康检查异常: {e}")
            return False
    
    def test_companies(self) -> bool:
        """测试企业列表API"""
        print("🏢 测试企业列表API...")
        try:
            response = self.session.get(f"{self.base_url}/api/v1/metrics/companies")
            if response.status_code == 200:
                companies = response.json()
                print(f"✅ 获取到 {len(companies)} 家企业")
                for company in companies[:3]:  # 显示前3家
                    print(f"   - {company['name']} ({company['code']})")
                return True
            else:
                print(f"❌ 获取企业列表失败: {response.status_code}")
                return False
        except Exception as e:
            print(f"❌ 企业列表API异常: {e}")
            return False
    
    def test_metrics_definitions(self) -> bool:
        """测试指标定义API"""
        print("📊 测试指标定义API...")
        try:
            response = self.session.get(f"{self.base_url}/api/v1/metrics/definitions")
            if response.status_code == 200:
                metrics = response.json()
                print(f"✅ 获取到 {len(metrics)} 个指标定义")
                for metric in metrics[:3]:  # 显示前3个
                    print(f"   - {metric['name']} ({metric['code']}) - 优先级: {metric['priority']}")
                return True
            else:
                print(f"❌ 获取指标定义失败: {response.status_code}")
                return False
        except Exception as e:
            print(f"❌ 指标定义API异常: {e}")
            return False
    
    def test_execute_metric(self) -> bool:
        """测试执行单个指标"""
        print("⚡ 测试执行单个指标...")
        try:
            # 先获取一个企业和指标
            companies_response = self.session.get(f"{self.base_url}/api/v1/metrics/companies")
            metrics_response = self.session.get(f"{self.base_url}/api/v1/metrics/definitions")
            
            if companies_response.status_code != 200 or metrics_response.status_code != 200:
                print("❌ 无法获取企业或指标信息")
                return False
            
            companies = companies_response.json()
            metrics = metrics_response.json()
            
            if not companies or not metrics:
                print("❌ 没有可用的企业或指标")
                return False
            
            # 执行指标
            payload = {
                "company_id": companies[0]["id"],
                "metric_id": metrics[0]["id"],
                "execution_params": {"test": True}
            }
            
            response = self.session.post(
                f"{self.base_url}/api/v1/metrics/execute",
                json=payload
            )
            
            if response.status_code == 200:
                result = response.json()
                print(f"✅ 指标执行已调度: {result['execution_id']}")
                print(f"   企业: {companies[0]['name']}")
                print(f"   指标: {metrics[0]['name']}")
                return True
            else:
                print(f"❌ 指标执行失败: {response.status_code}")
                print(f"   响应: {response.text}")
                return False
        except Exception as e:
            print(f"❌ 指标执行API异常: {e}")
            return False
    
    def test_system_stats(self) -> bool:
        """测试系统统计API"""
        print("📈 测试系统统计API...")
        try:
            response = self.session.get(f"{self.base_url}/api/v1/system/stats")
            if response.status_code == 200:
                stats = response.json()
                print("✅ 系统统计信息:")
                print(f"   数据库连接: {stats['database_connections']['active_connections']}/{stats['database_connections']['pool_size']}")
                print(f"   活跃任务: {stats['task_queue_stats']['active_tasks']}/{stats['task_queue_stats']['max_concurrent']}")
                print(f"   CPU使用率: {stats['system_resources']['cpu_usage_percent']:.1f}%")
                print(f"   内存使用率: {stats['system_resources']['memory_usage_percent']:.1f}%")
                return True
            else:
                print(f"❌ 获取系统统计失败: {response.status_code}")
                return False
        except Exception as e:
            print(f"❌ 系统统计API异常: {e}")
            return False
    
    def test_monitoring_dashboard(self) -> bool:
        """测试监控仪表板API"""
        print("📊 测试监控仪表板API...")
        try:
            response = self.session.get(f"{self.base_url}/api/v1/monitoring/dashboard")
            if response.status_code == 200:
                dashboard = response.json()
                print("✅ 监控仪表板数据:")
                print(f"   系统资源: CPU {dashboard['system_resources']['cpu_usage_percent']:.1f}%, 内存 {dashboard['system_resources']['memory_usage_percent']:.1f}%")
                print(f"   数据库: {dashboard['database']['active_connections']} 活跃连接")
                print(f"   任务队列: {dashboard['task_queue']['active_tasks']} 活跃任务")
                print(f"   告警: {dashboard['alerts']['active_alerts']} 活跃告警")
                return True
            else:
                print(f"❌ 获取监控仪表板失败: {response.status_code}")
                return False
        except Exception as e:
            print(f"❌ 监控仪表板API异常: {e}")
            return False
    
    def test_batch_execute(self) -> bool:
        """测试批量执行指标"""
        print("🚀 测试批量执行指标...")
        try:
            # 获取企业和指标
            companies_response = self.session.get(f"{self.base_url}/api/v1/metrics/companies?limit=2")
            metrics_response = self.session.get(f"{self.base_url}/api/v1/metrics/definitions?limit=2")
            
            if companies_response.status_code != 200 or metrics_response.status_code != 200:
                print("❌ 无法获取企业或指标信息")
                return False
            
            companies = companies_response.json()
            metrics = metrics_response.json()
            
            if len(companies) < 2 or len(metrics) < 2:
                print("❌ 企业或指标数量不足")
                return False
            
            # 批量执行
            payload = {
                "company_ids": [companies[0]["id"], companies[1]["id"]],
                "metric_ids": [metrics[0]["id"], metrics[1]["id"]],
                "batch_params": {"test_batch": True}
            }
            
            response = self.session.post(
                f"{self.base_url}/api/v1/metrics/batch-execute",
                json=payload
            )
            
            if response.status_code == 200:
                result = response.json()
                print(f"✅ 批量执行已调度: {result['batch_id']}")
                print(f"   总任务数: {result['total_tasks']}")
                print(f"   企业数: {len(payload['company_ids'])}")
                print(f"   指标数: {len(payload['metric_ids'])}")
                return True
            else:
                print(f"❌ 批量执行失败: {response.status_code}")
                print(f"   响应: {response.text}")
                return False
        except Exception as e:
            print(f"❌ 批量执行API异常: {e}")
            return False
    
    def run_all_tests(self) -> bool:
        """运行所有测试"""
        print("🧪 开始API测试...")
        print("=" * 50)
        
        tests = [
            ("健康检查", self.test_health),
            ("企业列表", self.test_companies),
            ("指标定义", self.test_metrics_definitions),
            ("系统统计", self.test_system_stats),
            ("监控仪表板", self.test_monitoring_dashboard),
            ("执行单个指标", self.test_execute_metric),
            ("批量执行指标", self.test_batch_execute),
        ]
        
        passed = 0
        total = len(tests)
        
        for test_name, test_func in tests:
            print(f"\n📋 {test_name}测试:")
            try:
                if test_func():
                    passed += 1
                else:
                    print(f"❌ {test_name}测试失败")
            except Exception as e:
                print(f"❌ {test_name}测试异常: {e}")
            
            time.sleep(1)  # 避免请求过快
        
        print("\n" + "=" * 50)
        print(f"🎯 测试结果: {passed}/{total} 通过")
        
        if passed == total:
            print("🎉 所有测试通过！")
            return True
        else:
            print("⚠️  部分测试失败，请检查系统状态")
            return False


def main():
    """主函数"""
    base_url = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8000"
    
    print(f"🚀 企业指标调度系统API测试")
    print(f"📡 测试地址: {base_url}")
    print()
    
    tester = MetricsAPITester(base_url)
    
    # 等待系统启动
    print("⏳ 等待系统启动...")
    for i in range(30):
        if tester.test_health():
            break
        print(f"   等待中... ({i+1}/30)")
        time.sleep(2)
    else:
        print("❌ 系统启动超时")
        sys.exit(1)
    
    # 运行测试
    success = tester.run_all_tests()
    
    if success:
        print("\n✅ 所有测试完成，系统运行正常！")
        sys.exit(0)
    else:
        print("\n❌ 测试失败，请检查系统状态")
        sys.exit(1)


if __name__ == "__main__":
    main()