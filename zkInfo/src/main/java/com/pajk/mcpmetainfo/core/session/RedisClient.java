package com.pajk.mcpmetainfo.core.session;

import java.util.Map;
import java.util.Set;

/**
 * Redis 操作接口，用于抽象本地和生产环境的 Redis 客户端
 */
public interface RedisClient {

    /**
     * 设置 Hash 字段
     */
    void hset(String key, String field, String value);

    /**
     * 批量设置 Hash 字段
     */
    void hsetAll(String key, Map<String, String> hash);

    /**
     * 获取 Hash 所有字段
     */
    Map<String, String> hgetAll(String key);

    /**
     * 设置过期时间（秒）
     */
    void expire(String key, long seconds);

    /**
     * 添加到 Set
     */
    void sadd(String key, String... members);

    /**
     * 从 Set 移除
     */
    void srem(String key, String... members);

    /**
     * 删除键
     */
    void del(String key);

    /**
     * 模式匹配查询键
     */
    Set<String> keys(String pattern);

    /**
     * 获取键的类型
     * @return 键的类型：string, list, set, zset, hash, none
     */
    String type(String key);

    /**
     * 获取 Set 的所有成员
     */
    Set<String> smembers(String key);

    /**
     * 获取 String 类型的值
     */
    String get(String key);

    /**
     * 执行操作（用于需要事务或批量操作的场景）
     */
    <T> T execute(RedisOperation<T> operation);

    /**
     * Redis 操作函数式接口
     */
    @FunctionalInterface
    interface RedisOperation<T> {
        T execute(RedisClient client);
    }
}
