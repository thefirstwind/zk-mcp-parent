package com.zkinfo.controller;

import com.zkinfo.mapper.DubboMethodParameterMapper;
import com.zkinfo.mapper.DubboServiceMapper;
import com.zkinfo.model.DubboMethodParameterEntity;
import com.zkinfo.model.DubboServiceEntity;
import com.zkinfo.model.DubboServiceMethodEntity;
import com.zkinfo.model.DubboServiceNodeEntity;
import com.zkinfo.service.DubboServiceDbService;
import com.zkinfo.service.DubboServiceMethodService;
import com.zkinfo.service.ZkWatcherSchedulerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
    
    /**
     * 审核Dubbo服务
     * 审核通过后更新节点的IP信息并添加ZooKeeper watcher
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
            
            // TODO: 更新节点的IP信息（需要具体实现）
            // 这里可以调用相应的服务方法来更新节点IP信息
            
            // 添加ZooKeeper watcher（参考ZkWatcherSchedulerService）
            // 注意：实际的watcher添加逻辑通常由定时任务处理，这里可以根据需求决定是否立即触发
            
            return ResponseEntity.ok("Dubbo服务审批成功");
        } catch (Exception e) {
            log.error("审批Dubbo服务失败", e);
            return ResponseEntity.status(500).body("审批失败: " + e.getMessage());
        }
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
     * @return Dubbo服务列表
     */
    @GetMapping
    public ResponseEntity<List<DubboServiceEntity>> getServices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            // 参数校验
            if (page < 1) page = 1;
            if (size < 1) size = 10;
            if (size > 100) size = 100; // 限制最大页面大小
            
            // 计算偏移量
            int offset = (page - 1) * size;
            
            // 获取所有服务（在实际项目中，应该在Mapper中实现分页查询）
            List<DubboServiceEntity> allServices = dubboServiceDbService.findAll();
            
            // 手动分页
            int totalCount = allServices.size();
            int fromIndex = Math.min(offset, totalCount);
            int toIndex = Math.min(offset + size, totalCount);
            
            List<DubboServiceEntity> paginatedServices = 
                fromIndex < totalCount ? allServices.subList(fromIndex, toIndex) : new ArrayList<>();
            
            return ResponseEntity.ok(paginatedServices);
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