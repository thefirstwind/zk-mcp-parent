package com.pajk.provider2.service;

import com.pajk.provider2.model.Order;
import java.util.List;

/**
 * 订单服务接口
 */
public interface OrderService {
    
    /**
     * 根据订单ID获取订单信息
     * @param orderId 订单ID
     * @return 订单信息
     */
    Order getOrderById(String orderId, String status);
    
    /**
     * 根据用户ID获取订单列表
     * @param userId 用户ID
     * @return 订单列表
     */
    List<Order> getOrdersByUserId(Long userId);
    
    /**
     * 创建新订单
     * @param order 订单信息
     * @return 创建的订单信息
     */
    Order createOrder(Order order);
    
    /**
     * 更新订单状态
     * @param orderId 订单ID
     * @param status 新状态
     * @return 更新后的订单信息
     */
    Order updateOrderStatus(String orderId, String status);
    
    /**
     * 取消订单
     * @param orderId 订单ID
     * @return 是否取消成功
     */
    boolean cancelOrder(String orderId);
    
    /**
     * 计算订单总金额
     * @param orderId 订单ID
     * @return 订单总金额
     */
    Double calculateOrderTotal(String orderId, Integer num);

}

