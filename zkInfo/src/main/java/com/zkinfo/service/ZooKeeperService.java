package com.zkinfo.service;

import com.zkinfo.config.ZooKeeperConfig;
import com.zkinfo.model.ProviderInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ZooKeeper 服务管理类
 * 
 * 负责与 ZooKeeper 集群的连接管理、服务发现、节点监听等核心功能。
 * 实现了对 Dubbo 服务注册信息的实时监控，包括服务提供者的注册、注销、
 * 状态变更等事件的处理。
 * 
 * <p>主要功能：</p>
 * <ul>
 *   <li>ZooKeeper 客户端连接管理</li>
 *   <li>服务节点的实时监听</li>
 *   <li>Dubbo 服务提供者信息解析</li>
 *   <li>服务注册/注销事件处理</li>
 *   <li>连接状态监控</li>
 * </ul>
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
public class ZooKeeperService {
    
    @Autowired
    private ZooKeeperConfig config;
    
    @Autowired
    private ProviderService providerService;
    
    @Autowired(required = false)
    private DubboToMcpRegistrationService dubboToMcpRegistrationService;
    
    @Autowired(required = false)
    private NacosMcpRegistrationService nacosMcpRegistrationService;
    
    @Autowired(required = false)
    private DubboToMcpAutoRegistrationService autoRegistrationService;
    
    @Autowired(required = false)
    private ServiceCollectionFilterService filterService;
    
