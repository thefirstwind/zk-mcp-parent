package com.zkinfo.controller;

import com.zkinfo.model.ApplicationInfo;
import com.zkinfo.model.McpResponse;
import com.zkinfo.model.ProviderInfo;
import com.zkinfo.service.DubboToMcpAutoRegistrationService;
import com.zkinfo.service.McpConverterService;
import com.zkinfo.service.McpExecutorService;
import com.zkinfo.service.NacosMcpRegistrationService;
import com.zkinfo.service.ProviderService;
import com.zkinfo.service.ZooKeeperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ZkInfo API 控制器
 * 
 * 提供 ZooKeeper 服务信息查询、监控和 MCP 转换的 REST API 接口。
 * 支持服务发现、提供者管理、心跳监控和 MCP 协议转换等功能。
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ApiController {
    
    @Autowired
    private ProviderService providerService;
    
    @Autowired
    private McpConverterService mcpConverterService;
    
    @Autowired
    private McpExecutorService mcpExecutorService;
    
    @Autowired
    private ZooKeeperService zooKeeperService;
    
    @Autowired(required = false)
    private DubboToMcpAutoRegistrationService dubboToMcpAutoRegistrationService;
    
    @Autowired(required = false)
    private NacosMcpRegistrationService nacosMcpRegistrationService;
    
    /**
     * 获取所有应用信息
     * 
     * @return 应用信息列表
     */
    @GetMapping("/applications")
    public ResponseEntity<List<ApplicationInfo>> getApplications() {
        try {
            List<ApplicationInfo> applications = providerService.getAllApplications();
            log.debug("获取应用信息列表，共 {} 个应用", applications.size());
            return ResponseEntity.ok(applications);
        } catch (Exception e) {
            log.error("获取应用信息失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 根据应用名获取服务提供者信息
     * 
     * @param application 应用名称
     * @return 服务提供者列表
     */
    @GetMapping("/applications/{applicationName}")
    public ResponseEntity<ApplicationInfo> getApplication(
            @PathVariable String applicationName) {
        try {
            ApplicationInfo appInfo = providerService.getApplicationByName(applicationName);
            if (appInfo == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(appInfo);
        } catch (Exception e) {
            log.error("获取应用信息失败: {}", applicationName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取应用的MCP格式数据
     */
    @GetMapping("/applications/{applicationName}/mcp")
    public ResponseEntity<McpResponse> getApplicationMcp(
            @PathVariable String applicationName) {
        try {
            McpResponse mcpResponse = mcpConverterService.convertApplicationToMcp(applicationName);
            return ResponseEntity.ok(mcpResponse);
        } catch (Exception e) {
            log.error("获取应用MCP数据失败: {}", applicationName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取所有应用的MCP格式数据
     */
    @GetMapping("/mcp")
    public ResponseEntity<List<McpResponse>> getAllApplicationsMcp() {
        try {
            List<McpResponse> mcpResponses = mcpConverterService.convertAllApplicationsToMcp();
            return ResponseEntity.ok(mcpResponses);
        } catch (Exception e) {
            log.error("获取所有应用MCP数据失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取所有接口列表
     */
    @GetMapping("/interfaces")
    public ResponseEntity<List<String>> getInterfaces() {
        try {
            List<String> interfaces = providerService.getAllInterfaces();
            return ResponseEntity.ok(interfaces);
        } catch (Exception e) {
            log.error("获取接口列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 根据接口名获取Provider列表
     */
    @GetMapping("/interfaces/{interfaceName}/providers")
    public ResponseEntity<List<ProviderInfo>> getProvidersByInterface(
            @PathVariable String interfaceName) {
        try {
            List<ProviderInfo> providers = providerService.getProvidersByInterface(interfaceName);
            return ResponseEntity.ok(providers);
        } catch (Exception e) {
            log.error("获取接口Provider列表失败: {}", interfaceName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 搜索Provider
     */
    @GetMapping("/providers/search")
    public ResponseEntity<List<ProviderInfo>> searchProviders(
            @RequestParam(required = false) String keyword) {
        try {
            List<ProviderInfo> providers = providerService.searchProviders(keyword);
            return ResponseEntity.ok(providers);
        } catch (Exception e) {
            log.error("搜索Provider失败: {}", keyword, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取所有Provider列表
     */
    @GetMapping("/providers")
    public ResponseEntity<List<ProviderInfo>> getAllProviders() {
        try {
            List<ProviderInfo> providers = providerService.getAllProviders();
            return ResponseEntity.ok(providers);
        } catch (Exception e) {
            log.error("获取所有Provider失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取已注册到Nacos的MCP服务列表
     * 从Nacos直接查询，包含所有类型的注册服务（Dubbo服务和虚拟项目）
     */
    @GetMapping("/registered-services")
    public ResponseEntity<Map<String, Object>> getRegisteredServices() {
        try {
            Map<String, Object> result = new HashMap<>();
            Set<String> allRegisteredServices = new HashSet<>();
            
            // 1. 从 DubboToMcpAutoRegistrationService 获取已注册的 Dubbo 服务
            if (dubboToMcpAutoRegistrationService != null) {
                Set<String> dubboServices = dubboToMcpAutoRegistrationService.getRegisteredServices();
                allRegisteredServices.addAll(dubboServices);
                log.debug("Found {} services from DubboToMcpAutoRegistrationService", dubboServices.size());
            }
            
            // 2. 从 Nacos 直接查询已注册的 MCP 服务（包含虚拟项目）
            if (nacosMcpRegistrationService != null) {
                try {
                    Set<String> nacosServices = nacosMcpRegistrationService.getRegisteredServicesFromNacos();
                    allRegisteredServices.addAll(nacosServices);
                    log.debug("Found {} services from Nacos", nacosServices.size());
                } catch (Exception e) {
                    log.warn("Failed to query services from Nacos: {}", e.getMessage());
                }
            }
            
            result.put("registeredServices", allRegisteredServices);
            result.put("count", allRegisteredServices.size());
            
            // 分别统计不同类型的服务
            Map<String, Integer> serviceTypes = new HashMap<>();
            if (dubboToMcpAutoRegistrationService != null) {
                serviceTypes.put("dubboServices", dubboToMcpAutoRegistrationService.getRegisteredServices().size());
            }
            if (nacosMcpRegistrationService != null) {
                try {
                    Set<String> nacosServices = nacosMcpRegistrationService.getRegisteredServicesFromNacos();
                    serviceTypes.put("nacosServices", nacosServices.size());
                } catch (Exception e) {
                    // 忽略错误
                }
            }
            result.put("serviceTypes", serviceTypes);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取已注册服务列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // 基础统计
            Map<String, Integer> onlineStats = providerService.getOnlineStats();
            stats.putAll(onlineStats);
            
            // 接口数量
            stats.put("totalInterfaces", providerService.getAllInterfaces().size());
            
            // ZooKeeper连接状态
            stats.put("zkConnected", zooKeeperService.isConnected());
            
            // MCP元数据
            McpResponse.McpMetadata mcpMetadata = mcpConverterService.getGlobalMcpMetadata();
            stats.put("mcpMetadata", mcpMetadata);
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 根据应用名获取 MCP 格式的服务信息
     * 
     * @param application 应用名称
     * @return MCP 响应对象
     */
    @GetMapping("/mcp/application/{application}")
    public ResponseEntity<McpResponse> getMcpByApplication(@PathVariable String application) {
        try {
            // 获取指定应用的MCP格式数据
            McpResponse mcpResponse = mcpConverterService.convertApplicationToMcp(application);
            return ResponseEntity.ok(mcpResponse);
        } catch (Exception e) {
            log.error("获取系统信息失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 调试接口：查看ZooKeeper树结构
     */
    @GetMapping("/debug/zk-tree")
    public ResponseEntity<Map<String, Object>> getZkTree() {
        try {
            Map<String, Object> result = new HashMap<>();
            
            // 获取基础路径下的所有子节点
            String basePath = "/dubbo";
            List<String> children = zooKeeperService.getClient().getChildren().forPath(basePath);
            result.put("basePath", basePath);
            result.put("children", children);
            
            // 遍历每个服务，查看其结构
            Map<String, Object> services = new HashMap<>();
            for (String service : children) {
                try {
                    String servicePath = basePath + "/" + service;
                    List<String> serviceChildren = zooKeeperService.getClient().getChildren().forPath(servicePath);
                    services.put(service, serviceChildren);
                    
                    // 如果有providers路径，查看其内容
                    if (serviceChildren.contains("providers")) {
                        String providersPath = servicePath + "/providers";
                        List<String> providers = zooKeeperService.getClient().getChildren().forPath(providersPath);
                        services.put(service + "_providers", providers);
                    }
                } catch (Exception e) {
                    services.put(service, "Error: " + e.getMessage());
                }
            }
            result.put("services", services);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取ZK树结构失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.ok(error);
        }
    }
    
    /**
     * 执行 MCP 工具调用
     * 
     * @param request MCP 调用请求
     * @return 调用结果
     */
    @PostMapping("/mcp/call")
    public ResponseEntity<Map<String, Object>> executeMcpCall(@RequestBody McpCallRequest request) {
        try {
            log.info("接收到 MCP 调用请求: {}", request.getToolName());
            
            // 参数验证
            if (request.getToolName() == null || request.getToolName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "工具名称不能为空"));
            }
            
            // 执行调用
            McpExecutorService.McpCallResult result = mcpExecutorService.executeToolCallSync(
                    request.getToolName(),
                    request.getArgs(),
                    request.getTimeout()
            );
            
            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("executionTime", System.currentTimeMillis() - result.getExecutionTime());
            
            if (result.isSuccess()) {
                response.put("result", result.getResult());
            } else {
                response.put("error", result.getErrorMessage());
                if (result.getException() != null) {
                    response.put("exceptionType", result.getException().getClass().getSimpleName());
                }
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("MCP 调用执行失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "调用执行失败: " + e.getMessage());
            error.put("exceptionType", e.getClass().getSimpleName());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 异步执行 MCP 工具调用
     */
    @PostMapping("/mcp/call-async")
    public ResponseEntity<Map<String, Object>> executeMcpCallAsync(@RequestBody McpCallRequest request) {
        try {
            log.info("接收到异步 MCP 调用请求: {}", request.getToolName());
            
            // 参数验证
            if (request.getToolName() == null || request.getToolName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "工具名称不能为空"));
            }
            
            // 异步执行调用
            mcpExecutorService.executeToolCall(
                    request.getToolName(),
                    request.getArgs(),
                    request.getTimeout()
            ).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("异步 MCP 调用失败: {}", request.getToolName(), throwable);
                } else {
                    log.info("异步 MCP 调用完成: {} -> {}", request.getToolName(), result.isSuccess());
                }
            });
            
            // 立即返回接受状态
            Map<String, Object> response = new HashMap<>();
            response.put("accepted", true);
            response.put("message", "调用请求已接受，正在异步执行");
            response.put("toolName", request.getToolName());
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            log.error("异步 MCP 调用提交失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("accepted", false);
            error.put("error", "调用提交失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * MCP 调用请求实体
     */
    public static class McpCallRequest {
        private String toolName;
        private Object[] args;
        private Map<String, Object> arguments;
        private Integer timeout;
        
        // Constructors
        public McpCallRequest() {}
        
        public McpCallRequest(String toolName, Object[] args, Integer timeout) {
            this.toolName = toolName;
            this.args = args;
            this.timeout = timeout;
        }
        
        // Getters and Setters
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        
        public Object[] getArgs() { 
            // 如果args为空，尝试从arguments中提取
            if (args == null && arguments != null) {
                if (arguments.containsKey("args")) {
                    Object argsObj = arguments.get("args");
                    if (argsObj instanceof List) {
                        List<?> argsList = (List<?>) argsObj;
                        return argsList.toArray();
                    } else if (argsObj instanceof Object[]) {
                        return (Object[]) argsObj;
                    }
                } else {
                    // 如果arguments不包含args键，将所有值作为参数数组
                    return arguments.values().toArray();
                }
            }
            return args; 
        }
        public void setArgs(Object[] args) { this.args = args; }
        
        public Map<String, Object> getArguments() { return arguments; }
        public void setArguments(Map<String, Object> arguments) { this.arguments = arguments; }
        
        public Integer getTimeout() { return timeout; }
        public void setTimeout(Integer timeout) { this.timeout = timeout; }
    }
}

