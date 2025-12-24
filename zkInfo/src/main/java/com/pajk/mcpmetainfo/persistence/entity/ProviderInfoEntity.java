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
     * 审批状态: INIT-初始化, PENDING-待审批, APPROVED-已审批, REJECTED-已拒绝
     */
    private ApprovalStatus approvalStatus = ApprovalStatus.INIT;
    
    /**
     * 审批人
     */
    private String approver;
    
    /**
     * 审批时间
     */
    private LocalDateTime approvalTime;
    
    /**
     * 审批意见
     */
    private String approvalComment;
    
    /**
     * 是否健康
     */
    private Boolean isHealthy = true;
    
    /**
     * 最后同步时间
     */
    private LocalDateTime lastSyncTime;
    
    /**
     * 路径根（如：/dubbo）
     */
    private String pathRoot;
    
    /**
     * 路径中的接口名
     */
    private String pathInterface;
    
    /**
     * 路径中的地址（IP:Port）
     */
    private String pathAddress;
    
    /**
     * 路径中的协议
     */
    private String pathProtocol;
    
    /**
     * 路径中的版本
     */
    private String pathVersion;
    
    /**
     * 路径中的分组
     */
    private String pathGroup;
    
    /**
     * 路径中的应用名
     */
    private String pathApplication;
    
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
     */
    public ProviderInfoEntity(ProviderInfo providerInfo) {
        // 复制父类的所有属性
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
        
        // 解析 zk_path 并填充结构化字段
        if (providerInfo.getZkPath() != null) {
            com.pajk.mcpmetainfo.core.util.ZkPathParser.fillPathFields(this, providerInfo.getZkPath());
        }
        
        // 初始化数据库相关字段
        this.approvalStatus = ApprovalStatus.INIT;
        this.lastSyncTime = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 更新方法：从ProviderInfo更新ProviderInfoEntity
     */
    public void updateFromProviderInfo(ProviderInfo providerInfo) {
        // 更新父类的所有属性
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
        
        // 解析 zk_path 并更新结构化字段
        if (providerInfo.getZkPath() != null) {
            com.pajk.mcpmetainfo.core.util.ZkPathParser.fillPathFields(this, providerInfo.getZkPath());
        }
        
        // 更新同步时间
        this.lastSyncTime = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 转换为 ProviderInfo 对象
     * 
     * @return ProviderInfo 对象
     */
    public ProviderInfo toProviderInfo() {
        ProviderInfo providerInfo = new ProviderInfo();
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

