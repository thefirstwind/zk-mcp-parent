package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceNodeEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provider 持久化服务
 * 
 * 参考 mcp-router-v3 的 McpServerPersistenceService，提供：
 * 1. 同步持久化 Provider 注册/注销信息
 * 2. 定期更新 Provider 心跳状态
 * 3. 自动清理过期的离线 Provider
 * 4. 提供 Provider 信息查询接口和统计信息
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-12-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderPersistenceService {
    
    private final DubboServiceDbService dubboServiceDbService;
    @Deprecated
    private final ProviderInfoDbService providerInfoDbService; // 已废弃，保留用于向后兼容
    private final InterfaceWhitelistService interfaceWhitelistService;
    
    // 统计指标
    private final AtomicLong totalRegistrations = new AtomicLong(0);
    private final AtomicLong totalDeregistrations = new AtomicLong(0);
    private final AtomicLong totalHeartbeats = new AtomicLong(0);
    private final AtomicLong failedOperations = new AtomicLong(0);
    
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("✅ ProviderPersistenceService initialized successfully");
        log.info("📊 Database persistence is ENABLED for Provider registration");
    }
    
    /**
     * 持久化 Provider 注册信息（同步操作）
     * 注册操作频率低，可以同步持久化确保一致性
     * 
     * @param providerInfo Provider 信息
     */
    @Transactional
    public void persistProviderRegistration(ProviderInfo providerInfo) {
        try {
            if (providerInfo == null) {
                log.warn("⚠️ ProviderInfo is null, skipping persistence");
                return;
            }
            
            // 白名单检查：只有匹配白名单的接口才准许入库
            if (interfaceWhitelistService != null && !interfaceWhitelistService.isAllowed(providerInfo.getInterfaceName())) {
                log.debug("接口 {} 不在白名单中，跳过入库", providerInfo.getInterfaceName());
                return;
            }
            
            log.debug("🔍 Persisting Provider: {} ({}:{})", 
                providerInfo.getInterfaceName(), providerInfo.getIp(), providerInfo.getPort());
            
            // 1. 保存或更新服务信息和节点信息（已合并，包含心跳和状态信息）
            // 注意：已废弃 zk_provider_info 表，现在直接使用 zk_dubbo_service_node 存储所有信息
            dubboServiceDbService.saveOrUpdateServiceWithNode(providerInfo);
            
            totalRegistrations.incrementAndGet();
            log.info("✅ Provider persisted to database: {} ({}:{}) - online={}, healthy={}", 
                providerInfo.getInterfaceName(), providerInfo.getIp(), providerInfo.getPort(),
                providerInfo.isOnline(), true);
            
        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.error("❌ Failed to persist Provider registration: {} - {}", 
                providerInfo != null ? providerInfo.getInterfaceName() : "null", e.getMessage(), e);
        }
    }
    
    /**
     * 持久化 Provider 注销信息
     * 
     * @param zkPath ZooKeeper 路径
     */
    @Transactional
    public void persistProviderDeregistration(String zkPath) {
        try {
            if (zkPath == null || zkPath.isEmpty()) {
                log.warn("⚠️ ZkPath is null or empty, skipping deregistration");
                return;
            }
            
            // 标记 Provider 为离线（使用新表结构）
            try {
                ProviderInfo providerInfo = dubboServiceDbService.findProviderByZkPath(zkPath);
                if (providerInfo != null) {
                    DubboServiceEntity service = dubboServiceDbService.findByInterfaceName(providerInfo.getInterfaceName());
                    if (service != null) {
                        dubboServiceDbService.updateOnlineStatus(service.getId(), providerInfo.getAddress(), false);
                    }
                }
            } catch (Exception e) {
                log.warn("标记 Provider 为离线失败: {}", zkPath, e);
            }
            
            totalDeregistrations.incrementAndGet();
            log.debug("✅ Provider deregistration persisted: {}", zkPath);
            
        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.error("❌ Failed to persist Provider deregistration: {} - {}", 
                zkPath, e.getMessage());
        }
    }
    
    /**
     * 更新 Provider 健康检查时间
     * 
     * @param zkPath ZooKeeper 路径
     */
    public void updateProviderHealthCheck(String zkPath) {
        try {
            if (zkPath == null || zkPath.isEmpty()) {
                return;
            }
            
            // 更新心跳时间（使用新表结构）
            try {
                ProviderInfo providerInfo = dubboServiceDbService.findProviderByZkPath(zkPath);
                if (providerInfo != null) {
                    DubboServiceEntity service = dubboServiceDbService.findByInterfaceName(providerInfo.getInterfaceName());
                    if (service != null) {
                        dubboServiceDbService.updateLastHeartbeat(service.getId(), providerInfo.getAddress(), LocalDateTime.now());
                    }
                }
            } catch (Exception e) {
                log.debug("更新心跳时间失败: {} - {}", zkPath, e.getMessage());
            }
            
            totalHeartbeats.incrementAndGet();
            log.trace("🫀 Provider health check updated: {}", zkPath);
            
        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.debug("Failed to update Provider health check: {} - {}", 
                zkPath, e.getMessage());
        }
    }
    
    /**
     * 更新 Provider 健康状态
     * 
     * @param zkPath ZooKeeper 路径
     * @param healthy 是否健康
     */
    public void updateProviderHealthStatus(String zkPath, boolean healthy) {
        try {
            if (zkPath == null || zkPath.isEmpty()) {
                return;
            }
            
            // 更新健康状态（使用新表结构）
            try {
                ProviderInfo providerInfo = dubboServiceDbService.findProviderByZkPath(zkPath);
                if (providerInfo != null) {
                    DubboServiceEntity service = dubboServiceDbService.findByInterfaceName(providerInfo.getInterfaceName());
                    if (service != null) {
                        dubboServiceDbService.updateHealthStatus(service.getId(), providerInfo.getAddress(), healthy);
                    }
                }
            } catch (Exception e) {
                log.warn("更新健康状态失败: {} - {}", zkPath, e.getMessage());
            }
            
            log.debug("✅ Provider health status updated: {} -> {}", 
                zkPath, healthy ? "HEALTHY" : "UNHEALTHY");
            
        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.error("❌ Failed to update Provider health status: {} - {}", 
                zkPath, e.getMessage());
        }
    }
    
    /**
     * 标记服务的所有临时节点为不健康
     * 当 ZooKeeper 检测到服务的所有实例都下线时调用
     * 
     * @param interfaceName 接口名
     */
    public void markEphemeralProvidersUnhealthy(String interfaceName) {
        try {
            // TODO: 实现标记临时节点为不健康的逻辑
            // 需要根据 interfaceName 查找所有临时节点并标记为不健康
            log.debug("ℹ️ Marking ephemeral providers as unhealthy for interface: {}", interfaceName);
            
        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.error("❌ Failed to mark ephemeral providers as unhealthy: {} - {}", 
                interfaceName, e.getMessage());
        }
    }
    
    /**
     * 定期检查并标记健康检查超时的 Provider 为离线
     * 每2分钟执行一次，标记超过5分钟未健康检查的 Provider
     */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void checkAndMarkTimeoutProviders() {
        try {
            // 查询超过5分钟未健康检查的 Provider（使用新表结构）
            List<DubboServiceNodeEntity> timeoutNodes = dubboServiceDbService.findNodesByHealthCheckTimeout(5);
            
            if (!timeoutNodes.isEmpty()) {
                int markedCount = 0;
                for (DubboServiceNodeEntity node : timeoutNodes) {
                    try {
                        dubboServiceDbService.updateOnlineStatus(node.getServiceId(), node.getAddress(), false);
                        markedCount++;
                    } catch (Exception e) {
                        log.warn("⚠️ Failed to mark Provider offline: serviceId={}, address={}", 
                            node.getServiceId(), node.getAddress(), e);
                    }
                }
                
                log.warn("⚠️ Marked {} Providers as offline due to health check timeout", markedCount);
                totalDeregistrations.addAndGet(markedCount);
            }
            
        } catch (Exception e) {
            log.error("Failed to check and mark timeout Providers: {}", e.getMessage());
        }
    }
    
    /**
     * 定期清理过期的离线 Provider 记录
     * 每天凌晨3点执行，删除7天前离线的 Provider 记录
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredOfflineProviders() {
        try {
            LocalDateTime beforeTime = LocalDateTime.now().minusDays(7);
            int deleted = dubboServiceDbService.deleteOfflineNodesBefore(beforeTime);
            
            if (deleted > 0) {
                log.info("🧹 Cleaned up {} expired offline Provider records", deleted);
            }
            
        } catch (Exception e) {
            log.error("Failed to cleanup expired offline Providers: {}", e.getMessage());
        }
    }
    
    /**
     * 获取统计信息
     * 
     * @return 统计信息 Map
     */
    public Map<String, Object> getStatistics() {
        try {
            int onlineCount = dubboServiceDbService.countOnlineNodes();
            int healthyCount = dubboServiceDbService.countHealthyNodes();
            
            return Map.of(
                "total_registrations", totalRegistrations.get(),
                "total_deregistrations", totalDeregistrations.get(),
                "total_heartbeats", totalHeartbeats.get(),
                "failed_operations", failedOperations.get(),
                "online_providers", onlineCount,
                "healthy_providers", healthyCount
            );
        } catch (Exception e) {
            log.error("Failed to get statistics: {}", e.getMessage());
            return Map.of();
        }
    }
}

