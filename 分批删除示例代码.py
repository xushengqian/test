"""
ks_index_user_performance_details 删除服务
优化方案：分批删除 + 死锁重试
"""

import time
import logging
from typing import Optional
from sqlalchemy import text, create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.exc import OperationalError

logger = logging.getLogger(__name__)


class UserPerformanceDetailsService:
    """用户性能详情删除服务"""
    
    def __init__(self, engine):
        """
        初始化服务
        
        Args:
            engine: SQLAlchemy engine
        """
        self.Session = sessionmaker(bind=engine)
        self.batch_size = 1000  # 每批删除1000条
        self.max_retries = 3    # 最大重试次数
    
    def delete_in_batches(self, index_id: int, index_params: str) -> int:
        """
        分批删除，避免长时间锁定和死锁
        
        Args:
            index_id: 索引ID
            index_params: 索引参数
            
        Returns:
            删除的总记录数
        """
        session = self.Session()
        total_deleted = 0
        
        try:
            while True:
                # 执行分批删除
                result = session.execute(
                    text("""
                        DELETE FROM ks_index_user_performance_details 
                        WHERE index_id = :index_id AND index_params = :index_params 
                        LIMIT :batch_size
                    """),
                    {
                        "index_id": index_id,
                        "index_params": index_params,
                        "batch_size": self.batch_size
                    }
                )
                
                deleted_count = result.rowcount
                total_deleted += deleted_count
                
                # 提交事务，释放锁
                session.commit()
                
                logger.info(f"已删除 {deleted_count} 条记录，累计删除 {total_deleted} 条")
                
                # 如果本次删除数量为0，说明已经删除完成
                if deleted_count == 0:
                    break
                
                # 短暂休眠，释放锁，避免死锁
                time.sleep(0.01)  # 10毫秒
                
        except Exception as e:
            session.rollback()
            logger.error(f"分批删除失败: {e}", exc_info=True)
            raise
        finally:
            session.close()
        
        return total_deleted
    
    def delete_with_retry(self, index_id: int, index_params: str) -> int:
        """
        带重试的删除操作（处理死锁）
        
        Args:
            index_id: 索引ID
            index_params: 索引参数
            
        Returns:
            删除的总记录数
        """
        attempt = 0
        
        while attempt < self.max_retries:
            try:
                return self.delete_in_batches(index_id, index_params)
                
            except OperationalError as e:
                # 检查是否是死锁错误
                # MySQL死锁错误码: 1213
                # PostgreSQL死锁错误码: 40001
                error_code = e.orig.args[0] if hasattr(e, 'orig') else None
                
                if error_code in (1213, 40001) or 'Deadlock' in str(e):
                    attempt += 1
                    
                    if attempt >= self.max_retries:
                        logger.error(f"删除操作失败，已达到最大重试次数: {e}")
                        raise RuntimeError(f"删除操作失败，已达到最大重试次数: {e}")
                    
                    # 指数退避
                    wait_time = 0.1 * (2 ** (attempt - 1))
                    logger.warning(f"检测到死锁，第 {attempt} 次重试，等待 {wait_time} 秒")
                    time.sleep(wait_time)
                else:
                    # 其他错误直接抛出
                    raise
            except Exception as e:
                logger.error(f"删除操作发生未知错误: {e}", exc_info=True)
                raise
        
        return 0
    
    def count_to_delete(self, index_id: int, index_params: str) -> int:
        """
        查询待删除的记录数（用于预估）
        
        Args:
            index_id: 索引ID
            index_params: 索引参数
            
        Returns:
            待删除的记录数
        """
        session = self.Session()
        
        try:
            result = session.execute(
                text("""
                    SELECT COUNT(*) as cnt 
                    FROM ks_index_user_performance_details 
                    WHERE index_id = :index_id AND index_params = :index_params
                """),
                {
                    "index_id": index_id,
                    "index_params": index_params
                }
            )
            
            count = result.scalar()
            return count if count else 0
            
        finally:
            session.close()


# 使用示例
if __name__ == "__main__":
    # 创建数据库连接
    engine = create_engine(
        "mysql+pymysql://user:password@localhost/database",
        pool_pre_ping=True,
        pool_recycle=3600
    )
    
    # 创建服务实例
    service = UserPerformanceDetailsService(engine)
    
    # 执行删除
    try:
        # 先查询待删除数量
        count = service.count_to_delete(index_id=123, index_params="param_value")
        print(f"待删除记录数: {count}")
        
        # 执行删除（带重试）
        deleted = service.delete_with_retry(index_id=123, index_params="param_value")
        print(f"成功删除 {deleted} 条记录")
        
    except Exception as e:
        print(f"删除失败: {e}")
