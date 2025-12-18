package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Dubbo到MCP自动注册服务
 * 
 * 监听Zookeeper中的Dubbo服务变化，自动将发现的Dubbo服务注册为MCP服务到Nacos
 * 模拟 mcp-server-v6 的自动注册机制
 * 
 * <p>核心功能：</p>
 * <ul>
 *   <li>监听服务变化事件</li>
 *   <li>自动注册新服务到Nacos</li>
 *   <li>自动注销已移除的服务</li>
 *   <li>防重复注册机制</li>
 * </ul>
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-01-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DubboToMcpAutoRegistrationService {

    private final ProviderService providerService;
    private final NacosMcpRegistrationService nacosMcpRegistrationService;
    private final ServiceCollectionFilterService filterService;
    
    @Value("${nacos.registry.auto-register:true}")
    private boolean autoRegisterEnabled;
    
    @Value("${nacos.registry.auto-register-delay:5000}")
    private long autoRegisterDelay; // 延迟注册时间（毫秒），避免频繁注册
    
    // 已注册的服务缓存，key: serviceInterface:version:group
    private final Set<String> registeredServices = ConcurrentHashMap.newKeySet();
    
    // 待注册的服务队列，避免并发注册同一服务
    private final Map<String, Long> pendingRegistrations = new ConcurrentHashMap<>();
    
    /**
     * 处理服务添加事件
     * 当发现新的Dubbo服务时，自动注册为MCP服务到Nacos
     * 
     * @param providerInfo 新发现的Provider信息
     */
    @Async
    public void handleProviderAdded(ProviderInfo providerInfo) {
        if (!autoRegisterEnabled) {
            log.debug("Auto registration is disabled, skip");
            return;
        }
        
        try {
            String serviceKey = buildServiceKey(providerInfo);
            
            // 检查是否已注册
            if (registeredServices.contains(serviceKey)) {
                log.debug("Service already registered: {}", serviceKey);
                return;
            }
            
            // 防抖：延迟注册，避免频繁注册
            long currentTime = System.currentTimeMillis();
            Long lastPendingTime = pendingRegistrations.get(serviceKey);
            
            if (lastPendingTime != null && (currentTime - lastPendingTime) < autoRegisterDelay) {
                log.debug("Service registration pending, skip: {}", serviceKey);
                return;
            }
            
            pendingRegistrations.put(serviceKey, currentTime);
            
            // 延迟注册
            Thread.sleep(autoRegisterDelay);
            
            // 再次检查是否已注册（可能在延迟期间已注册）
            if (registeredServices.contains(serviceKey)) {
                pendingRegistrations.remove(serviceKey);
                return;
            }
            
            // 应用三层过滤机制：检查服务是否应该被采集
            if (!filterService.shouldCollect(
                    providerInfo.getInterfaceName(),
                    providerInfo.getVersion(),
                    providerInfo.getGroup())) {
                log.debug("Service {}/{} filtered out by filter service, skip registration", 
                        providerInfo.getInterfaceName(), providerInfo.getVersion());
                pendingRegistrations.remove(serviceKey);
                return;
            }
            
            // 获取该服务的所有Provider（相同接口、版本、分组）
            List<ProviderInfo> sameServiceProviders = getSameServiceProviders(providerInfo);
            
            // 去重：避免重复的方法
            sameServiceProviders = deduplicateProviders(sameServiceProviders);
            
            if (sameServiceProviders.isEmpty()) {
                log.warn("No providers found for service: {}", serviceKey);
                pendingRegistrations.remove(serviceKey);
                return;
            }
            
            // 注册到Nacos
            String version = providerInfo.getVersion() != null ? providerInfo.getVersion() : "1.0.0";
            nacosMcpRegistrationService.registerDubboServiceAsMcp(
                    providerInfo.getInterfaceName(),
                    version,
                    sameServiceProviders
            );
            
            // 标记为已注册
            registeredServices.add(serviceKey);
            pendingRegistrations.remove(serviceKey);
            
            log.info("✅ Auto registered service to Nacos: {}:{}", 
                    providerInfo.getInterfaceName(), version);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Registration interrupted: {}", providerInfo.getInterfaceName());
        } catch (Exception e) {
            log.error("Failed to auto register service: {}", providerInfo.getInterfaceName(), e);
            pendingRegistrations.remove(buildServiceKey(providerInfo));
        }
    }
    
    /**
     * 处理服务移除事件
     * 当Dubbo服务下线时，如果服务已注册，更新注册信息
     * 
     * @param providerInfo 已移除的Provider信息
     */
    @Async
    public void handleProviderRemoved(ProviderInfo providerInfo) {
        try {
            String serviceKey = buildServiceKey(providerInfo);
            
            // 只处理已注册的服务
            if (!isServiceRegistered(serviceKey)) {
                return;
            }
            
            // 检查该服务的其他Provider是否还存在
            List<ProviderInfo> remainingProviders = getSameServiceProviders(providerInfo);
            
            if (remainingProviders.isEmpty()) {
                // 没有其他Provider了，标记服务为不可用（但不注销，等待审批流程）
                log.warn("⚠️ All providers removed for registered service: {}, marking as unavailable", serviceKey);
                // TODO: 更新服务状态为不可用，但不注销
            } else {
                // 还有Provider，更新服务信息
                checkAndUpdateService(providerInfo);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle provider removed: {}", providerInfo.getInterfaceName(), e);
        }
    }
    
    /**
     * 处理服务更新事件
     * 当服务信息更新时，如果服务已注册，自动更新
     * 
     * @param providerInfo 更新的Provider信息
     */
    @Async
    public void handleProviderUpdated(ProviderInfo providerInfo) {
        try {
            String serviceKey = buildServiceKey(providerInfo);
            
            // 只处理已注册的服务
            if (isServiceRegistered(serviceKey)) {
                checkAndUpdateService(providerInfo);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle provider updated: {}", providerInfo.getInterfaceName(), e);
        }
    }
    
    /**
     * 获取相同服务的所有Provider
     * 相同服务 = 相同接口名 + 相同版本 + 相同分组
     */
    private List<ProviderInfo> getSameServiceProviders(ProviderInfo providerInfo) {
        return providerService.getAllProviders().stream()
                .filter(p -> Objects.equals(p.getInterfaceName(), providerInfo.getInterfaceName()))
                .filter(p -> Objects.equals(p.getVersion(), providerInfo.getVersion()))
                .filter(p -> Objects.equals(p.getGroup(), providerInfo.getGroup()))
                .filter(ProviderInfo::isOnline)
                .collect(Collectors.toList());
    }
    
    /**
     * 去重Provider（相同地址和端口的只保留一个）
     */
    private List<ProviderInfo> deduplicateProviders(List<ProviderInfo> providers) {
        Map<String, ProviderInfo> uniqueProviders = new LinkedHashMap<>();
        for (ProviderInfo provider : providers) {
            String key = provider.getAddress() + ":" + provider.getPort();
            if (!uniqueProviders.containsKey(key)) {
                uniqueProviders.put(key, provider);
            }
        }
        return new ArrayList<>(uniqueProviders.values());
    }
    
    /**
     * 构建服务唯一标识
     * 格式：interfaceName:version:group
     */
    private String buildServiceKey(ProviderInfo providerInfo) {
        return String.format("%s:%s:%s",
                providerInfo.getInterfaceName(),
                providerInfo.getVersion() != null ? providerInfo.getVersion() : "default",
                providerInfo.getGroup() != null ? providerInfo.getGroup() : "default"
        );
    }
    
    /**
     * 准入流程触发注册（审批通过后调用）
     * 
     * @param serviceInterface 服务接口名
     * @param version 服务版本
     * @param group 服务分组
     * @param approvalId 审批ID
     */
    public void registerAfterApproval(String serviceInterface, String version, String group, Long approvalId) {
        try {
            List<ProviderInfo> providers = providerService.getAllProviders().stream()
                    .filter(p -> Objects.equals(p.getInterfaceName(), serviceInterface))
                    .filter(p -> Objects.equals(p.getVersion(), version))
                    .filter(p -> Objects.equals(p.getGroup(), group))
                    .filter(ProviderInfo::isOnline)
                    .collect(Collectors.toList());
            
            if (providers.isEmpty()) {
                log.warn("No providers found for service: {}:{}:{}", serviceInterface, version, group);
                return;
            }
            
            nacosMcpRegistrationService.registerDubboServiceAsMcp(
                    serviceInterface,
                    version != null ? version : "1.0.0",
                    providers
            );
            
            String serviceKey = String.format("%s:%s:%s", serviceInterface, version, group);
            registeredServices.add(serviceKey);
            
            log.info("✅ Registered service to Nacos after approval (approvalId: {}): {}:{}:{}", 
                    approvalId, serviceInterface, version, group);
            
        } catch (Exception e) {
            log.error("Failed to register service after approval: {}:{}:{}", 
                    serviceInterface, version, group, e);
        }
    }
    
    /**
     * 标记服务为已注册（用于初始化已注册服务列表）
     */
    public void markServiceAsRegistered(String serviceInterface, String version, String group) {
        String serviceKey = String.format("%s:%s:%s", serviceInterface, version, group);
        registeredServices.add(serviceKey);
        log.debug("Marked service as registered: {}", serviceKey);
    }
    
    /**
     * 获取已注册的服务列表
     */
    public Set<String> getRegisteredServices() {
        return new HashSet<>(registeredServices);
    }
    
    /**
     * 清除注册缓存（用于重新注册）
     */
    public void clearRegistrationCache() {
        registeredServices.clear();
        pendingRegistrations.clear();
        log.info("Cleared registration cache");
    }
    
    /**
     * 检查服务是否已注册
     */
    private boolean isServiceRegistered(String serviceKey) {
        return registeredServices.contains(serviceKey);
    }
    
    /**
     * 检查并更新服务（当服务已注册且Provider发生变化时）
     */
    private void checkAndUpdateService(ProviderInfo providerInfo) {
        try {
            String serviceKey = buildServiceKey(providerInfo);
            List<ProviderInfo> sameServiceProviders = getSameServiceProviders(providerInfo);
            
            if (sameServiceProviders.isEmpty()) {
                log.warn("No providers found for registered service: {}, skipping update", serviceKey);
                return;
            }
            
            // 去重
            sameServiceProviders = deduplicateProviders(sameServiceProviders);
            
            // 更新注册信息
            String version = providerInfo.getVersion() != null ? providerInfo.getVersion() : "1.0.0";
            nacosMcpRegistrationService.registerDubboServiceAsMcp(
                    providerInfo.getInterfaceName(),
                    version,
                    sameServiceProviders
            );
            
            log.info("✅ Updated registered service: {}:{}", 
                    providerInfo.getInterfaceName(), version);
            
        } catch (Exception e) {
            log.error("Failed to update service: {}", providerInfo.getInterfaceName(), e);
        }
    }
}

