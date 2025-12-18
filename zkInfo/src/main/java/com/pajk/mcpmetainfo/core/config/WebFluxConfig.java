package com.pajk.mcpmetainfo.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * WebFlux配置类
 * 确保RouterFunction能够被正确注册
 * 
 * 注意：在Spring Boot中，当同时存在WebMVC和WebFlux时，
 * 需要显式配置 WebFlux 的 DispatcherHandler 来支持 RouterFunction
 */
@Slf4j
@Configuration
public class WebFluxConfig {
    
    public WebFluxConfig() {
        log.info("✅ WebFluxConfig initialized - RouterFunction support enabled");
    }
    
    /**
     * 创建一个空的 RouterFunction 作为占位符，确保 WebFlux 基础设施被初始化
     * 实际的 SSE 路由由 MultiEndpointMcpRouterConfig 提供
     */
    @Bean
    public RouterFunction<ServerResponse> webFluxRouterFunction() {
        log.info("Registering WebFlux RouterFunction infrastructure");
        return RouterFunctions.route()
                .GET("/health-check-webflux", request -> 
                    ServerResponse.ok().bodyValue("WebFlux is enabled"))
                .build();
    }
}

