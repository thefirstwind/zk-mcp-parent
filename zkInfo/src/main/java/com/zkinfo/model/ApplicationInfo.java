package com.zkinfo.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dubbo 应用信息聚合实体
 * 
 * 按应用维度聚合和管理所有服务提供者信息，提供应用级别的统计、查询和管理功能。
 * 该实体是系统中应用数据的核心载体，支持多维度的数据组织和快速查询。
 * 
 * <p>数据组织方式：</p>
 * <ul>
 *   <li><strong>providers</strong>: 应用下所有服务提供者的完整列表</li>
 *   <li><strong>providersByInterface</strong>: 按服务接口分组的提供者索引</li>
 *   <li><strong>统计信息</strong>: 在线/离线提供者数量统计</li>
 *   <li><strong>时间信息</strong>: 首次发现时间和最后更新时间</li>
 * </ul>
 * 
 * <p>核心功能：</p>
 * <ul>
 *   <li>提供者信息的增删改查</li>
 *   <li>按接口维度的快速查询</li>
 *   <li>实时统计信息更新</li>
 *   <li>应用状态管理</li>
 *   <li>线程安全的并发操作</li>
 * </ul>
 * 
 * <p>状态枚举：</p>
 * <ul>
 *   <li>ONLINE: 应用有在线的服务提供者</li>
 *   <li>OFFLINE: 应用所有服务提供者都离线</li>
 *   <li>UNKNOWN: 应用状态未知</li>
 * </ul>
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Data
@NoArgsConstructor
public class ApplicationInfo {
    
    /**
     * 应用名称
     */
    private String applicationName;
    
    /**
     * 应用下的所有Provider信息
     */
    private List<ProviderInfo> providers = new CopyOnWriteArrayList<>();
    
    /**
     * 按接口名分组的Provider信息
     */
    private Map<String, List<ProviderInfo>> providersByInterface = new ConcurrentHashMap<>();
    
    /**
     * 在线的Provider数量
     */
    private int onlineProviderCount;
    
    /**
     * 总Provider数量
     */
    private int totalProviderCount;
    
    /**
     * 应用首次发现时间
     */
    private LocalDateTime firstDiscoveredTime;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdateTime;
    
    /**
     * 应用状态
     */
    private ApplicationStatus status = ApplicationStatus.UNKNOWN;
    
    public ApplicationInfo(String applicationName) {
        this.applicationName = applicationName;
        this.firstDiscoveredTime = LocalDateTime.now();
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    /**
     * 添加Provider信息
     */
    public synchronized void addProvider(ProviderInfo provider) {
        providers.add(provider);
        
        // 按接口分组
        String interfaceName = provider.getInterfaceName();
        providersByInterface.computeIfAbsent(interfaceName, k -> new CopyOnWriteArrayList<>()).add(provider);
        
        updateCounts();
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    /**
     * 移除Provider信息
     */
    public synchronized void removeProvider(ProviderInfo provider) {
        providers.remove(provider);
        
        // 从接口分组中移除
        String interfaceName = provider.getInterfaceName();
        List<ProviderInfo> interfaceProviders = providersByInterface.get(interfaceName);
        if (interfaceProviders != null) {
            interfaceProviders.remove(provider);
            if (interfaceProviders.isEmpty()) {
                providersByInterface.remove(interfaceName);
            }
        }
        
        updateCounts();
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    /**
     * 更新Provider状态
     */
    public synchronized void updateProviderStatus(String address, boolean online) {
        providers.stream()
                .filter(p -> address.equals(p.getAddress()))
                .forEach(p -> {
                    p.setOnline(online);
                    if (online) {
                        p.setLastHeartbeat(LocalDateTime.now());
                    }
                });
        
        updateCounts();
        updateApplicationStatus();
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    /**
     * 更新统计数量
     */
    private void updateCounts() {
        this.totalProviderCount = providers.size();
        this.onlineProviderCount = (int) providers.stream().filter(ProviderInfo::isOnline).count();
    }
    
    /**
     * 更新应用状态
     */
    private void updateApplicationStatus() {
        if (totalProviderCount == 0) {
            this.status = ApplicationStatus.OFFLINE;
        } else if (onlineProviderCount == 0) {
            this.status = ApplicationStatus.OFFLINE;
        } else if (onlineProviderCount == totalProviderCount) {
            this.status = ApplicationStatus.ONLINE;
        } else {
            this.status = ApplicationStatus.PARTIAL;
        }
    }
    
    /**
     * 获取所有接口名称
     */
    public List<String> getInterfaceNames() {
        return List.copyOf(providersByInterface.keySet());
    }
    
    /**
     * 根据接口名获取Provider列表
     */
    public List<ProviderInfo> getProvidersByInterface(String interfaceName) {
        return providersByInterface.getOrDefault(interfaceName, List.of());
    }
    
    /**
     * 应用状态枚举
     */
    public enum ApplicationStatus {
        ONLINE,    // 全部在线
        OFFLINE,   // 全部离线
        PARTIAL,   // 部分在线
        UNKNOWN    // 未知状态
    }
}
