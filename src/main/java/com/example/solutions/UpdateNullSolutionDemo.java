package com.example.solutions;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.entity.User;

/**
 * MyBatis-Plus updateById null字段更新问题解决方案演示
 * 
 * @author AI Assistant
 * @date 2024
 */
public class UpdateNullSolutionDemo {
    
    /**
     * 问题说明：
     * 
     * 当使用 MyBatis-Plus 的 baseMapper.updateById(entity) 方法时，
     * 如果实体对象的某些字段值为 null，这些字段不会被更新到数据库。
     * 
     * 原因：
     * MyBatis-Plus 的默认字段更新策略是 FieldStrategy.NOT_NULL，
     * 即只更新非 null 的字段。
     */
    
    // ==================== 解决方案1：实体注解配置 ====================
    
    /**
     * 在实体类的字段上使用 @TableField 注解
     */
    static class Solution1_EntityAnnotation {
        
        // 方式1：特定字段使用 IGNORED 策略（始终更新，包括null）
        @TableField(updateStrategy = FieldStrategy.IGNORED)
        private String alwaysUpdateField;
        
        // 方式2：特定字段使用 NOT_EMPTY 策略（非空字符串才更新）
        @TableField(updateStrategy = FieldStrategy.NOT_EMPTY)
        private String notEmptyField;
        
        // 方式3：默认策略 NOT_NULL（非null才更新）
        private String defaultField;
    }
    
    // ==================== 解决方案2：全局配置 ====================
    
    /**
     * 在 application.yml 中配置全局策略：
     * 
     * mybatis-plus:
     *   global-config:
     *     db-config:
     *       # 全局字段更新策略
     *       update-strategy: not_null  # 可选值：ignored, not_null, not_empty
     *       
     * 策略说明：
     * - ignored: 忽略判断，所有字段都更新（包括null）
     * - not_null: 非NULL更新（默认）
     * - not_empty: 非空更新（对于字符串类型，空字符串也不更新）
     */
    
    // ==================== 解决方案3：使用 UpdateWrapper ====================
    
    public void solution3_UpdateWrapper(Long userId) {
        // 方式1：使用 UpdateWrapper
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", userId)
                .set("email", null)      // 明确设置为null
                .set("phone", null)      // 明确设置为null
                .set("address", "新地址"); // 正常更新
        
        // 执行更新
        // userService.update(updateWrapper);
        
        // 方式2：使用 LambdaUpdateWrapper（推荐，类型安全）
        LambdaUpdateWrapper<User> lambdaWrapper = Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getEmail, null)      // 明确设置为null
                .set(User::getPhone, null)      // 明确设置为null
                .set(User::getAddress, "新地址");
        
        // 执行更新
        // userService.update(lambdaWrapper);
    }
    
    // ==================== 解决方案4：自定义SQL ====================
    
    /**
     * 在 Mapper 接口中定义自定义方法
     */
    interface CustomUpdateMapper {
        
        // 方式1：使用 @Update 注解
        // @Update("UPDATE user SET email = #{email}, phone = #{phone} WHERE id = #{id}")
        // int updateWithNull(@Param("id") Long id, 
        //                   @Param("email") String email, 
        //                   @Param("phone") String phone);
        
        // 方式2：使用 XML 配置
        /*
        <update id="updateWithNull">
            UPDATE user 
            SET email = #{email},
                phone = #{phone},
                update_time = NOW()
            WHERE id = #{id}
        </update>
        */
    }
    
    // ==================== 解决方案5：动态SQL ====================
    
    public void solution5_DynamicSQL(User user, boolean forceUpdateNull) {
        LambdaUpdateWrapper<User> wrapper = Wrappers.<User>lambdaUpdate()
                .eq(User::getId, user.getId());
        
        // 根据条件决定是否更新null值
        if (user.getEmail() != null || forceUpdateNull) {
            wrapper.set(User::getEmail, user.getEmail());
        }
        if (user.getPhone() != null || forceUpdateNull) {
            wrapper.set(User::getPhone, user.getPhone());
        }
        if (user.getAddress() != null) {
            wrapper.set(User::getAddress, user.getAddress());
        }
        
        // 执行更新
        // userService.update(wrapper);
    }
    
    // ==================== 解决方案6：使用 allEq 方法 ====================
    
    public void solution6_AllEq(Long userId, User user) {
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userId);
        
        // 构建更新字段Map
        java.util.Map<String, Object> updateMap = new java.util.HashMap<>();
        updateMap.put("email", user.getEmail());
        updateMap.put("phone", user.getPhone());
        updateMap.put("address", user.getAddress());
        
        // allEq 第二个参数控制null值处理
        // true: null值也会更新
        // false: null值不更新（默认）
        wrapper.allEq(updateMap, true);
        
        // 执行更新
        // userService.update(wrapper);
    }
    
    // ==================== 最佳实践建议 ====================
    
    /**
     * 最佳实践建议：
     * 
     * 1. 场景选择：
     *    - 偶尔需要更新null值：使用 UpdateWrapper/LambdaUpdateWrapper
     *    - 某字段经常需要更新null：在实体字段上加 @TableField(updateStrategy = FieldStrategy.IGNORED)
     *    - 全局都需要更新null：修改全局配置（慎用）
     *    - 复杂业务逻辑：自定义SQL
     * 
     * 2. 性能考虑：
     *    - UpdateWrapper 性能最好（单次数据库操作）
     *    - 避免先查后更（两次数据库操作）
     *    - 批量更新时考虑使用批处理
     * 
     * 3. 安全性：
     *    - 谨慎使用全局 IGNORED 策略，可能导致意外的null更新
     *    - 使用 LambdaUpdateWrapper 避免字段名拼写错误
     *    - 明确区分"不更新"和"更新为null"的业务场景
     * 
     * 4. 代码规范：
     *    - 统一团队内的处理方式
     *    - 在方法注释中说明null值的处理策略
     *    - 对于重要字段，添加单元测试验证null更新行为
     */
    
    // ==================== 常见错误示例 ====================
    
    public static class CommonMistakes {
        
        /**
         * 错误示例1：期望updateById更新null值
         */
        public void mistake1_ExpectNullUpdate() {
            User user = new User();
            user.setId(1L);
            user.setEmail(null);  // 期望更新为null，但实际不会更新
            
            // baseMapper.updateById(user);  // email字段不会被更新！
        }
        
        /**
         * 错误示例2：混淆空字符串和null
         */
        public void mistake2_EmptyStringVsNull() {
            User user = new User();
            user.setId(1L);
            user.setEmail("");  // 空字符串会被更新
            // user.setEmail(null);  // null不会被更新（默认策略）
            
            // baseMapper.updateById(user);
        }
        
        /**
         * 错误示例3：忘记设置更新条件
         */
        public void mistake3_NoCondition() {
            UpdateWrapper<User> wrapper = new UpdateWrapper<>();
            wrapper.set("email", null);  // 设置更新内容
            // 忘记设置WHERE条件，会更新所有记录！
            
            // userService.update(wrapper);  // 危险：更新所有记录
        }
    }
}