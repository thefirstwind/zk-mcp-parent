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
     * 服务接口名（便于定位问题）
     */
    private String interfaceName;
    
    /**
     * 服务版本（从 zk_dubbo_service 获取）
     */
    private String version;
    
    /**
     * 提供者地址 (IP:Port)
     */
    private String address;
    
    /**
     * 注册时间
     */
    private LocalDateTime registrationTime;
    
    /**
     * 最后心跳时间
     */
    private LocalDateTime lastHeartbeatTime;
    
    /**
     * 是否在线
     */
    private Boolean isOnline;
    
    /**
     * 是否健康
     */
    private Boolean isHealthy;
    
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
        this.interfaceName = providerInfo.getInterfaceName();
        this.address = providerInfo.getAddress();
        this.registrationTime = providerInfo.getRegistrationTime() != null ? providerInfo.getRegistrationTime() : 
            (providerInfo.getRegisterTime() != null ? providerInfo.getRegisterTime() : LocalDateTime.now());
        this.lastHeartbeatTime = providerInfo.getLastHeartbeat();
        this.isOnline = providerInfo.getOnline() != null ? providerInfo.getOnline() : true;
        this.isHealthy = providerInfo.getHealthy() != null ? providerInfo.getHealthy() : true;
        
        // 初始化数据库相关字段
        this.lastSyncTime = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 构造函数：从ProviderInfo和ServiceEntity创建DubboServiceNodeEntity
     */
    public DubboServiceNodeEntity(ProviderInfo providerInfo, Long serviceId, String version) {
        this.serviceId = serviceId;
        this.interfaceName = providerInfo.getInterfaceName();
        this.version = version;
        this.address = providerInfo.getAddress();
        this.registrationTime = providerInfo.getRegistrationTime() != null ? providerInfo.getRegistrationTime() : 
            (providerInfo.getRegisterTime() != null ? providerInfo.getRegisterTime() : LocalDateTime.now());
        this.lastHeartbeatTime = providerInfo.getLastHeartbeat();
        this.isOnline = providerInfo.getOnline() != null ? providerInfo.getOnline() : true;
        this.isHealthy = providerInfo.getHealthy() != null ? providerInfo.getHealthy() : true;
        
        // 初始化数据库相关字段
        this.lastSyncTime = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 更新方法：从ProviderInfo更新DubboServiceNodeEntity
     */
    public void updateFromProviderInfo(ProviderInfo providerInfo) {
        this.interfaceName = providerInfo.getInterfaceName();
        this.address = providerInfo.getAddress();
        if (providerInfo.getRegistrationTime() != null) {
            this.registrationTime = providerInfo.getRegistrationTime();
        } else if (providerInfo.getRegisterTime() != null) {
            this.registrationTime = providerInfo.getRegisterTime();
        }
        if (providerInfo.getLastHeartbeat() != null) {
            this.lastHeartbeatTime = providerInfo.getLastHeartbeat();
        }
        if (providerInfo.getOnline() != null) {
            this.isOnline = providerInfo.getOnline();
        }
        if (providerInfo.getHealthy() != null) {
            this.isHealthy = providerInfo.getHealthy();
        }
        
        // 更新同步时间
        this.lastSyncTime = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 更新方法：从ProviderInfo和version更新DubboServiceNodeEntity
     */
    public void updateFromProviderInfo(ProviderInfo providerInfo, String version) {
        this.interfaceName = providerInfo.getInterfaceName();
        this.version = version;
        this.address = providerInfo.getAddress();
        if (providerInfo.getRegistrationTime() != null) {
            this.registrationTime = providerInfo.getRegistrationTime();
        } else if (providerInfo.getRegisterTime() != null) {
            this.registrationTime = providerInfo.getRegisterTime();
        }
        if (providerInfo.getLastHeartbeat() != null) {
            this.lastHeartbeatTime = providerInfo.getLastHeartbeat();
        }
        if (providerInfo.getOnline() != null) {
            this.isOnline = providerInfo.getOnline();
        }
        if (providerInfo.getHealthy() != null) {
            this.isHealthy = providerInfo.getHealthy();
        }
        
        // 更新同步时间
        this.lastSyncTime = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}

