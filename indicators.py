"""
指标运算模块 - Indicator Calculation Module
支持多种技术指标的计算和结果保存
"""

import pandas as pd
import numpy as np
from typing import Dict, List, Optional, Union
import json
import csv
from datetime import datetime
import sqlite3
import os


class IndicatorCalculator:
    """指标计算器基类"""
    
    def __init__(self, data: pd.DataFrame):
        """
        初始化指标计算器
        
        Args:
            data: 包含OHLCV数据的DataFrame，列名应为 ['open', 'high', 'low', 'close', 'volume']
        """
        self.data = data.copy()
        self.results = {}
        
        # 验证数据格式
        required_columns = ['open', 'high', 'low', 'close', 'volume']
        if not all(col in self.data.columns for col in required_columns):
            raise ValueError(f"数据必须包含以下列: {required_columns}")
    
    def sma(self, period: int, column: str = 'close') -> pd.Series:
        """简单移动平均线 (Simple Moving Average)"""
        return self.data[column].rolling(window=period).mean()
    
    def ema(self, period: int, column: str = 'close') -> pd.Series:
        """指数移动平均线 (Exponential Moving Average)"""
        return self.data[column].ewm(span=period).mean()
    
    def rsi(self, period: int = 14) -> pd.Series:
        """相对强弱指数 (Relative Strength Index)"""
        delta = self.data['close'].diff()
        gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
        loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()
        rs = gain / loss
        rsi = 100 - (100 / (1 + rs))
        return rsi
    
    def macd(self, fast_period: int = 12, slow_period: int = 26, signal_period: int = 9) -> Dict[str, pd.Series]:
        """MACD指标 (Moving Average Convergence Divergence)"""
        ema_fast = self.ema(fast_period)
        ema_slow = self.ema(slow_period)
        macd_line = ema_fast - ema_slow
        signal_line = macd_line.ewm(span=signal_period).mean()
        histogram = macd_line - signal_line
        
        return {
            'macd': macd_line,
            'signal': signal_line,
            'histogram': histogram
        }
    
    def bollinger_bands(self, period: int = 20, std_dev: float = 2) -> Dict[str, pd.Series]:
        """布林带 (Bollinger Bands)"""
        sma = self.sma(period)
        std = self.data['close'].rolling(window=period).std()
        
        return {
            'upper': sma + (std * std_dev),
            'middle': sma,
            'lower': sma - (std * std_dev)
        }
    
    def stochastic(self, k_period: int = 14, d_period: int = 3) -> Dict[str, pd.Series]:
        """随机指标 (Stochastic Oscillator)"""
        lowest_low = self.data['low'].rolling(window=k_period).min()
        highest_high = self.data['high'].rolling(window=k_period).max()
        
        k_percent = 100 * ((self.data['close'] - lowest_low) / (highest_high - lowest_low))
        d_percent = k_percent.rolling(window=d_period).mean()
        
        return {
            'k_percent': k_percent,
            'd_percent': d_percent
        }
    
    def atr(self, period: int = 14) -> pd.Series:
        """平均真实波幅 (Average True Range)"""
        high_low = self.data['high'] - self.data['low']
        high_close = np.abs(self.data['high'] - self.data['close'].shift())
        low_close = np.abs(self.data['low'] - self.data['close'].shift())
        
        true_range = np.maximum(high_low, np.maximum(high_close, low_close))
        atr = true_range.rolling(window=period).mean()
        
        return atr
    
    def williams_r(self, period: int = 14) -> pd.Series:
        """威廉指标 (Williams %R)"""
        highest_high = self.data['high'].rolling(window=period).max()
        lowest_low = self.data['low'].rolling(window=period).min()
        
        williams_r = -100 * ((highest_high - self.data['close']) / (highest_high - lowest_low))
        return williams_r
    
    def calculate_all_indicators(self, config: Dict) -> Dict:
        """根据配置计算所有指标"""
        indicators = {}
        
        # 移动平均线
        if 'sma' in config:
            for period in config['sma']:
                indicators[f'sma_{period}'] = self.sma(period)
        
        if 'ema' in config:
            for period in config['ema']:
                indicators[f'ema_{period}'] = self.ema(period)
        
        # RSI
        if 'rsi' in config:
            indicators['rsi'] = self.rsi(config['rsi'].get('period', 14))
        
        # MACD
        if 'macd' in config:
            macd_config = config['macd']
            macd_result = self.macd(
                macd_config.get('fast_period', 12),
                macd_config.get('slow_period', 26),
                macd_config.get('signal_period', 9)
            )
            indicators.update(macd_result)
        
        # 布林带
        if 'bollinger' in config:
            bb_config = config['bollinger']
            bb_result = self.bollinger_bands(
                bb_config.get('period', 20),
                bb_config.get('std_dev', 2)
            )
            indicators.update({f'bb_{k}': v for k, v in bb_result.items()})
        
        # 随机指标
        if 'stochastic' in config:
            stoch_config = config['stochastic']
            stoch_result = self.stochastic(
                stoch_config.get('k_period', 14),
                stoch_config.get('d_period', 3)
            )
            indicators.update(stoch_result)
        
        # ATR
        if 'atr' in config:
            indicators['atr'] = self.atr(config['atr'].get('period', 14))
        
        # 威廉指标
        if 'williams_r' in config:
            indicators['williams_r'] = self.williams_r(config['williams_r'].get('period', 14))
        
        self.results = indicators
        return indicators


