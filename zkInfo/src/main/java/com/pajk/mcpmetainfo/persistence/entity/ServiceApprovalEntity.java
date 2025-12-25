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
     * 关联的Dubbo服务ID（引用 zk_dubbo_service.id）
     */
    private Long serviceId;
    
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
     * 注意：serviceId 需要从 ServiceApproval 的 serviceInterface/serviceVersion/serviceGroup 查找对应的 service_id
     */
    public static ServiceApprovalEntity fromServiceApproval(ServiceApproval approval) {
        ServiceApprovalEntity entity = new ServiceApprovalEntity();
        entity.setId(approval.getId());
        // serviceId 需要外部设置，通过 serviceInterface/serviceVersion/serviceGroup 查找对应的 service_id
        // entity.setServiceId(...); // 需要外部设置
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
     * 注意：serviceInterface/serviceVersion/serviceGroup 需要从关联的 zk_dubbo_service 获取
     */
    public ServiceApproval toServiceApproval() {
        ServiceApproval approval = new ServiceApproval();
        approval.setId(this.id);
        // serviceInterface/serviceVersion/serviceGroup 需要从关联的 zk_dubbo_service 获取
        // approval.setServiceInterface(...); // 需要从关联的 service 获取
        // approval.setServiceVersion(...); // 需要从关联的 service 获取
        // approval.setServiceGroup(...); // 需要从关联的 service 获取
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

