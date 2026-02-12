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
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.pajk.mcpmetainfo.core.service.McpProtocolService;
import com.pajk.mcpmetainfo.core.service.McpLoggingService;


/**
 * MCP 消息处理 Controller（WebMVC 模式）
 * 处理通过 POST /mcp/message 发送的 MCP 消息
 */
@Slf4j
@RestController
@RequestMapping(value = "/mcp")
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
    private final McpLoggingService mcpLoggingService;
    private final McpProtocolService mcpProtocolService;

    
    /**
     * 处理 MCP 消息：POST /mcp/{serviceName}/message?sessionId=xxx（路径参数方式，参考 mcp-router-v3）
     */
    @PostMapping(value = "/{serviceName}/message", 
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleMessageWithPath(
            @PathVariable String serviceName,
            @RequestParam(required = false) String sessionId,
            @RequestBody Map<String, Object> request) {
        
        log.info("📨 MCP message request (path): serviceName={}, sessionId={}, method={}", 
                serviceName, sessionId, request.get("method"));
        
        // 保持原始 serviceName 作为 endpoint，不再强制剥离前缀
        String endpoint = serviceName;
        log.debug("🔍 Using serviceName '{}' as endpoint", serviceName);
        
        // 调用统一的处理逻辑
        return handleMessage(sessionId, endpoint, request, serviceName);
    }

    /**
     * 处理 MCP 消息：POST /mcp/message?sessionId=xxx（查询参数方式）
     */
    @PostMapping(value = "/message", 
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleMessage(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String endpoint,  // 从 URL 参数获取 endpoint
            @RequestBody Map<String, Object> request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Service-Name", required = false) String serviceNameHeader) {
        
        log.info("📨 MCP message request: sessionId={}, endpoint={}, method={}, X-Service-Name={}", 
                sessionId, endpoint, request.get("method"), serviceNameHeader);
        
        // 获取 endpoint（参考 mcp-router-v3 的 session 管理）
        // 优先级：1. URL 参数 endpoint, 2. session, 3. X-Service-Name header, 4. 请求参数, 5. 自动查找
        String resolvedEndpoint = endpoint;  // 先使用 URL 参数中的 endpoint
        
        // 1. 如果 URL 参数中没有 endpoint，且 sessionId 存在，尝试从 session 中获取 endpoint
        if ((resolvedEndpoint == null || resolvedEndpoint.isEmpty()) && sessionId != null && !sessionId.isEmpty()) {
            resolvedEndpoint = sessionManager.getEndpointForSession(sessionId);
            
            // 2. 如果找不到 endpoint，尝试从 session 中获取 serviceName
            if (resolvedEndpoint == null || resolvedEndpoint.isEmpty()) {
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
                        resolvedEndpoint = tryEndpoint;
                        log.info("📝 Using endpoint from session serviceName: {} -> {}", serviceName, resolvedEndpoint);
                    } else {
                        resolvedEndpoint = tryEndpoint;
                        log.info("📝 Using serviceName as endpoint: {}", resolvedEndpoint);
                    }
                }
            }
        }
        
        // 3. 如果 endpoint 仍然为 null，尝试从请求头或请求中推断 endpoint（RESTful 调用场景）
        if (resolvedEndpoint == null || resolvedEndpoint.isEmpty()) {
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
                    resolvedEndpoint = tryEndpoint;
                    log.info("📝 Using endpoint from X-Service-Name header: {} -> {}", serviceNameHeader, resolvedEndpoint);
                } else {
                    resolvedEndpoint = tryEndpoint;
                    log.info("📝 Using X-Service-Name as endpoint: {}", resolvedEndpoint);
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
                        resolvedEndpoint = tryEndpoint;
                        log.info("📝 Using endpoint from request params: {} -> {}", serviceName, resolvedEndpoint);
                    } else {
                        resolvedEndpoint = tryEndpoint;
                        log.info("📝 Using request param serviceName as endpoint: {}", resolvedEndpoint);
                    }
                } else {
                    // 3. 尝试从所有虚拟项目中查找（如果只有一个虚拟项目，使用它）
                    List<VirtualProjectService.VirtualProjectInfo> virtualProjects = virtualProjectService.getAllVirtualProjects();
                    if (virtualProjects != null && virtualProjects.size() == 1) {
                        VirtualProjectService.VirtualProjectInfo vp = virtualProjects.get(0);
                        if (vp.getEndpoint() != null) {
                            resolvedEndpoint = vp.getEndpoint().getEndpointName();
                            log.info("📝 Using single virtual project endpoint: {}", resolvedEndpoint);
                        }
                    } else if (virtualProjects != null && virtualProjects.size() > 1) {
                        log.warn("⚠️ Multiple virtual projects found ({}), cannot auto-select endpoint. " +
                                "Please specify endpoint via URL parameter, X-Service-Name header or session.", virtualProjects.size());
                    }
                }
            }
        }
        
        // 使用解析后的 endpoint（如果 resolvedEndpoint 不为空，使用它；否则使用原始的 endpoint 参数）
        if (resolvedEndpoint != null && !resolvedEndpoint.isEmpty()) {
            endpoint = resolvedEndpoint;
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
            // JSON-RPC 通知（无 id）应不产生响应，直接忽略（参考 mcp-sdk）
            if (id == null && method != null && method.startsWith("notifications/")) {
                if ("notifications/initialized".equals(method)) {
                    log.info("🚀 Client connection initialized: sessionId={}", sessionId);
                } else {
                    log.info("ℹ️ Received JSON-RPC notification '{}', ignoring as per spec", method);
                }
                // 不通过 SSE 发送任何数据，直接返回 202 Accepted
                return ResponseEntity.accepted()
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(Map.of("status", "accepted", 
                                "message", "Notification processed"));
            }

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
            } else if ("tools/list".equals(method)) {
                handleToolsList(emitter, endpoint, id, sessionId);
            } else if ("tools/call".equals(method)) {
                handleToolCall(emitter, request, endpoint, id, sessionId);
            } else if ("resources/list".equals(method)) {
                handleResourcesList(emitter, endpoint, id, sessionId);
            } else if ("resources/read".equals(method)) {
                handleResourceRead(emitter, request, id, sessionId);
            } else if ("resources/subscribe".equals(method)) {
                handleSubscribeResource(emitter, request, id, sessionId);
            } else if ("resources/unsubscribe".equals(method)) {
                handleUnsubscribeResource(emitter, request, id, sessionId);
            } else if ("resources/templates/list".equals(method)) {
                handleResourcesTemplatesList(emitter, endpoint, id, sessionId);
            } else if ("prompts/list".equals(method)) {
                handlePromptsList(emitter, endpoint, id, sessionId);
            } else if ("prompts/get".equals(method)) {
                handlePromptGet(emitter, request, id, sessionId);
            } else if ("logging/log".equals(method)) {
                handleLogMessage(emitter, request, id, sessionId);
            } else if ("logging/setLevel".equals(method)) {
                handleLoggingSetLevel(emitter, request, id, sessionId);
            } else if ("ping".equals(method)) {
                handlePing(emitter, id, sessionId);
            } else {
                sendErrorResponseSafe(emitter, id, -32601, "Method not found: " + method, sessionId);
            }
            
            // 返回 202 Accepted（响应通过 SSE 发送）
            return ResponseEntity.accepted()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of("status", "accepted", 
                            "message", "Request accepted, but client disconnected"));
        } catch (Exception e) {
            log.error("❌ Error handling MCP message: sessionId={}, method={}", sessionId, method, e);
            sendErrorResponseSafe(emitter, id, -32603, "Internal error: " + e.getMessage(), sessionId);
            return ResponseEntity.accepted()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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
        
        // 设置 resources 能力（subscribe = true 表示支持资源订阅）
        Map<String, Object> resourcesCap = new java.util.LinkedHashMap<>();
        resourcesCap.put("subscribe", true);
        resourcesCap.put("listChanged", true);
        capabilities.put("resources", resourcesCap);
        
        // 设置 prompts 能力（listChanged = true 会触发客户端自动调用 prompts/list）
        Map<String, Object> promptsCap = new java.util.LinkedHashMap<>();
        promptsCap.put("listChanged", true);
        capabilities.put("prompts", promptsCap);

        // 设置 logging 能力
        capabilities.put("logging", new java.util.HashMap<>());
        
        result.put("capabilities", capabilities);
        
        Map<String, Object> serverInfo = new java.util.LinkedHashMap<>();
        serverInfo.put("name", serviceName);
        serverInfo.put("version", "1.0.0");
        serverInfo.put("description", "Dubbo MCP Service Adapter (zkInfo)");
        
        // 添加 capabilities 到 serverInfo (有些客户端在这里寻找)
        serverInfo.put("capabilities", Arrays.asList("tools", "resources", "prompts", "logging"));
        
        result.put("serverInfo", serverInfo);
        
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id != null ? id : "null");
        response.put("result", result);
        
        String responseJson = objectMapper.writeValueAsString(response);
        
        // 立即发送响应（不等待）
        // 参考 mcp-router-v3：不设置 event 名称，使用默认 event（符合 MCP 标准）
        try {
            emitter.send(SseEmitter.event()
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
     * 处理 ping 请求
     */
    private void handlePing(SseEmitter emitter, String id, String sessionId) throws IOException {
        log.info("📨 Handling ping request: sessionId={}, id={}", sessionId, id);
        
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id != null ? id : "null");
        response.put("result", new java.util.HashMap<>());
        
        String responseJson = objectMapper.writeValueAsString(response);
        sendSseEventSafe(emitter, responseJson, "ping", sessionId);
        log.info("✅ Ping response sent via SSE: sessionId={}, id={}", sessionId, id);
    }
    
    /**
     * 处理 resources/read 请求
     */
    private void handleResourceRead(SseEmitter emitter, Map<String, Object> request, String id, String sessionId) throws IOException {
        log.info("📨 Handling resources/read request: sessionId={}, id={}", sessionId, id);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        String uri = (String) params.get("uri");
        
        McpProtocol.ReadResourceParams readParams = McpProtocol.ReadResourceParams.builder()
                .uri(uri)
                .build();
        
        mcpResourcesService.readResource(readParams)
                .subscribe(result -> {
                    try {
                        Map<String, Object> response = new java.util.LinkedHashMap<>();
                        response.put("jsonrpc", "2.0");
                        response.put("id", id != null ? id : "null");
                        response.put("result", result);
                        
                        String responseJson = objectMapper.writeValueAsString(response);
                        sendSseEventSafe(emitter, responseJson, "resources/read", sessionId);
                        log.info("✅ Resources/read response sent: uri={}", uri);
                    } catch (Exception e) {
                        log.error("❌ Error sending resource result", e);
                        try {
                            sendErrorResponseSafe(emitter, id, -32603, "Internal error: " + e.getMessage(), sessionId);
                        } catch (Exception ex) {
                            log.error("Failed to send error response", ex);
                        }
                    }
                }, error -> {
                    log.error("❌ Error reading resource", error);
                    try {
                        sendErrorResponseSafe(emitter, id, -32603, "Internal error: " + error.getMessage(), sessionId);
                    } catch (Exception ex) {
                        log.error("Failed to send error response", ex);
                    }
                });
    }

    /**
     * 处理 prompts/get 请求
     */
    private void handlePromptGet(SseEmitter emitter, Map<String, Object> request, String id, String sessionId) throws IOException {
        log.info("📨 Handling prompts/get request: sessionId={}, id={}", sessionId, id);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        String name = (String) params.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
        
        McpProtocol.GetPromptParams getParams = McpProtocol.GetPromptParams.builder()
                .name(name)
                .arguments(arguments)
                .build();
        
        mcpPromptsService.getPrompt(getParams)
                .subscribe(result -> {
                    try {
                        Map<String, Object> response = new java.util.LinkedHashMap<>();
                        response.put("jsonrpc", "2.0");
                        response.put("id", id != null ? id : "null");
                        response.put("result", result);
                        
                        String responseJson = objectMapper.writeValueAsString(response);
                        sendSseEventSafe(emitter, responseJson, "prompts/get", sessionId);
                        log.info("✅ Prompts/get response sent: name={}", name);
                    } catch (Exception e) {
                        log.error("❌ Error sending prompt result", e);
                        try {
                            sendErrorResponseSafe(emitter, id, -32603, "Internal error: " + e.getMessage(), sessionId);
                        } catch (Exception ex) {
                            log.error("Failed to send error response", ex);
                        }
                    }
                }, error -> {
                    log.error("❌ Error getting prompt", error);
                    try {
                        sendErrorResponseSafe(emitter, id, -32603, "Internal error: " + error.getMessage(), sessionId);
                    } catch (Exception ex) {
                        log.error("Failed to send error response", ex);
                    }
                });
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
            
            // 保持原始 endpoint，由 resolver 决定如何查找
            String actualEndpoint = endpoint;
            
            java.util.Optional<EndpointResolver.EndpointInfo> endpointInfoOpt = endpointResolver.resolveEndpoint(actualEndpoint);
            if (endpointInfoOpt.isPresent()) {
                EndpointResolver.EndpointInfo endpointInfo = endpointInfoOpt.get();
                log.info("✅ Resolved endpoint '{}' to EndpointInfo: isVirtualProject={}, projectId={}", 
                        actualEndpoint, endpointInfo.isVirtualProject(), endpointInfo.getProjectId());
                
                if (endpointInfo.isVirtualProject()) {
                    // 虚拟项目：优先从 Nacos Config 获取工具
                    tools = virtualProjectRegistrationService.getVirtualProjectToolsByEndpointName(actualEndpoint);
                    log.info("✅ Got {} tools from virtual project Nacos Config (endpointName: {})", tools.size(), actualEndpoint);
                    
                    // 如果 Nacos Config 中没有工具，但有 projectId，尝试从 DB 生成 (Fallback)
                    if (tools.isEmpty() && endpointInfo.getProjectId() != null) {
                        log.warn("⚠️ No tools found in Nacos Config for virtual project '{}', falling back to DB generation", actualEndpoint);
                        tools = generateToolsFromProjectId(endpointInfo.getProjectId());
                        log.info("✅ Generated {} tools from virtual project DB definition (projectId: {})", tools.size(), endpointInfo.getProjectId());
                    }
                } else {
                    // 实际项目：从 ProviderService 获取工具
                    Long projectId = endpointInfo.getProjectId();
                    if (projectId != null) {
                        tools = generateToolsFromProjectId(projectId);
                        log.info("✅ Generated {} tools from real project (projectId: {})", tools.size(), projectId);
                    } else {
                        log.warn("⚠️ Real project endpoint found but projectId is null: {}", actualEndpoint);
                    }
                }
            } else {
                log.warn("⚠️ Endpoint not resolved: {}. Available endpoints may need to be checked.", actualEndpoint);
                // 尝试列出所有可用的虚拟项目 endpoint，帮助调试
                try {
                    List<VirtualProjectService.VirtualProjectInfo> allVirtualProjects = virtualProjectService.getAllVirtualProjects();
                    if (allVirtualProjects != null && !allVirtualProjects.isEmpty()) {
                        log.info("📋 Available virtual project endpoints:");
                        for (VirtualProjectService.VirtualProjectInfo vp : allVirtualProjects) {
                            if (vp.getEndpoint() != null) {
                                log.info("   - {}", vp.getEndpoint().getEndpointName());
                            }
                        }
                    } else {
                        log.warn("⚠️ No virtual projects found in the system");
                    }
                } catch (Exception e) {
                    log.debug("Failed to list virtual projects for debugging: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get tools for endpoint: {}", endpoint, e);
        }
        
        return tools;
    }
    
    /**
     * 根据项目 ID 生成工具列表 (从 DB 和 ProviderService)
     */
    private List<Map<String, Object>> generateToolsFromProjectId(Long projectId) {
        List<Map<String, Object>> tools = new ArrayList<>();
        try {
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
                            .filter(p -> (version == null || version.isEmpty() || version.equals(p.getVersion())) &&
                                    (group == null || group.isEmpty() || group.equals(p.getGroup())))
                            .collect(java.util.stream.Collectors.toList());
                    
                    log.debug("Found {} providers for service {}:{}:{}", providers.size(), serviceInterface, version, group);
                    
                    // 生成工具
                    if (!providers.isEmpty()) {
                        // 使用 Set 去重，避免同一个接口的多个 Provider 生成重复的工具
                        java.util.Set<String> processedMethods = new java.util.HashSet<>();
                        
                        for (ProviderInfo provider : providers) {
                            if (provider.getMethods() != null && !provider.getMethods().isEmpty()) {
                                String[] methods = provider.getMethods().split(",");
                                for (String method : methods) {
                                    String methodTrimmed = method.trim();
                                    String toolKey = provider.getInterfaceName() + "." + methodTrimmed;
                                    
                                    if (processedMethods.contains(toolKey)) {
                                        continue;
                                    }
                                    processedMethods.add(toolKey);
                                    
                                    Map<String, Object> tool = new java.util.HashMap<>();
                                    
                                    // 工具名称：接口名.方法名
                                    tool.put("name", toolKey);
                                    
                                    // 工具描述
                                    String dbDesc = mcpToolSchemaGenerator.getMethodDescriptionFromDb(provider.getInterfaceName(), methodTrimmed);
                                    tool.put("description", (dbDesc != null && !dbDesc.isBlank()) 
                                            ? dbDesc 
                                            : String.format("调用 %s 服务的 %s 方法", provider.getInterfaceName(), methodTrimmed));
                                    
                                    // 根据实际方法参数生成 inputSchema
                                    Map<String, Object> inputSchema = mcpToolSchemaGenerator.createInputSchemaFromMethod(
                                            provider.getInterfaceName(), methodTrimmed);
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
        } catch (Exception e) {
            log.error("Failed to generate tools from project ID: {}", projectId, e);
        }
        return tools;
    }
    
    /**
     * 处理 tools/list 请求
     */
    private void handleToolsList(SseEmitter emitter, String endpoint, String id, String sessionId) throws IOException {
        log.info("📨 Handling tools/list request: endpoint={}, id={}, sessionId={}", endpoint, id, sessionId);
        
        // 如果 endpoint 为 null，尝试从 session 中获取（多节点环境下，endpoint 存储在 Redis）
        String actualEndpoint = endpoint;
        if (actualEndpoint == null || actualEndpoint.isEmpty()) {
            if (sessionId != null && !sessionId.isEmpty()) {
                // 1. 尝试从 session 中获取 endpoint
                actualEndpoint = sessionManager.getEndpointForSession(sessionId);
                if (actualEndpoint != null && !actualEndpoint.isEmpty()) {
                    log.info("📝 Using endpoint from session: {}", actualEndpoint);
                } else {
                    // 2. 尝试从 session 中获取 serviceName，然后转换为 endpoint
                    String serviceName = sessionManager.getServiceName(sessionId);
                    if (serviceName != null && !serviceName.isEmpty()) {
                        // 如果 serviceName 以 virtual- 开头，去掉前缀
                        if (serviceName.startsWith("virtual-")) {
                            actualEndpoint = serviceName.substring("virtual-".length());
                            log.info("📝 Using endpoint from session serviceName: {} -> {}", serviceName, actualEndpoint);
                        } else if (serviceName.startsWith("mcp-")) {
                            actualEndpoint = serviceName.substring("mcp-".length());
                            log.info("📝 Using endpoint from session serviceName: {} -> {}", serviceName, actualEndpoint);
                        } else {
                            actualEndpoint = serviceName;
                            log.info("📝 Using serviceName as endpoint: {}", actualEndpoint);
                        }
                    }
                }
            }
            
            // 3. 如果仍然为 null，尝试从所有虚拟项目中查找（向后兼容）
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
                    // 列出所有虚拟项目，帮助调试
                    for (VirtualProjectService.VirtualProjectInfo vp : virtualProjects) {
                        if (vp.getEndpoint() != null) {
                            log.info("   Available endpoint: {}", vp.getEndpoint().getEndpointName());
                        }
                    }
                } else {
                    log.warn("⚠️ No virtual projects found");
                }
            }
        }
        
        // 保持原始名称，不再强制剥离前缀。后续 resolver 会根据全名查找。
        log.info("🔍 Using endpoint for tools/list: {}", actualEndpoint);
        List<Map<String, Object>> tools = getToolsForEndpointInternal(actualEndpoint);
        log.info("✅ Got {} tools for endpoint: {}", tools.size(), actualEndpoint);
        
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tools", tools);
        // Removed toolsMeta to comply with MCP spec
        
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

        // 如果 endpoint 为 null，尝试从 session 中获取（多节点环境下，endpoint 存储在 Redis）
        String actualEndpoint = endpoint;
        if (actualEndpoint == null || actualEndpoint.isEmpty()) {
            if (sessionId != null && !sessionId.isEmpty()) {
                // 1. 尝试从 session 中获取 endpoint
                actualEndpoint = sessionManager.getEndpointForSession(sessionId);
                if (actualEndpoint != null && !actualEndpoint.isEmpty()) {
                    log.info("📝 Using endpoint from session: {}", actualEndpoint);
                } else {
                    // 2. 尝试从 session 中获取 serviceName，然后转换为 endpoint
                    String serviceName = sessionManager.getServiceName(sessionId);
                    if (serviceName != null && !serviceName.isEmpty()) {
                        // 如果 serviceName 以 virtual- 开头，去掉前缀
                        if (serviceName.startsWith("virtual-")) {
                            actualEndpoint = serviceName.substring("virtual-".length());
                            log.info("📝 Using endpoint from session serviceName: {} -> {}", serviceName, actualEndpoint);
                        } else if (serviceName.startsWith("mcp-")) {
                            actualEndpoint = serviceName.substring("mcp-".length());
                            log.info("📝 Using endpoint from session serviceName: {} -> {}", serviceName, actualEndpoint);
                        } else {
                            actualEndpoint = serviceName;
                            log.info("📝 Using serviceName as endpoint: {}", actualEndpoint);
                        }
                    }
                }
            }
            
            // 3. 如果仍然为 null，尝试从所有虚拟项目中查找（向后兼容）
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
        }
        
        // 保持原始名称，不再强制剥离前缀
        log.debug("🔍 Tool call for actualEndpoint: {}", actualEndpoint);
        
        // 尝试解析 endpoint
        if (actualEndpoint != null && !actualEndpoint.isEmpty()) {
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
        } else {
            log.warn("⚠️ Endpoint is still null after all attempts, but continuing execution. " +
                    "McpExecutorService will try to find the service by toolName: {}", toolName);
        }

        // MCP 协议中，arguments 应该是 Map<String, Object>，根据方法签名提取参数
        Object argumentsObj = params.get("arguments");
        log.info("📥 Received arguments: type={}, value={}", 
                argumentsObj != null ? argumentsObj.getClass().getSimpleName() : "null", argumentsObj);
        
        Object[] args;

        // 从 toolName 中提取接口名和方法名
        // toolName 格式：com.pajk.mcpmetainfo.core.demo.service.UserService.getAllUsers
        String[] toolParts = toolName.split("\\.");
        String methodName = toolParts.length > 0 ? toolParts[toolParts.length - 1] : toolName;
        String interfaceName = toolParts.length > 1 ? 
                String.join(".", java.util.Arrays.copyOf(toolParts, toolParts.length - 1)) : null;

        log.info("🔍 Parsed tool name: interface={}, method={}", interfaceName, methodName);

        if (argumentsObj instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> argumentsMap = (java.util.Map<String, Object>) argumentsObj;
            log.info("📋 Arguments Map: keys={}, size={}", argumentsMap.keySet(), argumentsMap.size());
            
            if (interfaceName != null) {
                // 根据方法签名从 argumentsMap 中提取参数
                log.info("🔧 Extracting parameters using method signature for {}.{}", interfaceName, methodName);
                args = mcpToolSchemaGenerator.extractMethodParameters(interfaceName, methodName, argumentsMap);
                log.info("✅ Extracted {} parameters", args != null ? args.length : 0);
            } else {
                // 如果无法获取接口名，使用向后兼容逻辑
                log.warn("⚠️ Interface name is null, using backward compatibility logic");
                if (argumentsMap.containsKey("args") && argumentsMap.get("args") instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> argsList = (java.util.List<Object>) argumentsMap.get("args");
                    args = argsList.toArray();
                } else if (argumentsMap.isEmpty()) {
                    args = new Object[0];
                } else {
                    args = new Object[]{argumentsMap};
                }
            }
            
            // 尝试提取显式参数类型并进行转换 (修复 Integer -> Long 问题)
            // 这部分逻辑从 McpProtocolService 借鉴而来，确保在 McpMessageController 中也能正确转换类型
            try {
                String[] explicitParameterTypes = mcpProtocolService.extractParameterTypes(toolName, argumentsMap);
                
                if (explicitParameterTypes != null) {
                     log.info("✅ Extracted explicit parameter types for {}: {}", toolName, java.util.Arrays.toString(explicitParameterTypes));
                     
                     if (args != null && explicitParameterTypes.length == args.length) {
                         for (int i = 0; i < args.length; i++) {
                             String targetType = explicitParameterTypes[i];
                             Object originalValue = args[i];
                             
                             if (originalValue != null && targetType != null) {
                                 try {
                                     if ("java.lang.Long".equals(targetType) && originalValue instanceof Integer) {
                                         args[i] = ((Integer) originalValue).longValue();
                                         log.info("参数[{}] 自动转换: Integer {} -> Long {}", i, originalValue, args[i]);
                                     } else if ("java.lang.Long".equals(targetType) && originalValue instanceof String) {
                                         args[i] = Long.parseLong((String) originalValue);
                                         log.info("参数[{}] 自动转换: String {} -> Long {}", i, originalValue, args[i]);
                                     } else if ("java.lang.Integer".equals(targetType) && originalValue instanceof Long) {
                                         args[i] = ((Long) originalValue).intValue();
                                         log.info("参数[{}] 自动转换: Long {} -> Integer {}", i, originalValue, args[i]);
                                     }
                                 } catch (Exception e) {
                                     log.warn("参数[{}] 类型转换失败: {} -> {}, error={}", i, originalValue.getClass().getName(), targetType, e.getMessage());
                                 }
                             }
                         }
                         
                         // 将显式参数类型传递给 Dubbo 调用
                         // 注意：这里需要重新定义 result，因为需要将 parameterTypes 传进去
                         McpExecutorService.McpCallResult result = mcpExecutorService.executeToolCallSync(
                                 toolName, args, null, explicitParameterTypes);
                                 
                         try {
                             handleToolCallResult(emitter, result, id, sessionId);
                         } catch (Exception e) {
                             log.error("Failed to handle tool call result", e);
                         }
                         return; // 提前返回

                     }
                }
            } catch (Exception e) {
                log.warn("提取参数类型或转换失败: {}", e.getMessage());
            }

        } else if (argumentsObj instanceof java.util.List) {

            // 如果是 List，直接转换（向后兼容）
            @SuppressWarnings("unchecked")
            java.util.List<Object> argumentsList = (java.util.List<Object>) argumentsObj;
            args = argumentsList.toArray();
            log.info("📋 Arguments List: size={}, converted to array", argumentsList.size());
        } else {
            args = new Object[0];
            log.info("📋 Arguments is not Map or List, using empty array");
        }

        log.info("📨 Executing tool call: tool={}, endpoint={}, argsCount={}", toolName, endpoint, args.length);
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                log.info("   args[{}]: type={}, value={}", i, 
                        args[i] != null ? args[i].getClass().getSimpleName() : "null", args[i]);
            }
        }

        // 执行工具调用（McpExecutorService 会根据 toolName 自动查找对应的服务）
        // 传入 null 让 executeToolCallSync 使用配置的 Dubbo 超时时间（默认 30 秒）
        McpExecutorService.McpCallResult result = mcpExecutorService.executeToolCallSync(
                toolName, args, null);

        try {
            handleToolCallResult(emitter, result, id, sessionId);
        } catch (Exception e) {
            log.error("Failed to handle tool call result", e);
        }
    }

    private void handleToolCallResult(SseEmitter emitter, McpExecutorService.McpCallResult result, String id, String sessionId) throws IOException {
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
        
        // 如果 endpoint 为 null，尝试从 session 中获取（多节点环境下，endpoint 存储在 Redis）
        String actualEndpoint = endpoint;
        if (actualEndpoint == null || actualEndpoint.isEmpty()) {
            if (sessionId != null && !sessionId.isEmpty()) {
                // 1. 尝试从 session 中获取 endpoint
                actualEndpoint = sessionManager.getEndpointForSession(sessionId);
                if (actualEndpoint != null && !actualEndpoint.isEmpty()) {
                    log.info("📝 Using endpoint from session: {}", actualEndpoint);
                } else {
                    // 2. 尝试从 session 中获取 serviceName，然后转换为 endpoint
                    String serviceName = sessionManager.getServiceName(sessionId);
                    if (serviceName != null && !serviceName.isEmpty()) {
                        // 如果 serviceName 以 virtual- 开头，去掉前缀
                        if (serviceName.startsWith("virtual-")) {
                            actualEndpoint = serviceName.substring("virtual-".length());
                            log.info("📝 Using endpoint from session serviceName: {} -> {}", serviceName, actualEndpoint);
                        } else if (serviceName.startsWith("mcp-")) {
                            actualEndpoint = serviceName.substring("mcp-".length());
                            log.info("📝 Using endpoint from session serviceName: {} -> {}", serviceName, actualEndpoint);
                        } else {
                            actualEndpoint = serviceName;
                            log.info("📝 Using serviceName as endpoint: {}", actualEndpoint);
                        }
                    }
                }
            }
        }
        
        // 如果 endpoint 以 virtual- 或 mcp- 开头，去掉前缀再解析
        if (actualEndpoint != null) {
            if (actualEndpoint.startsWith("virtual-")) {
                actualEndpoint = actualEndpoint.substring("virtual-".length());
                log.info("📝 Endpoint '{}' starts with virtual-, using '{}' for lookup", endpoint, actualEndpoint);
            } else if (actualEndpoint.startsWith("mcp-")) {
                actualEndpoint = actualEndpoint.substring("mcp-".length());
                log.info("📝 Endpoint '{}' starts with mcp-, using '{}' for lookup", endpoint, actualEndpoint);
            }
        }
        
        // 参考 tools/list 的实现，先解析 endpoint（确保 endpoint 正确解析）
        if (actualEndpoint == null || actualEndpoint.isEmpty()) {
            log.error("❌ Endpoint not found: {}. " +
                    "Please ensure: 1) For virtual projects, the endpoint is registered; " +
                    "2) For MCP service names (zk-mcp-*), the service is registered in a project.", endpoint);
            sendErrorResponseSafe(emitter, id, -32602, 
                    "Endpoint not found: " + endpoint + ". Please check if the service is registered.", sessionId);
            return;
        }
        
        java.util.Optional<EndpointResolver.EndpointInfo> endpointInfoOpt = endpointResolver.resolveEndpoint(actualEndpoint);
        if (!endpointInfoOpt.isPresent()) {
            log.error("❌ Endpoint not found: {} (tried as: {}). " +
                    "Please ensure: 1) For virtual projects, the endpoint is registered; " +
                    "2) For MCP service names (zk-mcp-*), the service is registered in a project.", 
                    endpoint, actualEndpoint);
            sendErrorResponseSafe(emitter, id, -32602, 
                    "Endpoint not found: " + actualEndpoint + ". Please check if the service is registered.", sessionId);
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
     * 处理 resources/templates/list 请求
     */
    private void handleResourcesTemplatesList(SseEmitter emitter, String endpoint, String id, String sessionId) throws IOException {
        log.info("📨 Handling resources/templates/list request: sessionId={}, id={}", sessionId, id);
        
        // Return empty list for now as we don't support templates yet
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("resourceTemplates", new java.util.ArrayList<>());
        
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id != null ? id : "null");
        response.put("result", result);
        
        String responseJson = objectMapper.writeValueAsString(response);
        sendSseEventSafe(emitter, responseJson, "resources/templates/list", sessionId);
        log.info("✅ Resources/templates/list response sent via SSE");
    }

    /**
     * 处理 prompts/list 请求
     * 参考 tools/list 的实现，先解析 endpoint，然后返回提示列表
     */
    private void handlePromptsList(SseEmitter emitter, String endpoint, String id, String sessionId) throws IOException {
        log.info("📨 Handling prompts/list request: endpoint={}, sessionId={}", endpoint, sessionId);
        
        // 如果 endpoint 为 null，尝试从 session 中获取（多节点环境下，endpoint 存储在 Redis）
        String actualEndpoint = endpoint;
        if (actualEndpoint == null || actualEndpoint.isEmpty()) {
            if (sessionId != null && !sessionId.isEmpty()) {
                // 1. 尝试从 session 中获取 endpoint
                actualEndpoint = sessionManager.getEndpointForSession(sessionId);
                if (actualEndpoint != null && !actualEndpoint.isEmpty()) {
                    log.info("📝 Using endpoint from session: {}", actualEndpoint);
                } else {
                    // 2. 尝试从 session 中获取 serviceName，然后转换为 endpoint
                    String serviceName = sessionManager.getServiceName(sessionId);
                    if (serviceName != null && !serviceName.isEmpty()) {
                        // 如果 serviceName 以 virtual- 开头，去掉前缀
                        if (serviceName.startsWith("virtual-")) {
                            actualEndpoint = serviceName.substring("virtual-".length());
                            log.info("📝 Using endpoint from session serviceName: {} -> {}", serviceName, actualEndpoint);
                        } else if (serviceName.startsWith("mcp-")) {
                            actualEndpoint = serviceName.substring("mcp-".length());
                            log.info("📝 Using endpoint from session serviceName: {} -> {}", serviceName, actualEndpoint);
                        } else {
                            actualEndpoint = serviceName;
                            log.info("📝 Using serviceName as endpoint: {}", actualEndpoint);
                        }
                    }
                }
            }
        }
        
        // 如果 endpoint 以 virtual- 或 mcp- 开头，去掉前缀再解析
        if (actualEndpoint != null) {
            if (actualEndpoint.startsWith("virtual-")) {
                actualEndpoint = actualEndpoint.substring("virtual-".length());
                log.info("📝 Endpoint '{}' starts with virtual-, using '{}' for lookup", endpoint, actualEndpoint);
            } else if (actualEndpoint.startsWith("mcp-")) {
                actualEndpoint = actualEndpoint.substring("mcp-".length());
                log.info("📝 Endpoint '{}' starts with mcp-, using '{}' for lookup", endpoint, actualEndpoint);
            }
        }
        
        // 参考 tools/list 的实现，先解析 endpoint（确保 endpoint 正确解析）
        if (actualEndpoint == null || actualEndpoint.isEmpty()) {
            log.error("❌ Endpoint not found: {}. " +
                    "Please ensure: 1) For virtual projects, the endpoint is registered; " +
                    "2) For MCP service names (zk-mcp-*), the service is registered in a project.", endpoint);
            sendErrorResponseSafe(emitter, id, -32602, 
                    "Endpoint not found: " + endpoint + ". Please check if the service is registered.", sessionId);
            return;
        }
        
        java.util.Optional<EndpointResolver.EndpointInfo> endpointInfoOpt = endpointResolver.resolveEndpoint(actualEndpoint);
        if (!endpointInfoOpt.isPresent()) {
            log.error("❌ Endpoint not found: {} (tried as: {}). " +
                    "Please ensure: 1) For virtual projects, the endpoint is registered; " +
                    "2) For MCP service names (zk-mcp-*), the service is registered in a project.", 
                    endpoint, actualEndpoint);
            sendErrorResponseSafe(emitter, id, -32602, 
                    "Endpoint not found: " + actualEndpoint + ". Please check if the service is registered.", sessionId);
            return;
        }
        
        EndpointResolver.EndpointInfo endpointInfo = endpointInfoOpt.get();
        log.info("✅ Resolved endpoint '{}' to {} project: {}", 
                actualEndpoint, endpointInfo.isVirtualProject() ? "virtual" : "real", endpointInfo.getMcpServiceName());
        
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
            // 参考 mcp-router-v3：不设置 event 名称，使用默认 event（符合 MCP 标准）
            emitter.send(SseEmitter.event()
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
                // Removed toolsMeta to comply with MCP spec
                response.put("result", result);
                log.info("✅ Returning tools/list response: tools count={}", tools.size());
                
            } else if ("tools/call".equals(method)) {
                // ... (tools/call handling remains same) ...
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
                @SuppressWarnings("unchecked")
                Map<String, Object> argumentsMap = (argumentsObj instanceof java.util.Map) ? 
                        (java.util.Map<String, Object>) argumentsObj : new java.util.HashMap<>();
                
                // 使用 McpProtocolService 执行调用 (支持参数类型推断和统一的逻辑)
                try {
                    // executeToolCall 返回 Mono，这里需要阻塞获取结果
                    McpProtocol.CallToolResult toolResult = mcpProtocolService.executeToolCall(
                            toolName, argumentsMap, 30000).block(); // 30s timeout
                    
                    if (toolResult != null) {
                        if (toolResult.getIsError()) {
                            // 提取错误信息
                            String errorMsg = "Unknown error";
                            if (toolResult.getContent() != null && !toolResult.getContent().isEmpty()) {
                                errorMsg = toolResult.getContent().stream()
                                    .map(c -> c.getText())
                                    .collect(java.util.stream.Collectors.joining("\n"));
                            }
                            response.put("error", Map.of("code", -32603, "message", errorMsg));
                        } else {
                            // 调用成功，转换结果格式
                            Map<String, Object> resultMap = new java.util.LinkedHashMap<>();
                            resultMap.put("content", toolResult.getContent());
                            resultMap.put("isError", false);
                            response.put("result", resultMap);
                        }
                    } else {
                        response.put("error", Map.of("code", -32603, "message", "Tool execution returned null"));
                    }
                } catch (Exception e) {
                    log.error("RESTful tool call failed", e);
                    response.put("error", Map.of("code", -32603, "message", e.getMessage()));
                }
                
            } else if ("resources/list".equals(method)) {
                // 处理 resources/list
                List<McpProtocol.McpResource> resources = new ArrayList<>();
                try {
                    McpProtocol.ListResourcesResult listResult = mcpResourcesService.listResources(
                            new McpProtocol.ListResourcesParams())
                            .timeout(java.time.Duration.ofSeconds(5))
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
                // Removed resourcesMeta
                response.put("result", result);
                
            } else if ("prompts/list".equals(method)) {
                // 处理 prompts/list
                List<McpProtocol.McpPrompt> prompts = new ArrayList<>();
                try {
                    McpProtocol.ListPromptsResult listResult = mcpPromptsService.listPrompts(
                            new McpProtocol.ListPromptsParams())
                            .timeout(java.time.Duration.ofSeconds(5))
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
                // Removed promptsMeta
                response.put("result", result);
                
            } else if ("resources/templates/list".equals(method)) {
                // 处理 resources/templates/list (Empty list)
                Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("resourceTemplates", new java.util.ArrayList<>());
                response.put("result", result);

            } else if ("resources/read".equals(method)) {
                // 处理 resources/read
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) request.get("params");
                String uri = (String) params.get("uri");
                
                try {
                    McpProtocol.ReadResourceResult readResult = mcpResourcesService.readResource(
                            McpProtocol.ReadResourceParams.builder().uri(uri).build())
                            .timeout(java.time.Duration.ofSeconds(5))
                            .block();
                    response.put("result", readResult);
                } catch (Exception e) {
                   log.error("❌ Failed to read resource: {}", e.getMessage());
                   response.put("error", Map.of("code", -32603, "message", "Failed to read resource: " + e.getMessage()));
                   return ResponseEntity.ok(response);
                }


            } else if ("logging/setLevel".equals(method)) {
                // 处理 logging/setLevel
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) request.get("params");
                String level = (String) params.get("level");
                log.info("📝 Setting log level (RESTful): {}", level);
                // Return empty result as acknowledgment
                response.put("result", new java.util.HashMap<>());

            } else if ("logging/log".equals(method)) {
                // 处理 logging/log (Log it and return success)
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) request.get("params");
                String level = (String) params.get("level");
                String data = (String) params.get("data");
                log.info("📝 Received log message (RESTful): level={}, data={}", level, data);
                // Return empty result
                response.put("result", new java.util.HashMap<>());

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
    
    /**
     * 处理 resources/subscribe 请求
     */
    private void handleSubscribeResource(SseEmitter emitter, Map<String, Object> request, String id, String sessionId) throws IOException {
        log.info("📨 Handling resources/subscribe request: sessionId={}, id={}", sessionId, id);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        String uri = (String) params.get("uri");
        
        McpProtocol.SubscribeResourceParams subscribeParams = McpProtocol.SubscribeResourceParams.builder()
                .uri(uri)
                .build();
        
        mcpResourcesService.subscribeResource(sessionId, subscribeParams)
                .subscribe(v -> {
                    try {
                        Map<String, Object> response = new java.util.LinkedHashMap<>();
                        response.put("jsonrpc", "2.0");
                        response.put("id", id != null ? id : "null");
                        response.put("result", Map.of("subscribed", true));
                        
                        String responseJson = objectMapper.writeValueAsString(response);
                        sendSseEventSafe(emitter, responseJson, "resources/subscribe", sessionId);
                        log.info("✅ Resources/subscribe response sent: uri={}", uri);
                    } catch (Exception e) {
                        log.error("❌ Error sending subscribe result", e);
                    }
                });
    }

    /**
     * 处理 resources/unsubscribe 请求
     */
    private void handleUnsubscribeResource(SseEmitter emitter, Map<String, Object> request, String id, String sessionId) throws IOException {
        log.info("📨 Handling resources/unsubscribe request: sessionId={}, id={}", sessionId, id);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        String uri = (String) params.get("uri");
        
        McpProtocol.UnsubscribeResourceParams unsubscribeParams = McpProtocol.UnsubscribeResourceParams.builder()
                .uri(uri)
                .build();
        
        mcpResourcesService.unsubscribeResource(sessionId, unsubscribeParams)
                .subscribe(v -> {
                    try {
                        Map<String, Object> response = new java.util.LinkedHashMap<>();
                        response.put("jsonrpc", "2.0");
                        response.put("id", id != null ? id : "null");
                        response.put("result", Map.of("unsubscribed", true));
                        
                        String responseJson = objectMapper.writeValueAsString(response);
                        sendSseEventSafe(emitter, responseJson, "resources/unsubscribe", sessionId);
                        log.info("✅ Resources/unsubscribe response sent: uri={}", uri);
                    } catch (Exception e) {
                        log.error("❌ Error sending unsubscribe result", e);
                    }
                });
    }

    /**
     * 处理 logging/setLevel 请求
     */
    private void handleLoggingSetLevel(SseEmitter emitter, Map<String, Object> request, String id, String sessionId) throws IOException {
        log.info("📨 Handling logging/setLevel request: sessionId={}, id={}", sessionId, id);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        String level = (String) params.get("level");
        
        log.info("📝 Setting log level for session {}: {}", sessionId, level);
        
        // 目前简单的确认设置成功，不做实际的过滤逻辑（因为 McpLoggingService 是全局的）
        // 可以在未来实现基于 session 的日志级别过滤
        
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id != null ? id : "null");
        response.put("result", new java.util.HashMap<>());
        
        String responseJson = objectMapper.writeValueAsString(response);
        sendSseEventSafe(emitter, responseJson, "logging/setLevel", sessionId);
        log.info("✅ Logging/setLevel response sent");
    }

    /**
     * 处理 logging/log 请求
     */
    private void handleLogMessage(SseEmitter emitter, Map<String, Object> request, String id, String sessionId) throws IOException {
        log.info("📨 Handling logging/log request: sessionId={}, id={}", sessionId, id);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        
        McpProtocol.LogMessageParams logParams = objectMapper.convertValue(params, McpProtocol.LogMessageParams.class);
        
        mcpLoggingService.logMessage(logParams)
                .subscribe(v -> {
                    try {
                        Map<String, Object> response = new java.util.LinkedHashMap<>();
                        response.put("jsonrpc", "2.0");
                        response.put("id", id != null ? id : "null");
                        response.put("result", Map.of("logged", true));
                        
                        String responseJson = objectMapper.writeValueAsString(response);
                        sendSseEventSafe(emitter, responseJson, "logging/log", sessionId);
                        log.info("✅ Logging/log response sent");
                    } catch (Exception e) {
                        log.error("❌ Error sending log result", e);
                    }
                });
    }
}

