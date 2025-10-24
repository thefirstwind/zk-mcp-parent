package com.zkinfo.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * MCP AI客户端应用程序
 * 使用Spring AI Alibaba集成DeepSeek LLM
 * 作为MCP Client调用zkInfo MCP Server
 */
@SpringBootApplication
@EnableAsync
public class McpAiClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpAiClientApplication.class, args);
    }
}



