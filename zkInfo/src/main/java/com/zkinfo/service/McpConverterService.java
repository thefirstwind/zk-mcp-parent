package com.zkinfo.service;

import com.zkinfo.model.ApplicationInfo;
import com.zkinfo.model.McpResponse;
import com.zkinfo.model.ProviderInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP (Model Context Protocol) 格式转换服务
 * 
 * 负责将 Dubbo 服务信息转换为 MCP 标准格式，使传统的 RPC 服务能够被
 * AI 系统和智能化工具理解和调用。MCP 是一种标准化的服务描述协议，
 * 提供了统一的工具定义和服务描述格式。
 * 
 * <p>转换功能：</p>
 * <ul>
 *   <li>将服务方法转换为 MCP 工具 (Tools)</li>
 *   <li>将服务接口转换为 MCP 服务 (Services)</li>
 *   <li>生成 MCP 元数据信息 (Metadata)</li>
 *   <li>支持单应用和全局转换</li>
 *   <li>提供丰富的服务描述信息</li>
 * </ul>
 * 
 * <p>MCP 格式特点：</p>
 * <ul>
 *   <li>标准化的工具定义格式</li>
 *   <li>完整的输入输出模式描述</li>
 *   <li>丰富的元数据信息</li>
 *   <li>便于 AI 系统理解和调用</li>
 * </ul>
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
public class McpConverterService {
    
    @Autowired
    private ProviderService providerService;
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 将应用信息转换为MCP格式
     */
    public McpResponse convertApplicationToMcp(String applicationName) {
        ApplicationInfo appInfo = providerService.getApplicationByName(applicationName);
        if (appInfo == null) {
            return createEmptyMcpResponse(applicationName);
        }
        
        return convertApplicationToMcp(appInfo);
    }
    
    /**
     * 将应用信息转换为MCP格式
     */
    public McpResponse convertApplicationToMcp(ApplicationInfo appInfo) {
        try {
            McpResponse response = new McpResponse();
            response.setApplication(appInfo.getApplicationName());
            
            // 转换工具列表
            List<McpResponse.McpTool> tools = convertProvidersToTools(appInfo.getProviders());
            response.setTools(tools);
            
            // 转换服务列表
            List<McpResponse.McpService> services = convertProvidersToServices(appInfo);
            response.setServices(services);
            
            // 创建元数据
            McpResponse.McpMetadata metadata = createMetadata(appInfo, tools.size(), services.size());
            response.setMetadata(metadata);
            
            return response;
            
        } catch (Exception e) {
            log.error("转换应用 {} 为MCP格式失败", appInfo.getApplicationName(), e);
            return createEmptyMcpResponse(appInfo.getApplicationName());
        }
    }
    
    /**
     * 获取所有应用的MCP格式数据
     */
    public List<McpResponse> convertAllApplicationsToMcp() {
        List<ApplicationInfo> applications = providerService.getAllApplications();
        
        return applications.stream()
                .map(this::convertApplicationToMcp)
                .collect(Collectors.toList());
    }
    
    /**
     * 将Provider列表转换为MCP工具列表
     */
    private List<McpResponse.McpTool> convertProvidersToTools(List<ProviderInfo> providers) {
        List<McpResponse.McpTool> tools = new ArrayList<>();
        
        for (ProviderInfo provider : providers) {
            try {
                // 为每个Provider的每个方法创建一个工具
                if (provider.getMethods() != null && !provider.getMethods().isEmpty()) {
                    String[] methods = provider.getMethods().split(",");
                    
                    for (String method : methods) {
                        McpResponse.McpTool tool = new McpResponse.McpTool();
                        
                        // 工具名称：接口名.方法名
                        String toolName = provider.getInterfaceName() + "." + method.trim();
                        tool.setName(toolName);
                        
                        // 工具描述
                        tool.setDescription(String.format("调用 %s 服务的 %s 方法", 
                                provider.getInterfaceName(), method.trim()));
                        
                        // 输入参数schema（简化版本）
                        Map<String, Object> inputSchema = createInputSchema(provider, method.trim());
                        tool.setInputSchema(inputSchema);
                        
                        // 其他属性
                        tool.setType("function");
                        tool.setVersion(provider.getVersion());
                        tool.setGroup(provider.getGroup());
                        tool.setProvider(provider.getAddress());
                        tool.setOnline(provider.isOnline());
                        
                        tools.add(tool);
                    }
                } else {
                    // 如果没有方法信息，创建一个通用工具
                    McpResponse.McpTool tool = new McpResponse.McpTool();
                    tool.setName(provider.getInterfaceName());
                    tool.setDescription("调用 " + provider.getInterfaceName() + " 服务");
                    tool.setInputSchema(createGenericInputSchema(provider));
                    tool.setType("function");
                    tool.setVersion(provider.getVersion());
                    tool.setGroup(provider.getGroup());
                    tool.setProvider(provider.getAddress());
                    tool.setOnline(provider.isOnline());
                    
                    tools.add(tool);
                }
                
            } catch (Exception e) {
                log.error("转换Provider为工具失败: {}", provider.getInterfaceName(), e);
            }
        }
        
        return tools;
    }
    
