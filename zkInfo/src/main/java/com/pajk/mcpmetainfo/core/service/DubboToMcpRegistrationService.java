package com.pajk.mcpmetainfo.core.service;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import com.pajk.mcpmetainfo.core.util.McpToolSchemaGenerator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Dubbo转MCP服务注册服务
 * 模拟 mcp-server-v6 的注册机制，将发现的Dubbo服务自动注册为MCP服务到Nacos
 * 
 * 核心功能：
 * 1. 监听Dubbo服务变化，自动注册为MCP服务
 * 2. 创建Nacos配置（tools, versions, server）
 * 3. 注册服务实例到Nacos
 * 4. 实现与mcp-server-v6相同的注册格式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DubboToMcpRegistrationService {

    private final NamingService namingService;
    private final ConfigService configService;
    private final ProviderService providerService;
    private final McpConverterService mcpConverterService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private com.pajk.mcpmetainfo.core.util.McpToolSchemaGenerator mcpToolSchemaGenerator;

    @Value("${server.port:9091}")
    private int serverPort;

    @Value("${nacos.registry.service-group:mcp-server}")
    private String serviceGroup;

    @Value("${nacos.registry.enabled:true}")
    private boolean registryEnabled;

    // 配置组常量（与mcp-server-v6保持一致）
    private static final String SERVER_GROUP = "mcp-server";
    private static final String TOOLS_GROUP = "mcp-tools";
    private static final String VERSIONS_GROUP = "mcp-server-versions";

    // 已注册的服务缓存（serviceInterface:version -> serviceId）
    private final Map<String, String> registeredServices = new ConcurrentHashMap<>();

    /**
     * 应用启动完成后，注册所有已发现的Dubbo服务
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerAllServicesOnStartup() {
        if (!registryEnabled) {
            log.info("Nacos registry is disabled, skip auto registration");
            return;
        }

        log.info("🚀 Starting to register all Dubbo services as MCP services...");
        
        // 获取所有应用
        List<com.pajk.mcpmetainfo.core.model.ApplicationInfo> applications = providerService.getAllApplications();
        
        // 按服务接口分组
        Map<String, List<ProviderInfo>> servicesByInterface = new HashMap<>();
        for (com.pajk.mcpmetainfo.core.model.ApplicationInfo app : applications) {
            for (ProviderInfo provider : app.getProviders()) {
                String key = provider.getInterfaceName() + ":" + 
                             (provider.getVersion() != null ? provider.getVersion() : "default");
                servicesByInterface.computeIfAbsent(key, k -> new ArrayList<>()).add(provider);
            }
        }

        // 批量注册
        for (Map.Entry<String, List<ProviderInfo>> entry : servicesByInterface.entrySet()) {
            String[] parts = entry.getKey().split(":");
            String serviceInterface = parts[0];
            String version = parts.length > 1 ? parts[1] : "default";
            
            try {
                registerDubboServiceAsMcp(serviceInterface, version, entry.getValue());
            } catch (Exception e) {
                log.error("Failed to register service: {}:{}", serviceInterface, version, e);
            }
        }

        log.info("✅ Completed registering {} services to Nacos", servicesByInterface.size());
    }

    /**
     * 注册单个Dubbo服务为MCP服务
     * 当Zookeeper发现新服务时调用
     */
    @Async
    public void registerDubboServiceAsMcp(String serviceInterface, String version, 
                                          List<ProviderInfo> providers) {
        if (!registryEnabled) {
            return;
        }

        String serviceKey = serviceInterface + ":" + version;
        
        // 检查是否已注册
        if (registeredServices.containsKey(serviceKey)) {
            log.debug("Service {}:{} already registered, skip", serviceInterface, version);
            return;
        }

        try {
            // 1. 生成服务ID（UUID，可重现）
            String serviceId = generateServiceId(serviceInterface, version);
            
            // 2. 构建MCP服务名称
            String mcpServiceName = buildMcpServiceName(serviceInterface, version);
            
            log.info("🚀 Registering Dubbo service as MCP: {}:{} -> {}", 
                    serviceInterface, version, mcpServiceName);
            
            // 3. 生成工具列表（从Dubbo方法转换为MCP工具）
            List<Map<String, Object>> tools = generateMcpTools(providers);
            
            if (tools.isEmpty()) {
                log.warn("⚠️ No tools found for service {}:{}, skip registration", 
                        serviceInterface, version);
                return;
            }
            
            // 4. 创建并发布配置到Nacos配置中心
            publishConfigsToNacos(serviceId, mcpServiceName, version, tools);
            
            // 5. 注册服务实例到Nacos服务列表
            registerInstanceToNacos(mcpServiceName, serviceId, version, tools, providers);
            
            // 6. 缓存已注册服务
            registeredServices.put(serviceKey, serviceId);
            
            log.info("✅ Successfully registered MCP service: {} to Nacos ({} tools)", 
                    mcpServiceName, tools.size());
            
        } catch (Exception e) {
            log.error("❌ Failed to register MCP service: {}:{}", serviceInterface, version, e);
        }
    }

    /**
     * 注销MCP服务（当服务从Zookeeper移除时调用）
     */
    @Async
    public void deregisterMcpService(String serviceInterface, String version) {
        if (!registryEnabled) {
            return;
        }

        try {
            String serviceKey = serviceInterface + ":" + version;
            String mcpServiceName = buildMcpServiceName(serviceInterface, version);
            String localIp = getLocalIp();
            
            namingService.deregisterInstance(mcpServiceName, serviceGroup, localIp, serverPort);
            
            // 从缓存中移除
            registeredServices.remove(serviceKey);
            
            log.info("✅ Deregistered MCP service: {} from Nacos", mcpServiceName);
        } catch (Exception e) {
            log.error("❌ Failed to deregister MCP service: {}:{}", serviceInterface, version, e);
        }
    }

    /**
     * 生成服务ID（UUID，基于服务名和版本，确保可重现）
     */
    private String generateServiceId(String serviceInterface, String version) {
        String key = serviceInterface + ":" + version;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * 构建MCP服务名称
     * 格式：zk-mcp-{interfaceName}-{version}
     */
    private String buildMcpServiceName(String serviceInterface, String version) {
        String normalizedName = serviceInterface
                .replace(".", "-")
                .replace("/", "-")
                .toLowerCase();
        String versionStr = version != null && !version.isEmpty() ? version : "default";
        return "zk-mcp-" + normalizedName + "-" + versionStr;
    }

    /**
     * 生成MCP工具列表（从Dubbo方法转换）
     * 使用McpConverterService来转换，确保格式正确
     */
    private List<Map<String, Object>> generateMcpTools(List<ProviderInfo> providers) {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        // 去重：同一个接口的多个provider可能有相同的方法
        Set<String> toolNames = new HashSet<>();
        
        for (ProviderInfo provider : providers) {
            if (provider.getMethods() != null && !provider.getMethods().isEmpty()) {
                String[] methods = provider.getMethods().split(",");
                
                for (String method : methods) {
                    String methodName = method.trim();
                    if (methodName.isEmpty()) {
                        continue;
                    }
                    
                    // 工具名称：接口名.方法名
                    String toolName = provider.getInterfaceName() + "." + methodName;
                    
                    // 去重
                    if (toolNames.contains(toolName)) {
                        continue;
                    }
                    toolNames.add(toolName);
                    
                    // 构建工具定义
                    Map<String, Object> tool = new HashMap<>();
                    tool.put("name", toolName);
                    tool.put("description", String.format("调用 %s 服务的 %s 方法", 
                            provider.getInterfaceName(), methodName));
                    
                    // 根据实际方法参数生成 inputSchema
                    Map<String, Object> inputSchema = mcpToolSchemaGenerator.createInputSchemaFromMethod(
                            provider.getInterfaceName(), methodName);
                    tool.put("inputSchema", inputSchema);
                    
                    tools.add(tool);
                }
            }
        }
        
        return tools;
    }

    /**
     * 发布配置到Nacos配置中心
     * 需要创建3个配置（与mcp-server-v6格式一致）：
     * 1. {serviceId}-{version}-mcp-tools.json (mcp-tools组)
     * 2. {serviceId}-mcp-versions.json (mcp-server-versions组)
     * 3. {serviceId}-{version}-mcp-server.json (mcp-server组)
     */
    private void publishConfigsToNacos(String serviceId, String mcpServiceName, 
                                       String version, List<Map<String, Object>> tools) 
            throws NacosException {
        
        // 1. 发布 mcp-tools.json
        String toolsDataId = serviceId + "-" + version + "-mcp-tools.json";
        String toolsContent = createToolsConfig(tools);
        boolean toolsPublished = configService.publishConfig(toolsDataId, TOOLS_GROUP, toolsContent);
        if (toolsPublished) {
            log.info("📝 Published tools config: {} ({} tools)", toolsDataId, tools.size());
        } else {
            log.warn("⚠️ Failed to publish tools config: {}", toolsDataId);
        }
        
        // 2. 发布 mcp-versions.json
        String versionsDataId = serviceId + "-mcp-versions.json";
        String versionsContent = createVersionsConfig(serviceId, mcpServiceName, version);
        boolean versionsPublished = configService.publishConfig(versionsDataId, VERSIONS_GROUP, versionsContent);
        if (versionsPublished) {
            log.info("📝 Published versions config: {}", versionsDataId);
        } else {
            log.warn("⚠️ Failed to publish versions config: {}", versionsDataId);
        }
        
        // 3. 发布 mcp-server.json
        String serverDataId = serviceId + "-" + version + "-mcp-server.json";
        String serverContent = createServerConfig(serviceId, mcpServiceName, version, toolsDataId);
        boolean serverPublished = configService.publishConfig(serverDataId, SERVER_GROUP, serverContent);
        if (serverPublished) {
            log.info("📝 Published server config: {}", serverDataId);
        } else {
            log.warn("⚠️ Failed to publish server config: {}", serverDataId);
        }
    }

    /**
     * 创建工具配置JSON（与mcp-server-v6格式一致）
     */
    private String createToolsConfig(List<Map<String, Object>> tools) {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("tools", tools);
            config.put("toolsMeta", Map.of());
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to create tools config", e);
            throw new RuntimeException("Failed to create tools config", e);
        }
    }

    /**
     * 创建版本配置JSON（与mcp-server-v6格式一致）
     */
    private String createVersionsConfig(String serviceId, String mcpServiceName, String version) {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("id", serviceId);
            config.put("name", mcpServiceName);
            config.put("protocol", "mcp-sse");
            config.put("frontProtocol", "mcp-sse");
            config.put("description", "Dubbo service converted to MCP: " + mcpServiceName);
            config.put("enabled", true);
            config.put("capabilities", List.of("TOOL"));
            config.put("latestPublishedVersion", version);
            
            Map<String, Object> versionDetail = new HashMap<>();
            versionDetail.put("version", version);
            versionDetail.put("release_date", Instant.now().toString());
            config.put("versionDetails", List.of(versionDetail));
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to create versions config", e);
            throw new RuntimeException("Failed to create versions config", e);
        }
    }

    /**
     * 创建服务器配置JSON（与mcp-server-v6格式一致）
     */
    private String createServerConfig(String serviceId, String mcpServiceName, 
                                      String version, String toolsDataId) {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("id", serviceId);
            config.put("name", mcpServiceName);
            config.put("protocol", "mcp-sse");
            config.put("frontProtocol", "mcp-sse");
            config.put("description", "Dubbo service converted to MCP: " + mcpServiceName);
            
            // 版本详情
            Map<String, Object> versionDetail = new HashMap<>();
            versionDetail.put("version", version);
            versionDetail.put("release_date", Instant.now().toString());
            config.put("versionDetail", versionDetail);
            
            // 远程服务器配置
            Map<String, Object> remoteServerConfig = new HashMap<>();
            Map<String, Object> serviceRef = new HashMap<>();
            serviceRef.put("namespaceId", "public");
            serviceRef.put("groupName", serviceGroup);
            serviceRef.put("serviceName", mcpServiceName);
            remoteServerConfig.put("serviceRef", serviceRef);
            remoteServerConfig.put("exportPath", "/sse");
            config.put("remoteServerConfig", remoteServerConfig);
            
            config.put("enabled", true);
            config.put("capabilities", List.of("TOOL"));
            config.put("toolsDescriptionRef", toolsDataId);
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to create server config", e);
            throw new RuntimeException("Failed to create server config", e);
        }
    }

    /**
     * 注册服务实例到Nacos服务列表
     * 元数据格式与mcp-server-v6保持一致
     */
    private void registerInstanceToNacos(String mcpServiceName, String serviceId, 
                                        String version, List<Map<String, Object>> tools,
                                        List<ProviderInfo> providers) 
            throws NacosException {
        
        String localIp = getLocalIp();
        
        // 创建实例
        Instance instance = new Instance();
        instance.setIp(localIp);
        instance.setPort(serverPort);
        instance.setHealthy(true);
        instance.setEnabled(true);
        instance.setEphemeral(true);
        
        // 设置元数据（与mcp-server-v6格式一致）
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", version != null ? version : "1.0.0");
        metadata.put("sseEndpoint", "/sse");
        metadata.put("sseMessageEndpoint", "/mcp/message");
        metadata.put("protocol", "mcp-sse");
        metadata.put("serverName", mcpServiceName);
        metadata.put("serverId", serviceId);
        
        // 从providers中提取application信息
        String application = extractApplicationFromProviders(providers);
        if (application != null && !application.isEmpty()) {
            metadata.put("application", application);
            log.info("📦 Setting application for MCP service: {} -> {}", mcpServiceName, application);
        } else {
            log.warn("⚠️ No application found in providers for MCP service: {}", mcpServiceName);
        }
        
        // 工具名称列表（逗号分隔）
        String toolNames = tools.stream()
                .map(tool -> (String) tool.get("name"))
                .collect(Collectors.joining(","));
        metadata.put("tools.names", toolNames);
        
        // 计算server配置的MD5（与mcp-server-v6一致）
        String serverDataId = serviceId + "-" + version + "-mcp-server.json";
        try {
            String serverConfig = configService.getConfig(serverDataId, SERVER_GROUP, 5000);
            if (serverConfig != null) {
                String md5 = calculateMd5(serverConfig);
                metadata.put("server.md5", md5);
            }
        } catch (Exception e) {
            log.warn("Failed to get server config for MD5 calculation", e);
        }
        
        instance.setMetadata(metadata);
        
        // 注册实例
        namingService.registerInstance(mcpServiceName, serviceGroup, instance);
        
        log.info("✅ Registered instance to Nacos: {}:{} in group: {} (application: {}, {} tools)", 
                localIp, serverPort, serviceGroup, application != null ? application : "N/A", tools.size());
    }
    
    /**
     * 从providers中提取application信息
     * 如果有多个不同的application，返回第一个非空的application
     * 如果所有providers都没有application，尝试从接口名中提取应用名
     */
    private String extractApplicationFromProviders(List<ProviderInfo> providers) {
        if (providers == null || providers.isEmpty()) {
            return null;
        }
        
        // 收集所有非空的application
        Set<String> applications = providers.stream()
                .map(ProviderInfo::getApplication)
                .filter(app -> app != null && !app.isEmpty())
                .collect(Collectors.toSet());
        
        if (!applications.isEmpty()) {
            // 如果有多个不同的application，返回第一个（或者可以考虑合并）
            if (applications.size() > 1) {
                log.warn("⚠️ Multiple applications found in providers: {}, using first one: {}", 
                        applications, applications.iterator().next());
            }
            return applications.iterator().next();
        }
        
        // 如果所有providers都没有application，尝试从接口名中提取应用名
        log.debug("⚠️ No application found in providers, trying to extract from interface name");
        String extractedApp = extractApplicationFromInterfaceName(providers);
        if (extractedApp != null && !extractedApp.isEmpty()) {
            log.info("📦 Extracted application from interface name: {}", extractedApp);
            return extractedApp;
        }
        
        log.warn("⚠️ Could not extract application from providers or interface names");
        return null;
    }
    
    /**
     * 从接口名中提取应用名
     * 策略：取接口名的包名部分，转换为应用名格式
     * 例如：com.pajk.mcpmetainfo.core.demo.service.OrderService -> demo-service
     */
    private String extractApplicationFromInterfaceName(List<ProviderInfo> providers) {
        if (providers == null || providers.isEmpty()) {
            return null;
        }
        
        // 收集所有接口名
        Set<String> interfaceNames = providers.stream()
                .map(ProviderInfo::getInterfaceName)
                .filter(name -> name != null && !name.isEmpty())
                .collect(Collectors.toSet());
        
        if (interfaceNames.isEmpty()) {
            return null;
        }
        
        // 从第一个接口名中提取应用名
        String firstInterface = interfaceNames.iterator().next();
        
        // 解析包名：com.pajk.mcpmetainfo.core.demo.service.OrderService
        // 策略1：取倒数第二个包名作为应用名（如果存在）
        if (firstInterface.contains(".")) {
            String[] parts = firstInterface.split("\\.");
            if (parts.length >= 3) {
                // 取倒数第二个部分，例如：com.pajk.mcpmetainfo.core.demo.service -> demo
                String appName = parts[parts.length - 2];
                // 转换为应用名格式（小写，用连字符连接）
                return appName.toLowerCase().replace("_", "-");
            } else if (parts.length == 2) {
                // 如果只有两个部分，取第一个部分
                return parts[0].toLowerCase().replace("_", "-");
            }
        }
        
        // 如果无法从包名提取，使用接口名的前缀部分
        // 例如：OrderService -> order-service
        if (firstInterface.contains(".")) {
            String simpleName = firstInterface.substring(firstInterface.lastIndexOf('.') + 1);
            // 将驼峰命名转换为连字符格式：OrderService -> order-service
            return simpleName.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
        }
        
        return null;
    }

    /**
     * 获取本机IP地址
     */
    private String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("Failed to get local IP, using 127.0.0.1", e);
            return "127.0.0.1";
        }
    }

    /**
     * 计算MD5（与mcp-server-v6一致）
     */
    private String calculateMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to calculate MD5", e);
            return "";
        }
    }

    /**
     * 获取已注册的服务列表
     */
    public Map<String, String> getRegisteredServices() {
        return new HashMap<>(registeredServices);
    }
}

