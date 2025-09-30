package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * 演示 updateById 字段为 null 时未更新的问题
 */
@Data
@Accessors(chain = true)
@TableName("user")
public class User {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户名 - 默认策略：NOT_NULL（null值不更新）
     */
    @TableField("username")
    private String username;
    
    /**
     * 邮箱 - 使用 IGNORED 策略（null值也会更新）
     */
    @TableField(value = "email", updateStrategy = FieldStrategy.IGNORED)
    private String email;
    
    /**
     * 手机号 - 使用 NOT_EMPTY 策略（null和空字符串都不更新）
     */
    @TableField(value = "phone", updateStrategy = FieldStrategy.NOT_EMPTY)
    private String phone;
    
    /**
     * 年龄 - 默认策略：NOT_NULL
     */
    @TableField("age")
    private Integer age;
    
    /**
     * 状态 - 使用 IGNORED 策略
     */
    @TableField(value = "status", updateStrategy = FieldStrategy.IGNORED)
    private Integer status;
    
    /**
     * 描述 - 可能需要设置为 null
     */
    @TableField("description")
    private String description;
    
    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}