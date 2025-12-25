package com.pajk.mcpmetainfo.persistence.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Dubbo服务提供者信息实体（数据库映射版本）
 * 
 * 扩展ProviderInfo类，添加数据库持久化相关字段，包括审批流程信息。
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderInfoEntity extends ProviderInfo {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 关联的Dubbo服务ID（引用 zk_dubbo_service.id）
     */
    private Long serviceId;
    
    /**
     * 关联的服务节点ID（引用 zk_dubbo_service_node.id）
     */
    private Long nodeId;
    
    /**
     * 是否健康
     */
    private Boolean isHealthy = true;
    
    /**
     * 注意：审批状态已移除，Provider 的审批状态通过 service_id 关联 zk_dubbo_service.approval_status 获取
     */
    
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
     * 审批状态枚举
     */
    public enum ApprovalStatus {
        INIT("初始化"),
        PENDING("待审批"),
        APPROVED("已审批"),
        REJECTED("已拒绝");
        
        private final String description;
        
        ApprovalStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 构造函数：从ProviderInfo创建ProviderInfoEntity
     * 注意：serviceId 和 nodeId 需要外部设置
     */
    public ProviderInfoEntity(ProviderInfo providerInfo) {
        // 复制父类的所有属性（这些字段不保存到数据库，只用于内存操作）
        this.setInterfaceName(providerInfo.getInterfaceName());
        this.setAddress(providerInfo.getAddress());
        this.setProtocol(providerInfo.getProtocol());
        this.setVersion(providerInfo.getVersion());
        this.setGroup(providerInfo.getGroup());
        this.setApplication(providerInfo.getApplication());
        this.setMethods(providerInfo.getMethods());
        this.setParameters(providerInfo.getParameters());
        this.setRegisterTime(providerInfo.getRegisterTime());
        this.setLastHeartbeat(providerInfo.getLastHeartbeat());
        this.setOnline(providerInfo.isOnline());
        this.setZkPath(providerInfo.getZkPath());
        
        // 初始化数据库相关字段
        this.lastSyncTime = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 构造函数：从ProviderInfo创建ProviderInfoEntity，并设置关联ID
     */
    public ProviderInfoEntity(ProviderInfo providerInfo, Long serviceId, Long nodeId) {
        this(providerInfo);
        this.serviceId = serviceId;
        this.nodeId = nodeId;
    }
    
    /**
     * 更新方法：从ProviderInfo更新ProviderInfoEntity
     * 注意：只更新 Provider 特有的字段，服务基本信息通过 service_id 和 node_id 关联获取
     */
    public void updateFromProviderInfo(ProviderInfo providerInfo) {
        // 更新父类的所有属性（这些字段不保存到数据库，只用于内存操作）
        this.setInterfaceName(providerInfo.getInterfaceName());
        this.setAddress(providerInfo.getAddress());
        this.setProtocol(providerInfo.getProtocol());
        this.setVersion(providerInfo.getVersion());
        this.setGroup(providerInfo.getGroup());
        this.setApplication(providerInfo.getApplication());
        this.setMethods(providerInfo.getMethods());
        this.setParameters(providerInfo.getParameters());
        this.setRegisterTime(providerInfo.getRegisterTime());
        this.setLastHeartbeat(providerInfo.getLastHeartbeat());
        this.setOnline(providerInfo.isOnline());
        this.setZkPath(providerInfo.getZkPath());
        
        // 更新同步时间
        this.lastSyncTime = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 转换为 ProviderInfo 对象
     * 注意：如果实体中没有基本信息（通过关联获取），需要外部传入
     * 
     * @return ProviderInfo 对象
     */
    public ProviderInfo toProviderInfo() {
        ProviderInfo providerInfo = new ProviderInfo();
        // 从父类获取基本信息（这些字段可能通过关联查询填充）
        providerInfo.setInterfaceName(this.getInterfaceName());
        providerInfo.setAddress(this.getAddress());
        providerInfo.setProtocol(this.getProtocol());
        providerInfo.setVersion(this.getVersion());
        providerInfo.setGroup(this.getGroup());
        providerInfo.setApplication(this.getApplication());
        providerInfo.setMethods(this.getMethods());
        providerInfo.setParameters(this.getParameters());
        providerInfo.setRegisterTime(this.getRegisterTime());
        providerInfo.setLastHeartbeat(this.getLastHeartbeat());
        providerInfo.setOnline(this.isOnline());
        providerInfo.setZkPath(this.getZkPath());
        return providerInfo;
    }
}

