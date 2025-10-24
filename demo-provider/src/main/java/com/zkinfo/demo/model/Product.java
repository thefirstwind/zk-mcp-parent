package com.zkinfo.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 产品实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 产品ID
     */
    private Long id;
    
    /**
     * 产品名称
     */
    private String name;
    
    /**
     * 产品描述
     */
    private String description;
    
    /**
     * 产品分类
     */
    private String category;
    
    /**
     * 产品价格
     */
    private Double price;
    
    /**
     * 库存数量
     */
    private Integer stock;
    
    /**
     * 产品图片URL
     */
    private String imageUrl;
    
    /**
     * 品牌
     */
    private String brand;
    
    /**
     * 产品状态 (ACTIVE/INACTIVE/OUT_OF_STOCK)
     */
    private String status;
    
    /**
     * 销量
     */
    private Integer salesCount;
    
    /**
     * 评分
     */
    private Double rating;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}





