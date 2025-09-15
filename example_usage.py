#!/usr/bin/env python3
"""
指标运算示例用法
演示如何使用指标计算系统
"""

import pandas as pd
import json
from indicators import IndicatorCalculator, ResultSaver, load_sample_data


def load_config(config_file: str = "config.json") -> dict:
    """加载配置文件"""
    with open(config_file, 'r', encoding='utf-8') as f:
        return json.load(f)


def load_data_from_csv(file_path: str) -> pd.DataFrame:
    """从CSV文件加载数据"""
    data = pd.read_csv(file_path, index_col=0, parse_dates=True)
    return data


def main():
    """主函数 - 演示指标计算和保存"""
    print("=" * 60)
    print("指标运算系统 - 示例用法")
    print("=" * 60)
    
    # 1. 加载配置
    try:
        config = load_config()
        print("✓ 配置文件加载成功")
    except FileNotFoundError:
        print("⚠ 配置文件未找到，使用默认配置")
        config = {
            "indicators": {
                "sma": [5, 10, 20],
                "ema": [12, 26],
                "rsi": {"period": 14}
            },
            "output": {
                "formats": ["csv", "json"],
                "directory": "output"
            }
        }
    
    # 2. 加载数据
    print("\n📊 数据加载...")
    
    # 选项1: 使用示例数据
    data = load_sample_data()
    print(f"✓ 使用示例数据: {len(data)} 条记录")
    
    # 选项2: 从CSV文件加载 (如果存在)
    # try:
    #     data = load_data_from_csv("your_data.csv")
    #     print(f"✓ 从CSV文件加载: {len(data)} 条记录")
    # except FileNotFoundError:
    #     print("⚠ CSV文件未找到，使用示例数据")
    #     data = load_sample_data()
    
    # 显示数据预览
    print("\n数据预览:")
    print(data.head())
    
    # 3. 创建指标计算器
    print("\n🔧 创建指标计算器...")
    calculator = IndicatorCalculator(data)
    print("✓ 指标计算器创建成功")
    
    # 4. 计算指标
    print("\n📈 计算指标...")
    indicators = calculator.calculate_all_indicators(config["indicators"])
    print(f"✓ 计算完成: {len(indicators)} 个指标")
    
    # 显示指标列表
    print("\n计算的指标:")
    for i, name in enumerate(indicators.keys(), 1):
        print(f"  {i:2d}. {name}")
    
    # 5. 保存结果
    print("\n💾 保存结果...")
    saver = ResultSaver(config["output"]["directory"])
    
    saved_files = []
    
    # 根据配置保存不同格式
    if "csv" in config["output"]["formats"]:
        csv_file = saver.save_to_csv(data, indicators)
        saved_files.append(("CSV", csv_file))
        print(f"✓ CSV文件已保存: {csv_file}")
    
    if "json" in config["output"]["formats"]:
        json_file = saver.save_to_json(data, indicators)
        saved_files.append(("JSON", json_file))
        print(f"✓ JSON文件已保存: {json_file}")
    
    if "database" in config["output"]["formats"]:
        db_file = saver.save_to_database(data, indicators)
        saved_files.append(("数据库", db_file))
        print(f"✓ 数据库文件已保存: {db_file}")
    
    if config["output"].get("include_summary", False):
        summary_file = saver.save_summary_report(indicators)
        saved_files.append(("摘要报告", summary_file))
        print(f"✓ 摘要报告已保存: {summary_file}")
    
    # 6. 显示保存的文件
    print("\n📁 保存的文件:")
    for format_name, file_path in saved_files:
        print(f"  {format_name}: {file_path}")
    
    # 7. 显示一些指标统计信息
    print("\n📊 指标统计信息:")
    print("-" * 40)
    for name, indicator in list(indicators.items())[:5]:  # 只显示前5个指标
        valid_data = indicator.dropna()
        if len(valid_data) > 0:
            print(f"{name:15s}: 均值={valid_data.mean():8.4f}, "
                  f"标准差={valid_data.std():8.4f}, "
                  f"数据点={len(valid_data):4d}")
    
    print("\n" + "=" * 60)
    print("指标运算完成！")
    print("=" * 60)


if __name__ == "__main__":
    main()