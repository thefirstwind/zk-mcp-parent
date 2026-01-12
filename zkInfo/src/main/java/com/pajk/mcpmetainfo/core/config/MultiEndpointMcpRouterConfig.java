package com.pajk.mcpmetainfo.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.pajk.mcpmetainfo.core.model.Project;
import com.pajk.mcpmetainfo.core.service.EndpointResolver;
import com.pajk.mcpmetainfo.core.service.McpExecutorService;
import com.pajk.mcpmetainfo.core.service.McpSessionManager;
import com.pajk.mcpmetainfo.core.service.NacosMcpRegistrationService;
import com.pajk.mcpmetainfo.core.service.ProjectManagementService;
import com.pajk.mcpmetainfo.core.service.VirtualProjectRegistrationService;
import com.pajk.mcpmetainfo.core.service.VirtualProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * 多Endpoint MCP路由配置
 * 支持动态的 /sse/{endpoint} 格式，其中endpoint可以是虚拟项目ID或实际项目名称
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class MultiEndpointMcpRouterConfig {
    
    private final EndpointResolver endpointResolver;
    private final McpSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final VirtualProjectRegistrationService virtualProjectRegistrationService;
    private final VirtualProjectService virtualProjectService;
    private final McpExecutorService mcpExecutorService;
    private final ProjectManagementService projectManagementService;
    private final NacosMcpRegistrationService nacosMcpRegistrationService;
    
    /**
     * 创建多Endpoint路由函数
     * 支持：
     * - GET /sse/{endpoint} - 建立SSE连接
     * - POST /mcp/{endpoint}/message?sessionId=xxx - 发送MCP消息
     * - POST /mcp/message?sessionId=xxx - 发送MCP消息（通过sessionId查找endpoint）
     * 
     * 注意：此 RouterFunction 已被禁用，因为应用使用 WebMVC 模式（servlet），
     * SSE 端点由 SseController（WebMVC）处理，而不是 WebFlux RouterFunction。
     * 如果需要启用 WebFlux 路由，请将 application.yml 中的 web-application-type 改为 reactive。
     */
    // @Bean
    // @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(name = "mcpRouterFunction")
    // @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.main.web-application-type", havingValue = "reactive")
    public RouterFunction<ServerResponse> multiEndpointRouterFunction() {
        log.info("Creating multi-endpoint MCP router function");
        
        return route()
                // 标准 SSE 端点：GET /sse（兼容 mcp-router-v3，根据服务名称自动解析 endpoint）
                .GET("/sse", this::handleSseStandard)
                // SSE端点：GET /sse/{endpoint}
                .GET("/sse/{endpoint}", this::handleSseWithEndpoint)
                // 消息端点：POST /mcp/{endpoint}/message?sessionId=xxx
                .POST("/mcp/{endpoint}/message", this::handleMessageWithEndpoint)
                // 通用消息端点：POST /mcp/message?sessionId=xxx
                .POST("/mcp/message", this::handleMessage)
                // CORS预检请求
                .OPTIONS("/sse", req -> ServerResponse.ok().build())
                .OPTIONS("/sse/{endpoint}", req -> ServerResponse.ok().build())
                .OPTIONS("/mcp/{endpoint}/message", req -> ServerResponse.ok().build())
                .OPTIONS("/mcp/message", req -> ServerResponse.ok().build())
                .build();
    }
    
    /**
     * 处理标准 SSE 连接请求：GET /sse（兼容 mcp-router-v3）
     * 根据服务名称（从请求头、查询参数或 Nacos）自动解析 endpoint
     */
    private Mono<ServerResponse> handleSseStandard(ServerRequest request) {
        // 1. 尝试从查询参数中获取服务名称
        String serviceName = request.queryParam("serviceName").orElse(null);
        
        // 2. 如果没有提供 serviceName，尝试从请求头中获取
        if (serviceName == null || serviceName.isEmpty()) {
            serviceName = request.headers().firstHeader("X-Service-Name");
        }
        
        // 3. 如果仍然没有，尝试从 Nacos 查询（根据请求的 IP 和端口匹配服务实例）
        if (serviceName == null || serviceName.isEmpty()) {
            serviceName = resolveServiceNameFromNacos(request);
        }
        
        // 4. 如果仍然无法确定服务名称，返回错误
        if (serviceName == null || serviceName.isEmpty()) {
            log.warn("⚠️ Standard SSE endpoint called without serviceName, cannot determine endpoint");
            return ServerResponse.status(400)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue("{\"error\":\"serviceName parameter is required for /sse endpoint. Use /sse/{endpoint} or add ?serviceName=xxx\"}"));
        }
        
        log.info("📡 Standard SSE connection request with serviceName: {}", serviceName);
        
        // 根据服务名称解析 endpoint
        String endpoint = resolveEndpointFromServiceName(serviceName);
        
        if (endpoint == null) {
            log.warn("⚠️ Cannot resolve endpoint from serviceName: {}", serviceName);
            return ServerResponse.status(404)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue("{\"error\":\"Cannot resolve endpoint from serviceName: " + serviceName + "\"}"));
        }
        
        log.info("✅ Resolved serviceName '{}' to endpoint: {}", serviceName, endpoint);
        
        // 使用解析出的 endpoint 处理 SSE 连接
        return handleSseWithEndpointInternal(request, endpoint);
    }
    
    /**
     * 从 Nacos 查询服务名称（根据请求的 IP 和端口匹配服务实例）
     * 优化：使用缓存，避免每次都查询 Nacos
     */
    private final Map<String, String> serviceNameCache = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long lastCacheUpdate = 0;
    private static final long CACHE_TTL_MS = 5000; // 缓存5秒
    
    private String resolveServiceNameFromNacos(ServerRequest request) {
        // 检查缓存
        long now = System.currentTimeMillis();
        if (now - lastCacheUpdate < CACHE_TTL_MS && !serviceNameCache.isEmpty()) {
            // 从缓存中查找匹配的服务
            String localIp = nacosMcpRegistrationService.getLocalIp();
            int localPort = nacosMcpRegistrationService.getServerPort();
            String cacheKey = localIp + ":" + localPort;
            String cachedServiceName = serviceNameCache.get(cacheKey);
            if (cachedServiceName != null) {
                log.debug("✅ Found serviceName from cache: {}", cachedServiceName);
                return cachedServiceName;
            }
        }
        
        try {
            // 获取本机 IP（zkInfo 服务运行的 IP）
            String localIp = nacosMcpRegistrationService.getLocalIp();
            int localPort = nacosMcpRegistrationService.getServerPort();
            
            log.debug("🔍 Trying to resolve serviceName from Nacos: localIp={}, localPort={}", 
                    localIp, localPort);
            
            // 从 Nacos 查询所有 MCP 服务
            NamingService namingService = nacosMcpRegistrationService.getNamingService();
            String serviceGroup = nacosMcpRegistrationService.getServiceGroup();
            
            // 查询指定 group 下的所有服务（限制查询数量，避免超时）
            com.alibaba.nacos.api.naming.pojo.ListView<String> servicesList = 
                    namingService.getServicesOfServer(1, 100, serviceGroup); // 限制最多100个服务
            
            if (servicesList != null && servicesList.getData() != null) {
                String cacheKey = localIp + ":" + localPort;
                for (String serviceName : servicesList.getData()) {
                    try {
                        // 查询服务的所有实例（只查询健康的实例）
                        List<Instance> instances = namingService.selectInstances(serviceName, serviceGroup, true);
                        if (instances != null) {
                            for (Instance instance : instances) {
                                // 匹配 IP 和端口
                                if (localIp.equals(instance.getIp()) && localPort == instance.getPort()) {
                                    log.info("✅ Found matching service from Nacos: {} (IP: {}, Port: {})", 
                                            serviceName, instance.getIp(), instance.getPort());
                                    // 更新缓存
                                    serviceNameCache.put(cacheKey, serviceName);
                                    lastCacheUpdate = now;
                                    return serviceName;
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Failed to get instances for service: {}", serviceName, e);
                    }
                }
            }
            
            log.debug("⚠️ No matching service found in Nacos for IP: {}, Port: {}", localIp, localPort);
            return null;
        } catch (Exception e) {
            log.warn("Failed to resolve serviceName from Nacos", e);
            return null;
        }
    }
    
    /**
     * 根据服务名称解析 endpoint
     */
    private String resolveEndpointFromServiceName(String serviceName) {
        // 1. 如果是虚拟项目服务（以 mcp- 开头），尝试查找虚拟项目
        if (serviceName.startsWith("mcp-")) {
            String endpointName = serviceName.substring(4); // 去掉 "mcp-" 前缀
            try {
                Optional<EndpointResolver.EndpointInfo> endpointInfo = endpointResolver.resolveEndpoint(endpointName);
                if (endpointInfo.isPresent()) {
                    return endpointName;
                }
            } catch (Exception e) {
                log.debug("Failed to resolve endpoint for virtual project: {}", endpointName, e);
            }
        }
        
        // 2. 如果是 zk-mcp- 开头的服务，尝试提取接口名并查找对应的项目
        if (serviceName.startsWith("zk-mcp-")) {
            // 格式：zk-mcp-com-zkinfo-demo-service-orderservice-1.0.0
            // 需要提取接口名：com.pajk.mcpmetainfo.core.demo.service.OrderService
            String withoutPrefix = serviceName.substring(7); // 去掉 "zk-mcp-" 前缀
            // 查找最后一个 "-" 作为版本分隔符
            int lastDash = withoutPrefix.lastIndexOf("-");
            if (lastDash > 0) {
                String interfacePart = withoutPrefix.substring(0, lastDash);
                String version = withoutPrefix.substring(lastDash + 1);
                // 将 interfacePart 转换回接口名格式
                String interfaceName = interfacePart.replace("-", ".");
                log.debug("Extracted interfaceName: {}, version: {} from serviceName: {}", interfaceName, version, serviceName);
                
                // 查找包含该接口的项目
                try {
                    List<Project> projects = projectManagementService.getProjectsByService(interfaceName, version);
                    if (projects != null && !projects.isEmpty()) {
                        // 返回第一个项目的 projectCode 作为 endpoint
                        Project project = projects.get(0);
                        log.info("✅ Found project {} for service {}:{}", project.getProjectCode(), interfaceName, version);
                        return project.getProjectCode();
                    }
                } catch (Exception e) {
                    log.debug("Failed to find project for service: {}:{}", interfaceName, version, e);
                }
            }
        }
        
        return null;
    }
    
    /**
     * 内部方法：处理 SSE 连接（共享逻辑）
     */
    private Mono<ServerResponse> handleSseWithEndpointInternal(ServerRequest request, String endpoint) {
        log.info("📡 SSE connection request for endpoint: {}", endpoint);
        
        // 解析endpoint
        return Mono.fromCallable(() -> endpointResolver.resolveEndpoint(endpoint))
                .flatMap(optionalEndpointInfo -> {
                    if (!optionalEndpointInfo.isPresent()) {
                        log.warn("⚠️ Endpoint not found: {}", endpoint);
                        return ServerResponse.status(404)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue("{\"error\":\"Endpoint not found: " + endpoint + "\"}"));
                    }
                    EndpointResolver.EndpointInfo endpointInfo = optionalEndpointInfo.get();
                    String mcpServiceName = endpointInfo.getMcpServiceName();
                    log.info("✅ Resolved endpoint '{}' to MCP service: {}", endpoint, mcpServiceName);
                    
                    return buildSseResponse(request, endpoint, mcpServiceName);
                });
    }
    
    /**
     * 构建 SSE 响应（共享逻辑）
     */
    private Mono<ServerResponse> buildSseResponse(ServerRequest request, String endpoint, String mcpServiceName) {
        // 生成sessionId
        String sessionId = UUID.randomUUID().toString();
        
        // 创建SSE Sink
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer();
        sessionManager.registerSink(sessionId, endpoint, sink);
        
        // 构建消息端点URL
        // 注意：WebFluxSseClientTransport 期望的标准格式是：/mcp/message?sessionId=xxx
        // 但是 zkInfo 支持多 endpoint，所以路径是：/mcp/{endpoint}/message?sessionId=xxx
        // 为了兼容，我们使用标准格式，但需要在 /mcp/message 端点中根据 sessionId 查找 endpoint
        String baseUrl = buildBaseUrl(request);
        String messageEndpoint = String.format("%s/mcp/message?sessionId=%s", baseUrl, sessionId);
        
        // 发送endpoint事件
        // 注意：WebFluxSseClientTransport 期望的格式是：event:endpoint\ndata:<messageEndpoint URL>\n\n
        // 其中 messageEndpoint 应该是完整的 URL，例如：http://localhost:9091/mcp/message?sessionId=xxx
        ServerSentEvent<String> endpointEvent = ServerSentEvent.<String>builder()
                .event("endpoint")
                .data(messageEndpoint)  // 直接发送 URL，而不是 JSON 对象
                .build();
        
        // 心跳事件
        Flux<ServerSentEvent<String>> heartbeatFlux = Flux.interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.<String>builder()
                        .event("heartbeat")
                        .data("{\"type\":\"heartbeat\",\"timestamp\":" + System.currentTimeMillis() + "}")
                        .build());
        
        // 合并事件流
        // 注意：WebFluxSseClientTransport 通过 HTTP POST /mcp/message 发送消息，而不是通过 SSE 流
        // 所以这里只需要处理从 sink 发送的响应消息
        Flux<ServerSentEvent<String>> eventFlux = Flux.concat(
                Flux.just(endpointEvent),
                Flux.merge(
                        sink.asFlux().onBackpressureBuffer(1000),
                        heartbeatFlux
                                .doOnNext(tick -> {
                                    // 更新会话活跃时间
                                    sessionManager.touch(sessionId);
                                })
                                .onBackpressureBuffer(100)
                )
        )
        .share()
        .doOnSubscribe(subscription -> log.info("✅ Connection subscribed: sessionId={}, endpoint={}, mcpServiceName={}", 
                sessionId, endpoint, mcpServiceName))
        .doOnCancel(() -> {
            log.warn("❌ Connection cancelled: sessionId={}, endpoint={}, reason=client_disconnect", 
                    sessionId, endpoint);
            sessionManager.removeSession(sessionId);
        })
        .doOnError(error -> {
            log.error("❌ Connection error: sessionId={}, endpoint={}", sessionId, endpoint, error);
            sessionManager.removeSession(sessionId);
        })
        .doOnComplete(() -> log.info("✅ Connection completed: sessionId={}, endpoint={}", sessionId, endpoint));
        
        log.info("✅ SSE connection established: endpoint={}, sessionId={}, mcpServiceName={}", 
                endpoint, sessionId, mcpServiceName);
        
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache, no-transform")
                .header("Connection", "keep-alive")
                .header("X-Accel-Buffering", "no")
                .body(BodyInserters.fromServerSentEvents(eventFlux));
    }
    
    /**
     * 处理SSE连接请求：GET /sse/{endpoint}
     * endpoint可以是：
     * 1. 虚拟项目的endpointName或ID
     * 2. 实际项目的projectCode或projectName
     * 3. MCP服务名称（如：zk-mcp-com-zkinfo-demo-service-orderservice-1.0.0）
     */
    private Mono<ServerResponse> handleSseWithEndpoint(ServerRequest request) {
        String endpoint = request.pathVariable("endpoint");
        log.info("📡 SSE connection request for endpoint: {}", endpoint);
        
        // 快速检查：如果是服务名称格式（zk-mcp- 或 mcp- 开头），直接尝试解析为服务名称
        if (endpoint.startsWith("zk-mcp-") || endpoint.startsWith("mcp-")) {
            log.debug("🔍 Detected service name format: {}, trying to resolve directly", endpoint);
            String resolvedEndpoint = resolveEndpointFromServiceName(endpoint);
            if (resolvedEndpoint != null) {
                log.info("✅ Resolved service name '{}' to endpoint: {}", endpoint, resolvedEndpoint);
                // 使用解析出的 endpoint 解析
                return Mono.fromCallable(() -> endpointResolver.resolveEndpoint(resolvedEndpoint))
                        .subscribeOn(Schedulers.boundedElastic()) // 异步执行，避免阻塞
                        .timeout(Duration.ofMillis(200)) // 设置超时，避免长时间等待
                        .flatMap(optionalEndpointInfo -> {
                            if (optionalEndpointInfo.isPresent()) {
                                EndpointResolver.EndpointInfo endpointInfo = optionalEndpointInfo.get();
                                String mcpServiceName = endpointInfo.getMcpServiceName();
                                log.info("✅ Resolved endpoint '{}' to MCP service: {}", resolvedEndpoint, mcpServiceName);
                                return buildSseResponse(request, resolvedEndpoint, mcpServiceName);
                            } else {
                                log.warn("⚠️ Cannot resolve endpoint: {}", resolvedEndpoint);
                                return ServerResponse.status(404)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(BodyInserters.fromValue("{\"error\":\"Endpoint not found: " + endpoint + "\"}"));
                            }
                        })
                        .onErrorResume(error -> {
                            log.error("❌ Error resolving service name: {}", endpoint, error);
                            return ServerResponse.status(500)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(BodyInserters.fromValue("{\"error\":\"Internal error resolving endpoint: " + endpoint + "\"}"));
                        });
            }
        }
        
        // 首先尝试使用 EndpointResolver 解析（支持虚拟项目ID/名称、实际项目code/name）
        return Mono.fromCallable(() -> endpointResolver.resolveEndpoint(endpoint))
                .subscribeOn(Schedulers.boundedElastic()) // 异步执行
                .timeout(Duration.ofMillis(200)) // 设置超时
                .flatMap(optionalEndpointInfo -> {
                    if (optionalEndpointInfo.isPresent()) {
                        // 找到了，使用解析出的 endpoint
                        EndpointResolver.EndpointInfo endpointInfo = optionalEndpointInfo.get();
                        String mcpServiceName = endpointInfo.getMcpServiceName();
                        log.info("✅ Resolved endpoint '{}' to MCP service: {}", endpoint, mcpServiceName);
                        return buildSseResponse(request, endpoint, mcpServiceName);
                    }
                    
                    // 如果 EndpointResolver 无法解析，尝试将 endpoint 作为服务名称处理
                    log.debug("⚠️ EndpointResolver could not resolve '{}', trying as service name", endpoint);
                    String resolvedEndpoint = resolveEndpointFromServiceName(endpoint);
                    
                    if (resolvedEndpoint != null) {
                        log.info("✅ Resolved service name '{}' to endpoint: {}", endpoint, resolvedEndpoint);
                        // 使用解析出的 endpoint 再次尝试解析
                        return Mono.fromCallable(() -> endpointResolver.resolveEndpoint(resolvedEndpoint))
                                .subscribeOn(Schedulers.boundedElastic())
                                .timeout(Duration.ofMillis(200))
                                .flatMap(optionalEndpointInfo2 -> {
                                    if (optionalEndpointInfo2.isPresent()) {
                                        EndpointResolver.EndpointInfo endpointInfo = optionalEndpointInfo2.get();
                                        String mcpServiceName = endpointInfo.getMcpServiceName();
                                        log.info("✅ Resolved endpoint '{}' to MCP service: {}", resolvedEndpoint, mcpServiceName);
                                        return buildSseResponse(request, resolvedEndpoint, mcpServiceName);
                                    } else {
                                        log.warn("⚠️ Cannot resolve endpoint: {}", resolvedEndpoint);
                                        return ServerResponse.status(404)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .body(BodyInserters.fromValue("{\"error\":\"Endpoint not found: " + endpoint + "\"}"));
                                    }
                                });
                    } else {
                        log.warn("⚠️ Cannot resolve endpoint or service name: {}", endpoint);
                        return ServerResponse.status(404)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue("{\"error\":\"Endpoint not found: " + endpoint + "\"}"));
                    }
                })
                .onErrorResume(error -> {
                    log.error("❌ Error resolving endpoint: {}", endpoint, error);
                    return ServerResponse.status(500)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(BodyInserters.fromValue("{\"error\":\"Internal error resolving endpoint: " + endpoint + "\"}"));
                });
    }
    
    /**
     * 处理消息请求：POST /mcp/{endpoint}/message?sessionId=xxx
     */
    private Mono<ServerResponse> handleMessageWithEndpoint(ServerRequest request) {
        String endpoint = request.pathVariable("endpoint");
        String sessionId = request.queryParam("sessionId").orElse(UUID.randomUUID().toString());
        
        log.info("📨 MCP message request: endpoint={}, sessionId={}", endpoint, sessionId);
        
        return request.bodyToMono(String.class)
                .flatMap(body -> {
                    log.debug("📨 Message body: {}", body);
                    
                    // 解析endpoint
                    return Mono.fromCallable(() -> endpointResolver.resolveEndpoint(endpoint))
                            .flatMap(optionalEndpointInfo -> {
                                if (!optionalEndpointInfo.isPresent()) {
                                    log.warn("⚠️ Endpoint not found: {}", endpoint);
                                    return ServerResponse.status(404)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .body(BodyInserters.fromValue("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32601,\"message\":\"Endpoint not found: " + endpoint + "\"}}"));
                                }
                                EndpointResolver.EndpointInfo endpointInfo = optionalEndpointInfo.get();
                                String mcpServiceName = endpointInfo.getMcpServiceName();
                                
                                // 获取或创建Session（占位符）
                                return sessionManager.getOrCreateSession(endpoint)
                                        .then(handleMcpMessage(sessionId, endpoint, mcpServiceName, body));
                            });
                })
                .onErrorResume(error -> {
                    log.error("❌ Error handling message for endpoint: {}", endpoint, error);
                    return ServerResponse.status(500)
                            .body(BodyInserters.fromValue("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal error: " + error.getMessage() + "\"}}"));
                });
    }
    
    /**
     * 处理消息请求：POST /mcp/message?sessionId=xxx
     */
    private Mono<ServerResponse> handleMessage(ServerRequest request) {
        String sessionId = request.queryParam("sessionId").orElse(null);
        
        if (sessionId == null || sessionId.isEmpty()) {
            return ServerResponse.status(400)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32600,\"message\":\"sessionId is required\"}}"));
        }
        
        // 根据sessionId查找endpoint
        String endpoint = sessionManager.getEndpointForSession(sessionId);
        if (endpoint == null) {
            return ServerResponse.status(400)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32601,\"message\":\"Session not found: " + sessionId + "\"}}"));
        }
        
        log.info("📨 MCP message request: sessionId={}, resolved endpoint={}", sessionId, endpoint);
        
        return request.bodyToMono(String.class)
                .flatMap(body -> {
                    return Mono.fromCallable(() -> endpointResolver.resolveEndpoint(endpoint))
                            .flatMap(optionalEndpointInfo -> {
                                if (optionalEndpointInfo.isPresent()) {
                                    EndpointResolver.EndpointInfo endpointInfo = optionalEndpointInfo.get();
                                    String mcpServiceName = endpointInfo.getMcpServiceName();
                                    return handleMcpMessage(sessionId, endpoint, mcpServiceName, body);
                                } else {
                                    log.warn("⚠️ Endpoint not found: {}", endpoint);
                                    return ServerResponse.status(404)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .body(BodyInserters.fromValue("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32601,\"message\":\"Endpoint not found: " + endpoint + "\"}}"));
                                }
                            });
                });
    }
    
    /**
     * 处理MCP消息
     */
    private Mono<ServerResponse> handleMcpMessage(String sessionId, String endpoint, String mcpServiceName, String body) {
        try {
            // 解析JSON-RPC消息
            Map<String, Object> message = objectMapper.readValue(body, Map.class);
            String method = (String) message.get("method");
            String id = String.valueOf(message.get("id"));
            
            log.info("📨 Processing MCP message: sessionId={}, endpoint={}, method={}, id={}", 
                    sessionId, endpoint, method, id);
            
            // 获取SSE Sink
            Sinks.Many<ServerSentEvent<String>> sink = sessionManager.getSink(sessionId);
            if (sink == null) {
                log.warn("⚠️ SSE sink not found for sessionId={}, trying to wait for it", sessionId);
                // 如果 sink 不存在，尝试等待（最多 100ms，因为 mcp-router-v3 的初始化超时只有 200ms）
                return sessionManager.waitForSseSink(sessionId, 0)
                        .timeout(Duration.ofMillis(100))
                        .flatMap(s -> handleInitialize(sessionId, id, mcpServiceName, s))
                        .switchIfEmpty(Mono.defer(() -> {
                            log.error("❌ SSE sink not found for sessionId={} after waiting", sessionId);
                            return ServerResponse.badRequest()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(BodyInserters.fromValue("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{\"code\":-32601,\"message\":\"Session not found\"}}"));
                        }));
            }
            
            // 处理不同的MCP方法
            if ("initialize".equals(method)) {
                return handleInitialize(sessionId, id, mcpServiceName, sink);
            } else if ("tools/list".equals(method)) {
                return handleToolsList(sessionId, id, endpoint, sink);
            } else if ("tools/call".equals(method)) {
                return handleToolCall(sessionId, id, endpoint, message, sink);
            } else {
                // 其他方法
                return sendResponseViaSse(sink, id, "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"status\":\"received\",\"method\":\"" + method + "\"}}");
            }
        } catch (Exception e) {
            log.error("❌ Error processing MCP message", e);
            return ServerResponse.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"Parse error: " + e.getMessage() + "\"}}"));
        }
    }
    
    /**
     * 通过 SSE 流处理 initialize 请求（WebFluxSseClientTransport 通过 SSE 流发送）
     */
    private void handleInitializeViaSse(String sessionId, String id, String mcpServiceName, 
                                        Sinks.Many<ServerSentEvent<String>> sink) {
        try {
            log.info("📥 Processing initialize request via SSE: sessionId={}, id={}, mcpServiceName={}", 
                    sessionId, id, mcpServiceName);
            
            // 构建 initialize 响应
            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> serverInfo = new HashMap<>();
            serverInfo.put("name", mcpServiceName);
            serverInfo.put("version", "1.0.0");
            result.put("protocolVersion", "2024-11-05");
            result.put("capabilities", Map.of());
            result.put("serverInfo", serverInfo);
            response.put("result", result);
            
            String responseJson = objectMapper.writeValueAsString(response);
            log.info("📤 Sending initialize response via SSE: sessionId={}, response={}", sessionId, responseJson);
            
            // 通过 SSE sink 发送响应
            ServerSentEvent<String> sseEvent = ServerSentEvent.<String>builder()
                    .data(responseJson)
                    .build();
            
            Sinks.EmitResult emitResult = sink.tryEmitNext(sseEvent);
            if (emitResult.isSuccess()) {
                log.info("✅ Successfully sent initialize response via SSE: sessionId={}", sessionId);
            } else {
                log.error("❌ Failed to emit initialize response via SSE: sessionId={}, emitResult={}", 
                        sessionId, emitResult);
            }
        } catch (Exception e) {
            log.error("❌ Error handling initialize via SSE: sessionId={}", sessionId, e);
        }
    }
    
    /**
     * 通过 SSE 流处理 tools/list 请求
     */
    private void handleToolsListViaSse(String sessionId, String id, String endpoint, String mcpServiceName,
                                      Sinks.Many<ServerSentEvent<String>> sink) {
        try {
            log.info("📥 Processing tools/list request via SSE: sessionId={}, id={}, endpoint={}", 
                    sessionId, id, endpoint);
            
            // 获取工具列表（从 Nacos 查询）
            // 去掉 virtual- 前缀（如果存在）
            String actualEndpoint = endpoint;
            if (endpoint.startsWith("virtual-")) {
                actualEndpoint = endpoint.substring("virtual-".length());
            }
            
            List<Map<String, Object>> tools = virtualProjectRegistrationService.getVirtualProjectToolsByEndpointName(actualEndpoint);
            
            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            
            Map<String, Object> result = new HashMap<>();
            result.put("tools", tools);
            response.put("result", result);
            
            String responseJson = objectMapper.writeValueAsString(response);
            log.info("📤 Sending tools/list response via SSE: sessionId={}, toolsCount={}", sessionId, tools.size());
            
            // 通过 SSE sink 发送响应
            ServerSentEvent<String> sseEvent = ServerSentEvent.<String>builder()
                    .data(responseJson)
                    .build();
            
            Sinks.EmitResult emitResult = sink.tryEmitNext(sseEvent);
            if (emitResult.isSuccess()) {
                log.info("✅ Successfully sent tools/list response via SSE: sessionId={}", sessionId);
            } else {
                log.error("❌ Failed to emit tools/list response via SSE: sessionId={}, emitResult={}", 
                        sessionId, emitResult);
            }
        } catch (Exception e) {
            log.error("❌ Error handling tools/list via SSE: sessionId={}", sessionId, e);
        }
    }
    
    /**
     * 通过 SSE 流处理 tools/call 请求
     */
    private void handleToolCallViaSse(String sessionId, String id, Map<String, Object> params, 
                                      String endpoint, String mcpServiceName,
                                      Sinks.Many<ServerSentEvent<String>> sink) {
        try {
            log.info("📥 Processing tools/call request via SSE: sessionId={}, id={}, endpoint={}", 
                    sessionId, id, endpoint);
            
            // 提取工具名称和参数
            String toolName = (String) params.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
            
            // 将参数 Map 转换为 Object[] 数组（按参数顺序）
            Object[] args = arguments != null ? arguments.values().toArray() : new Object[0];
            
            // 执行工具调用
            McpExecutorService.McpCallResult callResult = mcpExecutorService.executeToolCallSync(toolName, args, 5000);
            Object result = callResult.getResult();
            
            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("content", List.of(Map.of("type", "text", "text", objectMapper.writeValueAsString(result))));
            response.put("result", resultMap);
            
            String responseJson = objectMapper.writeValueAsString(response);
            log.info("📤 Sending tools/call response via SSE: sessionId={}, toolName={}", sessionId, toolName);
            
            // 通过 SSE sink 发送响应
            ServerSentEvent<String> sseEvent = ServerSentEvent.<String>builder()
                    .data(responseJson)
                    .build();
            
            Sinks.EmitResult emitResult = sink.tryEmitNext(sseEvent);
            if (emitResult.isSuccess()) {
                log.info("✅ Successfully sent tools/call response via SSE: sessionId={}", sessionId);
            } else {
                log.error("❌ Failed to emit tools/call response via SSE: sessionId={}, emitResult={}", 
                        sessionId, emitResult);
            }
        } catch (Exception e) {
            log.error("❌ Error handling tools/call via SSE: sessionId={}", sessionId, e);
            
            // 发送错误响应
            try {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("jsonrpc", "2.0");
                errorResponse.put("id", id);
                Map<String, Object> error = new HashMap<>();
                error.put("code", -32603);
                error.put("message", "Internal error: " + e.getMessage());
                errorResponse.put("error", error);
                
                String errorJson = objectMapper.writeValueAsString(errorResponse);
                ServerSentEvent<String> errorEvent = ServerSentEvent.<String>builder()
                        .data(errorJson)
                        .build();
                sink.tryEmitNext(errorEvent);
            } catch (Exception ex) {
                log.error("❌ Failed to send error response via SSE", ex);
            }
        }
    }
    
    /**
     * 处理initialize请求（通过 HTTP POST）
     * 参考 mcp-router-v3：通过 SSE sink 发送响应，HTTP 返回 202 Accepted
     * 注意：mcp-router-v3 的初始化超时只有 200ms，所以必须立即响应
     */
    private Mono<ServerResponse> handleInitialize(String sessionId, String id, String mcpServiceName, 
                                                  Sinks.Many<ServerSentEvent<String>> sink) {
        log.info("📥 Processing initialize request: sessionId={}, id={}, mcpServiceName={}", sessionId, id, mcpServiceName);
        
        // 立即构建响应（不等待）
        String response = String.format(
                "{\"jsonrpc\":\"2.0\",\"id\":%s,\"result\":{" +
                        "\"protocolVersion\":\"2024-11-05\"," +
                        "\"capabilities\":{\"tools\":{\"listChanged\":true}}," +
                        "\"serverInfo\":{\"name\":\"%s\",\"version\":\"1.0.0\"}" +
                        "}}",
                id, mcpServiceName
        );
        
        // 立即通过 SSE sink 发送响应（不等待）
        ServerSentEvent<String> sseEvent = ServerSentEvent.<String>builder()
                .data(response)
                .build();
        Sinks.EmitResult emitResult = sink.tryEmitNext(sseEvent);
        if (emitResult.isSuccess()) {
            log.info("✅ Successfully sent initialize response via SSE: sessionId={}, id={}", sessionId, id);
        } else {
            log.error("❌ Failed to emit SSE event: sessionId={}, id={}, result={}", sessionId, id, emitResult);
            // 如果 SSE 发送失败，回退到 HTTP 响应
            return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(response));
        }
        
        // HTTP 响应返回 202 Accepted（表示响应已通过 SSE 发送）
        return ServerResponse.accepted()
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{\"status\":\"accepted\",\"message\":\"Request accepted, response will be sent via SSE\"}"));
    }
    
    /**
     * 处理tools/list请求
     * 参考 mcp-router-v3：通过 SSE sink 发送响应，HTTP 返回 202 Accepted
     */
    private Mono<ServerResponse> handleToolsList(String sessionId, String id, String endpoint, 
                                                 Sinks.Many<ServerSentEvent<String>> sink) {
        // 等待 SSE sink 就绪
        Mono<Sinks.Many<ServerSentEvent<String>>> sinkMono = sessionManager.waitForSseSink(sessionId, 0)
                .timeout(Duration.ofMillis(500))
                .switchIfEmpty(Mono.just(sink)); // 如果等待超时，使用传入的 sink
        
        return sinkMono
                .flatMap(sseSink -> {
                    // 根据 endpoint 获取工具列表（从 Nacos 查询）
                    return Mono.fromCallable(() -> {
                        // 去掉 virtual- 前缀（如果存在）
                        String actualEndpoint = endpoint;
                        if (endpoint.startsWith("virtual-")) {
                            actualEndpoint = endpoint.substring("virtual-".length());
                        }
                        
                        List<Map<String, Object>> tools = virtualProjectRegistrationService.getVirtualProjectToolsByEndpointName(actualEndpoint);
                        
                        // 转换为 MCP 格式
                        Map<String, Object> result = new java.util.HashMap<>();
                        result.put("tools", tools);
                        result.put("toolsMeta", new java.util.HashMap<>());
                        
                        Map<String, Object> response = new java.util.HashMap<>();
                        response.put("jsonrpc", "2.0");
                        response.put("id", id);
                        response.put("result", result);
                        
                        return objectMapper.writeValueAsString(response);
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(responseJson -> {
                        // 通过 SSE sink 发送响应
                        ServerSentEvent<String> sseEvent = ServerSentEvent.<String>builder()
                                .data(responseJson)
                                .build();
                        Sinks.EmitResult emitResult = sseSink.tryEmitNext(sseEvent);
                        if (emitResult.isSuccess()) {
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
                                @SuppressWarnings("unchecked")
                                Map<String, Object> resultMap = (Map<String, Object>) responseMap.get("result");
                                @SuppressWarnings("unchecked")
                                List<?> toolsList = (List<?>) resultMap.get("tools");
                                log.info("✅ Successfully sent tools/list response via SSE: sessionId={}, tools={}", 
                                        sessionId, toolsList != null ? toolsList.size() : 0);
                            } catch (Exception e) {
                                log.debug("Failed to parse response JSON for logging: {}", e.getMessage());
                            }
                        } else {
                            log.warn("⚠️ Failed to emit SSE event: sessionId={}, result={}", sessionId, emitResult);
                        }
                        
                        // HTTP 响应返回 202 Accepted
                        return ServerResponse.accepted()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue("{\"status\":\"accepted\",\"message\":\"Request accepted, response will be sent via SSE\"}"));
                    })
                    .onErrorResume(error -> {
                        log.error("❌ Error handling tools/list: sessionId={}, endpoint={}", sessionId, endpoint, error);
                        String errorResponse = String.format(
                                "{\"jsonrpc\":\"2.0\",\"id\":%s,\"error\":{\"code\":-32603,\"message\":\"%s\"}}",
                                id, error.getMessage()
                        );
                        return sendErrorResponseViaSse(sseSink, errorResponse);
                    });
                });
    }
    
    /**
     * 处理tools/call请求
     * 参考 mcp-router-v3：通过 SSE sink 发送响应，HTTP 返回 202 Accepted
     */
    private Mono<ServerResponse> handleToolCall(String sessionId, String id, String endpoint, 
                                                Map<String, Object> message, Sinks.Many<ServerSentEvent<String>> sink) {
        // 等待 SSE sink 就绪
        Mono<Sinks.Many<ServerSentEvent<String>>> sinkMono = sessionManager.waitForSseSink(sessionId, 0)
                .timeout(Duration.ofMillis(500))
                .switchIfEmpty(Mono.just(sink)); // 如果等待超时，使用传入的 sink
        
        return sinkMono
                .flatMap(sseSink -> {
                    // 提取工具调用参数
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = (Map<String, Object>) message.get("params");
                    String toolName = (String) params.get("name");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
                    
                    // 调用工具
                    return Mono.fromCallable(() -> {
                        // 提取参数
                        @SuppressWarnings("unchecked")
                        List<Object> argsList = (List<Object>) arguments.getOrDefault("args", List.of());
                        Object[] args = argsList.toArray();
                        
                        // 提取超时时间（默认3000ms）
                        Integer timeout = arguments.containsKey("timeout") ? 
                                ((Number) arguments.get("timeout")).intValue() : 3000;
                        
                        // 执行工具调用
                        McpExecutorService.McpCallResult result = mcpExecutorService.executeToolCallSync(
                                toolName, 
                                args,
                                timeout
                        );
                        
                        if (result.isSuccess()) {
                            // 构建成功响应
                            Map<String, Object> content = new java.util.HashMap<>();
                            content.put("type", "text");
                            content.put("text", objectMapper.writeValueAsString(result.getResult()));
                            
                            Map<String, Object> resultMap = new java.util.HashMap<>();
                            resultMap.put("content", List.of(content));
                            resultMap.put("isError", false);
                            
                            Map<String, Object> response = new java.util.HashMap<>();
                            response.put("jsonrpc", "2.0");
                            response.put("id", id);
                            response.put("result", resultMap);
                            
                            return objectMapper.writeValueAsString(response);
                        } else {
                            // 构建错误响应
                            return String.format(
                                    "{\"jsonrpc\":\"2.0\",\"id\":%s,\"error\":{\"code\":-32603,\"message\":\"%s\"}}",
                                    id, result.getErrorMessage()
                            );
                        }
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(responseJson -> {
                        // 通过 SSE sink 发送响应
                        ServerSentEvent<String> sseEvent = ServerSentEvent.<String>builder()
                                .data(responseJson)
                                .build();
                        Sinks.EmitResult emitResult = sseSink.tryEmitNext(sseEvent);
                        if (emitResult.isSuccess()) {
                            log.info("✅ Successfully sent tools/call response via SSE: sessionId={}, tool={}", sessionId, toolName);
                        } else {
                            log.warn("⚠️ Failed to emit SSE event: sessionId={}, result={}", sessionId, emitResult);
                        }
                        
                        // HTTP 响应返回 202 Accepted
                        return ServerResponse.accepted()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue("{\"status\":\"accepted\",\"message\":\"Request accepted, response will be sent via SSE\"}"));
                    })
                    .onErrorResume(error -> {
                        log.error("❌ Error handling tools/call: sessionId={}, tool={}", sessionId, toolName, error);
                        String errorResponse = String.format(
                                "{\"jsonrpc\":\"2.0\",\"id\":%s,\"error\":{\"code\":-32603,\"message\":\"%s\"}}",
                                id, error.getMessage()
                        );
                        return sendErrorResponseViaSse(sseSink, errorResponse);
                    });
                });
    }
    
    /**
     * 通过 SSE sink 发送响应
     */
    private Mono<ServerResponse> sendResponseViaSse(Sinks.Many<ServerSentEvent<String>> sink, String id, String responseJson) {
        ServerSentEvent<String> sseEvent = ServerSentEvent.<String>builder()
                .data(responseJson)
                .build();
        Sinks.EmitResult emitResult = sink.tryEmitNext(sseEvent);
        if (emitResult.isSuccess()) {
            log.debug("✅ Successfully sent response via SSE: id={}", id);
        } else {
            log.warn("⚠️ Failed to emit SSE event: id={}, result={}", id, emitResult);
        }
        
        return ServerResponse.accepted()
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{\"status\":\"accepted\",\"message\":\"Request accepted, response will be sent via SSE\"}"));
    }
    
    /**
     * 通过 SSE sink 发送错误响应
     */
    private Mono<ServerResponse> sendErrorResponseViaSse(Sinks.Many<ServerSentEvent<String>> sink, String errorResponse) {
        ServerSentEvent<String> errorEvent = ServerSentEvent.<String>builder()
                .data(errorResponse)
                .build();
        Sinks.EmitResult emitResult = sink.tryEmitNext(errorEvent);
        if (!emitResult.isSuccess() && emitResult != Sinks.EmitResult.FAIL_TERMINATED && emitResult != Sinks.EmitResult.FAIL_CANCELLED) {
            log.warn("⚠️ Failed to emit SSE error event: result={}", emitResult);
        }
        
        return ServerResponse.accepted()
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{\"status\":\"accepted\",\"error\":\"Error response sent via SSE\"}"));
    }
    
    /**
     * 构建基础URL
     * 参考 mcp-router-v3 的实现，支持代理头和 context-path
     * 注意：此方法在 WebFlux 模式下使用，但当前应用使用 WebMVC 模式
     */
    private String buildBaseUrl(ServerRequest request) {
        try {
            // 提取 context-path
            String contextPath = extractContextPath(request);
            
            // 优先读取代理相关头
            String forwardedProto = request.headers().firstHeader("X-Forwarded-Proto");
            if (forwardedProto == null) {
                forwardedProto = request.headers().firstHeader("x-forwarded-proto");
            }
            String forwardedHost = request.headers().firstHeader("X-Forwarded-Host");
            if (forwardedHost == null) {
                forwardedHost = request.headers().firstHeader("x-forwarded-host");
            }
            String forwardedPort = request.headers().firstHeader("X-Forwarded-Port");
            if (forwardedPort == null) {
                forwardedPort = request.headers().firstHeader("x-forwarded-port");
            }
            
            String scheme;
            String hostPort;
            
            log.debug("🔍 Building base URL (WebFlux) - forwardedProto: {}, forwardedHost: {}, forwardedPort: {}, contextPath: {}", 
                    forwardedProto, forwardedHost, forwardedPort, contextPath);
            
            if (forwardedHost != null && !forwardedHost.isEmpty()) {
                scheme = (forwardedProto != null && !forwardedProto.isEmpty()) ? forwardedProto : "http";
                hostPort = forwardedHost;
                // 如果 X-Forwarded-Host 不包含端口，且 X-Forwarded-Port 存在，则添加端口
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
                log.info("✅ Built base URL from forwarded headers (WebFlux): {}", baseUrl);
                return baseUrl;
            }
            
            // 其次使用 Host 头
            String host = request.headers().firstHeader("Host");
            if (host != null && !host.isEmpty()) {
                String reqScheme = request.uri().getScheme();
                if (reqScheme == null || reqScheme.isEmpty()) {
                    reqScheme = "http";
                }
                // 处理 Host 头中的端口（如果是标准端口，则移除）
                String hostWithoutPort = host;
                if (host.contains(":")) {
                    String[] parts = host.split(":");
                    if (parts.length == 2) {
                        try {
                            int port = Integer.parseInt(parts[1]);
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
                log.info("✅ Built base URL from Host header (WebFlux): {}", baseUrl);
                return baseUrl;
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to build base URL from request (WebFlux): {}, falling back to default", e.getMessage());
        }
        
        // 回退到默认配置
        return "http://127.0.0.1:9091";
    }
    
    /**
     * 从请求中提取 context-path（WebFlux 模式）
     * 参考 mcp-router-v3 的实现
     */
    private String extractContextPath(ServerRequest request) {
        try {
            // 1. 优先从 X-Forwarded-Prefix 头中获取
            String forwardedPrefix = request.headers().firstHeader("X-Forwarded-Prefix");
            if (forwardedPrefix == null || forwardedPrefix.isEmpty()) {
                forwardedPrefix = request.headers().firstHeader("x-forwarded-prefix");
            }
            if (forwardedPrefix != null && !forwardedPrefix.isEmpty()) {
                String contextPath = forwardedPrefix.trim();
                if (!contextPath.startsWith("/")) {
                    contextPath = "/" + contextPath;
                }
                if (contextPath.endsWith("/") && contextPath.length() > 1) {
                    contextPath = contextPath.substring(0, contextPath.length() - 1);
                }
                log.info("✅ Extracted context-path from X-Forwarded-Prefix (WebFlux): {}", contextPath);
                return contextPath;
            }
            
            // 2. 从完整的请求 URI 路径中提取
            String fullPath = request.uri().getPath();
            String requestPath = request.path();
            
            if (fullPath != null && requestPath != null && 
                !fullPath.equals(requestPath) && fullPath.startsWith(requestPath)) {
                String diff = fullPath.substring(0, fullPath.length() - requestPath.length());
                if (diff.endsWith("/")) {
                    diff = diff.substring(0, diff.length() - 1);
                }
                if (!diff.isEmpty()) {
                    log.debug("Extracted context-path from URI difference (WebFlux): {}", diff);
                    return diff;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract context-path (WebFlux): {}", e.getMessage());
        }
        
        return "";
    }
}

