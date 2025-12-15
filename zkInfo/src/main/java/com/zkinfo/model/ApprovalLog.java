package com.zkinfo.model;

import com.zkinfo.model.ProviderInfoEntity.ApprovalStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审批日志实体
 * 
 * 记录Provider信息的审批历史，便于审计和追踪。
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalLog {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 关联的服务提供者ID
     */
    private Long providerId;
    
    /**
     * 原审批状态
     */
    private ApprovalStatus oldStatus;
    
    /**
     * 新审批状态
     */
    private ApprovalStatus newStatus;
    
    /**
     * 审批人
     */
    private String approver;
    
    /**
     * 审批意见
     */
    private String approvalComment;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 构造函数
     */
    public ApprovalLog(Long providerId, ApprovalStatus oldStatus, ApprovalStatus newStatus, 
                       String approver, String approvalComment) {
        this.providerId = providerId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.approver = approver;
        this.approvalComment = approvalComment;
        this.createdAt = LocalDateTime.now();
    }
}