    /**
     * 将应用信息转换为MCP服务列表
     */
    private List<McpResponse.McpService> convertProvidersToServices(ApplicationInfo appInfo) {
        List<McpResponse.McpService> services = new ArrayList<>();
        
        // 按接口分组
        Map<String, List<ProviderInfo>> providersByInterface = appInfo.getProvidersByInterface();
        
        for (Map.Entry<String, List<ProviderInfo>> entry : providersByInterface.entrySet()) {
            String interfaceName = entry.getKey();
            List<ProviderInfo> interfaceProviders = entry.getValue();
            
            if (interfaceProviders.isEmpty()) {
                continue;
            }
            
            try {
                McpResponse.McpService service = new McpResponse.McpService();
                
                // 使用第一个Provider的信息作为服务基础信息
                ProviderInfo firstProvider = interfaceProviders.get(0);
                
                service.setName(interfaceName);
                service.setDescription("Dubbo服务: " + interfaceName);
                service.setInterfaceName(interfaceName);
                service.setVersion(firstProvider.getVersion());
                service.setGroup(firstProvider.getGroup());
                service.setProtocol(firstProvider.getProtocol());
                
                // 解析方法列表
                if (firstProvider.getMethods() != null && !firstProvider.getMethods().isEmpty()) {
                    List<String> methods = Arrays.asList(firstProvider.getMethods().split(","));
                    service.setMethods(methods.stream().map(String::trim).collect(Collectors.toList()));
                }
                
                // 转换提供者列表
                List<McpResponse.ServiceProvider> serviceProviders = interfaceProviders.stream()
                        .map(this::convertToServiceProvider)
                        .collect(Collectors.toList());
                service.setProviders(serviceProviders);
                
                // 确定服务状态
                long onlineCount = serviceProviders.stream().filter(McpResponse.ServiceProvider::isOnline).count();
                if (onlineCount == 0) {
                    service.setStatus("offline");
                } else if (onlineCount == serviceProviders.size()) {
                    service.setStatus("online");
                } else {
                    service.setStatus("partial");
                }
                
                services.add(service);
                
            } catch (Exception e) {
                log.error("转换接口 {} 为服务失败", interfaceName, e);
            }
        }
        
        return services;
    }
    
    /**
     * 转换为服务提供者
     */
    private McpResponse.ServiceProvider convertToServiceProvider(ProviderInfo provider) {
        McpResponse.ServiceProvider serviceProvider = new McpResponse.ServiceProvider();
        serviceProvider.setAddress(provider.getAddress());
        serviceProvider.setOnline(provider.isOnline());
        
        if (provider.getLastHeartbeat() != null) {
            serviceProvider.setLastHeartbeat(provider.getLastHeartbeat().format(FORMATTER));
        }
        
        // 设置默认权重
        serviceProvider.setWeight(100);
        
        return serviceProvider;
    }
    
    /**
     * 创建输入参数schema
     */
    private Map<String, Object> createInputSchema(ProviderInfo provider, String method) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        // 添加通用参数
        Map<String, Object> argsProperty = new HashMap<>();
        argsProperty.put("type", "array");
        argsProperty.put("description", "方法参数列表");
        argsProperty.put("items", Map.of("type", "any"));
        properties.put("args", argsProperty);
        
        // 添加超时参数
        Map<String, Object> timeoutProperty = new HashMap<>();
        timeoutProperty.put("type", "integer");
        timeoutProperty.put("description", "调用超时时间(毫秒)");
        timeoutProperty.put("default", 3000);
        properties.put("timeout", timeoutProperty);
        
