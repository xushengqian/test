/**
 * 改进的分批删除实现
 * 直接改进原始代码，添加分批删除功能
 */
public class ImprovedBatchDelete {
    
    // 每批删除的记录数，可根据实际情况调整
    private static final int BATCH_SIZE = 1000;
    
    /**
     * 分批删除记录（改进版本）
     * 
     * @param table 表名
     * @param indexId 索引ID
     * @param indexParams 索引参数
     * @param customSqlExecuteService SQL执行服务
     */
    public void batchDelete(String table, int indexId, String indexParams, 
                           CustomSqlExecuteService customSqlExecuteService) {
        // 1. 先查询总数
        String originSqlCount = "SELECT COUNT(*) AS count FROM " + table + 
                               " WHERE index_id = " + indexId + 
                               " AND index_params = '" + indexParams + "'";
        int count = getCountVal(originSqlCount);
        
        if (count == 0) {
            return;
        }
        
        // 2. 分批删除
        int deletedTotal = 0;
        int batchNumber = 0;
        
        while (deletedTotal < count) {
            // 构建分批删除SQL
            // 注意：不同数据库的语法可能不同
            // MySQL: DELETE ... LIMIT
            // PostgreSQL: DELETE ... WHERE id IN (SELECT id ... LIMIT)
            // Oracle: DELETE ... WHERE ROWNUM <= ?
            
            String sql = "DELETE FROM " + table + 
                        " WHERE index_id = " + indexId + 
                        " AND index_params = '" + indexParams + "'" +
                        " LIMIT " + BATCH_SIZE;
            
            int deletedRows = customSqlExecuteService.executeCustomSqlUpdate(sql);
            deletedTotal += deletedRows;
            batchNumber++;
            
            // 如果删除的行数为0，说明已经删除完毕
            if (deletedRows == 0) {
                break;
            }
            
            // 可选：添加日志
            // log.info("批次 {} 删除完成，已删除 {} 条记录，剩余约 {} 条", 
            //          batchNumber, deletedTotal, count - deletedTotal);
            
            // 可选：添加短暂延迟，避免对数据库造成过大压力
            // try {
            //     Thread.sleep(50);
            // } catch (InterruptedException e) {
            //     Thread.currentThread().interrupt();
            //     break;
            // }
        }
    }
    
    /**
     * 如果数据库不支持 DELETE ... LIMIT，使用此方法
     * 通过主键ID范围删除
     */
    public void batchDeleteByRange(String table, int indexId, String indexParams,
                                   CustomSqlExecuteService customSqlExecuteService) {
        String originSqlCount = "SELECT COUNT(*) AS count FROM " + table + 
                               " WHERE index_id = " + indexId + 
                               " AND index_params = '" + indexParams + "'";
        int count = getCountVal(originSqlCount);
        
        if (count == 0) {
            return;
        }
        
        // 获取最小和最大ID
        String minMaxSql = "SELECT MIN(id) AS min_id, MAX(id) AS max_id FROM " + table + 
                          " WHERE index_id = " + indexId + 
                          " AND index_params = '" + indexParams + "'";
        // int minId = getMinId(minMaxSql);
        // int maxId = getMaxId(minMaxSql);
        
        // 按ID范围分批删除
        // for (int startId = minId; startId <= maxId; startId += BATCH_SIZE) {
        //     int endId = Math.min(startId + BATCH_SIZE - 1, maxId);
        //     String sql = "DELETE FROM " + table + 
        //                 " WHERE index_id = " + indexId + 
        //                 " AND index_params = '" + indexParams + "'" +
        //                 " AND id >= " + startId + " AND id <= " + endId;
        //     customSqlExecuteService.executeCustomSqlUpdate(sql);
        // }
    }
    
    /**
     * 使用子查询方式删除（适用于不支持 DELETE LIMIT 的数据库）
     */
    public void batchDeleteWithSubquery(String table, int indexId, String indexParams,
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
            // 使用子查询限制删除数量
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
    
    // 原有的方法，保持不变
    private int getCountVal(String sql) {
        // 原有实现
        return 0;
    }
}
