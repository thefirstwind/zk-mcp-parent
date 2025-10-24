package com.zkinfo.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * MCP客户端配置
 */
@Configuration
public class McpClientConfig {

    @Value("${mcp.server.url:http://localhost:8080}")
    private String mcpServerUrl;

    @Value("${mcp.server.timeout:30000}")
    private int mcpServerTimeout;

    /**
     * WebClient用于调用MCP Server
     */
    @Bean
    public WebClient mcpWebClient() {
        return WebClient.builder()
                .baseUrl(mcpServerUrl)
                .build();
    }

    /**
     * ObjectMapper用于JSON序列化（MCP专用）
     * 注意：全局 ObjectMapper 已在 OpenAiConfig 中定义
     */
    @Bean(name = "mcpObjectMapper")
    public ObjectMapper mcpObjectMapper() {
        return new ObjectMapper();
    }
}

