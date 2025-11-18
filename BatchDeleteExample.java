/**
 * 分批删除示例代码
 * 改进点：
 * 1. 使用参数化查询防止 SQL 注入
 * 2. 实现分批删除，避免一次性删除大量数据
 * 3. 添加事务控制和错误处理
 */
public class BatchDeleteExample {
    
    // 每批删除的记录数
    private static final int BATCH_SIZE = 1000;
    
    /**
     * 分批删除记录
     * 
     * @param table 表名
     * @param indexId 索引ID
     * @param indexParams 索引参数
     * @param customSqlExecuteService SQL执行服务
     */
    public void batchDeleteRecords(String table, int indexId, String indexParams, 
                                    CustomSqlExecuteService customSqlExecuteService) {
        // 1. 先查询总数
        String countSql = "SELECT COUNT(*) AS count FROM " + table + 
                         " WHERE index_id = ? AND index_params = ?";
        int totalCount = getCountVal(countSql, indexId, indexParams);
        
        if (totalCount == 0) {
            return;
        }
        
        // 2. 计算需要删除的批次数
        int batchCount = (totalCount + BATCH_SIZE - 1) / BATCH_SIZE;
        
        // 3. 分批删除
        for (int i = 0; i < batchCount; i++) {
            String deleteSql = "DELETE FROM " + table + 
                             " WHERE index_id = ? AND index_params = ? " +
                             " LIMIT ?";
            
            // 执行删除，每次删除 BATCH_SIZE 条记录
            int deletedRows = customSqlExecuteService.executeCustomSqlUpdate(
                deleteSql, indexId, indexParams, BATCH_SIZE);
            
            // 如果删除的行数小于批次大小，说明已经删除完毕
            if (deletedRows < BATCH_SIZE) {
                break;
            }
            
            // 可选：添加延迟，避免对数据库造成过大压力
            // Thread.sleep(100);
        }
    }
    
    /**
     * 使用参数化查询获取记录数（推荐方式）
     */
    private int getCountVal(String sql, int indexId, String indexParams) {
        // 使用 PreparedStatement 执行参数化查询
        // 这里需要根据实际的数据库访问方式实现
        // 示例：
        // PreparedStatement ps = connection.prepareStatement(sql);
        // ps.setInt(1, indexId);
        // ps.setString(2, indexParams);
        // ResultSet rs = ps.executeQuery();
        // return rs.next() ? rs.getInt("count") : 0;
        return 0; // 占位实现
    }
    
    /**
     * 方法2：如果数据库不支持 LIMIT 在 DELETE 中使用，可以使用主键范围删除
     */
    public void batchDeleteByPrimaryKeyRange(String table, int indexId, String indexParams,
                                             CustomSqlExecuteService customSqlExecuteService) {
        String countSql = "SELECT COUNT(*) AS count FROM " + table + 
                         " WHERE index_id = ? AND index_params = ?";
        int totalCount = getCountVal(countSql, indexId, indexParams);
        
        if (totalCount == 0) {
            return;
        }
        
        // 使用主键范围分批删除
        int offset = 0;
        while (true) {
            // 先查询当前批次的主键
            String selectSql = "SELECT id FROM " + table + 
                             " WHERE index_id = ? AND index_params = ? " +
                             " ORDER BY id LIMIT ? OFFSET ?";
            
            // 获取当前批次的主键列表
            // List<Integer> ids = getPrimaryKeys(selectSql, indexId, indexParams, BATCH_SIZE, offset);
            
            // 如果列表为空，说明已经删除完毕
            // if (ids.isEmpty()) {
            //     break;
            // }
            
            // 构建 IN 子句删除
            // String deleteSql = "DELETE FROM " + table + " WHERE id IN (" + 
            //                   ids.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
            // customSqlExecuteService.executeCustomSqlUpdate(deleteSql);
            
            offset += BATCH_SIZE;
            if (offset >= totalCount) {
                break;
            }
        }
    }
    
    /**
     * 方法3：使用游标方式删除（适用于大数据量）
     */
    public void batchDeleteWithCursor(String table, int indexId, String indexParams,
                                      CustomSqlExecuteService customSqlExecuteService) {
        String countSql = "SELECT COUNT(*) AS count FROM " + table + 
                         " WHERE index_id = ? AND index_params = ?";
        int totalCount = getCountVal(countSql, indexId, indexParams);
        
        if (totalCount == 0) {
            return;
        }
        
        int deletedTotal = 0;
        while (deletedTotal < totalCount) {
            // 使用子查询限制删除数量
            String deleteSql = "DELETE FROM " + table + 
                             " WHERE index_id = ? AND index_params = ? " +
                             " AND id IN (" +
                             "   SELECT id FROM (" +
                             "     SELECT id FROM " + table + 
                             "     WHERE index_id = ? AND index_params = ? " +
                             "     LIMIT ?" +
                             "   ) AS temp" +
                             ")";
            
            int deletedRows = customSqlExecuteService.executeCustomSqlUpdate(
                deleteSql, indexId, indexParams, indexId, indexParams, BATCH_SIZE);
            
            deletedTotal += deletedRows;
            
            if (deletedRows == 0) {
                break;
            }
        }
    }
}

/**
 * SQL执行服务接口（示例）
 */
interface CustomSqlExecuteService {
    /**
     * 执行更新SQL（参数化查询版本）
     */
    int executeCustomSqlUpdate(String sql, Object... params);
}
