#!/usr/bin/env python3
"""
示例指标脚本

这是一个示例Python脚本，展示如何编写自定义指标脚本。

环境变量：
- JOB_INSTANCE_ID: 作业实例ID
- JOB_CODE: 作业编码
- METRIC_CODE: 指标编码
- METRIC_NAME: 指标名称
- PARAM_*: 自定义参数，例如 PARAM_DATE

输出格式：
- 必须输出JSON格式的结果
- 可以是单个对象或对象数组
- 建议包含 value 字段作为指标值
- 可以包含多个维度字段
"""

import os
import sys
import json
from datetime import datetime
import random


def main():
    # 获取环境变量
    job_instance_id = os.environ.get('JOB_INSTANCE_ID')
    job_code = os.environ.get('JOB_CODE')
    metric_code = os.environ.get('METRIC_CODE')
    metric_name = os.environ.get('METRIC_NAME')
    
    # 获取自定义参数
    date_param = os.environ.get('PARAM_DATE', datetime.now().strftime('%Y-%m-%d'))
    
    print(f"执行指标: {metric_name} ({metric_code})", file=sys.stderr)
    print(f"作业编码: {job_code}", file=sys.stderr)
    print(f"日期参数: {date_param}", file=sys.stderr)
    
    # 模拟指标计算
    # 在实际场景中，这里可以是：
    # - 调用外部API
    # - 读取文件
    # - 复杂的数据处理
    # - 机器学习模型推理
    # 等等
    
    results = []
    
    # 示例1：单一指标值
    results.append({
        'value': random.randint(1000, 10000),
        'calc_date': date_param,
        'dimension': 'total'
    })
    
    # 示例2：多维度指标
    for category in ['A', 'B', 'C']:
        results.append({
            'value': random.randint(100, 1000),
            'calc_date': date_param,
            'category': category,
            'dimension': f'category_{category}'
        })
    
    # 示例3：复杂的指标对象
    results.append({
        'value': random.uniform(0.5, 0.9),
        'calc_date': date_param,
        'metric_type': 'ratio',
        'numerator': random.randint(800, 900),
        'denominator': 1000,
        'dimension': 'conversion_rate'
    })
    
    # 输出JSON结果到stdout
    # 注意：只有stdout的内容会被解析为结果
    # stderr的内容会被记录到日志
    print(json.dumps(results, ensure_ascii=False, indent=2))
    
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as e:
        print(f"错误: {e}", file=sys.stderr)
        sys.exit(1)
