package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ZooKeeper 启动初始化服务
 * 
 * 在应用启动完成后，批量从 ZooKeeper 拉取所有 Provider 信息并批量入库
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
public class ZooKeeperBootstrapService {
    
    private final ZooKeeperService zooKeeperService;
    private final DubboServiceDbService dubboServiceDbService;
    private final ProviderInfoDbService providerInfoDbService;
    private final DubboServiceMethodService dubboServiceMethodService;
    private final ProviderService providerService;
    private final InterfaceWhitelistService interfaceWhitelistService;
    
    public ZooKeeperBootstrapService(
            ZooKeeperService zooKeeperService,
            DubboServiceDbService dubboServiceDbService,
            ProviderInfoDbService providerInfoDbService,
            DubboServiceMethodService dubboServiceMethodService,
            ProviderService providerService,
            InterfaceWhitelistService interfaceWhitelistService) {
        this.zooKeeperService = zooKeeperService;
        this.dubboServiceDbService = dubboServiceDbService;
        this.providerInfoDbService = providerInfoDbService;
        this.dubboServiceMethodService = dubboServiceMethodService;
        this.providerService = providerService;
        this.interfaceWhitelistService = interfaceWhitelistService;
    }
    
    /**
     * 应用启动完成后，批量拉取 ZooKeeper 数据并入库
     */
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapZooKeeperData() {
        log.info("🚀 开始批量拉取 ZooKeeper 数据并入库...");
        
        // 异步执行，避免阻塞启动
        CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // 1. 批量拉取所有 Provider 信息
                List<ProviderInfo> allProviders = loadAllProvidersFromZooKeeper();
                log.info("从 ZooKeeper 拉取到 {} 个 Provider", allProviders.size());
                
                if (allProviders.isEmpty()) {
                    log.warn("未从 ZooKeeper 拉取到任何 Provider 信息");
                    return;
                }
                
                // 2. 按接口分组，逐个接口完整处理（确保每个接口的数据完整性）
                persistProvidersByInterface(allProviders);
                
                // 3. 启动监听（只监听已审批的服务）
                zooKeeperService.startWatchingProviders();
                
                // 4. 将已审批的服务添加到 ProviderService
                addApprovedProvidersToService(allProviders);
                
                long duration = System.currentTimeMillis() - startTime;
                log.info("✅ ZooKeeper 数据初始化完成，总耗时: {}ms", duration);
                
            } catch (Exception e) {
                log.error("❌ ZooKeeper 数据初始化失败", e);
            }
        });
    }
    
    /**
     * 从 ZooKeeper 批量拉取所有 Provider 信息（并行优化版本）
     * 
     * 优化点：
     * 1. 使用并行流并行处理多个服务
     * 2. 减少不必要的 checkExists 调用（直接尝试 getChildren，失败则跳过）
     * 3. 使用线程安全的集合收集结果
     * 4. 支持白名单过滤：只拉取匹配白名单的服务
     */
    private List<ProviderInfo> loadAllProvidersFromZooKeeper() {
        long startTime = System.currentTimeMillis();
        
        try {
            String basePath = zooKeeperService.getConfig().getBasePath();
            CuratorFramework client = zooKeeperService.getClient();
            
            // 获取所有服务接口
            List<String> allServices = client.getChildren().forPath(basePath);
            log.info("发现 {} 个服务接口，开始并行拉取 Provider 信息", allServices.size());
            
            // 如果配置了白名单，进行过滤
            final List<String> services;
            if (interfaceWhitelistService != null && interfaceWhitelistService.isWhitelistConfigured()) {
                List<String> whitelistPrefixes = interfaceWhitelistService.getWhitelistPrefixes();
                log.info("应用白名单过滤，白名单前缀: {}", whitelistPrefixes);
                
                // 过滤出匹配白名单的服务（左匹配）
                List<String> filteredServices = allServices.stream()
                        .filter(service -> {
                            // 检查服务名是否匹配任何一个白名单前缀
                            for (String prefix : whitelistPrefixes) {
                                if (service.startsWith(prefix)) {
                                    log.debug("✅ 服务 {} 匹配白名单前缀: {}", service, prefix);
                                    return true;
                                }
                            }
                            log.debug("❌ 服务 {} 不匹配白名单，跳过", service);
                            return false;
                        })
                        .collect(Collectors.toList());
                
                log.info("白名单过滤后，剩余 {} 个服务接口（原始: {}）", filteredServices.size(), allServices.size());
                services = filteredServices;
            } else {
                services = allServices;
            }
            
            if (services.isEmpty()) {
                return Collections.emptyList();
            }
            
            // 使用并行流并行处理多个服务
            // 设置并行度（根据 CPU 核心数和服务数量动态调整）
            int parallelism = Math.min(Math.max(services.size() / 10, 4), 
                                       Runtime.getRuntime().availableProcessors() * 2);
            
            ForkJoinPool customThreadPool = new ForkJoinPool(parallelism);
            
            try {
                List<ProviderInfo> allProviders = customThreadPool.submit(() -> 
                    services.parallelStream()
                        .flatMap(service -> {
                            try {
                                return loadProvidersForService(client, basePath, service).stream();
                            } catch (Exception e) {
                                log.error("拉取服务 {} 的 Provider 失败", service, e);
                                return java.util.stream.Stream.empty();
                            }
                        })
                        .collect(Collectors.toList())
                ).get(5, TimeUnit.MINUTES); // 设置超时时间 5 分钟
                
                long duration = System.currentTimeMillis() - startTime;
                log.info("✅ 并行拉取完成: {} 个服务接口，共 {} 个 Provider，耗时: {}ms", 
                        services.size(), allProviders.size(), duration);
                
                return allProviders;
            } finally {
                customThreadPool.shutdown();
            }
            
        } catch (Exception e) {
            log.error("批量拉取 ZooKeeper Provider 信息失败", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 加载单个服务的所有 Provider 信息
     * 
     * @param client ZooKeeper 客户端
     * @param basePath 基础路径
     * @param service 服务名称
     * @return Provider 信息列表
     */
    private List<ProviderInfo> loadProvidersForService(CuratorFramework client, String basePath, String service) {
        List<ProviderInfo> providers = new ArrayList<>();
        
        try {
            String providersPath = basePath + "/" + service + "/providers";
            
            // 直接尝试获取子节点，如果路径不存在会抛出异常，捕获后跳过
            // 这样避免了额外的 checkExists 调用，减少网络往返
            List<String> providerNodes;
            try {
                providerNodes = client.getChildren().forPath(providersPath);
            } catch (Exception e) {
                // 路径不存在或其他错误，跳过
                log.debug("服务 {} 的 providers 路径不存在或访问失败: {}", service, providersPath);
                return providers;
            }
            
            if (providerNodes.isEmpty()) {
                return providers;
            }
            
            log.debug("服务 {} 有 {} 个 Provider", service, providerNodes.size());
            
            // 并行解析 Provider（如果 Provider 数量较多）
            if (providerNodes.size() > 10) {
                providers = providerNodes.parallelStream()
                    .map(providerNode -> parseProviderNode(client, providersPath, providerNode, service))
                    .filter(provider -> provider != null)
                    .collect(Collectors.toList());
            } else {
                // Provider 数量少时，串行处理即可
                for (String providerNode : providerNodes) {
                    ProviderInfo provider = parseProviderNode(client, providersPath, providerNode, service);
                    if (provider != null) {
                        providers.add(provider);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("加载服务 {} 的 Provider 失败", service, e);
        }
        
        return providers;
    }
    
    /**
     * 解析单个 Provider 节点
     * 
     * @param client ZooKeeper 客户端
     * @param providersPath providers 路径
     * @param providerNode Provider 节点名称
     * @param service 服务名称
     * @return ProviderInfo 对象，解析失败返回 null
     */
    private ProviderInfo parseProviderNode(CuratorFramework client, String providersPath, 
                                          String providerNode, String service) {
        try {
            String providerPath = providersPath + "/" + providerNode;
            String providerUrl = URLDecoder.decode(providerNode, StandardCharsets.UTF_8);
            
            ProviderInfo providerInfo = zooKeeperService.parseProviderUrl(providerUrl, service);
            if (providerInfo != null) {
                providerInfo.setZkPath(providerPath);
                return providerInfo;
            }
        } catch (Exception e) {
            log.error("解析 Provider 失败: {}", providerNode, e);
        }
        
        return null;
    }
    
    /**
     * 将已审批的服务添加到 ProviderService
     */
    private void addApprovedProvidersToService(List<ProviderInfo> providers) {
        try {
            // 获取所有已审批的服务
            List<com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity> approvedServices = 
                dubboServiceDbService.findApprovedServices();
            java.util.Set<String> approvedServiceKeys = new java.util.HashSet<>();
            for (com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity service : approvedServices) {
                approvedServiceKeys.add(buildServiceKey(service));
            }
            
            int addedCount = 0;
            for (ProviderInfo provider : providers) {
                String serviceKey = buildServiceKey(provider);
                if (approvedServiceKeys.contains(serviceKey)) {
                    providerService.addProvider(provider);
                    addedCount++;
                }
            }
            
            log.info("将 {} 个已审批的 Provider 添加到服务监控", addedCount);
        } catch (Exception e) {
            log.error("添加已审批的 Provider 到服务监控失败", e);
        }
    }
    
    /**
     * 按接口分组，逐个接口完整处理 Provider 信息
     * 确保每个接口的 service、node、provider、method、parameter 都完整落库
     * 
     * @param allProviders 所有 Provider 信息列表
     */
    private void persistProvidersByInterface(List<ProviderInfo> allProviders) {
        if (allProviders == null || allProviders.isEmpty()) {
            return;
        }
        
        long startTime = System.currentTimeMillis();
        int totalCount = allProviders.size();
        int successCount = 0;
        int failureCount = 0;
        int processedInterfaceCount = 0;
        
        log.info("开始按接口分组持久化 {} 条Provider信息", totalCount);
        
        // 1. 按接口分组
        Map<String, List<ProviderInfo>> serviceGroupMap = allProviders.stream()
                .collect(Collectors.groupingBy(this::buildServiceKey));
        
        int totalInterfaces = serviceGroupMap.size();
        log.info("共 {} 个不同的接口需要处理", totalInterfaces);
        
        // 2. 按接口逐个处理，确保每个接口的数据完整性
        // 重要：每个接口的处理是独立的，即使某个接口失败也不影响其他接口
        for (Map.Entry<String, List<ProviderInfo>> entry : serviceGroupMap.entrySet()) {
            String serviceKey = entry.getKey();
            List<ProviderInfo> serviceProviders = entry.getValue();
            int interfaceSuccessCount = 0;
            int interfaceFailureCount = 0;
            
            try {
                processedInterfaceCount++;
                log.info("[{}/{}] 开始处理接口: {} ({} 个Provider)", 
                        processedInterfaceCount, totalInterfaces, serviceKey, serviceProviders.size());
                
                if (serviceProviders.isEmpty()) {
                    log.warn("接口 {} 的Provider列表为空，跳过", serviceKey);
                    continue;
                }
                
                ProviderInfo firstProvider = serviceProviders.get(0);
                
                // 2.1 处理服务信息（service）
                com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity serviceEntity = 
                    dubboServiceDbService.saveOrUpdateService(firstProvider);
                
                if (serviceEntity == null || serviceEntity.getId() == null) {
                    log.error("服务插入失败，无法获取ID: {}", serviceKey);
                    interfaceFailureCount += serviceProviders.size();
                    failureCount += serviceProviders.size();
                    continue;
                }
                
                log.debug("服务处理完成: serviceId={}, interfaceName={}", 
                        serviceEntity.getId(), serviceEntity.getInterfaceName());
                
                // 2.2 处理服务方法信息（service method 和 parameter）
                try {
                    dubboServiceMethodService.saveOrUpdateServiceMethods(firstProvider, serviceEntity.getId());
                    log.debug("服务方法处理完成: serviceId={}, interfaceName={}", 
                            serviceEntity.getId(), serviceEntity.getInterfaceName());
                } catch (Exception e) {
                    log.error("保存服务方法信息失败: serviceId={}, interfaceName={}, error={}", 
                            serviceEntity.getId(), serviceEntity.getInterfaceName(), e.getMessage(), e);
                    // 不增加失败计数，因为方法信息不是必需的，继续处理Provider
                }
                
                // 2.3 处理每个 Provider（node 和 provider）
                for (ProviderInfo providerInfo : serviceProviders) {
                    try {
                        // 注意：saveOrUpdateProvider 内部会处理 service 和 node
                        // 但由于我们已经处理了 service，saveOrUpdateService 内部有 ON DUPLICATE KEY UPDATE，不会重复插入
                        providerInfoDbService.saveOrUpdateProvider(providerInfo);
                        
                        interfaceSuccessCount++;
                        successCount++;
                        log.debug("Provider处理完成: serviceId={}, address={}", 
                                serviceEntity.getId(), providerInfo.getAddress());
                        
                    } catch (Exception e) {
                        interfaceFailureCount++;
                        failureCount++;
                        log.error("处理Provider失败: serviceKey={}, address={}, error={}", 
                                serviceKey, providerInfo.getAddress(), e.getMessage(), e);
                        // 继续处理下一个Provider，不中断
                    }
                }
                
                log.info("[{}/{}] 接口处理完成: {} (serviceId={}, providerCount={}, 成功={}, 失败={})", 
                        processedInterfaceCount, totalInterfaces, serviceKey, serviceEntity.getId(), 
                        serviceProviders.size(), interfaceSuccessCount, interfaceFailureCount);
                
            } catch (Exception e) {
                // 捕获接口级别的异常，记录日志但继续处理下一个接口
                interfaceFailureCount += serviceProviders.size();
                failureCount += serviceProviders.size();
                log.error("[{}/{}] 处理接口失败: {}, error={}, 将继续处理下一个接口", 
                        processedInterfaceCount, totalInterfaces, serviceKey, e.getMessage(), e);
                // 不抛出异常，继续处理下一个接口
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("✅ 按接口分组持久化完成: 接口总数={}, 已处理={}, Provider总数={}, 成功={}, 失败={}, 耗时={}ms, 平均={}ms/条",
                totalInterfaces, processedInterfaceCount, totalCount, successCount, failureCount, duration,
                totalCount > 0 ? duration / totalCount : 0);
    }
    
    /**
     * 构建服务唯一标识
     */
    private String buildServiceKey(ProviderInfo providerInfo) {
        return providerInfo.getInterfaceName() + ":" +
                (providerInfo.getProtocol() != null ? providerInfo.getProtocol() : "") + ":" +
                (providerInfo.getVersion() != null ? providerInfo.getVersion() : "") + ":" +
                (providerInfo.getGroup() != null ? providerInfo.getGroup() : "") + ":" +
                (providerInfo.getApplication() != null ? providerInfo.getApplication() : "");
    }
    
    private String buildServiceKey(com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity service) {
        return service.getInterfaceName() + ":" +
                (service.getProtocol() != null ? service.getProtocol() : "") + ":" +
                (service.getVersion() != null ? service.getVersion() : "") + ":" +
                (service.getGroup() != null ? service.getGroup() : "") + ":" +
                (service.getApplication() != null ? service.getApplication() : "");
    }
}

