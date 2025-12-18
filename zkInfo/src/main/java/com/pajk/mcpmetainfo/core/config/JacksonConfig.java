package com.pajk.mcpmetainfo.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Jackson配置类
 * 配置ObjectMapper以支持Java 8时间类型的序列化
 */
@Slf4j
@Configuration
public class JacksonConfig {
    
    /**
     * 配置Jackson ObjectMapper Builder
     * 确保所有ObjectMapper都注册JavaTimeModule
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            builder.modules(new JavaTimeModule());
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            log.info("✅ Configured Jackson2ObjectMapperBuilder with JavaTimeModule for LocalDateTime support");
        };
    }
    
    /**
     * 配置主ObjectMapper Bean
     * 确保Spring MVC和WebFlux都使用正确的ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper mapper = builder.build();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        log.info("✅ Configured primary ObjectMapper with JavaTimeModule for LocalDateTime support");
        return mapper;
    }
}