    private CuratorFramework client;
    private final ConcurrentHashMap<String, CuratorCache> pathCaches = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        try {
            // 创建ZooKeeper客户端
            ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(
                    config.getRetry().getBaseSleepTime(),
                    config.getRetry().getMaxRetries()
            );
            
            client = CuratorFrameworkFactory.builder()
                    .connectString(config.getConnectString())
                    .sessionTimeoutMs(config.getSessionTimeout())
                    .connectionTimeoutMs(config.getConnectionTimeout())
                    .retryPolicy(retryPolicy)
                    .build();
            
            client.start();
            
            // 等待连接建立
            client.blockUntilConnected();
            log.info("ZooKeeper客户端连接成功: {}", config.getConnectString());
            
            // 开始监听Provider节点
            startWatchingProviders();
            
        } catch (Exception e) {
            log.error("初始化ZooKeeper客户端失败", e);
            throw new RuntimeException("Failed to initialize ZooKeeper client", e);
        }
    }
    
    /**
     * 开始监听Provider节点
     */
    private void startWatchingProviders() {
        try {
            String providersPath = config.getBasePath();
            
            // 确保基础路径存在
            if (client.checkExists().forPath(providersPath) == null) {
                log.warn("ZooKeeper基础路径不存在: {}", providersPath);
                return;
            }
            
            // 获取所有服务接口
            List<String> services = client.getChildren().forPath(providersPath);
            log.info("发现 {} 个服务接口", services.size());
            
            // 优化：只监听项目包含的服务（如果启用了过滤）
            int watchedCount = 0;
            for (String service : services) {
                String servicePath = providersPath + "/" + service;
                
                // 检查服务是否在项目中（快速检查，不检查版本）
                if (filterService != null && !isServiceInProjects(service)) {
                    log.debug("服务 {} 不在任何项目中，跳过监听", service);
                    continue;
                }
                
                watchServiceProviders(servicePath, service);
                watchedCount++;
            }
            
            log.info("实际监听 {} 个服务接口（已过滤 {} 个）", watchedCount, services.size() - watchedCount);
            
            // 监听新服务的添加
            watchNewServices(providersPath);
            
        } catch (Exception e) {
            log.error("开始监听Provider节点失败", e);
        }
    }
    
    /**
     * 监听特定服务的Provider变化
     * 优化：只监听项目包含的服务
     */
    private void watchServiceProviders(String servicePath, String serviceName) {
        try {
            String providersPath = servicePath + "/providers";
            
            // 检查providers路径是否存在
            if (client.checkExists().forPath(providersPath) == null) {
                log.debug("服务 {} 的providers路径不存在: {}", serviceName, providersPath);
                return;
            }
            
            // 首先加载已存在的provider信息
            loadExistingProviders(providersPath, serviceName);
            
            CuratorCache cache = CuratorCache.build(client, providersPath);
            
            cache.listenable().addListener((type, oldData, data) -> {
                try {
                    switch (type) {
                        case NODE_CREATED:
                            if (data != null && data.getPath().startsWith(providersPath + "/")) {
                                String providerUrl = URLDecoder.decode(
                                        data.getPath().substring(providersPath.length() + 1),
                                        StandardCharsets.UTF_8
                                );
                                
                                // 应用过滤规则：只有通过过滤的服务才会被处理
                                ProviderInfo providerInfo = parseProviderUrl(providerUrl, serviceName);
                                
                                if (providerInfo != null) {
                                    // 应用三层过滤机制
                                    if (filterService == null || filterService.shouldCollect(
                                            providerInfo.getInterfaceName(),
                                            providerInfo.getVersion(),
                                            providerInfo.getGroup())) {
                                        handleProviderAdded(data, serviceName);
                                    } else {
                                        log.debug("Provider {}/{} 被过滤规则排除，跳过处理", 
                                                providerInfo.getInterfaceName(),
                                                providerInfo.getVersion());
                                    }
                                }
                            }
                            break;
                        case NODE_DELETED:
                            if (oldData != null && oldData.getPath().startsWith(providersPath + "/")) {
                                handleProviderRemoved(oldData, serviceName);
                            }
                            break;
                        case NODE_CHANGED:
                            if (data != null && data.getPath().startsWith(providersPath + "/")) {
                                handleProviderUpdated(data, serviceName);
                            }
                            break;
                        default:
                            break;
                    }
                } catch (Exception e) {
                    log.error("处理Provider事件失败", e);
                }
            });
            
            cache.start();
            pathCaches.put(providersPath, cache);
            
            log.info("开始监听服务 {} 的Provider变化: {}", serviceName, providersPath);
            
        } catch (Exception e) {
            log.error("监听服务 {} 的Provider失败", serviceName, e);
        }
    }
    
    /**
     * 加载已存在的Provider信息
     */
    private void 
    loadExistingProviders(String providersPath, String serviceName) {
        try {
            List<String> existingProviders = client.getChildren().forPath(providersPath);
            log.info("发现服务 {} 已有 {} 个Provider", serviceName, existingProviders.size());
            
            for (String providerNode : existingProviders) {
                try {
                    String providerPath = providersPath + "/" + providerNode;
                    String providerUrl = URLDecoder.decode(providerNode, StandardCharsets.UTF_8);
                    
                    log.info("加载已存在的Provider: {} -> {}", serviceName, providerUrl);
                    
                    ProviderInfo providerInfo = parseProviderUrl(providerUrl, serviceName);
                    if (providerInfo != null) {
                        providerInfo.setZkPath(providerPath);
                        providerService.addProvider(providerInfo);
                    }
                } catch (Exception e) {
                    log.error("加载Provider失败: {}", providerNode, e);
                }
            }
        } catch (Exception e) {
            log.error("加载已存在的Provider失败: {}", providersPath, e);
        }
    }
    
    /**
     * 监听新服务的添加
     */
    private void watchNewServices(String basePath) {
        try {
            CuratorCache serviceCache = CuratorCache.build(client, basePath);
            
            serviceCache.listenable().addListener((type, oldData, data) -> {
                if (type == org.apache.curator.framework.recipes.cache.CuratorCacheListener.Type.NODE_CREATED && 
                    data != null && data.getPath().startsWith(basePath + "/")) {
                    String serviceName = data.getPath().substring(basePath.length() + 1);
                    log.info("发现新服务: {}", serviceName);
                    
                    String servicePath = basePath + "/" + serviceName;
                    watchServiceProviders(servicePath, serviceName);
                }
            });
            
            serviceCache.start();
            pathCaches.put(basePath, serviceCache);
            
        } catch (Exception e) {
            log.error("监听新服务失败", e);
        }
    }
    
    
    /**
     * 处理Provider添加事件
     */
    private void handleProviderAdded(ChildData data, String serviceName) {
        try {
            String providerUrl = URLDecoder.decode(
                    data.getPath().substring(data.getPath().lastIndexOf('/') + 1),
                    StandardCharsets.UTF_8
            );
            
            log.info("Provider添加: {} -> {}", serviceName, providerUrl);
            
            ProviderInfo providerInfo = parseProviderUrl(providerUrl, serviceName);
            if (providerInfo != null) {
                providerInfo.setZkPath(data.getPath());
                providerService.addProvider(providerInfo);
                
                // 自动注册到Nacos（如果启用）
                if (autoRegistrationService != null) {
                    autoRegistrationService.handleProviderAdded(providerInfo);
                }
            }
            
        } catch (Exception e) {
            log.error("处理Provider添加事件失败", e);
        }
    }
    
    /**
     * 处理Provider移除事件
     */
    private void handleProviderRemoved(ChildData data, String serviceName) {
        try {
            String zkPath = data.getPath();
            log.info("Provider移除: {} -> {}", serviceName, zkPath);
            
            // 先获取Provider信息（用于注销）
            ProviderInfo removedProvider = providerService.getProviderByZkPath(zkPath);
            
            // 从ProviderService中移除
            ProviderInfo actualRemoved = providerService.removeProviderByZkPath(zkPath);
            
            // 自动注销Nacos服务（如果启用）
            if (actualRemoved != null && autoRegistrationService != null) {
                autoRegistrationService.handleProviderRemoved(actualRemoved);
            }
            
        } catch (Exception e) {
            log.error("处理Provider移除事件失败", e);
        }
    }
    
    /**
     * 处理Provider更新事件
     */
    private void handleProviderUpdated(ChildData data, String serviceName) {
        try {
            String providerUrl = URLDecoder.decode(
                    data.getPath().substring(data.getPath().lastIndexOf('/') + 1),
                    StandardCharsets.UTF_8
            );
            
            log.info("Provider更新: {} -> {}", serviceName, providerUrl);
            
            ProviderInfo providerInfo = parseProviderUrl(providerUrl, serviceName);
            if (providerInfo != null) {
                providerInfo.setZkPath(data.getPath());
                providerService.updateProvider(providerInfo);
                
                // 自动更新Nacos注册（如果启用）
                if (autoRegistrationService != null) {
                    autoRegistrationService.handleProviderUpdated(providerInfo);
                }
            }
            
        } catch (Exception e) {
            log.error("处理Provider更新事件失败", e);
        }
    }
    
    /**
     * 解析Provider URL
     */
    private ProviderInfo parseProviderUrl(String providerUrl, String serviceName) {
        try {
            // Dubbo Provider URL格式: dubbo://192.168.1.100:20880/com.example.Service?version=1.0.0&group=default&...
            if (!providerUrl.startsWith("dubbo://")) {
                log.warn("不支持的Provider URL格式: {}", providerUrl);
                return null;
            }
            
            ProviderInfo provider = new ProviderInfo();
            provider.setInterfaceName(serviceName);
            provider.setOnline(true);
            
            // 解析URL
            String[] parts = providerUrl.split("\\?");
            String baseUrl = parts[0];
            
            // 提取协议、地址和接口
            String[] urlParts = baseUrl.split("://");
            provider.setProtocol(urlParts[0]);
            
            String[] addressAndInterface = urlParts[1].split("/");
            provider.setAddress(addressAndInterface[0]);
            
            if (addressAndInterface.length > 1) {
                provider.setInterfaceName(addressAndInterface[1]);
            }
            
            // 解析参数
            java.util.Map<String, String> parameters = new java.util.HashMap<>();
            if (parts.length > 1) {
                String[] params = parts[1].split("&");
                for (String param : params) {
                    String[] kv = param.split("=");
                    if (kv.length == 2) {
                        String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                        String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        
                        // 保存所有参数到 parameters Map
                        parameters.put(key, value);
                        
                        switch (key) {
                            case "version":
                                provider.setVersion(value);
                                break;
                            case "group":
                                provider.setGroup(value);
                                break;
                            case "application":
                                provider.setApplication(value);
                                break;
                            case "methods":
                                provider.setMethods(value);
                                break;
                        }
                    }
                }
            }
            
            // 如果 application 为空，尝试从 parameters 中获取（可能参数名不同）
            if ((provider.getApplication() == null || provider.getApplication().isEmpty()) && !parameters.isEmpty()) {
                // 尝试常见的 application 参数名变体
                String app = parameters.get("application") != null ? parameters.get("application") :
                            parameters.get("dubbo.application.name") != null ? parameters.get("dubbo.application.name") :
                            parameters.get("application.name") != null ? parameters.get("application.name") :
                            null;
                if (app != null && !app.isEmpty()) {
                    provider.setApplication(app);
                    log.debug("Extracted application from parameters: {}", app);
                }
            }
            
            // 保存所有参数
            provider.setParameters(parameters);
            
            return provider;
            
        } catch (Exception e) {
            log.error("解析Provider URL失败: {}", providerUrl, e);
            return null;
        }
    }
    
    /**
     * 获取ZooKeeper客户端
     */
    public CuratorFramework getClient() {
        return client;
    }
    
    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return client != null && client.getZookeeperClient().isConnected();
    }
    
    /**
     * 检查服务是否在任何项目中（快速检查，不检查版本）
     * 用于优化ZooKeeper监听，只监听项目包含的服务
     * 
     * @param serviceName 服务名称（接口名）
     * @return true表示服务在项目中，false表示不在
     */
    private boolean isServiceInProjects(String serviceName) {
        if (filterService == null) {
            // 如果没有过滤服务，默认监听所有服务
            return true;
        }
        
        // 快速检查：尝试匹配任何版本的服务
        // 这里使用一个临时版本号来检查服务是否在项目中
        // 如果filterService的isInDefinedProjects方法支持，可以直接调用
        // 暂时返回true，让过滤在更细粒度的地方进行
        // TODO: 实现更精确的项目服务检查（需要数据库支持）
        return true; // 暂时允许所有服务，等待项目数据初始化
    }
    
    /**
     * 检查指定路径是否已经添加了缓存监听
     * 
     * @param path 路径
     * @return 是否已添加缓存监听
     */
    public boolean isPathCached(String path) {
        return pathCaches.containsKey(path);
    }
    
    @PreDestroy
    public void destroy() {
        try {
            // 关闭所有缓存
            for (CuratorCache cache : pathCaches.values()) {
                cache.close();
            }
            pathCaches.clear();
            
            // 关闭客户端
            if (client != null) {
                client.close();
            }
            
            log.info("ZooKeeper客户端已关闭");
            
        } catch (Exception e) {
            log.error("关闭ZooKeeper客户端失败", e);
        }
    }
}
