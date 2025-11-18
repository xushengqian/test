#!/usr/bin/env python3
"""
FreeSWITCH 通道检查和终止工具

解决 'show channels' 显示通道但 'uuid_kill' 提示 'no such channel' 的问题
"""

import subprocess
import sys
import re
import time
from typing import List, Dict, Optional


class FreeSwitchChannelManager:
    """FreeSWITCH 通道管理器"""
    
    def __init__(self, fs_cli_path: str = "fs_cli"):
        """
        初始化
        
        Args:
            fs_cli_path: fs_cli 命令的路径，默认为 "fs_cli"
        """
        self.fs_cli_path = fs_cli_path
    
    def execute_command(self, command: str) -> Optional[str]:
        """
        执行 FreeSWITCH 命令
        
        Args:
            command: 要执行的命令
            
        Returns:
            命令输出，如果失败则返回 None
        """
        try:
            result = subprocess.run(
                [self.fs_cli_path, "-x", command],
                capture_output=True,
                text=True,
                timeout=10
            )
            return result.stdout
        except subprocess.TimeoutExpired:
            print(f"命令执行超时: {command}", file=sys.stderr)
            return None
        except FileNotFoundError:
            print(f"找不到 fs_cli 命令: {self.fs_cli_path}", file=sys.stderr)
            print("请确保 FreeSWITCH 已安装并且 fs_cli 在 PATH 中", file=sys.stderr)
            return None
        except Exception as e:
            print(f"执行命令时出错: {e}", file=sys.stderr)
            return None
    
    def get_channels(self) -> List[Dict[str, str]]:
        """
        获取所有活动通道
        
        Returns:
            通道信息列表，每个通道包含 uuid、direction、created 等信息
        """
        output = self.execute_command("show channels")
        if not output:
            return []
        
        channels = []
        lines = output.strip().split('\n')
        
        # 查找包含 UUID 的行
        for line in lines:
            # FreeSWITCH UUID 格式: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
            uuid_match = re.search(r'([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})', line)
            if uuid_match:
                uuid = uuid_match.group(1).strip()
                channels.append({
                    'uuid': uuid,
                    'raw_line': line.strip()
                })
        
        return channels
    
    def check_channel_exists(self, uuid: str) -> bool:
        """
        检查通道是否真实存在
        
        Args:
            uuid: 通道 UUID
            
        Returns:
            True 如果通道存在，否则 False
        """
        output = self.execute_command(f"uuid_exists {uuid}")
        if output and "true" in output.lower():
            return True
        return False
    
    def kill_channel(self, uuid: str, cause: str = "NORMAL_CLEARING") -> bool:
        """
        终止指定通道
        
        Args:
            uuid: 通道 UUID
            cause: 挂断原因，默认为 NORMAL_CLEARING
            
        Returns:
            True 如果成功，否则 False
        """
        output = self.execute_command(f"uuid_kill {uuid} {cause}")
        if output:
            if "no such channel" in output.lower():
                return False
            return True
        return False
    
    def force_kill_channel(self, uuid: str) -> bool:
        """
        强制终止通道（尝试多种方法）
        
        Args:
            uuid: 通道 UUID
            
        Returns:
            True 如果成功，否则 False
        """
        # 方法1: uuid_kill
        if self.kill_channel(uuid):
            return True
        
        # 方法2: uuid_break
        output = self.execute_command(f"uuid_break {uuid}")
        time.sleep(0.5)
        
        # 方法3: uuid_park 然后 uuid_kill
        self.execute_command(f"uuid_park {uuid}")
        time.sleep(0.5)
        
        # 再次尝试 kill
        return self.kill_channel(uuid)


