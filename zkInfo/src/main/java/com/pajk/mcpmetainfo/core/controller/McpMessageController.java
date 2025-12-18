package com.pajk.mcpmetainfo.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpmetainfo.core.service.EndpointResolver;
import com.pajk.mcpmetainfo.core.service.McpExecutorService;
import com.pajk.mcpmetainfo.core.service.McpSessionManager;
import com.pajk.mcpmetainfo.core.service.VirtualProjectRegistrationService;
import com.pajk.mcpmetainfo.core.service.ProjectManagementService;
import com.pajk.mcpmetainfo.core.model.ProjectService;
import com.pajk.mcpmetainfo.core.service.ProviderService;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import com.pajk.mcpmetainfo.core.util.McpToolSchemaGenerator;
import com.pajk.mcpmetainfo.core.service.McpResourcesService;
import com.pajk.mcpmetainfo.core.service.McpPromptsService;
import com.pajk.mcpmetainfo.core.service.VirtualProjectService;
import com.pajk.mcpmetainfo.core.mcp.McpProtocol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP 消息处理 Controller（WebMVC 模式）
 * 处理通过 POST /mcp/message 发送的 MCP 消息
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class McpMessageController {
    
    private final McpSessionManager sessionManager;
    private final McpExecutorService mcpExecutorService;
    private final ObjectMapper objectMapper;
    private final VirtualProjectRegistrationService virtualProjectRegistrationService;
    private final EndpointResolver endpointResolver;
    private final ProjectManagementService projectManagementService;
    private final ProviderService providerService;
    private final McpResourcesService mcpResourcesService;
    private final McpPromptsService mcpPromptsService;
    private final McpToolSchemaGenerator mcpToolSchemaGenerator;
    private final VirtualProjectService virtualProjectService;
    
    /**
     * 处理 MCP 消息：POST /mcp/message?sessionId=xxx
     */
    @PostMapping(value = "/message", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleMessage(
            @RequestParam(required = false) String sessionId,
            @RequestBody Map<String, Object> request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Service-Name", required = false) String serviceNameHeader) {
        
        log.info("📨 MCP message request: sessionId={}, method={}, X-Service-Name={}", 
                sessionId, request.get("method"), serviceNameHeader);
        
        // 获取 endpoint（参考 mcp-router-v3 的 session 管理）
        String endpoint = null;
        
        // 1. 如果 sessionId 存在，首先尝试从 session 中获取 endpoint
        if (sessionId != null && !sessionId.isEmpty()) {
            endpoint = sessionManager.getEndpointForSession(sessionId);
            
            // 2. 如果找不到 endpoint，尝试从 session 中获取 serviceName
            if (endpoint == null) {
                String serviceName = sessionManager.getServiceName(sessionId);
                if (serviceName != null && !serviceName.isEmpty()) {
                    // 如果 serviceName 以 virtual- 开头，去掉前缀
                    String tryEndpoint = serviceName;
                    if (serviceName.startsWith("virtual-")) {
                        tryEndpoint = serviceName.substring("virtual-".length());
                        log.debug("🔍 ServiceName '{}' starts with virtual-, using '{}' for endpoint lookup", serviceName, tryEndpoint);
                    } else if (serviceName.startsWith("mcp-")) {
                        // 向后兼容：如果以 mcp- 开头，也去掉前缀
                        tryEndpoint = serviceName.substring("mcp-".length());
                        log.debug("🔍 ServiceName '{}' starts with mcp-, using '{}' for endpoint lookup", serviceName, tryEndpoint);
                    }
                    java.util.Optional<EndpointResolver.EndpointInfo> endpointInfoOpt = 
                            endpointResolver.resolveEndpoint(tryEndpoint);
                    if (endpointInfoOpt.isPresent()) {
                        endpoint = tryEndpoint;
                        log.info("📝 Using endpoint from session serviceName: {} -> {}", serviceName, endpoint);
                    } else {
                        endpoint = tryEndpoint;
                        log.info("📝 Using serviceName as endpoint: {}", endpoint);
                    }
                }
            }
        }
        
        // 3. 如果 endpoint 仍然为 null，尝试从请求头或请求中推断 endpoint（RESTful 调用场景）
        if (endpoint == null) {
            log.debug("⚠️ Endpoint not found in session, trying to infer from request");
            
            // 1. 尝试从请求头获取服务名
            if (serviceNameHeader != null && !serviceNameHeader.isEmpty()) {
                String tryEndpoint = serviceNameHeader;
                if (serviceNameHeader.startsWith("virtual-")) {
                    tryEndpoint = serviceNameHeader.substring("virtual-".length());
                    log.debug("🔍 X-Service-Name '{}' starts with virtual-, using '{}' for endpoint lookup", serviceNameHeader, tryEndpoint);
                } else if (serviceNameHeader.startsWith("mcp-")) {
                    // 向后兼容：如果以 mcp- 开头，也去掉前缀
                    tryEndpoint = serviceNameHeader.substring("mcp-".length());
                    log.debug("🔍 X-Service-Name '{}' starts with mcp-, using '{}' for endpoint lookup", serviceNameHeader, tryEndpoint);
                }
                java.util.Optional<EndpointResolver.EndpointInfo> endpointInfoOpt = 
                        endpointResolver.resolveEndpoint(tryEndpoint);
                if (endpointInfoOpt.isPresent()) {
                    endpoint = tryEndpoint;
                    log.info("📝 Using endpoint from X-Service-Name header: {} -> {}", serviceNameHeader, endpoint);
                } else {
                    endpoint = tryEndpoint;
                    log.info("📝 Using X-Service-Name as endpoint: {}", endpoint);
                }
            } else {
                // 2. 尝试从请求参数中获取（如果 mcp-router-v3 传递了服务名）
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) request.get("params");
                if (params != null && params.containsKey("serviceName")) {
                    String serviceName = (String) params.get("serviceName");
                    String tryEndpoint = serviceName;
                    if (serviceName.startsWith("virtual-")) {
                        tryEndpoint = serviceName.substring("virtual-".length());
                        log.debug("🔍 Request param serviceName '{}' starts with virtual-, using '{}' for endpoint lookup", serviceName, tryEndpoint);
                    } else if (serviceName.startsWith("mcp-")) {
                        // 向后兼容：如果以 mcp- 开头，也去掉前缀
                        tryEndpoint = serviceName.substring("mcp-".length());
                        log.debug("🔍 Request param serviceName '{}' starts with mcp-, using '{}' for endpoint lookup", serviceName, tryEndpoint);
                    }
                    java.util.Optional<EndpointResolver.EndpointInfo> endpointInfoOpt = 
                            endpointResolver.resolveEndpoint(tryEndpoint);
                    if (endpointInfoOpt.isPresent()) {
                        endpoint = tryEndpoint;
                        log.info("📝 Using endpoint from request params: {} -> {}", serviceName, endpoint);
                    } else {
                        endpoint = tryEndpoint;
                        log.info("📝 Using request param serviceName as endpoint: {}", endpoint);
                    }
                } else {
                    // 3. 尝试从所有虚拟项目中查找（如果只有一个虚拟项目，使用它）
                    List<VirtualProjectService.VirtualProjectInfo> virtualProjects = virtualProjectService.getAllVirtualProjects();
                    if (virtualProjects != null && virtualProjects.size() == 1) {
                        VirtualProjectService.VirtualProjectInfo vp = virtualProjects.get(0);
                        if (vp.getEndpoint() != null) {
                            endpoint = vp.getEndpoint().getEndpointName();
                            log.info("📝 Using single virtual project endpoint: {}", endpoint);
                        }
                    } else if (virtualProjects != null && virtualProjects.size() > 1) {
                        log.warn("⚠️ Multiple virtual projects found ({}), cannot auto-select endpoint. " +
                                "Please specify endpoint via X-Service-Name header or session.", virtualProjects.size());
                    }
                }
            }
        }
        
        // 如果 endpoint 仍然为 null，记录警告但继续处理（某些方法可能不需要 endpoint）
        if (endpoint == null) {
            log.warn("⚠️ Endpoint is still null after all attempts, method: {}", request.get("method"));
        }
        
        // 获取 SseEmitter（WebMVC 模式）
        // 注意：对于 SSE 连接，emitter 应该在连接建立时就已经注册
        // 如果找不到，可能是：
        // 1. SSE 连接还未完全建立（需要等待）
        // 2. sessionId 不匹配
        // 3. 直接 HTTP 调用（没有 SSE 连接）
        SseEmitter emitter = null;
        if (sessionId != null && !sessionId.isEmpty()) {
            emitter = sessionManager.getSseEmitter(sessionId);
            
            // 如果找不到 emitter，立即返回（不阻塞等待）
            // SSE 连接建立是异步的，如果连接还未建立，应该返回错误而不是等待
            if (emitter == null) {
                log.debug("⚠️ SSE emitter not found for session: {}, treating as direct HTTP call", sessionId);
            }
        }
        
        boolean isDirectHttpCall = (emitter == null);
        
        if (emitter == null) {
            log.info("📨 Direct HTTP call (no SSE emitter): method={}, sessionId={}, endpoint={}", 
                    request.get("method"), sessionId, endpoint);
            // 对于直接 HTTP 调用（如 mcp-router-v3 的 RESTful 接口），直接返回 JSON 响应
        } else {
            log.info("✅ SSE emitter found for session: {}", sessionId);
        }
        
        // 处理消息
        String method = (String) request.get("method");
        String id = request.get("id") != null ? request.get("id").toString() : null;
        
        try {
            // 如果是直接 HTTP 调用（没有 SSE emitter），直接返回 JSON 响应
            if (isDirectHttpCall) {
                log.info("📨 Direct HTTP call: method={}, sessionId={}, endpoint={}", method, sessionId, endpoint);
                return handleRestfulMessage(request, method, id, endpoint, sessionId);
            }
            
            // SSE 模式：通过 SSE 发送响应
            log.info("📨 Processing SSE message: method={}, sessionId={}, endpoint={}, id={}", 
                    method, sessionId, endpoint, id);
            if ("initialize".equals(method)) {
                handleInitialize(emitter, request, id, sessionId);
            } else if ("prompts/list".equals(method)) {
                handlePromptsList(emitter, endpoint, id, sessionId);
            } else if ("tools/list".equals(method)) {
                log.info("🔧 Calling handleToolsList: endpoint={}, id={}, sessionId={}", endpoint, id, sessionId);
                handleToolsList(emitter, endpoint, id, sessionId);
                log.info("✅ handleToolsList completed: endpoint={}, sessionId={}", endpoint, sessionId);
            } else if ("tools/call".equals(method)) {
                handleToolCall(emitter, request, endpoint, id, sessionId);
            } else if ("resources/list".equals(method)) {
                handleResourcesList(emitter, endpoint, id, sessionId);
            } else {
                sendErrorResponseSafe(emitter, id, -32601, "Method not found: " + method, sessionId);
            }
            
            // 返回 202 Accepted（响应通过 SSE 发送）
            return ResponseEntity.accepted()
                    .body(Map.of("status", "accepted", 
                            "message", "Request accepted, response will be sent via SSE"));
            
        } catch (IOException e) {
            // 客户端断开连接（Broken pipe），这是正常情况，不需要记录错误
            if (e.getMessage() != null && e.getMessage().contains("Broken pipe")) {
                log.debug("ℹ️ Client disconnected (broken pipe) for session: {}, method={}", sessionId, method);
            } else {
                log.warn("⚠️ IO error handling MCP message: sessionId={}, method={}, error={}", 
                        sessionId, method, e.getMessage());
            }
            return ResponseEntity.accepted()
                    .body(Map.of("status", "accepted", 
                            "message", "Request accepted, but client disconnected"));
        } catch (Exception e) {
            log.error("❌ Error handling MCP message: sessionId={}, method={}", sessionId, method, e);
            sendErrorResponseSafe(emitter, id, -32603, "Internal error: " + e.getMessage(), sessionId);
            return ResponseEntity.accepted()
                    .body(Map.of("status", "accepted", 
                            "message", "Request accepted, error response will be sent via SSE"));
        }
    }
    
    /**
     * 处理 initialize 请求
     * 关键：必须立即响应，mcp-router-v3 的初始化超时只有 200ms
     */
    private void handleInitialize(SseEmitter emitter, Map<String, Object> request, String id, String sessionId) throws IOException {
        log.info("📨 Handling initialize request: sessionId={}, id={}", sessionId, id);
        
        // 获取 endpoint 以确定服务名称（sessionId 可能为 null，需要处理）
        String endpoint = sessionId != null ? sessionManager.getEndpointForSession(sessionId) : null;
        String serviceName = endpoint != null ? endpoint : "zkInfo-MCP-Server";
        
        // 如果 endpoint 是 MCP 服务名称，使用它作为 serverInfo.name
        if (endpoint != null && endpoint.startsWith("zk-mcp-")) {
            serviceName = endpoint;
        }
        
        // 构建响应（使用 LinkedHashMap 确保顺序）
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("protocolVersion", "2024-11-05");
        
        // 参考 mcp-router-v3 的实现，设置完整的 capabilities 以触发客户端自动调用 tools/list、resources/list、prompts/list
        Map<String, Object> capabilities = new java.util.LinkedHashMap<>();
        
        // 设置 tools 能力（listChanged = true 会触发客户端自动调用 tools/list）
        Map<String, Object> toolsCap = new java.util.LinkedHashMap<>();
        toolsCap.put("listChanged", true);
        capabilities.put("tools", toolsCap);
        
        // 设置 resources 能力（listChanged = true 会触发客户端自动调用 resources/list）
        Map<String, Object> resourcesCap = new java.util.LinkedHashMap<>();
        resourcesCap.put("subscribe", false);
        resourcesCap.put("listChanged", true);
        capabilities.put("resources", resourcesCap);
        
        // 设置 prompts 能力（listChanged = true 会触发客户端自动调用 prompts/list）
        Map<String, Object> promptsCap = new java.util.LinkedHashMap<>();
        promptsCap.put("listChanged", true);
        capabilities.put("prompts", promptsCap);
        
        result.put("capabilities", capabilities);
        
        Map<String, Object> serverInfo = new java.util.LinkedHashMap<>();
        serverInfo.put("name", serviceName);
        serverInfo.put("version", "1.0.0");
        result.put("serverInfo", serverInfo);
        
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id != null ? id : "null");
        response.put("result", result);
        
        String responseJson = objectMapper.writeValueAsString(response);
        
        // 立即发送响应（不等待）
        try {
            emitter.send(SseEmitter.event()
                    .name("message")  // 设置 event type 以兼容 WebFluxSseClientTransport
                    .data(responseJson));
            log.info("✅ Initialize response sent via SSE: sessionId={}, id={}, serviceName={}", 
                    sessionId, id, serviceName);
        } catch (IOException e) {
            log.error("❌ Failed to send initialize response via SSE: sessionId={}, id={}", 
                    sessionId, id, e);
            throw e;
        }
    }
    
    /**
     * 获取 endpoint 的工具列表（内部方法，供 SSE 和 RESTful 调用复用）
     */
    private List<Map<String, Object>> getToolsForEndpointInternal(String endpoint) {
        log.info("📨 Getting tools for endpoint: {}", endpoint);
        
        // 解析 endpoint 获取工具列表
        List<Map<String, Object>> tools = new ArrayList<>();
        
        try {
            // 如果 endpoint 为 null，尝试从所有虚拟项目中查找
            if (endpoint == null || endpoint.isEmpty()) {
                log.warn("⚠️ Endpoint is null or empty, trying to find from virtual projects");
                List<VirtualProjectService.VirtualProjectInfo> virtualProjects = virtualProjectService.getAllVirtualProjects();
                if (virtualProjects != null && virtualProjects.size() == 1) {
                    VirtualProjectService.VirtualProjectInfo vp = virtualProjects.get(0);
                    if (vp.getEndpoint() != null) {
                        endpoint = vp.getEndpoint().getEndpointName();
                        log.info("📝 Using single virtual project endpoint: {}", endpoint);
                    }
                } else if (virtualProjects != null && virtualProjects.size() > 1) {
                    log.warn("⚠️ Multiple virtual projects found ({}), cannot auto-select endpoint", virtualProjects.size());
                }
            }
            
            // 如果 endpoint 以 mcp- 开头，去掉前缀再解析（因为注册时不添加 mcp- 前缀）
            String actualEndpoint = endpoint;
            if (endpoint != null && endpoint.startsWith("mcp-")) {
                actualEndpoint = endpoint.substring("mcp-".length());
                log.info("📝 Endpoint '{}' starts with mcp-, using '{}' for lookup", endpoint, actualEndpoint);
            }
            
            java.util.Optional<EndpointResolver.EndpointInfo> endpointInfoOpt = endpointResolver.resolveEndpoint(actualEndpoint);
            if (endpointInfoOpt.isPresent()) {
                EndpointResolver.EndpointInfo endpointInfo = endpointInfoOpt.get();
                if (endpointInfo.isVirtualProject()) {
                    // 虚拟项目：从 VirtualProjectRegistrationService 获取工具
                    Long projectId = endpointInfo.getProjectId();
                    if (projectId != null) {
                        tools = virtualProjectRegistrationService.getVirtualProjectTools(projectId);
                        log.info("✅ Got {} tools from virtual project (projectId: {})", tools.size(), projectId);
                    } else {
                        log.warn("⚠️ Virtual project endpoint found but projectId is null: {}", actualEndpoint);
                    }
                } else {
                    // 实际项目：从 ProviderService 获取工具
                    Long projectId = endpointInfo.getProjectId();
                    if (projectId != null) {
                        // 获取项目关联的服务
                        List<ProjectService> projectServices = 
                                projectManagementService.getProjectServices(projectId);
                        
                        log.info("📋 Found {} services in project (projectId: {})", projectServices.size(), projectId);
                        
                        // 从每个服务获取工具
                        for (ProjectService projectService : projectServices) {
                            String serviceInterface = projectService.getServiceInterface();
                            String version = projectService.getServiceVersion();
                            String group = projectService.getServiceGroup();
                            
                            log.debug("Processing service: {}:{}:{}", serviceInterface, version, group);
                            
                            // 从 ProviderService 获取该服务的 Provider，然后生成工具
                            try {
                                List<ProviderInfo> providers = providerService.getProvidersByInterface(serviceInterface);
                                
                                // 过滤版本和分组
                                providers = providers.stream()
                                        .filter(p -> (version == null || version.equals(p.getVersion())) &&
                                                (group == null || group.equals(p.getGroup())))
                                        .collect(java.util.stream.Collectors.toList());
                                
                                log.debug("Found {} providers for service {}:{}:{}", providers.size(), serviceInterface, version, group);
                                
                                // 生成工具（复用 VirtualProjectRegistrationService 的逻辑）
                                if (!providers.isEmpty()) {
                                    for (ProviderInfo provider : providers) {
                                        if (provider.getMethods() != null && !provider.getMethods().isEmpty()) {
                                            String[] methods = provider.getMethods().split(",");
                                            for (String method : methods) {
                                                Map<String, Object> tool = new java.util.HashMap<>();
                                                
                                                // 工具名称：接口名.方法名
                                                String toolName = provider.getInterfaceName() + "." + method.trim();
                                                tool.put("name", toolName);
                                                
                                                // 工具描述
                                                tool.put("description", String.format("调用 %s 服务的 %s 方法", 
                                                        provider.getInterfaceName(), method.trim()));
                                                
                                                // 根据实际方法参数生成 inputSchema
                                                Map<String, Object> inputSchema = mcpToolSchemaGenerator.createInputSchemaFromMethod(
                                                        provider.getInterfaceName(), method.trim());
                                                tool.put("inputSchema", inputSchema);
                                                
                                                tools.add(tool);
                                            }
                                        } else {
                                            log.warn("⚠️ Provider {}:{} has no methods", provider.getInterfaceName(), provider.getVersion());
                                        }
                                    }
                                } else {
                                    log.warn("⚠️ No providers found for service {}:{}:{}", serviceInterface, version, group);
                                }
                            } catch (Exception e) {
                                log.warn("Failed to get tools for service: {}:{}:{}", 
                                        serviceInterface, version, group, e);
                            }
                        }
                        
                        log.info("✅ Generated {} tools from project (projectId: {})", tools.size(), projectId);
                    } else {
                        log.warn("⚠️ Real project endpoint found but projectId is null: {}", actualEndpoint);
                    }
                }
            } else {
                log.warn("⚠️ Endpoint not resolved: {}", actualEndpoint);
            }
        } catch (Exception e) {
            log.warn("Failed to get tools for endpoint: {}", endpoint, e);
        }
        
        return tools;
    }
    
    /**
     * 处理 tools/list 请求
     */
    private void handleToolsList(SseEmitter emitter, String endpoint, String id, String sessionId) throws IOException {
        log.info("📨 Handling tools/list request: endpoint={}, id={}, sessionId={}", endpoint, id, sessionId);
        
        // 如果 endpoint 为 null，尝试从所有虚拟项目中查找
        String actualEndpoint = endpoint;
        if (actualEndpoint == null || actualEndpoint.isEmpty()) {
            log.warn("⚠️ Endpoint is null in handleToolsList, trying to find from virtual projects");
            List<VirtualProjectService.VirtualProjectInfo> virtualProjects = virtualProjectService.getAllVirtualProjects();
            if (virtualProjects != null && virtualProjects.size() == 1) {
                VirtualProjectService.VirtualProjectInfo vp = virtualProjects.get(0);
                if (vp.getEndpoint() != null) {
                    actualEndpoint = vp.getEndpoint().getEndpointName();
                    log.info("📝 Using single virtual project endpoint: {}", actualEndpoint);
                }
            } else if (virtualProjects != null && virtualProjects.size() > 1) {
                log.warn("⚠️ Multiple virtual projects found ({}), cannot auto-select endpoint", virtualProjects.size());
            }
        }
        
        List<Map<String, Object>> tools = getToolsForEndpointInternal(actualEndpoint);
        log.info("✅ Got {} tools for endpoint: {}", tools.size(), actualEndpoint);
        
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tools", tools);
        result.put("toolsMeta", new java.util.HashMap<>());  // 添加 toolsMeta 字段（MCP 协议要求）
        
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id != null ? id : "null");
        response.put("result", result);
        
        String responseJson = objectMapper.writeValueAsString(response);
        log.debug("📤 Sending tools/list response: {}", responseJson);
        
        // 使用 sendSseEventSafe 确保错误处理一致
        sendSseEventSafe(emitter, responseJson, "tools/list", sessionId);
        
        log.info("✅ Tools/list response sent via SSE: tools count={}, sessionId={}", tools.size(), sessionId);
    }
    
    /**
     * 处理 tools/call 请求
     */
    private void handleToolCall(SseEmitter emitter, Map<String, Object> request, 
                                String endpoint, String id, String sessionId) throws IOException {
        log.info("📨 Handling tools/call request: endpoint={}, sessionId={}", endpoint, sessionId);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        String toolName = (String) params.get("name");

        // 如果 endpoint 为 null，尝试从所有虚拟项目中查找
        String actualEndpoint = endpoint;
        if (actualEndpoint == null || actualEndpoint.isEmpty()) {
            log.warn("⚠️ Endpoint is null in handleToolCall, trying to find from virtual projects");
            List<VirtualProjectService.VirtualProjectInfo> virtualProjects = virtualProjectService.getAllVirtualProjects();
            if (virtualProjects != null && virtualProjects.size() == 1) {
                VirtualProjectService.VirtualProjectInfo vp = virtualProjects.get(0);
                if (vp.getEndpoint() != null) {
                    actualEndpoint = vp.getEndpoint().getEndpointName();
                    log.info("📝 Using single virtual project endpoint: {}", actualEndpoint);
                }
            } else if (virtualProjects != null && virtualProjects.size() > 1) {
                log.warn("⚠️ Multiple virtual projects found ({}), cannot auto-select endpoint", virtualProjects.size());
            }
        }
        
        // 如果 endpoint 以 mcp- 开头，去掉前缀再解析（因为注册时不添加 mcp- 前缀）
        if (actualEndpoint.startsWith("mcp-")) {
            actualEndpoint = actualEndpoint.substring("mcp-".length());
            log.info("📝 Endpoint '{}' starts with mcp-, using '{}' for lookup", endpoint, actualEndpoint);
        }
        
        // 尝试解析 endpoint（但即使解析失败也继续执行，因为 McpExecutorService 会根据 toolName 自动查找服务）
        java.util.Optional<EndpointResolver.EndpointInfo> endpointInfoOpt = endpointResolver.resolveEndpoint(actualEndpoint);
        if (!endpointInfoOpt.isPresent()) {
            log.warn("⚠️ Endpoint not found: {} (tried as: {}), but continuing execution. " +
                    "McpExecutorService will try to find the service by toolName: {}", 
                    endpoint, actualEndpoint, toolName);
            // 不返回错误，让 McpExecutorService 尝试根据 toolName 查找服务
        } else {
            EndpointResolver.EndpointInfo endpointInfo = endpointInfoOpt.get();
            log.info("✅ Resolved endpoint '{}' to {} project: {}", 
                    actualEndpoint, endpointInfo.isVirtualProject() ? "virtual" : "real", endpointInfo.getMcpServiceName());
        }

        // MCP 协议中，arguments 应该是 Map<String, Object>，根据方法签名提取参数
        Object argumentsObj = params.get("arguments");
        Object[] args;

        // 从 toolName 中提取接口名和方法名
        // toolName 格式：com.pajk.mcpmetainfo.core.demo.service.UserService.getAllUsers
        String[] toolParts = toolName.split("\\.");
        String methodName = toolParts.length > 0 ? toolParts[toolParts.length - 1] : toolName;
        String interfaceName = toolParts.length > 1 ? 
                String.join(".", java.util.Arrays.copyOf(toolParts, toolParts.length - 1)) : null;

        if (argumentsObj instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> argumentsMap = (java.util.Map<String, Object>) argumentsObj;
            
            if (interfaceName != null) {
                // 根据方法签名从 argumentsMap 中提取参数
                args = mcpToolSchemaGenerator.extractMethodParameters(interfaceName, methodName, argumentsMap);
            } else {
                // 如果无法获取接口名，使用向后兼容逻辑
                if (argumentsMap.containsKey("args") && argumentsMap.get("args") instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> argsList = (java.util.List<Object>) argumentsMap.get("args");
                    args = argsList.toArray();
                } else if (argumentsMap.isEmpty()) {
                    // 如果 arguments 是空 Map，表示无参数方法调用
                    args = new Object[0];
                } else {
                    // 如果 arguments 不为空且没有 args 字段，将整个 Map 作为参数
                    args = new Object[]{argumentsMap};
                }
            }
        } else if (argumentsObj instanceof java.util.List) {
            // 如果是 List，直接转换（向后兼容）
            @SuppressWarnings("unchecked")
            java.util.List<Object> argumentsList = (java.util.List<Object>) argumentsObj;
            args = argumentsList.toArray();
        } else {
            args = new Object[0];
        }

        log.info("📨 Executing tool call: tool={}, endpoint={}, argsCount={}", toolName, endpoint, args.length);

        // 执行工具调用（McpExecutorService 会根据 toolName 自动查找对应的服务）
        McpExecutorService.McpCallResult result = mcpExecutorService.executeToolCallSync(
                toolName, args, 5000);

        Map<String, Object> response;
        if (result.isSuccess()) {
            // 构建符合 MCP 协议的响应格式
            Map<String, Object> contentItem = new java.util.LinkedHashMap<>();
            contentItem.put("type", "text");
            contentItem.put("text", objectMapper.writeValueAsString(result.getResult()));

            java.util.List<Map<String, Object>> content = new java.util.ArrayList<>();
            content.add(contentItem);

            Map<String, Object> resultMap = new java.util.LinkedHashMap<>();
            resultMap.put("content", content);
            resultMap.put("isError", false);  // 添加 isError 字段（MCP 协议要求）

            response = new java.util.LinkedHashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id != null ? id : "null");
            response.put("result", resultMap);
        } else {
            response = Map.of(
                    "jsonrpc", "2.0",
                    "id", id != null ? id : "null",
                    "error", Map.of(
                            "code", -32603,
                            "message", result.getErrorMessage()
                    )
            );
        }

        String responseJson = objectMapper.writeValueAsString(response);

        // 安全发送响应，捕获 Broken pipe 等异常
        sendSseEventSafe(emitter, responseJson, "tools/call", sessionId);

        log.info("✅ Tools/call response sent via SSE: success={}", result.isSuccess());
    }
    /**
     * 处理 resources/list 请求
     * 参考 tools/list 的实现，先解析 endpoint，然后返回资源列表
     */
    private void handleResourcesList(SseEmitter emitter, String endpoint, String id, String sessionId) throws IOException {
        log.info("📨 Handling resources/list request: endpoint={}, sessionId={}", endpoint, sessionId);
        
        // 参考 tools/list 的实现，先解析 endpoint（确保 endpoint 正确解析）
        java.util.Optional<EndpointResolver.EndpointInfo> endpointInfoOpt = endpointResolver.resolveEndpoint(endpoint);
        if (!endpointInfoOpt.isPresent()) {
            log.error("❌ Endpoint not found: {}. " +
                    "Please ensure: 1) For virtual projects, the endpoint is registered; " +
                    "2) For MCP service names (zk-mcp-*), the service is registered in a project.", endpoint);
            sendErrorResponseSafe(emitter, id, -32602, 
                    "Endpoint not found: " + endpoint + ". Please check if the service is registered.", sessionId);
            return;
        }
        
        EndpointResolver.EndpointInfo endpointInfo = endpointInfoOpt.get();
        log.info("✅ Resolved endpoint '{}' to {} project: {}", 
                endpoint, endpointInfo.isVirtualProject() ? "virtual" : "real", endpointInfo.getMcpServiceName());
        
        // 调用 McpResourcesService 获取资源列表（异步执行，避免阻塞）
        McpProtocol.ListResourcesParams params = new McpProtocol.ListResourcesParams();
        mcpResourcesService.listResources(params)
                .timeout(java.time.Duration.ofSeconds(5)) // 5秒超时
                .subscribe(
                        result -> {
                            try {
                                Map<String, Object> response = new java.util.LinkedHashMap<>();
                                response.put("jsonrpc", "2.0");
                                response.put("id", id != null ? id : "null");
                                
                                if (result != null && result.getResources() != null) {
                                    Map<String, Object> resultMap = new java.util.LinkedHashMap<>();
                                    resultMap.put("resources", result.getResources());
                                    if (result.getNextCursor() != null) {
                                        resultMap.put("nextCursor", result.getNextCursor());
                                    }
                                    response.put("result", resultMap);
                                } else {
                                    Map<String, Object> resultMap = new java.util.LinkedHashMap<>();
                                    resultMap.put("resources", new java.util.ArrayList<>());
                                    response.put("result", resultMap);
                                }
                                
                                String responseJson = objectMapper.writeValueAsString(response);
                                sendSseEventSafe(emitter, responseJson, "resources/list", sessionId);
                                
                                log.info("✅ Resources/list response sent via SSE: resources count={}", 
                                        result != null && result.getResources() != null ? result.getResources().size() : 0);
                            } catch (Exception e) {
                                log.error("❌ Error processing resources/list result: sessionId={}", sessionId, e);
                                sendErrorResponseSafe(emitter, id, -32603, "Internal error: " + e.getMessage(), sessionId);
                            }
                        },
                        error -> {
                            log.error("❌ Error handling resources/list: sessionId={}", sessionId, error);
                            sendErrorResponseSafe(emitter, id, -32603, "Internal error: " + error.getMessage(), sessionId);
                        }
                );
    }
    
    /**
     * 处理 prompts/list 请求
     * 参考 tools/list 的实现，先解析 endpoint，然后返回提示列表
     */
    private void handlePromptsList(SseEmitter emitter, String endpoint, String id, String sessionId) throws IOException {
        log.info("📨 Handling prompts/list request: endpoint={}, sessionId={}", endpoint, sessionId);
        
        // 参考 tools/list 的实现，先解析 endpoint（确保 endpoint 正确解析）
        java.util.Optional<EndpointResolver.EndpointInfo> endpointInfoOpt = endpointResolver.resolveEndpoint(endpoint);
        if (!endpointInfoOpt.isPresent()) {
            log.error("❌ Endpoint not found: {}. " +
                    "Please ensure: 1) For virtual projects, the endpoint is registered; " +
                    "2) For MCP service names (zk-mcp-*), the service is registered in a project.", endpoint);
            sendErrorResponseSafe(emitter, id, -32602, 
                    "Endpoint not found: " + endpoint + ". Please check if the service is registered.", sessionId);
            return;
        }
        
        EndpointResolver.EndpointInfo endpointInfo = endpointInfoOpt.get();
        log.info("✅ Resolved endpoint '{}' to {} project: {}", 
                endpoint, endpointInfo.isVirtualProject() ? "virtual" : "real", endpointInfo.getMcpServiceName());
        
        // 调用 McpPromptsService 获取提示列表（异步执行，避免阻塞）
        McpProtocol.ListPromptsParams params = new McpProtocol.ListPromptsParams();
        mcpPromptsService.listPrompts(params)
                .timeout(java.time.Duration.ofSeconds(5)) // 5秒超时
                .subscribe(
                        result -> {
                            try {
                                Map<String, Object> response = new java.util.LinkedHashMap<>();
                                response.put("jsonrpc", "2.0");
                                response.put("id", id != null ? id : "null");
                                
                                if (result != null && result.getPrompts() != null) {
                                    Map<String, Object> resultMap = new java.util.LinkedHashMap<>();
                                    resultMap.put("prompts", result.getPrompts());
                                    if (result.getNextCursor() != null) {
                                        resultMap.put("nextCursor", result.getNextCursor());
                                    }
                                    response.put("result", resultMap);
                                } else {
                                    Map<String, Object> resultMap = new java.util.LinkedHashMap<>();
                                    resultMap.put("prompts", new java.util.ArrayList<>());
                                    response.put("result", resultMap);
                                }
                                
                                String responseJson = objectMapper.writeValueAsString(response);
                                sendSseEventSafe(emitter, responseJson, "prompts/list", sessionId);
                                
                                log.info("✅ Prompts/list response sent via SSE: prompts count={}", 
                                        result != null && result.getPrompts() != null ? result.getPrompts().size() : 0);
                            } catch (Exception e) {
                                log.error("❌ Error processing prompts/list result: sessionId={}", sessionId, e);
                                sendErrorResponseSafe(emitter, id, -32603, "Internal error: " + e.getMessage(), sessionId);
                            }
                        },
                        error -> {
                            log.error("❌ Error handling prompts/list: sessionId={}", sessionId, error);
                            sendErrorResponseSafe(emitter, id, -32603, "Internal error: " + error.getMessage(), sessionId);
                        }
                );
    }
    
    /**
     * 安全发送 SSE 事件（捕获 Broken pipe 等异常）
     * 参考 mcp-router-v3 的实现，优雅处理客户端断开连接的情况
     */
    private void sendSseEventSafe(SseEmitter emitter, String data, String method, String sessionId) {
        if (emitter == null) {
            log.warn("⚠️ Cannot send SSE event, emitter is null: method={}, sessionId={}", method, sessionId);
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name("message")  // 设置 event type 以兼容 WebFluxSseClientTransport
                    .data(data));
            log.info("✅ Successfully sent SSE event '{}' for session: {} (data length: {})", 
                    method, sessionId, data != null ? data.length() : 0);
        } catch (IllegalStateException e) {
            // ResponseBodyEmitter has already completed - 客户端已断开连接
            if (e.getMessage() != null && e.getMessage().contains("already completed")) {
                log.debug("ℹ️ SSE emitter already completed for session: {}, method={}", sessionId, method);
            } else {
                log.warn("⚠️ SSE emitter illegal state for session: {}, method={}, error={}", 
                        sessionId, method, e.getMessage());
            }
        } catch (IOException e) {
            // Broken pipe - 客户端断开连接，这是正常情况
            if (e.getMessage() != null && e.getMessage().contains("Broken pipe")) {
                log.debug("ℹ️ Client disconnected (broken pipe) for session: {}, method={}", sessionId, method);
            } else {
                log.warn("⚠️ IO error sending SSE event for session: {}, method={}, error={}", 
                        sessionId, method, e.getMessage());
            }
        } catch (Exception e) {
            log.warn("⚠️ Unexpected error sending SSE event for session: {}, method={}, error={}", 
                    sessionId, method, e.getMessage());
        }
    }

    /**
     * 安全发送错误响应（避免在已关闭的 emitter 上发送）
     */
    private void sendErrorResponseSafe(SseEmitter emitter, String id, int code, String message, String sessionId) {
        try {
            Map<String, Object> errorResponse = Map.of(
                    "jsonrpc", "2.0",
                    "id", id != null ? id : "null",
                    "error", Map.of(
                            "code", code,
                            "message", message
                    )
            );
            String responseJson = objectMapper.writeValueAsString(errorResponse);
            sendSseEventSafe(emitter, responseJson, "error", sessionId);
        } catch (Exception e) {
            // 如果构建错误响应失败，只记录日志，不抛出异常
            log.debug("ℹ️ Failed to send error response (emitter may be closed): sessionId={}, error={}", 
                    sessionId, e.getMessage());
        }
    }

    
    /**
     * 发送错误响应
     */
    private void sendErrorResponse(SseEmitter emitter, String id, int code, String message) {
        try {
            Map<String, Object> errorResponse = Map.of(
                    "jsonrpc", "2.0",
                    "id", id != null ? id : "null",
                    "error", Map.of(
                            "code", code,
                            "message", message
                    )
            );
            String responseJson = objectMapper.writeValueAsString(errorResponse);
            emitter.send(SseEmitter.event()
                    .name("message")  // 设置 event type 以兼容 WebFluxSseClientTransport
                    .data(responseJson));
        } catch (IOException e) {
            log.error("Failed to send error response", e);
        }
    }
    
    /**
     * 处理 RESTful 消息（没有 SSE emitter 的情况）
     * 直接返回 JSON 响应，而不是通过 SSE 发送
     */
    private ResponseEntity<Map<String, Object>> handleRestfulMessage(
            Map<String, Object> request, String method, String id, String endpoint, String sessionId) {
        log.info("📨 Handling RESTful message: method={}, endpoint={}, sessionId={}", method, endpoint, sessionId);
        
        try {
            // 如果 endpoint 为 null，尝试从所有虚拟项目中查找
            if (endpoint == null || endpoint.isEmpty()) {
                log.warn("⚠️ Endpoint is null in RESTful call, trying to find from virtual projects");
                List<VirtualProjectService.VirtualProjectInfo> virtualProjects = virtualProjectService.getAllVirtualProjects();
                if (virtualProjects != null && virtualProjects.size() == 1) {
                    VirtualProjectService.VirtualProjectInfo vp = virtualProjects.get(0);
                    if (vp.getEndpoint() != null) {
                        endpoint = vp.getEndpoint().getEndpointName();
                        log.info("📝 Using single virtual project endpoint: {}", endpoint);
                    }
                } else if (virtualProjects != null && virtualProjects.size() > 1) {
                    log.warn("⚠️ Multiple virtual projects found ({}), cannot auto-select endpoint", virtualProjects.size());
                }
            }
            
            // 如果 endpoint 仍然为 null，返回错误
            if (endpoint == null || endpoint.isEmpty()) {
                log.error("❌ Endpoint is still null after all attempts, cannot process RESTful message: method={}", method);
                Map<String, Object> errorResponse = new java.util.LinkedHashMap<>();
                errorResponse.put("jsonrpc", "2.0");
                errorResponse.put("id", id != null ? id : "null");
                errorResponse.put("error", Map.of(
                        "code", -32602,
                        "message", "Endpoint not found. Please specify endpoint via X-Service-Name header or ensure virtual project is registered."
                ));
                return ResponseEntity.ok(errorResponse);
            }
            
            // 如果 endpoint 以 virtual- 或 mcp- 开头，去掉前缀
            if (endpoint.startsWith("virtual-")) {
                endpoint = endpoint.substring("virtual-".length());
                log.info("📝 Endpoint starts with virtual-, using: {}", endpoint);
            } else if (endpoint.startsWith("mcp-")) {
                // 向后兼容：如果以 mcp- 开头，也去掉前缀
                endpoint = endpoint.substring("mcp-".length());
                log.info("📝 Endpoint starts with mcp-, using: {}", endpoint);
            }
            
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id != null ? id : "null");
            
            if ("tools/list".equals(method)) {
                // 处理 tools/list
                List<Map<String, Object>> tools = getToolsForEndpointInternal(endpoint);
                log.info("✅ Got {} tools for endpoint: {}", tools.size(), endpoint);
                Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("tools", tools);
                result.put("toolsMeta", new java.util.HashMap<>());
                response.put("result", result);
                log.info("✅ Returning tools/list response: tools count={}", tools.size());
                
            } else if ("tools/call".equals(method)) {
                // 处理 tools/call
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) request.get("params");
                String toolName = (String) params.get("name");
                
                // 解析 endpoint（如果 endpoint 以 mcp- 开头，去掉前缀再解析）
                String actualEndpoint = endpoint;
                if (endpoint != null && endpoint.startsWith("mcp-")) {
                    actualEndpoint = endpoint.substring("mcp-".length());
                    log.info("📝 Endpoint '{}' starts with mcp-, using '{}' for lookup", endpoint, actualEndpoint);
                }
                
                java.util.Optional<EndpointResolver.EndpointInfo> endpointInfoOpt = endpointResolver.resolveEndpoint(actualEndpoint);
                if (!endpointInfoOpt.isPresent()) {
                    log.error("❌ Endpoint not found for RESTful tools/call: {} (tried as: {}). " +
                            "Please ensure: 1) For virtual projects, the endpoint is registered; " +
                            "2) For MCP service names (zk-mcp-*), the service is registered in a project.", 
                            endpoint, actualEndpoint);
                    response.put("error", Map.of("code", -32602, 
                            "message", "Endpoint not found: " + endpoint + ". Please check if the service is registered."));
                    return ResponseEntity.ok(response);
                }
                
                // 提取参数
                Object argumentsObj = params.get("arguments");
                Object[] args;
                
                String[] toolParts = toolName.split("\\.");
                String methodName = toolParts.length > 0 ? toolParts[toolParts.length - 1] : toolName;
                String interfaceName = toolParts.length > 1 ? 
                        String.join(".", java.util.Arrays.copyOf(toolParts, toolParts.length - 1)) : null;
                
                if (argumentsObj instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> argumentsMap = (java.util.Map<String, Object>) argumentsObj;
                    if (interfaceName != null) {
                        args = mcpToolSchemaGenerator.extractMethodParameters(interfaceName, methodName, argumentsMap);
                    } else if (argumentsMap.isEmpty()) {
                        args = new Object[0];
                    } else {
                        args = new Object[]{argumentsMap};
                    }
                } else {
                    args = new Object[0];
                }
                
                // 执行调用
                McpExecutorService.McpCallResult result = mcpExecutorService.executeToolCallSync(toolName, args, 5000);
                
                if (result.isSuccess()) {
                    Map<String, Object> contentItem = new java.util.LinkedHashMap<>();
                    contentItem.put("type", "text");
                    contentItem.put("text", objectMapper.writeValueAsString(result.getResult()));
                    
                    java.util.List<Map<String, Object>> content = new java.util.ArrayList<>();
                    content.add(contentItem);
                    
                    Map<String, Object> resultMap = new java.util.LinkedHashMap<>();
                    resultMap.put("content", content);
                    resultMap.put("isError", false);
                    response.put("result", resultMap);
                } else {
                    response.put("error", Map.of("code", -32603, "message", result.getErrorMessage()));
                }
                
            } else if ("resources/list".equals(method)) {
                // 处理 resources/list（使用超时保护，避免长时间阻塞）
                List<McpProtocol.McpResource> resources = new ArrayList<>();
                try {
                    McpProtocol.ListResourcesResult listResult = mcpResourcesService.listResources(
                            new McpProtocol.ListResourcesParams())
                            .timeout(java.time.Duration.ofSeconds(5)) // 5秒超时
                            .block();
                    if (listResult != null && listResult.getResources() != null) {
                        resources.addAll(listResult.getResources());
                    }
                } catch (Exception e) {
                    log.error("❌ Failed to retrieve resources: {}", e.getMessage());
                    response.put("error", Map.of("code", -32603, "message", "Failed to retrieve resources: " + e.getMessage()));
                    return ResponseEntity.ok(response);
                }
                
                Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("resources", resources);
                result.put("resourcesMeta", new java.util.HashMap<>());
                response.put("result", result);
                
            } else if ("prompts/list".equals(method)) {
                // 处理 prompts/list（使用超时保护，避免长时间阻塞）
                List<McpProtocol.McpPrompt> prompts = new ArrayList<>();
                try {
                    McpProtocol.ListPromptsResult listResult = mcpPromptsService.listPrompts(
                            new McpProtocol.ListPromptsParams())
                            .timeout(java.time.Duration.ofSeconds(5)) // 5秒超时
                            .block();
                    if (listResult != null && listResult.getPrompts() != null) {
                        prompts.addAll(listResult.getPrompts());
                    }
                } catch (Exception e) {
                    log.error("❌ Failed to retrieve prompts: {}", e.getMessage());
                    response.put("error", Map.of("code", -32603, "message", "Failed to retrieve prompts: " + e.getMessage()));
                    return ResponseEntity.ok(response);
                }
                
                Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("prompts", prompts);
                result.put("promptsMeta", new java.util.HashMap<>());
                response.put("result", result);
                
            } else {
                response.put("error", Map.of("code", -32601, "message", "Method not found: " + method));
            }
            
            log.info("✅ Returning RESTful response: method={}, hasResult={}", method, response.containsKey("result"));
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(response);
            
        } catch (Exception e) {
            log.error("❌ Error handling RESTful message: method={}, endpoint={}", method, endpoint, e);
            Map<String, Object> errorResponse = new java.util.LinkedHashMap<>();
            errorResponse.put("jsonrpc", "2.0");
            errorResponse.put("id", id != null ? id : "null");
            errorResponse.put("error", Map.of("code", -32603, "message", "Internal error: " + e.getMessage()));
            return ResponseEntity.ok(errorResponse);
        }
    }
    
}

