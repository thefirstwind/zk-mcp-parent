package com.pajk.mcpmetainfo.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpmetainfo.core.mcp.McpProtocol;
import com.pajk.mcpmetainfo.core.service.McpProtocolService;
import com.pajk.mcpmetainfo.core.service.McpResourcesService;
import com.pajk.mcpmetainfo.core.service.McpPromptsService;
import com.pajk.mcpmetainfo.core.service.McpLoggingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.List;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP协议控制器
 * 支持HTTP、WebSocket和SSE三种通信方式
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpController {

    private final McpProtocolService mcpProtocolService;
    private final McpResourcesService mcpResourcesService;
    private final McpPromptsService mcpPromptsService;
    private final McpLoggingService mcpLoggingService;
    private final ObjectMapper objectMapper;
    
    // WebSocket会话管理
    private final ConcurrentHashMap<String, WebSocketSession> webSocketSessions = new ConcurrentHashMap<>();
    private final AtomicLong sessionIdGenerator = new AtomicLong(1);

    /**
     * HTTP方式的MCP协议调用
     */
    @PostMapping(value = "/jsonrpc", 
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<McpProtocol.JsonRpcResponse> handleJsonRpc(@RequestBody McpProtocol.JsonRpcRequest request) {
        log.info("收到HTTP MCP请求: method={}", request.getMethod());
        return mcpProtocolService.handleRequest(request);
    }

    /**
     * SSE方式的流式MCP调用
     */
    @GetMapping(value = "/stream/{streamId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<McpProtocol.StreamChunk>> streamData(@PathVariable String streamId) {
        log.info("开始SSE流式传输: streamId={}", streamId);
        
        return mcpProtocolService.getStreamData(streamId)
                .map(chunk -> ServerSentEvent.<McpProtocol.StreamChunk>builder()
                    .id(chunk.getId())
                    .event(chunk.getType())
                    .data(chunk)
                    .build())
                .doOnNext(event -> log.debug("发送SSE事件: {}", event.id()))
                .doOnComplete(() -> log.info("SSE流式传输完成: streamId={}", streamId))
                .doOnError(error -> log.error("SSE流式传输错误: streamId=" + streamId, error));
    }

    /**
     * 创建流式调用并返回SSE端点
     */
    @PostMapping(value = "/stream", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Object> createStream(@RequestBody McpProtocol.JsonRpcRequest request) {
        log.info("创建流式调用: method={}", request.getMethod());
        
        return mcpProtocolService.handleRequest(request)
                .map(response -> {
                    if (response.getResult() instanceof McpProtocol.CallToolResult) {
                        McpProtocol.CallToolResult result = (McpProtocol.CallToolResult) response.getResult();
                        if (result.getStreamId() != null) {
                            return Map.of(
                                "streamId", result.getStreamId(),
                                "sseUrl", "/mcp/stream/" + result.getStreamId(),
                                "status", "streaming"
                            );
                        }
                    }
                    return response;
                });
    }

    /**
     * WebSocket处理器
     */
    @org.springframework.stereotype.Component
    public static class McpWebSocketHandler implements WebSocketHandler {
        
        private final McpProtocolService mcpProtocolService;
        private final ObjectMapper objectMapper;
        private final ConcurrentHashMap<String, WebSocketSession> sessions;
        
        public McpWebSocketHandler(McpProtocolService mcpProtocolService, 
                                 ObjectMapper objectMapper,
                                 ConcurrentHashMap<String, WebSocketSession> sessions) {
            this.mcpProtocolService = mcpProtocolService;
            this.objectMapper = objectMapper;
            this.sessions = sessions;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            String sessionId = "ws_" + System.currentTimeMillis();
            session.getAttributes().put("sessionId", sessionId);
            sessions.put(sessionId, session);
            
            log.info("WebSocket连接建立: sessionId={}", sessionId);
            
            // 发送欢迎消息
            McpProtocol.JsonRpcResponse welcome = McpProtocol.JsonRpcResponse.builder()
                    .id("welcome")
                    .result(Map.of(
                        "message", "欢迎使用zkInfo MCP服务",
                        "sessionId", sessionId,
                        "protocolVersion", "2024-11-05"
                    ))
                    .build();
            
            sendMessage(session, welcome);
        }

        @Override
        public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
            try {
                String payload = message.getPayload().toString();
                log.info("收到WebSocket消息: {}", payload);
                
                McpProtocol.JsonRpcRequest request = objectMapper.readValue(payload, McpProtocol.JsonRpcRequest.class);
                
                // 处理请求
                mcpProtocolService.handleRequest(request)
                        .subscribe(
                            response -> {
                                try {
                                    sendMessage(session, response);
                                } catch (Exception e) {
                                    log.error("发送WebSocket响应失败", e);
                                }
                            },
                            error -> log.error("处理WebSocket请求失败", error)
                        );
                        
            } catch (Exception e) {
                log.error("处理WebSocket消息失败", e);
                
                McpProtocol.JsonRpcResponse errorResponse = McpProtocol.JsonRpcResponse.builder()
                        .id("error")
                        .error(McpProtocol.JsonRpcError.builder()
                            .code(McpProtocol.ErrorCodes.PARSE_ERROR)
                            .message("消息解析失败: " + e.getMessage())
                            .build())
                        .build();
                
                sendMessage(session, errorResponse);
            }
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
            String sessionId = (String) session.getAttributes().get("sessionId");
            log.error("WebSocket传输错误: sessionId={}", sessionId, exception);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
            String sessionId = (String) session.getAttributes().get("sessionId");
            sessions.remove(sessionId);
            log.info("WebSocket连接关闭: sessionId={}, status={}", sessionId, closeStatus);
        }

        @Override
        public boolean supportsPartialMessages() {
            return false;
        }
        
        private void sendMessage(WebSocketSession session, Object message) throws Exception {
            if (session.isOpen()) {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
            }
        }
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public Mono<Object> health() {
        return Mono.just(Map.of(
            "status", "UP",
            "protocol", "MCP 2024-11-05",
            "capabilities", List.of("tools", "streaming", "sse", "websocket"),
            "activeSessions", webSocketSessions.size(),
            "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * 获取MCP服务器信息
     */
    @GetMapping("/info")
    public Mono<McpProtocol.ServerInfo> getServerInfo() {
        return Mono.just(McpProtocol.ServerInfo.builder()
                .name("zkInfo-MCP-Server")
                .version("1.0.0")
                .description("Dubbo服务的MCP协议适配器，支持HTTP、WebSocket和SSE")
                .capabilities(List.of("tools", "streaming", "sse", "websocket"))
                .metadata(Map.of(
                    "dubbo.version", "3.2.0",
                    "zookeeper.enabled", true,
                    "streaming.supported", true,
                    "sse.supported", true,
                    "websocket.supported", true,
                    "activeSessions", webSocketSessions.size()
                ))
                .build());
    }

    /**
     * 广播消息到所有WebSocket连接
     */
    public void broadcastToWebSockets(Object message) {
        webSocketSessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    String json = objectMapper.writeValueAsString(message);
                    session.sendMessage(new TextMessage(json));
                }
            } catch (Exception e) {
                log.error("广播WebSocket消息失败", e);
            }
        });
    }

    /**
     * 获取活跃的WebSocket会话数
     */
    @GetMapping("/sessions/count")
    public Mono<Object> getSessionCount() {
        return Mono.just(Map.of(
            "activeWebSocketSessions", webSocketSessions.size(),
            "timestamp", System.currentTimeMillis()
        ));
    }

    // ========== Resources 端点 ==========

    /**
     * 列出所有资源
     */
    @GetMapping("/resources")
    public Mono<McpProtocol.ListResourcesResult> listResources(
            @RequestParam(required = false) String cursor) {
        McpProtocol.ListResourcesParams params = McpProtocol.ListResourcesParams.builder()
                .cursor(cursor)
                .build();
        return mcpResourcesService.listResources(params);
    }

    /**
     * 读取指定资源
     */
    @GetMapping("/resources/{uri}")
    public Mono<McpProtocol.ReadResourceResult> readResource(@PathVariable String uri) {
        McpProtocol.ReadResourceParams params = McpProtocol.ReadResourceParams.builder()
                .uri(uri)
                .build();
        return mcpResourcesService.readResource(params);
    }

    /**
     * 订阅资源
     */
    @PostMapping("/resources/subscribe")
    public Mono<Void> subscribeResource(@RequestBody McpProtocol.SubscribeResourceParams params) {
        return mcpResourcesService.subscribeResource("http_client", params);
    }

    /**
     * 取消订阅资源
     */
    @PostMapping("/resources/unsubscribe")
    public Mono<Void> unsubscribeResource(@RequestBody McpProtocol.UnsubscribeResourceParams params) {
        return mcpResourcesService.unsubscribeResource("http_client", params);
    }

    // ========== Prompts 端点 ==========

    /**
     * 列出所有提示
     */
    @GetMapping("/prompts")
    public Mono<McpProtocol.ListPromptsResult> listPrompts(
            @RequestParam(required = false) String cursor) {
        McpProtocol.ListPromptsParams params = McpProtocol.ListPromptsParams.builder()
                .cursor(cursor)
                .build();
        return mcpPromptsService.listPrompts(params);
    }

    /**
     * 获取指定提示
     */
    @PostMapping("/prompts/get")
    public Mono<McpProtocol.GetPromptResult> getPrompt(@RequestBody McpProtocol.GetPromptParams params) {
        return mcpPromptsService.getPrompt(params);
    }

    /**
     * 添加自定义提示
     */
    @PostMapping("/prompts/add")
    public Mono<Void> addPrompt(@RequestBody McpProtocol.McpPrompt prompt) {
        mcpPromptsService.addPrompt(prompt);
        return Mono.empty();
    }

    /**
     * 删除提示
     */
    @DeleteMapping("/prompts/{name}")
    public Mono<Void> removePrompt(@PathVariable String name) {
        mcpPromptsService.removePrompt(name);
        return Mono.empty();
    }

    // ========== Logging 端点 ==========

    /**
     * 记录日志消息
     */
    @PostMapping("/logging/log")
    public Mono<Void> logMessage(@RequestBody McpProtocol.LogMessageParams params) {
        return mcpLoggingService.logMessage(params);
    }

    /**
     * 获取日志消息
     */
    @GetMapping("/logging/messages")
    public Mono<List<McpProtocol.McpLogMessage>> getLogMessages(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String logger,
            @RequestParam(defaultValue = "100") int limit) {
        return mcpLoggingService.getLogMessages(level, logger, limit);
    }

    /**
     * 获取日志统计信息
     */
    @GetMapping("/logging/statistics")
    public Mono<Map<String, Object>> getLogStatistics() {
        return mcpLoggingService.getLogStatistics();
    }

    /**
     * 清空日志
     */
    @DeleteMapping("/logging/clear")
    public Mono<Void> clearLogs() {
        return mcpLoggingService.clearLogs();
    }
}
