package com.pajk.mcpmetainfo.core.service;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import com.pajk.mcpmetainfo.persistence.entity.DubboMethodParameterEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceMethodEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import com.pajk.mcpmetainfo.core.util.McpToolSchemaGenerator;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.constant.AiConstants;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Comparator;
import javax.annotation.PostConstruct;

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
    private final ZkInfoNodeDiscoveryService zkInfoNodeDiscoveryService; // zkInfo 节点发现服务
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final NacosMcpHttpApiService nacosMcpHttpApiService; // Nacos MCP HTTP API 服务

    @Value("${nacos.v3.api.enabled:true}")
    private boolean useV3Api; // 是否使用 v3 API
    
    @Value("${spring.cloud.nacos.discovery.server-addr:127.0.0.1:8848}")
    private String nacosServerAddr;
    
    @Value("${spring.cloud.nacos.discovery.username:}")
    private String nacosUsername;
    
    @Value("${spring.cloud.nacos.discovery.password:}")
    private String nacosPassword;
    
    @Value("${spring.cloud.nacos.discovery.namespace:public}")
    private String nacosNamespace;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.pajk.mcpmetainfo.core.util.McpToolSchemaGenerator mcpToolSchemaGenerator;
    
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.pajk.mcpmetainfo.core.util.EnhancedMcpToolGenerator enhancedMcpToolGenerator;
    
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.pajk.mcpmetainfo.core.service.DubboServiceDbService dubboServiceDbService;
    
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.pajk.mcpmetainfo.core.service.DubboServiceMethodService dubboServiceMethodService;

    @Value("${server.port:9091}")
    private int serverPort;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Value("${nacos.registry.service-group:mcp-server}")
    private String serviceGroup;

    @Value("${nacos.registry.enabled:true}")
    private boolean registryEnabled;

    // 配置组常量
    private static final String SERVER_GROUP = "mcp-server";
    private static final String TOOLS_GROUP = "mcp-tools";
    private static final String VERSIONS_GROUP = "mcp-server-versions";
    
    @PostConstruct
    public void init() {
        if (!registryEnabled) {
            log.info("⚠️ Nacos registry is disabled (registryEnabled=false)");
            return;
        }
        log.info("🚀 Initializing NacosMcpRegistrationService with server: {}", nacosServerAddr);
        log.info("✅ Will use NacosMcpHttpApiService for MCP registration");
    }

    /**
     * 检查服务是否存在
     * 
     * @param serviceName 服务名称
     * @return true if service exists
     */
    public boolean isServiceExists(String serviceName) {
        if (!registryEnabled) {
            return false;
        }
        try {
            if (useV3Api && nacosV3ApiService != null) {
                // Using V3 API
                List<Map<String, Object>> instances = nacosV3ApiService.getInstanceList(serviceName, serviceGroup, null, false);
                return instances != null && !instances.isEmpty();
            } else {
                // Fallback to SDK
                List<Instance> instances = namingService.getAllInstances(serviceName, serviceGroup);
                return instances != null && !instances.isEmpty();
            }
        } catch (Exception e) {
            log.warn("Failed to check if service exists: {}", serviceName, e);
            return false;
        }
    }

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
            
            
            // 4. 发布到 Nacos MCP 管理（使用 HTTP API）
            McpServerBasicInfo serverBasicInfo = buildMcpServerBasicInfo(mcpServiceName, version, null);

            McpToolSpecification toolSpec = buildMcpToolSpecification(tools);
            McpEndpointSpec endpointSpec = buildMcpEndpointSpec(mcpServiceName);
            
            boolean publishMcpSuccess = nacosMcpHttpApiService.createMcpServer(mcpServiceName, serverBasicInfo, toolSpec, endpointSpec);
            
            // 同时也发布配置到 ConfigService（向后兼容）
            String appName = extractApplicationFromProviders(providers);
            String serverContent = publishConfigsToNacos(serviceId, mcpServiceName, version, tools, appName);
            
            // 5. 注册服务实例到Nacos服务列表
            registerInstanceToNacos(mcpServiceName, serviceId, version, tools, providers, null, true, serverContent);
            
            log.info("✅ Successfully registered Dubbo MCP service: {} to Nacos (HTTP API: {}, ConfigService: OK)", 
                    mcpServiceName, publishMcpSuccess ? "SUCCESS" : "FAILED");
            
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
            
            
            // 3. 发布到 Nacos MCP 管理（使用 HTTP API）
            McpServerBasicInfo serverBasicInfo = buildMcpServerBasicInfo(mcpServiceName, version, null);

            McpToolSpecification toolSpec = buildMcpToolSpecification(tools);
            McpEndpointSpec endpointSpec = buildMcpEndpointSpec(mcpServiceName);
            
            boolean publishMcpSuccess = nacosMcpHttpApiService.createMcpServer(mcpServiceName, serverBasicInfo, toolSpec, endpointSpec);
            
            // 4.发布配置到 ConfigService
            String serverContent = publishConfigsToNacos(serviceId, mcpServiceName, version, tools, mcpServiceName);
            
            // 5. 注册服务实例到Nacos服务列表
            registerInstancesToNacosForAllNodes(mcpServiceName, serviceId, version, tools, providers, mcpServiceName, true, serverContent);
            
            log.info("✅ Successfully registered virtual project MCP service: {} to Nacos (HTTP API: {}, ConfigService: OK)", 
                    mcpServiceName, publishMcpSuccess ? "SUCCESS" : "FAILED");
            
        } catch (Exception e) {
            log.error("❌ Failed to register virtual project MCP service: {}", mcpServiceName, e);
            throw new RuntimeException("Failed to register virtual project MCP service to Nacos", e);
        }
    }

    /**
     * 将虚拟项目注册为MCP服务到Nacos（使用已有的配置对象）
     * 
     * @param config 虚拟项目配置
     */
    public void registerVirtualProject(com.pajk.mcpmetainfo.core.model.wizard.VirtualProjectConfig config) {
        if (!registryEnabled) {
            log.debug("Nacos registry is disabled, skip registration");
            return;
        }

        try {
            String mcpServiceName = config.getMcpServiceName();
            String projectName = config.getProjectName();
            
            // 确保服务名以 virtual- 开头
            if (mcpServiceName == null || mcpServiceName.isEmpty()) {
                mcpServiceName = "virtual-" + projectName.toLowerCase().replace(" ", "-");
            } else if (!mcpServiceName.startsWith("virtual-")) {
                mcpServiceName = "virtual-" + mcpServiceName.toLowerCase().replace(" ", "-");
            }
            
            String version = "1.0.0";
            String virtualProjectName = projectName;
            
            // 1. 生成服务ID（UUID）
            String serviceId = generateServiceId(mcpServiceName, version);
            
            log.info("🚀 Registering virtual project to Nacos: {} (project: {})", 
                    mcpServiceName, virtualProjectName);
            
            // 2. 工具列表已经在 config 中（用户编辑后的）
            List<Map<String, Object>> tools = config.getTools();
            
            // 2.5 预处理工具列表：确保 inputSchema 是 Map 而不是 JSON 字符串
            // 这是为了确保发布到 Nacos Config 的配置是完全结构化的 JSON
            if (tools != null) {
                for (Map<String, Object> toolMap : tools) {
                    Object inputSchema = toolMap.get("inputSchema");
                    if (inputSchema instanceof String) {
                        try {
                            Map<String, Object> schemaMap = objectMapper.readValue((String) inputSchema, Map.class);
                            toolMap.put("inputSchema", schemaMap);
                        } catch (Exception e) {
                            log.warn("Failed to parse inputSchema JSON string during registration for tool {}: {}", 
                                    toolMap.get("name"), e.getMessage());
                        }
                    }
                }
            }
            
            // 3. 发布到 Nacos MCP 管理（使用 HTTP API）
            McpServerBasicInfo serverBasicInfo = buildMcpServerBasicInfo(mcpServiceName, version, config.getDescription());
            McpToolSpecification toolSpec = buildMcpToolSpecification(tools);
            McpEndpointSpec endpointSpec = buildMcpEndpointSpec(mcpServiceName);
            
            boolean publishMcpSuccess = nacosMcpHttpApiService.createMcpServer(mcpServiceName, serverBasicInfo, toolSpec, endpointSpec);

            
            // 同时也发布配置到 ConfigService（向后兼容）
            String serverContent = publishConfigsToNacos(serviceId, mcpServiceName, version, tools, mcpServiceName);
            
            // 4. 注册所有活跃的 zkInfo 节点作为实例
            // 这里传入空 Provider 列表，因为元数据仅依赖于 mcpServiceName 和 tools 数量
            registerInstancesToNacosForAllNodes(mcpServiceName, serviceId, version, tools, new ArrayList<>(), mcpServiceName, false, serverContent);
            
            log.info("✅ Successfully registered virtual project MCP service: {} to Nacos", mcpServiceName);
            
        } catch (Exception e) {
            log.error("❌ Failed to register virtual project MCP: {}", config.getProjectName(), e);
            throw new RuntimeException("Failed to register virtual project to Nacos", e);
        }
    }


    /**
     * 更新服务状态
     * 
     * @param serviceInterface 服务接口名
     * @param version 服务版本
     * @param isOnline 是否在线
     */
    public void updateServiceStatus(String serviceInterface, String version, boolean isOnline) {
        if (!registryEnabled) {
            return;
        }

        try {
            String mcpServiceName = buildMcpServiceName(serviceInterface, version);
            
            // 获取本机IP
            String localIp = getLocalIp();
            
            // 查找并更新实例
            List<Instance> instances = namingService.getAllInstances(mcpServiceName, serviceGroup);
            boolean found = false;
            for (Instance instance : instances) {
                if (instance.getIp().equals(localIp) && instance.getPort() == serverPort) {
                    found = true;
                    if (isOnline) {
                        instance.setEnabled(true);
                        instance.setHealthy(true);
                        namingService.registerInstance(mcpServiceName, serviceGroup, instance);
                        log.info("✅ Updated service status: {} -> online=true", mcpServiceName);
                    } else {
                        // 如果下线，则注销实例，彻底移除多余的旧 IP
                        try {
                            if (useV3Api && nacosV3ApiService != null) {
                                nacosV3ApiService.deregisterInstance(mcpServiceName, localIp, serverPort, serviceGroup, instance.isEphemeral());
                            } else {
                                namingService.deregisterInstance(mcpServiceName, serviceGroup, localIp, serverPort);
                            }
                            log.info("✅ Successfully deregistered offline service instance: {} from Nacos", mcpServiceName);
                        } catch (Exception e) {
                            log.warn("⚠️ Failed to deregister instance, disabling it instead: {}", e.getMessage());
                            instance.setEnabled(false);
                            instance.setHealthy(false);
                            namingService.registerInstance(mcpServiceName, serviceGroup, instance);
                        }
                    }
                    break;
                }
            }
            
            if (!found && isOnline) {
                // 如果没找到实例但要求上线，可能需要重新发起完整注册
                log.warn("⚠️ No instance found for service {} to update, it might have been deregistered.", mcpServiceName);
            }
        } catch (Exception e) {
            log.error("Failed to update service status: {}", serviceInterface, e);
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
                    String dbDesc = mcpToolSchemaGenerator.getMethodDescriptionFromDb(provider.getInterfaceName(), methodName);
                    tool.put("description", (dbDesc != null && !dbDesc.isBlank())
                            ? dbDesc
                            : String.format("调用 %s 服务的 %s 方法", provider.getInterfaceName(), methodName));
                    
                    // 根据数据库中持久化的参数信息生成 inputSchema
                    Map<String, Object> inputSchema = createInputSchemaFromDatabase(
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
     * 从数据库中持久化的 DubboMethodParameterEntity 数据创建 inputSchema
     * 
     * @param interfaceName 接口全限定名
     * @param methodName 方法名
     * @return inputSchema Map
     */
    private Map<String, Object> createInputSchemaFromDatabase(String interfaceName, String methodName) {
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        
        try {
            // 如果数据库服务不可用，优先使用 EnhancedMcpToolGenerator（反射），否则回退到 mcpToolSchemaGenerator
            if (dubboServiceDbService == null || dubboServiceMethodService == null) {
                log.debug("⚠️ Database services not available, trying EnhancedMcpToolGenerator (reflection)");
                if (enhancedMcpToolGenerator != null) {
                    try {
                        Map<String, Object> enhancedTool = enhancedMcpToolGenerator.generateEnhancedTool(interfaceName, methodName);
                        if (enhancedTool != null && enhancedTool.containsKey("inputSchema")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> reflectionSchema = (Map<String, Object>) enhancedTool.get("inputSchema");
                            if (reflectionSchema != null && reflectionSchema.containsKey("properties")) {
                                log.info("✅ Successfully generated inputSchema via reflection for {}.{}", 
                                        interfaceName, methodName);
                                return reflectionSchema;
                            }
                        }
                    } catch (Exception e) {
                        log.debug("⚠️ EnhancedMcpToolGenerator failed for {}.{}: {}, falling back to mcpToolSchemaGenerator", 
                                interfaceName, methodName, e.getMessage());
                    }
                }
                log.debug("⚠️ Falling back to mcpToolSchemaGenerator");
                return mcpToolSchemaGenerator.createInputSchemaFromMethod(interfaceName, methodName);
            }
            
            // 1. 根据 interfaceName 查找服务
            DubboServiceEntity service = dubboServiceDbService.findByInterfaceName(interfaceName);
            if (service == null) {
                log.debug("⚠️ Service not found in database: {}, trying EnhancedMcpToolGenerator (reflection)", 
                        interfaceName);
                if (enhancedMcpToolGenerator != null) {
                    try {
                        Map<String, Object> enhancedTool = enhancedMcpToolGenerator.generateEnhancedTool(interfaceName, methodName);
                        if (enhancedTool != null && enhancedTool.containsKey("inputSchema")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> reflectionSchema = (Map<String, Object>) enhancedTool.get("inputSchema");
                            if (reflectionSchema != null && reflectionSchema.containsKey("properties")) {
                                log.info("✅ Successfully generated inputSchema via reflection for {}.{}", 
                                        interfaceName, methodName);
                                return reflectionSchema;
                            }
                        }
                    } catch (Exception e) {
                        log.debug("⚠️ EnhancedMcpToolGenerator failed for {}.{}: {}, falling back to mcpToolSchemaGenerator", 
                                interfaceName, methodName, e.getMessage());
                    }
                }
                log.debug("⚠️ Falling back to mcpToolSchemaGenerator");
                return mcpToolSchemaGenerator.createInputSchemaFromMethod(interfaceName, methodName);
            }
            
            Long serviceId = service.getId();
            log.debug("✅ Found service in database: {} (ID: {})", interfaceName, serviceId);
            
            // 2. 根据 serviceId 和 methodName 查找方法
            DubboServiceMethodEntity method = dubboServiceMethodService.findByServiceIdAndMethodName(
                    serviceId, methodName);
            if (method == null) {
                log.debug("⚠️ Method not found in database: {}.{}, trying EnhancedMcpToolGenerator (reflection)", 
                        interfaceName, methodName);
                if (enhancedMcpToolGenerator != null) {
                    try {
                        Map<String, Object> enhancedTool = enhancedMcpToolGenerator.generateEnhancedTool(interfaceName, methodName);
                        if (enhancedTool != null && enhancedTool.containsKey("inputSchema")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> reflectionSchema = (Map<String, Object>) enhancedTool.get("inputSchema");
                            if (reflectionSchema != null && reflectionSchema.containsKey("properties")) {
                                log.info("✅ Successfully generated inputSchema via reflection for {}.{}", 
                                        interfaceName, methodName);
                                return reflectionSchema;
                            }
                        }
                    } catch (Exception e) {
                        log.debug("⚠️ EnhancedMcpToolGenerator failed for {}.{}: {}, falling back to mcpToolSchemaGenerator", 
                                interfaceName, methodName, e.getMessage());
                    }
                }
                log.debug("⚠️ Falling back to mcpToolSchemaGenerator");
                return mcpToolSchemaGenerator.createInputSchemaFromMethod(interfaceName, methodName);
            }
            
            log.debug("✅ Found method in database: {}.{} (ID: {})", interfaceName, methodName, method.getId());
            
            // 3. 根据 methodId 查找参数列表
            List<DubboMethodParameterEntity> parameters = dubboServiceMethodService.findParametersByMethodId(
                    method.getId());
            
            if (parameters == null || parameters.isEmpty()) {
                log.debug("⚠️ No parameters found in database for {}.{}, trying EnhancedMcpToolGenerator (reflection)", 
                        interfaceName, methodName);
                // 尝试使用反射获取参数信息
                if (enhancedMcpToolGenerator != null) {
                    try {
                        Map<String, Object> enhancedTool = enhancedMcpToolGenerator.generateEnhancedTool(interfaceName, methodName);
                        if (enhancedTool != null && enhancedTool.containsKey("inputSchema")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> reflectionSchema = (Map<String, Object>) enhancedTool.get("inputSchema");
                            if (reflectionSchema != null && reflectionSchema.containsKey("properties")) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> reflectionProperties = (Map<String, Object>) reflectionSchema.get("properties");
                                if (reflectionProperties != null && !reflectionProperties.isEmpty()) {
                                    log.info("✅ Successfully generated inputSchema via reflection for {}.{} ({} parameters)", 
                                            interfaceName, methodName, reflectionProperties.size());
                                    return reflectionSchema;
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("⚠️ EnhancedMcpToolGenerator failed for {}.{}: {}", 
                                interfaceName, methodName, e.getMessage());
                    }
                }
                log.debug("⚠️ Creating schema without parameters");
                // 无参数方法，properties 为空
            } else {
                // 按 parameterOrder 排序
                parameters.sort(Comparator.comparing(DubboMethodParameterEntity::getParameterOrder));
                
                log.debug("✅ Found {} parameters in database for {}.{}", 
                        parameters.size(), interfaceName, methodName);
                
                // 4. 为每个参数创建属性
                for (DubboMethodParameterEntity param : parameters) {
                    String paramName = param.getParameterName();
                    String paramType = param.getParameterType();
                    String paramDescription = param.getParameterDescription();
                    String paramSchemaJson = param.getParameterSchemaJson();
                    
                    // 如果参数名为空，使用默认名称
                    if (paramName == null || paramName.isEmpty()) {
                        paramName = "param" + param.getParameterOrder();
                    }
                    
                    log.debug("    Parameter[{}]: {} ({})", param.getParameterOrder(), paramName, paramType);
                    
                    Map<String, Object> paramProperty = null;
                    boolean paramRequired = true;

                    // If structured schema exists, use it (preferred)
                    if (paramSchemaJson != null && !paramSchemaJson.isBlank()) {
                        try {
                            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(paramSchemaJson);
                            com.fasterxml.jackson.databind.JsonNode requiredNode = root.get("required");
                            if (requiredNode != null && requiredNode.isBoolean()) {
                                paramRequired = requiredNode.asBoolean(true);
                            }
                            com.fasterxml.jackson.databind.JsonNode schemaNode = root.get("jsonSchema");
                            if (schemaNode == null || schemaNode.isMissingNode() || schemaNode.isNull()) {
                                schemaNode = root.get("schema");
                            }
                            if (schemaNode != null && schemaNode.isObject()) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> schemaMap = objectMapper.convertValue(schemaNode, Map.class);
                                paramProperty = new HashMap<>(schemaMap);
                            }
                        } catch (Exception ex) {
                            log.debug("⚠️ Failed to parse parameter_schema_json for {}.{} param={} error={}",
                                    interfaceName, methodName, paramName, ex.getMessage());
                        }
                    }

                    if (paramProperty == null) {
                        paramProperty = new HashMap<>();
                        // 根据参数类型设置 type
                        String jsonType = getJsonTypeFromJavaTypeName(paramType);
                        paramProperty.put("type", jsonType);

                        // 如果是数组或集合类型，设置 items
                        if (paramType != null && (paramType.endsWith("[]") || paramType.contains("List") ||
                                paramType.contains("Set") || paramType.contains("Collection"))) {
                            Map<String, Object> items = new HashMap<>();
                            items.put("type", "any");
                            paramProperty.put("items", items);
                        }
                    }
                    
                    // 设置描述：优先使用数据库中的描述，否则根据类型生成
                    String finalDesc = (paramDescription != null && !paramDescription.isEmpty())
                            ? paramDescription
                            : getParameterDescriptionFromType(paramType, paramName);
                    if (!paramProperty.containsKey("description") || paramProperty.get("description") == null ||
                            String.valueOf(paramProperty.get("description")).isBlank()) {
                        paramProperty.put("description", finalDesc);
                    }
                    
                    properties.put(paramName, paramProperty);
                    if (paramRequired) {
                        required.add(paramName);
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ Error creating inputSchema from database for {}.{}: {}, trying EnhancedMcpToolGenerator (reflection)", 
                    interfaceName, methodName, e.getMessage(), e);
            // 发生错误时，优先尝试使用 EnhancedMcpToolGenerator（反射）
            if (enhancedMcpToolGenerator != null) {
                try {
                    Map<String, Object> enhancedTool = enhancedMcpToolGenerator.generateEnhancedTool(interfaceName, methodName);
                    if (enhancedTool != null && enhancedTool.containsKey("inputSchema")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> reflectionSchema = (Map<String, Object>) enhancedTool.get("inputSchema");
                        if (reflectionSchema != null && reflectionSchema.containsKey("properties")) {
                            log.info("✅ Successfully generated inputSchema via reflection for {}.{}", 
                                    interfaceName, methodName);
                            return reflectionSchema;
                        }
                    }
                } catch (Exception reflectionException) {
                    log.debug("⚠️ EnhancedMcpToolGenerator also failed for {}.{}: {}, falling back to mcpToolSchemaGenerator", 
                            interfaceName, methodName, reflectionException.getMessage());
                }
            }
            // 最后回退到使用 mcpToolSchemaGenerator
            log.debug("⚠️ Falling back to mcpToolSchemaGenerator");
            return mcpToolSchemaGenerator.createInputSchemaFromMethod(interfaceName, methodName);
        }
        
        inputSchema.put("properties", properties);
        if (!required.isEmpty()) {
            inputSchema.put("required", required);
        }
        
        return inputSchema;
    }
    
    /**
     * 获取参数描述（基于类型名）
     */
    private String getParameterDescriptionFromType(String typeName, String paramName) {
        if (typeName == null || typeName.isEmpty()) {
            return String.format("参数 %s", paramName);
        }
        String simpleType = typeName.contains(".") ? 
                typeName.substring(typeName.lastIndexOf(".") + 1) : typeName;
        
        // 添加 (类型: <typeName>) 格式，以便 McpProtocolService 可以提取它
        return String.format("%s 类型的参数 %s (类型: %s)", simpleType, paramName, typeName);
    }
    
    /**
     * 将 Java 类型名转换为 JSON Schema 类型
     */
    private String getJsonTypeFromJavaTypeName(String javaTypeName) {
        if (javaTypeName == null || javaTypeName.isEmpty()) {
            return "any";
        }
        
        // 基本类型
        if (javaTypeName.equals("boolean") || javaTypeName.equals("java.lang.Boolean")) {
            return "boolean";
        } else if (javaTypeName.equals("int") || javaTypeName.equals("java.lang.Integer") ||
                   javaTypeName.equals("long") || javaTypeName.equals("java.lang.Long") ||
                   javaTypeName.equals("short") || javaTypeName.equals("java.lang.Short") ||
                   javaTypeName.equals("byte") || javaTypeName.equals("java.lang.Byte")) {
            return "integer";
        } else if (javaTypeName.equals("float") || javaTypeName.equals("java.lang.Float") ||
                   javaTypeName.equals("double") || javaTypeName.equals("java.lang.Double")) {
            return "number";
        } else if (javaTypeName.equals("java.lang.String") || javaTypeName.equals("String") ||
                   javaTypeName.equals("char") || javaTypeName.equals("java.lang.Character")) {
            return "string";
        } else if (javaTypeName.endsWith("[]") || javaTypeName.contains("List") || 
                   javaTypeName.contains("Set") || javaTypeName.contains("Collection")) {
            return "array";
        } else if (javaTypeName.contains("Map")) {
            return "object";
        } else {
            // 其他对象类型
            return "object";
        }
    }

    /**
     * 发布配置到Nacos配置中心
     * 需要创建3个配置：tools, versions, server
     * 
     * @return 服务器配置内容（用于计算 MD5）
     */
    private String publishConfigsToNacos(String serviceId, String mcpServiceName, 
                                       String version, List<Map<String, Object>> tools, String appName) 
            throws NacosException {
        
        // 1. 发布 mcp-tools.json（指定格式为 JSON）
        String toolsDataId = serviceId + "-" + version + "-mcp-tools.json";
        String toolsContent = createToolsConfig(tools);
        boolean toolsPublished = false;
        
        // 尝试使用 HTTP API 发布（支持 type=json 和 appName）
        if (nacosV3ApiService != null) {
            toolsPublished = nacosV3ApiService.publishConfigV1(toolsDataId, TOOLS_GROUP, toolsContent, "json", appName);
        }
        
        if (!toolsPublished) {
            // 回退到 SDK
            configService.publishConfig(toolsDataId, TOOLS_GROUP, toolsContent);
            log.info("📝 Published tools config via SDK: {}", toolsDataId);
        }
        
        // 2. 发布 mcp-versions.json（指定格式为 JSON）
        String versionsDataId = serviceId + "-mcp-versions.json";
        String versionsContent = createVersionsConfig(serviceId, mcpServiceName, version);
        boolean versionsPublished = false;
        
        if (nacosV3ApiService != null) {
            versionsPublished = nacosV3ApiService.publishConfigV1(versionsDataId, VERSIONS_GROUP, versionsContent, "json", appName);
        }
        
        if (!versionsPublished) {
            configService.publishConfig(versionsDataId, VERSIONS_GROUP, versionsContent);
            log.info("📝 Published versions config via SDK: {}", versionsDataId);
        }
        
        // 3. 发布 mcp-server.json（指定格式为 JSON）
        String serverDataId = serviceId + "-" + version + "-mcp-server.json";
        String serverContent = createServerConfig(serviceId, mcpServiceName, version, toolsDataId);
        boolean serverPublished = false;
        
        if (nacosV3ApiService != null) {
            serverPublished = nacosV3ApiService.publishConfigV1(serverDataId, SERVER_GROUP, serverContent, "json", appName);
        }
        
        if (!serverPublished) {
            configService.publishConfig(serverDataId, SERVER_GROUP, serverContent);
            log.info("📝 Published server config via SDK: {}", serverDataId);
        }
        
        return serverContent;
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
     * 将 Map<String, Object> 形式的工具列表转换为 McpTool 对象列表
     */
    private List<McpTool> createMcpToolList(List<Map<String, Object>> toolsMapList) {
        List<McpTool> mcpTools = new ArrayList<>();
        if (toolsMapList == null || toolsMapList.isEmpty()) {
            return mcpTools;
        }
        
        for (Map<String, Object> toolMap : toolsMapList) {
            try {
                McpTool tool = new McpTool();
                String name = (String) toolMap.get("name");
                if (name == null) {
                    name = (String) toolMap.get("toolName");
                }
                tool.setName(name);
                tool.setDescription((String) toolMap.get("description"));

                
                // inputSchema 是 Map，需要根据 McpTool 定义处理
                // McpTool 的 inputSchema 字段通常是一个 JsonNode 或 Map
                // 在 Nacos SDK 中，McpTool.setInputSchema() 的参数类型取决于 SDK 版本
                // 假设是 Object 或 Map<String, Object>
                // 如果 SDK 要求特定类型，可能需要转换
                // McpTool.setInputSchema() requires Map<String, Object> in Nacos 3.x
                Object inputSchema = toolMap.get("inputSchema");
                if (inputSchema instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> inputSchemaMap = (Map<String, Object>) inputSchema;
                    tool.setInputSchema(inputSchemaMap);
                } else if (inputSchema instanceof String) {
                    // 如果是 JSON 字符串，解析为 Map
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> schemaMap = mapper.readValue((String) inputSchema, Map.class);
                        tool.setInputSchema(schemaMap);
                    } catch (Exception e) {
                        log.warn("Failed to parse inputSchema JSON string for tool {}: {}", name, e.getMessage());
                    }
                } else if (inputSchema != null) {
                    log.warn("Expected inputSchema to be Map or String but got: {}", inputSchema.getClass().getName());
                }

                
                mcpTools.add(tool);
            } catch (Exception e) {
                log.warn("Failed to convert tool map to McpTool: {}", toolMap, e);
            }
        }
        return mcpTools;
    }
    
    /**
     * 构建 MCP Server 基本信息
     */
    private McpServerBasicInfo buildMcpServerBasicInfo(String mcpServiceName, String version, String description) {
        McpServerBasicInfo serverBasicInfo = new McpServerBasicInfo();
        serverBasicInfo.setName(mcpServiceName);
        serverBasicInfo.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        serverBasicInfo.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        serverBasicInfo.setDescription(description != null && !description.isEmpty() ? description : "Dubbo service converted to MCP: " + mcpServiceName);

        
        // 设置版本详情
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion(version);
        serverBasicInfo.setVersionDetail(versionDetail);
        
        // 设置远程服务配置
        McpServerRemoteServiceConfig remoteServerConfig = new McpServerRemoteServiceConfig();
        remoteServerConfig.setExportPath("/sse");
        
        McpServiceRef serviceRef = new McpServiceRef();
        serviceRef.setNamespaceId(nacosNamespace != null ? nacosNamespace : "public");
        serviceRef.setGroupName(serviceGroup);
        serviceRef.setServiceName(mcpServiceName);
        remoteServerConfig.setServiceRef(serviceRef);
        
        serverBasicInfo.setRemoteServerConfig(remoteServerConfig);
        
        return serverBasicInfo;
    }

    /**
     * 构建 MCP 工具规格
     */
    private McpToolSpecification buildMcpToolSpecification(List<Map<String, Object>> tools) {
        McpToolSpecification toolSpec = new McpToolSpecification();
        List<McpTool> mcpTools = createMcpToolList(tools);
        toolSpec.setTools(mcpTools);
        return toolSpec;
    }

    /**
     * 构建 MCP 端点规格
     */
    private McpEndpointSpec buildMcpEndpointSpec(String mcpServiceName) {
        McpEndpointSpec endpointSpec = new McpEndpointSpec();
        endpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF);
        
        Map<String, String> endpointData = new HashMap<>();
        endpointData.put("namespaceId", nacosNamespace != null ? nacosNamespace : "public");
        endpointData.put("groupName", serviceGroup);
        endpointData.put("serviceName", mcpServiceName);
        endpointSpec.setData(endpointData);
        
        return endpointSpec;
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
                                        String application, boolean ephemeral, String serverConfigContent) 
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
        
        // 对于虚拟项目，记录节点类型日志
        if (ephemeral) {
            log.info("📌 Registering virtual project as ephemeral node (ephemeral=true): {} - will be auto-deleted when zkInfo stops", mcpServiceName);
        } else {
            log.info("📌 Registering virtual project as persistent node (ephemeral=false): {}", mcpServiceName);
        }
        
        // 设置元数据（MCP 客户端初始化所需的关键字段）
        Map<String, String> metadata = new HashMap<>();
        
        // 基础信息
        metadata.put("version", version != null ? version : "1.0.0");
        metadata.put("protocol", "mcp-sse");
        metadata.put("scheme", "http"); // 添加 scheme 字段（MCP 客户端需要）
        
        // SSE 端点信息（MCP 客户端初始化时需要使用）
        // 注意：endpoint 应该包含完整的路径，客户端会使用 baseUrl + endpoint
        // 对于虚拟项目，sseEndpoint 应该是 /sse/{mcpServiceName}
        // 确保与用户查询时的期望路径一致
        String sseEndpoint = "/sse/" + mcpServiceName;
        metadata.put("sseEndpoint", sseEndpoint);
        // Message 端点：使用 /mcp/{serviceName}/message 格式（与 SseController 保持一致）
        String sseMessageEndpoint = "/mcp/" + mcpServiceName + "/message";
        metadata.put("sseMessageEndpoint", sseMessageEndpoint);
        
        // 添加 context-path 信息（如果存在），供 mcp-router-v3 使用
        // 注意：context-path 可能在不同环境下不同，这里存储的是配置的默认值
        // 实际使用时，zkInfo 会根据请求动态构建完整的 URL
        if (contextPath != null && !contextPath.isEmpty() && !contextPath.equals("/")) {
            // 规范化 context-path：确保以 / 开头，但不以 / 结尾
            String normalizedContextPath = contextPath.trim();
            if (!normalizedContextPath.startsWith("/")) {
                normalizedContextPath = "/" + normalizedContextPath;
            }
            if (normalizedContextPath.endsWith("/") && normalizedContextPath.length() > 1) {
                normalizedContextPath = normalizedContextPath.substring(0, normalizedContextPath.length() - 1);
            }
            metadata.put("contextPath", normalizedContextPath);
            log.debug("📦 Added context-path to metadata: {} for service: {}", normalizedContextPath, mcpServiceName);
        }
        
        // 服务标识
        metadata.put("serverName", mcpServiceName);
        metadata.put("serverId", serviceId);
        
        // 设置application：如果是虚拟项目，直接使用 mcpServiceName 确保一致性
        // 否则使用传入的 application
        String finalApplication = application;
        if (mcpServiceName.startsWith("virtual-")) {
            finalApplication = mcpServiceName;
        } else if (finalApplication == null || finalApplication.isEmpty()) {
            finalApplication = mcpServiceName;
        }
        metadata.put("application", finalApplication);
        log.info("📦 Setting application for MCP service: {} -> {} (virtual project: {})", 
                mcpServiceName, finalApplication, application != null ? application : "N/A");
        
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
        
        // 记录元数据内容（用于调试）
        log.info("📦 Metadata for virtual project {}: version={}, sseEndpoint={}, sseMessageEndpoint={}, protocol={}, serverName={}, serverId={}, application={}, tools.count={}", 
                mcpServiceName,
                metadata.get("version"),
                metadata.get("sseEndpoint"),
                metadata.get("sseMessageEndpoint"),
                metadata.get("protocol"),
                metadata.get("serverName"),
                metadata.get("serverId"),
                metadata.get("application"),
                metadata.get("tools.count"));
        
        // 计算server配置的MD5
        if (serverConfigContent != null && !serverConfigContent.isEmpty()) {
            String md5 = calculateMd5(serverConfigContent);
            metadata.put("server.md5", md5);
            log.debug("📦 Calculated MD5 from provided content for {}: {}", mcpServiceName, md5);
        } else {
            // 如果 content 为空（例如通过 AiMaintainerService 注册），则跳过 MD5 计算
            // 或者如果之前就没有发布 Config，那么也不应该计算 MD5
            log.debug("⚠️ Skipping server.md5 calculation for {} as serverConfigContent is null (likely using AiMaintainerService)", mcpServiceName);
        }
        
        // 确保 metadata 不为空
        if (metadata == null || metadata.isEmpty()) {
            log.error("❌ Metadata is empty! Cannot register instance without metadata.");
            throw new RuntimeException("Metadata is required for MCP service registration");
        }
        
        // 记录元数据内容（用于调试和验证）
        log.info("📦 Registering instance with {} metadata fields: {}", metadata.size(), String.join(", ", metadata.keySet()));
        log.debug("📦 Metadata details for {}: {}", mcpServiceName, metadata);
        
        instance.setMetadata(metadata);
        
        // 注册实例：优先使用 SDK（因为 SDK 的 metadata 传递更可靠），v3 API 作为备选
        // 注意：Nacos SDK 的 registerInstance 方法会正确处理 metadata
        // 而 v3 API 的 metadata 传递可能存在问题，所以优先使用 SDK
        try {
            // 使用 SDK 注册（确保 metadata 正确传递）
            namingService.registerInstance(mcpServiceName, serviceGroup, instance);
            log.info("✅ Registered instance to Nacos (SDK): {}:{} in group: {} (application: {}, ephemeral: {}, metadata: {} fields)", 
                    localIp, serverPort, serviceGroup, finalApplication != null ? finalApplication : "N/A", ephemeral, metadata.size());
            log.info("📦 Registered metadata keys: {}", String.join(", ", metadata.keySet()));
            
            // 验证注册后的实例是否包含 metadata（通过查询实例列表）
            try {
                List<com.alibaba.nacos.api.naming.pojo.Instance> instances = namingService.getAllInstances(mcpServiceName, serviceGroup);
                for (com.alibaba.nacos.api.naming.pojo.Instance registeredInstance : instances) {
                    if (localIp.equals(registeredInstance.getIp()) && serverPort == registeredInstance.getPort()) {
                        Map<String, String> registeredMetadata = registeredInstance.getMetadata();
                        if (registeredMetadata != null && !registeredMetadata.isEmpty()) {
                            log.info("✅ Verified: Instance metadata in Nacos: {} fields - {}", 
                                    registeredMetadata.size(), String.join(", ", registeredMetadata.keySet()));
                        } else {
                            log.error("❌ ERROR: Instance registered but metadata is empty in Nacos! Expected {} fields: {}", 
                                    metadata.size(), String.join(", ", metadata.keySet()));
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("⚠️ Failed to verify instance metadata after registration: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("❌ Failed to register via SDK: {}", e.getMessage(), e);
            // 如果 SDK 失败，尝试使用 v3 API（作为最后的备选）
            if (useV3Api && nacosV3ApiService != null) {
                log.warn("⚠️ Trying v3 API as fallback...");
                boolean success = nacosV3ApiService.registerInstance(
                        mcpServiceName, localIp, serverPort, serviceGroup, 
                        "DEFAULT", ephemeral, metadata);
                if (success) {
                    log.info("✅ Registered instance to Nacos v3 (fallback): {}:{} in group: {} (application: {}, ephemeral: {}, metadata: {} fields)", 
                            localIp, serverPort, serviceGroup, finalApplication != null ? finalApplication : "N/A", ephemeral, metadata.size());
                } else {
                    log.error("❌ Failed to register via v3 API fallback");
                    throw new RuntimeException("Failed to register instance to Nacos", e);
                }
            } else {
                throw new RuntimeException("Failed to register instance to Nacos", e);
            }
        }
    }

    /**
     * 为所有活跃的 zkInfo 节点注册虚拟项目实例到 Nacos
     * 
     * @param mcpServiceName MCP服务名称
     * @param serviceId 服务ID
     * @param version 版本
     * @param tools 工具列表
     * @param providers 提供者列表
     * @param application 应用名称
     * @param ephemeral 是否临时节点
     */
    private void registerInstancesToNacosForAllNodes(String mcpServiceName, String serviceId, 
                                                     String version, List<Map<String, Object>> tools,
                                                     List<ProviderInfo> providers,
                                                     String application, boolean ephemeral, String serverConfigContent) {
        try {
            // 1. 获取所有活跃的 zkInfo 节点
            List<ZkInfoNodeDiscoveryService.ZkInfoNode> activeNodes = zkInfoNodeDiscoveryService.getAllActiveZkInfoNodes();
            
            if (activeNodes.isEmpty()) {
                log.warn("⚠️ No active zkInfo nodes found, registering current node only");
                // 如果没有找到节点，至少注册当前节点
                registerInstanceToNacos(mcpServiceName, serviceId, version, tools, providers, application, ephemeral, serverConfigContent);
                return;
            }
            
            log.info("🚀 Registering virtual project to {} zkInfo nodes: {}", 
                    activeNodes.size(), 
                    activeNodes.stream()
                            .map(ZkInfoNodeDiscoveryService.ZkInfoNode::getAddress)
                            .collect(java.util.stream.Collectors.joining(", ")));
            
            // 2. 为每个节点注册实例
            int successCount = 0;
            int failCount = 0;
            
            for (ZkInfoNodeDiscoveryService.ZkInfoNode node : activeNodes) {
                try {
                    registerInstanceToNacosForNode(mcpServiceName, serviceId, version, tools, providers, 
                            application, ephemeral, node.getIp(), node.getPort(), serverConfigContent);
                    successCount++;
                    log.info("✅ Registered virtual project instance for node: {}:{}", node.getIp(), node.getPort());
                } catch (Exception e) {
                    failCount++;
                    log.error("❌ Failed to register virtual project instance for node: {}:{}, error: {}", 
                            node.getIp(), node.getPort(), e.getMessage(), e);
                }
            }
            
            log.info("✅ Completed registering virtual project instances: {} succeeded, {} failed out of {} total nodes", 
                    successCount, failCount, activeNodes.size());
            
            // 3. 清理掉不再活跃的节点实例（特别是针对持久节点）
            try {
                List<Instance> existingInstances = namingService.getAllInstances(mcpServiceName, serviceGroup);
                if (existingInstances != null && !existingInstances.isEmpty()) {
                    Set<String> activeNodeAddresses = activeNodes.stream()
                            .map(n -> n.getIp() + ":" + n.getPort())
                            .collect(java.util.stream.Collectors.toSet());
                    
                    for (Instance existing : existingInstances) {
                        String addr = existing.getIp() + ":" + existing.getPort();
                        if (!activeNodeAddresses.contains(addr)) {
                            log.warn("⚠️ Found stale instance in Nacos for service {}: {}, deregistering...", mcpServiceName, addr);
                            try {
                                if (useV3Api && nacosV3ApiService != null) {
                                    nacosV3ApiService.deregisterInstance(mcpServiceName, existing.getIp(), existing.getPort(), 
                                            serviceGroup, existing.isEphemeral());
                                } else {
                                    namingService.deregisterInstance(mcpServiceName, serviceGroup, 
                                            existing.getIp(), existing.getPort());
                                }
                                log.info("✅ Successfully deregistered stale instance: {} for service: {}", addr, mcpServiceName);
                            } catch (Exception e) {
                                log.error("❌ Failed to deregister stale instance: {}, error: {}", addr, e.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("⚠️ Failed to cleanup stale instances for service {}: {}", mcpServiceName, e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to register instances for all nodes, falling back to current node only: {}", e.getMessage(), e);
            // 如果失败，至少注册当前节点
            try {
                registerInstanceToNacos(mcpServiceName, serviceId, version, tools, providers, application, ephemeral, serverConfigContent);
            } catch (Exception fallbackError) {
                log.error("❌ Failed to register current node as fallback: {}", fallbackError.getMessage(), fallbackError);
                throw new RuntimeException("Failed to register virtual project instances", e);
            }
        }
    }

    /**
     * 为指定节点注册虚拟项目实例到 Nacos
     * 
     * @param mcpServiceName MCP服务名称
     * @param serviceId 服务ID
     * @param version 版本
     * @param tools 工具列表
     * @param providers 提供者列表
     * @param application 应用名称
     * @param ephemeral 是否临时节点
     * @param nodeIp 节点IP
     * @param nodePort 节点端口
     */
    private void registerInstanceToNacosForNode(String mcpServiceName, String serviceId, 
                                                String version, List<Map<String, Object>> tools,
                                                List<ProviderInfo> providers,
                                                String application, boolean ephemeral,
                                                String nodeIp, int nodePort, String serverConfigContent) throws NacosException {
        
        // 创建实例
        Instance instance = new Instance();
        instance.setIp(nodeIp);
        instance.setPort(nodePort);
        instance.setHealthy(true);
        instance.setEnabled(true);
        instance.setEphemeral(ephemeral);
        
        // 设置元数据
        Map<String, String> metadata = buildInstanceMetadata(mcpServiceName, serviceId, version, tools, application, serverConfigContent);
        
        if (metadata == null || metadata.isEmpty()) {
            log.error("❌ Metadata is empty! Cannot register instance for node {}:{} without metadata.", nodeIp, nodePort);
            throw new RuntimeException("Metadata is required for MCP service registration");
        }
        
        instance.setMetadata(metadata);
        
        // 注册实例
        namingService.registerInstance(mcpServiceName, serviceGroup, instance);
        
        log.info("✅ Registered instance to Nacos for node {}:{} in group: {} (application: {}, ephemeral: {})", 
                nodeIp, nodePort, serviceGroup, application != null ? application : "N/A", ephemeral);
    }

    /**
     * 构建实例元数据
     */
    private Map<String, String> buildInstanceMetadata(String mcpServiceName, String serviceId, 
                                                      String version, List<Map<String, Object>> tools,
                                                      String application, String serverConfigContent) {
        Map<String, String> metadata = new HashMap<>();
        
        // 基础信息
        metadata.put("version", version != null ? version : "1.0.0");
        metadata.put("protocol", "mcp-sse");
        metadata.put("scheme", "http");
        
        // SSE 端点信息
        // 直接使用 mcpServiceName (带 virtual- 前缀) 构造 endpoint，确保路径完整
        String sseEndpoint = "/sse/" + mcpServiceName;
        metadata.put("sseEndpoint", sseEndpoint);
        // Message 端点：使用 /mcp/{serviceName}/message 格式（与 SseController 保持一致）
        String sseMessageEndpoint = "/mcp/" + mcpServiceName + "/message";
        metadata.put("sseMessageEndpoint", sseMessageEndpoint);
        
        // 添加 context-path 信息（如果存在），供 mcp-router-v3 使用
        // 注意：context-path 可能在不同环境下不同，这里存储的是配置的默认值
        // 实际使用时，zkInfo 会根据请求动态构建完整的 URL
        if (contextPath != null && !contextPath.isEmpty() && !contextPath.equals("/")) {
            // 规范化 context-path：确保以 / 开头，但不以 / 结尾
            String normalizedContextPath = contextPath.trim();
            if (!normalizedContextPath.startsWith("/")) {
                normalizedContextPath = "/" + normalizedContextPath;
            }
            if (normalizedContextPath.endsWith("/") && normalizedContextPath.length() > 1) {
                normalizedContextPath = normalizedContextPath.substring(0, normalizedContextPath.length() - 1);
            }
            metadata.put("contextPath", normalizedContextPath);
            log.debug("📦 Added context-path to metadata: {} for service: {}", normalizedContextPath, mcpServiceName);
        }
        
        // 服务标识
        metadata.put("serverName", mcpServiceName);
        metadata.put("serverId", serviceId);
        
        // 设置application：如果是虚拟项目，直接使用 mcpServiceName 确保一致性
        // 否则使用传入的 application
        String finalApplication = application;
        if (mcpServiceName.startsWith("virtual-")) {
            finalApplication = mcpServiceName;
        } else if (finalApplication == null || finalApplication.isEmpty()) {
            finalApplication = mcpServiceName;
        }
        metadata.put("application", finalApplication);
        
        // 工具数量
        metadata.put("tools.count", String.valueOf(tools.size()));
        
        // 计算server配置的MD5（使用传入的内容，不再请求Nacos）
        if (serverConfigContent != null && !serverConfigContent.isEmpty()) {
            String md5 = calculateMd5(serverConfigContent);
            metadata.put("server.md5", md5);
            log.debug("📦 Calculated MD5 from content: {}", md5);
        } else {
            log.warn("⚠️ Server config content is empty, cannot calculate MD5");
        }
        
        // 检查 metadata 总大小
        int totalSize = calculateMetadataSize(metadata);
        if (totalSize > 1024) {
            log.warn("⚠️ Metadata size ({}) exceeds 1024 bytes, removing optional fields", totalSize);
            metadata.remove("tools.names");
            totalSize = calculateMetadataSize(metadata);
            if (totalSize > 1024) {
                log.error("❌ Metadata size ({}) still exceeds 1024 bytes after removing optional fields", totalSize);
                throw new RuntimeException("Metadata size exceeds Nacos limit (1024 bytes): " + totalSize);
            }
        }
        
        return metadata;
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
            // 限制每次查询最多1000条，避免内存溢出
            // 如果需要查询更多，可以分页查询
            int pageSize = 1000;
            int pageNo = 1;
            ListView<String> servicesList = namingService.getServicesOfServer(pageNo, pageSize, serviceGroup);
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
     * 从 Nacos 配置中心获取工具配置
     * 
     * @param serviceName 服务名称（如 virtual-{endpointName}）
     * @param serviceGroup 服务组
     * @return 工具列表
     */
    public List<Map<String, Object>> getToolsFromNacosConfig(String serviceName, String serviceGroup) {
        try {
            // 1. 从 Nacos 服务列表查询服务实例，获取 serviceId 和 version
            List<Instance> instances = namingService.selectInstances(serviceName, serviceGroup, true);
            if (instances == null || instances.isEmpty()) {
                log.warn("⚠️ No healthy instances found for service: {} in group: {}", serviceName, serviceGroup);
                return Collections.emptyList();
            }
            
            // 使用第一个健康实例的 metadata 获取 serviceId 和 version
            Instance instance = instances.get(0);
            Map<String, String> metadata = instance.getMetadata();
            if (metadata == null || metadata.isEmpty()) {
                log.warn("⚠️ Instance has no metadata for service: {}", serviceName);
                return Collections.emptyList();
            }
            
            String serviceId = metadata.get("serverId");
            String version = metadata.get("version");
            if (serviceId == null || version == null) {
                log.warn("⚠️ Instance metadata missing serverId or version for service: {}", serviceName);
                return Collections.emptyList();
            }
            
            log.debug("📦 Found service instance: serviceId={}, version={}", serviceId, version);
            
            // 2. 从配置中心获取工具配置
            // dataId 格式：{serviceId}-{version}-mcp-tools.json
            String toolsDataId = serviceId + "-" + version + "-mcp-tools.json";
            String toolsConfig = configService.getConfig(toolsDataId, TOOLS_GROUP, 5000);
            
            if (toolsConfig == null || toolsConfig.trim().isEmpty()) {
                log.warn("⚠️ No tools config found in Nacos: dataId={}, group={}", toolsDataId, TOOLS_GROUP);
                return Collections.emptyList();
            }
            
            log.debug("✅ Got tools config from Nacos: dataId={}, size={} bytes", toolsDataId, toolsConfig.length());
            
            // 3. 解析 JSON 配置
            Map<String, Object> toolsInfo = objectMapper.readValue(toolsConfig, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) toolsInfo.get("tools");
            
            if (tools == null || tools.isEmpty()) {
                log.warn("⚠️ Tools list is empty in config: dataId={}", toolsDataId);
                return Collections.emptyList();
            }
            
            log.info("✅ Parsed {} tools from Nacos config [NEW VERSION]: dataId={}", tools.size(), toolsDataId);
            
            // Sanitize tools: ensure 'name' property exists (MCP spec requirement)
            // Sanitize tools: ensure 'name' property exists (MCP spec requirement)
            for (Map<String, Object> tool : tools) {
                if (!tool.containsKey("name") && tool.containsKey("toolName")) {
                    tool.put("name", tool.get("toolName"));
                }
            }
            
            return tools;
            
        } catch (Exception e) {
            log.error("❌ Failed to get tools from Nacos config for service: {}, error: {}", 
                    serviceName, e.getMessage(), e);
            return Collections.emptyList();
        }
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
            
            // 查询实例的 ephemeral 状态（普通 Dubbo 服务都是临时节点）
            boolean ephemeral = true; // 普通 Dubbo 服务默认都是临时节点
            if (useV3Api && nacosV3ApiService != null) {
                try {
                    List<Map<String, Object>> instances = nacosV3ApiService.getInstanceList(
                            mcpServiceName, serviceGroup, null, false);
                    for (Map<String, Object> instance : instances) {
                        String instanceIp = (String) instance.get("ip");
                        Integer instancePort = (Integer) instance.get("port");
                        if (localIp.equals(instanceIp) && serverPort == instancePort) {
                            Object ephemeralObj = instance.get("ephemeral");
                            if (ephemeralObj instanceof Boolean) {
                                ephemeral = (Boolean) ephemeralObj;
                            } else if (ephemeralObj instanceof String) {
                                ephemeral = Boolean.parseBoolean((String) ephemeralObj);
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to query instance ephemeral status, using default (ephemeral=true): {}", e.getMessage());
                }
            }
            
            // 注销实例：优先使用 v3 API，否则使用 SDK
            if (useV3Api && nacosV3ApiService != null) {
                boolean success = nacosV3ApiService.deregisterInstance(
                        mcpServiceName, localIp, serverPort, serviceGroup, ephemeral);
                if (success) {
                    log.info("✅ Deregistered MCP service (v3 API): {} from Nacos (ephemeral: {})", mcpServiceName, ephemeral);
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
     * 包括：删除服务实例和所有相关配置
     */
    public void deregisterVirtualProjectMcpService(String mcpServiceName, String version) {
        try {
            String localIp = getLocalIp();
            
            // 1. 生成服务ID（与注册时保持一致）
            String serviceId = generateServiceId(mcpServiceName, version);
            
            // 2. 删除 Nacos 配置中心的配置
            deleteConfigsFromNacos(serviceId, mcpServiceName, version);
            
            // 3. 查询实例的 ephemeral 状态（用于正确删除）
            // 注意：虚拟节点是永久节点（ephemeral=false），需要查询实际状态以确保正确删除
            boolean ephemeral = false; // 默认值：虚拟节点是永久节点（ephemeral=false）
            if (useV3Api && nacosV3ApiService != null) {
                try {
                    List<Map<String, Object>> instances = nacosV3ApiService.getInstanceList(
                            mcpServiceName, serviceGroup, null, false);
                    for (Map<String, Object> instance : instances) {
                        String instanceIp = (String) instance.get("ip");
                        Integer instancePort = (Integer) instance.get("port");
                        if (localIp.equals(instanceIp) && serverPort == instancePort) {
                            // 获取 ephemeral 状态
                            Object ephemeralObj = instance.get("ephemeral");
                            if (ephemeralObj instanceof Boolean) {
                                ephemeral = (Boolean) ephemeralObj;
                            } else if (ephemeralObj instanceof String) {
                                ephemeral = Boolean.parseBoolean((String) ephemeralObj);
                            }
                            log.info("🔍 Found instance ephemeral status: {} (service: {}, ip: {}, port: {})", 
                                    ephemeral, mcpServiceName, localIp, serverPort);
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Failed to query instance ephemeral status, using default (ephemeral=false for virtual projects): {}", e.getMessage());
                }
            }
            
            // 4. 注销服务实例：优先使用 v3 API，否则使用 SDK
            if (useV3Api && nacosV3ApiService != null) {
                boolean success = nacosV3ApiService.deregisterInstance(
                        mcpServiceName, localIp, serverPort, serviceGroup, ephemeral);
                if (success) {
                    log.info("✅ Deregistered virtual project MCP service instance (v3 API): {} from Nacos (ephemeral: {})", 
                            mcpServiceName, ephemeral);
                } else {
                    log.warn("⚠️ Failed to deregister instance via v3 API, falling back to SDK");
                    namingService.deregisterInstance(mcpServiceName, serviceGroup, localIp, serverPort);
                }
            } else {
                namingService.deregisterInstance(mcpServiceName, serviceGroup, localIp, serverPort);
                log.info("✅ Deregistered virtual project MCP service instance (SDK): {} from Nacos", mcpServiceName);
            }
            
            // 5. 显式清理并删除服务
            if (useV3Api && nacosV3ApiService != null) {
                try {
                    // 5.1 首先查询该服务下的所有残留实例并强制注销
                    log.info("🔍 Checking for remaining instances of service: {}", mcpServiceName);
                    List<Map<String, Object>> remainingInstances = nacosV3ApiService.getInstanceList(mcpServiceName, serviceGroup, null, false);
                    if (remainingInstances != null && !remainingInstances.isEmpty()) {
                        log.info("🧹 Found {} remaining instances, cleaning them up...", remainingInstances.size());
                        for (Map<String, Object> inst : remainingInstances) {
                            String ip = (String) inst.get("ip");
                            Integer port = (Integer) inst.get("port");
                            Boolean isEphemeral = (Boolean) inst.get("ephemeral");
                            if (ip != null && port != null) {
                                log.info("  - Deregistering instance: {}:{} (ephemeral={})", ip, port, isEphemeral);
                                nacosV3ApiService.deregisterInstance(mcpServiceName, ip, port, serviceGroup, isEphemeral != null ? isEphemeral : false);
                            }
                        }
                        // 给 Nacos 一点时间处理注销任务
                        Thread.sleep(1000);
                    }
                    
                    // 5.2 删除 MCP 服务元数据（使用 HTTP API）
                    boolean mcpDeleted = nacosMcpHttpApiService.deleteMcpServer(mcpServiceName);
                    if (mcpDeleted) {
                        log.info("🗑️ Explicitly deleted MCP server metadata: {}", mcpServiceName);
                    } else {
                        log.warn("⚠️ Failed to delete MCP server metadata: {}", mcpServiceName);
                    }
                    
                    // 5.2 删除 Nacos 服务定义
                    // 由于实例注销可能是异步的，或者有延迟，如果报错"service not empty"则重试
                    int retryCount = 0;
                    boolean deleted = false;
                    while (retryCount < 3 && !deleted) {
                        try {
                            if (retryCount > 0) {
                                Thread.sleep(1000); // 重试前等待1秒
                            }
                            
                            boolean result = nacosV3ApiService.deleteService(mcpServiceName, serviceGroup);
                            if (result) {
                                log.info("🗑️ Explicitly deleted service definition: {}", mcpServiceName);
                                deleted = true;
                            } else {
                                // 如果返回 false 但没有抛出异常，可能是其他原因，记录日志但继续尝试或退出
                                log.warn("⚠️ Failed to delete service definition (result=false), attempt {}/3", retryCount + 1);
                                retryCount++;
                            }
                        } catch (org.springframework.web.client.HttpClientErrorException.BadRequest e) {
                            // 捕获 400 错误 (如 Service not empty)
                            log.warn("⚠️ Failed to delete service definition: {} (attempt {}/3). Waiting for instances to be removed...", e.getResponseBodyAsString(), retryCount + 1);
                            retryCount++;
                        } catch (Exception e) {
                            log.warn("⚠️ Failed to delete service definition: {} (attempt {}/3)", e.getMessage(), retryCount + 1);
                            retryCount++;
                        }
                    }
                    
                    if (!deleted) {
                        log.error("❌ Failed to delete service definition after 3 attempts: {}", mcpServiceName);
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Failed to cleanup service/MCP definition: {}", e.getMessage());
                }
            }
            
            log.info("✅ Successfully deregistered virtual project MCP service: {} (serviceId: {}, ephemeral: {})", 
                    mcpServiceName, serviceId, ephemeral);
        } catch (Exception e) {
            log.error("❌ Failed to deregister virtual project MCP service: {}", mcpServiceName, e);
        }
    }
    
    /**
     * 从 Nacos 配置中心删除配置
     * 删除注册时创建的所有配置：tools、versions、server
     */
    private void deleteConfigsFromNacos(String serviceId, String mcpServiceName, String version) {
        try {
            // 1. 删除 mcp-tools.json
            String toolsDataId = serviceId + "-" + version + "-mcp-tools.json";
            try {
                configService.removeConfig(toolsDataId, TOOLS_GROUP);
                log.info("✅ Deleted tools config: {}", toolsDataId);
            } catch (Exception e) {
                log.warn("⚠️ Failed to delete tools config: {} - {}", toolsDataId, e.getMessage());
            }
            
            // 2. 删除 mcp-versions.json
            String versionsDataId = serviceId + "-mcp-versions.json";
            try {
                configService.removeConfig(versionsDataId, VERSIONS_GROUP);
                log.info("✅ Deleted versions config: {}", versionsDataId);
            } catch (Exception e) {
                log.warn("⚠️ Failed to delete versions config: {} - {}", versionsDataId, e.getMessage());
            }
            
            // 3. 删除 mcp-server.json
            String serverDataId = serviceId + "-" + version + "-mcp-server.json";
            try {
                configService.removeConfig(serverDataId, SERVER_GROUP);
                log.info("✅ Deleted server config: {}", serverDataId);
            } catch (Exception e) {
                log.warn("⚠️ Failed to delete server config: {} - {}", serverDataId, e.getMessage());
            }
            
            log.info("✅ Successfully deleted all configs for virtual project: {} (serviceId: {})", 
                    mcpServiceName, serviceId);
        } catch (Exception e) {
            log.error("❌ Failed to delete configs from Nacos: serviceId={}, mcpServiceName={}", 
                    serviceId, mcpServiceName, e);
        }
    }
}

