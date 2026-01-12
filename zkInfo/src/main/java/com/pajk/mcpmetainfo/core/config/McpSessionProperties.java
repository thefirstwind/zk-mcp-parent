package com.pajk.mcpmetainfo.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

@ConfigurationProperties(prefix = "mcp.session")
public class McpSessionProperties {

    /**
     * Redis key 前缀，例如 zkinfo
     */
    private String redisPrefix = "zkinfo";

    /**
     * 会话 TTL，默认 30 分钟
     */
    private Duration ttl = Duration.ofMinutes(30);

    /**
     * 可选：显式指定实例 ID（用于多实例部署）
     */
    private String instanceId;

    public String getRedisPrefix() {
        return redisPrefix;
    }

    public void setRedisPrefix(String redisPrefix) {
        if (StringUtils.hasText(redisPrefix)) {
            this.redisPrefix = redisPrefix;
        }
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            this.ttl = ttl;
        }
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
}
