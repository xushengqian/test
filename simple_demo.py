#!/usr/bin/env python3
"""
指标运算系统 - 简化演示版本
不依赖外部库，展示核心功能
"""

import json
import csv
import os
from datetime import datetime, timedelta
import math


class SimpleIndicatorCalculator:
    """简化版指标计算器"""
    
    def __init__(self, data):
        self.data = data
        self.results = {}
    
    def sma(self, period, prices):
        """简单移动平均"""
        result = []
        for i in range(len(prices)):
            if i < period - 1:
                result.append(None)
            else:
                avg = sum(prices[i-period+1:i+1]) / period
                result.append(round(avg, 4))
        return result
    
    def ema(self, period, prices):
        """指数移动平均"""
        if not prices:
            return []
        
        multiplier = 2 / (period + 1)
        result = [prices[0]]  # 第一个值
        
        for i in range(1, len(prices)):
            ema_value = (prices[i] * multiplier) + (result[i-1] * (1 - multiplier))
            result.append(round(ema_value, 4))
        
        return result
    
    def rsi(self, period, prices):
        """相对强弱指数"""
        if len(prices) < period + 1:
            return [None] * len(prices)
        
        gains = []
        losses = []
        
        for i in range(1, len(prices)):
            change = prices[i] - prices[i-1]
            if change > 0:
                gains.append(change)
                losses.append(0)
            else:
                gains.append(0)
                losses.append(-change)
        
        result = [None] * len(prices)
        
        for i in range(period, len(prices)):
            avg_gain = sum(gains[i-period:i]) / period
            avg_loss = sum(losses[i-period:i]) / period
            
            if avg_loss == 0:
                rsi = 100
            else:
                rs = avg_gain / avg_loss
                rsi = 100 - (100 / (1 + rs))
            
            result[i] = round(rsi, 4)
        
        return result
    
    def calculate_all(self, config):
        """计算所有指标"""
        prices = [row['close'] for row in self.data]
        
        results = {}
        
        # SMA
        if 'sma' in config:
            for period in config['sma']:
                results[f'sma_{period}'] = self.sma(period, prices)
        
        # EMA
        if 'ema' in config:
            for period in config['ema']:
                results[f'ema_{period}'] = self.ema(period, prices)
        
        # RSI
        if 'rsi' in config:
            period = config['rsi'].get('period', 14)
            results['rsi'] = self.rsi(period, prices)
        
        self.results = results
        return results


