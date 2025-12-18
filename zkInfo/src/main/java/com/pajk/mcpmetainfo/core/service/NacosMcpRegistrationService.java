package com.pajk.mcpmetainfo.core.service;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import com.pajk.mcpmetainfo.core.util.McpToolSchemaGenerator;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Nacos MCP服务注册服务
 * 模拟 mcp-server-v6 的注册机制，将Dubbo服务注册为MCP服务到Nacos
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NacosMcpRegistrationService {

    private final NamingService namingService; // 保留用于向后兼容
    private final ConfigService configService; // 保留用于向后兼容
    private final NacosV3ApiService nacosV3ApiService; // Nacos v3 HTTP API 服务
    private final McpConverterService mcpConverterService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${nacos.v3.api.enabled:true}")
    private boolean useV3Api; // 是否使用 v3 API
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.pajk.mcpmetainfo.core.util.McpToolSchemaGenerator mcpToolSchemaGenerator;

    @Value("${server.port:9091}")
    private int serverPort;

    @Value("${nacos.registry.service-group:mcp-server}")
    private String serviceGroup;

    @Value("${nacos.registry.enabled:true}")
    private boolean registryEnabled;

    // 配置组常量
    private static final String SERVER_GROUP = "mcp-server";
    private static final String TOOLS_GROUP = "mcp-tools";
    private static final String VERSIONS_GROUP = "mcp-server-versions";

    /**
     * 将Dubbo服务注册为MCP服务到Nacos
     * 
     * @param serviceInterface 服务接口名
     * @param version 服务版本
     * @param providers 服务提供者列表
     */
    public void registerDubboServiceAsMcp(String serviceInterface, String version, 
                                          List<ProviderInfo> providers) {
        if (!registryEnabled) {
            log.debug("Nacos registry is disabled, skip registration");
            return;
        }

        try {
            // 1. 生成服务ID（UUID）
            String serviceId = generateServiceId(serviceInterface, version);
            
            // 2. 构建MCP服务名称
            String mcpServiceName = buildMcpServiceName(serviceInterface, version);
            
            log.info("🚀 Registering Dubbo service as MCP: {} -> {}", serviceInterface, mcpServiceName);
            
            // 3. 生成工具列表（从Dubbo方法转换为MCP工具）
            List<Map<String, Object>> tools = generateMcpTools(providers);
            
            // 4. 创建并发布配置到Nacos配置中心
            publishConfigsToNacos(serviceId, mcpServiceName, version, tools);
            
            // 5. 注册服务实例到Nacos服务列表（普通Dubbo服务，application从providers中提取）
            // 普通Dubbo服务使用临时节点（ephemeral=true），需要心跳机制
            registerInstanceToNacos(mcpServiceName, serviceId, version, tools, providers, null, true);
            
            log.info("✅ Successfully registered MCP service: {} to Nacos", mcpServiceName);
            
        } catch (Exception e) {
            log.error("❌ Failed to register MCP service: {}", serviceInterface, e);
            throw new RuntimeException("Failed to register MCP service to Nacos", e);
        }
    }
    
    /**
     * 将虚拟项目注册为MCP服务到Nacos（使用指定的服务名称）
     * 
     * @param mcpServiceName MCP服务名称（如 mcp-data-analysis）
     * @param version 服务版本
     * @param providers 服务提供者列表
     * @param virtualProjectName 虚拟项目名称（作为 application）
     */
    public void registerVirtualProjectAsMcp(String mcpServiceName, String version, 
                                            List<ProviderInfo> providers,
                                            String virtualProjectName) {
        if (!registryEnabled) {
            log.debug("Nacos registry is disabled, skip registration");
            return;
        }

        try {
            // 1. 生成服务ID（UUID）- 使用服务名称而不是接口名
            String serviceId = generateServiceId(mcpServiceName, version);
            
            log.info("🚀 Registering virtual project as MCP: {} (version: {}, project: {})", 
                    mcpServiceName, version, virtualProjectName);
            
            // 2. 生成工具列表（从Dubbo方法转换为MCP工具）
            List<Map<String, Object>> tools = generateMcpTools(providers);
            
            // 3. 创建并发布配置到Nacos配置中心
            publishConfigsToNacos(serviceId, mcpServiceName, version, tools);
            
            // 4. 注册服务实例到Nacos服务列表（使用虚拟项目名称作为 application）
            // 虚拟项目使用持久节点（ephemeral=false），不需要心跳机制
            registerInstanceToNacos(mcpServiceName, serviceId, version, tools, providers, virtualProjectName, false);
            
            log.info("✅ Successfully registered virtual project MCP service: {} to Nacos (application: {})", 
                    mcpServiceName, virtualProjectName);
            
        } catch (Exception e) {
            log.error("❌ Failed to register virtual project MCP service: {}", mcpServiceName, e);
            throw new RuntimeException("Failed to register virtual project MCP service to Nacos", e);
        }
    }

    /**
     * 生成服务ID（UUID）
     * 使用固定算法从服务名生成，确保可重现
     * 格式与mcp-server-v6一致：使用UUID v3（基于名称的UUID）
     */
    private String generateServiceId(String serviceInterface, String version) {
        String key = serviceInterface + ":" + version;
        // 使用UUID v3（基于名称的UUID），确保相同服务总是生成相同的ID
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * 构建MCP服务名称
     */
    private String buildMcpServiceName(String serviceInterface, String version) {
        // 格式：zk-mcp-{interfaceName}-{version}
        String normalizedName = serviceInterface
                .replace(".", "-")
                .replace("/", "-")
                .toLowerCase();
        return "zk-mcp-" + normalizedName + "-" + (version != null ? version : "default");
    }

    /**
     * 生成MCP工具列表（从Dubbo方法转换）
     */
    private List<Map<String, Object>> generateMcpTools(List<ProviderInfo> providers) {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        // 使用McpConverterService转换工具
        for (ProviderInfo provider : providers) {
            String methodsStr = provider.getMethods();
            if (methodsStr != null && !methodsStr.isEmpty()) {
                String[] methods = methodsStr.split(",");
                for (String method : methods) {
                    String methodName = method.trim();
                    if (methodName.isEmpty()) {
                        continue;
                    }
                    
                    Map<String, Object> tool = new HashMap<>();
                    
                    // 工具名称：接口名.方法名
                    String toolName = provider.getInterfaceName() + "." + methodName;
                    tool.put("name", toolName);
                    
                    // 工具描述
                    tool.put("description", String.format("调用 %s 服务的 %s 方法", 
                            provider.getInterfaceName(), methodName));
                    
                    // 根据实际方法参数生成 inputSchema
                    Map<String, Object> inputSchema = mcpToolSchemaGenerator.createInputSchemaFromMethod(
                            provider.getInterfaceName(), methodName);
                    tool.put("inputSchema", inputSchema);
                    
                    tools.add(tool);
                }
            } else {
                // methods 为空，记录警告日志
                log.warn("⚠️ Provider {}:{} has no methods, cannot generate tools. " +
                        "Please ensure the Dubbo provider URL includes the 'methods' parameter.",
                        provider.getInterfaceName(), provider.getVersion());
            }
        }
        
        if (tools.isEmpty() && !providers.isEmpty()) {
            log.error("❌ No tools generated for {} providers. " +
                    "This may indicate that all providers have empty methods. " +
                    "Please check ZooKeeper provider URLs to ensure they include the 'methods' parameter.",
                    providers.size());
        }
        
        return tools;
    }

    /**
     * 发布配置到Nacos配置中心
     * 需要创建3个配置：tools, versions, server
     */
    private void publishConfigsToNacos(String serviceId, String mcpServiceName, 
                                       String version, List<Map<String, Object>> tools) 
            throws NacosException {
        
        // 1. 发布 mcp-tools.json（指定格式为 JSON）
        String toolsDataId = serviceId + "-" + version + "-mcp-tools.json";
        String toolsContent = createToolsConfig(tools);
        // Nacos SDK 的 publishConfig 方法：publishConfig(String dataId, String group, String content)
        // 配置类型由 dataId 的后缀决定（.json 表示 JSON 格式）
        configService.publishConfig(toolsDataId, TOOLS_GROUP, toolsContent);
        log.info("📝 Published tools config: {} (format: JSON, determined by .json suffix)", toolsDataId);
        
        // 2. 发布 mcp-versions.json（指定格式为 JSON）
        String versionsDataId = serviceId + "-mcp-versions.json";
        String versionsContent = createVersionsConfig(serviceId, mcpServiceName, version);
        configService.publishConfig(versionsDataId, VERSIONS_GROUP, versionsContent);
        log.info("📝 Published versions config: {} (format: JSON, determined by .json suffix)", versionsDataId);
        
        // 3. 发布 mcp-server.json（指定格式为 JSON）
        String serverDataId = serviceId + "-" + version + "-mcp-server.json";
        String serverContent = createServerConfig(serviceId, mcpServiceName, version, toolsDataId);
        configService.publishConfig(serverDataId, SERVER_GROUP, serverContent);
        log.info("📝 Published server config: {} (format: JSON, determined by .json suffix)", serverDataId);
    }

    /**
     * 创建工具配置JSON
     */
    private String createToolsConfig(List<Map<String, Object>> tools) {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("tools", tools);
            config.put("toolsMeta", Map.of());
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to create tools config", e);
            throw new RuntimeException("Failed to create tools config", e);
        }
    }

    /**
     * 创建版本配置JSON
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
            
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to create versions config", e);
            throw new RuntimeException("Failed to create versions config", e);
        }
    }

    /**
     * 创建服务器配置JSON
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
            
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to create server config", e);
            throw new RuntimeException("Failed to create server config", e);
        }
    }

    /**
     * 注册服务实例到Nacos服务列表
     * 
     * @param mcpServiceName MCP服务名称
     * @param serviceId 服务ID
     * @param version 版本
     * @param tools 工具列表
     * @param providers Provider列表
     * @param application 应用名称（如果为null，则从providers中提取）
     * @param ephemeral 是否为临时节点（true=临时节点需要心跳，false=持久节点不需要心跳）
     */
    private void registerInstanceToNacos(String mcpServiceName, String serviceId, 
                                        String version, List<Map<String, Object>> tools,
                                        List<ProviderInfo> providers,
                                        String application, boolean ephemeral) 
            throws NacosException {
        
        // 获取本机IP
        String localIp = getLocalIp();
        
        // 创建实例
        Instance instance = new Instance();
        instance.setIp(localIp);
        instance.setPort(serverPort);
        instance.setHealthy(true);
        instance.setEnabled(true);
        instance.setEphemeral(ephemeral);
        
        // 对于虚拟项目（持久节点），记录日志
        if (!ephemeral) {
            log.info("📌 Registering virtual project as persistent node (ephemeral=false): {}", mcpServiceName);
        }
        
        // 设置元数据
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", version != null ? version : "1.0.0");
        metadata.put("sseEndpoint", "/sse");
        metadata.put("sseMessageEndpoint", "/mcp/message");
        metadata.put("protocol", "mcp-sse");
        metadata.put("serverName", mcpServiceName);
        metadata.put("serverId", serviceId);
        
        // 设置application：归属应用应该设置为服务名（mcpServiceName）
        // 这是 Nacos 服务列表中的服务名，用于标识服务归属
        String finalApplication = mcpServiceName;
        metadata.put("application", finalApplication);
        log.info("📦 Setting application for MCP service: {} -> {} (service name)", mcpServiceName, finalApplication);
        
        // 工具数量（而不是完整的工具名称列表，避免超过 Nacos metadata 1024 字节限制）
        // 工具列表已经存储在 Nacos 配置中心的 mcp-tools.json 中，可以通过 toolsDescriptionRef 获取
        metadata.put("tools.count", String.valueOf(tools.size()));
        
        // 检查 metadata 总大小，确保不超过 1024 字节
        int totalSize = calculateMetadataSize(metadata);
        if (totalSize > 1024) {
            log.warn("⚠️ Metadata size ({}) exceeds 1024 bytes, removing optional fields", totalSize);
            // 移除可选字段，只保留必要的
            metadata.remove("tools.names"); // 如果存在的话
            // 重新计算大小
            totalSize = calculateMetadataSize(metadata);
            if (totalSize > 1024) {
                log.error("❌ Metadata size ({}) still exceeds 1024 bytes after removing optional fields", totalSize);
                throw new RuntimeException("Metadata size exceeds Nacos limit (1024 bytes): " + totalSize);
            }
        }
        
        log.debug("📦 Metadata size: {} bytes (limit: 1024)", totalSize);
        
        // 计算server配置的MD5
        String serverDataId = serviceId + "-" + version + "-mcp-server.json";
        try {
            String serverConfig = null;
            if (useV3Api && nacosV3ApiService != null) {
                // 使用 v3 API 获取配置
                serverConfig = nacosV3ApiService.getConfig(serverDataId, SERVER_GROUP);
            } else {
                // 使用 SDK 获取配置（向后兼容）
                serverConfig = configService.getConfig(serverDataId, SERVER_GROUP, 5000);
            }
            
            if (serverConfig != null) {
                String md5 = calculateMd5(serverConfig);
                metadata.put("server.md5", md5);
            }
        } catch (Exception e) {
            log.warn("Failed to get server config for MD5 calculation", e);
        }
        
        instance.setMetadata(metadata);
        
        // 注册实例：优先使用 v3 API，否则使用 SDK
        if (useV3Api && nacosV3ApiService != null) {
            boolean success = nacosV3ApiService.registerInstance(
                    mcpServiceName, localIp, serverPort, serviceGroup, 
                    "DEFAULT", ephemeral, metadata);
            if (success) {
                log.info("✅ Registered instance to Nacos v3: {}:{} in group: {} (application: {}, ephemeral: {})", 
                        localIp, serverPort, serviceGroup, finalApplication != null ? finalApplication : "N/A", ephemeral);
            } else {
                log.warn("⚠️ Failed to register via v3 API, falling back to SDK");
                namingService.registerInstance(mcpServiceName, serviceGroup, instance);
            }
        } else {
            // 使用 SDK 注册（向后兼容）
            namingService.registerInstance(mcpServiceName, serviceGroup, instance);
            log.info("✅ Registered instance to Nacos (SDK): {}:{} in group: {} (application: {}, ephemeral: {})", 
                    localIp, serverPort, serviceGroup, finalApplication != null ? finalApplication : "N/A", ephemeral);
        }
    }
    
    /**
     * 从 Nacos 查询已注册的 MCP 服务列表
     * 
     * @return 已注册的服务名称集合
     */
    public Set<String> getRegisteredServicesFromNacos() {
        Set<String> services = new HashSet<>();
        try {
            // 注意：Nacos v3 客户端 API 不提供查询所有服务的接口
            // 这里需要维护一个已注册服务的列表，或者使用 Admin API
            // 暂时保留使用 SDK 的方式（向后兼容）
            if (useV3Api && nacosV3ApiService != null) {
                // v3 API 不支持查询所有服务，需要维护已注册服务列表
                // 这里暂时使用 SDK 方式，或者可以从配置中心读取服务列表
                log.warn("⚠️ Nacos v3 client API does not support querying all services, " +
                        "please maintain a list of registered services or use Admin API");
                // 可以尝试从已知的服务名称列表查询
            }
            
            // 使用 SDK 查询（向后兼容）
            ListView<String> servicesList = namingService.getServicesOfServer(1, Integer.MAX_VALUE, serviceGroup);
            if (servicesList != null && servicesList.getData() != null) {
                List<String> serviceNames = servicesList.getData();
                // 过滤出 MCP 服务（以 mcp- 开头或包含在 mcp-server group 中）
                for (String serviceName : serviceNames) {
                    try {
                        // 检查服务实例的 metadata，确认是 MCP 服务
                        List<Instance> instances;
                        if (useV3Api && nacosV3ApiService != null) {
                            List<Map<String, Object>> instanceList = nacosV3ApiService.getInstanceList(
                                    serviceName, serviceGroup, null, false);
                            instances = convertInstanceList(instanceList);
                        } else {
                            instances = namingService.getAllInstances(serviceName, serviceGroup);
                        }
                        
                        if (instances != null && !instances.isEmpty()) {
                            Instance instance = instances.get(0);
                            if (instance.getMetadata() != null) {
                                String protocol = instance.getMetadata().get("protocol");
                                if ("mcp-sse".equals(protocol) || serviceName.startsWith("mcp-")) {
                                    services.add(serviceName);
                                }
                            } else if (serviceName.startsWith("mcp-")) {
                                // 如果没有 metadata，但服务名以 mcp- 开头，也认为是 MCP 服务
                                services.add(serviceName);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to get instances for service: {} in group: {}", serviceName, serviceGroup, e);
                    }
                }
            }
            log.debug("Found {} MCP services from Nacos (group: {})", services.size(), serviceGroup);
        } catch (Exception e) {
            log.error("Failed to query registered services from Nacos", e);
            throw new RuntimeException("Failed to query services from Nacos", e);
        }
        return services;
    }
    
    /**
     * 根据 IP 和 Port 查找匹配的服务实例
     * 
     * @param serviceName 服务名称
     * @param ip IP 地址
     * @param port 端口号
     * @return 匹配的实例，如果没有找到则返回 null
     */
    public com.alibaba.nacos.api.naming.pojo.Instance findInstanceByIpAndPort(String serviceName, String ip, int port) {
        try {
            List<Instance> instances;
            if (useV3Api && nacosV3ApiService != null) {
                // 使用 v3 API 查询实例列表
                List<Map<String, Object>> instanceList = nacosV3ApiService.getInstanceList(
                        serviceName, serviceGroup, null, false);
                instances = convertInstanceList(instanceList);
            } else {
                // 使用 SDK 查询（向后兼容）
                instances = namingService.getAllInstances(serviceName, serviceGroup);
            }
            
            if (instances != null && !instances.isEmpty()) {
                for (Instance instance : instances) {
                    if (instance.getIp().equals(ip) && instance.getPort() == port) {
                        return instance;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to find instance for service: {} with IP: {} and Port: {}", serviceName, ip, port, e);
        }
        return null;
    }
    
    /**
     * 将 v3 API 返回的实例列表转换为 SDK Instance 对象
     */
    @SuppressWarnings("unchecked")
    private List<Instance> convertInstanceList(List<Map<String, Object>> instanceList) {
        List<Instance> instances = new ArrayList<>();
        for (Map<String, Object> instanceMap : instanceList) {
            Instance instance = new Instance();
            instance.setIp((String) instanceMap.get("ip"));
            instance.setPort(((Number) instanceMap.get("port")).intValue());
            instance.setWeight(((Number) instanceMap.getOrDefault("weight", 1.0)).doubleValue());
            instance.setHealthy((Boolean) instanceMap.getOrDefault("healthy", true));
            instance.setEnabled((Boolean) instanceMap.getOrDefault("enabled", true));
            instance.setEphemeral((Boolean) instanceMap.getOrDefault("ephemeral", true));
            instance.setClusterName((String) instanceMap.getOrDefault("clusterName", "DEFAULT"));
            instance.setServiceName((String) instanceMap.get("serviceName"));
            
            // 转换 metadata
            Object metadataObj = instanceMap.get("metadata");
            if (metadataObj instanceof Map) {
                Map<String, String> metadata = new HashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) metadataObj).entrySet()) {
                    metadata.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
                instance.setMetadata(metadata);
            }
            
            instances.add(instance);
        }
        return instances;
    }
    
    /**
     * 计算 metadata 的总大小（字节）
     */
    private int calculateMetadataSize(Map<String, String> metadata) {
        int totalSize = 0;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            // key + "=" + value + "\n" (Nacos 内部格式)
            totalSize += entry.getKey().getBytes(StandardCharsets.UTF_8).length;
            totalSize += 1; // "="
            totalSize += (entry.getValue() != null ? entry.getValue().getBytes(StandardCharsets.UTF_8).length : 0);
            totalSize += 1; // "\n"
        }
        return totalSize;
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
        // 例如：com.pajk.mcpmetainfo.core.demo.service.OrderService -> demo-service
        // 或者：com.example.user.service.UserService -> user-service
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
    public String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("Failed to get local IP, using 127.0.0.1", e);
            return "127.0.0.1";
        }
    }
    
    /**
     * 获取服务器端口
     */
    public int getServerPort() {
        return serverPort;
    }
    
    /**
     * 获取 NamingService
     */
    public NamingService getNamingService() {
        return namingService;
    }
    
    /**
     * 获取服务组
     */
    public String getServiceGroup() {
        return serviceGroup;
    }

    /**
     * 计算MD5
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
     * 注销MCP服务
     */
    public void deregisterMcpService(String serviceInterface, String version) {
        try {
            String mcpServiceName = buildMcpServiceName(serviceInterface, version);
            String localIp = getLocalIp();
            
            // 注销实例：优先使用 v3 API，否则使用 SDK
            if (useV3Api && nacosV3ApiService != null) {
                boolean success = nacosV3ApiService.deregisterInstance(
                        mcpServiceName, localIp, serverPort, serviceGroup);
                if (success) {
                    log.info("✅ Deregistered MCP service (v3 API): {} from Nacos", mcpServiceName);
                } else {
                    log.warn("⚠️ Failed to deregister via v3 API, falling back to SDK");
                    namingService.deregisterInstance(mcpServiceName, serviceGroup, localIp, serverPort);
                }
            } else {
                namingService.deregisterInstance(mcpServiceName, serviceGroup, localIp, serverPort);
                log.info("✅ Deregistered MCP service (SDK): {} from Nacos", mcpServiceName);
            }
        } catch (Exception e) {
            log.error("❌ Failed to deregister MCP service: {}", serviceInterface, e);
        }
    }
    
    /**
     * 注销虚拟项目MCP服务从Nacos（使用指定的服务名称）
     */
    public void deregisterVirtualProjectMcpService(String mcpServiceName, String version) {
        try {
            String localIp = getLocalIp();
            
            // 注销实例：优先使用 v3 API，否则使用 SDK
            if (useV3Api && nacosV3ApiService != null) {
                boolean success = nacosV3ApiService.deregisterInstance(
                        mcpServiceName, localIp, serverPort, serviceGroup);
                if (success) {
                    log.info("✅ Deregistered virtual project MCP service (v3 API): {} from Nacos", mcpServiceName);
                } else {
                    log.warn("⚠️ Failed to deregister via v3 API, falling back to SDK");
                    namingService.deregisterInstance(mcpServiceName, serviceGroup, localIp, serverPort);
                }
            } else {
                namingService.deregisterInstance(mcpServiceName, serviceGroup, localIp, serverPort);
                log.info("✅ Deregistered virtual project MCP service (SDK): {} from Nacos", mcpServiceName);
            }
        } catch (Exception e) {
            log.error("❌ Failed to deregister virtual project MCP service: {}", mcpServiceName, e);
        }
    }
}