class ResultSaver:
    """结果保存器"""
    
    def __init__(self, output_dir: str = "output"):
        self.output_dir = output_dir
        os.makedirs(output_dir, exist_ok=True)
    
    def save_to_csv(self, data: pd.DataFrame, indicators: Dict, filename: str = None) -> str:
        """保存结果到CSV文件"""
        if filename is None:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            filename = f"indicators_{timestamp}.csv"
        
        filepath = os.path.join(self.output_dir, filename)
        
        # 合并原始数据和指标
        result_df = data.copy()
        for name, indicator in indicators.items():
            result_df[f'indicator_{name}'] = indicator
        
        result_df.to_csv(filepath, index=True, encoding='utf-8')
        return filepath
    
    def save_to_json(self, data: pd.DataFrame, indicators: Dict, filename: str = None) -> str:
        """保存结果到JSON文件"""
        if filename is None:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            filename = f"indicators_{timestamp}.json"
        
        filepath = os.path.join(self.output_dir, filename)
        
        # 准备JSON数据
        json_data = {
            'metadata': {
                'timestamp': datetime.now().isoformat(),
                'data_points': len(data),
                'indicators': list(indicators.keys())
            },
            'data': data.to_dict('records'),
            'indicators': {name: indicator.dropna().to_dict() for name, indicator in indicators.items()}
        }
        
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(json_data, f, ensure_ascii=False, indent=2, default=str)
        
        return filepath
    
    def save_to_database(self, data: pd.DataFrame, indicators: Dict, db_name: str = "indicators.db") -> str:
        """保存结果到SQLite数据库"""
        db_path = os.path.join(self.output_dir, db_name)
        
        with sqlite3.connect(db_path) as conn:
            # 保存原始数据
            data.to_sql('price_data', conn, if_exists='replace', index=True)
            
            # 保存指标数据
            for name, indicator in indicators.items():
                indicator_df = pd.DataFrame({
                    'date': data.index,
                    'value': indicator
                }).dropna()
                indicator_df.to_sql(f'indicator_{name}', conn, if_exists='replace', index=False)
        
        return db_path
    
    def save_summary_report(self, indicators: Dict, filename: str = None) -> str:
        """保存指标摘要报告"""
        if filename is None:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            filename = f"indicator_summary_{timestamp}.txt"
        
        filepath = os.path.join(self.output_dir, filename)
        
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write("指标计算摘要报告\n")
            f.write("=" * 50 + "\n\n")
            f.write(f"生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write(f"指标数量: {len(indicators)}\n\n")
            
            for name, indicator in indicators.items():
                f.write(f"指标名称: {name}\n")
                f.write(f"数据点数: {len(indicator.dropna())}\n")
                f.write(f"平均值: {indicator.mean():.4f}\n")
                f.write(f"标准差: {indicator.std():.4f}\n")
                f.write(f"最小值: {indicator.min():.4f}\n")
                f.write(f"最大值: {indicator.max():.4f}\n")
                f.write("-" * 30 + "\n")
        
        return filepath


def load_sample_data() -> pd.DataFrame:
    """加载示例数据"""
    # 生成示例OHLCV数据
    np.random.seed(42)
    dates = pd.date_range('2023-01-01', periods=100, freq='D')
    
    # 模拟价格数据
    base_price = 100
    returns = np.random.normal(0, 0.02, 100)
    prices = [base_price]
    
    for ret in returns[1:]:
        prices.append(prices[-1] * (1 + ret))
    
    data = pd.DataFrame({
        'open': prices,
        'high': [p * (1 + abs(np.random.normal(0, 0.01))) for p in prices],
        'low': [p * (1 - abs(np.random.normal(0, 0.01))) for p in prices],
        'close': prices,
        'volume': np.random.randint(1000, 10000, 100)
    }, index=dates)
    
    return data


if __name__ == "__main__":
    # 示例用法
    print("指标运算系统启动...")
    
    # 加载数据
    data = load_sample_data()
    print(f"加载数据: {len(data)} 条记录")
    
    # 创建计算器
    calculator = IndicatorCalculator(data)
    
    # 配置指标
    config = {
        'sma': [5, 10, 20],
        'ema': [12, 26],
        'rsi': {'period': 14},
        'macd': {'fast_period': 12, 'slow_period': 26, 'signal_period': 9},
        'bollinger': {'period': 20, 'std_dev': 2},
        'stochastic': {'k_period': 14, 'd_period': 3},
        'atr': {'period': 14},
        'williams_r': {'period': 14}
    }
    
    # 计算指标
    indicators = calculator.calculate_all_indicators(config)
    print(f"计算完成: {len(indicators)} 个指标")
    
    # 保存结果
    saver = ResultSaver()
    
    # 保存到不同格式
    csv_file = saver.save_to_csv(data, indicators)
    json_file = saver.save_to_json(data, indicators)
    db_file = saver.save_to_database(data, indicators)
    summary_file = saver.save_summary_report(indicators)
    
    print(f"结果已保存:")
    print(f"  CSV: {csv_file}")
    print(f"  JSON: {json_file}")
    print(f"  数据库: {db_file}")
    print(f"  摘要报告: {summary_file}")