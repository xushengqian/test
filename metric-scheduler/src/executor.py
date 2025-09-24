import os
import json
import subprocess
from datetime import datetime
from typing import Dict, Any, List, Optional
from loguru import logger
from sqlalchemy.orm import Session
from tenacity import retry, stop_after_attempt, wait_fixed
import pymysql
from models import JobInstance, JobLog, MetricResult, Metric
from config.config import config


class MetricExecutor:
    """指标执行器"""
    
    def __init__(self, session: Session):
        self.session = session
        
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
            if metric.metric_type == 'SQL':
                result = self._execute_sql_metric(job_instance, metric)
            elif metric.metric_type == 'STORED_PROCEDURE':
                result = self._execute_stored_procedure_metric(job_instance, metric)
            elif metric.metric_type == 'SCRIPT':
                result = self._execute_script_metric(job_instance, metric)
            else:
                raise ValueError(f"不支持的指标类型: {metric.metric_type}")
            
            # 保存执行结果
            self._save_metric_results(job_instance, metric, result)
            
            # 更新作业状态为成功
            job_instance.status = 'SUCCESS'
            job_instance.actual_end_time = datetime.now()
            job_instance.result_data = {"row_count": len(result)} if isinstance(result, list) else {}
            self.session.commit()
            
            self._log_job(job_instance.id, 'INFO', f'作业执行成功: {job_instance.job_code}')
            return True
            
        except Exception as e:
            # 记录错误
            error_msg = str(e)
            logger.error(f"执行作业失败: {job_instance.job_code}, 错误: {error_msg}")
            self._log_job(job_instance.id, 'ERROR', f'作业执行失败: {error_msg}')
            
            # 更新作业状态
            job_instance.status = 'FAILED'
            job_instance.actual_end_time = datetime.now()
            job_instance.error_message = error_msg
            self.session.commit()
            
            return False
    
    def _execute_sql_metric(self, job_instance: JobInstance, metric: Metric) -> List[Dict[str, Any]]:
        """执行SQL类型的指标"""
        self._log_job(job_instance.id, 'INFO', f'执行SQL指标: {metric.metric_code}')
        
        # 创建新的数据库连接执行查询
        connection = pymysql.connect(
            host=config.mysql.host,
            port=config.mysql.port,
            user=config.mysql.user,
            password=config.mysql.password,
            database=config.mysql.database,
            charset='utf8mb4',
            cursorclass=pymysql.cursors.DictCursor
        )
        
        try:
            with connection.cursor() as cursor:
                # 替换SQL中的参数
                sql = metric.metric_sql
                if metric.params:
                    for key, value in metric.params.items():
                        sql = sql.replace(f'${{{key}}}', str(value))
                
                self._log_job(job_instance.id, 'DEBUG', f'执行SQL: {sql}')
                cursor.execute(sql)
                results = cursor.fetchall()
                
                self._log_job(job_instance.id, 'INFO', f'SQL执行完成，返回 {len(results)} 条记录')
                return results
        finally:
            connection.close()
    
    def _execute_stored_procedure_metric(self, job_instance: JobInstance, metric: Metric) -> List[Dict[str, Any]]:
        """执行存储过程类型的指标"""
        self._log_job(job_instance.id, 'INFO', f'执行存储过程指标: {metric.metric_code}')
        
        connection = pymysql.connect(
            host=config.mysql.host,
            port=config.mysql.port,
            user=config.mysql.user,
            password=config.mysql.password,
            database=config.mysql.database,
            charset='utf8mb4',
            cursorclass=pymysql.cursors.DictCursor
        )
        
        try:
            with connection.cursor() as cursor:
                # 构建存储过程调用
                proc_name = metric.metric_sql
                params = []
                if metric.params:
                    params = list(metric.params.values())
                
                self._log_job(job_instance.id, 'DEBUG', f'调用存储过程: {proc_name}, 参数: {params}')
                cursor.callproc(proc_name, params)
                results = cursor.fetchall()
                
                self._log_job(job_instance.id, 'INFO', f'存储过程执行完成，返回 {len(results)} 条记录')
                return results
        finally:
            connection.close()
    
    def _execute_script_metric(self, job_instance: JobInstance, metric: Metric) -> List[Dict[str, Any]]:
        """执行脚本类型的指标"""
        self._log_job(job_instance.id, 'INFO', f'执行脚本指标: {metric.metric_code}')
        
        script_path = metric.script_path
        if not os.path.exists(script_path):
            raise FileNotFoundError(f"脚本文件不存在: {script_path}")
        
        # 准备环境变量
        env = os.environ.copy()
        env['JOB_INSTANCE_ID'] = str(job_instance.id)
        env['METRIC_CODE'] = metric.metric_code
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
                timeout=job_instance.metric_schedule.timeout_seconds
            )
            
            if result.returncode != 0:
                raise RuntimeError(f"脚本执行失败: {result.stderr}")
            
            # 解析脚本输出（假设输出为JSON格式）
            output = result.stdout.strip()
            if output:
                results = json.loads(output)
                if not isinstance(results, list):
                    results = [results]
            else:
                results = []
            
            self._log_job(job_instance.id, 'INFO', f'脚本执行完成，返回 {len(results)} 条记录')
            return results
            
        except subprocess.TimeoutExpired:
            raise TimeoutError(f"脚本执行超时: {script_path}")
    
    def _save_metric_results(self, job_instance: JobInstance, metric: Metric, results: List[Dict[str, Any]]):
        """保存指标结果"""
        if not results:
            self._log_job(job_instance.id, 'WARN', '没有指标结果需要保存')
            return
        
        calc_time = datetime.now()
        
        for result in results:
            # 提取维度和值
            dimension_values = {}
            metric_value = None
            metric_json = {}
            
            for key, value in result.items():
                if key.lower() in ['value', 'metric_value']:
                    metric_value = float(value) if value is not None else None
                elif key.lower() in ['calc_time', 'calc_date']:
                    if isinstance(value, str):
                        calc_time = datetime.strptime(value, '%Y-%m-%d %H:%M:%S') if ':' in value else datetime.strptime(value, '%Y-%m-%d')
                    else:
                        calc_time = value
                else:
                    dimension_values[key] = value
                    metric_json[key] = value
            
            # 生成维度键
            dimension_key = '|'.join(f"{k}={v}" for k, v in sorted(dimension_values.items()))
            
            # 创建结果记录
            metric_result = MetricResult(
                job_instance_id=job_instance.id,
                metric_id=metric.id,
                dimension_key=dimension_key,
                dimension_values=dimension_values,
                metric_value=metric_value,
                metric_json=metric_json,
                calc_time=calc_time
            )
            self.session.add(metric_result)
        
        self.session.commit()
        self._log_job(job_instance.id, 'INFO', f'保存了 {len(results)} 条指标结果')
    
    def _log_job(self, job_instance_id: int, log_level: str, log_message: str):
        """记录作业日志"""
        job_log = JobLog(
            job_instance_id=job_instance_id,
            log_level=log_level,
            log_message=log_message
        )
        self.session.add(job_log)
        self.session.commit()
        
        # 同时记录到系统日志
        if log_level == 'DEBUG':
            logger.debug(f"[Job {job_instance_id}] {log_message}")
        elif log_level == 'INFO':
            logger.info(f"[Job {job_instance_id}] {log_message}")
        elif log_level == 'WARN':
            logger.warning(f"[Job {job_instance_id}] {log_message}")
        elif log_level == 'ERROR':
            logger.error(f"[Job {job_instance_id}] {log_message}")