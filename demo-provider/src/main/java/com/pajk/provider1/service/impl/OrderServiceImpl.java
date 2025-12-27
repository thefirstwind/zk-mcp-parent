package com.pajk.provider1.service.impl;

import com.pajk.provider1.model.Order;
import com.pajk.provider1.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 订单服务实现类
 */
@Slf4j
@DubboService(version = "1.0.0", group = "demo")
public class OrderServiceImpl implements OrderService {
    
    private final Map<String, Order> orderStorage = new ConcurrentHashMap<>();
    
    public OrderServiceImpl() {
        // 初始化一些测试数据
        initTestData();
    }
    
    private void initTestData() {
        List<Order.OrderItem> items1 = new ArrayList<>();
        items1.add(new Order.OrderItem(1L, "iPhone 15", 7999.0, 1, 7999.0));
        items1.add(new Order.OrderItem(2L, "AirPods Pro", 1999.0, 1, 1999.0));
        
        Order order1 = new Order("ORD001", 1L, "PAID", 9998.0, 
                                "北京市朝阳区xxx街道xxx号", "Alice Wang", "13800138001", 
                                "请尽快发货", items1, LocalDateTime.now(), LocalDateTime.now());
        
        List<Order.OrderItem> items2 = new ArrayList<>();
        items2.add(new Order.OrderItem(3L, "MacBook Pro", 15999.0, 1, 15999.0));
        
        Order order2 = new Order("ORD002", 2L, "SHIPPED", 15999.0, 
                                "上海市浦东新区xxx路xxx号", "Bob Chen", "13800138002", 
                                "", items2, LocalDateTime.now(), LocalDateTime.now());
        
        orderStorage.put("ORD001", order1);
        orderStorage.put("ORD002", order2);
    }
    
    @Override
    public Order getOrderById(String orderId) {
        log.info("Getting order by id: {}", orderId);
        Order order = orderStorage.get(orderId);
        if (order == null) {
            log.warn("Order not found with id: {}", orderId);
        }
        return order;
    }
    
    @Override
    public List<Order> getOrdersByUserId(Long userId) {
        log.info("Getting orders by user id: {}", userId);
        List<Order> userOrders = orderStorage.values().stream()
                .filter(order -> order.getUserId().equals(userId))
                .collect(Collectors.toList());
        log.info("Found {} orders for user: {}", userOrders.size(), userId);
        return userOrders;
    }
    
    @Override
    public Order createOrder(Order order) {
        log.info("Creating new order for user: {}", order.getUserId());
        String orderId = "ORD" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        order.setId(orderId);
        order.setStatus("PENDING");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        
        // 计算总金额
        if (order.getOrderItems() != null) {
            double total = order.getOrderItems().stream()
                    .mapToDouble(item -> item.getPrice() * item.getQuantity())
                    .sum();
            order.setTotalAmount(total);
        }
        
        orderStorage.put(orderId, order);
        log.info("Order created successfully with id: {}", orderId);
        return order;
    }
    
    @Override
    public Order updateOrderStatus(String orderId, String status) {
        log.info("Updating order status: {} to {}", orderId, status);
        Order order = orderStorage.get(orderId);
        if (order == null) {
            log.warn("Order not found for status update: {}", orderId);
            return null;
        }
        
        order.setStatus(status);
        order.setUpdateTime(LocalDateTime.now());
        orderStorage.put(orderId, order);
        log.info("Order status updated successfully: {} -> {}", orderId, status);
        return order;
    }
    
    @Override
    public boolean cancelOrder(String orderId) {
        log.info("Cancelling order: {}", orderId);
        Order order = orderStorage.get(orderId);
        if (order == null) {
            log.warn("Order not found for cancellation: {}", orderId);
            return false;
        }
        
        if ("DELIVERED".equals(order.getStatus()) || "CANCELLED".equals(order.getStatus())) {
            log.warn("Cannot cancel order in status: {}", order.getStatus());
            return false;
        }
        
        order.setStatus("CANCELLED");
        order.setUpdateTime(LocalDateTime.now());
        orderStorage.put(orderId, order);
        log.info("Order cancelled successfully: {}", orderId);
        return true;
    }
    
    @Override
    public Double calculateOrderTotal(String orderId) {
        log.info("Calculating order total: {}", orderId);
        Order order = orderStorage.get(orderId);
        if (order == null) {
            log.warn("Order not found for total calculation: {}", orderId);
            return null;
        }
        
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return 0.0;
        }
        
        double total = order.getOrderItems().stream()
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum();
        
        log.info("Order total calculated: {} = {}", orderId, total);
        return total;
    }

}   





