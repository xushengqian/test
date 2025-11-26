import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.AllColumns;

import java.util.Collections;

/**
 * SQL 查询修改器 - 将查询字段替换为 count(*)
 */
public class SqlCountModifier {

    /**
     * 将 SQL 查询的 SELECT 字段替换为 count(*)
     * 
     * @param sql 原始 SQL 查询
     * @return 修改后的 SQL 查询
     * @throws Exception 解析异常
     */
    public static String modifySqlToCount(String sql) throws Exception {
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
        
        // 返回修改后的 SQL
        return select.toString();
    }

    /**
     * 示例使用
     */
    public static void main(String[] args) {
        try {
            // 示例1: 简单查询
            String sql1 = "SELECT id, name, age FROM users WHERE age > 18";
            String modifiedSql1 = modifySqlToCount(sql1);
            System.out.println("原始 SQL: " + sql1);
            System.out.println("修改后 SQL: " + modifiedSql1);
            System.out.println();
            
            // 示例2: 带 JOIN 的查询
            String sql2 = "SELECT u.id, u.name, o.order_id FROM users u JOIN orders o ON u.id = o.user_id WHERE u.status = 'active'";
            String modifiedSql2 = modifySqlToCount(sql2);
            System.out.println("原始 SQL: " + sql2);
            System.out.println("修改后 SQL: " + modifiedSql2);
            System.out.println();
            
            // 示例3: 带 GROUP BY 的查询
            String sql3 = "SELECT department, COUNT(*) as cnt FROM employees GROUP BY department";
            String modifiedSql3 = modifySqlToCount(sql3);
            System.out.println("原始 SQL: " + sql3);
            System.out.println("修改后 SQL: " + modifiedSql3);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
