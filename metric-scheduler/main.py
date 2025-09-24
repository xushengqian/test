#!/usr/bin/env python
"""
指标调度系统主程序
"""
import sys
import os
from loguru import logger

# 添加src目录到Python路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'src'))

from cli import cli

if __name__ == '__main__':
    cli()