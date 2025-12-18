package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import com.pajk.mcpmetainfo.persistence.entity.ProviderInfoEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCache;

import jakarta.annotation.PreDestroy;

import java.util.Map;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ZooKeeper Watcher定时调度服务
 * 
 * 负责定时扫描已审批的Dubbo服务，并为其添加ZooKeeper监听器(Watcher)，
 * 以实时监控服务提供者的注册、注销和状态变更。
 * 
 * <p>定时任务说明：</p>
 * <ul>
 *   <li>每天凌晨0点执行一次全量扫描</li>
 *   <li>检查已审批但未添加Watcher的服务</li>
 *   <li>为这些服务添加Watcher以监控其变化</li>
 * </ul>
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
public class ZkWatcherSchedulerService {
    
    @Autowired
    private DubboServiceDbService dubboServiceDbService;
    
    @Autowired
    private ProviderInfoDbService providerInfoDbService;
    
    @Autowired
    private ProviderService providerService;
    
    @Autowired
    private ZooKeeperService zooKeeperService;
    
    @Autowired
    private com.pajk.mcpmetainfo.core.config.ZooKeeperConfig zooKeeperConfig;
    
    // 存储已添加的Watcher，避免重复添加
    private final ConcurrentHashMap<String, TreeCache> pathCaches = new ConcurrentHashMap<>();
    
    /**
     * 应用程序关闭时清理资源
     */
    @PreDestroy
    public void destroy() {
        log.info("正在清理ZooKeeper Watcher资源...");
        for (Map.Entry<String, TreeCache> entry : pathCaches.entrySet()) {
            try {
                entry.getValue().close();
                log.debug("已关闭Watcher: {}", entry.getKey());
            } catch (Exception e) {
                log.warn("关闭Watcher时发生错误: {}", entry.getKey(), e);
            }
        }
        pathCaches.clear();
        log.info("ZooKeeper Watcher资源清理完成");
    }
    
    /**
     * 每天凌晨0点执行的定时任务
     * 扫描所有已审批的Dubbo服务，检查并添加缺失的Watcher
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void scanAndAddWatchers() {
        log.info("开始执行ZooKeeper Watcher定时扫描任务");
        
        try {
            // 获取所有已审批的Dubbo服务
            List<DubboServiceEntity> approvedServices = dubboServiceDbService.findByApprovalStatus(
                DubboServiceEntity.ApprovalStatus.APPROVED
            );
            
            log.info("找到 {} 个已审批的Dubbo服务", approvedServices.size());
            
            // 遍历每个已审批的服务
            for (DubboServiceEntity service : approvedServices) {
                try {
                    // 构建服务在ZooKeeper中的路径
                    String servicePath = buildServicePath(service);
                    
                    // 检查是否已经添加了Watcher
                    if (isWatcherAdded(servicePath)) {
                        log.debug("服务 {} 已经添加了Watcher，跳过", servicePath);
                        continue;
                    }
                    
                    // 为服务添加Watcher
                    addWatcherForService(servicePath, service);
                    
                } catch (Exception e) {
                    log.error("处理服务 {} 的Watcher时发生错误", service.getServiceKey(), e);
                }
            }
            
            log.info("ZooKeeper Watcher定时扫描任务执行完成");
            
        } catch (Exception e) {
            log.error("执行ZooKeeper Watcher定时扫描任务时发生错误", e);
        }
    }
    
    /**
     * 构建服务在ZooKeeper中的路径
     * 
     * @param service Dubbo服务实体
     * @return ZooKeeper中providers目录的路径
     */
    private String buildServicePath(DubboServiceEntity service) {
        StringBuilder path = new StringBuilder(zooKeeperConfig.getBasePath()); // 基础路径来自配置
        
        if (service.getInterfaceName() != null && !service.getInterfaceName().isEmpty()) {
            path.append("/").append(service.getInterfaceName()).append("/providers");
        }
        
        return path.toString();
    }
    
    /**
     * 检查指定路径是否已经添加了Watcher
     * 
     * @param servicePath 服务路径
     * @return 是否已添加Watcher
     */
    private boolean isWatcherAdded(String servicePath) {
        return zooKeeperService.isPathCached(servicePath);
    }
    
    /**
     * 为指定服务添加Watcher
     * 
     * @param servicePath 服务路径
     * @param service Dubbo服务实体
     */
    private void addWatcherForService(String servicePath, DubboServiceEntity service) {
        try {
            CuratorFramework client = zooKeeperService.getClient();
            
            // 检查服务路径是否存在
            if (client.checkExists().forPath(servicePath) == null) {
                log.warn("服务路径 {} 在ZooKeeper中不存在", servicePath);
                return;
            }
            
            // 创建TreeCache监听providers路径的变化
            TreeCache cache = new TreeCache(client, servicePath);
            
            cache.getListenable().addListener((client1, event) -> {
                try {
                    // 从服务路径中提取服务名称 (移除最后的"/providers"部分)
                    String serviceName = service.getInterfaceName();
                    
                    // 复用ZooKeeperService中的处理逻辑
                    switch (event.getType()) {
                        case NODE_ADDED:
                            if (event.getData() != null) {
                                log.info("检测到服务 {} 的Provider添加事件", serviceName);
                                // 由于ZooKeeperService中的处理方法是私有的，我们在这里重新实现类似的逻辑
                                handleProviderAdded(event.getData(), serviceName, service);
                            }
                            break;
                        case NODE_REMOVED:
                            if (event.getData() != null) {
                                log.info("检测到服务 {} 的Provider移除事件", serviceName);
                                // 由于ZooKeeperService中的处理方法是私有的，我们在这里重新实现类似的逻辑
                                handleProviderRemoved(event.getData(), serviceName);
                            }
                            break;
                        case NODE_UPDATED:
                            if (event.getData() != null) {
                                log.info("检测到服务 {} 的Provider更新事件", serviceName);
                                // 由于ZooKeeperService中的处理方法是私有的，我们在这里重新实现类似的逻辑
                                handleProviderUpdated(event.getData(), serviceName, service);
                            }
                            break;
                        default:
                            log.debug("检测到服务 {} 的其他事件: {}", serviceName, event.getType());
                            break;
                    }
                } catch (Exception e) {
                    log.error("处理服务 {} 的Provider事件时发生错误", service.getInterfaceName(), e);
                }
            });
            
            // 启动监听
            cache.start();
            
            // 将cache存储起来，防止重复添加
            pathCaches.put(servicePath, cache);
            
            log.info("成功为服务 {} 添加Watcher: {}", service.getInterfaceName(), servicePath);
            
        } catch (Exception e) {
            log.error("为服务 {} 添加Watcher时发生错误", service.getServiceKey(), e);
        }
    }
    
    /**
     * 处理Provider添加事件
     */
    private void handleProviderAdded(ChildData data, String serviceName, DubboServiceEntity service) {
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
                // 注意：这里我们需要检查对应的服务是否已审批
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
    private void handleProviderUpdated(ChildData data, String serviceName, DubboServiceEntity service) {
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
}