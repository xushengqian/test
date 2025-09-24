#!/usr/bin/env python
"""
指标调度系统命令行工具
"""
import click
import sys
import os
from datetime import datetime
from loguru import logger
from tabulate import tabulate

# 添加项目路径
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from models import Session, Metric, Schedule, MetricSchedule, JobInstance
from monitor import JobMonitor
from scheduler import MetricScheduler
from config.config import config


@click.group()
def cli():
    """指标调度系统管理工具"""
    pass


@cli.command()
def start():
    """启动调度器"""
    # 配置日志
    logger.remove()
    logger.add(sys.stdout, level=config.log.log_level)
    logger.add(config.log.log_file, level=config.log.log_level, rotation="10 MB")
    
    # 创建日志目录
    os.makedirs(os.path.dirname(config.log.log_file), exist_ok=True)
    
    # 启动调度器
    scheduler = MetricScheduler()
    try:
        scheduler.start()
    except KeyboardInterrupt:
        logger.info("接收到停止信号")
    finally:
        scheduler.stop()


@cli.group()
def metric():
    """指标管理"""
    pass


@metric.command('list')
def list_metrics():
    """列出所有指标"""
    with Session() as session:
        metrics = session.query(Metric).all()
        if not metrics:
            click.echo("没有配置任何指标")
            return
        
        headers = ['ID', '指标代码', '指标名称', '类型', '是否启用', '创建时间']
        rows = []
        for m in metrics:
            rows.append([
                m.id,
                m.metric_code,
                m.metric_name[:30],
                m.metric_type,
                '是' if m.is_active else '否',
                m.created_at.strftime('%Y-%m-%d %H:%M:%S')
            ])
        click.echo(tabulate(rows, headers=headers, tablefmt='grid'))


@metric.command('add')
@click.option('--code', prompt='指标代码', help='指标代码')
@click.option('--name', prompt='指标名称', help='指标名称')
@click.option('--type', type=click.Choice(['SQL', 'STORED_PROCEDURE', 'SCRIPT']), 
              prompt='指标类型', help='指标类型')
@click.option('--sql', help='SQL语句或存储过程名称')
@click.option('--script', help='脚本文件路径')
@click.option('--desc', help='指标描述')
def add_metric(code, name, type, sql, script, desc):
    """添加新指标"""
    with Session() as session:
        # 检查指标是否已存在
        if session.query(Metric).filter_by(metric_code=code).first():
            click.echo(f"错误：指标代码 {code} 已存在")
            return
        
        # 创建指标
        metric = Metric(
            metric_code=code,
            metric_name=name,
            metric_type=type,
            metric_sql=sql,
            script_path=script,
            description=desc
        )
        session.add(metric)
        session.commit()
        
        click.echo(f"成功添加指标: {code}")


@metric.command('delete')
@click.argument('code')
def delete_metric(code):
    """删除指标"""
    with Session() as session:
        metric = session.query(Metric).filter_by(metric_code=code).first()
        if not metric:
            click.echo(f"错误：指标 {code} 不存在")
            return
        
        # 检查是否有关联的调度
        if metric.metric_schedules:
            click.echo(f"错误：指标 {code} 存在关联的调度配置，请先删除调度配置")
            return
        
        session.delete(metric)
        session.commit()
        click.echo(f"成功删除指标: {code}")


@cli.group()
def schedule():
    """调度管理"""
    pass


@schedule.command('list')
def list_schedules():
    """列出所有调度"""
    with Session() as session:
        schedules = session.query(Schedule).all()
        if not schedules:
            click.echo("没有配置任何调度")
            return
        
        headers = ['ID', '调度代码', '调度名称', '类型', '表达式/频率', '是否启用']
        rows = []
        for s in schedules:
            expr = s.cron_expression if s.schedule_type == 'CRON' else f"{s.fixed_rate_seconds}秒"
            rows.append([
                s.id,
                s.schedule_code,
                s.schedule_name,
                s.schedule_type,
                expr,
                '是' if s.is_active else '否'
            ])
        click.echo(tabulate(rows, headers=headers, tablefmt='grid'))


