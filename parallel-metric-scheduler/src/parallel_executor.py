"""
并行指标执行器 - 支持多种指标类型的并行执行
"""
import os
import json
import subprocess
from datetime import datetime
from typing import Dict, Any, List, Optional
from concurrent.futures import ThreadPoolExecutor, as_completed
from loguru import logger
from sqlalchemy.orm import Session
import pymysql

from models import JobInstance, JobLog, MetricResult, Metric
from config.config import config


class ParallelMetricExecutor:
    """并行指标执行器"""
    
    def __init__(self, session: Session):
        self.session = session
        self.result_pool = ThreadPoolExecutor(max_workers=5, thread_name_prefix='result')
        
    def execute_job(self, job_instance: JobInstance) -> bool:
        """执行作业"""
        try:
            # 更新作业状态为运行中
            job_instance.status = 'RUNNING'
            job_instance.actual_start_time = datetime.now()
            self.session.commit()
            
            # 记录日志
            self._log_job(job_instance.id, 'INFO', f'开始执行作业: {job_instance.job_code}')
            
            # 获取指标信息
            metric = job_instance.metric_schedule.metric
            
            # 根据指标类型执行
            result = []
            if metric.metric_type == 'SQL':
                result = self._execute_sql_metric(job_instance, metric)
            elif metric.metric_type == 'STORED_PROCEDURE':
                result = self._execute_stored_procedure_metric(job_instance, metric)
            elif metric.metric_type == 'SCRIPT':
                result = self._execute_script_metric(job_instance, metric)
            elif metric.metric_type == 'PARALLEL_SQL':
                result = self._execute_parallel_sql_metric(job_instance, metric)
            else:
                raise ValueError(f"不支持的指标类型: {metric.metric_type}")
            
            # 异步保存执行结果
            if result:
                future = self.result_pool.submit(
                    self._save_metric_results,
                    job_instance.id,
                    metric.id,
                    result
                )
            
            # 更新作业状态为成功
            job_instance.status = 'SUCCESS'
            job_instance.actual_end_time = datetime.now()
            job_instance.result_data = {
                "row_count": len(result) if isinstance(result, list) else 0
            }
            self.session.commit()
            
            self._log_job(job_instance.id, 'INFO', 
                         f'作业执行成功，产生 {len(result) if isinstance(result, list) else 0} 条结果')
            return True
            
        except Exception as e:
            # 记录错误
            error_msg = str(e)
            logger.error(f"执行作业失败: {job_instance.job_code}, 错误: {error_msg}")
            self._log_job(job_instance.id, 'ERROR', f'作业执行失败: {error_msg}')
            
            # 更新作业状态
            job_instance.status = 'FAILED'
            job_instance.actual_end_time = datetime.now()
            job_instance.error_message = error_msg[:1000]  # 限制长度
            self.session.commit()
            
            return False
    
    def _execute_sql_metric(self, job_instance: JobInstance, metric: Metric) -> List[Dict[str, Any]]:
        """执行SQL类型的指标"""
        self._log_job(job_instance.id, 'INFO', f'执行SQL指标: {metric.metric_code}')
        
        connection = self._create_db_connection()
        
        try:
            with connection.cursor() as cursor:
                # 替换SQL中的参数
                sql = self._replace_params(metric.metric_sql, metric.params)
                
                self._log_job(job_instance.id, 'DEBUG', f'执行SQL: {sql[:500]}...')
                cursor.execute(sql)
                results = cursor.fetchall()
                
                self._log_job(job_instance.id, 'INFO', f'SQL执行完成，返回 {len(results)} 条记录')
                return results
        finally:
            connection.close()
    
    def _execute_parallel_sql_metric(self, job_instance: JobInstance, metric: Metric) -> List[Dict[str, Any]]:
        """并行执行多个SQL查询"""
        self._log_job(job_instance.id, 'INFO', f'并行执行SQL指标: {metric.metric_code}')
        
        # 假设metric.metric_sql包含多个SQL语句，用分号分隔
        sql_statements = [s.strip() for s in metric.metric_sql.split(';') if s.strip()]
        
        if len(sql_statements) <= 1:
            # 如果只有一个SQL，直接执行
            return self._execute_sql_metric(job_instance, metric)
        
        # 并行执行多个SQL
        results = []
        futures = []
        
        with ThreadPoolExecutor(max_workers=min(len(sql_statements), 5)) as pool:
            for i, sql in enumerate(sql_statements):
                future = pool.submit(
                    self._execute_single_sql,
                    job_instance.id,
                    sql,
                    metric.params,
                    i
                )
                futures.append(future)
            
            # 收集结果
            for future in as_completed(futures):
                try:
                    result = future.result()
                    if result:
                        results.extend(result)
                except Exception as e:
                    logger.error(f"并行SQL执行失败: {e}")
        
        self._log_job(job_instance.id, 'INFO', 
                     f'并行SQL执行完成，共返回 {len(results)} 条记录')
        return results
    
    def _execute_single_sql(self, job_instance_id: int, sql: str, 
                           params: Optional[Dict], index: int) -> List[Dict[str, Any]]:
        """执行单个SQL语句"""
        connection = self._create_db_connection()
        
        try:
            with connection.cursor() as cursor:
                sql = self._replace_params(sql, params)
                cursor.execute(sql)
                results = cursor.fetchall()
                logger.debug(f"SQL[{index}] 返回 {len(results)} 条记录")
                return results
        finally:
            connection.close()
    
    def _execute_stored_procedure_metric(self, job_instance: JobInstance, 
                                        metric: Metric) -> List[Dict[str, Any]]:
        """执行存储过程类型的指标"""
        self._log_job(job_instance.id, 'INFO', f'执行存储过程指标: {metric.metric_code}')
        
        connection = self._create_db_connection()
        
        try:
            with connection.cursor() as cursor:
                # 构建存储过程调用
                proc_name = metric.metric_sql
                params = []
                if metric.params:
                    params = list(metric.params.values())
                
                self._log_job(job_instance.id, 'DEBUG', 
                             f'调用存储过程: {proc_name}, 参数: {params}')
                cursor.callproc(proc_name, params)
                results = cursor.fetchall()
                
                # 提交事务（存储过程可能有写操作）
                connection.commit()
                
                self._log_job(job_instance.id, 'INFO', 
                             f'存储过程执行完成，返回 {len(results)} 条记录')
                return results
        finally:
            connection.close()
    
    def _execute_script_metric(self, job_instance: JobInstance, 
                               metric: Metric) -> List[Dict[str, Any]]:
        """执行脚本类型的指标"""
        self._log_job(job_instance.id, 'INFO', f'执行脚本指标: {metric.metric_code}')
        
        script_path = metric.script_path
        if not os.path.exists(script_path):
            raise FileNotFoundError(f"脚本文件不存在: {script_path}")
        
        # 准备环境变量
        env = os.environ.copy()
        env['JOB_INSTANCE_ID'] = str(job_instance.id)
        env['JOB_CODE'] = job_instance.job_code
        env['METRIC_CODE'] = metric.metric_code
        env['METRIC_NAME'] = metric.metric_name
        
        if metric.params:
            for key, value in metric.params.items():
                env[f'PARAM_{key.upper()}'] = str(value)
        
        self._log_job(job_instance.id, 'DEBUG', f'执行脚本: {script_path}')
        
        # 执行脚本
        try:
            result = subprocess.run(
                ['python', script_path],
                capture_output=True,
                text=True,
                env=env,
                timeout=job_instance.metric_schedule.timeout_seconds,
                cwd=os.path.dirname(script_path) or '.'
            )
            
            if result.returncode != 0:
                error_msg = result.stderr or result.stdout
                raise RuntimeError(f"脚本执行失败 (退出码: {result.returncode}): {error_msg}")
            
            # 解析脚本输出（假设输出为JSON格式）
            output = result.stdout.strip()
            if output:
                try:
                    results = json.loads(output)
                    if not isinstance(results, list):
                        results = [results]
                except json.JSONDecodeError as e:
                    logger.warning(f"脚本输出不是有效的JSON: {output[:200]}")
                    # 尝试将输出作为单个结果
                    results = [{"output": output}]
            else:
                results = []
            
            self._log_job(job_instance.id, 'INFO', 
                         f'脚本执行完成，返回 {len(results)} 条记录')
            return results
            
        except subprocess.TimeoutExpired:
            raise TimeoutError(f"脚本执行超时 ({job_instance.metric_schedule.timeout_seconds}秒): {script_path}")
    
    def _save_metric_results(self, job_instance_id: int, metric_id: int, 
                            results: List[Dict[str, Any]]):
        """保存指标结果（在独立会话中）"""
        if not results:
            return
        
        try:
            from models import Session as DBSession
            
            with DBSession() as session:
                calc_time = datetime.now()
                saved_count = 0
                
                # 批量保存结果
                for result in results:
                    try:
                        # 提取维度和值
                        dimension_values = {}
                        metric_value = None
                        metric_json = {}
                        result_calc_time = calc_time
                        
                        for key, value in result.items():
                            key_lower = key.lower()
                            if key_lower in ['value', 'metric_value']:
                                metric_value = float(value) if value is not None else None
                            elif key_lower in ['calc_time', 'calc_date']:
                                if isinstance(value, str):
                                    try:
                                        if ':' in value:
                                            result_calc_time = datetime.strptime(value, '%Y-%m-%d %H:%M:%S')
                                        else:
                                            result_calc_time = datetime.strptime(value, '%Y-%m-%d')
                                    except:
                                        pass
                                elif isinstance(value, datetime):
                                    result_calc_time = value
                            else:
                                dimension_values[key] = value
                                metric_json[key] = value
                        
                        # 如果没有明确的value字段，尝试使用第一个数值字段
                        if metric_value is None and metric_json:
                            for v in metric_json.values():
                                if isinstance(v, (int, float)):
                                    metric_value = float(v)
                                    break
                        
                        # 生成维度键
                        dimension_key = '|'.join(
                            f"{k}={v}" for k, v in sorted(dimension_values.items())
                        ) if dimension_values else 'default'
                        
                        # 创建结果记录
                        metric_result = MetricResult(
                            job_instance_id=job_instance_id,
                            metric_id=metric_id,
                            dimension_key=dimension_key,
                            dimension_values=dimension_values or None,
                            metric_value=metric_value,
                            metric_json=metric_json,
                            calc_time=result_calc_time
                        )
                        session.add(metric_result)
                        saved_count += 1
                        
                    except Exception as e:
                        logger.error(f"保存单条结果失败: {e}, 结果: {result}")
                
                session.commit()
                logger.debug(f"保存了 {saved_count}/{len(results)} 条指标结果")
                
        except Exception as e:
            logger.error(f"保存指标结果失败: {e}", exc_info=True)
    
    def _create_db_connection(self):
        """创建数据库连接"""
        return pymysql.connect(
            host=config.mysql.host,
            port=config.mysql.port,
            user=config.mysql.user,
            password=config.mysql.password,
            database=config.mysql.database,
            charset='utf8mb4',
            cursorclass=pymysql.cursors.DictCursor,
            connect_timeout=10,
            read_timeout=30,
            write_timeout=30
        )
    
    def _replace_params(self, sql: str, params: Optional[Dict[str, Any]]) -> str:
        """替换SQL中的参数"""
        if not params:
            return sql
        
        result = sql
        for key, value in params.items():
            # 支持 ${param} 和 {param} 两种格式
            result = result.replace(f'${{{key}}}', str(value))
            result = result.replace(f'{{{key}}}', str(value))
        
        return result
    
    def _log_job(self, job_instance_id: int, log_level: str, log_message: str):
        """记录作业日志"""
        try:
            job_log = JobLog(
                job_instance_id=job_instance_id,
                log_level=log_level,
                log_message=log_message[:2000]  # 限制长度
            )
            self.session.add(job_log)
            self.session.commit()
        except Exception as e:
            logger.error(f"记录作业日志失败: {e}")
        
        # 同时记录到系统日志
        log_prefix = f"[Job {job_instance_id}]"
        if log_level == 'DEBUG':
            logger.debug(f"{log_prefix} {log_message}")
        elif log_level == 'INFO':
            logger.info(f"{log_prefix} {log_message}")
        elif log_level == 'WARN':
            logger.warning(f"{log_prefix} {log_message}")
        elif log_level == 'ERROR':
            logger.error(f"{log_prefix} {log_message}")
