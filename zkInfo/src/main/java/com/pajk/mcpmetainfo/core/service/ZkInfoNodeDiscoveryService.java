package com.pajk.mcpmetainfo.core.service;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * zkInfo 节点发现服务
 * 用于发现所有活跃的 zkInfo 节点
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZkInfoNodeDiscoveryService {

    private final NamingService namingService;

    @Value("${spring.application.name:zkinfo}")
    private String zkInfoServiceName;

    @Value("${nacos.registry.service-group:mcp-server}")
    private String serviceGroup;

    /**
     * 获取所有活跃的 zkInfo 节点
     * 
     * @return 活跃的 zkInfo 节点列表（IP:Port 格式）
     */
    public List<ZkInfoNode> getAllActiveZkInfoNodes() {
        List<ZkInfoNode> nodes = new ArrayList<>();
        
        try {
            // 从 Nacos 查询所有 zkInfo 服务实例
            List<Instance> instances = namingService.getAllInstances(zkInfoServiceName, serviceGroup);
            
            if (instances == null || instances.isEmpty()) {
                log.warn("⚠️ No zkInfo instances found in Nacos, serviceName: {}, group: {}", 
                        zkInfoServiceName, serviceGroup);
                // 如果 Nacos 中没有注册，至少返回当前节点
                nodes.add(getCurrentNode());
                return nodes;
            }
            
            // 过滤出健康的实例
            List<Instance> healthyInstances = instances.stream()
                    .filter(Instance::isHealthy)
                    .filter(Instance::isEnabled)
                    .collect(Collectors.toList());
            
            if (healthyInstances.isEmpty()) {
                log.warn("⚠️ No healthy zkInfo instances found, using current node only");
                nodes.add(getCurrentNode());
                return nodes;
            }
            
            // 转换为 ZkInfoNode
            for (Instance instance : healthyInstances) {
                ZkInfoNode node = new ZkInfoNode();
                node.setIp(instance.getIp());
                node.setPort(instance.getPort());
                node.setHealthy(instance.isHealthy());
                node.setEnabled(instance.isEnabled());
                node.setMetadata(instance.getMetadata());
                nodes.add(node);
            }
            
            log.info("✅ Found {} active zkInfo nodes: {}", nodes.size(), 
                    nodes.stream()
                            .map(n -> n.getIp() + ":" + n.getPort())
                            .collect(Collectors.joining(", ")));
            
        } catch (NacosException e) {
            log.error("❌ Failed to discover zkInfo nodes from Nacos: {}", e.getMessage(), e);
            // 如果查询失败，至少返回当前节点
            nodes.add(getCurrentNode());
        }
        
        return nodes;
    }

    /**
     * 获取当前节点信息
     */
    private ZkInfoNode getCurrentNode() {
        ZkInfoNode node = new ZkInfoNode();
        try {
            String localIp = getLocalIp();
            node.setIp(localIp);
            node.setPort(getServerPort());
            node.setHealthy(true);
            node.setEnabled(true);
        } catch (Exception e) {
            log.error("Failed to get current node info", e);
        }
        return node;
    }

    /**
     * 获取本机IP
     */
    private String getLocalIp() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.error("Failed to get local IP", e);
            return "127.0.0.1";
        }
    }

    @Value("${server.port:9091}")
    private int serverPort;

    /**
     * 获取服务器端口
     */
    private int getServerPort() {
        return serverPort;
    }

    /**
     * zkInfo 节点信息
     */
    public static class ZkInfoNode {
        private String ip;
        private int port;
        private boolean healthy;
        private boolean enabled;
        private Map<String, String> metadata;

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isHealthy() {
            return healthy;
        }

        public void setHealthy(boolean healthy) {
            this.healthy = healthy;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }

        public String getAddress() {
            return ip + ":" + port;
        }
    }
}

