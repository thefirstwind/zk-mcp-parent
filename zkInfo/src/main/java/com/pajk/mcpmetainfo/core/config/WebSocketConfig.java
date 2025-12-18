package com.pajk.mcpmetainfo.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpmetainfo.core.controller.McpController;
import com.pajk.mcpmetainfo.core.service.McpProtocolService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket配置
 * 为MCP协议提供WebSocket支持
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final McpProtocolService mcpProtocolService;
    private final ObjectMapper objectMapper;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mcpWebSocketHandler(), "/mcp/ws")
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .setAllowedOrigins("*"); // 生产环境应该限制域名
    }

    @Bean
    public McpController.McpWebSocketHandler mcpWebSocketHandler() {
        return new McpController.McpWebSocketHandler(
            mcpProtocolService, 
            objectMapper, 
            webSocketSessions()
        );
    }

    @Bean
    public ConcurrentHashMap<String, WebSocketSession> webSocketSessions() {
        return new ConcurrentHashMap<>();
    }
}





