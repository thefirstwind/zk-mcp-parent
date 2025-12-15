package com.zkinfo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
        
        // 更新同步时间
        this.lastSyncTime = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}