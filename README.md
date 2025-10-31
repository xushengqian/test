# MySQL 条件字段值获取示例

本项目展示了在MySQL中如何实现条件字段值获取的功能。

## 需求
如果字段A为空，则取字段B的值；如果字段A不为空，则取字段A的值。

## 实现方法

### 方法1: COALESCE() 函数（推荐）
```sql
SELECT COALESCE(field_a, field_b) AS result_field
FROM your_table;
```
- **优点**: 简洁、高效、可处理多个字段
- **说明**: 返回参数列表中第一个非NULL的值

### 方法2: IFNULL() 函数
```sql
SELECT IFNULL(field_a, field_b) AS result_field
FROM your_table;
```
- **优点**: 语法简单直观
- **说明**: 仅支持两个参数，如果第一个为NULL则返回第二个

### 方法3: CASE WHEN 语句
```sql
SELECT 
    CASE 
        WHEN field_a IS NULL OR field_a = '' THEN field_b
        ELSE field_a
    END AS result_field
FROM your_table;
```
- **优点**: 最灵活，可以处理复杂条件和空字符串
- **说明**: 可以同时检查NULL和空字符串

## 使用场景
- 联系方式选择（手机号优先，否则显示邮箱）
- 备用字段值获取
- 多级字段值回退

详细示例请查看 `mysql_conditional_field_example.sql` 文件。