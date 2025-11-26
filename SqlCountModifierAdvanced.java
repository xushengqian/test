import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;

import java.util.Collections;

/**
 * 高级 SQL 查询修改器 - 处理更复杂的场景
 */
public class SqlCountModifierAdvanced {

    /**
     * 方法1: 基础版本 - 仅替换字段为 count(*)
     */
    public static String basicCountModifier(String sql) throws Exception {
        Select select = (Select) CCJSqlParserUtil.parse(sql);
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        
        // 创建 count(*) 函数
        Function countFunction = new Function();
        countFunction.setName("count");
        countFunction.setAllColumns(true);
        
        // 替换字段
        SelectExpressionItem countItem = new SelectExpressionItem(countFunction);
        plainSelect.setSelectItems(Collections.singletonList(countItem));
        
        return select.toString();
    }

    /**
     * 方法2: 完整版本 - 移除 GROUP BY、HAVING、ORDER BY、LIMIT
     * 适用于获取总记录数的场景
     */
    public static String fullCountModifier(String sql) throws Exception {
        Select select = (Select) CCJSqlParserUtil.parse(sql);
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        
        // 创建 count(*) 函数
        Function countFunction = new Function();
        countFunction.setName("COUNT");
        countFunction.setAllColumns(true);
        
        // 替换字段为 count(*)
        SelectExpressionItem countItem = new SelectExpressionItem(countFunction);
        plainSelect.setSelectItems(Collections.singletonList(countItem));
        
        // 移除不需要的子句
        plainSelect.setGroupByElement(null);  // 移除 GROUP BY
        plainSelect.setHaving(null);           // 移除 HAVING
        plainSelect.setOrderByElements(null);  // 移除 ORDER BY
        plainSelect.setLimit(null);            // 移除 LIMIT
        plainSelect.setOffset(null);           // 移除 OFFSET
        plainSelect.setDistinct(null);         // 移除 DISTINCT
        
        return select.toString();
    }

    /**
     * 方法3: 保留 DISTINCT - 使用 COUNT(DISTINCT columns)
     */
    public static String countDistinctModifier(String sql, String distinctColumn) throws Exception {
        Select select = (Select) CCJSqlParserUtil.parse(sql);
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        
        // 创建 count(distinct column) 函数
        Function countFunction = new Function();
        countFunction.setName("COUNT");
        countFunction.setDistinct(true);
        
        // 如果指定了列，使用该列；否则使用第一列
        if (distinctColumn != null && !distinctColumn.isEmpty()) {
            ExpressionList expressionList = new ExpressionList();
            expressionList.addExpressions(CCJSqlParserUtil.parseExpression(distinctColumn));
            countFunction.setParameters(expressionList);
        } else {
            countFunction.setAllColumns(true);
        }
        
        // 替换字段
        SelectExpressionItem countItem = new SelectExpressionItem(countFunction);
        plainSelect.setSelectItems(Collections.singletonList(countItem));
        
        // 移除不需要的子句
        plainSelect.setGroupByElement(null);
        plainSelect.setOrderByElements(null);
        plainSelect.setLimit(null);
        plainSelect.setOffset(null);
        plainSelect.setDistinct(null);
        
        return select.toString();
    }

    /**
     * 方法4: 智能处理 - 根据原 SQL 自动判断是否需要保留某些子句
     */
    public static String smartCountModifier(String sql, boolean removeGroupBy) throws Exception {
        Select select = (Select) CCJSqlParserUtil.parse(sql);
        
        // 检查是否为 PlainSelect
        if (!(select.getSelectBody() instanceof PlainSelect)) {
            throw new IllegalArgumentException("仅支持简单的 SELECT 查询，不支持 UNION 等复杂查询");
        }
        
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        
        // 创建 count(*) 函数
        Function countFunction = new Function();
        countFunction.setName("COUNT");
        countFunction.setAllColumns(true);
        
        // 替换字段
        SelectExpressionItem countItem = new SelectExpressionItem(countFunction);
        plainSelect.setSelectItems(Collections.singletonList(countItem));
        
        // 根据参数决定是否移除 GROUP BY
        if (removeGroupBy) {
            plainSelect.setGroupByElement(null);
            plainSelect.setHaving(null);
        }
        
        // 总是移除 ORDER BY、LIMIT、OFFSET（对 count 查询无意义）
        plainSelect.setOrderByElements(null);
        plainSelect.setLimit(null);
        plainSelect.setOffset(null);
        plainSelect.setDistinct(null);
        
        return select.toString();
    }

