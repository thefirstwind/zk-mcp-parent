package com.pajk.mcpmetainfo.persistence.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Provider方法实体
 * 
 * 对应数据库中的 zk_provider_method 表，存储 Provider 的方法列表
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderMethodEntity {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 关联的Provider ID
     */
    private Long providerId;
    
    /**
     * 方法名
     */
    private String methodName;
    
    /**
     * 方法顺序
     */
    private Integer methodOrder;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    public ProviderMethodEntity(Long providerId, String methodName, Integer methodOrder) {
        this.providerId = providerId;
        this.methodName = methodName;
        this.methodOrder = methodOrder;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}


