package org.springblade.modules.index.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 索引自定义SQL Mapper
 * 处理死锁问题的示例实现
 */
@Mapper
public interface IndexCustomSqlMapper {
    
    /**
     * 更新自定义SQL（删除操作）
     * 注意：实际执行时应该添加重试机制
     * 
     * @param indexId 索引ID
     * @param indexParams 索引参数
     * @param limit 删除数量限制
     * @return 删除的行数
     */
    int updateCustomSql(
        @Param("indexId") Long indexId,
        @Param("indexParams") String indexParams,
        @Param("limit") Integer limit
    );
    
    /**
     * 批量删除（小批量，减少死锁风险）
     * 
     * @param indexId 索引ID
     * @param indexParams 索引参数
     * @param batchSize 批次大小，建议100-200
     * @return 总删除行数
     */
    int deleteInBatches(
        @Param("indexId") Long indexId,
        @Param("indexParams") String indexParams,
        @Param("batchSize") Integer batchSize
    );
}
