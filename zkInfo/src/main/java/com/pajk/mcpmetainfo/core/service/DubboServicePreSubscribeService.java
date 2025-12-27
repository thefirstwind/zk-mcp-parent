package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.rpc.service.GenericService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Dubbo 服务预订阅服务
 * 
 * 在应用启动完成后，预订阅所有符合白名单的服务，提前建立连接
 * 这样可以：
 * 1. 提前发现服务可用性问题
 * 2. 减少首次调用的延迟
 * 3. 确保订阅符合白名单逻辑
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-12-27
 */
@Slf4j
@Service
public class DubboServicePreSubscribeService {
    
    private final McpExecutorService mcpExecutorService;
    private final DubboServiceDbService dubboServiceDbService;
    private final InterfaceWhitelistService interfaceWhitelistService;
    
    public DubboServicePreSubscribeService(
            McpExecutorService mcpExecutorService,
            DubboServiceDbService dubboServiceDbService,
            InterfaceWhitelistService interfaceWhitelistService) {
        this.mcpExecutorService = mcpExecutorService;
        this.dubboServiceDbService = dubboServiceDbService;
        this.interfaceWhitelistService = interfaceWhitelistService;
    }
    
    /**
     * 应用启动完成后，预订阅所有符合白名单的服务
     */
    @EventListener(ApplicationReadyEvent.class)
    public void preSubscribeServicesOnStartup() {
        log.info("🚀 开始预订阅符合白名单的 Dubbo 服务...");
        
        // 异步执行，避免阻塞启动
        CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // 1. 获取所有已审批的服务（这些服务已经在数据库中）
                List<com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity> approvedServices = 
                    dubboServiceDbService.findApprovedServices();
                
                if (approvedServices == null || approvedServices.isEmpty()) {
                    log.warn("未找到已审批的服务，跳过预订阅");
                    return;
                }
                
                log.info("发现 {} 个已审批的服务，开始应用白名单过滤", approvedServices.size());
                
                // 2. 应用白名单过滤
                List<com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity> whitelistedServices;
                if (interfaceWhitelistService != null && interfaceWhitelistService.isWhitelistConfigured()) {
                    whitelistedServices = approvedServices.stream()
                        .filter(service -> {
                            String interfaceName = service.getInterfaceName();
                            boolean allowed = interfaceWhitelistService.isAllowed(interfaceName);
                            if (!allowed) {
                                log.debug("❌ 服务 {} 不在白名单中，跳过预订阅", interfaceName);
                            }
                            return allowed;
                        })
                        .collect(Collectors.toList());
                    
                    log.info("白名单过滤后，剩余 {} 个服务需要预订阅（原始: {}）", 
                            whitelistedServices.size(), approvedServices.size());
                } else {
                    log.info("白名单未配置，预订阅所有已审批的服务");
                    whitelistedServices = approvedServices;
                }
                
                if (whitelistedServices.isEmpty()) {
                    log.warn("白名单过滤后没有服务需要预订阅");
                    return;
                }
                
                // 3. 对每个服务进行预订阅
                int successCount = 0;
                int failureCount = 0;
                
                for (com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity service : whitelistedServices) {
                    try {
                        preSubscribeService(service);
                        successCount++;
                    } catch (Exception e) {
                        failureCount++;
                        log.error("预订阅服务失败: {}, error: {}", 
                                service.getInterfaceName(), e.getMessage(), e);
                        // 继续处理下一个服务，不中断
                    }
                }
                
                long duration = System.currentTimeMillis() - startTime;
                log.info("✅ 预订阅完成: 总数={}, 成功={}, 失败={}, 耗时={}ms", 
                        whitelistedServices.size(), successCount, failureCount, duration);
                
            } catch (Exception e) {
                log.error("❌ 预订阅服务失败", e);
            }
        });
    }
    
    /**
     * 预订阅单个服务
     * 通过创建 ReferenceConfig 并调用 get() 来触发订阅
     * 
     * @param service 服务实体
     */
    private void preSubscribeService(com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity service) {
        String interfaceName = service.getInterfaceName();
        log.info("🔍 预订阅服务: {}", interfaceName);
        
        try {
            // 1. 获取该服务的 Provider 列表
            List<ProviderInfo> providers = dubboServiceDbService.getProvidersByServiceId(service.getId());
            
            if (providers == null || providers.isEmpty()) {
                log.warn("服务 {} 没有可用的 Provider，跳过预订阅", interfaceName);
                return;
            }
            
            // 2. 选择第一个可用的 Provider（优先选择在线的）
            ProviderInfo provider = providers.stream()
                .filter(ProviderInfo::isOnline)
                .findFirst()
                .orElse(providers.get(0));
            
            log.info("选择 Provider: {}:{}:{} at {}:{}", 
                    provider.getInterfaceName(),
                    provider.getVersion(),
                    provider.getGroup(),
                    provider.getAddress(),
                    provider.getPort());
            
            // 3. 调用预订阅方法，这会创建 ReferenceConfig 并触发订阅
            boolean success = mcpExecutorService.preSubscribeService(interfaceName, provider);
            
            if (success) {
                log.info("✅ 成功预订阅服务: {}", interfaceName);
            } else {
                log.warn("⚠️ 预订阅服务失败: {}", interfaceName);
            }
            
        } catch (Exception e) {
            log.error("预订阅服务失败: {}, error: {}", interfaceName, e.getMessage(), e);
            throw e;
        }
    }
}

