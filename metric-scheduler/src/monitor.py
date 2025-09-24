from datetime import datetime, timedelta
from typing import List, Dict, Any, Optional
from sqlalchemy import func, and_, or_, desc
from sqlalchemy.orm import Session
from tabulate import tabulate
from models import Session as DBSession, JobInstance, MetricSchedule, Metric, Schedule, JobLog, MetricResult


class JobMonitor:
    """作业监控器"""
    
    def __init__(self):
        self.session = DBSession()
    
    def get_job_summary(self, hours: int = 24) -> Dict[str, Any]:
        """获取作业执行摘要"""
        start_time = datetime.now() - timedelta(hours=hours)
        
        # 统计各状态作业数量
        status_counts = self.session.query(
            JobInstance.status,
            func.count(JobInstance.id).label('count')
        ).filter(
            JobInstance.created_at >= start_time
        ).group_by(JobInstance.status).all()
        
        # 统计总数
        total_jobs = sum(count for _, count in status_counts)
        
        # 计算成功率
        success_count = next((count for status, count in status_counts if status == 'SUCCESS'), 0)
        success_rate = (success_count / total_jobs * 100) if total_jobs > 0 else 0
        
        # 平均执行时间
        avg_duration = self.session.query(
            func.avg(
                func.timestampdiff(
                    func.literal('SECOND'),
                    JobInstance.actual_start_time,
                    JobInstance.actual_end_time
                )
            )
        ).filter(
            JobInstance.status == 'SUCCESS',
            JobInstance.created_at >= start_time,
            JobInstance.actual_start_time.isnot(None),
            JobInstance.actual_end_time.isnot(None)
        ).scalar() or 0
        
        return {
            'time_range': f'最近 {hours} 小时',
            'total_jobs': total_jobs,
            'status_distribution': dict(status_counts),
            'success_rate': f'{success_rate:.2f}%',
            'avg_duration_seconds': float(avg_duration)
        }
    
    def get_running_jobs(self) -> List[Dict[str, Any]]:
        """获取正在运行的作业"""
        jobs = self.session.query(JobInstance).filter(
            JobInstance.status == 'RUNNING'
        ).order_by(JobInstance.actual_start_time).all()
        
        result = []
        now = datetime.now()
        
        for job in jobs:
            duration = (now - job.actual_start_time).total_seconds() if job.actual_start_time else 0
            result.append({
                'job_code': job.job_code,
                'metric_code': job.metric_schedule.metric.metric_code,
                'start_time': job.actual_start_time.strftime('%Y-%m-%d %H:%M:%S') if job.actual_start_time else '',
                'duration_seconds': duration,
                'timeout_seconds': job.metric_schedule.timeout_seconds
            })
        
        return result
    
    def get_failed_jobs(self, hours: int = 24, limit: int = 50) -> List[Dict[str, Any]]:
        """获取失败的作业"""
        start_time = datetime.now() - timedelta(hours=hours)
        
        jobs = self.session.query(JobInstance).filter(
            JobInstance.status.in_(['FAILED', 'TIMEOUT']),
            JobInstance.created_at >= start_time
        ).order_by(desc(JobInstance.created_at)).limit(limit).all()
        
        result = []
        for job in jobs:
            result.append({
                'job_code': job.job_code,
                'metric_code': job.metric_schedule.metric.metric_code,
                'status': job.status,
                'retry_count': job.retry_count,
                'error_message': job.error_message[:100] if job.error_message else '',
                'scheduled_time': job.scheduled_time.strftime('%Y-%m-%d %H:%M:%S'),
                'created_at': job.created_at.strftime('%Y-%m-%d %H:%M:%S')
            })
        
        return result
    
    def get_metric_performance(self, metric_code: Optional[str] = None, days: int = 7) -> List[Dict[str, Any]]:
        """获取指标执行性能统计"""
        start_time = datetime.now() - timedelta(days=days)
        
        query = self.session.query(
            Metric.metric_code,
            Metric.metric_name,
            func.count(JobInstance.id).label('total_executions'),
            func.sum(func.case([(JobInstance.status == 'SUCCESS', 1)], else_=0)).label('success_count'),
            func.sum(func.case([(JobInstance.status == 'FAILED', 1)], else_=0)).label('failed_count'),
            func.sum(func.case([(JobInstance.status == 'TIMEOUT', 1)], else_=0)).label('timeout_count'),
            func.avg(
                func.case([(
                    and_(
                        JobInstance.status == 'SUCCESS',
                        JobInstance.actual_start_time.isnot(None),
                        JobInstance.actual_end_time.isnot(None)
                    ),
                    func.timestampdiff(
                        func.literal('SECOND'),
                        JobInstance.actual_start_time,
                        JobInstance.actual_end_time
                    )
                )], else_=None)
            ).label('avg_duration')
        ).join(
            MetricSchedule
        ).join(
            JobInstance
        ).filter(
            JobInstance.created_at >= start_time
        ).group_by(
            Metric.id, Metric.metric_code, Metric.metric_name
        )
        
        if metric_code:
            query = query.filter(Metric.metric_code == metric_code)
        
        results = query.all()
        
        output = []
        for row in results:
            success_rate = (row.success_count / row.total_executions * 100) if row.total_executions > 0 else 0
            output.append({
                'metric_code': row.metric_code,
                'metric_name': row.metric_name,
                'total_executions': row.total_executions,
                'success_count': row.success_count,
                'failed_count': row.failed_count,
                'timeout_count': row.timeout_count,
                'success_rate': f'{success_rate:.2f}%',
                'avg_duration_seconds': float(row.avg_duration) if row.avg_duration else 0
            })
        
        return output
    
    def get_job_logs(self, job_code: str) -> List[Dict[str, Any]]:
        """获取作业日志"""
        job = self.session.query(JobInstance).filter(
            JobInstance.job_code == job_code
        ).first()
        
        if not job:
            return []
        
        logs = self.session.query(JobLog).filter(
            JobLog.job_instance_id == job.id
        ).order_by(JobLog.log_time).all()
        
        result = []
        for log in logs:
            result.append({
                'log_time': log.log_time.strftime('%Y-%m-%d %H:%M:%S'),
                'log_level': log.log_level,
                'log_message': log.log_message
            })
        
        return result
    
    def get_metric_results(self, metric_code: str, hours: int = 24, limit: int = 100) -> List[Dict[str, Any]]:
        """获取指标结果"""
        start_time = datetime.now() - timedelta(hours=hours)
        
        results = self.session.query(MetricResult).join(
            Metric
        ).filter(
            Metric.metric_code == metric_code,
            MetricResult.created_at >= start_time
        ).order_by(desc(MetricResult.calc_time)).limit(limit).all()
        
        output = []
        for result in results:
            output.append({
                'calc_time': result.calc_time.strftime('%Y-%m-%d %H:%M:%S'),
                'dimension_key': result.dimension_key,
                'metric_value': float(result.metric_value) if result.metric_value else None,
                'dimension_values': result.dimension_values,
                'created_at': result.created_at.strftime('%Y-%m-%d %H:%M:%S')
            })
        
        return output
    
    def print_job_summary(self, hours: int = 24):
        """打印作业摘要"""
        summary = self.get_job_summary(hours)
        print(f"\n=== 作业执行摘要 ({summary['time_range']}) ===")
        print(f"总作业数: {summary['total_jobs']}")
        print(f"成功率: {summary['success_rate']}")
        print(f"平均执行时间: {summary['avg_duration_seconds']:.2f} 秒")
        print("\n状态分布:")
        for status, count in summary['status_distribution'].items():
            print(f"  {status}: {count}")
    
    def print_running_jobs(self):
        """打印运行中的作业"""
        jobs = self.get_running_jobs()
        if not jobs:
            print("\n没有正在运行的作业")
            return
        
        print(f"\n=== 正在运行的作业 ({len(jobs)} 个) ===")
        headers = ['作业代码', '指标代码', '开始时间', '已运行(秒)', '超时时间(秒)']
        rows = []
        for job in jobs:
            rows.append([
                job['job_code'][:30],
                job['metric_code'],
                job['start_time'],
                f"{job['duration_seconds']:.0f}",
                job['timeout_seconds']
            ])
        print(tabulate(rows, headers=headers, tablefmt='grid'))
    
    def print_failed_jobs(self, hours: int = 24):
        """打印失败的作业"""
        jobs = self.get_failed_jobs(hours)
        if not jobs:
            print(f"\n最近 {hours} 小时没有失败的作业")
            return
        
        print(f"\n=== 失败的作业 (最近 {hours} 小时, 共 {len(jobs)} 个) ===")
        headers = ['作业代码', '指标代码', '状态', '重试次数', '错误信息', '计划时间']
        rows = []
        for job in jobs:
            rows.append([
                job['job_code'][:30],
                job['metric_code'],
                job['status'],
                job['retry_count'],
                job['error_message'][:50],
                job['scheduled_time']
            ])
        print(tabulate(rows, headers=headers, tablefmt='grid'))
    
    def print_metric_performance(self, days: int = 7):
        """打印指标性能统计"""
        metrics = self.get_metric_performance(days=days)
        if not metrics:
            print(f"\n最近 {days} 天没有指标执行记录")
            return
        
        print(f"\n=== 指标执行性能统计 (最近 {days} 天) ===")
        headers = ['指标代码', '指标名称', '总执行', '成功', '失败', '超时', '成功率', '平均耗时(秒)']
        rows = []
        for metric in metrics:
            rows.append([
                metric['metric_code'],
                metric['metric_name'][:20],
                metric['total_executions'],
                metric['success_count'],
                metric['failed_count'],
                metric['timeout_count'],
                metric['success_rate'],
                f"{metric['avg_duration_seconds']:.2f}"
            ])
        print(tabulate(rows, headers=headers, tablefmt='grid'))
    
    def close(self):
        """关闭会话"""
        self.session.close()