package com.example.entity;

import javax.persistence.*;
import java.math.BigDecimal;

/**
 * 用户绩效实体类
 */
@Entity
@Table(name = "ks_index_user_performance", 
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"month", "user_id"}),
           @UniqueConstraint(columnNames = {"index_params"})
       })
public class UserPerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month", nullable = false, length = 7)
    private String month;

    @Column(name = "user_id", nullable = false, length = 20)
    private String userId;

    @Column(name = "index_params", nullable = false, unique = true, length = 50)
    private String indexParams;

    @Column(name = "kfzj_deal_amount", precision = 15, scale = 2)
    private BigDecimal kfzjDealAmount;

    // 乐观锁版本字段（可选，用于方案 4）
    @Version
    private Long version;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getIndexParams() {
        return indexParams;
    }

    public void setIndexParams(String indexParams) {
        this.indexParams = indexParams;
    }

    public BigDecimal getKfzjDealAmount() {
        return kfzjDealAmount;
    }

    public void setKfzjDealAmount(BigDecimal kfzjDealAmount) {
        this.kfzjDealAmount = kfzjDealAmount;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
