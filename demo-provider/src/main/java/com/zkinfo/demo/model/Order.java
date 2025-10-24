package com.zkinfo.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 订单ID
     */
    private String id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 订单状态 (PENDING/PAID/SHIPPED/DELIVERED/CANCELLED)
     */
    private String status;
    
    /**
     * 订单总金额
     */
    private Double totalAmount;
    
    /**
     * 收货地址
     */
    private String shippingAddress;
    
    /**
     * 收货人姓名
     */
    private String receiverName;
    
    /**
     * 收货人电话
     */
    private String receiverPhone;
    
    /**
     * 订单备注
     */
    private String remark;
    
    /**
     * 订单项列表
     */
    private List<OrderItem> orderItems;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 订单项内部类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private Long productId;
        private String productName;
        private Double price;
        private Integer quantity;
        private Double subtotal;
    }
}





