package com.pajk.mcpmetainfo.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpmetainfo.core.service.EndpointResolver;
import com.pajk.mcpmetainfo.core.service.McpSessionManager;
import com.pajk.mcpmetainfo.core.service.McpExecutorService;
import com.pajk.mcpmetainfo.core.service.ProjectManagementService;
import com.pajk.mcpmetainfo.core.service.VirtualProjectService;
import com.pajk.mcpmetainfo.core.service.NacosMcpRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE Controller for WebMVC
 * 提供 SSE 端点支持，转发到 WebFlux handler 或直接实现
 */
@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SseController {
    
    private final EndpointResolver endpointResolver;
    private final McpSessionManager sessionManager;
    private final McpExecutorService mcpExecutorService;
    private final ProjectManagementService projectManagementService;
    private final VirtualProjectService virtualProjectService;
    private final NacosMcpRegistrationService nacosMcpRegistrationService;
    private final Environment environment;
    
    // 使用共享的线程池，避免每个连接都创建新的线程池
    private static final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // WebMVC 模式下存储 SseEmitter
    private final Map<String, SseEmitter> sseEmitterMap = new ConcurrentHashMap<>();
    
    // 存储每个 session 的心跳任务，用于在连接关闭时取消
    private final Map<String, java.util.concurrent.ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();
    
    /**
     * 获取 SseEmitter（供 McpMessageController 使用）
     */
    public SseEmitter getSseEmitter(String sessionId) {
        return sseEmitterMap.get(sessionId);
    }
    
    /**
     * 标准 SSE 端点：GET /sse?serviceName={serviceName}
     * 支持多种方式获取 serviceName：
     * 1. 查询参数 serviceName
     * 2. Header X-Service-Name
     * 3. 从 Nacos 注册信息中查找（根据请求的 IP:Port）
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> sseStandard(
            @RequestParam(required = false) String serviceName,
            @RequestHeader(value = "X-Service-Name", required = false) String serviceNameHeader,
            @RequestHeader(value = "Host", required = false) String hostHeader) {

        String actualServiceName = serviceName != null ? serviceName : serviceNameHeader;
        
        // 如果还没有 serviceName，尝试从 Nacos 注册信息中查找
        if (actualServiceName == null || actualServiceName.isEmpty()) {
            log.debug("⚠️ Standard SSE endpoint called without serviceName, trying to resolve from Nacos...");
            
            // 尝试从 Nacos 注册信息中查找匹配的服务
            try {
                String localIp = nacosMcpRegistrationService.getLocalIp();
                int serverPort = nacosMcpRegistrationService.getServerPort();
                
                // 查询 Nacos 中注册的所有 MCP 服务
                Collection<String> registeredServices = nacosMcpRegistrationService.getRegisteredServicesFromNacos();
                
                // 查找匹配的服务（根据 IP 和 Port）
                // 优先查找虚拟项目服务（mcp- 开头），然后查找普通 Dubbo 服务（zk-mcp- 开头）
                List<String> matchedServices = new ArrayList<>();
                for (String registeredService : registeredServices) {
                    try {
                        // 查询该服务的所有实例
                        com.alibaba.nacos.api.naming.pojo.Instance matchedInstance = 
                            nacosMcpRegistrationService.findInstanceByIpAndPort(registeredService, localIp, serverPort);
                        if (matchedInstance != null) {
                            matchedServices.add(registeredService);
                        }
                    } catch (Exception e) {
                        log.debug("Failed to check instance for service: {}", registeredService, e);
                    }
                }
                
                // 优先选择虚拟项目服务（virtual- 开头）
                if (!matchedServices.isEmpty()) {
                    for (String service : matchedServices) {
                        // 虚拟项目服务：以 virtual- 开头
                        if (service.startsWith("virtual-")) {
                            actualServiceName = service;
                            log.info("✅ Resolved serviceName from Nacos (virtual project): {} (IP: {}, Port: {})", 
                                actualServiceName, localIp, serverPort);
                            break;
                        }
                    }
                    // 如果没有虚拟项目服务，使用第一个匹配的服务
                    if (actualServiceName == null || actualServiceName.isEmpty()) {
                        actualServiceName = matchedServices.get(0);
                        log.info("✅ Resolved serviceName from Nacos by IP:Port match: {} (IP: {}, Port: {})", 
                            actualServiceName, localIp, serverPort);
                    }
                }
                
                // 如果还没有找到，尝试使用第一个 virtual- 开头的服务（虚拟项目）
                if (actualServiceName == null || actualServiceName.isEmpty()) {
                    for (String registeredService : registeredServices) {
                        // 虚拟项目服务：以 virtual- 开头
                        if (registeredService.startsWith("virtual-")) {
                            actualServiceName = registeredService;
                            log.info("✅ Resolved serviceName from Nacos (virtual project fallback): {}", actualServiceName);
                            break;
                        }
                    }
                }
                
                // 最后尝试使用第一个 zk-mcp- 开头的服务（普通 Dubbo 服务）
                if (actualServiceName == null || actualServiceName.isEmpty()) {
                    for (String registeredService : registeredServices) {
                        if (registeredService.startsWith("zk-mcp-")) {
                            actualServiceName = registeredService;
                            log.info("✅ Resolved serviceName from Nacos (dubbo service fallback): {}", actualServiceName);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to resolve serviceName from Nacos", e);
            }
        }
        
        // 如果还是没有 serviceName，尝试从 Host header 解析
        if ((actualServiceName == null || actualServiceName.isEmpty()) && hostHeader != null) {
            log.debug("Trying to resolve serviceName from Host header: {}", hostHeader);
            // 这里可以根据实际需求实现解析逻辑
        }
        
        if (actualServiceName == null || actualServiceName.isEmpty()) {
            log.warn("⚠️ Standard SSE endpoint called without serviceName and cannot resolve from Nacos");
            // 尝试从所有虚拟项目中查找（如果只有一个虚拟项目，使用它）
            List<VirtualProjectService.VirtualProjectInfo> virtualProjects = virtualProjectService.getAllVirtualProjects();
            if (virtualProjects != null && virtualProjects.size() == 1) {
                VirtualProjectService.VirtualProjectInfo vp = virtualProjects.get(0);
                if (vp.getEndpoint() != null) {
                    String endpoint = vp.getEndpoint().getEndpointName();
                    log.info("📝 Using single virtual project endpoint: {}", endpoint);
                    return handleSse(endpoint);
                }
            } else if (virtualProjects != null && virtualProjects.size() > 1) {
                log.warn("⚠️ Multiple virtual projects found ({}), cannot auto-select endpoint", virtualProjects.size());
            }
            // 不返回 400，而是尝试使用默认处理（允许后续通过 endpoint 事件指定）
            // 返回一个通用的 SSE 连接，让客户端通过后续的 endpoint 事件来指定服务
            return handleSseWithoutEndpoint();
        }
        
        log.info("📡 Standard SSE connection request with serviceName: {}", actualServiceName);
        
        // 解析 endpoint
        // 如果 serviceName 以 virtual- 开头，去掉前缀再解析
        String tryServiceName = actualServiceName;
        if (actualServiceName.startsWith("virtual-")) {
            tryServiceName = actualServiceName.substring("virtual-".length());
            log.debug("🔍 ServiceName '{}' starts with virtual-, using '{}' for endpoint lookup", actualServiceName, tryServiceName);
        } else if (actualServiceName.startsWith("mcp-")) {
            // 向后兼容：如果以 mcp- 开头，也去掉前缀
            tryServiceName = actualServiceName.substring("mcp-".length());
            log.debug("🔍 ServiceName '{}' starts with mcp-, using '{}' for endpoint lookup", actualServiceName, tryServiceName);
        }
        
        String endpoint = resolveEndpointFromServiceName(tryServiceName);
        if (endpoint == null) {
            log.warn("⚠️ Cannot resolve endpoint from serviceName: {}, trying to use serviceName directly", tryServiceName);
            // 如果无法解析，直接使用 serviceName 作为 endpoint
            endpoint = tryServiceName;
        }
        
        return handleSse(endpoint);
    }
    
    /**
     * 处理没有明确 endpoint 的 SSE 连接
     * 返回一个通用的 SSE 连接，等待客户端通过后续消息指定 endpoint
     */
    private ResponseEntity<SseEmitter> handleSseWithoutEndpoint() {
        log.info("📡 SSE connection request without explicit endpoint, creating generic connection");
        
        // 创建 SseEmitter（超时时间 10 分钟，与 mcp-router-v3 保持一致）
        // 注意：实际会话超时由 SessionCleanupService 定期清理，这里设置较长的超时时间以避免过早断开
        // 但会话在 Redis 中的 TTL 是 10 分钟，超过 10 分钟未活跃会被清理
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        String sessionId = UUID.randomUUID().toString();
        
        // 注册 session（使用临时 endpoint）
        String tempEndpoint = "temp-" + sessionId;
        sseEmitterMap.put(sessionId, emitter);
        sessionManager.registerSseEmitter(sessionId, tempEndpoint, emitter);
        
        // 构建消息端点 URL（从请求头动态构建，参考 mcp-router-v3）
        // 注意：IP 请求时不包含 context-path，域名请求时包含 context-path
        String baseUrl = buildBaseUrlFromRequestForMessageEndpoint();
        String messageEndpoint = String.format("%s/mcp/message?sessionId=%s", baseUrl, sessionId);
        
        try {
            // 发送 endpoint 事件
            // 注意：使用 id() 避免空行，确保 SSE 格式正确
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .id(sessionId)
                    .data(messageEndpoint));
            
            // 启动心跳并保存任务引用（传递 sessionId 用于日志和清理）
            java.util.concurrent.ScheduledFuture<?> heartbeatTask = startHeartbeat(emitter, sessionId);
            heartbeatTasks.put(sessionId, heartbeatTask);
            
            // 设置完成和超时回调
            emitter.onCompletion(() -> {
                log.info("SSE connection completed for session: {}", sessionId);
                cleanupSession(sessionId);
            });
            
            emitter.onTimeout(() -> {
                log.warn("SSE connection timeout for session: {}", sessionId);
                cleanupSession(sessionId);
            });
            
            emitter.onError((ex) -> {
                // Broken pipe、Connection reset 和 already completed 是正常的客户端断开情况，降级为 DEBUG
                String errorMsg = ex.getMessage();
                if (ex instanceof IOException && errorMsg != null && 
                    (errorMsg.contains("Broken pipe") || errorMsg.contains("Connection reset"))) {
                    log.debug("ℹ️ Client disconnected ({}) for session: {}", errorMsg, sessionId);
                } else if (ex instanceof IllegalStateException && errorMsg != null && errorMsg.contains("already completed")) {
                    log.debug("ℹ️ SSE emitter already completed for session: {}", sessionId);
                } else {
                    log.error("SSE connection error for session: {}", sessionId, ex);
                }
                cleanupSession(sessionId);
            });
            
        } catch (IOException e) {
            log.error("Failed to send initial SSE event", e);
            emitter.completeWithError(e);
            return ResponseEntity.internalServerError().build();
        }
        
        // 设置 SSE 响应头（参考 mcp-router-v3）
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-transform")
                .header("Connection", "keep-alive")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }
    
    /**
     * SSE 端点：GET /sse/{endpoint}
     */
    @GetMapping(value = "/sse/{endpoint}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> sse(@PathVariable String endpoint) {
        log.info("📡 SSE connection request for endpoint: {}", endpoint);
        return handleSse(endpoint);
    }
    
    /**
     * 处理 SSE 连接
     */
    private ResponseEntity<SseEmitter> handleSse(String endpoint) {
        // 解析 endpoint
        EndpointResolver.EndpointInfo endpointInfo = endpointResolver.resolveEndpoint(endpoint)
                .orElse(null);

        String mcpServiceName;
        if (endpointInfo == null) {
            log.warn("⚠️ Endpoint not found: {}, but creating SSE connection anyway", endpoint);
            // 如果无法解析 endpoint，判断是否为虚拟项目，使用 virtual-{endpoint} 格式
            // 否则使用 endpoint 本身作为 serviceName（支持直接使用 MCP 服务名称如 zk-mcp-*）
            // 这里无法判断是否为虚拟项目，所以先尝试使用 endpoint 本身
            mcpServiceName = endpoint;
        } else {
            // 使用解析后的 mcpServiceName（虚拟项目会是 virtual-{endpointName} 格式）
            mcpServiceName = endpointInfo.getMcpServiceName();
            log.info("✅ Resolved endpoint '{}' to MCP service: {}", endpoint, mcpServiceName);
        }

        // 创建 SseEmitter（超时时间 10 分钟，与 mcp-router-v3 保持一致）
        // 注意：实际会话超时由 SessionCleanupService 定期清理，这里设置较长的超时时间以避免过早断开
        // 但会话在 Redis 中的 TTL 是 10 分钟，超过 10 分钟未活跃会被清理
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        String sessionId = UUID.randomUUID().toString();

        // 注册 session（WebMVC 模式使用 SseEmitter）
        // 参考 mcp-router-v3 的 initializeSession：先注册 serviceName，再注册 emitter
        sseEmitterMap.put(sessionId, emitter);
        sessionManager.registerSseEmitter(sessionId, endpoint, emitter);

        // 注册 serviceName（参考 mcp-router-v3 的 registerSessionService）
        if (mcpServiceName != null && !mcpServiceName.isEmpty()) {
            sessionManager.registerSessionService(sessionId, mcpServiceName);
            log.info("✅ Registered serviceName for SSE connection: sessionId={}, serviceName={}", sessionId, mcpServiceName);
        }

        // 初始化时调用 touch（参考 mcp-router-v3 的 initializeSession）
        try {
            sessionManager.touch(sessionId);
        } catch (Exception e) {
            log.warn("⚠️ Failed to touch session during initialization: {}", e.getMessage());
        }

        // 构建消息端点 URL（从请求头动态构建，参考 mcp-router-v3）
        // 如果有 serviceName，使用 /mcp/{serviceName}/message 格式；否则使用 /mcp/message 格式
        // 注意：IP 请求时不包含 context-path，域名请求时包含 context-path
        String baseUrl = buildBaseUrlFromRequestForMessageEndpoint();
        String messageEndpoint;
        if (mcpServiceName != null && !mcpServiceName.isEmpty()) {
            // 路径参数方式：/mcp/{serviceName}/message?sessionId={sessionId}
            messageEndpoint = String.format("%s/mcp/%s/message?sessionId=%s", baseUrl, mcpServiceName, sessionId);
        } else {
            // 查询参数方式：/mcp/message?sessionId={sessionId}
            messageEndpoint = String.format("%s/mcp/message?sessionId=%s", baseUrl, sessionId);
        }
        log.info("📡 Generated message endpoint: serviceName={}, messageEndpoint={}", mcpServiceName, messageEndpoint);

        try {
            // 发送 endpoint 事件（客户端收到后会通过 POST /mcp/message 发送 initialize 和 tools/list 请求）
            // 注意：使用 id() 避免空行，确保 SSE 格式正确
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .id(sessionId)
                    .data(messageEndpoint));

            // 启动心跳并保存任务引用（传递 sessionId 用于日志和清理）
            java.util.concurrent.ScheduledFuture<?> heartbeatTask = startHeartbeat(emitter, sessionId);
            heartbeatTasks.put(sessionId, heartbeatTask);

            // 设置完成和超时回调
            emitter.onCompletion(() -> {
                log.info("SSE connection completed for session: {}", sessionId);
                cleanupSession(sessionId);
            });

            emitter.onTimeout(() -> {
                log.warn("SSE connection timeout for session: {}", sessionId);
                cleanupSession(sessionId);
            });

            emitter.onError((ex) -> {
                // Broken pipe、Connection reset 和 already completed 是正常的客户端断开情况，降级为 DEBUG
                String errorMsg = ex.getMessage();
                if (ex instanceof IOException && errorMsg != null &&
                    (errorMsg.contains("Broken pipe") || errorMsg.contains("Connection reset"))) {
                    log.debug("ℹ️ Client disconnected ({}) for session: {}", errorMsg, sessionId);
                } else if (ex instanceof IllegalStateException && errorMsg != null && errorMsg.contains("already completed")) {
                    log.debug("ℹ️ SSE emitter already completed for session: {}", sessionId);
                } else {
                    log.error("SSE connection error for session: {}", sessionId, ex);
                }
                cleanupSession(sessionId);
            });

        } catch (IOException e) {
            log.error("Failed to send initial SSE event", e);
            emitter.completeWithError(e);
            return ResponseEntity.internalServerError().build();
        }

        // 设置 SSE 响应头（参考 mcp-router-v3）
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-transform")
                .header("Connection", "keep-alive")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }
    
    /**
     * 启动心跳
     * 参考 mcp-router-v3 的实现，每15秒发送心跳事件并更新会话活跃时间
     */
    private java.util.concurrent.ScheduledFuture<?> startHeartbeat(SseEmitter emitter, String sessionId) {
        // 每15秒发送一次心跳，参考 mcp-router-v3 的实现
        return executorService.scheduleAtFixedRate(() -> {
            try {
                // 检查 emitter 是否仍然有效
                if (sseEmitterMap.containsKey(sessionId) && emitter != null) {
                    // 不发送心跳事件，只更新会话活跃时间（touch session）
                    // 原因：MCP 客户端不识别 "heartbeat" 事件类型，会报错
                    // 心跳的目的是保持连接活跃，通过 touch 更新会话时间即可
                    sessionManager.touch(sessionId);
                    
                    // 移除心跳日志，减少日志输出（只在 trace 级别记录）
                    log.trace("💓 Heartbeat (touch only): sessionId={}", sessionId);
                }
                // 移除无效心跳的日志，减少日志输出
            } catch (Exception e) {
                // 由于不再发送心跳事件，不会抛出 IOException
                // 只捕获通用异常，记录日志即可（降低日志级别）
                log.debug("⚠️ Heartbeat error: sessionId={}, error={}", sessionId, e.getMessage());
            }
        }, 15, 15, TimeUnit.SECONDS); // 初始延迟15秒，之后每15秒执行一次
    }
    
    /**
     * 从服务名称解析 endpoint
     */
    private String resolveEndpointFromServiceName(String serviceName) {
        // 1. 如果是虚拟项目服务（以 mcp- 开头），尝试查找虚拟项目
        // 例如：mcp-data-analysis -> data-analysis
        if (serviceName.startsWith("mcp-")) {
            String endpointName = serviceName.substring(4); // 去掉 "mcp-" 前缀
            log.debug("🔍 Detected virtual project service name: {}, trying to resolve endpoint: {}", serviceName, endpointName);
            try {
                // 尝试查找虚拟项目
                VirtualProjectService.VirtualProjectInfo virtualProject = 
                        virtualProjectService.getVirtualProjectByEndpointName(endpointName);
                if (virtualProject != null && virtualProject.getEndpoint() != null) {
                    String resolvedEndpoint = virtualProject.getEndpoint().getEndpointName();
                    log.info("✅ Resolved virtual project service '{}' to endpoint: {}", serviceName, resolvedEndpoint);
                    return resolvedEndpoint;
                }
            } catch (Exception e) {
                log.debug("Failed to resolve endpoint for virtual project service: {}", serviceName, e);
            }
        }
        
        // 2. 如果服务名称格式是 zk-mcp-{interface}-{version}，尝试提取接口名
        if (serviceName.startsWith("zk-mcp-")) {
            String withoutPrefix = serviceName.substring("zk-mcp-".length());
            // 提取版本号前的部分作为接口名
            String[] parts = withoutPrefix.split("-[0-9]+\\.[0-9]+\\.[0-9]+$");
            if (parts.length > 0) {
                String interfacePart = parts[0];
                // 尝试查找对应的项目
                // 这里简化处理，直接返回服务名称
                return serviceName;
            }
        }
        
        // 3. 尝试作为 endpoint 直接解析
        if (endpointResolver.resolveEndpoint(serviceName).isPresent()) {
            return serviceName;
        }
        
        return null;
    }
    
    /**
     * 为 message endpoint 构建 Base URL
     * 根据请求类型（IP vs 域名）决定是否包含 context-path：
     * - IP 请求：不包含 context-path（如：http://10.138.21.246:8080）
     * - 域名请求：包含 context-path（如：http://srv.test.pajk.com/mcp-metainfo）
     */
    private String buildBaseUrlFromRequestForMessageEndpoint() {
        try {
            org.springframework.web.context.request.RequestAttributes requestAttributes = 
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (requestAttributes != null) {
                org.springframework.web.context.request.ServletRequestAttributes servletRequestAttributes = 
                        (org.springframework.web.context.request.ServletRequestAttributes) requestAttributes;
                jakarta.servlet.http.HttpServletRequest request = servletRequestAttributes.getRequest();
                
                // 获取 Host（优先使用 X-Forwarded-Host，否则使用 Host 头）
                String host = request.getHeader("X-Forwarded-Host");
                if (host == null || host.isEmpty()) {
                    host = request.getHeader("x-forwarded-host");
                }
                if (host == null || host.isEmpty()) {
                    host = request.getHeader("Host");
                }
                
                // 判断是 IP 还是域名
                boolean isIpAddress = isIpAddress(host);
                
                // IP 请求不包含 context-path，域名请求包含 context-path
                return buildBaseUrlFromRequest(!isIpAddress);
            }
        } catch (Exception e) {
            log.debug("Failed to detect request type, defaulting to include context-path: {}", e.getMessage());
        }
        
        // 默认包含 context-path（安全起见，假设是域名请求）
        return buildBaseUrlFromRequest(true);
    }
    
    /**
     * 判断字符串是否是 IP 地址
     * 
     * @param host Host 字符串（可能包含端口，如 "10.138.21.246:8080"）
     * @return true 如果是 IP 地址，false 如果是域名
     */
    private boolean isIpAddress(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        
        // 移除端口号
        String hostWithoutPort = host;
        if (host.contains(":")) {
            hostWithoutPort = host.split(":")[0];
        }
        
        // 简单的 IP 地址检测：检查是否匹配 IPv4 格式（xxx.xxx.xxx.xxx）
        // 注意：这是一个简化的检测，不处理 IPv6
        return hostWithoutPort.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
    }
    
    /**
     * 从请求头构建 Base URL
     * 参考 mcp-router-v3 的实现，优先使用代理头（X-Forwarded-Host, X-Forwarded-Proto）
     * 支持 context-path 和域名配置（生产环境）
     * 
     * @param includeContextPath 是否包含 context-path
     */
    private String buildBaseUrlFromRequest(boolean includeContextPath) {
        String contextPath = "";
        
        // 如果不需要包含 context-path，直接跳过 context-path 提取
        if (!includeContextPath) {
            contextPath = "";
        } else {
            try {
                org.springframework.web.context.request.RequestAttributes requestAttributes = 
                        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
                if (requestAttributes != null) {
                    org.springframework.web.context.request.ServletRequestAttributes servletRequestAttributes = 
                            (org.springframework.web.context.request.ServletRequestAttributes) requestAttributes;
                    jakarta.servlet.http.HttpServletRequest request = servletRequestAttributes.getRequest();
                    
                    // 优先从 HttpServletRequest 获取 context-path（最准确）
                    String requestContextPath = request.getContextPath();
                    if (requestContextPath != null && !requestContextPath.isEmpty() && !requestContextPath.equals("/")) {
                        contextPath = requestContextPath;
                        // 确保 context-path 不以 / 结尾（除非是根路径）
                        if (contextPath.endsWith("/") && contextPath.length() > 1) {
                            contextPath = contextPath.substring(0, contextPath.length() - 1);
                        }
                    } else {
                        // 如果从请求中获取不到，则从配置文件读取
                        contextPath = environment.getProperty("server.servlet.context-path", "");
                        // 确保 context-path 以 / 开头，但不以 / 结尾（除非是根路径）
                        if (contextPath != null && !contextPath.isEmpty() && !contextPath.equals("/")) {
                            if (!contextPath.startsWith("/")) {
                                contextPath = "/" + contextPath;
                            }
                            // 移除末尾的 /（除非是根路径）
                            if (contextPath.endsWith("/") && contextPath.length() > 1) {
                                contextPath = contextPath.substring(0, contextPath.length() - 1);
                            }
                        } else {
                            contextPath = "";
                        }
                    }
                    
                    // 支持 X-Forwarded-Prefix 来获取 context-path（反向代理环境）
                    String forwardedPrefix = request.getHeader("X-Forwarded-Prefix");
                    if (forwardedPrefix == null || forwardedPrefix.isEmpty()) {
                        forwardedPrefix = request.getHeader("x-forwarded-prefix");
                    }
                    // 如果从 X-Forwarded-Prefix 获取到 context-path，优先使用它
                    if (forwardedPrefix != null && !forwardedPrefix.isEmpty()) {
                        String prefixContextPath = forwardedPrefix.trim();
                        // 确保以 / 开头
                        if (!prefixContextPath.startsWith("/")) {
                            prefixContextPath = "/" + prefixContextPath;
                        }
                        // 移除末尾的斜杠
                        if (prefixContextPath.endsWith("/") && prefixContextPath.length() > 1) {
                            prefixContextPath = prefixContextPath.substring(0, prefixContextPath.length() - 1);
                        }
                        contextPath = prefixContextPath;
                        log.info("✅ Extracted context-path from X-Forwarded-Prefix: {}", contextPath);
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to extract context-path: {}", e.getMessage());
            }
        }
        
        try {
            org.springframework.web.context.request.RequestAttributes requestAttributes = 
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (requestAttributes != null) {
                org.springframework.web.context.request.ServletRequestAttributes servletRequestAttributes = 
                        (org.springframework.web.context.request.ServletRequestAttributes) requestAttributes;
                jakarta.servlet.http.HttpServletRequest request = servletRequestAttributes.getRequest();
                
                // 优先读取代理相关头（不区分大小写）
                String forwardedProto = request.getHeader("X-Forwarded-Proto");
                if (forwardedProto == null) {
                    forwardedProto = request.getHeader("x-forwarded-proto");
                }
                String forwardedHost = request.getHeader("X-Forwarded-Host");
                if (forwardedHost == null) {
                    forwardedHost = request.getHeader("x-forwarded-host");
                }
                String forwardedPort = request.getHeader("X-Forwarded-Port");
                if (forwardedPort == null) {
                    forwardedPort = request.getHeader("x-forwarded-port");
                }
                
                String scheme;
                String hostPort;
                
                log.debug("🔍 Building base URL - forwardedProto: {}, forwardedHost: {}, forwardedPort: {}, Host: {}, contextPath: {}", 
                        forwardedProto, forwardedHost, forwardedPort, request.getHeader("Host"), contextPath);
                
                if (forwardedHost != null && !forwardedHost.isEmpty()) {
                    scheme = (forwardedProto != null && !forwardedProto.isEmpty()) ? forwardedProto : "http";
                    hostPort = forwardedHost;
                    // 如果 X-Forwarded-Host 不包含端口，且 X-Forwarded-Port 存在，则添加端口
                    // 但如果是标准端口（80/443），则不添加端口号（生产环境通常使用域名，不需要端口）
                    if (!hostPort.contains(":") && forwardedPort != null && !forwardedPort.isEmpty()) {
                        try {
                            int port = Integer.parseInt(forwardedPort);
                            // 只有非标准端口才添加
                            if (!((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))) {
                                hostPort = hostPort + ":" + forwardedPort;
                            }
                        } catch (NumberFormatException e) {
                            log.debug("Invalid forwarded port: {}", forwardedPort);
                        }
                    }
                    String baseUrl = scheme + "://" + hostPort + contextPath;
                    log.info("✅ Built base URL from forwarded headers: {}", baseUrl);
                    return baseUrl;
                }
                
                // 其次使用 Host 头与请求 scheme
                String host = request.getHeader("Host");
                if (host != null && !host.isEmpty()) {
                    String reqScheme = request.getScheme();
                    if (reqScheme == null || reqScheme.isEmpty()) {
                        reqScheme = "http";
                    }
                    // 处理 Host 头中的端口（如果是标准端口，则移除，生产环境通常使用域名）
                    String hostWithoutPort = host;
                    if (host.contains(":")) {
                        String[] parts = host.split(":");
                        if (parts.length == 2) {
                            try {
                                int port = Integer.parseInt(parts[1]);
                                // 如果是标准端口，移除端口号（生产环境使用域名，不需要端口）
                                if ((reqScheme.equals("http") && port == 80) || 
                                    (reqScheme.equals("https") && port == 443)) {
                                    hostWithoutPort = parts[0];
                                }
                            } catch (NumberFormatException e) {
                                // 端口号解析失败，保持原样
                            }
                        }
                    }
                    String baseUrl = reqScheme + "://" + hostWithoutPort + contextPath;
                    log.info("✅ Built base URL from Host header: {}", baseUrl);
                    return baseUrl;
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to build base URL from request headers: {}, falling back to default", e.getMessage());
        }
        
        // 回退到默认配置（包含 context-path）
        String defaultPort = environment.getProperty("server.port", "9091");
        String baseUrl = "http://127.0.0.1:" + defaultPort + contextPath;
        log.warn("⚠️ Built base URL from default config (fallback): {}", baseUrl);
        return baseUrl;
    }
    
    /**
     * 清理 session
     * 参考 mcp-router-v3 的实现，完善清理逻辑
     */
    private void cleanupSession(String sessionId) {
        log.info("🧹 Cleaning up session: {}", sessionId);
        
        // 取消心跳任务
        java.util.concurrent.ScheduledFuture<?> heartbeatTask = heartbeatTasks.remove(sessionId);
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel(false);
            log.debug("Cancelled heartbeat task for session: {}", sessionId);
        }
        
        // 移除 SSE emitter
        SseEmitter emitter = sseEmitterMap.remove(sessionId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("Failed to complete emitter for session: {}, error: {}", sessionId, e.getMessage());
            }
        }
        
        // 从 session manager 移除会话
        sessionManager.removeSession(sessionId);
        
        log.info("✅ Session cleaned up: {}", sessionId);
    }
}

