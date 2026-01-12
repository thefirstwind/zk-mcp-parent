package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.session.SessionMeta;
import com.pajk.mcpmetainfo.core.session.SessionRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话清理服务
 * 定期清理超时的 SSE 会话，参考 mcp-router-v3 的实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionCleanupService {

    private final SessionRedisRepository sessionRepository;
    private final McpSessionManager sessionManager;

    // SSE 会话超时时间：10分钟（600秒），与 mcp-router-v3 保持一致
    private static final long SSE_TIMEOUT_MS = 600_000;

    /**
     * 定期清理超时的 SSE 会话
     * 每 1 分钟执行一次，清理超过 10 分钟未活跃的会话
     */
    @Scheduled(fixedRate = 60_000, initialDelay = 60_000) // 每 1 分钟执行一次，启动后 1 分钟开始
    public void cleanupTimeoutSessions() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime timeoutThreshold = now.minusNanos(SSE_TIMEOUT_MS * 1_000_000);
            
            // 获取所有会话
            List<SessionMeta> allSessions = sessionRepository.findAllSessions();
            
            int cleanedCount = 0;
            for (SessionMeta meta : allSessions) {
                // 只处理 SSE 会话
                if (!"SSE".equalsIgnoreCase(meta.getTransportType())) {
                    continue;
                }
                
                // 检查会话是否超时
                if (meta.getLastActive() != null && meta.getLastActive().isBefore(timeoutThreshold)) {
                    try {
                        String sessionId = meta.getSessionId();
                        log.info("🧹 Cleaning up timeout SSE session: sessionId={}, lastActive={}, timeout={}ms", 
                                sessionId, meta.getLastActive(), SSE_TIMEOUT_MS);
                        
                        // 从 session manager 移除会话（会清理内存中的连接对象和 Redis 中的会话数据）
                        sessionManager.removeSession(sessionId);
                        cleanedCount++;
                    } catch (Exception e) {
                        log.error("❌ Failed to cleanup timeout session: sessionId={}", meta.getSessionId(), e);
                    }
                }
            }
            
            if (cleanedCount > 0) {
                log.info("✅ Cleaned up {} timeout SSE sessions", cleanedCount);
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to cleanup timeout sessions", e);
        }
    }
}