class SimpleResultSaver:
    """简化版结果保存器"""
    
    def __init__(self, output_dir="output"):
        self.output_dir = output_dir
        os.makedirs(output_dir, exist_ok=True)
    
    def save_to_csv(self, data, indicators, filename=None):
        """保存到CSV"""
        if filename is None:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            filename = f"indicators_{timestamp}.csv"
        
        filepath = os.path.join(self.output_dir, filename)
        
        with open(filepath, 'w', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            
            # 写入标题行
            headers = ['date', 'open', 'high', 'low', 'close', 'volume']
            for indicator_name in indicators.keys():
                headers.append(f'indicator_{indicator_name}')
            writer.writerow(headers)
            
            # 写入数据行
            for i, row in enumerate(data):
                data_row = [
                    row['date'],
                    row['open'],
                    row['high'],
                    row['low'],
                    row['close'],
                    row['volume']
                ]
                
                for indicator_name, indicator_values in indicators.items():
                    value = indicator_values[i] if i < len(indicator_values) else None
                    data_row.append(value)
                
                writer.writerow(data_row)
        
        return filepath
    
    def save_to_json(self, data, indicators, filename=None):
        """保存到JSON"""
        if filename is None:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            filename = f"indicators_{timestamp}.json"
        
        filepath = os.path.join(self.output_dir, filename)
        
        json_data = {
            'metadata': {
                'timestamp': datetime.now().isoformat(),
                'data_points': len(data),
                'indicators': list(indicators.keys())
            },
            'data': data,
            'indicators': indicators
        }
        
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(json_data, f, ensure_ascii=False, indent=2, default=str)
        
        return filepath
    
    def save_summary_report(self, indicators, filename=None):
        """保存摘要报告"""
        if filename is None:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            filename = f"indicator_summary_{timestamp}.txt"
        
        filepath = os.path.join(self.output_dir, filename)
        
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write("指标计算摘要报告\n")
            f.write("=" * 50 + "\n\n")
            f.write(f"生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write(f"指标数量: {len(indicators)}\n\n")
            
            for name, values in indicators.items():
                valid_values = [v for v in values if v is not None]
                if valid_values:
                    f.write(f"指标名称: {name}\n")
                    f.write(f"数据点数: {len(valid_values)}\n")
                    f.write(f"平均值: {sum(valid_values)/len(valid_values):.4f}\n")
                    f.write(f"最小值: {min(valid_values):.4f}\n")
                    f.write(f"最大值: {max(valid_values):.4f}\n")
                    f.write("-" * 30 + "\n")
        
        return filepath


def generate_sample_data(days=30):
    """生成示例数据"""
    data = []
    base_price = 100.0
    current_date = datetime.now() - timedelta(days=days)
    
    for i in range(days):
        # 模拟价格波动
        change = (i % 7 - 3) * 0.02 + (i % 3 - 1) * 0.01
        base_price *= (1 + change)
        
        # 生成OHLCV数据
        open_price = base_price
        high_price = open_price * (1 + abs(change) * 0.5)
        low_price = open_price * (1 - abs(change) * 0.3)
        close_price = open_price * (1 + change * 0.8)
        volume = 1000 + (i % 10) * 100
        
        data.append({
            'date': current_date.strftime('%Y-%m-%d'),
            'open': round(open_price, 2),
            'high': round(high_price, 2),
            'low': round(low_price, 2),
            'close': round(close_price, 2),
            'volume': volume
        })
        
        current_date += timedelta(days=1)
    
    return data


def main():
    """主函数"""
    print("=" * 60)
    print("指标运算系统 - 简化演示版本")
    print("=" * 60)
    
    # 1. 生成示例数据
    print("📊 生成示例数据...")
    data = generate_sample_data(30)
    print(f"✓ 生成了 {len(data)} 天的示例数据")
    
    # 显示数据预览
    print("\n数据预览:")
    print("日期        开盘    最高    最低    收盘    成交量")
    print("-" * 45)
    for row in data[:5]:
        print(f"{row['date']}  {row['open']:6.2f}  {row['high']:6.2f}  {row['low']:6.2f}  {row['close']:6.2f}  {row['volume']:6d}")
    print("...")
    
    # 2. 创建计算器
    print("\n🔧 创建指标计算器...")
    calculator = SimpleIndicatorCalculator(data)
    print("✓ 指标计算器创建成功")
    
    # 3. 配置指标
    config = {
        'sma': [5, 10, 20],
        'ema': [12, 26],
        'rsi': {'period': 14}
    }
    
    # 4. 计算指标
    print("\n📈 计算指标...")
    indicators = calculator.calculate_all(config)
    print(f"✓ 计算完成: {len(indicators)} 个指标")
    
    # 显示指标列表
    print("\n计算的指标:")
    for i, name in enumerate(indicators.keys(), 1):
        print(f"  {i:2d}. {name}")
    
    # 5. 保存结果
    print("\n💾 保存结果...")
    saver = SimpleResultSaver()
    
    # 保存到不同格式
    csv_file = saver.save_to_csv(data, indicators)
    json_file = saver.save_to_json(data, indicators)
    summary_file = saver.save_summary_report(indicators)
    
    print(f"✓ CSV文件已保存: {csv_file}")
    print(f"✓ JSON文件已保存: {json_file}")
    print(f"✓ 摘要报告已保存: {summary_file}")
    
    # 6. 显示一些指标统计信息
    print("\n📊 指标统计信息:")
    print("-" * 40)
    for name, values in indicators.items():
        valid_values = [v for v in values if v is not None]
        if valid_values:
            avg = sum(valid_values) / len(valid_values)
            min_val = min(valid_values)
            max_val = max(valid_values)
            print(f"{name:15s}: 均值={avg:8.4f}, 最小值={min_val:8.4f}, 最大值={max_val:8.4f}")
    
    print("\n" + "=" * 60)
    print("🎉 指标运算完成！")
    print("=" * 60)
    print(f"输出目录: {os.path.abspath('output')}")
    print("生成的文件:")
    for file in os.listdir('output'):
        print(f"  - {file}")


if __name__ == "__main__":
    main()