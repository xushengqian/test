# SQL 查询字段替换为 count(*) 使用指南

## 功能说明

使用 JSQLParser 库将任意 SQL 查询的 SELECT 字段替换为 `count(*)`，保留原有的 WHERE、JOIN 等子句。

## 核心代码

### 方法1：基础实现

```java
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;

// 解析 SQL 查询
Select select = (Select) CCJSqlParserUtil.parse(sql);
PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

// 创建 count(*) 函数
Function countFunction = new Function();
countFunction.setName("count");
countFunction.setAllColumns(true);  // 设置为 count(*)

// 创建 SelectExpressionItem 并设置为 count(*)
SelectExpressionItem countItem = new SelectExpressionItem();
countItem.setExpression(countFunction);

// 替换原查询字段为 count(*)
plainSelect.setSelectItems(Collections.singletonList(countItem));

// 获取修改后的 SQL
String modifiedSql = select.toString();
```

### 方法2：使用 AllColumns（更简洁）

```java
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.*;

// 解析 SQL 查询
Select select = (Select) CCJSqlParserUtil.parse(sql);
PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

// 创建 count(*) 表达式
Function countFunction = new Function();
countFunction.setName("COUNT");
countFunction.setParameters(new ExpressionList(new AllColumns()));

// 或者更简单的方式
Function countFunction = new Function();
countFunction.setName("COUNT");
countFunction.setAllColumns(true);

// 替换查询字段
SelectExpressionItem selectItem = new SelectExpressionItem(countFunction);
plainSelect.setSelectItems(Collections.singletonList(selectItem));

// 获取修改后的 SQL
String modifiedSql = select.toString();
```

## 示例

### 示例1：简单查询

**原始 SQL:**
```sql
SELECT id, name, age FROM users WHERE age > 18
```

**修改后 SQL:**
```sql
SELECT COUNT(*) FROM users WHERE age > 18
```

### 示例2：带 JOIN 的查询

**原始 SQL:**
```sql
SELECT u.id, u.name, o.order_id 
FROM users u 
JOIN orders o ON u.id = o.user_id 
WHERE u.status = 'active'
```

**修改后 SQL:**
```sql
SELECT COUNT(*) 
FROM users u 
JOIN orders o ON u.id = o.user_id 
WHERE u.status = 'active'
```

### 示例3：带 GROUP BY 的查询

**原始 SQL:**
```sql
SELECT department, COUNT(*) as cnt 
FROM employees 
GROUP BY department
```

**修改后 SQL:**
```sql
SELECT COUNT(*) 
FROM employees 
GROUP BY department
```

> **注意:** 如果原始 SQL 包含 `GROUP BY` 子句，修改后的查询仍会保留 `GROUP BY`，但通常在统计总数时你可能需要移除它。

## 完整处理（可选处理 GROUP BY 和 ORDER BY）

如果需要移除 `GROUP BY`、`HAVING` 和 `ORDER BY` 子句（通常在统计总数时这些子句不需要）：

```java
// 解析 SQL 查询
Select select = (Select) CCJSqlParserUtil.parse(sql);
PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

// 创建 count(*) 函数
Function countFunction = new Function();
countFunction.setName("count");
countFunction.setAllColumns(true);

// 替换查询字段为 count(*)
SelectExpressionItem countItem = new SelectExpressionItem(countFunction);
plainSelect.setSelectItems(Collections.singletonList(countItem));

// 移除 GROUP BY、HAVING 和 ORDER BY（可选）
plainSelect.setGroupByColumnReferences(null);
plainSelect.setHaving(null);
plainSelect.setOrderByElements(null);

// 获取修改后的 SQL
String modifiedSql = select.toString();
```

## Maven 依赖

```xml
<dependency>
    <groupId>com.github.jsqlparser</groupId>
    <artifactId>jsqlparser</artifactId>
    <version>4.7</version>
</dependency>
```

## Gradle 依赖

```gradle
implementation 'com.github.jsqlparser:jsqlparser:4.7'
```

## 注意事项

1. **子查询处理**: 如果 SQL 包含子查询，上述代码只会处理最外层的 SELECT。如果需要处理子查询，需要递归处理。

2. **UNION 查询**: 如果 SQL 包含 UNION，需要特殊处理 `SetOperationList`。

3. **DISTINCT**: 如果原 SQL 有 `SELECT DISTINCT`，修改后会变成 `SELECT COUNT(*)`，如果需要保留去重，应使用 `COUNT(DISTINCT column)`。

4. **性能优化**: 对于分页查询，通常会先执行 count 查询获取总数，再执行实际的分页查询。

## 实际应用场景

这个功能通常用于：
- 分页查询时获取总记录数
- 统计符合条件的记录数量
- 检查查询结果是否为空
- 性能监控和查询分析

## 错误处理

```java
public static String modifySqlToCount(String sql) {
    try {
        Select select = (Select) CCJSqlParserUtil.parse(sql);
        
        // 检查是否为 PlainSelect
        if (!(select.getSelectBody() instanceof PlainSelect)) {
            throw new IllegalArgumentException("仅支持简单的 SELECT 查询");
        }
        
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        
        // 创建并设置 count(*)
        Function countFunction = new Function();
        countFunction.setName("count");
        countFunction.setAllColumns(true);
        
        SelectExpressionItem countItem = new SelectExpressionItem(countFunction);
        plainSelect.setSelectItems(Collections.singletonList(countItem));
        
        return select.toString();
        
    } catch (JSQLParserException e) {
        throw new RuntimeException("SQL 解析失败: " + e.getMessage(), e);
    }
}
```
