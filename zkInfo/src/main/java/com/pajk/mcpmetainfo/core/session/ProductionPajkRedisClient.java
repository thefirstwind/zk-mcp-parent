package com.pajk.mcpmetainfo.core.session;

import com.pajk.mcpmetainfo.core.config.PajkJedisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 生产环境 Redis 客户端实现，使用 PajkJedisClient
 * 
 * 注意：PajkJedisClient 的 API 与标准 Redis 操作不完全一致，
 * 这里需要根据实际的生产环境 API 进行适配
 */
@Component
@ConditionalOnProperty(name = "mcp.session.redis.type", havingValue = "production")
public class ProductionPajkRedisClient implements RedisClient {

    private static final Logger log = LoggerFactory.getLogger(ProductionPajkRedisClient.class);

    private final PajkJedisClient pajkJedisClient;

    public ProductionPajkRedisClient(PajkJedisClient pajkJedisClient) {
        this.pajkJedisClient = pajkJedisClient;
        log.info("✅ ProductionPajkRedisClient initialized (using PajkJedisClient)");
    }

    @Override
    public void hset(String key, String field, String value) {
        try {
            // PajkJedisClient 可能不支持直接的 Hash 操作
            // 需要根据实际 API 调整，这里使用组合键的方式
            String hashKey = key + ":" + field;
            pajkJedisClient.set(hashKey, value);
        } catch (Exception e) {
            log.error("Failed to hset key={}, field={}", key, field, e);
            throw new RuntimeException("Redis hset failed", e);
        }
    }

    @Override
    public void hsetAll(String key, Map<String, String> hash) {
        try {
            // 批量设置 Hash 字段
            for (Map.Entry<String, String> entry : hash.entrySet()) {
                hset(key, entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            log.error("Failed to hsetAll key={}", key, e);
            throw new RuntimeException("Redis hsetAll failed", e);
        }
    }

    @Override
    public Map<String, String> hgetAll(String key) {
        try {
            // PajkJedisClient 可能不支持直接的 Hash 操作
            // 需要根据实际 API 调整
            // 这里返回空 Map，实际实现需要根据生产环境 API 调整
            log.warn("hgetAll not fully supported by PajkJedisClient, returning empty map for key: {}", key);
            return new HashMap<>();
        } catch (Exception e) {
            log.error("Failed to hgetAll key={}", key, e);
            throw new RuntimeException("Redis hgetAll failed", e);
        }
    }

    @Override
    public void expire(String key, long seconds) {
        try {
            pajkJedisClient.expireKey(key, (int) seconds);
        } catch (Exception e) {
            log.error("Failed to expire key={}, seconds={}", key, seconds, e);
            throw new RuntimeException("Redis expire failed", e);
        }
    }

    @Override
    public void sadd(String key, String... members) {
        try {
            // PajkJedisClient 可能不支持 Set 操作
            // 需要根据实际 API 调整
            log.warn("sadd not fully supported by PajkJedisClient for key: {}", key);
        } catch (Exception e) {
            log.error("Failed to sadd key={}", key, e);
            throw new RuntimeException("Redis sadd failed", e);
        }
    }

    @Override
    public void srem(String key, String... members) {
        try {
            // PajkJedisClient 可能不支持 Set 操作
            // 需要根据实际 API 调整
            log.warn("srem not fully supported by PajkJedisClient for key: {}", key);
        } catch (Exception e) {
            log.error("Failed to srem key={}", key, e);
            throw new RuntimeException("Redis srem failed", e);
        }
    }

    @Override
    public void del(String key) {
        try {
            pajkJedisClient.del(key);
        } catch (Exception e) {
            log.error("Failed to del key={}", key, e);
            throw new RuntimeException("Redis del failed", e);
        }
    }

    @Override
    public Set<String> keys(String pattern) {
        try {
            // PajkJedisClient 可能不支持 keys 操作
            // 需要根据实际 API 调整
            log.warn("keys not fully supported by PajkJedisClient for pattern: {}", pattern);
            return Set.of();
        } catch (Exception e) {
            log.error("Failed to keys pattern={}", pattern, e);
            throw new RuntimeException("Redis keys failed", e);
        }
    }

    @Override
    public String type(String key) {
        try {
            // PajkJedisClient 可能不支持 type 操作
            log.warn("type not fully supported by PajkJedisClient for key: {}", key);
            return "unknown";
        } catch (Exception e) {
            log.error("Failed to type key={}", key, e);
            throw new RuntimeException("Redis type failed", e);
        }
    }

    @Override
    public Set<String> smembers(String key) {
        try {
            // PajkJedisClient 可能不支持 Set 操作
            log.warn("smembers not fully supported by PajkJedisClient for key: {}", key);
            return Set.of();
        } catch (Exception e) {
            log.error("Failed to smembers key={}", key, e);
            throw new RuntimeException("Redis smembers failed", e);
        }
    }

    @Override
    public String get(String key) {
        try {
            return pajkJedisClient.get(key);
        } catch (Exception e) {
            log.error("Failed to get key={}", key, e);
            throw new RuntimeException("Redis get failed", e);
        }
    }

    @Override
    public <T> T execute(RedisOperation<T> operation) {
        // 对于生产环境，直接执行操作
        return operation.execute(this);
    }
}