        schema.put("properties", properties);
        schema.put("required", List.of("args"));
        
        return schema;
    }
    
    /**
     * 创建通用输入参数schema
     */
    private Map<String, Object> createGenericInputSchema(ProviderInfo provider) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        // 方法名
        Map<String, Object> methodProperty = new HashMap<>();
        methodProperty.put("type", "string");
        methodProperty.put("description", "要调用的方法名");
        properties.put("method", methodProperty);
        
        // 参数
        Map<String, Object> argsProperty = new HashMap<>();
        argsProperty.put("type", "array");
        argsProperty.put("description", "方法参数列表");
        argsProperty.put("items", Map.of("type", "any"));
        properties.put("args", argsProperty);
        
        // 超时
        Map<String, Object> timeoutProperty = new HashMap<>();
        timeoutProperty.put("type", "integer");
        timeoutProperty.put("description", "调用超时时间(毫秒)");
        timeoutProperty.put("default", 3000);
        properties.put("timeout", timeoutProperty);
        
        schema.put("properties", properties);
        schema.put("required", List.of("method", "args"));
        
        return schema;
    }
    
    /**
     * 创建元数据
     */
    private McpResponse.McpMetadata createMetadata(ApplicationInfo appInfo, int toolCount, int serviceCount) {
        McpResponse.McpMetadata metadata = new McpResponse.McpMetadata();
        
        metadata.setProtocolVersion("1.0");
        metadata.setTimestamp(LocalDateTime.now().format(FORMATTER));
        metadata.setTotalTools(toolCount);
        metadata.setTotalServices(serviceCount);
        metadata.setOnlineProviders(appInfo.getOnlineProviderCount());
        metadata.setTotalProviders(appInfo.getTotalProviderCount());
        
        // 应用状态
        switch (appInfo.getStatus()) {
            case ONLINE:
                metadata.setApplicationStatus("online");
                break;
            case OFFLINE:
                metadata.setApplicationStatus("offline");
                break;
            case PARTIAL:
                metadata.setApplicationStatus("partial");
                break;
            default:
                metadata.setApplicationStatus("unknown");
                break;
        }
        
        return metadata;
    }
    
    /**
     * 创建空的MCP响应
     */
    private McpResponse createEmptyMcpResponse(String applicationName) {
        McpResponse response = new McpResponse();
        response.setApplication(applicationName);
        response.setTools(new ArrayList<>());
        response.setServices(new ArrayList<>());
        
        McpResponse.McpMetadata metadata = new McpResponse.McpMetadata();
        metadata.setProtocolVersion("1.0");
        metadata.setTimestamp(LocalDateTime.now().format(FORMATTER));
        metadata.setTotalTools(0);
        metadata.setTotalServices(0);
        metadata.setOnlineProviders(0);
        metadata.setTotalProviders(0);
        metadata.setApplicationStatus("offline");
        
        response.setMetadata(metadata);
        
        return response;
    }
    
    /**
     * 获取全局MCP统计信息
     */
    public McpResponse.McpMetadata getGlobalMcpMetadata() {
        Map<String, Integer> stats = providerService.getOnlineStats();
        
        McpResponse.McpMetadata metadata = new McpResponse.McpMetadata();
        metadata.setProtocolVersion("1.0");
        metadata.setTimestamp(LocalDateTime.now().format(FORMATTER));
        metadata.setTotalServices(providerService.getAllInterfaces().size());
        metadata.setOnlineProviders(stats.get("onlineProviders"));
        metadata.setTotalProviders(stats.get("totalProviders"));
        
        // 计算总工具数（每个方法算一个工具）
        int totalTools = providerService.getAllProviders().stream()
                .mapToInt(provider -> {
                    if (provider.getMethods() != null && !provider.getMethods().isEmpty()) {
                        return provider.getMethods().split(",").length;
                    }
                    return 1; // 没有方法信息时算作1个工具
                })
                .sum();
        metadata.setTotalTools(totalTools);
        
        // 全局状态
        if (stats.get("onlineProviders") == 0) {
            metadata.setApplicationStatus("offline");
        } else if (stats.get("onlineProviders").equals(stats.get("totalProviders"))) {
            metadata.setApplicationStatus("online");
        } else {
            metadata.setApplicationStatus("partial");
        }
        
        return metadata;
    }
}
