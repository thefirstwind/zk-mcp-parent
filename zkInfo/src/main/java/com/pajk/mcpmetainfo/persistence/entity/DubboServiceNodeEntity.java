package com.pajk.mcpmetainfo.persistence.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Dubbo服务节点信息实体（数据库映射版本）
 * 
 * 对应数据库中的dubbo_service_nodes表，存储服务节点的详细信息，与服务表关联
 * 注意：该表仅保存在线节点信息，通过ZK变更监听更新
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class DubboServiceNodeEntity {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 关联的服务ID
     */
    private Long serviceId;
    
    /**
     * 提供者地址 (IP:Port)
     */
    private String address;
    
    /**
     * ZooKeeper节点路径
     */
    private String zkPath;
    
    /**
     * 注册时间
     */
    private LocalDateTime registerTime;
    
    /**
     * 最后心跳时间
     */
    private LocalDateTime lastHeartbeat;
    
    /**
     * 最后同步时间
     */
    private LocalDateTime lastSyncTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 构造函数：从ProviderInfo创建DubboServiceNodeEntity
     */
    public DubboServiceNodeEntity(ProviderInfo providerInfo, Long serviceId) {
        this.serviceId = serviceId;
        this.address = providerInfo.getAddress();
        this.zkPath = providerInfo.getZkPath();
        this.registerTime = providerInfo.getRegisterTime();
        this.lastHeartbeat = providerInfo.getLastHeartbeat();
        
        // 初始化数据库相关字段
        this.lastSyncTime = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 更新方法：从ProviderInfo更新DubboServiceNodeEntity
     */
    public void updateFromProviderInfo(ProviderInfo providerInfo) {
        this.address = providerInfo.getAddress();
        this.zkPath = providerInfo.getZkPath();
        this.registerTime = providerInfo.getRegisterTime();
        this.lastHeartbeat = providerInfo.getLastHeartbeat();
        
        // 更新同步时间
        this.lastSyncTime = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}

