# 指标运算系统 (Indicator Calculation System)

一个功能完整的金融技术指标计算和结果保存系统，支持多种技术指标的计算和多种格式的结果输出。

## 功能特性

### 📊 支持的指标
- **移动平均线**: SMA (简单移动平均), EMA (指数移动平均)
- **趋势指标**: MACD (移动平均收敛散度)
- **震荡指标**: RSI (相对强弱指数), 随机指标 (Stochastic), 威廉指标 (Williams %R)
- **波动性指标**: 布林带 (Bollinger Bands), ATR (平均真实波幅)

### 💾 输出格式
- **CSV**: 便于Excel等工具分析
- **JSON**: 结构化数据，便于程序处理
- **SQLite数据库**: 便于复杂查询和分析
- **文本摘要报告**: 指标统计信息概览

### 🔧 主要特性
- 模块化设计，易于扩展
- 配置文件驱动，灵活定制
- 支持多种数据输入格式
- 自动生成时间戳文件名
- 完整的错误处理

## 快速开始

### 1. 安装依赖
```bash
pip install -r requirements.txt
```

### 2. 运行示例
```bash
python example_usage.py
```

### 3. 使用自定义数据
```python
from indicators import IndicatorCalculator, ResultSaver
import pandas as pd

# 加载你的数据 (需要包含 open, high, low, close, volume 列)
data = pd.read_csv('your_data.csv', index_col=0, parse_dates=True)

# 创建计算器
calculator = IndicatorCalculator(data)

# 配置要计算的指标
config = {
    'sma': [5, 10, 20],
    'rsi': {'period': 14},
    'macd': {'fast_period': 12, 'slow_period': 26, 'signal_period': 9}
}

# 计算指标
indicators = calculator.calculate_all_indicators(config)

# 保存结果
saver = ResultSaver('output')
csv_file = saver.save_to_csv(data, indicators)
print(f"结果已保存到: {csv_file}")
```

## 配置说明

### 指标配置 (config.json)
```json
{
  "indicators": {
    "sma": [5, 10, 20, 50],           // 简单移动平均线周期
    "ema": [12, 26, 50],              // 指数移动平均线周期
    "rsi": {"period": 14},            // RSI周期
    "macd": {                         // MACD参数
      "fast_period": 12,
      "slow_period": 26,
      "signal_period": 9
    },
    "bollinger": {                    // 布林带参数
      "period": 20,
      "std_dev": 2
    },
    "stochastic": {                   // 随机指标参数
      "k_period": 14,
      "d_period": 3
    },
    "atr": {"period": 14},            // ATR周期
    "williams_r": {"period": 14}      // 威廉指标周期
  },
  "output": {
    "formats": ["csv", "json", "database"],  // 输出格式
    "directory": "output",                    // 输出目录
    "include_summary": true                  // 是否生成摘要报告
  }
}
```

## 数据格式要求

输入数据必须是包含以下列的pandas DataFrame：
- `open`: 开盘价
- `high`: 最高价
- `low`: 最低价
- `close`: 收盘价
- `volume`: 成交量

索引应为日期时间格式。

## API 参考

### IndicatorCalculator 类

#### 构造函数
```python
IndicatorCalculator(data: pd.DataFrame)
```

#### 主要方法
- `sma(period, column='close')`: 计算简单移动平均
- `ema(period, column='close')`: 计算指数移动平均
- `rsi(period=14)`: 计算RSI指标
- `macd(fast_period=12, slow_period=26, signal_period=9)`: 计算MACD
- `bollinger_bands(period=20, std_dev=2)`: 计算布林带
- `stochastic(k_period=14, d_period=3)`: 计算随机指标
- `atr(period=14)`: 计算ATR
- `williams_r(period=14)`: 计算威廉指标
- `calculate_all_indicators(config)`: 根据配置计算所有指标

### ResultSaver 类

#### 构造函数
```python
ResultSaver(output_dir: str = "output")
```

#### 主要方法
- `save_to_csv(data, indicators, filename=None)`: 保存为CSV格式
- `save_to_json(data, indicators, filename=None)`: 保存为JSON格式
- `save_to_database(data, indicators, db_name="indicators.db")`: 保存到SQLite数据库
- `save_summary_report(indicators, filename=None)`: 生成摘要报告

## 输出文件说明

### CSV 文件
包含原始价格数据和所有计算的指标，每列格式为 `indicator_指标名称`

### JSON 文件
结构化数据，包含：
- `metadata`: 元数据信息
- `data`: 原始价格数据
- `indicators`: 指标数据

### SQLite 数据库
- `price_data`: 原始价格数据表
- `indicator_指标名称`: 各指标数据表

### 摘要报告
包含每个指标的统计信息：均值、标准差、最小值、最大值等

## 扩展开发

### 添加新指标
1. 在 `IndicatorCalculator` 类中添加新方法
2. 在 `calculate_all_indicators` 方法中添加配置处理
3. 更新配置文件格式

### 添加新输出格式
1. 在 `ResultSaver` 类中添加新方法
2. 在配置文件中添加新格式选项

## 示例数据

系统包含示例数据生成功能，可以用于测试和演示：
```python
from indicators import load_sample_data
data = load_sample_data()  # 生成100天的示例OHLCV数据
```

## 注意事项

1. 确保输入数据质量，缺失值会影响指标计算
2. 某些指标需要足够的历史数据才能计算（如20期SMA需要至少20个数据点）
3. 输出文件会覆盖同名文件，建议使用时间戳命名
4. 大量数据计算时注意内存使用

## 许可证

MIT License