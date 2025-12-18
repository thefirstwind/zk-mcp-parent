package com.pajk.mcpmetainfo.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 服务审批实体
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceApproval {
    
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
    private ApprovalStatus status;
    
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
     * 审批状态枚举
     */
    public enum ApprovalStatus {
        PENDING,    // 待审批
        APPROVED,   // 已通过
        REJECTED,   // 已拒绝
        CANCELLED   // 已取消
    }
}

