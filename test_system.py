#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FreeSwitch 机器人呼出系统测试脚本
用于测试各个模块的功能
"""

import os
import sys
import time
import json
import logging
from datetime import datetime

# 添加项目路径
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

# 由于目录名包含连字符，需要使用不同的导入方式
import importlib.util
import sys

# 导入 outbound_caller
spec = importlib.util.spec_from_file_location("outbound_caller", "src/robot/outbound_caller.py")
outbound_caller_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(outbound_caller_module)
OutboundCaller = outbound_caller_module.OutboundCaller

# 导入 intent_service
spec = importlib.util.spec_from_file_location("intent_service", "src/intent-recognition/intent_service.py")
intent_service_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(intent_service_module)
IntentService = intent_service_module.IntentService

# 导入 transfer_service
spec = importlib.util.spec_from_file_location("transfer_service", "src/agent-transfer/transfer_service.py")
transfer_service_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(transfer_service_module)
TransferService = transfer_service_module.TransferService
AgentStatus = transfer_service_module.AgentStatus

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class SystemTester:
    """系统测试器"""
    
    def __init__(self):
        self.test_results = {}
        
    def run_all_tests(self):
        """运行所有测试"""
        logger.info("开始系统功能测试")
        
        tests = [
            ("意图识别测试", self.test_intent_recognition),
            ("呼出服务测试", self.test_outbound_caller),
            ("转接服务测试", self.test_transfer_service),
            ("集成测试", self.test_integration)
        ]
        
        for test_name, test_func in tests:
            logger.info(f"运行测试: {test_name}")
            try:
                result = test_func()
                self.test_results[test_name] = {
                    'status': 'PASS' if result else 'FAIL',
                    'timestamp': datetime.now().isoformat()
                }
                logger.info(f"测试结果: {test_name} - {'PASS' if result else 'FAIL'}")
            except Exception as e:
                self.test_results[test_name] = {
                    'status': 'ERROR',
                    'error': str(e),
                    'timestamp': datetime.now().isoformat()
                }
                logger.error(f"测试异常: {test_name} - {str(e)}")
        
        self.print_test_summary()
    
    def test_intent_recognition(self):
        """测试意图识别功能"""
        try:
            service = IntentService()
            
            # 测试用例
            test_cases = [
                {
                    'input': '你好，我想咨询一下产品',
                    'expected_intent': 'GREETING',
                    'description': '问候语识别'
                },
                {
                    'input': '转人工客服',
                    'expected_intent': 'TRANSFER_TO_AGENT',
                    'description': '转人工意图识别'
                },
                {
                    'input': '我要投诉，你们的服务太差了',
                    'expected_intent': 'COMPLAINT',
                    'description': '投诉意图识别'
                },
                {
                    'input': '机器人不行，我要找真人',
                    'expected_intent': 'TRANSFER_TO_AGENT',
                    'description': '复杂转人工表达'
                },
                {
                    'input': '谢谢，再见',
                    'expected_intent': 'GOODBYE',
                    'description': '告别语识别'
                }
            ]
            
            passed = 0
            total = len(test_cases)
            
            for i, case in enumerate(test_cases):
                result = service.process_user_input(f"test_session_{i}", case['input'])
                intent_result = result['intent_result']
                
                if intent_result['intent'] == case['expected_intent']:
                    passed += 1
                    logger.info(f"✓ {case['description']}: {intent_result['intent']} (置信度: {intent_result['confidence']:.2f})")
                else:
                    logger.warning(f"✗ {case['description']}: 期望 {case['expected_intent']}, 实际 {intent_result['intent']}")
            
            success_rate = passed / total
            logger.info(f"意图识别测试完成: {passed}/{total} 通过 (成功率: {success_rate:.1%})")
            
            return success_rate >= 0.8  # 80% 成功率视为通过
            
        except Exception as e:
            logger.error(f"意图识别测试失败: {str(e)}")
            return False
    
    def test_outbound_caller(self):
        """测试呼出服务功能"""
        try:
            caller = OutboundCaller()
            
            # 测试添加任务
            test_phone = "13800138000"
            task_id = caller.add_call_task(
                customer_phone=test_phone,
                priority=5,
                campaign_id="TEST_CAMPAIGN",
                customer_data={
                    "name": "测试客户",
                    "level": "NORMAL"
                }
            )
            
            if not task_id:
                logger.error("添加呼出任务失败")
                return False
            
            logger.info(f"✓ 成功添加呼出任务: {task_id}")
            
            # 测试获取统计信息
            stats = caller.get_statistics()
            if not stats:
                logger.error("获取统计信息失败")
                return False
            
            logger.info(f"✓ 成功获取统计信息: {json.dumps(stats, ensure_ascii=False)}")
            
            # 测试号码验证
            valid_phone = caller.validate_phone_number("13800138000")
            invalid_phone = caller.validate_phone_number("123")
            
            if not valid_phone or invalid_phone:
                logger.error("号码验证功能异常")
                return False
            
            logger.info("✓ 号码验证功能正常")
            
            return True
            
        except Exception as e:
            logger.error(f"呼出服务测试失败: {str(e)}")
            return False
    
    def test_transfer_service(self):
        """测试转接服务功能"""
        try:
            service = TransferService()
            
            # 测试客服管理
            agent_manager = service.agent_manager
            
            # 获取客服列表
            agents = list(agent_manager.agents.values())
            if not agents:
                logger.error("没有找到客服信息")
                return False
            
            logger.info(f"✓ 找到 {len(agents)} 个客服")
            
            # 测试设置客服状态
            test_agent = agents[0]
            success = agent_manager.update_agent_status(test_agent.id, AgentStatus.ONLINE)
            if not success:
                logger.error("设置客服状态失败")
                return False
            
            logger.info(f"✓ 成功设置客服 {test_agent.name} 为在线状态")
            
            # 测试查找可用客服
            available_agents = agent_manager.get_available_agents()
            if not available_agents:
                logger.warning("没有可用客服")
            else:
                logger.info(f"✓ 找到 {len(available_agents)} 个可用客服")
            
            # 测试转接请求
            transfer_result = service.request_transfer(
                session_id="test_session_transfer",
                customer_phone="13800138000",
                customer_priority="NORMAL"
            )
            
            if transfer_result['status'] not in ['success', 'queued']:
                logger.error(f"转接请求失败: {transfer_result}")
                return False
            
            logger.info(f"✓ 转接请求成功: {transfer_result['status']}")
            
            # 测试获取统计信息
            stats = service.get_service_statistics()
            if not stats:
                logger.error("获取转接统计信息失败")
                return False
            
            logger.info(f"✓ 成功获取转接统计信息")
            
            return True
            
        except Exception as e:
            logger.error(f"转接服务测试失败: {str(e)}")
            return False
    
    def test_integration(self):
        """测试系统集成功能"""
        try:
            # 创建服务实例
            intent_service = IntentService()
            transfer_service = TransferService()
            
            # 模拟完整的转接流程
            session_id = "integration_test_session"
            
            # 1. 用户表达转人工意图
            user_input = "我要转人工客服"
            intent_result = intent_service.process_user_input(session_id, user_input)
            
            if not intent_result['should_transfer']:
                logger.error("系统未正确识别转人工需求")
                return False
            
            logger.info("✓ 系统正确识别转人工需求")
            
            # 2. 设置客服在线
            agent_manager = transfer_service.agent_manager
            agents = list(agent_manager.agents.values())
            if agents:
                agent_manager.update_agent_status(agents[0].id, AgentStatus.ONLINE)
                logger.info(f"✓ 设置客服 {agents[0].name} 在线")
            
            # 3. 请求转接
            transfer_result = transfer_service.request_transfer(
                session_id=session_id,
                customer_phone="13800138000",
                customer_priority="NORMAL"
            )
            
            if transfer_result['status'] not in ['success', 'queued']:
                logger.error(f"转接请求失败: {transfer_result}")
                return False
            
            logger.info(f"✓ 转接流程完成: {transfer_result['status']}")
            
            # 4. 测试多轮对话中的转接判断
            conversation_inputs = [
                "你好",
                "我有个问题",
                "你们的产品怎么样",
                "算了，我要找人工客服"
            ]
            
            for i, input_text in enumerate(conversation_inputs):
                result = intent_service.process_user_input(session_id, input_text)
                logger.info(f"对话轮次 {i+1}: {result['intent_result']['intent']} (转人工: {result['should_transfer']})")
            
            # 最后一轮应该触发转人工
            final_result = intent_service.process_user_input(session_id, "转人工")
            if not final_result['should_transfer']:
                logger.error("多轮对话转接判断失败")
                return False
            
            logger.info("✓ 多轮对话转接判断正确")
            
            return True
            
        except Exception as e:
            logger.error(f"集成测试失败: {str(e)}")
            return False
    
    def print_test_summary(self):
        """打印测试总结"""
        logger.info("=" * 60)
        logger.info("测试结果总结")
        logger.info("=" * 60)
        
        total_tests = len(self.test_results)
        passed_tests = sum(1 for r in self.test_results.values() if r['status'] == 'PASS')
        failed_tests = sum(1 for r in self.test_results.values() if r['status'] == 'FAIL')
        error_tests = sum(1 for r in self.test_results.values() if r['status'] == 'ERROR')
        
        for test_name, result in self.test_results.items():
            status_symbol = {
                'PASS': '✓',
                'FAIL': '✗',
                'ERROR': '⚠'
            }.get(result['status'], '?')
            
            logger.info(f"{status_symbol} {test_name}: {result['status']}")
            if result['status'] == 'ERROR':
                logger.info(f"    错误信息: {result.get('error', 'Unknown error')}")
        
        logger.info("-" * 60)
        logger.info(f"总计: {total_tests} 个测试")
        logger.info(f"通过: {passed_tests} 个")
        logger.info(f"失败: {failed_tests} 个")
        logger.info(f"错误: {error_tests} 个")
        logger.info(f"成功率: {passed_tests/total_tests:.1%}")
        logger.info("=" * 60)


def main():
    """主函数"""
    logger.info("FreeSwitch 机器人呼出系统测试")
    
    # 确保日志目录存在
    os.makedirs("logs", exist_ok=True)
    
    try:
        tester = SystemTester()
        tester.run_all_tests()
        
    except KeyboardInterrupt:
        logger.info("测试被用户中断")
    except Exception as e:
        logger.error(f"测试运行异常: {str(e)}")
        return 1
    
    return 0


if __name__ == "__main__":
    sys.exit(main())