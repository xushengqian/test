#!/usr/bin/env python3
"""
系统测试脚本
测试FreeSWITCH人工外呼实时音转文系统的基本功能
"""

import asyncio
import httpx
import json
import time
import logging

# 设置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class SystemTester:
    def __init__(self, base_url="http://localhost:8000"):
        self.base_url = base_url
        self.client = httpx.AsyncClient()
    
    async def test_health(self):
        """测试系统健康状态"""
        try:
            response = await self.client.get(f"{self.base_url}/")
            if response.status_code == 200:
                logger.info("✅ Web界面可访问")
                return True
            else:
                logger.error(f"❌ Web界面访问失败: {response.status_code}")
                return False
        except Exception as e:
            logger.error(f"❌ 连接失败: {e}")
            return False
    
    async def test_api_endpoints(self):
        """测试API端点"""
        try:
            # 测试获取通话列表
            response = await self.client.get(f"{self.base_url}/api/calls")
            if response.status_code == 200:
                logger.info("✅ 通话列表API正常")
            else:
                logger.error(f"❌ 通话列表API失败: {response.status_code}")
            
            # 测试发起外呼（使用测试号码）
            test_data = {
                "phone_number": "10086",
                "agent_id": "test_agent"
            }
            response = await self.client.post(
                f"{self.base_url}/api/calls/start",
                data=test_data
            )
            
            if response.status_code == 200:
                result = response.json()
                logger.info(f"✅ 外呼API正常，通话ID: {result.get('call_id')}")
                return result.get('call_id')
            else:
                logger.error(f"❌ 外呼API失败: {response.status_code} - {response.text}")
                return None
                
        except Exception as e:
            logger.error(f"❌ API测试失败: {e}")
            return None
    
    async def test_websocket(self, call_id):
        """测试WebSocket连接"""
        try:
            import websockets
            ws_url = f"ws://localhost:8000/ws/{call_id}"
            
            async with websockets.connect(ws_url) as websocket:
                logger.info("✅ WebSocket连接成功")
                
                # 发送测试消息
                test_message = b"test audio data"
                await websocket.send(test_message)
                logger.info("✅ WebSocket消息发送成功")
                
                return True
                
        except Exception as e:
            logger.error(f"❌ WebSocket测试失败: {e}")
            return False
    
    async def run_tests(self):
        """运行所有测试"""
        logger.info("🚀 开始系统测试...")
        
        # 等待服务启动
        logger.info("⏳ 等待服务启动...")
        await asyncio.sleep(5)
        
        # 测试Web界面
        if not await self.test_health():
            logger.error("❌ 系统健康检查失败")
            return False
        
        # 测试API
        call_id = await self.test_api_endpoints()
        if not call_id:
            logger.error("❌ API测试失败")
            return False
        
        # 测试WebSocket
        if not await self.test_websocket(call_id):
            logger.error("❌ WebSocket测试失败")
            return False
        
        logger.info("✅ 所有测试通过！系统运行正常")
        return True
    
    async def cleanup(self):
        """清理资源"""
        await self.client.aclose()

async def main():
    """主函数"""
    tester = SystemTester()
    
    try:
        success = await tester.run_tests()
        if success:
            print("\n🎉 系统测试完成！")
            print("🌐 访问 http://localhost:8000 使用系统")
        else:
            print("\n❌ 系统测试失败，请检查日志")
    finally:
        await tester.cleanup()

if __name__ == "__main__":
    asyncio.run(main())