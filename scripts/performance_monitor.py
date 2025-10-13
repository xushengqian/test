#!/usr/bin/env python3
"""
FreeSWITCH 性能监控脚本
实时监控 FreeSWITCH 性能指标并生成报告
"""

import os
import sys
import time
import json
import psutil
import socket
import subprocess
import datetime
import argparse
from collections import deque
import threading

class FreeSwitchMonitor:
    def __init__(self, fs_cli_path="/usr/local/freeswitch/bin/fs_cli"):
        self.fs_cli = fs_cli_path
        self.metrics_history = {
            'cpu': deque(maxlen=60),
            'memory': deque(maxlen=60),
            'calls': deque(maxlen=60),
            'channels': deque(maxlen=60),
            'sps': deque(maxlen=60),  # Sessions Per Second
            'network': deque(maxlen=60)
        }
        self.alert_thresholds = {
            'cpu_percent': 80,
            'memory_percent': 85,
            'max_calls': 5000,
            'max_channels': 10000,
            'max_sps': 300
        }
        self.fs_pid = None
        self.monitoring = False
        
    def find_freeswitch_pid(self):
        """查找 FreeSWITCH 进程 PID"""
        for proc in psutil.process_iter(['pid', 'name']):
            if proc.info['name'] == 'freeswitch':
                self.fs_pid = proc.info['pid']
                return True
        return False
    
    def execute_fs_cli(self, command):
        """执行 fs_cli 命令"""
        try:
            cmd = f"{self.fs_cli} -x '{command}'"
            result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=5)
            return result.stdout.strip()
        except Exception as e:
            print(f"执行命令失败: {e}")
            return None
    
    def get_system_metrics(self):
        """获取系统指标"""
        metrics = {}
        
        # CPU 使用率
        metrics['cpu_percent'] = psutil.cpu_percent(interval=1)
        metrics['cpu_count'] = psutil.cpu_count()
        
        # 内存使用
        mem = psutil.virtual_memory()
        metrics['memory_total'] = mem.total
        metrics['memory_used'] = mem.used
        metrics['memory_percent'] = mem.percent
        metrics['memory_available'] = mem.available
        
        # 磁盘使用
        disk = psutil.disk_usage('/')
        metrics['disk_total'] = disk.total
        metrics['disk_used'] = disk.used
        metrics['disk_percent'] = disk.percent
        
        # 网络统计
        net = psutil.net_io_counters()
        metrics['net_bytes_sent'] = net.bytes_sent
        metrics['net_bytes_recv'] = net.bytes_recv
        metrics['net_packets_sent'] = net.packets_sent
        metrics['net_packets_recv'] = net.packets_recv
        
        return metrics
    
    def get_freeswitch_metrics(self):
        """获取 FreeSWITCH 指标"""
        metrics = {}
        
        # 获取状态信息
        status = self.execute_fs_cli("status")
        if status:
            lines = status.split('\n')
            for line in lines:
                if 'session(s) since startup' in line:
                    metrics['total_sessions'] = int(line.split()[0])
                elif 'session(s) - peak' in line:
                    parts = line.split()
                    metrics['current_sessions'] = int(parts[0])
                    metrics['peak_sessions'] = int(parts[3].strip(','))
                    metrics['max_sessions'] = int(parts[5])
                elif 'session(s) per Sec' in line:
                    parts = line.split()
                    metrics['current_sps'] = int(parts[0])
                    metrics['peak_sps'] = int(parts[5].strip(','))
                    metrics['max_sps'] = int(parts[7])
        
        # 获取通道数
        channels = self.execute_fs_cli("show channels count")
        if channels:
            for line in channels.split('\n'):
                if 'total' in line.lower():
                    metrics['total_channels'] = int(line.split()[0])
        
        # 获取呼叫数
        calls = self.execute_fs_cli("show calls count")
        if calls:
            for line in calls.split('\n'):
                if 'total' in line.lower():
                    metrics['total_calls'] = int(line.split()[0])
        
        # 获取注册用户数
        registrations = self.execute_fs_cli("sofia status profile internal reg count")
        if registrations:
            for line in registrations.split('\n'):
                if 'total' in line.lower():
                    metrics['registrations'] = int(line.split()[-1])
        
        # 获取 FreeSWITCH 进程信息
        if self.fs_pid:
            try:
                fs_process = psutil.Process(self.fs_pid)
                metrics['fs_cpu_percent'] = fs_process.cpu_percent()
                metrics['fs_memory_percent'] = fs_process.memory_percent()
                metrics['fs_memory_rss'] = fs_process.memory_info().rss
                metrics['fs_num_threads'] = fs_process.num_threads()
                metrics['fs_num_fds'] = fs_process.num_fds()
                metrics['fs_connections'] = len(fs_process.connections())
            except:
                pass
        
        return metrics
    
    def check_alerts(self, system_metrics, fs_metrics):
        """检查告警条件"""
        alerts = []
        
        # CPU 告警
        if system_metrics.get('cpu_percent', 0) > self.alert_thresholds['cpu_percent']:
            alerts.append(f"⚠️ CPU 使用率过高: {system_metrics['cpu_percent']}%")
        
        # 内存告警
        if system_metrics.get('memory_percent', 0) > self.alert_thresholds['memory_percent']:
            alerts.append(f"⚠️ 内存使用率过高: {system_metrics['memory_percent']}%")
        
        # 呼叫数告警
        if fs_metrics.get('total_calls', 0) > self.alert_thresholds['max_calls']:
            alerts.append(f"⚠️ 呼叫数过多: {fs_metrics['total_calls']}")
        
        # 通道数告警
        if fs_metrics.get('total_channels', 0) > self.alert_thresholds['max_channels']:
            alerts.append(f"⚠️ 通道数过多: {fs_metrics['total_channels']}")
        
        # SPS 告警
        if fs_metrics.get('current_sps', 0) > self.alert_thresholds['max_sps']:
            alerts.append(f"⚠️ SPS 过高: {fs_metrics['current_sps']}")
        
        return alerts
    
    def print_dashboard(self, system_metrics, fs_metrics, alerts):
        """打印监控仪表板"""
        os.system('clear')
        
        timestamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print("=" * 80)
        print(f" FreeSWITCH 性能监控仪表板 - {timestamp}")
        print("=" * 80)
        
        # 系统资源
        print("\n📊 系统资源:")
        print("-" * 40)
        cpu_bar = self.create_progress_bar(system_metrics.get('cpu_percent', 0), 100)
        mem_bar = self.create_progress_bar(system_metrics.get('memory_percent', 0), 100)
        print(f"  CPU 使用率:    {cpu_bar} {system_metrics.get('cpu_percent', 0):.1f}%")
        print(f"  内存使用率:    {mem_bar} {system_metrics.get('memory_percent', 0):.1f}%")
        print(f"  可用内存:      {self.format_bytes(system_metrics.get('memory_available', 0))}")
        print(f"  磁盘使用率:    {system_metrics.get('disk_percent', 0):.1f}%")
        
        # FreeSWITCH 状态
        print("\n☎️  FreeSWITCH 状态:")
        print("-" * 40)
        if fs_metrics:
            print(f"  当前会话数:    {fs_metrics.get('current_sessions', 0)} / {fs_metrics.get('max_sessions', 0)}")
            print(f"  峰值会话数:    {fs_metrics.get('peak_sessions', 0)}")
            print(f"  当前 SPS:      {fs_metrics.get('current_sps', 0)} / {fs_metrics.get('max_sps', 0)}")
            print(f"  峰值 SPS:      {fs_metrics.get('peak_sps', 0)}")
            print(f"  活动通道数:    {fs_metrics.get('total_channels', 0)}")
            print(f"  活动呼叫数:    {fs_metrics.get('total_calls', 0)}")
            print(f"  注册用户数:    {fs_metrics.get('registrations', 0)}")
            
            # FreeSWITCH 进程信息
            if 'fs_cpu_percent' in fs_metrics:
                print(f"\n  FS进程 CPU:    {fs_metrics.get('fs_cpu_percent', 0):.1f}%")
                print(f"  FS进程内存:    {self.format_bytes(fs_metrics.get('fs_memory_rss', 0))} ({fs_metrics.get('fs_memory_percent', 0):.1f}%)")
                print(f"  FS线程数:      {fs_metrics.get('fs_num_threads', 0)}")
                print(f"  FS文件句柄:    {fs_metrics.get('fs_num_fds', 0)}")
                print(f"  FS网络连接:    {fs_metrics.get('fs_connections', 0)}")
        else:
            print("  FreeSWITCH 未运行或无法获取状态")
        
        # 网络统计
        print("\n🌐 网络统计:")
        print("-" * 40)
        print(f"  发送字节:      {self.format_bytes(system_metrics.get('net_bytes_sent', 0))}")
        print(f"  接收字节:      {self.format_bytes(system_metrics.get('net_bytes_recv', 0))}")
        print(f"  发送包数:      {system_metrics.get('net_packets_sent', 0):,}")
        print(f"  接收包数:      {system_metrics.get('net_packets_recv', 0):,}")
        
        # 告警信息
        if alerts:
            print("\n⚠️  告警信息:")
            print("-" * 40)
            for alert in alerts:
                print(f"  {alert}")
        
        # 操作提示
        print("\n" + "=" * 80)
        print("按 Ctrl+C 退出监控")
    
    def create_progress_bar(self, value, max_value, length=20):
        """创建进度条"""
        if max_value == 0:
            return "[" + " " * length + "]"
        
        filled = int(length * value / max_value)
        bar = "█" * filled + "░" * (length - filled)
        
        # 根据使用率添加颜色
        if value > 80:
            color = "\033[91m"  # 红色
        elif value > 60:
            color = "\033[93m"  # 黄色
        else:
            color = "\033[92m"  # 绿色
        
        return f"{color}[{bar}]\033[0m"
    
    def format_bytes(self, bytes):
        """格式化字节数"""
        for unit in ['B', 'KB', 'MB', 'GB', 'TB']:
            if bytes < 1024.0:
                return f"{bytes:.2f} {unit}"
            bytes /= 1024.0
        return f"{bytes:.2f} PB"
    
    def save_metrics_to_file(self, system_metrics, fs_metrics, filename="metrics.json"):
        """保存指标到文件"""
        timestamp = datetime.datetime.now().isoformat()
        data = {
            'timestamp': timestamp,
            'system': system_metrics,
            'freeswitch': fs_metrics
        }
        
        # 追加到 JSON Lines 文件
        with open(filename, 'a') as f:
            f.write(json.dumps(data) + '\n')
    
    def start_monitoring(self, interval=5, save_to_file=False):
        """启动监控"""
        self.monitoring = True
        
        print("正在启动 FreeSWITCH 性能监控...")
        
        # 查找 FreeSWITCH 进程
        if not self.find_freeswitch_pid():
            print("警告: 未找到 FreeSWITCH 进程")
        
        try:
            while self.monitoring:
                # 获取指标
                system_metrics = self.get_system_metrics()
                fs_metrics = self.get_freeswitch_metrics()
                
                # 检查告警
                alerts = self.check_alerts(system_metrics, fs_metrics)
                
                # 更新历史数据
                self.metrics_history['cpu'].append(system_metrics.get('cpu_percent', 0))
                self.metrics_history['memory'].append(system_metrics.get('memory_percent', 0))
                self.metrics_history['calls'].append(fs_metrics.get('total_calls', 0))
                self.metrics_history['channels'].append(fs_metrics.get('total_channels', 0))
                self.metrics_history['sps'].append(fs_metrics.get('current_sps', 0))
                
                # 显示仪表板
                self.print_dashboard(system_metrics, fs_metrics, alerts)
                
                # 保存到文件
                if save_to_file:
                    self.save_metrics_to_file(system_metrics, fs_metrics)
                
                # 等待下一次采样
                time.sleep(interval)
                
        except KeyboardInterrupt:
            print("\n\n监控已停止")
            self.monitoring = False
    
    def generate_report(self):
        """生成性能报告"""
        if not any(self.metrics_history.values()):
            print("没有足够的历史数据生成报告")
            return
        
        print("\n" + "=" * 80)
        print(" FreeSWITCH 性能报告")
        print("=" * 80)
        
        # 计算统计数据
        for metric_name, data in self.metrics_history.items():
            if data:
                avg_val = sum(data) / len(data)
                max_val = max(data)
                min_val = min(data)
                
                print(f"\n{metric_name.upper()} 统计:")
                print(f"  平均值: {avg_val:.2f}")
                print(f"  最大值: {max_val:.2f}")
                print(f"  最小值: {min_val:.2f}")

def main():
    parser = argparse.ArgumentParser(description='FreeSWITCH 性能监控工具')
    parser.add_argument('-i', '--interval', type=int, default=5,
                        help='监控间隔（秒）')
    parser.add_argument('-s', '--save', action='store_true',
                        help='保存指标到文件')
    parser.add_argument('-f', '--fs-cli', default='/usr/local/freeswitch/bin/fs_cli',
                        help='fs_cli 路径')
    parser.add_argument('--cpu-threshold', type=int, default=80,
                        help='CPU 告警阈值（%）')
    parser.add_argument('--mem-threshold', type=int, default=85,
                        help='内存告警阈值（%）')
    
    args = parser.parse_args()
    
    # 创建监控实例
    monitor = FreeSwitchMonitor(fs_cli_path=args.fs_cli)
    
    # 设置告警阈值
    monitor.alert_thresholds['cpu_percent'] = args.cpu_threshold
    monitor.alert_thresholds['memory_percent'] = args.mem_threshold
    
    # 启动监控
    try:
        monitor.start_monitoring(interval=args.interval, save_to_file=args.save)
    finally:
        # 生成报告
        monitor.generate_report()

if __name__ == "__main__":
    main()