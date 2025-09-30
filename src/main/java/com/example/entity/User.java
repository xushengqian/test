package com.example.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * 演示 MyBatis-Plus updateById 字段为null未更新的问题
 */
@Data
@Accessors(chain = true)
@TableName("user")
public class User {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 密码
     */
    private String password;
    
    /**
     * 邮箱
     * 默认策略：NOT_NULL - 当字段为null时不会更新
     */
    private String email;
    
    /**
     * 手机号
     * 使用 @TableField 注解指定更新策略为 IGNORED
     * 这样即使字段为null也会被更新
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String phone;
    
    /**
     * 地址
     * 使用 @TableField 注解，可以动态控制更新策略
     */
    @TableField(updateStrategy = FieldStrategy.DEFAULT)
    private String address;
    
    /**
     * 年龄
     */
    private Integer age;
    
    /**
     * 状态（0：禁用，1：启用）
     */
    private Integer status;
    
    /**
     * 备注
     * 演示使用 condition 条件控制
     */
    @TableField(condition = SqlCondition.LIKE)
    private String remark;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    /**
     * 逻辑删除标志
     */
    @TableLogic
    private Integer deleted;
}