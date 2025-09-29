#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
简单的HTTP服务器 - 用于托管Web控制面板
"""

import http.server
import socketserver
import os
import sys
import webbrowser
from pathlib import Path

# 配置
PORT = 8080
DIRECTORY = "web_interface"

class MyHTTPRequestHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)
    
    def end_headers(self):
        # 添加CORS头，允许跨域访问WebSocket
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        super().end_headers()

def main():
    """启动HTTP服务器"""
    # 检查目录是否存在
    if not os.path.exists(DIRECTORY):
        print(f"错误: 目录 '{DIRECTORY}' 不存在")
        sys.exit(1)
    
    # 创建服务器
    with socketserver.TCPServer(("", PORT), MyHTTPRequestHandler) as httpd:
        print(f"╔{'═'*50}╗")
        print(f"║{'FreeSWITCH 坐席控制面板':^50}║")
        print(f"╠{'═'*50}╣")
        print(f"║ HTTP服务器已启动在端口: {PORT:<27}║")
        print(f"║ 访问地址: http://localhost:{PORT:<22}║")
        print(f"║{'':50}║")
        print(f"║ 按 Ctrl+C 停止服务器{'':30}║")
        print(f"╚{'═'*50}╝")
        
        # 尝试自动打开浏览器
        try:
            webbrowser.open(f'http://localhost:{PORT}')
            print("\n✓ 已自动打开浏览器")
        except:
            print("\n提示: 请手动打开浏览器访问上述地址")
        
        print("\n服务器运行中...\n")
        
        # 启动服务器
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\n\n服务器已停止")
            sys.exit(0)

if __name__ == "__main__":
    main()