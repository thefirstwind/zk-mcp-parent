package com.pajk.mcpmetainfo.core.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Map;
import java.util.Set;

/**
 * 本地环境 Redis 客户端实现，使用 Jedis
 */
@Component
@ConditionalOnProperty(name = "mcp.session.redis.type", havingValue = "local", matchIfMissing = true)
public class LocalJedisRedisClient implements RedisClient {

    private static final Logger log = LoggerFactory.getLogger(LocalJedisRedisClient.class);

    private final JedisPool jedisPool;

    public LocalJedisRedisClient(redis.clients.jedis.JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        log.info("✅ LocalJedisRedisClient initialized (using JedisPool)");
    }

    @Override
    public void hset(String key, String field, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset(key, field, value);
        } catch (Exception e) {
            log.error("Failed to hset key={}, field={}", key, field, e);
            throw new RuntimeException("Redis hset failed", e);
        }
    }

    @Override
    public void hsetAll(String key, Map<String, String> hash) {
        try (Jedis jedis = jedisPool.getResource()) {
            // Jedis 2.9.0 uses hmset for setting multiple fields
            jedis.hmset(key, hash);
        } catch (Exception e) {
            log.error("Failed to hsetAll key={}", key, e);
            throw new RuntimeException("Redis hsetAll failed", e);
        }
    }

    @Override
    public Map<String, String> hgetAll(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hgetAll(key);
        } catch (Exception e) {
            log.error("Failed to hgetAll key={}", key, e);
            throw new RuntimeException("Redis hgetAll failed", e);
        }
    }

    @Override
    public void expire(String key, long seconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            // Jedis 2.9.0 expire method accepts int, not long
            jedis.expire(key, (int) seconds);
        } catch (Exception e) {
            log.error("Failed to expire key={}, seconds={}", key, seconds, e);
            throw new RuntimeException("Redis expire failed", e);
        }
    }

    @Override
    public void sadd(String key, String... members) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sadd(key, members);
        } catch (Exception e) {
            log.error("Failed to sadd key={}", key, e);
            throw new RuntimeException("Redis sadd failed", e);
        }
    }

    @Override
    public void srem(String key, String... members) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.srem(key, members);
        } catch (Exception e) {
            log.error("Failed to srem key={}", key, e);
            throw new RuntimeException("Redis srem failed", e);
        }
    }

    @Override
    public void del(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
        } catch (Exception e) {
            log.error("Failed to del key={}", key, e);
            throw new RuntimeException("Redis del failed", e);
        }
    }

    @Override
    public Set<String> keys(String pattern) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.keys(pattern);
        } catch (Exception e) {
            log.error("Failed to keys pattern={}", pattern, e);
            throw new RuntimeException("Redis keys failed", e);
        }
    }

    @Override
    public String type(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.type(key);
        } catch (Exception e) {
            log.error("Failed to type key={}", key, e);
            throw new RuntimeException("Redis type failed", e);
        }
    }

    @Override
    public Set<String> smembers(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.smembers(key);
        } catch (Exception e) {
            log.error("Failed to smembers key={}", key, e);
            throw new RuntimeException("Redis smembers failed", e);
        }
    }

    @Override
    public String get(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        } catch (Exception e) {
            log.error("Failed to get key={}", key, e);
            throw new RuntimeException("Redis get failed", e);
        }
    }

    @Override
    public <T> T execute(RedisOperation<T> operation) {
        try (Jedis jedis = jedisPool.getResource()) {
            // 对于 Jedis，直接执行操作即可
            return operation.execute(this);
        } catch (Exception e) {
            log.error("Failed to execute Redis operation", e);
            throw new RuntimeException("Redis execute failed", e);
        }
    }
}
