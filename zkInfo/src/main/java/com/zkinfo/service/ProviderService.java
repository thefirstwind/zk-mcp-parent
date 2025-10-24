package com.zkinfo.service;

import com.zkinfo.model.ApplicationInfo;
import com.zkinfo.model.ProviderInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 服务提供者信息管理服务
 * 
 * 负责管理所有 Dubbo 服务提供者的信息，包括提供者的注册、查询、统计、搜索等功能。
 * 按应用维度组织服务提供者信息，支持多种查询方式和统计分析。
 * 
 * <p>核心功能：</p>
 * <ul>
 *   <li>服务提供者信息的增删改查</li>
 *   <li>按应用、接口、地址等维度的信息组织</li>
 *   <li>在线状态统计和监控</li>
 *   <li>关键词搜索和过滤</li>
 *   <li>应用级别的信息聚合</li>
 * </ul>
 * 
 * <p>数据结构：</p>
 * <ul>
 *   <li>applications: 按应用名存储应用信息的主索引</li>
 *   <li>providersByZkPath: 按 ZooKeeper 路径存储的快速查找索引</li>
 * </ul>
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
public class ProviderService {
    
    /**
     * 按应用名存储应用信息
     */
    private final ConcurrentHashMap<String, ApplicationInfo> applications = new ConcurrentHashMap<>();
    
    /**
     * 按ZK路径索引Provider信息，用于快速查找和删除
     */
    private final ConcurrentHashMap<String, ProviderInfo> providersByZkPath = new ConcurrentHashMap<>();
    
    /**
     * 添加Provider信息
     */
    public void addProvider(ProviderInfo provider) {
        try {
            provider.setRegisterTime(LocalDateTime.now());
            provider.setLastHeartbeat(LocalDateTime.now());
            
            String applicationName = getApplicationName(provider);
            
            // 获取或创建应用信息
            ApplicationInfo appInfo = applications.computeIfAbsent(applicationName, ApplicationInfo::new);
            
            // 添加到应用中
            appInfo.addProvider(provider);
            
            // 添加到ZK路径索引
            if (provider.getZkPath() != null) {
                providersByZkPath.put(provider.getZkPath(), provider);
            }
            
            log.info("添加Provider: {} -> {}:{}", 
                    provider.getInterfaceName(), 
                    provider.getAddress(), 
                    applicationName);
            
        } catch (Exception e) {
            log.error("添加Provider失败", e);
        }
    }
    
    /**
     * 更新Provider信息
     */
    public void updateProvider(ProviderInfo provider) {
        try {
            // 先删除旧的Provider
            if (provider.getZkPath() != null) {
                removeProviderByZkPath(provider.getZkPath());
            }
            
            // 再添加新的Provider
            addProvider(provider);
            
            log.info("更新Provider: {} -> {}", 
                    provider.getInterfaceName(), 
                    provider.getAddress());
            
        } catch (Exception e) {
            log.error("更新Provider失败", e);
        }
    }
    
    /**
     * 根据ZK路径移除Provider
     */
    public void removeProviderByZkPath(String zkPath) {
        try {
            ProviderInfo provider = providersByZkPath.remove(zkPath);
            if (provider != null) {
                String applicationName = getApplicationName(provider);
                ApplicationInfo appInfo = applications.get(applicationName);
                
                if (appInfo != null) {
                    appInfo.removeProvider(provider);
                    
                    // 如果应用下没有Provider了，移除应用
                    if (appInfo.getTotalProviderCount() == 0) {
                        applications.remove(applicationName);
                        log.info("移除空应用: {}", applicationName);
                    }
                }
                
                log.info("移除Provider: {} -> {}", 
                        provider.getInterfaceName(), 
                        provider.getAddress());
            }
            
        } catch (Exception e) {
            log.error("移除Provider失败: {}", zkPath, e);
        }
    }
    
    /**
     * 更新Provider在线状态
     */
    public void updateProviderStatus(String address, boolean online) {
        try {
            for (ApplicationInfo appInfo : applications.values()) {
                appInfo.updateProviderStatus(address, online);
            }
            
            log.debug("更新Provider状态: {} -> {}", address, online ? "在线" : "离线");
            
        } catch (Exception e) {
            log.error("更新Provider状态失败: {}", address, e);
        }
    }
    
    /**
     * 获取所有应用信息
     */
    public List<ApplicationInfo> getAllApplications() {
        return new ArrayList<>(applications.values());
    }
    
    /**
     * 根据应用名获取应用信息
     */
    public ApplicationInfo getApplicationByName(String applicationName) {
        return applications.get(applicationName);
    }
    
