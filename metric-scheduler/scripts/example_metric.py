#!/usr/bin/env python
"""
示例指标脚本：计算用户活跃度指标
"""
import os
import sys
import json
from datetime import datetime, timedelta
import random

# 模拟计算用户活跃度指标
def calculate_user_activity():
    """计算用户活跃度指标"""
    # 从环境变量获取参数
    job_instance_id = os.environ.get('JOB_INSTANCE_ID')
    metric_code = os.environ.get('METRIC_CODE')
    date_param = os.environ.get('PARAM_DATE', datetime.now().strftime('%Y-%m-%d'))
    
    # 模拟计算结果
    results = []
    
    # 按小时统计
    for hour in range(24):
        calc_time = f"{date_param} {hour:02d}:00:00"
        
        # 模拟不同维度的数据
        platforms = ['iOS', 'Android', 'Web']
        for platform in platforms:
            # 生成随机数据
            active_users = random.randint(1000, 10000)
            new_users = random.randint(100, 1000)
            
            results.append({
                'calc_time': calc_time,
                'platform': platform,
                'hour': hour,
                'active_users': active_users,
                'new_users': new_users,
                'value': active_users  # 主要指标值
            })
    
    # 输出JSON格式结果
    print(json.dumps(results))
    return 0


if __name__ == '__main__':
    sys.exit(calculate_user_activity())