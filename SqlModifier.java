import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SelectItemVisitor;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import java.util.ArrayList;
import java.util.List;

public class SqlModifier {
    
    public static String replaceSelectFieldsWithCount(String sql) {
        try {
            // 解析 SQL 查询
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
            
            // 创建 count(*) 函数
            Function countFunction = new Function();
            countFunction.setName("COUNT");
            countFunction.setAllColumns(true);
            
            // 创建 SelectItem 并替换原来的字段列表
            List<SelectItem> selectItems = new ArrayList<>();
            selectItems.add(new net.sf.jsqlparser.statement.select.SelectExpressionItem(countFunction));
            
            // 设置新的 selectItems
            plainSelect.setSelectItems(selectItems);
            
            // 返回修改后的 SQL
            return select.toString();
        } catch (Exception e) {
            throw new RuntimeException("解析或修改 SQL 失败: " + e.getMessage(), e);
        }
    }
    
    public static void main(String[] args) {
        // 示例用法
        String originalSql = "SELECT id, name, age FROM users WHERE age > 18";
        System.out.println("原始 SQL: " + originalSql);
        
        String modifiedSql = replaceSelectFieldsWithCount(originalSql);
        System.out.println("修改后 SQL: " + modifiedSql);
    }
}
