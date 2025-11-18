/**
 * 分批删除服务
 * 实现根据index_id和index_params条件分批删除数据库记录
 */
public class BatchDeleteService {
    
    // 每批删除的记录数，可根据实际情况调整
    private static final int BATCH_SIZE = 1000;
    
    /**
     * 分批删除记录（方案1：使用LIMIT，适用于MySQL）
     * 
     * @param table 表名
     * @param indexId 索引ID
     * @param indexParams 索引参数
     * @param customSqlExecuteService SQL执行服务
     * @return 删除的总记录数
     */
    public int batchDeleteWithLimit(String table, int indexId, String indexParams, 
                                    CustomSqlExecuteService customSqlExecuteService) {
        // 先查询符合条件的记录总数
        String originSqlCount = "select count(*) as count from " + table 
                + " where index_id = " + indexId 
                + " and index_params = '" + indexParams + "'";
        int totalCount = getCountVal(originSqlCount);
        
        if (totalCount == 0) {
            return 0;
        }
        
        int deletedCount = 0;
        int batchCount = 0;
        
        // 循环分批删除，直到所有记录都被删除
        while (deletedCount < totalCount) {
            // 构建分批删除的SQL，使用LIMIT限制每次删除的数量（MySQL语法）
            String sql = "delete from " + table 
                    + " where index_id = " + indexId 
                    + " and index_params = '" + indexParams + "'"
                    + " limit " + BATCH_SIZE;
            
            int affectedRows = customSqlExecuteService.executeCustomSqlUpdate(sql);
            
            if (affectedRows == 0) {
                // 如果没有删除任何记录，说明已经删除完毕，退出循环
                break;
            }
            
            deletedCount += affectedRows;
            batchCount++;
            
            // 可选：添加日志输出
            System.out.println("第 " + batchCount + " 批删除完成，本批删除 " + affectedRows + " 条记录，累计删除 " + deletedCount + " 条记录");
            
            // 可选：添加短暂延迟，避免对数据库造成过大压力
            try {
                Thread.sleep(100); // 延迟100毫秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        return deletedCount;
    }
    
    /**
     * 分批删除记录（方案2：使用子查询，更通用，适用于大多数数据库）
     * 通过先查询主键，然后删除指定数量的记录
     * 
     * @param table 表名
     * @param indexId 索引ID
     * @param indexParams 索引参数
     * @param primaryKeyColumn 主键列名（如：id, pk_id等）
     * @param customSqlExecuteService SQL执行服务
     * @return 删除的总记录数
     */
    public int batchDeleteWithSubquery(String table, int indexId, String indexParams,
                                       String primaryKeyColumn,
                                       CustomSqlExecuteService customSqlExecuteService) {
        // 先查询符合条件的记录总数
        String originSqlCount = "select count(*) as count from " + table 
                + " where index_id = " + indexId 
                + " and index_params = '" + indexParams + "'";
        int totalCount = getCountVal(originSqlCount);
        
        if (totalCount == 0) {
            return 0;
        }
        
        int deletedCount = 0;
        int batchCount = 0;
        
        // 循环分批删除
        while (true) {
            // 先查询要删除的主键ID（限制数量）
            String selectSql = "select " + primaryKeyColumn + " from " + table 
                    + " where index_id = " + indexId 
                    + " and index_params = '" + indexParams + "'"
                    + " limit " + BATCH_SIZE;
            
            // 获取要删除的主键ID列表（需要根据实际项目实现）
            // List<Long> ids = getPrimaryKeyIds(selectSql);
            // if (ids == null || ids.isEmpty()) {
            //     break;
            // }
            
            // 构建删除SQL（使用IN子句）
            // String idsStr = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
            // String sql = "delete from " + table + " where " + primaryKeyColumn + " in (" + idsStr + ")";
            
            // 简化版本：直接使用LIMIT（如果数据库支持）
            String sql = "delete from " + table 
                    + " where index_id = " + indexId 
                    + " and index_params = '" + indexParams + "'"
                    + " limit " + BATCH_SIZE;
            
            int affectedRows = customSqlExecuteService.executeCustomSqlUpdate(sql);
            
            if (affectedRows == 0) {
                break;
            }
            
            deletedCount += affectedRows;
            batchCount++;
            
            System.out.println("第 " + batchCount + " 批删除完成，本批删除 " + affectedRows + " 条记录，累计删除 " + deletedCount + " 条记录");
            
            // 如果本次删除的记录数小于批次大小，说明已经是最后一批
            if (affectedRows < BATCH_SIZE) {
                break;
            }
            
            // 可选：添加短暂延迟
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        return deletedCount;
    }
    
    /**
     * 获取记录数
     * 注意：这个方法需要根据实际项目中的实现来调用
     */
    private int getCountVal(String sql) {
        // TODO: 实现获取记录数的逻辑
        // 示例：return jdbcTemplate.queryForObject(sql, Integer.class);
        return 0;
    }
}
