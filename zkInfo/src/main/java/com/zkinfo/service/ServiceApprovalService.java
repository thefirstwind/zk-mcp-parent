package com.zkinfo.service;

import com.zkinfo.model.ServiceApproval;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 服务审批服务
 * 
 * 负责服务审批流程的管理
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-01-15
 */
@Slf4j
@Service
public class ServiceApprovalService {
    
    private ServiceCollectionFilterService filterService;
    
    @Autowired(required = false)
    @Lazy
    public void setFilterService(ServiceCollectionFilterService filterService) {
        this.filterService = filterService;
    }
    
    // 审批缓存：id -> ServiceApproval
    private final Map<Long, ServiceApproval> approvalCache = new ConcurrentHashMap<>();
    private final AtomicLong approvalIdGenerator = new AtomicLong(1);
    
    // 服务到审批的映射：serviceKey -> approvalId
    private final Map<String, Long> serviceToApprovalIndex = new ConcurrentHashMap<>();
    
    /**
     * 创建审批申请
     */
    public ServiceApproval createApproval(ServiceApproval approval) {
        if (approval.getId() == null) {
            approval.setId(approvalIdGenerator.getAndIncrement());
        }
        
        approval.setStatus(ServiceApproval.ApprovalStatus.PENDING);
        approval.setCreatedAt(LocalDateTime.now());
        approval.setUpdatedAt(LocalDateTime.now());
        
        approvalCache.put(approval.getId(), approval);
        
        // 更新服务到审批的索引
        String serviceKey = buildServiceKey(approval.getServiceInterface(), approval.getServiceVersion());
        serviceToApprovalIndex.put(serviceKey, approval.getId());
        
        log.info("Created approval: id={}, service={}, applicant={}", 
                approval.getId(), serviceKey, approval.getApplicantName());
        
        return approval;
    }
    
    /**
     * 获取所有审批
     */
    public List<ServiceApproval> getAllApprovals() {
        return new ArrayList<>(approvalCache.values());
    }
    
    /**
     * 获取待审批列表
     */
    public List<ServiceApproval> getPendingApprovals() {
        return approvalCache.values().stream()
                .filter(a -> a.getStatus() == ServiceApproval.ApprovalStatus.PENDING)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取审批详情
     */
    public ServiceApproval getApproval(Long approvalId) {
        return approvalCache.get(approvalId);
    }
    
    /**
     * 根据服务获取审批
     */
    public ServiceApproval getApprovalByService(String serviceInterface, String version) {
        String serviceKey = buildServiceKey(serviceInterface, version);
        Long approvalId = serviceToApprovalIndex.get(serviceKey);
        if (approvalId != null) {
            return approvalCache.get(approvalId);
        }
        return null;
    }
    
    /**
     * 审批通过
     */
    public ServiceApproval approve(Long approvalId, Long approverId, String approverName, String comment) {
        ServiceApproval approval = approvalCache.get(approvalId);
        if (approval == null) {
            throw new IllegalArgumentException("审批不存在: " + approvalId);
        }
        
        if (approval.getStatus() != ServiceApproval.ApprovalStatus.PENDING) {
            throw new IllegalStateException("审批状态不正确，无法审批: " + approval.getStatus());
        }
        
        approval.setStatus(ServiceApproval.ApprovalStatus.APPROVED);
        approval.setApproverId(approverId);
        approval.setApproverName(approverName);
        approval.setComment(comment);
        approval.setApprovedAt(LocalDateTime.now());
        approval.setUpdatedAt(LocalDateTime.now());
        
        // 同步到过滤服务
        if (filterService != null) {
            filterService.markServiceAsApproved(
                    approval.getServiceInterface(),
                    approval.getServiceVersion(),
                    approvalId
            );
        }
        
        log.info("Approved service: id={}, service={}, approver={}", 
                approvalId, buildServiceKey(approval.getServiceInterface(), approval.getServiceVersion()), 
                approverName);
        
        return approval;
    }
    
    /**
     * 审批拒绝
     */
    public ServiceApproval reject(Long approvalId, Long approverId, String approverName, String comment) {
        ServiceApproval approval = approvalCache.get(approvalId);
        if (approval == null) {
            throw new IllegalArgumentException("审批不存在: " + approvalId);
        }
        
        if (approval.getStatus() != ServiceApproval.ApprovalStatus.PENDING) {
            throw new IllegalStateException("审批状态不正确，无法拒绝: " + approval.getStatus());
        }
        
        approval.setStatus(ServiceApproval.ApprovalStatus.REJECTED);
        approval.setApproverId(approverId);
        approval.setApproverName(approverName);
        approval.setComment(comment);
        approval.setApprovedAt(LocalDateTime.now());
        approval.setUpdatedAt(LocalDateTime.now());
        
        log.info("Rejected service: id={}, service={}, approver={}", 
                approvalId, buildServiceKey(approval.getServiceInterface(), approval.getServiceVersion()), 
                approverName);
        
        return approval;
    }
    
    /**
     * 取消审批
     */
    public ServiceApproval cancel(Long approvalId) {
        ServiceApproval approval = approvalCache.get(approvalId);
        if (approval == null) {
            throw new IllegalArgumentException("审批不存在: " + approvalId);
        }
        
        approval.setStatus(ServiceApproval.ApprovalStatus.CANCELLED);
        approval.setUpdatedAt(LocalDateTime.now());
        
        log.info("Cancelled approval: id={}", approvalId);
        
        return approval;
    }
    
    /**
     * 检查服务是否已审批
     */
    public boolean isServiceApproved(String serviceInterface, String version) {
        ServiceApproval approval = getApprovalByService(serviceInterface, version);
        return approval != null && approval.getStatus() == ServiceApproval.ApprovalStatus.APPROVED;
    }
    
    /**
     * 构建服务唯一标识
     */
    private String buildServiceKey(String serviceInterface, String version) {
        return String.format("%s:%s", serviceInterface, version != null ? version : "default");
    }
}

