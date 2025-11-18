/**
 * 分批删除示例代码
 * 基于您提供的代码进行改进，实现分批删除功能
 */

// 方案1：最简单的实现（适用于MySQL等支持DELETE LIMIT的数据库）
String originSqlCount = "select count(*) as count from " + table 
        + " where index_id = " + indexId 
        + " and index_params = '" + indexParams + "'";
int count = getCountVal(originSqlCount);
if (count == 0) {
    return;
}

// 每批删除的记录数
int batchSize = 1000;
int deletedCount = 0;
int batchNumber = 0;

// 循环分批删除
while (deletedCount < count) {
    String sql = "delete from " + table 
            + " where index_id = " + indexId 
            + " and index_params = '" + indexParams + "'"
            + " limit " + batchSize;
    
    int affectedRows = customSqlExecuteService.executeCustomSqlUpdate(sql);
    
    if (affectedRows == 0) {
        // 没有删除任何记录，退出循环
        break;
    }
    
    deletedCount += affectedRows;
    batchNumber++;
    
    // 可选：记录日志
    // logger.info("第{}批删除完成，删除{}条记录，累计删除{}条", batchNumber, affectedRows, deletedCount);
    
    // 如果本次删除的记录数小于批次大小，说明已经是最后一批
    if (affectedRows < batchSize) {
        break;
    }
    
    // 可选：添加短暂延迟，避免对数据库造成过大压力
    // Thread.sleep(100);
}

// ============================================
// 方案2：更通用的实现（适用于不支持DELETE LIMIT的数据库，如Oracle）
// ============================================
/*
String originSqlCount = "select count(*) as count from " + table 
        + " where index_id = " + indexId 
        + " and index_params = '" + indexParams + "'";
int count = getCountVal(originSqlCount);
if (count == 0) {
    return;
}

int batchSize = 1000;
int deletedCount = 0;
int batchNumber = 0;

while (deletedCount < count) {
    // 先查询要删除的主键ID（假设主键列名为id）
    String selectSql = "select id from " + table 
            + " where index_id = " + indexId 
            + " and index_params = '" + indexParams + "'"
            + " limit " + batchSize;
    
    // 获取主键ID列表（需要根据实际项目实现）
    List<Long> ids = getPrimaryKeyIds(selectSql);
    if (ids == null || ids.isEmpty()) {
        break;
    }
    
    // 构建删除SQL（使用IN子句）
    String idsStr = ids.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
    String sql = "delete from " + table 
            + " where id in (" + idsStr + ")";
    
    int affectedRows = customSqlExecuteService.executeCustomSqlUpdate(sql);
    
    if (affectedRows == 0) {
        break;
    }
    
    deletedCount += affectedRows;
    batchNumber++;
    
    // 可选：记录日志
    // logger.info("第{}批删除完成，删除{}条记录，累计删除{}条", batchNumber, affectedRows, deletedCount);
    
    // 如果本次删除的记录数小于批次大小，说明已经是最后一批
    if (affectedRows < batchSize) {
        break;
    }
    
    // 可选：添加短暂延迟
    // Thread.sleep(100);
}
*/

// ============================================
// 方案3：使用PreparedStatement防止SQL注入（推荐）
// ============================================
/*
String originSqlCount = "select count(*) as count from " + table 
        + " where index_id = ? and index_params = ?";
int count = getCountVal(originSqlCount, indexId, indexParams);
if (count == 0) {
    return;
}

int batchSize = 1000;
int deletedCount = 0;
int batchNumber = 0;

while (deletedCount < count) {
    String sql = "delete from " + table 
            + " where index_id = ? and index_params = ?"
            + " limit ?";
    
    // 使用PreparedStatement执行（需要根据实际项目实现）
    int affectedRows = customSqlExecuteService.executeCustomSqlUpdate(
            sql, indexId, indexParams, batchSize);
    
    if (affectedRows == 0) {
        break;
    }
    
    deletedCount += affectedRows;
    batchNumber++;
    
    if (affectedRows < batchSize) {
        break;
    }
    
    // 可选：添加短暂延迟
    // Thread.sleep(100);
}
*/
