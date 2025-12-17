package com.zkinfo.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Nacos配置类
 * 提供Nacos NamingService和ConfigService Bean
 */
@Slf4j
@Configuration
public class NacosConfig {

    @Value("${nacos.server-addr:127.0.0.1:8848}")
    private String serverAddr;

    @Value("${nacos.namespace:public}")
    private String namespace;

    @Value("${nacos.username:nacos}")
    private String username;

    @Value("${nacos.password:nacos}")
    private String password;

    /**
     * 创建NamingService Bean（用于服务注册与发现）
     */
    @Bean
    public NamingService namingService() throws NacosException {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        properties.setProperty("namespace", namespace);
        properties.setProperty("username", username);
        properties.setProperty("password", password);

        log.info("Initializing Nacos NamingService with serverAddr: {}, namespace: {}, username: {}", 
                serverAddr, namespace, username);
        
        NamingService namingService = NacosFactory.createNamingService(properties);
        log.info("✅ Nacos NamingService created successfully");
        return namingService;
    }

    /**
     * 创建ConfigService Bean（用于配置管理）
     */
    @Bean
    public ConfigService configService() throws NacosException {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        properties.setProperty("namespace", namespace);
        properties.setProperty("username", username);
        properties.setProperty("password", password);

        log.info("Initializing Nacos ConfigService with serverAddr: {}, namespace: {}", 
                serverAddr, namespace);
        
        ConfigService configService = NacosFactory.createConfigService(properties);
        log.info("✅ Nacos ConfigService created successfully");
        return configService;
    }
    
    /**
     * 创建 RestTemplate Bean（用于 Nacos v3 HTTP API）
     */
    @org.springframework.context.annotation.Bean
    public org.springframework.web.client.RestTemplate restTemplate() {
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        // 可以添加拦截器、错误处理器等
        return restTemplate;
    }
}

