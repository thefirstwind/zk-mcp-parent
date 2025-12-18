package com.pajk.mcpmetainfo.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpmetainfo.core.service.DubboMcpToolCallbackProvider;
import io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.function.server.RouterFunction;

/**
 * MCP Server配置类
 * 按照MCP标准协议实现SSE传输和路由配置
 */
@Slf4j
@Configuration
public class McpServerConfig {

    @Autowired
    private Environment environment;

    @Value("${server.port:9091}")
    private String serverPort;

    /**
     * 获取服务器端口
     */
    private int getServerPort() {
        String port = environment.getProperty("server.port", serverPort);
        return Integer.parseInt(port);
    }

    /**
     * 获取服务器IP地址
     */
    private String getServerIp() {
        // 从环境变量或配置中获取IP，默认使用本地IP
        String address = environment.getProperty("server.address", "127.0.0.1");
        // 如果配置的是 0.0.0.0（绑定所有接口），获取实际IP
        if ("0.0.0.0".equals(address)) {
            try {
                // 获取本机实际IP地址
                return java.net.InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                log.warn("Failed to get local IP, using 127.0.0.1", e);
                return "127.0.0.1";
            }
        }
        return address;
    }

    /**
     * 创建工具回调提供者
     * 将Dubbo服务转换为MCP Tools
     */
    @Bean
    public ToolCallbackProvider toolCallbackProvider(DubboMcpToolCallbackProvider dubboToolProvider) {
        log.info("Registering DubboMcpToolCallbackProvider as MCP Tool Provider");
        return dubboToolProvider;
    }

    /**
     * 注意：ObjectMapper Bean 由 JacksonConfig 统一配置
     * 这里不再重复定义，避免冲突
     */

    /**
     * 创建MCP Server Transport Provider
     * 按照MCP标准协议实现SSE传输
     */
    @Bean
    @ConditionalOnMissingBean(name = "multiEndpointRouterFunction")
    public McpServerTransportProvider mcpServerTransportProvider(ObjectMapper objectMapper) {
        // 构建基础URL
        String baseUrl = "http://" + getServerIp() + ":" + getServerPort();
        log.info("Creating MCP Server Transport with baseUrl: {}", baseUrl);

        // 创建WebFlux SSE Server Transport Provider
        WebFluxSseServerTransportProvider provider = new WebFluxSseServerTransportProvider(
                objectMapper,
                baseUrl,
                "/mcp/message",  // 消息端点
                "/sse"          // SSE端点
        );

        log.info("✅ MCP Server Transport Provider created successfully");
        log.info("📡 SSE endpoint: {}/sse", baseUrl);
        log.info("📨 Message endpoint: {}/mcp/message", baseUrl);

        return provider;
    }

    /**
     * 创建路由函数
     * 暴露MCP协议要求的SSE和消息端点
     * 
     * 注意：由于 MultiEndpointMcpRouterConfig 提供了更灵活的多端点支持，
     * 这里不再创建默认的 RouterFunction，避免路由冲突。
     * MultiEndpointMcpRouterConfig.multiEndpointRouterFunction() 会处理所有路由。
     */
    // @Bean
    // public RouterFunction<?> mcpRouterFunction(McpServerTransportProvider transportProvider) {
    //     if (transportProvider instanceof WebFluxSseServerTransportProvider webFluxProvider) {
    //         RouterFunction<?> routerFunction = webFluxProvider.getRouterFunction();
    //         log.info("✅ MCP Router Function created successfully");
    //         return routerFunction;
    //     } else {
    //         throw new IllegalStateException("Expected WebFluxSseServerTransportProvider but got: " +
    //                 transportProvider.getClass().getSimpleName());
    //     }
    // }
}

