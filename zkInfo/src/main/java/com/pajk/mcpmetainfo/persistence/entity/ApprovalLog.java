package com.pajk.mcpmetainfo.persistence.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审批日志实体
 * 
 * 记录服务级别的审批历史，便于审计和追踪。
 * 注意：审批现在在服务级别进行，不再在 Provider 级别
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
     * 关联的服务ID（引用 zk_dubbo_service.id）
     */
    private Long serviceId;
    
    /**
     * 关联的审批申请ID（引用 zk_service_approval.id，可选）
     */
    private Long approvalId;
    
    /**
     * 原审批状态（字符串形式，如 "INIT", "PENDING", "APPROVED", "REJECTED"）
     */
    private String oldStatus;
    
    /**
     * 新审批状态（字符串形式）
     */
    private String newStatus;
    
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
    public ApprovalLog(Long serviceId, String oldStatus, String newStatus, 
                       String approver, String approvalComment) {
        this.serviceId = serviceId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.approver = approver;
        this.approvalComment = approvalComment;
        this.createdAt = LocalDateTime.now();
    }
}

