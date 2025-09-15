#!/usr/bin/env python3
"""
指标运算系统测试脚本
验证所有功能是否正常工作
"""

import sys
import os
import pandas as pd
import numpy as np
from indicators import IndicatorCalculator, ResultSaver, load_sample_data


def test_data_loading():
    """测试数据加载功能"""
    print("测试数据加载...")
    data = load_sample_data()
    assert len(data) == 100, f"期望100条数据，实际{len(data)}条"
    assert all(col in data.columns for col in ['open', 'high', 'low', 'close', 'volume']), "缺少必要的列"
    print("✓ 数据加载测试通过")


def test_indicator_calculation():
    """测试指标计算功能"""
    print("测试指标计算...")
    data = load_sample_data()
    calculator = IndicatorCalculator(data)
    
    # 测试SMA
    sma_5 = calculator.sma(5)
    assert len(sma_5) == 100, "SMA长度不正确"
    assert sma_5.iloc[4] == data['close'].iloc[:5].mean(), "SMA计算错误"
    
    # 测试RSI
    rsi = calculator.rsi(14)
    assert len(rsi) == 100, "RSI长度不正确"
    assert all(0 <= val <= 100 for val in rsi.dropna()), "RSI值超出范围"
    
    # 测试MACD
    macd_result = calculator.macd()
    assert 'macd' in macd_result, "MACD结果缺少macd字段"
    assert 'signal' in macd_result, "MACD结果缺少signal字段"
    assert 'histogram' in macd_result, "MACD结果缺少histogram字段"
    
    print("✓ 指标计算测试通过")


def test_config_calculation():
    """测试配置驱动的指标计算"""
    print("测试配置驱动计算...")
    data = load_sample_data()
    calculator = IndicatorCalculator(data)
    
    config = {
        'sma': [5, 10],
        'ema': [12, 26],
        'rsi': {'period': 14},
        'macd': {'fast_period': 12, 'slow_period': 26, 'signal_period': 9}
    }
    
    indicators = calculator.calculate_all_indicators(config)
    
    expected_indicators = ['sma_5', 'sma_10', 'ema_12', 'ema_26', 'rsi', 'macd', 'signal', 'histogram']
    for expected in expected_indicators:
        assert expected in indicators, f"缺少指标: {expected}"
    
    print("✓ 配置驱动计算测试通过")


def test_result_saving():
    """测试结果保存功能"""
    print("测试结果保存...")
    data = load_sample_data()
    calculator = IndicatorCalculator(data)
    
    config = {
        'sma': [5, 10],
        'rsi': {'period': 14}
    }
    
    indicators = calculator.calculate_all_indicators(config)
    saver = ResultSaver("test_output")
    
    # 测试CSV保存
    csv_file = saver.save_to_csv(data, indicators, "test.csv")
    assert os.path.exists(csv_file), "CSV文件未创建"
    
    # 验证CSV内容
    saved_data = pd.read_csv(csv_file, index_col=0)
    assert len(saved_data) == len(data), "CSV数据长度不匹配"
    
    # 测试JSON保存
    json_file = saver.save_to_json(data, indicators, "test.json")
    assert os.path.exists(json_file), "JSON文件未创建"
    
    # 测试数据库保存
    db_file = saver.save_to_database(data, indicators, "test.db")
    assert os.path.exists(db_file), "数据库文件未创建"
    
    # 测试摘要报告
    summary_file = saver.save_summary_report(indicators, "test_summary.txt")
    assert os.path.exists(summary_file), "摘要报告文件未创建"
    
    print("✓ 结果保存测试通过")


def test_error_handling():
    """测试错误处理"""
    print("测试错误处理...")
    
    # 测试无效数据
    invalid_data = pd.DataFrame({'invalid': [1, 2, 3]})
    try:
        calculator = IndicatorCalculator(invalid_data)
        assert False, "应该抛出异常"
    except ValueError:
        pass  # 期望的异常
    
    # 测试空数据
    empty_data = pd.DataFrame({
        'open': [], 'high': [], 'low': [], 'close': [], 'volume': []
    })
    try:
        calculator = IndicatorCalculator(empty_data)
        # 空数据应该可以创建计算器，但计算时会返回空结果
        sma = calculator.sma(5)
        assert len(sma) == 0, "空数据的SMA应该为空"
    except Exception as e:
        print(f"空数据处理异常: {e}")
    
    print("✓ 错误处理测试通过")


def cleanup_test_files():
    """清理测试文件"""
    print("清理测试文件...")
    test_files = [
        "test_output/test.csv",
        "test_output/test.json", 
        "test_output/test.db",
        "test_output/test_summary.txt"
    ]
    
    for file_path in test_files:
        if os.path.exists(file_path):
            os.remove(file_path)
    
    # 删除测试输出目录（如果为空）
    if os.path.exists("test_output") and not os.listdir("test_output"):
        os.rmdir("test_output")
    
    print("✓ 测试文件清理完成")


def main():
    """运行所有测试"""
    print("=" * 50)
    print("指标运算系统 - 功能测试")
    print("=" * 50)
    
    try:
        test_data_loading()
        test_indicator_calculation()
        test_config_calculation()
        test_result_saving()
        test_error_handling()
        
        print("\n" + "=" * 50)
        print("🎉 所有测试通过！系统功能正常")
        print("=" * 50)
        
    except Exception as e:
        print(f"\n❌ 测试失败: {e}")
        sys.exit(1)
    
    finally:
        cleanup_test_files()


if __name__ == "__main__":
    main()