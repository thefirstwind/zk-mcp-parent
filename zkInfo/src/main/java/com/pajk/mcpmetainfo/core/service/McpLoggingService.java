package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.mcp.McpProtocol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP Logging 服务实现
 * 提供日志记录和查询功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpLoggingService {

    // 日志存储（内存实现，生产环境建议使用持久化存储）
    private final Queue<McpProtocol.McpLogMessage> logMessages = new ConcurrentLinkedQueue<>();
    private final AtomicLong logIdCounter = new AtomicLong(0);
    
    // 日志级别定义
    public static final Set<String> LOG_LEVELS = Set.of(
            "debug", "info", "notice", "warning", "error", 
            "critical", "alert", "emergency"
    );

    /**
     * 记录日志消息
     */
    public Mono<Void> logMessage(McpProtocol.LogMessageParams params) {
        log.info("记录日志: level={}, logger={}, data={}", 
                params.getLevel(), params.getLogger(), params.getData());
        
        // 验证日志级别
        if (!LOG_LEVELS.contains(params.getLevel())) {
            return Mono.error(new IllegalArgumentException("无效的日志级别: " + params.getLevel()));
        }
        
        // 创建日志消息
        McpProtocol.McpLogMessage logMessage = McpProtocol.McpLogMessage.builder()
                .level(params.getLevel())
                .data(params.getData())
                .logger(params.getLogger())
                .build();
        
        // 存储日志消息
        logMessages.offer(logMessage);
        
        // 限制内存中的日志数量（保留最近1000条）
        while (logMessages.size() > 1000) {
            logMessages.poll();
        }
        
        // 同时记录到系统日志
        logToSystemLog(logMessage);
        
        return Mono.empty();
    }

    /**
     * 获取日志消息
     */
    public Mono<List<McpProtocol.McpLogMessage>> getLogMessages(String level, String logger, int limit) {
        log.info("获取日志消息: level={}, logger={}, limit={}", level, logger, limit);
        
        return Mono.fromCallable(() -> {
            List<McpProtocol.McpLogMessage> filteredMessages = new ArrayList<>();
            int count = 0;
            
            // 从队列中获取日志消息（逆序，最新的在前）
            List<McpProtocol.McpLogMessage> allMessages = new ArrayList<>(logMessages);
            Collections.reverse(allMessages);
            
            for (McpProtocol.McpLogMessage message : allMessages) {
                if (count >= limit) {
                    break;
                }
                
                // 过滤条件
                boolean levelMatch = level == null || level.equals(message.getLevel());
                boolean loggerMatch = logger == null || logger.equals(message.getLogger());
                
                if (levelMatch && loggerMatch) {
                    filteredMessages.add(message);
                    count++;
                }
            }
            
            return filteredMessages;
        });
    }

    /**
     * 清空日志
     */
    public Mono<Void> clearLogs() {
        log.info("清空日志");
        logMessages.clear();
        return Mono.empty();
    }

    /**
     * 获取日志统计信息
     */
    public Mono<Map<String, Object>> getLogStatistics() {
        return Mono.fromCallable(() -> {
            Map<String, Long> levelCounts = new HashMap<>();
            Map<String, Long> loggerCounts = new HashMap<>();
            
            for (McpProtocol.McpLogMessage message : logMessages) {
                // 统计日志级别
                levelCounts.merge(message.getLevel(), 1L, Long::sum);
                
                // 统计日志记录器
                String logger = message.getLogger() != null ? message.getLogger() : "unknown";
                loggerCounts.merge(logger, 1L, Long::sum);
            }
            
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("totalMessages", logMessages.size());
            statistics.put("levelCounts", levelCounts);
            statistics.put("loggerCounts", loggerCounts);
            statistics.put("timestamp", Instant.now().toString());
            
            return statistics;
        });
    }

    /**
     * 记录到系统日志
     */
    private void logToSystemLog(McpProtocol.McpLogMessage logMessage) {
        String loggerName = logMessage.getLogger() != null ? logMessage.getLogger() : "mcp";
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(loggerName);
        
        String message = String.format("[MCP] %s", logMessage.getData());
        
        switch (logMessage.getLevel()) {
            case "debug":
                logger.debug(message);
                break;
            case "info":
                logger.info(message);
                break;
            case "notice":
                logger.info(message);
                break;
            case "warning":
                logger.warn(message);
                break;
            case "error":
                logger.error(message);
                break;
            case "critical":
                logger.error(message);
                break;
            case "alert":
                logger.error(message);
                break;
            case "emergency":
                logger.error(message);
                break;
            default:
                logger.info(message);
        }
    }

    /**
     * 创建系统日志消息
     */
    public void logSystemMessage(String level, String data) {
        McpProtocol.LogMessageParams params = McpProtocol.LogMessageParams.builder()
                .level(level)
                .data(data)
                .logger("system")
                .build();
        
        logMessage(params).subscribe();
    }

    /**
     * 创建MCP操作日志消息
     */
    public void logMcpOperation(String operation, String details) {
        String message = String.format("MCP操作: %s - %s", operation, details);
        logSystemMessage("info", message);
    }

    /**
     * 创建错误日志消息
     */
    public void logError(String operation, String error) {
        String message = String.format("MCP错误: %s - %s", operation, error);
        logSystemMessage("error", message);
    }

    /**
     * 创建调试日志消息
     */
    public void logDebug(String operation, String details) {
        String message = String.format("MCP调试: %s - %s", operation, details);
        logSystemMessage("debug", message);
    }
}





