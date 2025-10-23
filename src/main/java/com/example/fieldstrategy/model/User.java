package com.example.fieldstrategy.model;

import com.example.fieldstrategy.annotation.FieldConfig;
import com.example.fieldstrategy.enums.FieldStrategy;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户实体类，演示不同的字段策略使用
 * 
 * @author Example
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    /**
     * 用户ID - 只读字段，只在序列化时输出
     */
    @FieldConfig(
        strategy = FieldStrategy.READ_ONLY,
        description = "用户唯一标识",
        groups = {"detail", "list"}
    )
    private Long id;
    
    /**
     * 用户名 - 默认策略，正常序列化和反序列化
     */
    @FieldConfig(
        strategy = FieldStrategy.DEFAULT,
        description = "用户名",
        groups = {"detail", "list", "create"}
    )
    private String username;
    
    /**
     * 密码 - 只写字段，只在反序列化时接收，不会在序列化时输出
     */
    @FieldConfig(
        strategy = FieldStrategy.WRITE_ONLY,
        description = "用户密码",
        encrypted = true,
        groups = {"create", "update"}
    )
    private String password;
    
    /**
     * 邮箱 - 使用别名
     */
    @FieldConfig(
        strategy = FieldStrategy.DEFAULT,
        alias = "email_address",
        description = "用户邮箱",
        groups = {"detail", "create", "update"}
    )
    private String email;
    
    /**
     * 手机号 - 非空时才序列化
     */
    @FieldConfig(
        strategy = FieldStrategy.NOT_NULL,
        description = "手机号码",
        groups = {"detail", "update"}
    )
    private String phoneNumber;
    
    /**
     * 用户昵称 - 非空非空白时才序列化
     */
    @FieldConfig(
        strategy = FieldStrategy.NOT_EMPTY,
        description = "用户昵称",
        groups = {"detail", "list"}
    )
    private String nickname;
    
    /**
     * 内部备注 - 完全忽略的字段，不参与序列化和反序列化
     */
    @FieldConfig(
        strategy = FieldStrategy.IGNORED,
        description = "内部备注，不对外暴露"
    )
    private String internalNote;
    
    /**
     * 用户令牌 - 被忽略的敏感信息
     */
    @FieldConfig(
        strategy = FieldStrategy.IGNORED,
        description = "访问令牌"
    )
    private String accessToken;
    
    /**
     * 创建时间 - 只读字段
     */
    @FieldConfig(
        strategy = FieldStrategy.READ_ONLY,
        description = "创建时间",
        groups = {"detail"}
    )
    private LocalDateTime createdAt;
    
    /**
     * 更新时间 - 只读字段
     */
    @FieldConfig(
        strategy = FieldStrategy.READ_ONLY,
        description = "最后更新时间",
        groups = {"detail"}
    )
    private LocalDateTime updatedAt;
    
    /**
     * 用户角色 - 默认策略
     */
    @FieldConfig(
        strategy = FieldStrategy.DEFAULT,
        description = "用户角色列表",
        groups = {"detail"}
    )
    private List<String> roles;
    
    /**
     * 账户是否激活
     */
    @FieldConfig(
        strategy = FieldStrategy.DEFAULT,
        description = "账户激活状态",
        groups = {"detail", "list"}
    )
    private Boolean active;
    
    /**
     * 用户积分 - 只在detail分组中显示
     */
    @FieldConfig(
        strategy = FieldStrategy.DEFAULT,
        description = "用户积分",
        groups = {"detail"}
    )
    private Integer points;
    
    /**
     * 临时验证码 - 忽略字段
     */
    @FieldConfig(
        strategy = FieldStrategy.IGNORED,
        description = "临时验证码"
    )
    private String verificationCode;
}