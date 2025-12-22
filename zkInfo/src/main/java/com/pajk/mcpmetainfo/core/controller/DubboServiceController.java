package com.pajk.mcpmetainfo.core.controller;

import com.pajk.mcpmetainfo.persistence.mapper.DubboMethodParameterMapper;
import com.pajk.mcpmetainfo.persistence.mapper.DubboServiceMapper;
import com.pajk.mcpmetainfo.persistence.entity.DubboMethodParameterEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceMethodEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceNodeEntity;
import com.pajk.mcpmetainfo.core.service.DubboServiceDbService;
import com.pajk.mcpmetainfo.core.service.DubboServiceMethodService;
import com.pajk.mcpmetainfo.core.service.ZkWatcherSchedulerService;
import com.pajk.mcpmetainfo.core.model.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Dubbo服务管理控制器
 * 
 * 提供Dubbo服务审批、方法参数管理和审核流程的REST API接口。
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("/api/dubbo-services")
public class DubboServiceController {
    
    @Autowired
    private DubboServiceDbService dubboServiceDbService;
    
    @Autowired
    private DubboServiceMethodService dubboServiceMethodService;
    
    @Autowired
    private ZkWatcherSchedulerService zkWatcherSchedulerService;
    
    @Autowired
    private DubboMethodParameterMapper dubboMethodParameterMapper;
    
    @Autowired
    private DubboServiceMapper dubboServiceMapper;
    
    @Autowired
    private com.pajk.mcpmetainfo.core.config.ZooKeeperConfig zooKeeperConfig;
    
    /**
     * 审核Dubbo服务
     * 审核通过后添加ZooKeeper watcher
     * 
     * @param id 服务ID
     * @param approver 审批人
     * @param comment 审批意见
     * @return 审批结果
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<String> approveService(
            @PathVariable Long id,
            @RequestParam String approver,
            @RequestParam(required = false) String comment) {
        try {
            // 审批服务
            DubboServiceEntity approvedService = dubboServiceDbService.approveService(id, approver, true, comment);
            
            // 审批通过后，立即为该服务添加ZooKeeper watcher
            try {
                String servicePath = buildServicePath(approvedService);
                zkWatcherSchedulerService.addWatcherForApprovedService(servicePath, approvedService);
                log.info("成功为已审批服务添加Watcher: {} (ID: {})", approvedService.getInterfaceName(), id);
            } catch (Exception e) {
                log.warn("为已审批服务添加Watcher失败，将在下次定时任务中重试: {} (ID: {})", 
                    approvedService.getInterfaceName(), id, e);
            }
            
            return ResponseEntity.ok("Dubbo服务审批成功，已添加ZooKeeper watcher");
        } catch (Exception e) {
            log.error("审批Dubbo服务失败", e);
            return ResponseEntity.status(500).body("审批失败: " + e.getMessage());
        }
    }
    
    /**
     * 构建服务在ZooKeeper中的路径
     * 
     * @param service Dubbo服务实体
     * @return ZooKeeper中providers目录的路径
     */
    private String buildServicePath(DubboServiceEntity service) {
        StringBuilder path = new StringBuilder(zooKeeperConfig.getBasePath());
        
        if (service.getInterfaceName() != null && !service.getInterfaceName().isEmpty()) {
            path.append("/").append(service.getInterfaceName()).append("/providers");
        }
        
        return path.toString();
    }
    
    /**
     * 拒绝Dubbo服务
     * 
     * @param id 服务ID
     * @param approver 审批人
     * @param comment 拒绝意见
     * @return 拒绝结果
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<String> rejectService(
            @PathVariable Long id,
            @RequestParam String approver,
            @RequestParam(required = false) String comment) {
        try {
            dubboServiceDbService.approveService(id, approver, false, comment);
            return ResponseEntity.ok("Dubbo服务拒绝成功");
        } catch (Exception e) {
            log.error("拒绝Dubbo服务失败", e);
            return ResponseEntity.status(500).body("拒绝失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有待审批的Dubbo服务列表
     * 
     * @return 待审批的Dubbo服务列表
     */
    @GetMapping("/pending")
    public ResponseEntity<List<DubboServiceEntity>> getPendingServices() {
        try {
            List<DubboServiceEntity> pendingServices = dubboServiceDbService.findByApprovalStatus(
                DubboServiceEntity.ApprovalStatus.PENDING
            );
            return ResponseEntity.ok(pendingServices);
        } catch (Exception e) {
            log.error("获取待审批Dubbo服务列表失败", e);
            return ResponseEntity.status(500).build();
        }
    }
    
    /**
     * 获取所有已审批的Dubbo服务列表
     * 
     * @return 已审批的Dubbo服务列表
     */
    @GetMapping("/approved")
    public ResponseEntity<List<DubboServiceEntity>> getApprovedServices() {
        try {
            List<DubboServiceEntity> approvedServices = dubboServiceDbService.findByApprovalStatus(
                DubboServiceEntity.ApprovalStatus.APPROVED
            );
            return ResponseEntity.ok(approvedServices);
        } catch (Exception e) {
            log.error("获取已审批Dubbo服务列表失败", e);
            return ResponseEntity.status(500).build();
        }
    }
    
