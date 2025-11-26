# SQL 查询字段替换为 COUNT(*) 工具

这个项目展示了如何使用 JSQLParser 库将 SQL 查询的 SELECT 字段替换为 `COUNT(*)`。

## 文件说明

- **SqlCountModifier.java** - 基础实现，包含简单的字段替换功能
- **SqlCountModifierAdvanced.java** - 高级实现，包含5种不同的处理方法：
  1. 基础版本 - 仅替换字段
  2. 完整版本 - 移除 GROUP BY、ORDER BY 等子句
  3. COUNT DISTINCT - 支持去重统计
  4. 智能处理 - 根据需求保留或移除子句
  5. 子查询包装 - 将复杂查询包装为子查询

- **SQL_COUNT_MODIFIER_README.md** - 详细的使用指南和示例

## 核心代码片段

```java
// 解析 SQL 查询
Select select = (Select) CCJSqlParserUtil.parse(sql);
PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

// 创建 count(*) 函数
Function countFunction = new Function();
countFunction.setName("count");
countFunction.setAllColumns(true);

// 创建 SelectExpressionItem 并设置为 count(*)
SelectExpressionItem countItem = new SelectExpressionItem();
countItem.setExpression(countFunction);

// 替换原查询字段为 count(*)
plainSelect.setSelectItems(Collections.singletonList(countItem));

// 获取修改后的 SQL
String modifiedSql = select.toString();
```

## 快速开始

查看 `SQL_COUNT_MODIFIER_README.md` 获取详细的使用说明和示例。

## Maven 依赖

```xml
<dependency>
    <groupId>com.github.jsqlparser</groupId>
    <artifactId>jsqlparser</artifactId>
    <version>4.7</version>
</dependency>
```

## 使用场景

- 分页查询时获取总记录数
- 统计符合条件的记录数量
- 数据库查询优化
- 性能监控和分析