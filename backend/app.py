"""
Flask API??? - ??????
"""
import os
import asyncio
import logging
from flask import Flask, jsonify, request
from flask_cors import CORS
from dotenv import load_dotenv

# ??????
load_dotenv()

# ????
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# ??Flask??
app = Flask(__name__)
CORS(app)

# ?????????
active_services = {
    'websocket_handler': None,
    'freeswitch_handler': None
}


@app.route('/')
def index():
    """??"""
    return jsonify({
        'name': 'Freeswitch??????',
        'version': '1.0.0',
        'status': 'running'
    })


@app.route('/api/health')
def health():
    """????"""
    return jsonify({
        'status': 'healthy',
        'services': {
            'websocket': active_services['websocket_handler'] is not None,
            'freeswitch': active_services['freeswitch_handler'] is not None
        }
    })


@app.route('/api/sessions')
def get_sessions():
    """????????"""
    try:
        handler = active_services.get('websocket_handler')
        
        if not handler:
            return jsonify({'error': '?????'}), 503
        
        sessions = []
        for session_id, session_data in handler.sessions.items():
            sessions.append({
                'session_id': session_id,
                'call_info': session_data.get('call_info', {}),
                'start_time': session_data.get('start_time'),
                'message_count': len(session_data.get('transcription', []))
            })
        
        return jsonify({
            'sessions': sessions,
            'count': len(sessions)
        })
        
    except Exception as e:
        logger.error(f"?????????: {e}")
        return jsonify({'error': str(e)}), 500


@app.route('/api/sessions/<session_id>')
def get_session(session_id):
    """???????????"""
    try:
        handler = active_services.get('websocket_handler')
        
        if not handler:
            return jsonify({'error': '?????'}), 503
        
        if session_id not in handler.sessions:
            return jsonify({'error': '?????'}), 404
        
        session_data = handler.sessions[session_id]
        
        return jsonify({
            'session_id': session_id,
            'call_info': session_data.get('call_info', {}),
            'start_time': session_data.get('start_time'),
            'transcription': session_data.get('transcription', [])
        })
        
    except Exception as e:
        logger.error(f"?????????: {e}")
        return jsonify({'error': str(e)}), 500


@app.route('/api/call/originate', methods=['POST'])
def originate_call():
    """????"""
    try:
        data = request.get_json()
        
        destination = data.get('destination')
        caller_id = data.get('caller_id', '1000')
        
        if not destination:
            return jsonify({'error': '??????'}), 400
        
        fs_handler = active_services.get('freeswitch_handler')
        
        if not fs_handler:
            return jsonify({'error': 'Freeswitch?????'}), 503
        
        uuid = fs_handler.originate_call(destination, caller_id)
        
        if uuid:
            return jsonify({
                'success': True,
                'uuid': uuid,
                'message': '??????'
            })
        else:
            return jsonify({
                'success': False,
                'message': '??????'
            }), 500
            
    except Exception as e:
        logger.error(f"???????: {e}")
        return jsonify({'error': str(e)}), 500


@app.route('/api/call/transfer', methods=['POST'])
def transfer_call():
    """?????"""
    try:
        data = request.get_json()
        
        uuid = data.get('uuid')
        agent_extension = data.get('agent_extension', 'agent_queue')
        
        if not uuid:
            return jsonify({'error': '????UUID'}), 400
        
        fs_handler = active_services.get('freeswitch_handler')
        
        if not fs_handler:
            return jsonify({'error': 'Freeswitch?????'}), 503
        
        success = fs_handler.transfer_to_agent(uuid, agent_extension)
        
        return jsonify({
            'success': success,
            'message': '????' if success else '????'
        })
        
    except Exception as e:
        logger.error(f"???????: {e}")
        return jsonify({'error': str(e)}), 500


@app.route('/api/call/hangup', methods=['POST'])
def hangup_call():
    """????"""
    try:
        data = request.get_json()
        
        uuid = data.get('uuid')
        
        if not uuid:
            return jsonify({'error': '????UUID'}), 400
        
        fs_handler = active_services.get('freeswitch_handler')
        
        if not fs_handler:
            return jsonify({'error': 'Freeswitch?????'}), 503
        
        success = fs_handler.hangup_call(uuid)
        
        return jsonify({
            'success': success,
            'message': '????' if success else '????'
        })
        
    except Exception as e:
        logger.error(f"???????: {e}")
        return jsonify({'error': str(e)}), 500


@app.route('/api/stats')
def get_stats():
    """??????"""
    try:
        handler = active_services.get('websocket_handler')
        
        if not handler:
            return jsonify({'error': '?????'}), 503
        
        total_messages = sum(
            len(session.get('transcription', [])) 
            for session in handler.sessions.values()
        )
        
        return jsonify({
            'active_sessions': len(handler.sessions),
            'web_clients': len(handler.web_clients),
            'total_messages': total_messages
        })
        
    except Exception as e:
        logger.error(f"?????????: {e}")
        return jsonify({'error': str(e)}), 500


if __name__ == '__main__':
    # ????
    host = os.getenv('API_HOST', '0.0.0.0')
    port = int(os.getenv('API_PORT', 5000))
    debug = os.getenv('DEBUG', 'False').lower() == 'true'
    
    logger.info(f"??Flask API???: http://{host}:{port}")
    
    # ??Flask??
    app.run(host=host, port=port, debug=debug)
