package org.springblade.modules.index.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 索引自定义SQL Mapper
 */
@Mapper
public interface IndexCustomSqlMapper {
    
    /**
     * 更新自定义SQL（删除用户绩效明细）
     * 
     * @param indexId 索引ID
     * @param indexParams 索引参数
     * @param limit 删除数量限制
     * @return 删除的行数
     */
    int updateCustomSql(@Param("indexId") Long indexId, 
                       @Param("indexParams") String indexParams,
                       @Param("limit") Integer limit);
}
