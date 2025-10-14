"""
FreeSWITCH 机器人呼出系统 - 主程序入口
"""
import asyncio
import signal
import sys
import threading
from pathlib import Path

import yaml
import ESL
from flask import Flask, request, jsonify
from flask_cors import CORS

from outbound_bot import outbound_bot
from transfer_handler import transfer_handler
from utils.logger import get_logger
from utils.database import db_manager

logger = get_logger(__name__)

# 加载配置
config_path = Path(__file__).parent.parent / "config" / "config.yaml"
with open(config_path, 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

web_config = config.get('web', {})

# Flask 应用
app = Flask(__name__)
CORS(app)

# 全局变量
is_running = True
outbound_server = None

class OutboundServer:
    """Outbound Socket 服务器"""
    
    def __init__(self, host='127.0.0.1', port=8040):
        self.host = host
        self.port = port
        self.server = None
        
    async def handle_connection(self, reader, writer):
        """处理新的 socket 连接"""
        try:
            addr = writer.get_extra_info('peername')
            logger.info(f"新的 Outbound 连接: {addr}")
            
            # 创建 ESL 连接对象
            # 注意：这里需要适配 Python ESL 库的异步处理
            # 实际使用时可能需要调整
            
            # 读取连接信息
            data = await reader.read(4096)
            
            # 解析 call_id
            call_id = self.parse_call_id(data)
            
            if call_id:
                # 处理通话
                await outbound_bot.handle_call(None, call_id)
            
            writer.close()
            await writer.wait_closed()
            
        except Exception as e:
            logger.error(f"处理连接失败: {str(e)}")
    
    def parse_call_id(self, data):
        """从连接数据中解析 call_id"""
        # 实际解析逻辑需要根据 FreeSWITCH 发送的数据格式来实现
        # 这里简化处理
        return None
    
    async def start(self):
        """启动服务器"""
        self.server = await asyncio.start_server(
            self.handle_connection,
            self.host,
            self.port
        )
        
        addr = self.server.sockets[0].getsockname()
        logger.info(f'Outbound Socket 服务器启动: {addr[0]}:{addr[1]}')
        
        async with self.server:
            await self.server.serve_forever()

# Flask 路由
@app.route('/health', methods=['GET'])
def health_check():
    """健康检查"""
    return jsonify({
        'status': 'healthy',
        'service': 'freeswitch-bot'
    })

@app.route('/api/call/make', methods=['POST'])
def make_call():
    """
    发起外呼 API
    
    请求体:
    {
        "phone_number": "13800138000"
    }
    """
    data = request.json
    phone_number = data.get('phone_number')
    
    if not phone_number:
        return jsonify({
            'success': False,
            'message': '请提供电话号码'
        }), 400
    
    # 发起呼叫
    call_id = outbound_bot.make_call(phone_number)
    
    if call_id:
        return jsonify({
            'success': True,
            'call_id': call_id,
            'message': '呼叫已发起'
        })
    else:
        return jsonify({
            'success': False,
            'message': '呼叫失败'
        }), 500

@app.route('/api/call/status/<call_id>', methods=['GET'])
def get_call_status(call_id):
    """获取通话状态"""
    record = db_manager.get_call_record(call_id)
    
    if record:
        return jsonify({
            'success': True,
            'data': record
        })
    else:
        return jsonify({
            'success': False,
            'message': '通话记录不存在'
        }), 404

@app.route('/api/calls/active', methods=['GET'])
def get_active_calls():
    """获取活动通话列表"""
    calls = outbound_bot.get_active_calls()
    
    return jsonify({
        'success': True,
        'count': len(calls),
        'data': calls
    })

@app.route('/api/queue/status', methods=['GET'])
def get_queue_status():
    """获取队列状态"""
    status = transfer_handler.get_queue_status()
    
    return jsonify({
        'success': True,
        'data': status
    })

@app.route('/api/agent/status/<agent_number>', methods=['GET'])
def get_agent_status(agent_number):
    """获取坐席状态"""
    status = db_manager.get_agent_status(agent_number)
    
    return jsonify({
        'success': True,
        'agent_number': agent_number,
        'status': status
    })

@app.route('/api/agent/status/<agent_number>', methods=['POST'])
def update_agent_status(agent_number):
    """
    更新坐席状态
    
    请求体:
    {
        "status": "online"  # online, offline, busy, break, available
    }
    """
    data = request.json
    status = data.get('status')
    
    if not status:
        return jsonify({
            'success': False,
            'message': '请提供状态'
        }), 400
    
    db_manager.set_agent_status(agent_number, status)
    
    return jsonify({
        'success': True,
        'message': '状态已更新'
    })

def run_flask_app():
    """运行 Flask 应用"""
    host = web_config.get('host', '0.0.0.0')
    port = web_config.get('port', 5000)
    debug = web_config.get('debug', False)
    
    logger.info(f"启动 Web 服务: {host}:{port}")
    app.run(host=host, port=port, debug=debug)

async def main_loop():
    """主循环"""
    global outbound_server
    
    # 创建 Outbound 服务器
    outbound_server = OutboundServer()
    
    # 启动服务器
    await outbound_server.start()

def signal_handler(sig, frame):
    """信号处理"""
    global is_running
    logger.info("收到终止信号，正在关闭...")
    is_running = False
    sys.exit(0)

def main():
    """主函数"""
    logger.info("=" * 50)
    logger.info("FreeSWITCH 机器人呼出系统启动")
    logger.info("=" * 50)
    
    # 注册信号处理
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    # 连接 FreeSWITCH
    if not outbound_bot.connect_freeswitch():
        logger.error("无法连接到 FreeSWITCH，请检查配置")
        sys.exit(1)
    
    # 启动 Flask 应用（在单独的线程）
    flask_thread = threading.Thread(target=run_flask_app)
    flask_thread.daemon = True
    flask_thread.start()
    
    # 运行异步主循环
    try:
        asyncio.run(main_loop())
    except KeyboardInterrupt:
        logger.info("程序被用户中断")
    except Exception as e:
        logger.error(f"程序异常: {str(e)}")
    finally:
        logger.info("程序退出")

if __name__ == "__main__":
    main()