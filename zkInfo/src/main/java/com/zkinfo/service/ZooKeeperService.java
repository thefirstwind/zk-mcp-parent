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
import java.util.List;
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
            
            for (String service : services) {
                String servicePath = providersPath + "/" + service;
                watchServiceProviders(servicePath, service);
            }
            
            // 监听新服务的添加
            watchNewServices(providersPath);
            
        } catch (Exception e) {
            log.error("开始监听Provider节点失败", e);
        }
    }
    
    /**
     * 监听特定服务的Provider变化
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
                                handleProviderAdded(data, serviceName);
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
            
            providerService.removeProviderByZkPath(zkPath);
            
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
            if (parts.length > 1) {
                String[] params = parts[1].split("&");
                for (String param : params) {
                    String[] kv = param.split("=");
                    if (kv.length == 2) {
                        String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                        String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        
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
