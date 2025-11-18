/**
 * 分批删除解决方案
 * 直接改进原始代码，添加分批删除逻辑
 */
public class BatchDeleteSolution {
    
    // 每批删除的记录数
    private static final int BATCH_SIZE = 1000;
    
    /**
     * 原始代码改进版：添加分批删除功能
     */
    public void deleteRecordsInBatches(String table, int indexId, String indexParams,
                                       CustomSqlExecuteService customSqlExecuteService) {
        // 1. 查询总数（保持原有逻辑）
        String originSqlCount = "SELECT COUNT(*) AS count FROM " + table + 
                               " WHERE index_id = " + indexId + 
                               " AND index_params = '" + indexParams + "'";
        int count = getCountVal(originSqlCount);
        
        if (count == 0) {
            return;
        }
        
        // 2. 分批删除（新增逻辑）
        int deletedTotal = 0;
        while (deletedTotal < count) {
            // 方式1：MySQL 支持 DELETE ... LIMIT
            String sql = "DELETE FROM " + table + 
                        " WHERE index_id = " + indexId + 
                        " AND index_params = '" + indexParams + "'" +
                        " LIMIT " + BATCH_SIZE;
            
            int deletedRows = customSqlExecuteService.executeCustomSqlUpdate(sql);
            deletedTotal += deletedRows;
            
            // 如果本次删除0条，说明已经删除完毕
            if (deletedRows == 0) {
                break;
            }
        }
    }
    
    /**
     * 如果数据库不支持 DELETE LIMIT，使用子查询方式
     */
    public void deleteRecordsInBatchesWithSubquery(String table, int indexId, String indexParams,
                                                    CustomSqlExecuteService customSqlExecuteService) {
        String originSqlCount = "SELECT COUNT(*) AS count FROM " + table + 
                               " WHERE index_id = " + indexId + 
                               " AND index_params = '" + indexParams + "'";
        int count = getCountVal(originSqlCount);
        
        if (count == 0) {
            return;
        }
        
        int deletedTotal = 0;
        while (deletedTotal < count) {
            // 使用子查询限制删除数量（适用于 PostgreSQL 等）
            String sql = "DELETE FROM " + table + 
                        " WHERE index_id = " + indexId + 
                        " AND index_params = '" + indexParams + "'" +
                        " AND id IN (" +
                        "   SELECT id FROM (" +
                        "     SELECT id FROM " + table + 
                        "     WHERE index_id = " + indexId + 
                        "     AND index_params = '" + indexParams + "'" +
                        "     LIMIT " + BATCH_SIZE +
                        "   ) AS temp" +
                        ")";
            
            int deletedRows = customSqlExecuteService.executeCustomSqlUpdate(sql);
            deletedTotal += deletedRows;
            
            if (deletedRows == 0) {
                break;
            }
        }
    }
    
    // 保持原有方法
    private int getCountVal(String sql) {
        // 原有实现
        return 0;
    }
}