    /**
     * 获取所有应用名称
     */
    public List<String> getAllApplicationNames() {
        return new ArrayList<>(applications.keySet());
    }
    
    /**
     * 根据接口名获取所有Provider
     */
    public List<ProviderInfo> getProvidersByInterface(String interfaceName) {
        return applications.values().stream()
                .flatMap(app -> app.getProviders().stream())
                .filter(provider -> interfaceName.equals(provider.getInterfaceName()))
                .collect(Collectors.toList());
    }
    
    /**
     * 获取所有接口名称
     */
    public List<String> getAllInterfaces() {
        return applications.values().stream()
                .flatMap(app -> app.getProviders().stream())
                .map(ProviderInfo::getInterfaceName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
    
    /**
     * 获取在线Provider数量统计
     */
    public Map<String, Integer> getOnlineStats() {
        Map<String, Integer> stats = new HashMap<>();
        
        int totalProviders = 0;
        int onlineProviders = 0;
        int totalApplications = applications.size();
        int onlineApplications = 0;
        
        for (ApplicationInfo appInfo : applications.values()) {
            totalProviders += appInfo.getTotalProviderCount();
            onlineProviders += appInfo.getOnlineProviderCount();
            
            if (appInfo.getStatus() == ApplicationInfo.ApplicationStatus.ONLINE ||
                appInfo.getStatus() == ApplicationInfo.ApplicationStatus.PARTIAL) {
                onlineApplications++;
            }
        }
        
        stats.put("totalApplications", totalApplications);
        stats.put("onlineApplications", onlineApplications);
        stats.put("totalProviders", totalProviders);
        stats.put("onlineProviders", onlineProviders);
        
        return stats;
    }
    
    /**
     * 搜索Provider
     */
    public List<ProviderInfo> searchProviders(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllProviders();
        }
        
        String lowerKeyword = keyword.toLowerCase();
        
        return applications.values().stream()
                .flatMap(app -> app.getProviders().stream())
                .filter(provider -> 
                        provider.getInterfaceName().toLowerCase().contains(lowerKeyword) ||
                        provider.getAddress().toLowerCase().contains(lowerKeyword) ||
                        (provider.getApplication() != null && 
                         provider.getApplication().toLowerCase().contains(lowerKeyword))
                )
                .collect(Collectors.toList());
    }
    
    /**
     * 获取所有Provider
     */
    public List<ProviderInfo> getAllProviders() {
        return applications.values().stream()
                .flatMap(app -> app.getProviders().stream())
                .collect(Collectors.toList());
    }
    
    /**
     * 清理离线Provider
     */
    public void cleanupOfflineProviders(long offlineThresholdMinutes) {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(offlineThresholdMinutes);
            
            List<ProviderInfo> toRemove = new ArrayList<>();
            
            for (ApplicationInfo appInfo : applications.values()) {
                for (ProviderInfo provider : appInfo.getProviders()) {
                    if (!provider.isOnline() && 
                        provider.getLastHeartbeat() != null && 
                        provider.getLastHeartbeat().isBefore(threshold)) {
                        toRemove.add(provider);
                    }
                }
            }
            
            for (ProviderInfo provider : toRemove) {
                if (provider.getZkPath() != null) {
                    removeProviderByZkPath(provider.getZkPath());
                }
            }
            
            if (!toRemove.isEmpty()) {
                log.info("清理了 {} 个离线Provider", toRemove.size());
            }
            
        } catch (Exception e) {
            log.error("清理离线Provider失败", e);
        }
    }
    
    /**
     * 获取应用名称
     */
    private String getApplicationName(ProviderInfo provider) {
        // 优先使用Provider中的application字段
        if (provider.getApplication() != null && !provider.getApplication().isEmpty()) {
            return provider.getApplication();
        }
        
        // 如果没有application字段，使用接口名的包名作为应用名
        String interfaceName = provider.getInterfaceName();
        if (interfaceName != null && interfaceName.contains(".")) {
            String[] parts = interfaceName.split("\\.");
            if (parts.length >= 2) {
                // 取倒数第二个部分作为应用名，例如 com.example.service.UserService -> service
                return parts[parts.length - 2];
            }
        }
        
        // 默认应用名
        return "unknown";
    }
    
    /**
     * 获取应用数量
     */
    public int getApplicationCount() {
        return applications.size();
    }
    
    /**
     * 获取Provider总数
     */
    public int getTotalProviderCount() {
        return applications.values().stream()
                .mapToInt(ApplicationInfo::getTotalProviderCount)
                .sum();
    }
    
    /**
     * 获取在线Provider数量
     */
    public int getOnlineProviderCount() {
        return applications.values().stream()
                .mapToInt(ApplicationInfo::getOnlineProviderCount)
                .sum();
    }
}
