package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceNodeEntity;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provider信息数据库服务类（已重构）
 * 
 * 注意：已废弃 zk_provider_info、zk_provider_method、zk_provider_parameter 表
 * 现在所有功能都通过 zk_dubbo_service_node 和 zk_dubbo_service 表实现
 * 
 * 本服务类保留是为了向后兼容，实际功能已迁移到 DubboServiceDbService
 * 
 * @author ZkInfo Team
 * @version 2.0.0
 * @since 2024-01-01
 * @deprecated 建议直接使用 DubboServiceDbService
 */
@Slf4j
@Service
@Deprecated
public class ProviderInfoDbService {
    
    @Autowired
    private DubboServiceDbService dubboServiceDbService;
    
    @Autowired(required = false)
    private InterfaceWhitelistService interfaceWhitelistService;
    
    /**
     * 保存或更新Provider信息到数据库
     * 
     * 注意：已重构，现在通过 DubboServiceDbService 保存到 zk_dubbo_service_node
     * 
     * @param providerInfo Provider信息
     * @return null（已废弃，保留方法签名用于兼容）
     * @deprecated 请使用 DubboServiceDbService.saveOrUpdateServiceWithNode()
     */
    @Deprecated
    public Object saveOrUpdateProvider(ProviderInfo providerInfo) {
        try {
            // 白名单检查
            if (interfaceWhitelistService != null && !interfaceWhitelistService.isAllowed(providerInfo.getInterfaceName())) {
                log.debug("接口 {} 不在白名单中，跳过入库", providerInfo.getInterfaceName());
                return null;
            }
            
            // 使用新的服务保存
            dubboServiceDbService.saveOrUpdateServiceWithNode(providerInfo);
            log.debug("Provider信息已保存到新表结构: {}", providerInfo.getZkPath());
            
            return null; // 已废弃，返回 null
        } catch (Exception e) {
            log.error("保存Provider信息失败: {}", providerInfo.getZkPath(), e);
            throw new RuntimeException("保存Provider信息失败", e);
        }
    }
    
    /**
     * 根据ZooKeeper路径查找Provider信息
     * 
     * @deprecated 请使用 DubboServiceDbService 查询
     */
    @Deprecated
    public Optional<Object> findByZkPath(String zkPath) {
        log.warn("findByZkPath() 已废弃，请使用 DubboServiceDbService");
        return Optional.empty();
    }
    
    /**
     * 查找已审批的Provider信息列表
     * 
     * @deprecated 请使用 DubboServiceDbService 查询
     */
    @Deprecated
    public List<Object> findApprovedProviders() {
        log.warn("findApprovedProviders() 已废弃，请使用 DubboServiceDbService");
        return List.of();
    }
    
    /**
     * 根据审批状态查找Provider信息列表
     * 
     * @deprecated 请使用 DubboServiceDbService 查询
     */
    @Deprecated
    public List<Object> findByApprovalStatus(String approvalStatus) {
        log.warn("findByApprovalStatus() 已废弃，请使用 DubboServiceDbService");
        return List.of();
    }
    
    /**
     * 根据ID查找Provider信息
     * 
     * @deprecated 请使用 DubboServiceDbService 查询
     */
    @Deprecated
    public Object findById(Long id) {
        log.warn("findById() 已废弃，请使用 DubboServiceDbService");
        return null;
    }
    
