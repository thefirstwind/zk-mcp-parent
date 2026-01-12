package com.pajk.mcpmetainfo.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 配置
 * 为 zkInfo 的所有端点启用跨域资源共享（CORS）支持
 * 
 * 参考 mcp-router-v3 的 CorsConfig，但适配 WebMVC（而非 WebFlux）
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 全局 CORS 配置
     * 使用 CorsFilter 处理所有请求的 CORS 头
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许所有来源（生产环境应该限制为特定域名）
        config.setAllowedOrigins(Arrays.asList("*"));
        
        // 允许的 HTTP 方法
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
        // 允许的请求头
        config.setAllowedHeaders(Arrays.asList("*"));
        
        // 暴露给客户端的响应头
        config.setExposedHeaders(Arrays.asList(
            "Content-Type", 
            "Cache-Control", 
            "Connection", 
            "X-Accel-Buffering",
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials"
        ));
        
        // 是否允许发送凭证（cookies、authorization headers 等）
        config.setAllowCredentials(false);
        
        // 预检请求的缓存时间（秒）
        config.setMaxAge(3600L);
        
        // 注册 CORS 配置到所有路径
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }

    /**
     * WebMVC CORS 配置
     * 作为 CorsFilter 的补充，确保所有路径都支持 CORS
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders(
                    "Content-Type", 
                    "Cache-Control", 
                    "Connection", 
                    "X-Accel-Buffering",
                    "Access-Control-Allow-Origin",
                    "Access-Control-Allow-Credentials"
                )
                .allowCredentials(false)
                .maxAge(3600);
    }

    /**
     * Content Negotiation 配置
     * 确保 Spring 能正确处理 Accept 头，避免 406 Not Acceptable 错误
     * 注意：不能忽略 Accept 头，因为 SSE 端点需要返回 text/event-stream
     * 但是，如果 Accept 头不匹配，应该回退到默认类型而不是返回 406
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
                .favorParameter(false)  // 不使用查询参数
                .favorPathExtension(false)  // 不使用路径扩展名
                .ignoreAcceptHeader(false)  // 不忽略 Accept 头，让 @RequestMapping 的 produces 属性生效
                .useRegisteredExtensionsOnly(false)  // 允许使用注册的扩展名
                .defaultContentType(MediaType.APPLICATION_JSON)  // 默认 JSON
                .mediaType("json", MediaType.APPLICATION_JSON)  // 支持 json 类型
                .mediaType("xml", MediaType.APPLICATION_XML);  // 支持 XML（如果需要）
    }
}

