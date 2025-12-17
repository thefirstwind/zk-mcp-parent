package com.zkinfo.controller;

import com.zkinfo.model.ServiceApproval;
import com.zkinfo.service.ServiceApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务审批管理API控制器
 * 
 * 提供审批申请的创建、查询、审批等REST API
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-01-15
 */
@Slf4j
@RestController
@RequestMapping("/api/approvals")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ApprovalController {
    
    private final ServiceApprovalService approvalService;
    
    /**
     * 创建审批申请
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createApproval(@RequestBody CreateApprovalRequest request) {
        try {
            ServiceApproval approval = ServiceApproval.builder()
                    .serviceInterface(request.getServiceInterface())
                    .serviceVersion(request.getServiceVersion())
                    .serviceGroup(request.getServiceGroup())
                    .projectId(request.getProjectId())
                    .applicantId(request.getApplicantId())
                    .applicantName(request.getApplicantName())
                    .reason(request.getReason())
                    .build();
            
            ServiceApproval created = approvalService.createApproval(approval);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", created.getId());
            response.put("serviceInterface", created.getServiceInterface());
            response.put("serviceVersion", created.getServiceVersion());
            response.put("status", created.getStatus());
            response.put("message", "审批申请创建成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("创建审批申请失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "创建审批申请失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 获取所有审批
     */
    @GetMapping
    public ResponseEntity<List<ServiceApproval>> getAllApprovals() {
        try {
            List<ServiceApproval> approvals = approvalService.getAllApprovals();
            return ResponseEntity.ok(approvals);
        } catch (Exception e) {
            log.error("获取审批列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取待审批列表
     */
    @GetMapping("/pending")
    public ResponseEntity<List<ServiceApproval>> getPendingApprovals() {
        try {
            List<ServiceApproval> approvals = approvalService.getPendingApprovals();
            return ResponseEntity.ok(approvals);
        } catch (Exception e) {
            log.error("获取待审批列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取审批详情
     */
    @GetMapping("/{approvalId}")
    public ResponseEntity<ServiceApproval> getApproval(@PathVariable Long approvalId) {
        try {
            ServiceApproval approval = approvalService.getApproval(approvalId);
            if (approval == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(approval);
        } catch (Exception e) {
            log.error("获取审批详情失败: {}", approvalId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 审批通过
     */
    @PutMapping("/{approvalId}/approve")
    public ResponseEntity<Map<String, Object>> approve(
            @PathVariable Long approvalId,
            @RequestBody ApproveRequest request) {
        try {
            ServiceApproval approval = approvalService.approve(
                    approvalId,
                    request.getApproverId(),
                    request.getApproverName(),
                    request.getComment()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", approval.getId());
            response.put("status", approval.getStatus());
            response.put("approverName", approval.getApproverName());
            response.put("message", "审批通过");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("审批失败: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(404).body(error);
        } catch (IllegalStateException e) {
            log.error("审批失败: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(400).body(error);
        } catch (Exception e) {
            log.error("审批失败: {}", approvalId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "审批失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 审批拒绝
     */
    @PutMapping("/{approvalId}/reject")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable Long approvalId,
            @RequestBody RejectRequest request) {
        try {
            ServiceApproval approval = approvalService.reject(
                    approvalId,
                    request.getApproverId(),
                    request.getApproverName(),
                    request.getComment()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", approval.getId());
            response.put("status", approval.getStatus());
            response.put("approverName", approval.getApproverName());
            response.put("message", "审批已拒绝");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("拒绝审批失败: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(404).body(error);
        } catch (IllegalStateException e) {
            log.error("拒绝审批失败: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(400).body(error);
        } catch (Exception e) {
            log.error("拒绝审批失败: {}", approvalId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "拒绝审批失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 取消审批
     */
    @PutMapping("/{approvalId}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long approvalId) {
        try {
            ServiceApproval approval = approvalService.cancel(approvalId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", approval.getId());
            response.put("status", approval.getStatus());
            response.put("message", "审批已取消");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("取消审批失败: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(404).body(error);
        } catch (Exception e) {
            log.error("取消审批失败: {}", approvalId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "取消审批失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 检查服务是否已审批
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkApproval(
            @RequestParam String serviceInterface,
            @RequestParam String serviceVersion) {
        try {
            boolean approved = approvalService.isServiceApproved(serviceInterface, serviceVersion);
            
            Map<String, Object> response = new HashMap<>();
            response.put("serviceInterface", serviceInterface);
            response.put("serviceVersion", serviceVersion);
            response.put("approved", approved);
            response.put("message", approved ? "服务已审批" : "服务未审批");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("检查审批状态失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "检查审批状态失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 创建审批申请请求
     */
    public static class CreateApprovalRequest {
        private String serviceInterface;
        private String serviceVersion;
        private String serviceGroup;
        private Long projectId;
        private Long applicantId;
        private String applicantName;
        private String reason;
        
        // Getters and Setters
        public String getServiceInterface() { return serviceInterface; }
        public void setServiceInterface(String serviceInterface) { this.serviceInterface = serviceInterface; }
        
        public String getServiceVersion() { return serviceVersion; }
        public void setServiceVersion(String serviceVersion) { this.serviceVersion = serviceVersion; }
        
        public String getServiceGroup() { return serviceGroup; }
        public void setServiceGroup(String serviceGroup) { this.serviceGroup = serviceGroup; }
        
        public Long getProjectId() { return projectId; }
        public void setProjectId(Long projectId) { this.projectId = projectId; }
        
        public Long getApplicantId() { return applicantId; }
        public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
        
        public String getApplicantName() { return applicantName; }
        public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    /**
     * 审批请求
     */
    public static class ApproveRequest {
        private Long approverId;
        private String approverName;
        private String comment;
        
        // Getters and Setters
        public Long getApproverId() { return approverId; }
        public void setApproverId(Long approverId) { this.approverId = approverId; }
        
        public String getApproverName() { return approverName; }
        public void setApproverName(String approverName) { this.approverName = approverName; }
        
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }
    
    /**
     * 拒绝请求
     */
    public static class RejectRequest {
        private Long approverId;
        private String approverName;
        private String comment;
        
        // Getters and Setters
        public Long getApproverId() { return approverId; }
        public void setApproverId(Long approverId) { this.approverId = approverId; }
        
        public String getApproverName() { return approverName; }
        public void setApproverName(String approverName) { this.approverName = approverName; }
        
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }
}

