package com.zkinfo.service;

import com.zkinfo.config.ZooKeeperConfig;
import com.zkinfo.model.DubboServiceEntity;
import com.zkinfo.model.DubboServiceNodeEntity;
import com.zkinfo.model.ProviderInfo;
import com.zkinfo.model.ProviderInfoEntity;
import com.zkinfo.service.DubboServiceDbService;
import com.zkinfo.service.ProviderInfoDbService;
import lombok.extern.slf4j.Slf4j;
import java.util.Optional;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    
    @Autowired
    private ProviderInfoDbService providerInfoDbService;
    
    @Autowired
    private DubboServiceDbService dubboServiceDbService;
    
    // 移除了filterApprovedOnly配置项，现在总是根据数据库中的审批状态来判断是否watch
    
    private CuratorFramework client;
    private final ConcurrentHashMap<String, TreeCache> pathCaches = new ConcurrentHashMap<>();
    
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
            
            TreeCache cache = new TreeCache(client, providersPath);
            
            cache.getListenable().addListener((client, event) -> {
                try {
                    switch (event.getType()) {
                        case NODE_ADDED:
                            if (event.getData() != null && event.getData().getPath().startsWith(providersPath + "/")) {
                                handleProviderAdded(event.getData(), serviceName);
                            }
                            break;
                        case NODE_REMOVED:
                            if (event.getData() != null && event.getData().getPath().startsWith(providersPath + "/")) {
                                handleProviderRemoved(event.getData(), serviceName);
                            }
                            break;
                        case NODE_UPDATED:
                            if (event.getData() != null && event.getData().getPath().startsWith(providersPath + "/")) {
                                handleProviderUpdated(event.getData(), serviceName);
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
                        
                        // 所有dubbo service都得入库（新表结构）
                        try {
                            dubboServiceDbService.saveOrUpdateServiceWithNode(providerInfo);
                            log.debug("成功同步Provider到新数据库结构: {}", providerPath);
                        } catch (Exception e) {
                            log.error("同步Provider到新数据库结构失败: {}", providerPath, e);
                        }

                        // 检查数据库中的审批状态，只有已审批的Provider才会被添加watch
                        Optional<ProviderInfoEntity> approvedProvider = 
                            providerInfoDbService.findByZkPathAndApprovalStatus(providerPath, ProviderInfoEntity.ApprovalStatus.APPROVED);
                        if (approvedProvider.isPresent()) {
                            providerService.addProvider(providerInfo);
                            log.debug("添加已审批Provider到服务监控: {}", providerPath);
                        } else {
                            log.debug("跳过未审批Provider的服务监控: {}", providerPath);
                        }
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
            TreeCache serviceCache = new TreeCache(client, basePath);
            
            serviceCache.getListenable().addListener((client, event) -> {
                if (event.getType() == TreeCacheEvent.Type.NODE_ADDED && 
                    event.getData() != null && event.getData().getPath().startsWith(basePath + "/")) {
                    String serviceName = event.getData().getPath().substring(basePath.length() + 1);
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
                
                // 所有dubbo service都得入库（新表结构）
                try {
                    dubboServiceDbService.saveOrUpdateServiceWithNode(providerInfo);
                    log.debug("成功同步新Provider到新数据库结构: {}", data.getPath());
                } catch (Exception e) {
                    log.error("同步新Provider到新数据库结构失败: {}", data.getPath(), e);
                }
                
                // 检查数据库中的审批状态，只有已审批的Provider才会被添加watch
                Optional<ProviderInfoEntity> approvedProvider = 
                    providerInfoDbService.findByZkPathAndApprovalStatus(data.getPath(), ProviderInfoEntity.ApprovalStatus.APPROVED);
                if (approvedProvider.isPresent()) {
                    providerService.addProvider(providerInfo);
                    log.debug("添加新已审批Provider到服务监控: {}", data.getPath());
                } else {
                    log.debug("跳过新未审批Provider的服务监控: {}", data.getPath());
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
            
            providerService.removeProviderByZkPath(zkPath);
            
            // 从数据库中移除（新表结构）
            try {
                dubboServiceDbService.removeServiceByZkPath(zkPath);
                log.debug("成功从新数据库结构移除Provider: {}", zkPath);
            } catch (Exception e) {
                log.error("从新数据库结构移除Provider失败: {}", zkPath, e);
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
                
                // 所有dubbo service都得入库（新表结构）
                try {
                    dubboServiceDbService.saveOrUpdateServiceWithNode(providerInfo);
                    log.debug("成功同步更新Provider到新数据库结构: {}", data.getPath());
                } catch (Exception e) {
                    log.error("同步更新Provider到新数据库结构失败: {}", data.getPath(), e);
                }
                
                // 检查数据库中的审批状态，只有已审批的Provider才会被添加watch
                Optional<ProviderInfoEntity> approvedProvider = 
                    providerInfoDbService.findByZkPathAndApprovalStatus(data.getPath(), ProviderInfoEntity.ApprovalStatus.APPROVED);
                if (approvedProvider.isPresent()) {
                    providerService.updateProvider(providerInfo);
                    log.debug("更新已审批Provider到服务监控: {}", data.getPath());
                } else {
                    // 从服务监控中移除（如果之前已添加）
                    providerService.removeProviderByZkPath(data.getPath());
                    log.debug("移除未审批Provider的服务监控: {}", data.getPath());
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
        return client != null && client.getState() == CuratorFrameworkState.STARTED;
    }
    
    /**
     * 检查指定路径是否已经被缓存（已添加Watcher）
     * 
     * @param path 要检查的路径
     * @return 如果路径已被缓存返回true，否则返回false
     */
    public boolean isPathCached(String path) {
        return pathCaches.containsKey(path);
    }
    
    @PreDestroy
    public void destroy() {
        try {
            // 关闭所有缓存
            for (TreeCache cache : pathCaches.values()) {
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
