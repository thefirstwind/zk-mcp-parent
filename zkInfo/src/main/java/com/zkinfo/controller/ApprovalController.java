package com.zkinfo.controller;

import com.zkinfo.model.ProviderInfoEntity;
import com.zkinfo.service.ProviderInfoDbService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审批管理控制器
 * 
 * 提供审批相关的REST API接口，支持对Provider信息进行审批操作。
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("/api/approval")
public class ApprovalController {
    
    @Autowired
    private ProviderInfoDbService providerInfoDbService;
    
    /**
     * 获取所有待审批的Provider列表
     * 
     * @return 待审批的Provider列表
     */
    @GetMapping("/pending")
    public ResponseEntity<List<ProviderInfoEntity>> getPendingProviders() {
        try {
            List<ProviderInfoEntity> pendingProviders = providerInfoDbService.findByApprovalStatus("PENDING");
            return ResponseEntity.ok(pendingProviders);
        } catch (Exception e) {
            log.error("获取待审批Provider列表失败", e);
            return ResponseEntity.status(500).build();
        }
    }
    
    /**
     * 获取所有已审批的Provider列表
     * 
     * @return 已审批的Provider列表
     */
    @GetMapping("/approved")
    public ResponseEntity<List<ProviderInfoEntity>> getApprovedProviders() {
        try {
            List<ProviderInfoEntity> approvedProviders = providerInfoDbService.findApprovedProviders();
            return ResponseEntity.ok(approvedProviders);
        } catch (Exception e) {
            log.error("获取已审批Provider列表失败", e);
            return ResponseEntity.status(500).build();
        }
    }
    
    /**
     * 审批Provider
     * 
     * @param id Provider ID
     * @param approver 审批人
     * @param approvalComment 审批意见
     * @return 审批结果
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<String> approveProvider(
            @PathVariable Long id,
            @RequestParam String approver,
            @RequestParam(required = false) String approvalComment) {
        try {
            providerInfoDbService.approveProvider(id, approver, true, approvalComment);
            return ResponseEntity.ok("Provider审批成功");
        } catch (Exception e) {
            log.error("审批Provider失败", e);
            return ResponseEntity.status(500).body("审批失败: " + e.getMessage());
        }
    }
    
    /**
     * 拒绝Provider
     * 
     * @param id Provider ID
     * @param approver 审批人
     * @param rejectionComment 拒绝意见
     * @return 拒绝结果
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<String> rejectProvider(
            @PathVariable Long id,
            @RequestParam String approver,
            @RequestParam(required = false) String rejectionComment) {
        try {
            providerInfoDbService.approveProvider(id, approver, false, rejectionComment);
            return ResponseEntity.ok("Provider拒绝成功");
        } catch (Exception e) {
            log.error("拒绝Provider失败", e);
            return ResponseEntity.status(500).body("拒绝失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据ID获取Provider详情
     * 
     * @param id Provider ID
     * @return Provider详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProviderInfoEntity> getProviderById(@PathVariable Long id) {
        try {
            ProviderInfoEntity provider = providerInfoDbService.findById(id);
            if (provider != null) {
                return ResponseEntity.ok(provider);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取Provider详情失败", e);
            return ResponseEntity.status(500).build();
        }
    }
}