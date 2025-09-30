package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户 Mapper 接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    /**
     * 自定义更新方法 - 强制更新所有字段（包括 null 值）
     * 
     * @param user 用户对象
     * @return 影响行数
     */
    @Update("UPDATE user SET username = #{username}, email = #{email}, " +
            "phone = #{phone}, age = #{age}, status = #{status}, " +
            "description = #{description}, update_time = NOW() " +
            "WHERE id = #{id}")
    int updateByIdForceAll(@Param("user") User user);
    
    /**
     * 自定义更新方法 - 只更新指定字段为 null
     * 
     * @param id 用户ID
     * @param description 描述（可以为null）
     * @return 影响行数
     */
    @Update("UPDATE user SET description = #{description}, update_time = NOW() " +
            "WHERE id = #{id}")
    int updateDescriptionById(@Param("id") Long id, @Param("description") String description);
}