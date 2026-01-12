package com.pajk.mcpmetainfo.core.service;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * zkInfo 自身注册服务
 * 负责将 zkInfo 节点注册到 Nacos，以便其他节点能够发现
 * 同时检查并清理不健康的老节点
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZkInfoSelfRegistrationService {

    private final NamingService namingService;

    @Autowired(required = false)
    private NacosV3ApiService nacosV3ApiService;

    @Value("${spring.application.name:zkinfo}")
    private String zkInfoServiceName;

    @Value("${server.port:9091}")
    private int serverPort;

    @Value("${nacos.registry.service-group:mcp-server}")
    private String serviceGroup;

    @Value("${nacos.registry.enabled:true}")
    private boolean registryEnabled;

    @Value("${nacos.v3.api.enabled:true}")
    private boolean useV3Api;

    /**
     * 应用启动完成后，注册 zkInfo 节点自身到 Nacos
     * 同时检查并清理不健康的老节点
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerZkInfoNode() {
        if (!registryEnabled) {
            log.info("Nacos registry is disabled, skip zkInfo self-registration");
            return;
        }

        try {
            String localIp = getLocalIp();
            
            // 1. 检查并清理不健康的老节点
            checkAndCleanUnhealthyNodes(localIp);
            
            // 2. 注册当前节点
            Instance instance = new Instance();
            instance.setIp(localIp);
            instance.setPort(serverPort);
            instance.setHealthy(true);
            instance.setEnabled(true);
            instance.setEphemeral(true); // zkInfo 节点使用临时节点，停止后自动删除
            
            // 设置元数据
            Map<String, String> metadata = new HashMap<>();
            metadata.put("application", zkInfoServiceName);
            metadata.put("version", "1.0.0");
            metadata.put("nodeType", "zkinfo");
            metadata.put("startTime", String.valueOf(System.currentTimeMillis()));
            instance.setMetadata(metadata);
            
            // 注册实例
            namingService.registerInstance(zkInfoServiceName, serviceGroup, instance);
            
            log.info("✅ Registered zkInfo node to Nacos: {}:{} in group: {} (ephemeral: true)", 
                    localIp, serverPort, serviceGroup);
            
        } catch (Exception e) {
            log.error("❌ Failed to register zkInfo node to Nacos: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查并清理不健康的老节点
     * 对于不健康的持久节点（ephemeral=false），将其改为临时节点（ephemeral=true），让 Nacos 自动摘除
     * 
     * @param currentNodeIp 当前节点IP（不处理当前节点）
     */
    private void checkAndCleanUnhealthyNodes(String currentNodeIp) {
        try {
            // 查询所有 zkInfo 节点
            List<Instance> instances = namingService.getAllInstances(zkInfoServiceName, serviceGroup);
            
            if (instances == null || instances.isEmpty()) {
                log.debug("No existing zkInfo nodes found in Nacos");
                return;
            }
            
            int cleanedCount = 0;
            for (Instance instance : instances) {
                // 跳过当前节点
                if (currentNodeIp.equals(instance.getIp()) && serverPort == instance.getPort()) {
                    continue;
                }
                
                // 检查节点健康状态
                boolean isUnhealthy = !instance.isHealthy() || !instance.isEnabled();
                boolean isPersistent = !instance.isEphemeral();
                
                // 如果节点不健康且是持久节点，将其改为临时节点，让 Nacos 自动摘除
                if (isUnhealthy && isPersistent) {
                    try {
                        log.warn("⚠️ Found unhealthy persistent node: {}:{}, converting to ephemeral for auto-removal", 
                                instance.getIp(), instance.getPort());
                        
                        // 先删除持久节点
                        boolean deleted = false;
                        if (useV3Api && nacosV3ApiService != null) {
                            // 使用 v3 API 删除
                            deleted = nacosV3ApiService.deregisterInstance(
                                    zkInfoServiceName, instance.getIp(), instance.getPort(), 
                                    serviceGroup, false); // false 表示是持久节点
                        } else {
                            // 使用 SDK 删除（SDK 会自动识别 ephemeral 状态）
                            try {
                                namingService.deregisterInstance(zkInfoServiceName, serviceGroup, 
                                        instance.getIp(), instance.getPort());
                                deleted = true;
                            } catch (Exception e) {
                                log.warn("⚠️ Failed to delete via SDK, trying v3 API: {}", e.getMessage());
                                // 如果 SDK 失败，尝试使用 v3 API（如果可用）
                                if (nacosV3ApiService != null) {
                                    deleted = nacosV3ApiService.deregisterInstance(
                                            zkInfoServiceName, instance.getIp(), instance.getPort(), 
                                            serviceGroup, false);
                                }
                            }
                        }
                        
                        if (deleted) {
                            log.info("✅ Deleted unhealthy persistent node: {}:{}", 
                                    instance.getIp(), instance.getPort());
                            
                            // 重新注册为临时节点（这样 Nacos 会自动摘除没有心跳的节点）
                            // 注意：如果节点真的已经宕机，重新注册后 Nacos 会立即检测到没有心跳并删除
                            Instance ephemeralInstance = new Instance();
                            ephemeralInstance.setIp(instance.getIp());
                            ephemeralInstance.setPort(instance.getPort());
                            ephemeralInstance.setHealthy(false); // 标记为不健康
                            ephemeralInstance.setEnabled(false); // 禁用
                            ephemeralInstance.setEphemeral(true); // 改为临时节点
                            
                            // 保留原有元数据（如果有）
                            if (instance.getMetadata() != null) {
                                ephemeralInstance.setMetadata(new HashMap<>(instance.getMetadata()));
                            } else {
                                ephemeralInstance.setMetadata(new HashMap<>());
                            }
                            
                            namingService.registerInstance(zkInfoServiceName, serviceGroup, ephemeralInstance);
                            log.info("✅ Re-registered unhealthy node as ephemeral: {}:{} (will be auto-removed by Nacos if no heartbeat)", 
                                    instance.getIp(), instance.getPort());
                            cleanedCount++;
                        } else {
                            log.warn("⚠️ Failed to delete unhealthy persistent node: {}:{}", 
                                    instance.getIp(), instance.getPort());
                        }
                        
                    } catch (Exception e) {
                        log.error("❌ Failed to clean unhealthy node: {}:{}, error: {}", 
                                instance.getIp(), instance.getPort(), e.getMessage(), e);
                    }
                } else if (isUnhealthy && !isPersistent) {
                    // 不健康的临时节点，Nacos 会自动摘除，只记录日志
                    log.info("ℹ️ Found unhealthy ephemeral node: {}:{} (will be auto-removed by Nacos)", 
                            instance.getIp(), instance.getPort());
                }
            }
            
            if (cleanedCount > 0) {
                log.info("✅ Cleaned {} unhealthy persistent zkInfo nodes", cleanedCount);
            }
            
        } catch (NacosException e) {
            log.error("❌ Failed to check unhealthy nodes: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取本机IP
     */
    private String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.error("Failed to get local IP", e);
            return "127.0.0.1";
        }
    }
}

