package com.pajk.mcpmetainfo.persistence.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Provider参数实体
 * 
 * 对应数据库中的 zk_provider_parameter 表，存储 Provider 的参数键值对
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderParameterEntity {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 关联的Provider ID
     */
    private Long providerId;
    
    /**
     * 参数键
     */
    private String paramKey;
    
    /**
     * 参数值
     */
    private String paramValue;
    
    /**
     * 参数顺序
     */
    private Integer paramOrder;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    public ProviderParameterEntity(Long providerId, String paramKey, String paramValue, Integer paramOrder) {
        this.providerId = providerId;
        this.paramKey = paramKey;
        this.paramValue = paramValue;
        this.paramOrder = paramOrder;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}


