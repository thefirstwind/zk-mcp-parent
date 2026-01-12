package com.pajk.mcpmetainfo.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
@ConditionalOnProperty(name = "mcp.session.redis.type", havingValue = "local", matchIfMissing = true)
public class JedisConfig {

    private static final Logger log = LoggerFactory.getLogger(JedisConfig.class);

    @Value("${mcp.session.redis.host:localhost}")
    private String host;

    @Value("${mcp.session.redis.port:6379}")
    private int port;

    @Value("${mcp.session.redis.password:}")
    private String password;

    @Value("${mcp.session.redis.database:0}")
    private int database;

    @Value("${mcp.session.redis.timeout:2000}")
    private int timeout;

    @Value("${mcp.session.redis.pool.max-total:20}")
    private int maxTotal;

    @Value("${mcp.session.redis.pool.max-idle:10}")
    private int maxIdle;

    @Value("${mcp.session.redis.pool.min-idle:5}")
    private int minIdle;

    @Bean
    public JedisPool jedisPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setBlockWhenExhausted(true);

        JedisPool jedisPool;
        if (password != null && !password.isEmpty()) {
            jedisPool = new JedisPool(poolConfig, host, port, timeout, password, database);
            log.info("✅ JedisPool created with password authentication: {}:{}/{}", host, port, database);
        } else {
            jedisPool = new JedisPool(poolConfig, host, port, timeout, null, database);
            log.info("✅ JedisPool created without password: {}:{}/{}", host, port, database);
        }

        return jedisPool;
    }
}
