package com.pajk.mcpmetainfo.persistence.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pajk.mcpmetainfo.core.model.ServiceApproval;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 服务审批实体（数据库映射版本）
 * 
 * 对应数据库中的zk_service_approval表，存储服务审批信息
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-12-17
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceApprovalEntity {
    
    /**
     * 审批ID
     */
    private Long id;
    
    /**
     * 服务接口名
     */
    private String serviceInterface;
    
    /**
     * 服务版本
     */
    private String serviceVersion;
    
    /**
     * 服务分组
     */
    private String serviceGroup;
    
    /**
     * 项目ID（可选）
     */
    private Long projectId;
    
    /**
     * 申请人ID
     */
    private Long applicantId;
    
    /**
     * 申请人姓名
     */
    private String applicantName;
    
    /**
     * 申请原因
     */
    private String reason;
    
    /**
     * 审批状态
     */
    private ServiceApproval.ApprovalStatus status;
    
    /**
     * 审批人ID
     */
    private Long approverId;
    
    /**
     * 审批人姓名
     */
    private String approverName;
    
    /**
     * 审批意见
     */
    private String comment;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 审批时间
     */
    private LocalDateTime approvedAt;
    
    /**
     * 从 ServiceApproval 模型转换为 ServiceApprovalEntity
     */
    public static ServiceApprovalEntity fromServiceApproval(ServiceApproval approval) {
        ServiceApprovalEntity entity = new ServiceApprovalEntity();
        entity.setId(approval.getId());
        entity.setServiceInterface(approval.getServiceInterface());
        entity.setServiceVersion(approval.getServiceVersion());
        entity.setServiceGroup(approval.getServiceGroup());
        entity.setProjectId(approval.getProjectId());
        entity.setApplicantId(approval.getApplicantId());
        entity.setApplicantName(approval.getApplicantName());
        entity.setReason(approval.getReason());
        entity.setStatus(approval.getStatus());
        entity.setApproverId(approval.getApproverId());
        entity.setApproverName(approval.getApproverName());
        entity.setComment(approval.getComment());
        entity.setCreatedAt(approval.getCreatedAt());
        entity.setUpdatedAt(approval.getUpdatedAt());
        entity.setApprovedAt(approval.getApprovedAt());
        return entity;
    }
    
    /**
     * 转换为 ServiceApproval 模型
     */
    public ServiceApproval toServiceApproval() {
        ServiceApproval approval = new ServiceApproval();
        approval.setId(this.id);
        approval.setServiceInterface(this.serviceInterface);
        approval.setServiceVersion(this.serviceVersion);
        approval.setServiceGroup(this.serviceGroup);
        approval.setProjectId(this.projectId);
        approval.setApplicantId(this.applicantId);
        approval.setApplicantName(this.applicantName);
        approval.setReason(this.reason);
        approval.setStatus(this.status);
        approval.setApproverId(this.approverId);
        approval.setApproverName(this.approverName);
        approval.setComment(this.comment);
        approval.setCreatedAt(this.createdAt);
        approval.setUpdatedAt(this.updatedAt);
        approval.setApprovedAt(this.approvedAt);
        return approval;
    }
}