    /**
     * 分页查询Dubbo服务列表
     * 
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping
    public ResponseEntity<PageResult<DubboServiceEntity>> getServices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            // 使用数据库分页查询
            PageResult<DubboServiceEntity> result = dubboServiceDbService.findWithPagination(page, size);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("分页查询Dubbo服务列表失败", e);
            return ResponseEntity.status(500).build();
        }
    }
    
    /**
     * 根据ID获取Dubbo服务详情
     * 
     * @param id 服务ID
     * @return Dubbo服务详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<DubboServiceEntity> getServiceById(@PathVariable Long id) {
        try {
            DubboServiceEntity service = dubboServiceDbService.findById(id);
            if (service != null) {
                return ResponseEntity.ok(service);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取Dubbo服务详情失败", e);
            return ResponseEntity.status(500).build();
        }
    }
    
    // ==================== DubboMethodParameterEntity 增删改查接口 ====================
    
    /**
     * 创建方法参数
     * 
     * @param parameter 方法参数实体
     * @return 创建结果
     */
    @PostMapping("/parameters")
    public ResponseEntity<String> createParameter(@RequestBody DubboMethodParameterEntity parameter) {
        try {
            dubboServiceMethodService.saveMethodParameters(parameter.getMethodId(), List.of(parameter));
            return ResponseEntity.ok("方法参数创建成功");
        } catch (Exception e) {
            log.error("创建方法参数失败", e);
            return ResponseEntity.status(500).body("创建失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新方法参数
     * 
     * @param id 参数ID
     * @param parameter 方法参数实体
     * @return 更新结果
     */
    @PutMapping("/parameters/{id}")
    public ResponseEntity<String> updateParameter(
            @PathVariable Long id, 
            @RequestBody DubboMethodParameterEntity parameter) {
        try {
            // 设置ID
            parameter.setId(id);
            // 调用更新方法
            dubboMethodParameterMapper.update(parameter);
            return ResponseEntity.ok("方法参数更新成功");
        } catch (Exception e) {
            log.error("更新方法参数失败", e);
            return ResponseEntity.status(500).body("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除方法参数
     * 
     * @param id 参数ID
     * @return 删除结果
     */
    @DeleteMapping("/parameters/{id}")
    public ResponseEntity<String> deleteParameter(@PathVariable Long id) {
        try {
            // 调用删除方法
            dubboMethodParameterMapper.deleteById(id);
            return ResponseEntity.ok("方法参数删除成功");
        } catch (Exception e) {
            log.error("删除方法参数失败", e);
            return ResponseEntity.status(500).body("删除失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据ID获取方法参数详情
     * 
     * @param id 参数ID
     * @return 方法参数详情
     */
    @GetMapping("/parameters/{id}")
    public ResponseEntity<DubboMethodParameterEntity> getParameterById(@PathVariable Long id) {
        try {
            // 获取参数详情
            DubboMethodParameterEntity parameter = dubboMethodParameterMapper.findById(id);
            if (parameter != null) {
                return ResponseEntity.ok(parameter);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取方法参数详情失败", e);
            return ResponseEntity.status(500).build();
        }
    }
    
    /**
     * 根据方法ID获取所有参数
     * 
     * @param methodId 方法ID
     * @return 方法参数列表
     */
    @GetMapping("/methods/{methodId}/parameters")
    public ResponseEntity<List<DubboMethodParameterEntity>> getParametersByMethodId(@PathVariable Long methodId) {
        try {
            // 获取方法的所有参数
            List<DubboMethodParameterEntity> parameters = dubboMethodParameterMapper.findByMethodId(methodId);
            return ResponseEntity.ok(parameters);
        } catch (Exception e) {
            log.error("获取方法参数列表失败", e);
            return ResponseEntity.status(500).build();
        }
    }
    
    // ==================== 提交发起审核的接口 ====================
    
    /**
     * 提交Dubbo服务发起审核
     * 
     * @param id 服务ID
     * @param submitter 提交人
     * @return 提交结果
     */
    @PostMapping("/{id}/submit-for-review")
    public ResponseEntity<String> submitForReview(
            @PathVariable Long id,
            @RequestParam String submitter) {
        try {
            DubboServiceEntity service = dubboServiceDbService.findById(id);
            if (service == null) {
                return ResponseEntity.notFound().build();
            }
            
            // 更新服务状态为待审批
            service.setApprovalStatus(DubboServiceEntity.ApprovalStatus.PENDING);
            service.setApprover(submitter); // 使用审批人字段存储提交人信息
            service.setUpdatedAt(java.time.LocalDateTime.now());
            
            // 保存更新（直接使用mapper更新）
            dubboServiceMapper.update(service);
            
            return ResponseEntity.ok("Dubbo服务已提交审核");
        } catch (Exception e) {
            log.error("提交Dubbo服务审核失败", e);
            return ResponseEntity.status(500).body("提交审核失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据服务ID查询节点列表
     * 
     * @param serviceId 服务ID
     * @return 节点列表
     */
    @GetMapping("/{serviceId}/nodes")
    public ResponseEntity<List<DubboServiceNodeEntity>> getNodesByServiceId(@PathVariable Long serviceId) {
        try {
            List<DubboServiceNodeEntity> nodes = dubboServiceDbService.findNodesByServiceId(serviceId);
            return ResponseEntity.ok(nodes);
        } catch (Exception e) {
            log.error("根据服务ID查询节点列表失败", e);
            return ResponseEntity.status(500).build();
        }
    }
    
    /**
     * 根据服务ID查询方法列表
     * 
     * @param serviceId 服务ID
     * @return 方法列表
     */
    @GetMapping("/{serviceId}/methods")
    public ResponseEntity<List<DubboServiceMethodEntity>> getMethodsByServiceId(@PathVariable Long serviceId) {
        try {
            List<DubboServiceMethodEntity> methods = dubboServiceDbService.findMethodsByServiceId(serviceId);
            return ResponseEntity.ok(methods);
        } catch (Exception e) {
            log.error("根据服务ID查询方法列表失败", e);
            return ResponseEntity.status(500).build();
        }
    }
}