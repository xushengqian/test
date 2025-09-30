package com.example.demo;

/**
 * MyBatis-Plus baseMapper.updateById 字段为 null 未更新问题的解决方案
 * 
 * 问题描述：
 * 在使用 MyBatis-Plus 的 baseMapper.updateById() 方法时，如果实体对象的某个字段值为 null，
 * 该字段不会被更新到数据库中，这是 MyBatis-Plus 的默认行为。
 * 
 * 原因分析：
 * MyBatis-Plus 默认使用 FieldStrategy.NOT_NULL 策略，即只有非 null 的字段才会被包含在 UPDATE 语句中。
 * 这样设计是为了避免意外覆盖数据库中的有效数据。
 * 
 * 解决方案：
 * 
 * 1. 【推荐】使用 @TableField 注解的 updateStrategy 属性
 *    在实体类字段上使用 @TableField(updateStrategy = FieldStrategy.IGNORED)
 *    这样该字段的 null 值也会被更新到数据库
 * 
 * 2. 【推荐】使用 UpdateWrapper
 *    UpdateWrapper<User> wrapper = new UpdateWrapper<>();
 *    wrapper.eq("id", id).set("field_name", null);
 *    userService.update(wrapper);
 * 
 * 3. 全局配置字段策略
 *    在 application.yml 中配置：
 *    mybatis-plus:
 *      global-config:
 *        db-config:
 *          update-strategy: ignored
 * 
 * 4. 使用自定义 SQL
 *    在 Mapper 中编写自定义的 UPDATE SQL 语句
 * 
 * 5. 先查询再更新
 *    先查询出完整对象，然后设置需要更新的字段，再调用 updateById
 * 
 * 各方案对比：
 * 
 * 方案1 - @TableField(updateStrategy = FieldStrategy.IGNORED)：
 * 优点：简单直接，对特定字段生效
 * 缺点：需要在实体类上修改，影响所有使用该实体的更新操作
 * 适用场景：确定某个字段经常需要设置为 null 的情况
 * 
 * 方案2 - UpdateWrapper：
 * 优点：灵活性最高，可以精确控制每次更新的字段
 * 缺点：代码稍微复杂一些
 * 适用场景：需要动态控制更新字段的情况（推荐）
 * 
 * 方案3 - 全局配置：
 * 优点：一次配置，全局生效
 * 缺点：影响范围太大，可能导致意外的数据覆盖
 * 适用场景：项目中大部分更新操作都需要支持 null 值更新
 * 
 * 方案4 - 自定义 SQL：
 * 优点：完全控制 SQL 语句，性能最优
 * 缺点：需要编写和维护额外的 SQL 代码
 * 适用场景：复杂的更新逻辑或性能要求很高的场景
 * 
 * 方案5 - 先查询再更新：
 * 优点：逻辑清晰，易于理解
 * 缺点：需要额外的数据库查询，性能较差，存在并发问题
 * 适用场景：更新频率不高，对性能要求不严格的场景
 * 
 * 推荐使用顺序：
 * 1. UpdateWrapper（方案2）- 最灵活，适用于大多数场景
 * 2. @TableField 注解（方案1）- 适用于特定字段总是需要支持 null 更新
 * 3. 自定义 SQL（方案4）- 适用于复杂更新逻辑
 * 4. 全局配置（方案3）- 谨慎使用，适用于项目整体需求
 * 5. 先查询再更新（方案5）- 不推荐，除非特殊情况
 */
public class NullUpdateSolutions {
    
    // 这个类仅用于文档说明，不包含实际代码
    
}