    /**
     * 方法5: 包装为子查询 - 适用于复杂 GROUP BY 的场景
     * 将原 SQL 作为子查询，外层使用 COUNT(*)
     */
    public static String wrapAsSubqueryCount(String sql) throws Exception {
        // 解析原始 SQL
        Select originalSelect = (Select) CCJSqlParserUtil.parse(sql);
        PlainSelect originalPlainSelect = (PlainSelect) originalSelect.getSelectBody();
        
        // 移除原始查询的 ORDER BY 和 LIMIT（在子查询中无意义）
        originalPlainSelect.setOrderByElements(null);
        originalPlainSelect.setLimit(null);
        originalPlainSelect.setOffset(null);
        
        // 创建外层查询
        PlainSelect outerSelect = new PlainSelect();
        
        // 创建 count(*) 函数
        Function countFunction = new Function();
        countFunction.setName("COUNT");
        countFunction.setAllColumns(true);
        
        SelectExpressionItem countItem = new SelectExpressionItem(countFunction);
        outerSelect.setSelectItems(Collections.singletonList(countItem));
        
        // 将原始查询作为子查询
        SubSelect subSelect = new SubSelect();
        subSelect.setSelectBody(originalPlainSelect);
        subSelect.setAlias(new Alias("sub_query", false));
        
        outerSelect.setFromItem(subSelect);
        
        // 创建新的 Select 对象
        Select newSelect = new Select();
        newSelect.setSelectBody(outerSelect);
        
        return newSelect.toString();
    }

    /**
     * 测试所有方法
     */
    public static void main(String[] args) {
        try {
            System.out.println("========== 测试1: 基础版本 ==========");
            String sql1 = "SELECT id, name, age FROM users WHERE age > 18 ORDER BY age DESC LIMIT 10";
            System.out.println("原始 SQL: " + sql1);
            System.out.println("修改后: " + basicCountModifier(sql1));
            System.out.println();

            System.out.println("========== 测试2: 完整版本（移除所有不必要的子句）==========");
            String sql2 = "SELECT id, name, age FROM users WHERE age > 18 GROUP BY department HAVING COUNT(*) > 5 ORDER BY age DESC LIMIT 10";
            System.out.println("原始 SQL: " + sql2);
            System.out.println("修改后: " + fullCountModifier(sql2));
            System.out.println();

            System.out.println("========== 测试3: COUNT DISTINCT ==========");
            String sql3 = "SELECT DISTINCT department FROM employees WHERE status = 'active'";
            System.out.println("原始 SQL: " + sql3);
            System.out.println("修改后: " + countDistinctModifier(sql3, "department"));
            System.out.println();

            System.out.println("========== 测试4: 智能处理（保留 GROUP BY）==========");
            String sql4 = "SELECT department, COUNT(*) as cnt FROM employees GROUP BY department ORDER BY cnt DESC";
            System.out.println("原始 SQL: " + sql4);
            System.out.println("修改后（保留 GROUP BY）: " + smartCountModifier(sql4, false));
            System.out.println("修改后（移除 GROUP BY）: " + smartCountModifier(sql4, true));
            System.out.println();

            System.out.println("========== 测试5: 包装为子查询 ==========");
            String sql5 = "SELECT department, AVG(salary) as avg_salary FROM employees GROUP BY department HAVING AVG(salary) > 50000 ORDER BY avg_salary DESC";
            System.out.println("原始 SQL: " + sql5);
            System.out.println("修改后: " + wrapAsSubqueryCount(sql5));
            System.out.println();

            System.out.println("========== 测试6: 带 JOIN 的复杂查询 ==========");
            String sql6 = "SELECT u.id, u.name, o.total FROM users u LEFT JOIN orders o ON u.id = o.user_id WHERE u.status = 'active' AND o.total > 100";
            System.out.println("原始 SQL: " + sql6);
            System.out.println("修改后: " + fullCountModifier(sql6));
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
