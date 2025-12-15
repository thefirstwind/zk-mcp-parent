package com.zkinfo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Dubbo服务信息实体（数据库映射版本）
 * 
 * 对应数据库中的dubbo_services表，存储服务的基本信息，按服务维度保存
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
public class DubboServiceEntity {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 服务接口名
     */
    private String interfaceName;
    
    /**
     * 协议类型
     */
    private String protocol;
    
    /**
     * 服务版本
     */
    private String version;
    
    /**
     * 服务分组
     */
    private String group;
    
    /**
     * 应用名称
     */
    private String application;
    
    /**
     * 审批状态: PENDING-待审批, APPROVED-已审批, REJECTED-已拒绝
     */
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;
    
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
     * 该服务下的Provider数量
     */
    private Integer providerCount = 0;
    
    /**
     * 该服务下在线的Provider数量
     */
    private Integer onlineProviderCount = 0;
    
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
     * 构造函数：从ProviderInfo创建DubboServiceEntity
     */
    public DubboServiceEntity(ProviderInfo providerInfo) {
        this.interfaceName = providerInfo.getInterfaceName();
        this.protocol = providerInfo.getProtocol();
        this.version = providerInfo.getVersion();
        this.group = providerInfo.getGroup();
        this.application = providerInfo.getApplication();
        
        // 初始化数据库相关字段
        this.approvalStatus = ApprovalStatus.PENDING;
        this.providerCount = 0;
        this.onlineProviderCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 获取服务唯一标识
     */
    public String getServiceKey() {
        StringBuilder key = new StringBuilder(interfaceName);
        if (protocol != null && !protocol.isEmpty()) {
            key.append(":").append(protocol);
        }
        if (version != null && !version.isEmpty()) {
            key.append(":").append(version);
        }
        if (group != null && !group.isEmpty()) {
            key.append(":").append(group);
        }
        if (application != null && !application.isEmpty()) {
            key.append(":").append(application);
        }
        return key.toString();
    }
}