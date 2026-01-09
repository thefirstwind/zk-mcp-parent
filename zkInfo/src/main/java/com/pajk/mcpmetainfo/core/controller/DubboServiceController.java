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

import java.time.LocalDateTime;
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
    
    @Autowired(required = false)
    private com.pajk.mcpmetainfo.core.service.ZooKeeperService zooKeeperService;
    
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
            // 权限校验
            dubboServiceDbService.approveService(id, approver, false, comment);
            return ResponseEntity.ok("Dubbo服务拒绝成功");
        } catch (Exception e) {
            log.error("拒绝Dubbo服务失败", e);
            return ResponseEntity.status(500).body("拒绝失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取待审批的Dubbo服务列表（分页）
     * 
     * @param page 页码（从1开始，默认1）
     * @param size 每页大小（默认10，最大100）
     * @return 待审批的Dubbo服务列表（分页结果）
     */
    @GetMapping("/pending")
    public ResponseEntity<PageResult<DubboServiceEntity>> getPendingServices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            // 权限校验
            PageResult<DubboServiceEntity> result = dubboServiceDbService.findByApprovalStatusWithPagination(
                DubboServiceEntity.ApprovalStatus.PENDING, page, size);
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            log.warn("权限不足: {}", e.getMessage());
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            log.error("获取待审批Dubbo服务列表失败", e);
            return ResponseEntity.status(500).build();
        }
    }
    
    /**
     * 获取已审批的Dubbo服务列表（分页）
     * 
     * @param page 页码（从1开始，默认1）
     * @param size 每页大小（默认10，最大100）
     * @return 已审批的Dubbo服务列表（分页结果）
     */
    @GetMapping("/approved")
    public ResponseEntity<PageResult<DubboServiceEntity>> getApprovedServices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            // 权限校验
            PageResult<DubboServiceEntity> result = dubboServiceDbService.findByApprovalStatusWithPagination(
                DubboServiceEntity.ApprovalStatus.APPROVED, page, size);
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            log.warn("权限不足: {}", e.getMessage());
            return ResponseEntity.status(403).build();
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
            // 权限校验
            // 使用数据库分页查询
            PageResult<DubboServiceEntity> result = dubboServiceDbService.findWithPagination(page, size);
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            log.warn("权限不足: {}", e.getMessage());
            return ResponseEntity.status(403).build();
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
            // 权限校验
            DubboServiceEntity service = dubboServiceDbService.findById(id);
            if (service != null) {
                return ResponseEntity.ok(service);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (SecurityException e) {
            log.warn("权限不足: {}", e.getMessage());
            return ResponseEntity.status(403).build();
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
            // 权限校验
            
            // 获取 interfaceName 和 version：优先使用参数中的，如果为空则从方法实体中获取
            String interfaceName = parameter.getInterfaceName();
            String version = parameter.getVersion();
            if (parameter.getMethodId() != null) {
                // 通过 methodId 查询方法实体获取 interfaceName 和 version
                DubboServiceMethodEntity method = dubboServiceMethodService.findMethodById(parameter.getMethodId());
                if (method != null) {
                    if (interfaceName == null || interfaceName.isEmpty()) {
                        interfaceName = method.getInterfaceName();
                        parameter.setInterfaceName(interfaceName);
                    }
                    if (version == null || version.isEmpty()) {
                        version = method.getVersion();
                        parameter.setVersion(version);
                    }
                } else {
                    log.warn("无法从方法ID {} 获取 interfaceName 和 version，请确保前端传入该字段", parameter.getMethodId());
                }
            }
            
            dubboServiceMethodService.saveMethodParameters(
                parameter.getMethodId(), 
                interfaceName != null ? interfaceName : "", 
                version,
                List.of(parameter));
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
            // 权限校验
            // 设置ID
            parameter.setId(id);
            
            // 如果 interfaceName 或 version 为空，从方法实体中获取
            String interfaceName = parameter.getInterfaceName();
            String version = parameter.getVersion();
            if (parameter.getMethodId() != null) {
                DubboServiceMethodEntity method = dubboServiceMethodService.findMethodById(parameter.getMethodId());
                if (method != null) {
                    if (interfaceName == null || interfaceName.isEmpty()) {
                        interfaceName = method.getInterfaceName();
                        parameter.setInterfaceName(interfaceName);
                    }
                    if (version == null || version.isEmpty()) {
                        version = method.getVersion();
                        parameter.setVersion(version);
                    }
                }
            }
            
            // 设置更新时间
            parameter.setUpdatedAt(LocalDateTime.now());
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
            // 权限校验
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
            // 权限校验
            // 获取参数详情
            DubboMethodParameterEntity parameter = dubboMethodParameterMapper.findById(id);
            if (parameter != null) {
                return ResponseEntity.ok(parameter);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (SecurityException e) {
            log.warn("权限不足: {}", e.getMessage());
            return ResponseEntity.status(403).build();
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
            // 权限校验
            // 获取方法的所有参数
            List<DubboMethodParameterEntity> parameters = dubboMethodParameterMapper.findByMethodId(methodId);
            return ResponseEntity.ok(parameters);
        } catch (SecurityException e) {
            log.warn("权限不足: {}", e.getMessage());
            return ResponseEntity.status(403).build();
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
            // 权限校验
            DubboServiceEntity service = dubboServiceDbService.findById(id);
            if (service == null) {
                return ResponseEntity.notFound().build();
            }
            
            // 更新服务状态为待审批
            service.setApprovalStatus(DubboServiceEntity.ApprovalStatus.PENDING);
            service.setApprover(submitter); // 使用审批人字段存储提交人信息
            service.setUpdatedAt(LocalDateTime.now());
            
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
            // 权限校验
            List<DubboServiceMethodEntity> methods = dubboServiceDbService.findMethodsByServiceId(serviceId);
            return ResponseEntity.ok(methods);
        } catch (SecurityException e) {
            log.warn("权限不足: {}", e.getMessage());
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            log.error("根据服务ID查询方法列表失败", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 更新方法描述（人工维护）
     *
     * @param methodId 方法ID
     * @param request 请求体
     * @return 更新结果
     */
    @PutMapping("/methods/{methodId}/description")
    public ResponseEntity<String> updateMethodDescription(
            @PathVariable Long methodId,
            @RequestBody MethodDescriptionUpdateRequest request) {
        try {
            // 权限校验
            String desc = request != null ? request.getMethodDescription() : null;
            dubboServiceMethodService.updateMethodDescription(methodId, desc);
            return ResponseEntity.ok("保存成功");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (SecurityException e) {
            log.warn("权限不足: {}", e.getMessage());
            return ResponseEntity.status(403).body("权限不足");
        } catch (Exception e) {
            log.error("更新方法描述失败: methodId={}", methodId, e);
            return ResponseEntity.status(500).body("保存失败: " + e.getMessage());
        }
    }

    public static class MethodDescriptionUpdateRequest {
        private String methodDescription;

        public String getMethodDescription() {
            return methodDescription;
        }

        public void setMethodDescription(String methodDescription) {
            this.methodDescription = methodDescription;
        }
    }
    
    /**
     * 同步节点（从ZooKeeper重新同步服务节点信息）
     * 
     * @param id 服务ID
     * @return 同步结果
     */
    @PostMapping("/{id}/sync-nodes")
    public ResponseEntity<String> syncNodes(@PathVariable Long id) {
        try {
            // 权限校验
            DubboServiceEntity service = dubboServiceDbService.findById(id);
            if (service == null) {
                return ResponseEntity.notFound().build();
            }
            
            // 检查服务状态，只有已审批的服务才能同步节点
            if (service.getApprovalStatus() != DubboServiceEntity.ApprovalStatus.APPROVED) {
                return ResponseEntity.badRequest().body("只有已审批的服务才能同步节点");
            }
            
            if (zooKeeperService == null || zooKeeperService.getClient() == null) {
                return ResponseEntity.status(500).body("ZooKeeper服务未初始化");
            }
            
            // 1. 从ZooKeeper读取节点信息并持久化到数据库
            int syncedCount = 0;
            
            try {
                String servicePath = buildServicePath(service);
                String providersPath = servicePath + "/providers";
                
                // 检查providers路径是否存在
                org.apache.curator.framework.CuratorFramework client = zooKeeperService.getClient();
                if (client.checkExists().forPath(providersPath) != null) {
                    // 获取所有Provider节点
                    java.util.List<String> providerNodes = client.getChildren().forPath(providersPath);
                    log.info("从ZooKeeper读取到 {} 个Provider节点: {} (ID: {})", 
                            providerNodes.size(), service.getInterfaceName(), id);
                    
                    for (String providerNode : providerNodes) {
                        try {
                            String providerPath = providersPath + "/" + providerNode;
                            String providerUrl = java.net.URLDecoder.decode(providerNode, java.nio.charset.StandardCharsets.UTF_8);
                            
                            // 解析Provider URL
                            com.pajk.mcpmetainfo.core.model.ProviderInfo providerInfo = parseProviderUrl(providerUrl, service.getInterfaceName());
                            if (providerInfo != null) {
                                providerInfo.setZkPath(providerPath);
                                
                                // 持久化到数据库
                                dubboServiceDbService.saveOrUpdateServiceWithNode(providerInfo);
                                syncedCount++;
                                log.debug("成功同步Provider节点: {}", providerPath);
                            }
                        } catch (Exception e) {
                            log.warn("同步Provider节点失败: {}", providerNode, e);
                        }
                    }
                } else {
                    log.warn("服务 {} 的providers路径不存在: {}", service.getInterfaceName(), providersPath);
                }
            } catch (Exception e) {
                log.error("从ZooKeeper读取节点信息失败: {} (ID: {})", service.getInterfaceName(), id, e);
                return ResponseEntity.status(500).body("读取ZooKeeper节点信息失败: " + e.getMessage());
            }
            
            log.info("成功同步服务节点: {} (ID: {}, 同步了 {} 个Provider)", 
                    service.getInterfaceName(), id, syncedCount);
            return ResponseEntity.ok(String.format("节点同步成功，共同步 %d 个Provider节点", syncedCount));
            
        } catch (Exception e) {
            log.error("同步节点失败", e);
            return ResponseEntity.status(500).body("同步节点失败: " + e.getMessage());
        }
    }
    
    /**
     * 解析Provider URL
     * 
     * @param providerUrl Provider URL字符串
     * @param serviceName 服务名称
     * @return ProviderInfo对象
     */
    private com.pajk.mcpmetainfo.core.model.ProviderInfo parseProviderUrl(String providerUrl, String serviceName) {
        try {
            // Dubbo Provider URL格式: dubbo://192.168.1.100:20880/com.example.Service?version=1.0.0&group=default&...
            if (!providerUrl.startsWith("dubbo://")) {
                log.warn("不支持的Provider URL格式: {}", providerUrl);
                return null;
            }
            
            com.pajk.mcpmetainfo.core.model.ProviderInfo provider = new com.pajk.mcpmetainfo.core.model.ProviderInfo();
            provider.setInterfaceName(serviceName);
            provider.setOnline(true);
            
            // 解析URL
            String[] parts = providerUrl.split("\\?");
            String baseUrl = parts[0];
            
            // 提取协议、地址和接口
            String[] urlParts = baseUrl.split("://");
            provider.setProtocol(urlParts[0]);
            
            String[] addressAndInterface = urlParts[1].split("/");
            provider.setAddress(addressAndInterface[0]);
            
            if (addressAndInterface.length > 1) {
                provider.setInterfaceName(addressAndInterface[1]);
            }
            
            // 解析参数
            java.util.Map<String, String> parameters = new java.util.HashMap<>();
            if (parts.length > 1) {
                String[] params = parts[1].split("&");
                for (String param : params) {
                    String[] kv = param.split("=");
                    if (kv.length == 2) {
                        String key = java.net.URLDecoder.decode(kv[0], java.nio.charset.StandardCharsets.UTF_8);
                        String value = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
                        
                        // 保存所有参数到 parameters Map
                        parameters.put(key, value);
                        
                        switch (key) {
                            case "version":
                                provider.setVersion(value);
                                break;
                            case "group":
                                provider.setGroup(value);
                                break;
                            case "application":
                                provider.setApplication(value);
                                break;
                            case "methods":
                                provider.setMethods(value);
                                break;
                        }
                    }
                }
            }
            
            // 如果 application 为空，尝试从 parameters 中获取
            if ((provider.getApplication() == null || provider.getApplication().isEmpty()) && !parameters.isEmpty()) {
                String app = parameters.get("application") != null ? parameters.get("application") :
                            parameters.get("dubbo.application.name") != null ? parameters.get("dubbo.application.name") :
                            parameters.get("application.name") != null ? parameters.get("application.name") :
                            null;
                if (app != null && !app.isEmpty()) {
                    provider.setApplication(app);
                }
            }
            
            // 保存所有参数
            provider.setParameters(parameters);
            
            return provider;
            
        } catch (Exception e) {
            log.error("解析Provider URL失败: {}", providerUrl, e);
            return null;
        }
    }
    
    /**
     * 下线服务（将服务状态设为已下线）
     * 
     * @param id 服务ID
     * @return 下线结果
     */
    @PostMapping("/{id}/offline")
    public ResponseEntity<String> offlineService(@PathVariable Long id) {
        try {
            // 权限校验
            DubboServiceEntity service = dubboServiceDbService.findById(id);
            if (service == null) {
                return ResponseEntity.notFound().build();
            }
            
            // 检查服务状态，只有已审批的服务才能下线
            if (service.getApprovalStatus() != DubboServiceEntity.ApprovalStatus.APPROVED) {
                return ResponseEntity.badRequest().body("只有已审批的服务才能下线");
            }
            
            // 更新服务状态为已下线
            service.setApprovalStatus(DubboServiceEntity.ApprovalStatus.OFFLINE);
            service.setUpdatedAt(LocalDateTime.now());
            dubboServiceMapper.update(service);
            
            log.info("成功下线服务: {} (ID: {})", service.getInterfaceName(), id);
            return ResponseEntity.ok("服务已下线");
        } catch (Exception e) {
            log.error("下线服务失败", e);
            return ResponseEntity.status(500).body("下线服务失败: " + e.getMessage());
        }
    }
    
    /**
     * 上线服务（将服务状态从已下线恢复为已审批）
     * 
     * @param id 服务ID
     * @return 上线结果
     */
    @PostMapping("/{id}/online")
    public ResponseEntity<String> onlineService(@PathVariable Long id) {
        try {
            // 权限校验
            DubboServiceEntity service = dubboServiceDbService.findById(id);
            if (service == null) {
                return ResponseEntity.notFound().build();
            }
            
            // 检查服务状态，只有已下线的服务才能上线
            if (service.getApprovalStatus() != DubboServiceEntity.ApprovalStatus.OFFLINE) {
                return ResponseEntity.badRequest().body("只有已下线的服务才能上线");
            }
            
            // 更新服务状态为已审批
            service.setApprovalStatus(DubboServiceEntity.ApprovalStatus.APPROVED);
            service.setUpdatedAt(LocalDateTime.now());
            dubboServiceMapper.update(service);
            
            // 构建服务路径
            String servicePath = buildServicePath(service);
            
            // 1. 添加ZooKeeper watcher（类似审批通过后的操作）
            try {
                zkWatcherSchedulerService.addWatcherForApprovedService(servicePath, service);
                log.info("成功为上线服务添加Watcher: {} (ID: {})", service.getInterfaceName(), id);
            } catch (Exception e) {
                log.warn("为上线服务添加Watcher失败，将在下次定时任务中重试: {} (ID: {})", 
                    service.getInterfaceName(), id, e);
            }
            
            // 2. 从ZooKeeper读取节点信息并同步到数据库
            if (zooKeeperService != null && zooKeeperService.getClient() != null) {
                try {
                    String providersPath = servicePath + "/providers";
                    
                    // 检查providers路径是否存在
                    org.apache.curator.framework.CuratorFramework client = zooKeeperService.getClient();
                    if (client.checkExists().forPath(providersPath) != null) {
                        // 获取所有Provider节点
                        java.util.List<String> providerNodes = client.getChildren().forPath(providersPath);
                        log.info("从ZooKeeper读取到 {} 个Provider节点用于上线: {} (ID: {})", 
                                providerNodes.size(), service.getInterfaceName(), id);
                        
                        for (String providerNode : providerNodes) {
                            try {
                                String providerPath = providersPath + "/" + providerNode;
                                String providerUrl = java.net.URLDecoder.decode(providerNode, java.nio.charset.StandardCharsets.UTF_8);
                                
                                // 解析Provider URL
                                com.pajk.mcpmetainfo.core.model.ProviderInfo providerInfo = parseProviderUrl(providerUrl, service.getInterfaceName());
                                if (providerInfo != null) {
                                    providerInfo.setZkPath(providerPath);
                                    
                                    // 持久化到数据库
                                    dubboServiceDbService.saveOrUpdateServiceWithNode(providerInfo);
                                    log.debug("成功同步Provider节点用于上线: {}", providerPath);
                                }
                            } catch (Exception e) {
                                log.warn("同步Provider节点失败: {}", providerNode, e);
                            }
                        }
                    } else {
                        log.warn("服务 {} 的providers路径不存在: {}", service.getInterfaceName(), providersPath);
                    }
                } catch (Exception e) {
                    log.error("从ZooKeeper读取节点信息失败: {} (ID: {})", service.getInterfaceName(), id, e);
                }
            } else {
                log.warn("ZooKeeper服务未初始化，跳过节点同步");
            }
            
            log.info("成功上线服务: {} (ID: {})", service.getInterfaceName(), id);
            return ResponseEntity.ok("服务已上线，状态已恢复为已审批");
        } catch (Exception e) {
            log.error("上线服务失败", e);
            return ResponseEntity.status(500).body("上线服务失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取当前用户信息
     * 
     * @return 当前用户信息（包含staffId和角色）
     */
}