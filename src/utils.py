"""
工具函数模块
"""
import uuid
import hashlib
import json
from datetime import datetime, timezone
from typing import Any, Dict, Optional
from cryptography.fernet import Fernet
import base64


def get_current_time() -> datetime:
    """获取当前UTC时间"""
    return datetime.now(timezone.utc).replace(tzinfo=None)


def generate_task_id() -> str:
    """生成任务ID"""
    return f"task_{uuid.uuid4().hex[:16]}"


def generate_node_id() -> str:
    """生成节点ID"""
    return f"node_{uuid.uuid4().hex[:12]}"


def hash_password(password: str) -> str:
    """密码哈希"""
    return hashlib.sha256(password.encode()).hexdigest()


def verify_password(password: str, hashed: str) -> bool:
    """验证密码"""
    return hash_password(password) == hashed


def encrypt_data(data: str, key: Optional[str] = None) -> str:
    """加密数据"""
    if key is None:
        key = Fernet.generate_key()
    else:
        key = base64.urlsafe_b64encode(key.encode().ljust(32)[:32])
    
    f = Fernet(key)
    encrypted = f.encrypt(data.encode())
    return base64.urlsafe_b64encode(encrypted).decode()


def decrypt_data(encrypted_data: str, key: str) -> str:
    """解密数据"""
    key = base64.urlsafe_b64encode(key.encode().ljust(32)[:32])
    f = Fernet(key)
    
    encrypted_bytes = base64.urlsafe_b64decode(encrypted_data.encode())
    decrypted = f.decrypt(encrypted_bytes)
    return decrypted.decode()


def serialize_json(obj: Any) -> str:
    """序列化JSON"""
    def json_serializer(obj):
        if isinstance(obj, datetime):
            return obj.isoformat()
        raise TypeError(f"Object of type {type(obj)} is not JSON serializable")
    
    return json.dumps(obj, default=json_serializer, ensure_ascii=False)


def deserialize_json(json_str: str) -> Any:
    """反序列化JSON"""
    return json.loads(json_str)


def format_duration(duration_ms: int) -> str:
    """格式化持续时间"""
    if duration_ms < 1000:
        return f"{duration_ms}ms"
    elif duration_ms < 60000:
        return f"{duration_ms / 1000:.2f}s"
    elif duration_ms < 3600000:
        return f"{duration_ms / 60000:.2f}m"
    else:
        return f"{duration_ms / 3600000:.2f}h"


def validate_cron_expression(cron_expr: str) -> bool:
    """验证cron表达式"""
    try:
        from croniter import croniter
        croniter(cron_expr)
        return True
    except Exception:
        return False


def parse_sql_template(template: str, parameters: Dict[str, Any]) -> str:
    """解析SQL模板"""
    try:
        from jinja2 import Template
        jinja_template = Template(template)
        return jinja_template.render(**parameters)
    except Exception as e:
        raise ValueError(f"Failed to parse SQL template: {e}")


def sanitize_sql(sql: str) -> str:
    """SQL注入防护（基础版本）"""
    # 移除危险的SQL关键字
    dangerous_keywords = [
        'DROP', 'DELETE', 'INSERT', 'UPDATE', 'ALTER', 'CREATE',
        'TRUNCATE', 'EXEC', 'EXECUTE', 'UNION', 'SCRIPT'
    ]
    
    sql_upper = sql.upper()
    for keyword in dangerous_keywords:
        if keyword in sql_upper:
            raise ValueError(f"Dangerous SQL keyword detected: {keyword}")
    
    return sql


def calculate_retry_delay(retry_count: int, base_delay: int = 1) -> int:
    """计算重试延迟（指数退避）"""
    return min(base_delay * (2 ** retry_count), 300)  # 最大5分钟


def format_bytes(bytes_count: int) -> str:
    """格式化字节数"""
    for unit in ['B', 'KB', 'MB', 'GB', 'TB']:
        if bytes_count < 1024.0:
            return f"{bytes_count:.2f} {unit}"
        bytes_count /= 1024.0
    return f"{bytes_count:.2f} PB"


def truncate_string(text: str, max_length: int = 1000) -> str:
    """截断字符串"""
    if len(text) <= max_length:
        return text
    return text[:max_length - 3] + "..."


def safe_dict_get(data: Dict[str, Any], key: str, default: Any = None) -> Any:
    """安全获取字典值"""
    try:
        return data.get(key, default)
    except (AttributeError, TypeError):
        return default


def merge_dicts(*dicts: Dict[str, Any]) -> Dict[str, Any]:
    """合并多个字典"""
    result = {}
    for d in dicts:
        if isinstance(d, dict):
            result.update(d)
    return result


class Timer:
    """计时器上下文管理器"""
    
    def __init__(self):
        self.start_time = None
        self.end_time = None
        self.duration_ms = None
    
    def __enter__(self):
        self.start_time = datetime.now()
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        self.end_time = datetime.now()
        self.duration_ms = int((self.end_time - self.start_time).total_seconds() * 1000)
    
    def get_duration_ms(self) -> int:
        """获取持续时间（毫秒）"""
        return self.duration_ms or 0


class RateLimiter:
    """简单的速率限制器"""
    
    def __init__(self, max_calls: int, time_window: int):
        self.max_calls = max_calls
        self.time_window = time_window
        self.calls = []
    
    def is_allowed(self) -> bool:
        """检查是否允许调用"""
        now = datetime.now()
        
        # 清理过期的调用记录
        cutoff = now.timestamp() - self.time_window
        self.calls = [call_time for call_time in self.calls if call_time > cutoff]
        
        # 检查是否超过限制
        if len(self.calls) >= self.max_calls:
            return False
        
        # 记录本次调用
        self.calls.append(now.timestamp())
        return True


def create_connection_string(
    host: str,
    port: int,
    database: str,
    username: str,
    password: str,
    driver: str = "mysql+pymysql",
    **kwargs
) -> str:
    """创建数据库连接字符串"""
    params = "&".join([f"{k}={v}" for k, v in kwargs.items()])
    params_str = f"?{params}" if params else ""
    
    return f"{driver}://{username}:{password}@{host}:{port}/{database}{params_str}"


def validate_email(email: str) -> bool:
    """验证邮箱格式"""
    import re
    pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
    return re.match(pattern, email) is not None


def generate_api_key() -> str:
    """生成API密钥"""
    return f"mk_{uuid.uuid4().hex}"


def mask_sensitive_data(data: str, mask_char: str = "*", visible_chars: int = 4) -> str:
    """遮蔽敏感数据"""
    if len(data) <= visible_chars * 2:
        return mask_char * len(data)
    
    return data[:visible_chars] + mask_char * (len(data) - visible_chars * 2) + data[-visible_chars:]