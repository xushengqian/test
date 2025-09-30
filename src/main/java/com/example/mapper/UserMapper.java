package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    /**
     * 自定义SQL更新，可以更新null字段
     * 
     * @param user 用户对象
     * @return 影响行数
     */
    @Update("UPDATE user SET " +
            "username = #{user.username}, " +
            "password = #{user.password}, " +
            "email = #{user.email}, " +
            "phone = #{user.phone}, " +
            "address = #{user.address}, " +
            "age = #{user.age}, " +
            "status = #{user.status}, " +
            "remark = #{user.remark} " +
            "WHERE id = #{user.id}")
    int updateByIdWithNull(@Param("user") User user);
    
    /**
     * 选择性更新某些字段为null
     */
    @Update("UPDATE user SET email = null WHERE id = #{id}")
    int setEmailToNull(@Param("id") Long id);
    
    /**
     * 动态SQL更新
     */
    @Update("<script>" +
            "UPDATE user " +
            "<set>" +
            "    <if test='user.username != null or forceNull'>" +
            "        username = #{user.username}," +
            "    </if>" +
            "    <if test='user.email != null or forceNull'>" +
            "        email = #{user.email}," +
            "    </if>" +
            "    <if test='user.phone != null or forceNull'>" +
            "        phone = #{user.phone}," +
            "    </if>" +
            "</set>" +
            "WHERE id = #{user.id}" +
            "</script>")
    int updateDynamic(@Param("user") User user, @Param("forceNull") boolean forceNull);
}