@schedule.command('bind')
@click.option('--metric', prompt='指标代码', help='指标代码')
@click.option('--schedule', prompt='调度代码', help='调度代码')
@click.option('--priority', default=0, help='优先级')
@click.option('--retry', default=3, help='最大重试次数')
@click.option('--timeout', default=3600, help='超时时间(秒)')
def bind_schedule(metric, schedule, priority, retry, timeout):
    """绑定指标和调度"""
    with Session() as session:
        # 查找指标和调度
        metric_obj = session.query(Metric).filter_by(metric_code=metric).first()
        if not metric_obj:
            click.echo(f"错误：指标 {metric} 不存在")
            return
        
        schedule_obj = session.query(Schedule).filter_by(schedule_code=schedule).first()
        if not schedule_obj:
            click.echo(f"错误：调度 {schedule} 不存在")
            return
        
        # 检查是否已存在
        existing = session.query(MetricSchedule).filter_by(
            metric_id=metric_obj.id,
            schedule_id=schedule_obj.id
        ).first()
        if existing:
            click.echo(f"错误：指标 {metric} 和调度 {schedule} 已经绑定")
            return
        
        # 创建绑定
        metric_schedule = MetricSchedule(
            metric_id=metric_obj.id,
            schedule_id=schedule_obj.id,
            priority=priority,
            max_retry_times=retry,
            timeout_seconds=timeout
        )
        session.add(metric_schedule)
        session.commit()
        
        click.echo(f"成功绑定指标 {metric} 和调度 {schedule}")


@cli.group()
def monitor():
    """监控管理"""
    pass


@monitor.command('summary')
@click.option('--hours', default=24, help='统计时间范围(小时)')
def monitor_summary(hours):
    """查看作业执行摘要"""
    monitor = JobMonitor()
    try:
        monitor.print_job_summary(hours)
    finally:
        monitor.close()


@monitor.command('running')
def monitor_running():
    """查看运行中的作业"""
    monitor = JobMonitor()
    try:
        monitor.print_running_jobs()
    finally:
        monitor.close()


@monitor.command('failed')
@click.option('--hours', default=24, help='时间范围(小时)')
def monitor_failed(hours):
    """查看失败的作业"""
    monitor = JobMonitor()
    try:
        monitor.print_failed_jobs(hours)
    finally:
        monitor.close()


@monitor.command('performance')
@click.option('--days', default=7, help='统计天数')
def monitor_performance(days):
    """查看指标性能统计"""
    monitor = JobMonitor()
    try:
        monitor.print_metric_performance(days)
    finally:
        monitor.close()


@monitor.command('logs')
@click.argument('job_code')
def monitor_logs(job_code):
    """查看作业日志"""
    monitor = JobMonitor()
    try:
        logs = monitor.get_job_logs(job_code)
        if not logs:
            click.echo(f"没有找到作业 {job_code} 的日志")
            return
        
        click.echo(f"\n=== 作业日志: {job_code} ===")
        for log in logs:
            level_color = {
                'DEBUG': 'white',
                'INFO': 'green',
                'WARN': 'yellow',
                'ERROR': 'red'
            }.get(log['log_level'], 'white')
            
            click.echo(
                click.style(f"[{log['log_time']}] ", fg='blue') +
                click.style(f"[{log['log_level']}] ", fg=level_color) +
                log['log_message']
            )
    finally:
        monitor.close()


@monitor.command('results')
@click.argument('metric_code')
@click.option('--hours', default=24, help='时间范围(小时)')
@click.option('--limit', default=20, help='显示条数')
def monitor_results(metric_code, hours, limit):
    """查看指标结果"""
    monitor = JobMonitor()
    try:
        results = monitor.get_metric_results(metric_code, hours, limit)
        if not results:
            click.echo(f"最近 {hours} 小时没有指标 {metric_code} 的结果")
            return
        
        click.echo(f"\n=== 指标结果: {metric_code} (最近 {hours} 小时) ===")
        headers = ['计算时间', '维度', '指标值', '创建时间']
        rows = []
        for result in results[:limit]:
            rows.append([
                result['calc_time'],
                result['dimension_key'][:50] if result['dimension_key'] else '-',
                result['metric_value'] if result['metric_value'] is not None else '-',
                result['created_at']
            ])
        click.echo(tabulate(rows, headers=headers, tablefmt='grid'))
    finally:
        monitor.close()


if __name__ == '__main__':
    cli()