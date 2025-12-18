package com.pajk.mcpmetainfo.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Session管理器
 * 为每个endpoint管理独立的MCP Server Session
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpSessionManager {
    
    // sessionId -> endpoint映射
    private final Map<String, String> sessionToEndpointMap = new ConcurrentHashMap<>();
    
    // sessionId -> serviceName映射（参考 mcp-router-v3）
    private final Map<String, String> sessionToServiceNameMap = new ConcurrentHashMap<>();
    
    // sessionId -> SSE Sink映射
    private final Map<String, Sinks.Many<ServerSentEvent<String>>> sinkMap = new ConcurrentHashMap<>();
    
    // sessionId -> 最后活跃时间
    private final Map<String, LocalDateTime> sessionLastActiveTime = new ConcurrentHashMap<>();
    
    /**
     * 获取或创建endpoint的Session（占位符方法）
     * 实际的Session由WebFluxSseServerTransportProvider管理
     */
    public Mono<Void> getOrCreateSession(String endpoint) {
        log.debug("Getting or creating session for endpoint: {}", endpoint);
        // 实际的Session创建由WebFluxSseServerTransportProvider处理
        return Mono.empty();
    }
    
    /**
     * 注册SSE Sink（WebFlux 模式）
     */
    public void registerSink(String sessionId, String endpoint, Sinks.Many<ServerSentEvent<String>> sink) {
        sinkMap.put(sessionId, sink);
        sessionToEndpointMap.put(sessionId, endpoint);
        sessionLastActiveTime.put(sessionId, LocalDateTime.now());
        log.info("✅ Registered SSE sink: sessionId={}, endpoint={}", sessionId, endpoint);
    }
    
    // sessionId -> WebMVC SseEmitter映射（WebMVC 模式）
    private final Map<String, org.springframework.web.servlet.mvc.method.annotation.SseEmitter> sseEmitterMap = new ConcurrentHashMap<>();
    
    /**
     * 注册 WebMVC SseEmitter（WebMVC 模式）
     * 参考 mcp-router-v3 的 registerSessionService 和 registerSseSink
     */
    public void registerSseEmitter(String sessionId, String endpoint, org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
        sseEmitterMap.put(sessionId, emitter);
        sessionToEndpointMap.put(sessionId, endpoint);
        sessionLastActiveTime.put(sessionId, LocalDateTime.now());
        log.info("✅ Registered SSE emitter: sessionId={}, endpoint={}", sessionId, endpoint);
    }
    
    /**
     * 注册 session 的 serviceName（参考 mcp-router-v3 的 registerSessionService）
     */
    public void registerSessionService(String sessionId, String serviceName) {
        if (sessionId != null && !sessionId.isEmpty() && serviceName != null && !serviceName.isEmpty()) {
            sessionToServiceNameMap.put(sessionId, serviceName);
            touch(sessionId); // 更新活跃时间
            log.info("✅ Registered service for session: sessionId={}, serviceName={}", sessionId, serviceName);
        }
    }
    
    /**
     * 获取 session 的 serviceName（参考 mcp-router-v3 的 getServiceName）
     */
    public String getServiceName(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        return sessionToServiceNameMap.get(sessionId);
    }
    
    /**
     * 获取 WebMVC SseEmitter
     */
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter getSseEmitter(String sessionId) {
        return sseEmitterMap.get(sessionId);
    }
    
    /**
     * 更新会话活跃时间（参考 mcp-router-v3 的 touch）
     */
    public void touch(String sessionId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            // 只要 sessionId 存在，就更新活跃时间（不要求 endpoint 存在）
            sessionLastActiveTime.put(sessionId, LocalDateTime.now());
            log.debug("💓 Touched session: sessionId={}", sessionId);
        }
    }
    
    /**
     * 等待SSE Sink就绪
     */
    public Mono<Sinks.Many<ServerSentEvent<String>>> waitForSseSink(String sessionId, int maxWaitSeconds) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Mono.empty();
        }
        // 立即检查
        Sinks.Many<ServerSentEvent<String>> sink = sinkMap.get(sessionId);
        if (sink != null) {
            return Mono.just(sink);
        }
        // 如果 maxWaitSeconds 为 0，立即返回空（不等待）
        if (maxWaitSeconds <= 0) {
            return Mono.empty();
        }
        // 使用短延迟进行重试
        return Mono.delay(java.time.Duration.ofMillis(10))
                .flatMap(delay -> {
                    Sinks.Many<ServerSentEvent<String>> retrySink = sinkMap.get(sessionId);
                    if (retrySink != null) {
                        return Mono.just(retrySink);
                    }
                    return Mono.empty();
                });
    }
    
    /**
     * 获取endpoint对应的sessionId
     */
    public String getSessionIdForEndpoint(String endpoint) {
        return sessionToEndpointMap.entrySet().stream()
                .filter(e -> endpoint.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 根据sessionId获取endpoint
     */
    public String getEndpointForSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return sessionToEndpointMap.get(sessionId);
    }
    
    /**
     * 获取SSE Sink
     */
    public Sinks.Many<ServerSentEvent<String>> getSink(String sessionId) {
        return sinkMap.get(sessionId);
    }
    
    /**
     * 清理Session（参考 mcp-router-v3 的 removeSession）
     */
    public void removeSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        Sinks.Many<ServerSentEvent<String>> sink = sinkMap.remove(sessionId);
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = sseEmitterMap.remove(sessionId);
        String endpoint = sessionToEndpointMap.remove(sessionId);
        String serviceName = sessionToServiceNameMap.remove(sessionId);
        sessionLastActiveTime.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("⚠️ Error completing emitter for session: {}, error={}", sessionId, e.getMessage());
            }
        }
        log.info("✅ Removed session: sessionId={}, endpoint={}, serviceName={}", sessionId, endpoint, serviceName);
    }
    
    /**
     * 获取所有sessionId
     */
    public java.util.Set<String> getAllSessionIds() {
        return new java.util.HashSet<>(sinkMap.keySet());
    }
}

