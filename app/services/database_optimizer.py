import asyncio
import logging
from datetime import datetime, timedelta
from typing import List, Dict, Any
from sqlalchemy import text, func, desc
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import AsyncSessionLocal
from app.models import CallSession, Transcription

logger = logging.getLogger(__name__)

class DatabaseOptimizer:
    """数据库优化服务"""
    
    def __init__(self):
        self.batch_size = 100
        self.cleanup_interval = 3600  # 1小时清理一次
        self.max_transcription_age = 30  # 保留30天的转写记录
    
    async def batch_insert_transcriptions(self, transcriptions: List[Dict[str, Any]]):
        """批量插入转写记录"""
        if not transcriptions:
            return
        
        try:
            async with AsyncSessionLocal() as session:
                # 准备批量插入数据
                insert_data = []
                for trans in transcriptions:
                    insert_data.append({
                        'call_id': trans['call_id'],
                        'text': trans['text'],
                        'confidence': trans.get('confidence', 0.9),
                        'timestamp': trans.get('timestamp', datetime.now())
                    })
                
                # 批量插入
                await session.execute(
                    text("""
                        INSERT INTO transcriptions (call_id, text, confidence, timestamp, created_at)
                        VALUES (:call_id, :text, :confidence, :timestamp, :created_at)
                    """),
                    [dict(item, created_at=datetime.now()) for item in insert_data]
                )
                
                await session.commit()
                logger.info(f"Batch inserted {len(transcriptions)} transcriptions")
                
        except Exception as e:
            logger.error(f"Error batch inserting transcriptions: {e}")
    
    async def cleanup_old_data(self):
        """清理旧数据"""
        try:
            cutoff_date = datetime.now() - timedelta(days=self.max_transcription_age)
            
            async with AsyncSessionLocal() as session:
                # 删除旧的转写记录
                result = await session.execute(
                    text("DELETE FROM transcriptions WHERE created_at < :cutoff_date"),
                    {"cutoff_date": cutoff_date}
                )
                
                deleted_transcriptions = result.rowcount
                
                # 删除已结束且没有转写记录的通话会话
                result = await session.execute(
                    text("""
                        DELETE FROM call_sessions 
                        WHERE status = 'ended' 
                        AND end_time < :cutoff_date
                        AND call_id NOT IN (
                            SELECT DISTINCT call_id FROM transcriptions
                        )
                    """),
                    {"cutoff_date": cutoff_date}
                )
                
                deleted_sessions = result.rowcount
                await session.commit()
                
                logger.info(f"Cleaned up {deleted_transcriptions} transcriptions and {deleted_sessions} sessions")
                
        except Exception as e:
            logger.error(f"Error cleaning up old data: {e}")
    
    async def get_call_statistics(self, days: int = 7) -> Dict[str, Any]:
        """获取通话统计信息"""
        try:
            cutoff_date = datetime.now() - timedelta(days=days)
            
            async with AsyncSessionLocal() as session:
                # 通话统计
                call_stats = await session.execute(
                    text("""
                        SELECT 
                            status,
                            COUNT(*) as count,
                            AVG(EXTRACT(EPOCH FROM (end_time - start_time))) as avg_duration
                        FROM call_sessions 
                        WHERE start_time >= :cutoff_date
                        GROUP BY status
                    """),
                    {"cutoff_date": cutoff_date}
                )
                
                # 转写统计
                transcription_stats = await session.execute(
                    text("""
                        SELECT 
                            COUNT(*) as total_transcriptions,
                            AVG(confidence) as avg_confidence,
                            COUNT(DISTINCT call_id) as calls_with_transcriptions
                        FROM transcriptions 
                        WHERE created_at >= :cutoff_date
                    """),
                    {"cutoff_date": cutoff_date}
                )
                
                # 每日统计
                daily_stats = await session.execute(
                    text("""
                        SELECT 
                            DATE(start_time) as date,
                            COUNT(*) as calls,
                            COUNT(CASE WHEN status = 'active' THEN 1 END) as active_calls,
                            COUNT(CASE WHEN status = 'ended' THEN 1 END) as ended_calls
                        FROM call_sessions 
                        WHERE start_time >= :cutoff_date
                        GROUP BY DATE(start_time)
                        ORDER BY date DESC
                    """),
                    {"cutoff_date": cutoff_date}
                )
                
                return {
                    "period_days": days,
                    "call_stats": [dict(row) for row in call_stats],
                    "transcription_stats": dict(transcription_stats.fetchone()) if transcription_stats.rowcount > 0 else {},
                    "daily_stats": [dict(row) for row in daily_stats]
                }
                
        except Exception as e:
            logger.error(f"Error getting call statistics: {e}")
            return {}
    
    async def optimize_database(self):
        """优化数据库性能"""
        try:
            async with AsyncSessionLocal() as session:
                # 创建索引（如果不存在）
                indexes = [
                    "CREATE INDEX IF NOT EXISTS idx_transcriptions_call_id ON transcriptions(call_id)",
                    "CREATE INDEX IF NOT EXISTS idx_transcriptions_timestamp ON transcriptions(timestamp)",
                    "CREATE INDEX IF NOT EXISTS idx_transcriptions_created_at ON transcriptions(created_at)",
                    "CREATE INDEX IF NOT EXISTS idx_call_sessions_status ON call_sessions(status)",
                    "CREATE INDEX IF NOT EXISTS idx_call_sessions_start_time ON call_sessions(start_time)",
                    "CREATE INDEX IF NOT EXISTS idx_call_sessions_phone_number ON call_sessions(phone_number)"
                ]
                
                for index_sql in indexes:
                    await session.execute(text(index_sql))
                
                await session.commit()
                logger.info("Database optimization completed")
                
        except Exception as e:
            logger.error(f"Error optimizing database: {e}")
    
    async def get_performance_metrics(self) -> Dict[str, Any]:
        """获取数据库性能指标"""
        try:
            async with AsyncSessionLocal() as session:
                # 表大小统计
                table_sizes = await session.execute(
                    text("""
                        SELECT 
                            schemaname,
                            tablename,
                            pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
                        FROM pg_tables 
                        WHERE schemaname = 'public'
                        ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC
                    """)
                )
                
                # 索引使用统计
                index_usage = await session.execute(
                    text("""
                        SELECT 
                            schemaname,
                            tablename,
                            indexname,
                            idx_scan,
                            idx_tup_read,
                            idx_tup_fetch
                        FROM pg_stat_user_indexes 
                        WHERE schemaname = 'public'
                        ORDER BY idx_scan DESC
                    """)
                )
                
                return {
                    "table_sizes": [dict(row) for row in table_sizes],
                    "index_usage": [dict(row) for row in index_usage]
                }
                
        except Exception as e:
            logger.error(f"Error getting performance metrics: {e}")
            return {}
    
    async def start_cleanup_task(self):
        """启动清理任务"""
        while True:
            try:
                await self.cleanup_old_data()
                await asyncio.sleep(self.cleanup_interval)
            except Exception as e:
                logger.error(f"Cleanup task error: {e}")
                await asyncio.sleep(3600)  # 出错后等待1小时再重试

# 全局数据库优化器实例
db_optimizer = DatabaseOptimizer()