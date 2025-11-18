#!/usr/bin/env python3
"""
FreeSWITCH 通道检查和终止脚本
解决 uuid_kill 出现 "no such channel" 错误的问题
"""

import subprocess
import re
import sys
import argparse


def execute_fs_cli(command):
    """执行 FreeSWITCH CLI 命令"""
    try:
        result = subprocess.run(
            ['fs_cli', '-x', command],
            capture_output=True,
            text=True,
            timeout=10
        )
        return result.stdout.strip(), result.returncode == 0
    except subprocess.TimeoutExpired:
        return "", False
    except FileNotFoundError:
        print("错误: 未找到 fs_cli 命令，请确保 FreeSWITCH 已正确安装")
        sys.exit(1)
    except Exception as e:
        print(f"执行命令时出错: {e}")
        return "", False


def get_all_channels():
    """获取所有活动通道的 UUID"""
    output, success = execute_fs_cli("show channels")
    if not success:
        return []
    
    channels = []
    # 解析通道输出，提取 UUID
    # 格式通常是: UUID, [state], [direction], [number], ...
    for line in output.split('\n'):
        line = line.strip()
        if not line or line.startswith('UUID') or line.startswith('---'):
            continue
        
        # 提取 UUID (通常是第一个字段)
        parts = line.split(',')
        if parts:
            uuid = parts[0].strip()
            # 验证 UUID 格式 (FreeSWITCH UUID 通常是 36 字符，包含连字符)
            if re.match(r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', uuid, re.I):
                channels.append(uuid)
    
    return channels


def check_channel_exists(uuid):
    """检查指定 UUID 的通道是否存在"""
    output, success = execute_fs_cli(f"uuid_exists {uuid}")
    if success:
        return "true" in output.lower() or "exists" in output.lower()
    return False


def kill_channel(uuid, force=False):
    """终止指定 UUID 的通道"""
    # 首先检查通道是否存在
    if not check_channel_exists(uuid):
        print(f"警告: 通道 {uuid} 不存在或已断开")
        return False
    
    # 使用 uuid_kill 命令
    command = f"uuid_kill {uuid}"
    if force:
        command += " KILL"
    
    output, success = execute_fs_cli(command)
    
    if success:
        if "no such channel" in output.lower() or "not found" in output.lower():
            print(f"错误: 通道 {uuid} 不存在 (no such channel)")
            return False
        print(f"成功终止通道: {uuid}")
        return True
    else:
        print(f"终止通道失败: {uuid}")
        print(f"输出: {output}")
        return False


def kill_all_channels():
    """终止所有活动通道"""
    channels = get_all_channels()
    if not channels:
        print("没有找到活动通道")
        return
    
    print(f"找到 {len(channels)} 个活动通道")
    success_count = 0
    
    for uuid in channels:
        print(f"\n处理通道: {uuid}")
        if kill_channel(uuid):
            success_count += 1
    
    print(f"\n完成: 成功终止 {success_count}/{len(channels)} 个通道")


def main():
    parser = argparse.ArgumentParser(
        description='FreeSWITCH 通道检查和终止工具',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  %(prog)s --list                    # 列出所有通道
  %(prog)s --kill <UUID>            # 终止指定通道
  %(prog)s --kill-all               # 终止所有通道
  %(prog)s --check <UUID>           # 检查通道是否存在
        """
    )
    
    parser.add_argument('--list', '-l', action='store_true',
                       help='列出所有活动通道')
    parser.add_argument('--kill', '-k', metavar='UUID',
                       help='终止指定 UUID 的通道')
    parser.add_argument('--kill-all', '-a', action='store_true',
                       help='终止所有活动通道')
    parser.add_argument('--check', '-c', metavar='UUID',
                       help='检查指定 UUID 的通道是否存在')
    parser.add_argument('--force', '-f', action='store_true',
                       help='强制终止通道')
    
    args = parser.parse_args()
    
    if args.list:
        channels = get_all_channels()
        if channels:
            print(f"找到 {len(channels)} 个活动通道:")
            for uuid in channels:
                print(f"  {uuid}")
        else:
            print("没有找到活动通道")
    
    elif args.kill:
        kill_channel(args.kill, args.force)
    
    elif args.kill_all:
        kill_all_channels()
    
    elif args.check:
        exists = check_channel_exists(args.check)
        if exists:
            print(f"通道 {args.check} 存在")
        else:
            print(f"通道 {args.check} 不存在")
    
    else:
        parser.print_help()


if __name__ == '__main__':
    main()