    /**
     * 根据ZooKeeper路径和审批状态查找Provider信息
     * 
     * @deprecated 请使用 DubboServiceDbService 查询
     */
    @Deprecated
    public Optional<Object> findByZkPathAndApprovalStatus(String zkPath, String approvalStatus) {
        try {
            // 尝试从新表结构查询
            ProviderInfo providerInfo = dubboServiceDbService.findProviderByZkPath(zkPath);
            if (providerInfo != null) {
                // 检查审批状态（通过 service_id 关联 zk_dubbo_service）
                DubboServiceEntity service = dubboServiceDbService.findByInterfaceName(providerInfo.getInterfaceName());
                if (service != null && approvalStatus.equals(service.getApprovalStatus())) {
                    return Optional.of(providerInfo);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("查询Provider信息失败: zkPath={}", zkPath, e);
            return Optional.empty();
        }
    }
    
    /**
     * 标记 Provider 为离线
     * 
     * @param zkPath ZooKeeper 路径
     */
    public void markProviderOffline(String zkPath) {
        try {
            ProviderInfo providerInfo = dubboServiceDbService.findProviderByZkPath(zkPath);
            if (providerInfo != null) {
                DubboServiceEntity service = dubboServiceDbService.findByInterfaceName(providerInfo.getInterfaceName());
                if (service != null) {
                    dubboServiceDbService.updateOnlineStatus(service.getId(), providerInfo.getAddress(), false);
                    log.debug("✅ Marked Provider as offline: {}", zkPath);
                }
            }
        } catch (Exception e) {
            log.error("Failed to mark Provider offline: {}", zkPath, e);
        }
    }
    
    /**
     * 更新最后心跳时间
     * 
     * @param zkPath ZooKeeper 路径
     * @param lastHeartbeat 最后心跳时间
     */
    public void updateLastHeartbeat(String zkPath, LocalDateTime lastHeartbeat) {
        try {
            ProviderInfo providerInfo = dubboServiceDbService.findProviderByZkPath(zkPath);
            if (providerInfo != null) {
                DubboServiceEntity service = dubboServiceDbService.findByInterfaceName(providerInfo.getInterfaceName());
                if (service != null) {
                    dubboServiceDbService.updateLastHeartbeat(service.getId(), providerInfo.getAddress(), lastHeartbeat);
                }
            }
        } catch (Exception e) {
            log.error("Failed to update last heartbeat: {}", zkPath, e);
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
            ProviderInfo providerInfo = dubboServiceDbService.findProviderByZkPath(zkPath);
            if (providerInfo != null) {
                DubboServiceEntity service = dubboServiceDbService.findByInterfaceName(providerInfo.getInterfaceName());
                if (service != null) {
                    dubboServiceDbService.updateHealthStatus(service.getId(), providerInfo.getAddress(), healthy);
                }
            }
        } catch (Exception e) {
            log.error("Failed to update Provider health status: {}", zkPath, e);
        }
    }
    
    /**
     * 查找健康检查超时的 Provider
     * 
     * @param timeoutMinutes 超时分钟数
     * @return Provider 列表
     */
    public List<ProviderInfo> findProvidersByHealthCheckTimeout(int timeoutMinutes) {
        try {
            List<DubboServiceNodeEntity> timeoutNodes = dubboServiceDbService.findNodesByHealthCheckTimeout(timeoutMinutes);
            return timeoutNodes.stream()
                    .map(node -> {
                        DubboServiceEntity service = dubboServiceDbService.findById(node.getServiceId());
                        if (service != null) {
                            return dubboServiceDbService.convertToProviderInfo(service, node);
                        }
                        return null;
                    })
                    .filter(providerInfo -> providerInfo != null)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to find Providers by health check timeout: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * 删除指定时间之前的离线 Provider 记录
     * 
     * @param beforeTime 时间阈值
     * @return 删除的记录数
     */
    public int deleteOfflineProvidersBefore(LocalDateTime beforeTime) {
        try {
            return dubboServiceDbService.deleteOfflineNodesBefore(beforeTime);
        } catch (Exception e) {
            log.error("Failed to delete offline Providers before: {}", beforeTime, e);
            return 0;
        }
    }
    
    /**
     * 统计在线 Provider 数量
     * 
     * @return 在线 Provider 数量
     */
    public int countOnlineProviders() {
        try {
            return dubboServiceDbService.countOnlineNodes();
        } catch (Exception e) {
            log.error("Failed to count online Providers: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * 统计健康的 Provider 数量
     * 
     * @return 健康的 Provider 数量
     */
    public int countHealthyProviders() {
        try {
            return dubboServiceDbService.countHealthyNodes();
        } catch (Exception e) {
            log.error("Failed to count healthy Providers: {}", e.getMessage());
            return 0;
        }
    }
}
