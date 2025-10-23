/**
 * 用户实体类
 * 演示FieldStrategy.IGNORED的使用
 */
public class User {
    
    private Long id;
    
    private String username;
    
    private String email;
    
    /**
     * 密码字段 - 使用IGNORED策略，在所有操作中都被忽略
     */
    @FieldIgnore(strategy = FieldStrategy.IGNORED)
    private String password;
    
    /**
     * 创建时间 - 在更新操作中忽略，但不是完全忽略
     */
    @FieldIgnore(strategy = FieldStrategy.DEFAULT, value = {FieldIgnore.IgnoreType.UPDATE})
    private java.util.Date createTime;
    
    /**
     * 更新时间 - 在插入操作中忽略，但不是完全忽略
     */
    @FieldIgnore(strategy = FieldStrategy.DEFAULT, value = {FieldIgnore.IgnoreType.INSERT})
    private java.util.Date updateTime;
    
    /**
     * 临时字段 - 完全忽略，不参与任何数据库操作
     */
    @FieldIgnore(strategy = FieldStrategy.IGNORED)
    private String tempField;
    
    /**
     * 版本号 - 只在非空时处理
     */
    @FieldIgnore(strategy = FieldStrategy.NOT_NULL)
    private Integer version;
    
    // 构造函数
    public User() {}
    
    public User(Long id, String username, String email, String password) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.createTime = new java.util.Date();
        this.tempField = "这是临时数据";
    }
    
    // Getter和Setter方法
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public java.util.Date getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(java.util.Date createTime) {
        this.createTime = createTime;
    }
    
    public java.util.Date getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(java.util.Date updateTime) {
        this.updateTime = updateTime;
    }
    
    public String getTempField() {
        return tempField;
    }
    
    public void setTempField(String tempField) {
        this.tempField = tempField;
    }
    
    public Integer getVersion() {
        return version;
    }
    
    public void setVersion(Integer version) {
        this.version = version;
    }
    
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", password='[HIDDEN]'" +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", tempField='" + tempField + '\'' +
                ", version=" + version +
                '}';
    }
}