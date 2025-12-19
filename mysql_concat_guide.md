# MySQL CONCAT 函数使用指南

## 概述

`CONCAT` 是 MySQL 中用于连接字符串的函数，可以将两个或多个字符串连接在一起。

## 基本语法

```sql
CONCAT(str1, str2, str3, ...)
```

## 主要特性

1. **多参数支持**: 可以接受多个字符串参数
2. **NULL 处理**: 如果参数中包含 NULL，CONCAT 会返回 NULL
3. **类型转换**: 会自动将数字转换为字符串

## 常用示例

### 1. 基本字符串连接

```sql
SELECT CONCAT('Hello', ' ', 'World');
-- 结果: 'Hello World'
```

### 2. 连接表字段

```sql
SELECT CONCAT(first_name, ' ', last_name) AS full_name 
FROM users;
```

### 3. 处理 NULL 值

```sql
-- 使用 IFNULL 处理 NULL
SELECT CONCAT('Hello', IFNULL(middle_name, ''), 'World') AS result;

-- 使用 COALESCE 处理 NULL
SELECT CONCAT('Hello', COALESCE(middle_name, ''), 'World') AS result;
```

## CONCAT_WS 函数

`CONCAT_WS` (CONCAT With Separator) 是 CONCAT 的增强版本，使用指定的分隔符连接字符串。

### 语法

```sql
CONCAT_WS(separator, str1, str2, ...)
```

### 优势

- 自动忽略 NULL 值
- 分隔符只出现在非 NULL 值之间
- 更适合处理可能为空的字段

### 示例

```sql
SELECT CONCAT_WS(' - ', '2024', '01', '15');
-- 结果: '2024 - 01 - 15'

SELECT CONCAT_WS(', ', first_name, last_name, email) 
FROM users;
-- 自动忽略 NULL 值
```

## 实际应用场景

### 1. 生成完整地址

```sql
SELECT CONCAT_WS(', ', 
    address_line1, 
    address_line2, 
    city, 
    state, 
    postal_code
) AS full_address 
FROM addresses;
```

### 2. 格式化显示

```sql
SELECT CONCAT('用户ID: ', user_id, ' - ', username) AS user_info 
FROM users;
```

### 3. 生成文件路径

```sql
SELECT CONCAT('/var/www/', folder_name, '/', file_name) AS file_path 
FROM files;
```

## 与其他函数结合

```sql
-- 与 UPPER 结合
SELECT CONCAT(UPPER(first_name), ' ', UPPER(last_name)) AS full_name;

-- 与 LEFT 结合
SELECT CONCAT(LEFT(first_name, 1), '. ', last_name) AS abbreviated_name;

-- 与 DATE_FORMAT 结合
SELECT CONCAT('订单日期: ', DATE_FORMAT(order_date, '%Y-%m-%d')) AS order_info;
```

## 注意事项

1. **NULL 值处理**: CONCAT 遇到 NULL 会返回 NULL，建议使用 IFNULL 或 COALESCE
2. **性能**: 对于大量数据，考虑在应用层进行字符串连接
3. **字符集**: 确保连接的字符串使用相同的字符集
4. **长度限制**: 注意结果字符串的长度限制

## 替代方案

在 MySQL 8.0+ 中，可以设置 `sql_mode` 使用 `||` 运算符：

```sql
SET sql_mode = 'PIPES_AS_CONCAT';
SELECT 'Hello' || ' ' || 'World';
```

## 相关函数

- `CONCAT_WS()`: 带分隔符的连接
- `GROUP_CONCAT()`: 分组连接
- `REPLACE()`: 字符串替换
- `SUBSTRING()`: 字符串截取