def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(
        description="FreeSWITCH 通道检查和终止工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  # 列出所有通道
  python freeswitch_channel_killer.py --list
  
  # 验证通道是否存在
  python freeswitch_channel_killer.py --check <UUID>
  
  # 终止指定通道
  python freeswitch_channel_killer.py --kill <UUID>
  
  # 强制终止通道（尝试多种方法）
  python freeswitch_channel_killer.py --force-kill <UUID>
  
  # 终止所有通道
  python freeswitch_channel_killer.py --kill-all
        """
    )
    
    parser.add_argument(
        "--list",
        action="store_true",
        help="列出所有活动通道"
    )
    parser.add_argument(
        "--check",
        metavar="UUID",
        help="检查指定 UUID 的通道是否存在"
    )
    parser.add_argument(
        "--kill",
        metavar="UUID",
        help="终止指定 UUID 的通道"
    )
    parser.add_argument(
        "--force-kill",
        metavar="UUID",
        help="强制终止指定 UUID 的通道（尝试多种方法）"
    )
    parser.add_argument(
        "--kill-all",
        action="store_true",
        help="终止所有活动通道"
    )
    parser.add_argument(
        "--fs-cli-path",
        default="fs_cli",
        help="fs_cli 命令的路径（默认: fs_cli）"
    )
    
    args = parser.parse_args()
    
    # 创建管理器
    manager = FreeSwitchChannelManager(fs_cli_path=args.fs_cli_path)
    
    # 列出通道
    if args.list:
        print("正在获取活动通道列表...")
        channels = manager.get_channels()
        
        if not channels:
            print("没有找到活动通道")
            return 0
        
        print(f"\n找到 {len(channels)} 个通道:\n")
        for i, channel in enumerate(channels, 1):
            uuid = channel['uuid']
            exists = manager.check_channel_exists(uuid)
            status = "✓ 有效" if exists else "✗ 无效"
            print(f"{i}. UUID: {uuid}")
            print(f"   状态: {status}")
            print(f"   详情: {channel['raw_line']}")
            print()
        
        return 0
    
    # 检查通道
    if args.check:
        uuid = args.check.strip()
        print(f"正在检查通道: {uuid}")
        
        exists = manager.check_channel_exists(uuid)
        if exists:
            print("✓ 通道存在")
            return 0
        else:
            print("✗ 通道不存在")
            return 1
    
    # 终止通道
    if args.kill:
        uuid = args.kill.strip()
        print(f"正在终止通道: {uuid}")
        
        # 先检查通道是否存在
        if not manager.check_channel_exists(uuid):
            print("✗ 通道不存在，无法终止")
            return 1
        
        # 终止通道
        if manager.kill_channel(uuid):
            print("✓ 通道已成功终止")
            return 0
        else:
            print("✗ 终止通道失败")
            return 1
    
    # 强制终止通道
    if args.force_kill:
        uuid = args.force_kill.strip()
        print(f"正在强制终止通道: {uuid}")
        
        # 先检查通道是否存在
        if not manager.check_channel_exists(uuid):
            print("✗ 通道不存在，无法终止")
            return 1
        
        # 强制终止通道
        if manager.force_kill_channel(uuid):
            print("✓ 通道已成功终止")
            
            # 验证通道是否真的被终止
            time.sleep(0.5)
            if not manager.check_channel_exists(uuid):
                print("✓ 已验证通道已关闭")
                return 0
            else:
                print("⚠ 通道可能仍然存在，请检查")
                return 1
        else:
            print("✗ 终止通道失败")
            return 1
    
    # 终止所有通道
    if args.kill_all:
        print("正在获取所有活动通道...")
        channels = manager.get_channels()
        
        if not channels:
            print("没有找到活动通道")
            return 0
        
        print(f"找到 {len(channels)} 个通道，开始终止...\n")
        
        success_count = 0
        fail_count = 0
        
        for i, channel in enumerate(channels, 1):
            uuid = channel['uuid']
            print(f"[{i}/{len(channels)}] 终止通道 {uuid}...")
            
            if manager.force_kill_channel(uuid):
                print(f"  ✓ 成功")
                success_count += 1
            else:
                print(f"  ✗ 失败")
                fail_count += 1
        
        print(f"\n完成: {success_count} 成功, {fail_count} 失败")
        return 0 if fail_count == 0 else 1
    
    # 如果没有指定任何操作，显示帮助
    parser.print_help()
    return 0


if __name__ == "__main__":
    sys.exit(main())
