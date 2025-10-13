#!/usr/bin/python3
"""
FreeSWITCH 实时性能监控工具
提供实时的性能指标监控和告警
"""

import time
import subprocess
import json
import sys
import os
from datetime import datetime
import argparse

class FreeSWITCHMonitor:
    def __init__(self):
        self.thresholds = {
            'cpu_usage': 80.0,      # CPU使用率阈值
            'memory_usage': 85.0,   # 内存使用率阈值
            'max_sessions': 25000,  # 最大会话数阈值
            'session_rate': 400     # 每秒会话创建速率阈值
        }
        
    def get_system_stats(self):
        """获取系统统计信息"""
        stats = {}
        
        try:
            # CPU 使用率
            cpu_cmd = "top -bn1 | grep 'Cpu(s)' | awk '{print $2}' | sed 's/%us,//'"
            result = subprocess.run(cpu_cmd, shell=True, capture_output=True, text=True)
            stats['cpu_usage'] = float(result.stdout.strip()) if result.stdout.strip() else 0.0
            
            # 内存使用率
            mem_cmd = "free | awk 'FNR==2{printf \"%.2f\", $3/($3+$4)*100}'"
            result = subprocess.run(mem_cmd, shell=True, capture_output=True, text=True)
            stats['memory_usage'] = float(result.stdout.strip()) if result.stdout.strip() else 0.0
            
            # 系统负载
            load_cmd = "uptime | awk '{print $(NF-2)}' | sed 's/,//'"
            result = subprocess.run(load_cmd, shell=True, capture_output=True, text=True)
            stats['system_load'] = float(result.stdout.strip()) if result.stdout.strip() else 0.0
            
        except Exception as e:
            print(f"获取系统统计信息时出错: {e}")
            
        return stats
    
    def get_freeswitch_stats(self):
        """获取FreeSWITCH统计信息"""
        stats = {}
        
        try:
            # 检查FreeSWITCH是否运行
            ps_cmd = "pgrep freeswitch"
            result = subprocess.run(ps_cmd, shell=True, capture_output=True, text=True)
            if not result.stdout.strip():
                stats['running'] = False
                return stats
                
            stats['running'] = True
            
            # 获取状态信息
            status_cmd = "fs_cli -x 'status' 2>/dev/null"
            result = subprocess.run(status_cmd, shell=True, capture_output=True, text=True)
            if result.returncode == 0:
                status_output = result.stdout
                
                # 解析状态输出
                for line in status_output.split('\n'):
                    if 'session(s) since startup' in line:
                        stats['total_sessions'] = int(line.split()[0])
                    elif 'session(s)' in line and 'peak' in line:
                        stats['peak_sessions'] = int(line.split()[0])
                    elif 'session(s)/Sec out of max' in line:
                        parts = line.split()
                        stats['current_sessions'] = int(parts[0])
                        stats['sessions_per_sec'] = int(parts[1].split('/')[0])
                        stats['max_sessions'] = int(parts[5].replace(',', ''))
            
            # 获取通道数量
            channels_cmd = "fs_cli -x 'show channels count' 2>/dev/null"
            result = subprocess.run(channels_cmd, shell=True, capture_output=True, text=True)
            if result.returncode == 0:
                try:
                    stats['active_channels'] = int(result.stdout.strip().split()[-1])
                except:
                    stats['active_channels'] = 0
            
            # 获取注册数量
            reg_cmd = "fs_cli -x 'show registrations count' 2>/dev/null"
            result = subprocess.run(reg_cmd, shell=True, capture_output=True, text=True)
            if result.returncode == 0:
                try:
                    stats['registrations'] = int(result.stdout.strip().split()[-1])
                except:
                    stats['registrations'] = 0
                    
        except Exception as e:
            print(f"获取FreeSWITCH统计信息时出错: {e}")
            
        return stats
    
    def check_alerts(self, system_stats, fs_stats):
        """检查告警条件"""
        alerts = []
        
        # CPU使用率告警
        if system_stats.get('cpu_usage', 0) > self.thresholds['cpu_usage']:
            alerts.append(f"CPU使用率过高: {system_stats['cpu_usage']:.1f}%")
            
        # 内存使用率告警
        if system_stats.get('memory_usage', 0) > self.thresholds['memory_usage']:
            alerts.append(f"内存使用率过高: {system_stats['memory_usage']:.1f}%")
            
        # FreeSWITCH会话数告警
        if fs_stats.get('current_sessions', 0) > self.thresholds['max_sessions']:
            alerts.append(f"当前会话数过高: {fs_stats['current_sessions']}")
            
        # 会话创建速率告警
        if fs_stats.get('sessions_per_sec', 0) > self.thresholds['session_rate']:
            alerts.append(f"会话创建速率过高: {fs_stats['sessions_per_sec']} cps")
            
        # FreeSWITCH状态告警
        if not fs_stats.get('running', True):
            alerts.append("FreeSWITCH 进程未运行!")
            
        return alerts
    
    def print_stats(self, system_stats, fs_stats, alerts):
        """打印统计信息"""
        os.system('clear')  # 清屏
        
        print("=" * 60)
        print(f"FreeSWITCH 实时性能监控 - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print("=" * 60)
        
        # 系统信息
        print(f"\n📊 系统资源:")
        print(f"   CPU 使用率:    {system_stats.get('cpu_usage', 0):.1f}%")
        print(f"   内存使用率:    {system_stats.get('memory_usage', 0):.1f}%")
        print(f"   系统负载:      {system_stats.get('system_load', 0):.2f}")
        
        # FreeSWITCH信息
        print(f"\n📞 FreeSWITCH 状态:")
        if fs_stats.get('running', False):
            print(f"   运行状态:      ✅ 运行中")
            print(f"   当前会话:      {fs_stats.get('current_sessions', 0):,}")
            print(f"   峰值会话:      {fs_stats.get('peak_sessions', 0):,}")
            print(f"   总会话数:      {fs_stats.get('total_sessions', 0):,}")
            print(f"   会话速率:      {fs_stats.get('sessions_per_sec', 0)} cps")
            print(f"   最大会话:      {fs_stats.get('max_sessions', 0):,}")
            print(f"   活动通道:      {fs_stats.get('active_channels', 0):,}")
            print(f"   注册用户:      {fs_stats.get('registrations', 0):,}")
        else:
            print(f"   运行状态:      ❌ 未运行")
        
        # 告警信息
        if alerts:
            print(f"\n🚨 告警信息:")
            for alert in alerts:
                print(f"   ⚠️  {alert}")
        else:
            print(f"\n✅ 系统运行正常")
        
        # 性能建议
        cpu_usage = system_stats.get('cpu_usage', 0)
        mem_usage = system_stats.get('memory_usage', 0)
        current_sessions = fs_stats.get('current_sessions', 0)
        
        print(f"\n💡 性能建议:")
        if cpu_usage > 60:
            print(f"   • 考虑优化编解码器配置或增加CPU资源")
        if mem_usage > 70:
            print(f"   • 监控内存泄漏，考虑重启FreeSWITCH")
        if current_sessions > 20000:
            print(f"   • 接近最大容量，建议准备扩容")
        if not alerts:
            print(f"   • 系统运行良好，继续监控")
            
        print(f"\n按 Ctrl+C 退出监控")
        print("=" * 60)
    
    def run_monitor(self, interval=5):
        """运行监控"""
        print("启动 FreeSWITCH 实时性能监控...")
        print(f"监控间隔: {interval} 秒")
        
        try:
            while True:
                system_stats = self.get_system_stats()
                fs_stats = self.get_freeswitch_stats()
                alerts = self.check_alerts(system_stats, fs_stats)
                
                self.print_stats(system_stats, fs_stats, alerts)
                
                time.sleep(interval)
                
        except KeyboardInterrupt:
            print("\n\n监控已停止")
            sys.exit(0)
        except Exception as e:
            print(f"\n监控过程中发生错误: {e}")
            sys.exit(1)

def main():
    parser = argparse.ArgumentParser(description='FreeSWITCH 实时性能监控工具')
    parser.add_argument('-i', '--interval', type=int, default=5, 
                       help='监控间隔（秒），默认5秒')
    
    args = parser.parse_args()
    
    monitor = FreeSWITCHMonitor()
    monitor.run_monitor(args.interval)

if __name__ == "__main__":
    main()