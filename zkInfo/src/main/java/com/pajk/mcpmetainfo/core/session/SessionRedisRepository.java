package com.pajk.mcpmetainfo.core.session;

import com.pajk.mcpmetainfo.core.config.McpSessionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class SessionRedisRepository {

    private static final Logger log = LoggerFactory.getLogger(SessionRedisRepository.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final RedisClient redisClient;
    private final McpSessionProperties properties;

    public SessionRedisRepository(RedisClient redisClient, McpSessionProperties properties) {
        this.redisClient = redisClient;
        this.properties = properties;
        log.info("✅ SessionRedisRepository initialized with RedisClient: {}", redisClient.getClass().getSimpleName());
    }

    public void saveSessionMeta(SessionMeta meta) {
        if (meta == null || meta.getSessionId() == null) {
            return;
        }
        String sessionKey = sessionKey(meta.getSessionId());
        Map<String, String> map = meta.toMap();
        
        long ttlSeconds = properties.getTtl().getSeconds();
        try {
            // 第三层：保存 sessionId 和具体的内容
            redisClient.hsetAll(sessionKey, map);
            redisClient.expire(sessionKey, ttlSeconds);
            
            // 第二层：保存 instance 和 sessionId 的关系
            redisClient.sadd(instanceKey(meta.getInstanceId()), meta.getSessionId());
            redisClient.expire(instanceKey(meta.getInstanceId()), ttlSeconds);
            
            // 第一层：固定的 key，保存所有 instance
            redisClient.sadd(instancesKey(), meta.getInstanceId());
            // instances key 不设置过期时间，保持持久化
        } catch (Exception e) {
            log.error("Failed to save session meta for sessionId: {}", meta.getSessionId(), e);
        }
    }

    public Optional<SessionMeta> findSession(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        String key = sessionKey(sessionId);
        try {
            Map<String, String> map = redisClient.hgetAll(key);
            if (map == null || map.isEmpty()) {
                return Optional.empty();
            }
            // Convert Map<String, String> to Map<Object, Object> for compatibility with SessionMeta.fromMap
            Map<Object, Object> objectMap = new HashMap<>(map);
            return Optional.ofNullable(SessionMeta.fromMap(objectMap));
        } catch (Exception e) {
            log.error("Failed to find session for sessionId: {}", sessionId, e);
            return Optional.empty();
        }
    }

    public void updateLastActive(String sessionId) {
        if (sessionId == null) {
            return;
        }
        String key = sessionKey(sessionId);
        long ttlSeconds = properties.getTtl().getSeconds();
        try {
            redisClient.hset(key, "lastActive", FORMATTER.format(LocalDateTime.now()));
            redisClient.hset(key, "active", Boolean.TRUE.toString());
            redisClient.expire(key, ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to update lastActive for sessionId: {}", sessionId, e);
        }
    }

    public void updateBackendSessionId(String sessionId, String backendSessionId) {
        if (sessionId == null) {
            return;
        }
        String key = sessionKey(sessionId);
        long ttlSeconds = properties.getTtl().getSeconds();
        try {
            redisClient.hset(key, "backendSessionId", Optional.ofNullable(backendSessionId).orElse(""));
            redisClient.expire(key, ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to update backendSessionId for sessionId: {}", sessionId, e);
        }
    }

    public void removeSession(String sessionId, String instanceId) {
        if (sessionId == null) {
            return;
        }
        String targetInstance = instanceId;
        if (targetInstance == null) {
            targetInstance = findSession(sessionId).map(SessionMeta::getInstanceId).orElse(null);
        }
        String key = sessionKey(sessionId);
        try {
            // 第三层：删除 session 数据
            redisClient.del(key);
            
            if (targetInstance != null) {
                // 第二层：从 instance 的 sessionId 集合中删除
                redisClient.srem(instanceKey(targetInstance), sessionId);
                
                // 检查该 instance 下是否还有其他 sessions
                Set<String> remainingSessions = redisClient.smembers(instanceKey(targetInstance));
                if (remainingSessions == null || remainingSessions.isEmpty()) {
                    // 如果该 instance 下没有 sessions 了，清理该 instance
                    // 从第一层的 instances key 中删除该 instanceId
                    redisClient.srem(instancesKey(), targetInstance);
                    // 删除第二层的 instance key
                    redisClient.del(instanceKey(targetInstance));
                    log.debug("Removed empty instance {} from instances set and deleted instance key", targetInstance);
                }
            }
        } catch (Exception e) {
            log.error("Failed to remove session for sessionId: {}", sessionId, e);
        }
    }

    public List<SessionMeta> findAllSessions() {
        String pattern = sessionKey("*");
        try {
            Set<String> keys = redisClient.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                // 如果没有session，清理空的instance
                cleanupEmptyInstances();
                return Collections.emptyList();
            }
            List<SessionMeta> sessions = keys.stream()
                    .map(k -> {
                        try {
                            Map<String, String> map = redisClient.hgetAll(k);
                            if (map == null || map.isEmpty()) {
                                return null;
                            }
                            // Convert Map<String, String> to Map<Object, Object> for compatibility
                            Map<Object, Object> objectMap = new HashMap<>(map);
                            return SessionMeta.fromMap(objectMap);
                        } catch (Exception e) {
                            log.warn("Failed to read session data for key: {}", k, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            
            // 清理空的instance（session过期时）
            cleanupEmptyInstances();
            
            return sessions;
        } catch (Exception e) {
            log.error("Failed to findAllSessions", e);
            return Collections.emptyList();
        }
    }

    private String sessionKey(String sessionId) {
        return properties.getRedisPrefix() + ":sessions:" + sessionId;
    }

    private String instanceKey(String instanceId) {
        return properties.getRedisPrefix() + ":instance:" + instanceId;
    }
    
    /**
     * 第一层：固定的 key，保存所有 instance
     */
    private String instancesKey() {
        return properties.getRedisPrefix() + ":instances";
    }
    
    /**
     * 获取所有 instance IDs（自动清理空的 instance）
     */
    public Set<String> findAllInstances() {
        try {
            // 先清理空的 instance
            cleanupEmptyInstances();
            
            // 返回清理后的有效 instances
            Set<String> allInstances = redisClient.smembers(instancesKey());
            if (allInstances == null || allInstances.isEmpty()) {
                return Collections.emptySet();
            }
            
            // 再次验证，只返回确实有有效 session 的 instance
            Set<String> validInstances = new HashSet<>();
            for (String instanceId : allInstances) {
                Set<String> sessionIds = redisClient.smembers(instanceKey(instanceId));
                if (sessionIds != null && !sessionIds.isEmpty()) {
                    // 检查是否有有效的 session（session key 存在）
                    boolean hasValidSession = false;
                    for (String sessionId : sessionIds) {
                        Map<String, String> sessionData = redisClient.hgetAll(sessionKey(sessionId));
                        if (sessionData != null && !sessionData.isEmpty()) {
                            hasValidSession = true;
                            break;
                        }
                    }
                    
                    if (hasValidSession) {
                        validInstances.add(instanceId);
                    }
                }
            }
            
            return validInstances;
        } catch (Exception e) {
            log.error("Failed to findAllInstances", e);
            return Collections.emptySet();
        }
    }
    
    /**
     * 根据 instanceId 获取该 instance 下的所有 sessionIds
     */
    public Set<String> findSessionIdsByInstance(String instanceId) {
        if (instanceId == null) {
            return Collections.emptySet();
        }
        try {
            return redisClient.smembers(instanceKey(instanceId));
        } catch (Exception e) {
            log.error("Failed to findSessionIdsByInstance for instanceId: {}", instanceId, e);
            return Collections.emptySet();
        }
    }
    
    /**
     * 清理所有没有sessionId的instance
     * 检查每个instance下的sessionId集合，如果为空或所有session都已过期，则清理该instance
     * 
     * @return 清理的instance数量
     */
    public int cleanupEmptyInstances() {
        try {
            Set<String> allInstances = redisClient.smembers(instancesKey());
            if (allInstances == null || allInstances.isEmpty()) {
                return 0;
            }
            
            int cleanedCount = 0;
            Set<String> emptyInstances = new HashSet<>();
            
            for (String instanceId : allInstances) {
                Set<String> sessionIds = redisClient.smembers(instanceKey(instanceId));
                if (sessionIds == null || sessionIds.isEmpty()) {
                    // 该 instance 下没有任何 sessions，标记为待删除
                    emptyInstances.add(instanceId);
                } else {
                    // 检查是否有有效的 session（session key 存在且未过期）
                    boolean hasValidSession = false;
                    for (String sessionId : sessionIds) {
                        Map<String, String> sessionData = redisClient.hgetAll(sessionKey(sessionId));
                        if (sessionData != null && !sessionData.isEmpty()) {
                            hasValidSession = true;
                            break;
                        }
                    }
                    
                    if (!hasValidSession) {
                        // 该 instance 下的所有 sessions 都已过期，标记为待删除
                        emptyInstances.add(instanceId);
                    }
                }
            }
            
            // 清理空的 instance
            for (String emptyInstanceId : emptyInstances) {
                try {
                    // 从第一层的 instances key 中删除该 instanceId
                    redisClient.srem(instancesKey(), emptyInstanceId);
                    // 删除第二层的 instance key（如果还存在）
                    redisClient.del(instanceKey(emptyInstanceId));
                    cleanedCount++;
                    log.debug("Cleaned up empty instance: {}", emptyInstanceId);
                } catch (Exception e) {
                    log.warn("Failed to clean up empty instance: {}", emptyInstanceId, e);
                }
            }
            
            if (cleanedCount > 0) {
                log.info("Cleaned up {} empty instances", cleanedCount);
            }
            
            return cleanedCount;
        } catch (Exception e) {
            log.error("Failed to cleanup empty instances", e);
            return 0;
        }
    }
}


