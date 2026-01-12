package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.session.SessionInstanceIdProvider;
import com.pajk.mcpmetainfo.core.session.SessionMeta;
import com.pajk.mcpmetainfo.core.session.SessionRedisRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Session管理器
 * 使用 Redis 管理会话元数据，内存中只保留 SSE 连接对象
 * 参考 mcp-router-v3 的 McpSessionService 实现
 */
@Slf4j
@Service
public class McpSessionManager {
    
    private final SessionRedisRepository sessionRepository;
    private final String instanceId;
    
    // sessionId -> SSE Sink映射（WebFlux 模式，内存中保留）
    private final Map<String, Sinks.Many<ServerSentEvent<String>>> sinkMap = new ConcurrentHashMap<>();
    
    // sessionId -> WebMVC SseEmitter映射（WebMVC 模式，内存中保留）
    private final Map<String, org.springframework.web.servlet.mvc.method.annotation.SseEmitter> sseEmitterMap = new ConcurrentHashMap<>();
    
    public McpSessionManager(SessionRedisRepository sessionRepository,
                             SessionInstanceIdProvider instanceIdProvider) {
        this.sessionRepository = sessionRepository;
        this.instanceId = instanceIdProvider.getInstanceId();
    }
    
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
        if (!StringUtils.hasText(sessionId) || sink == null) {
            return;
        }
        sinkMap.put(sessionId, sink);
        // 保存到 Redis
        SessionMeta meta = new SessionMeta(sessionId, instanceId, null, null, "SSE", endpoint, LocalDateTime.now(), true);
        sessionRepository.saveSessionMeta(meta);
        log.info("✅ Registered SSE sink: sessionId={}, endpoint={}", sessionId, endpoint);
    }
    
    /**
     * 注册 WebMVC SseEmitter（WebMVC 模式）
     * 参考 mcp-router-v3 的 registerSessionService 和 registerSseSink
     */
    public void registerSseEmitter(String sessionId, String endpoint, org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
        if (!StringUtils.hasText(sessionId) || emitter == null) {
            return;
        }
        sseEmitterMap.put(sessionId, emitter);
        // 保存到 Redis
        SessionMeta meta = new SessionMeta(sessionId, instanceId, null, null, "SSE", endpoint, LocalDateTime.now(), true);
        sessionRepository.saveSessionMeta(meta);
        log.info("✅ Registered SSE emitter: sessionId={}, endpoint={}", sessionId, endpoint);
    }
    
    /**
     * 注册 session 的 serviceName（参考 mcp-router-v3 的 registerSessionService）
     */
    public void registerSessionService(String sessionId, String serviceName) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(serviceName)) {
            return;
        }
        // 从 Redis 获取现有 session，更新 serviceName
        sessionRepository.findSession(sessionId).ifPresentOrElse(
            meta -> {
                meta.setServiceName(serviceName);
                sessionRepository.saveSessionMeta(meta);
            },
            () -> {
                // 如果不存在，创建新的 session
                SessionMeta meta = new SessionMeta(sessionId, instanceId, serviceName, null, "SSE", null, LocalDateTime.now(), true);
                sessionRepository.saveSessionMeta(meta);
            }
        );
        touch(sessionId); // 更新活跃时间
        log.info("✅ Registered service for session: sessionId={}, serviceName={}", sessionId, serviceName);
    }
    
    /**
     * 获取 session 的 serviceName（参考 mcp-router-v3 的 getServiceName）
     */
    public String getServiceName(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        return sessionRepository.findSession(sessionId)
                .map(SessionMeta::getServiceName)
                .orElse(null);
    }
    
    /**
     * 获取 session 的 endpoint
     */
    public String getEndpointForSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        return sessionRepository.findSession(sessionId)
                .map(SessionMeta::getEndpoint)
                .orElse(null);
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
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        sessionRepository.updateLastActive(sessionId);
//        log.debug("💓 Touched session: sessionId={}", sessionId);
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
     * 注意：这个方法需要查询 Redis，性能较低，建议避免频繁调用
     */
    public String getSessionIdForEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return null;
        }
        // 查询当前实例的所有 sessions
        return sessionRepository.findSessionIdsByInstance(instanceId).stream()
                .filter(sessionId -> {
                    String sessionEndpoint = getEndpointForSession(sessionId);
                    return endpoint.equals(sessionEndpoint);
                })
                .findFirst()
                .orElse(null);
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
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        // 清理内存中的连接对象
        Sinks.Many<ServerSentEvent<String>> sink = sinkMap.remove(sessionId);
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = sseEmitterMap.remove(sessionId);
        
        // 从 Redis 获取 session 信息用于日志
        final String[] endpoint = {null};
        final String[] serviceName = {null};
        sessionRepository.findSession(sessionId).ifPresent(meta -> {
            endpoint[0] = meta.getEndpoint();
            serviceName[0] = meta.getServiceName();
        });
        
        // 从 Redis 删除 session
        sessionRepository.removeSession(sessionId, instanceId);
        
        // 关闭连接
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
        log.info("✅ Removed session: sessionId={}, endpoint={}, serviceName={}", sessionId, endpoint[0], serviceName[0]);
    }
    
    /**
     * 获取所有sessionId（当前实例）
     */
    public java.util.Set<String> getAllSessionIds() {
        return sessionRepository.findSessionIdsByInstance(instanceId);
    }
}

