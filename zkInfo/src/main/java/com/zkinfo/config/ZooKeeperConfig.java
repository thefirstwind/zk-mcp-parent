package com.zkinfo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ZooKeeper配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "zookeeper")
public class ZooKeeperConfig {
    
    /**
     * ZooKeeper连接字符串
     */
    private String connectString = "localhost:2181";
    
    /**
     * 会话超时时间(毫秒)
     */
    private int sessionTimeout = 30000;
    
    /**
     * 连接超时时间(毫秒)
     */
    private int connectionTimeout = 15000;
    
    /**
     * 基础路径
     */
    private String basePath = "/dubbo";
    
    /**
     * 重试配置
     */
    private Retry retry = new Retry();
    
    @Data
    public static class Retry {
        /**
         * 最大重试次数
         */
        private int maxRetries = 3;
        
        /**
         * 基础睡眠时间(毫秒)
         */
        private int baseSleepTime = 1000;
    }
}
