package com.example.fieldstrategy.model;

import com.example.fieldstrategy.annotation.FieldConfig;
import com.example.fieldstrategy.enums.FieldStrategy;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体类，演示电商场景中的字段策略使用
 * 
 * @author Example
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    
    /**
     * 商品ID
     */
    @FieldConfig(
        strategy = FieldStrategy.READ_ONLY,
        description = "商品唯一标识"
    )
    private Long id;
    
    /**
     * 商品名称
     */
    @FieldConfig(
        strategy = FieldStrategy.DEFAULT,
        description = "商品名称"
    )
    private String name;
    
    /**
     * 商品描述
     */
    @FieldConfig(
        strategy = FieldStrategy.NOT_EMPTY,
        description = "商品描述"
    )
    private String description;
    
    /**
     * 商品价格
     */
    @FieldConfig(
        strategy = FieldStrategy.DEFAULT,
        description = "商品价格"
    )
    private BigDecimal price;
    
    /**
     * 成本价格 - 敏感信息，完全忽略
     */
    @FieldConfig(
        strategy = FieldStrategy.IGNORED,
        description = "商品成本价，内部使用"
    )
    private BigDecimal costPrice;
    
    /**
     * 库存数量
     */
    @FieldConfig(
        strategy = FieldStrategy.DEFAULT,
        description = "库存数量",
        groups = {"admin", "inventory"}
    )
    private Integer stock;
    
    /**
     * 供应商信息 - 内部信息，忽略
     */
    @FieldConfig(
        strategy = FieldStrategy.IGNORED,
        description = "供应商详细信息"
    )
    private String supplierInfo;
    
    /**
     * 商品SKU
     */
    @FieldConfig(
        strategy = FieldStrategy.DEFAULT,
        alias = "sku_code",
        description = "商品SKU编码"
    )
    private String sku;
    
    /**
     * 折扣价格 - 非空时才显示
     */
    @FieldConfig(
        strategy = FieldStrategy.NOT_NULL,
        description = "折扣价格"
    )
    private BigDecimal discountPrice;
    
    /**
     * 商品分类
     */
    @FieldConfig(
        strategy = FieldStrategy.DEFAULT,
        description = "商品分类"
    )
    private String category;
    
    /**
     * 是否上架
     */
    @FieldConfig(
        strategy = FieldStrategy.DEFAULT,
        description = "上架状态"
    )
    private Boolean onSale;
    
    /**
     * 创建时间
     */
    @FieldConfig(
        strategy = FieldStrategy.READ_ONLY,
        description = "创建时间"
    )
    private LocalDateTime createdAt;
    
    /**
     * 销量统计 - 只读
     */
    @FieldConfig(
        strategy = FieldStrategy.READ_ONLY,
        description = "销售数量"
    )
    private Integer salesCount;
    
    /**
     * 内部备注 - 忽略
     */
    @FieldConfig(
        strategy = FieldStrategy.IGNORED,
        description = "内部备注信息"
    )
    private String internalNotes;
}