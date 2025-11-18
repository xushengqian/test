#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
FreeSWITCH 通道检查和杀死脚本
解决 show channels 显示通道但 uuid_kill 报 "no such channel" 的问题
"""

import subprocess
import sys
import re
import argparse
from typing import Optional, List, Tuple


class FreeSwitchChannelManager:
    """FreeSWITCH 通道管理器"""
    
    def __init__(self, fs_cli: str = "fs_cli"):
        """
        初始化
        
        Args:
            fs_cli: FreeSWITCH CLI 命令路径
        """
        self.fs_cli = fs_cli
        self._check_fs_cli()
    
    def _check_fs_cli(self):
        """检查 fs_cli 是否可用"""
        try:
            subprocess.run(
                [self.fs_cli, "-x", "status"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=5
            )
        except (FileNotFoundError, subprocess.TimeoutExpired):
            print(f"错误: {self.fs_cli} 命令未找到或无法连接")
            print("请确保 FreeSWITCH 已安装或设置正确的 fs_cli 路径")
            sys.exit(1)
    
    def _execute_command(self, command: str) -> Tuple[str, int]:
        """
        执行 FreeSWITCH 命令
        
        Args:
            command: 要执行的命令
            
        Returns:
            (输出, 返回码) 元组
        """
        try:
            result = subprocess.run(
                [self.fs_cli, "-x", command],
                capture_output=True,
                text=True,
                timeout=10
            )
            return result.stdout.strip(), result.returncode
        except subprocess.TimeoutExpired:
            return "命令执行超时", 1
        except Exception as e:
            return f"执行错误: {str(e)}", 1
    
    def check_channel_exists(self, uuid: str) -> bool:
        """
        检查通道是否存在
        
        Args:
            uuid: 通道 UUID
            
        Returns:
            如果通道存在返回 True，否则返回 False
        """
        if not uuid:
            return False
        
        output, returncode = self._execute_command(f"uuid_exists {uuid}")
        
        # uuid_exists 返回 "true" 或 "false"
        return "true" in output.lower() and returncode == 0
    
    def get_channel_info(self, uuid: str) -> Optional[str]:
        """
        获取通道详细信息
        
        Args:
            uuid: 通道 UUID
            
        Returns:
            通道信息字符串，如果失败返回 None
        """
        output, returncode = self._execute_command(f"uuid_dump {uuid}")
        
        if returncode == 0:
            return output
        return None
    
    def kill_channel(self, uuid: str, verify: bool = True) -> Tuple[bool, str]:
        """
        安全地杀死通道
        
        Args:
            uuid: 通道 UUID
            verify: 是否在杀死前验证通道存在
            
        Returns:
            (成功标志, 消息) 元组
        """
        if not uuid:
            return False, "错误: UUID 不能为空"
        
        # 先验证通道是否存在
        if verify:
            print(f"检查通道 {uuid} 是否存在...")
            if not self.check_channel_exists(uuid):
                return False, f"错误: 通道 {uuid} 不存在或已关闭"
            
            # 显示通道信息
            info = self.get_channel_info(uuid)
            if info:
                print("通道信息:")
                print("\n".join(info.split("\n")[:10]))  # 只显示前10行
        
        # 执行杀死操作
        print(f"正在杀死通道 {uuid}...")
        output, returncode = self._execute_command(f"uuid_kill {uuid}")
        
        # 检查结果
        if returncode == 0 and "error" not in output.lower() and "no such channel" not in output.lower():
            return True, f"成功: 通道 {uuid} 已被杀死"
        else:
            return False, f"错误: 无法杀死通道 {uuid}\n{output}"
    
    def list_channels(self) -> List[str]:
        """
        列出所有活动通道的 UUID
        
        Returns:
            UUID 列表
        """
        output, returncode = self._execute_command("show channels")
        
        if returncode != 0 or not output:
            return []
        
        # 提取 UUID (格式: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
        uuid_pattern = r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
        uuids = re.findall(uuid_pattern, output, re.IGNORECASE)
        
        return list(set(uuids))  # 去重
    
    def kill_all_channels(self, confirm: bool = False) -> Tuple[int, int]:
        """
        杀死所有活动通道
        
        Args:
            confirm: 是否已确认（如果为 False，会提示用户确认）
            
        Returns:
            (成功数, 失败数) 元组
        """
        if not confirm:
            print("警告: 将杀死所有活动通道！")
            response = input("确认继续? (y/N): ")
            if response.lower() != 'y':
                print("操作已取消")
                return 0, 0
        
        uuids = self.list_channels()
        
        if not uuids:
            print("没有活动通道")
            return 0, 0
        
        print(f"找到 {len(uuids)} 个活动通道")
        
        success = 0
        failed = 0
        
        for i, uuid in enumerate(uuids, 1):
            print(f"\n[{i}/{len(uuids)}] 处理通道: {uuid}")
            ok, msg = self.kill_channel(uuid, verify=True)
            if ok:
                success += 1
                print(f"✓ {msg}")
            else:
                failed += 1
                print(f"✗ {msg}")
        
        return success, failed


def main():
    """主函数"""
    parser = argparse.ArgumentParser(
        description="FreeSWITCH 通道检查和杀死工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  %(prog)s -u abc123-def456-789
  %(prog)s -a
  %(prog)s -l
  %(prog)s -c abc123-def456-789
        """
    )
    
    parser.add_argument(
        "-u", "--uuid",
        help="指定要杀死的通道 UUID"
    )
    
    parser.add_argument(
        "-a", "--all",
        action="store_true",
        help="杀死所有活动通道"
    )
    
    parser.add_argument(
        "-l", "--list",
        action="store_true",
        help="仅列出所有活动通道"
    )
    
    parser.add_argument(
        "-c", "--check",
        help="检查指定 UUID 的通道是否存在"
    )
    
    parser.add_argument(
        "--fs-cli",
        default="fs_cli",
        help="FreeSWITCH CLI 命令路径 (默认: fs_cli)"
    )
    
    parser.add_argument(
        "--no-verify",
        action="store_true",
        help="杀死通道前不验证其存在性"
    )
    
    args = parser.parse_args()
    
    # 创建管理器实例
    manager = FreeSwitchChannelManager(fs_cli=args.fs_cli)
    
    # 执行相应操作
    if args.list:
        uuids = manager.list_channels()
        if uuids:
            print(f"找到 {len(uuids)} 个活动通道:")
            for uuid in uuids:
                print(f"  - {uuid}")
        else:
            print("没有活动通道")
    
    elif args.check:
        if manager.check_channel_exists(args.check):
            print(f"✓ 通道 {args.check} 存在")
            info = manager.get_channel_info(args.check)
            if info:
                print("\n通道信息:")
                print(info)
        else:
            print(f"✗ 通道 {args.check} 不存在或已关闭")
            sys.exit(1)
    
    elif args.all:
        success, failed = manager.kill_all_channels()
        print(f"\n完成: 成功 {success}, 失败 {failed}, 总计 {success + failed}")
    
    elif args.uuid:
        ok, msg = manager.kill_channel(args.uuid, verify=not args.no_verify)
        print(msg)
        sys.exit(0 if ok else 1)
    
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
