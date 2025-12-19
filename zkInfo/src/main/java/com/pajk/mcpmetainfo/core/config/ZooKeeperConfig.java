package com.pajk.mcpmetainfo.core.config;

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
     * 默认60秒，减少会话超时导致的连接中断
     */
    private int sessionTimeout = 60000;
    
    /**
     * 连接超时时间(毫秒)
     * 默认30秒，给连接更多时间
     */
    private int connectionTimeout = 30000;
    
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
         * 默认3秒，减少重试频率，降低服务器压力
         * 注意：使用RetryForever时，maxRetries参数不再使用
         */
        private int baseSleepTime = 3000;
    }
}
