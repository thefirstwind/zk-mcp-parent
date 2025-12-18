package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.Project;
import com.pajk.mcpmetainfo.core.model.ProjectService;
import com.pajk.mcpmetainfo.core.model.VirtualProjectEndpoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.pajk.mcpmetainfo.core.util.McpToolSchemaGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 虚拟项目注册服务
 * 
 * 负责将虚拟项目注册为MCP服务到Nacos
 * 虚拟项目作为独立的MCP服务，对应 mcp-router-v3 的 endpoint
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-01-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualProjectRegistrationService {
    
    private final NacosMcpRegistrationService nacosMcpRegistrationService;
    private final ProjectManagementService projectManagementService;
    private final ProviderService providerService;
    
    @Autowired
    private McpToolSchemaGenerator mcpToolSchemaGenerator;
    
    @Value("${server.port:9091}")
    private int serverPort;
    
    /**
     * 将虚拟项目注册为MCP服务到Nacos
     * 
     * @param virtualProject 虚拟项目
     * @param endpoint Endpoint映射
     */
    public void registerVirtualProjectToNacos(Project virtualProject, VirtualProjectEndpoint endpoint) {
        try {
            log.info("🚀 Registering virtual project as MCP service: {} -> {}", 
                    virtualProject.getProjectName(), endpoint.getEndpointName());
            
            // 1. 获取虚拟项目包含的所有服务
            List<ProjectService> projectServices = projectManagementService.getProjectServices(virtualProject.getId());
            
            if (projectServices.isEmpty()) {
                log.warn("⚠️ Virtual project {} has no services, skip registration", 
                        virtualProject.getProjectName());
                return;
            }
            
            // 2. 聚合所有服务的Provider和工具
            List<com.pajk.mcpmetainfo.core.model.ProviderInfo> aggregatedProviders = aggregateProviders(projectServices);
            
            if (aggregatedProviders.isEmpty()) {
                log.warn("⚠️ Virtual project {} has no available providers, skip registration", 
                        virtualProject.getProjectName());
                return;
            }
            
            // 3. 使用NacosMcpRegistrationService注册虚拟项目
            // 使用 virtual-{endpointName} 作为服务名称（如 virtual-data-analysis），便于识别虚拟项目
            // 虚拟项目的application使用虚拟项目名称
            String serviceName = "virtual-" + endpoint.getEndpointName();
            nacosMcpRegistrationService.registerVirtualProjectAsMcp(
                    serviceName, // 使用 virtual-{endpointName} 格式
                    "1.0.0", // 虚拟项目统一使用1.0.0版本
                    aggregatedProviders,
                    virtualProject.getProjectName() // 虚拟项目名称作为application
            );
            
            log.info("✅ Successfully registered virtual project to Nacos: {} ({} services, {} providers)", 
                    endpoint.getEndpointName(), projectServices.size(), aggregatedProviders.size());
            
        } catch (Exception e) {
            log.error("❌ Failed to register virtual project to Nacos: {}", 
                    virtualProject.getProjectName(), e);
            throw new RuntimeException("Failed to register virtual project to Nacos", e);
        }
    }
    
    /**
     * 重新注册虚拟项目到Nacos（更新服务列表后调用）
     */
    public void reregisterVirtualProjectToNacos(Project virtualProject, VirtualProjectEndpoint endpoint) {
        // 先注销旧的服务
        deregisterVirtualProjectFromNacos(endpoint);
        
        // 重新注册
        registerVirtualProjectToNacos(virtualProject, endpoint);
        
        log.info("✅ Reregistered virtual project to Nacos: {}", endpoint.getEndpointName());
    }
    
    /**
     * 注销虚拟项目从Nacos
     */
    public void deregisterVirtualProjectFromNacos(VirtualProjectEndpoint endpoint) {
        try {
            // 使用 virtual-{endpointName} 格式注销
            String serviceName = "virtual-" + endpoint.getEndpointName();
            nacosMcpRegistrationService.deregisterVirtualProjectMcpService(
                    serviceName,
                    "1.0.0"
            );
            
            log.info("✅ Deregistered virtual project from Nacos: {} -> {}", endpoint.getEndpointName(), serviceName);
            
        } catch (Exception e) {
            log.error("❌ Failed to deregister virtual project from Nacos: {}", 
                    endpoint.getEndpointName(), e);
        }
    }
    
    /**
     * 聚合所有服务的Provider
     * 
     * 从不同实际项目的服务中收集Provider，去重后返回
     */
    private List<com.pajk.mcpmetainfo.core.model.ProviderInfo> aggregateProviders(List<ProjectService> projectServices) {
        Map<String, com.pajk.mcpmetainfo.core.model.ProviderInfo> uniqueProviders = new LinkedHashMap<>();
        
        log.info("Starting to aggregate providers from {} project services", projectServices.size());
        
        for (ProjectService projectService : projectServices) {
            if (!projectService.getEnabled()) {
                log.debug("Skipping disabled service: {}:{}:{}", 
                        projectService.getServiceInterface(),
                        projectService.getServiceVersion(),
                        projectService.getServiceGroup());
                continue;
            }
            
            // 从ProviderService中获取该服务的所有Provider
            // 注意：group可能为null或空字符串，需要特殊处理
            String serviceGroup = projectService.getServiceGroup();
            log.info("Looking for providers: {}:{}:{}", 
                    projectService.getServiceInterface(),
                    projectService.getServiceVersion(),
                    serviceGroup);
            
            List<com.pajk.mcpmetainfo.core.model.ProviderInfo> allProviders = providerService.getAllProviders();
            log.debug("Total providers available: {}", allProviders.size());
            
            List<com.pajk.mcpmetainfo.core.model.ProviderInfo> providers = allProviders.stream()
                    .filter(p -> {
                        boolean interfaceMatch = p.getInterfaceName().equals(projectService.getServiceInterface());
                        if (!interfaceMatch) {
                            return false;
                        }
                        
                        // version匹配：如果serviceVersion为null或空，匹配所有version；否则精确匹配
                        String serviceVersion = projectService.getServiceVersion();
                        boolean versionMatch;
                        if (serviceVersion == null || serviceVersion.isEmpty()) {
                            versionMatch = true; // 如果未指定版本，匹配所有版本
                            log.debug("ServiceVersion is null/empty, matching all versions");
                        } else {
                            versionMatch = Objects.equals(p.getVersion(), serviceVersion);
                        }
                        if (!versionMatch) {
                            log.debug("Version mismatch: serviceVersion={}, providerVersion={}", serviceVersion, p.getVersion());
                            return false;
                        }
                        
                        // group匹配：如果serviceGroup为null或空，匹配所有group；否则精确匹配
                        // 注意：虚拟项目中的 serviceGroup 可能是 "mcp-server"（用于标识），
                        // 但实际的 Provider 的 group 可能是 "demo" 或其他值
                        // 如果 serviceGroup 是 "mcp-server" 或 null/空，则忽略 group 匹配（匹配所有 group）
                        String providerGroup = p.getGroup();
                        boolean groupMatch;
                        if (serviceGroup == null || serviceGroup.isEmpty()) {
                            // 如果 serviceGroup 为 null 或空，匹配所有 group（虚拟项目通常不指定 group）
                            groupMatch = true;
                            log.debug("ServiceGroup is null/empty (virtual project), matching all groups");
                        } else if ("mcp-server".equals(serviceGroup)) {
                            // 如果 serviceGroup 是 "mcp-server"，这是虚拟项目的标识，不用于匹配 Provider
                            // 匹配所有 group 的 Provider
                            groupMatch = true;
                            log.debug("ServiceGroup is 'mcp-server' (virtual project identifier), matching all groups");
                        } else {
                            groupMatch = Objects.equals(providerGroup, serviceGroup);
                        }
                        if (!groupMatch) {
                            log.debug("Group mismatch: serviceGroup={}, providerGroup={}", serviceGroup, providerGroup);
                            return false;
                        }
                        
                        boolean online = p.isOnline();
                        if (!online) {
                            log.debug("Provider is offline: {}:{}:{}", p.getInterfaceName(), p.getVersion(), p.getGroup());
                            return false;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
            
            log.info("Found {} providers for service {}:{}:{} (requested group: {})", 
                    providers.size(), 
                    projectService.getServiceInterface(), 
                    projectService.getServiceVersion(),
                    serviceGroup,
                    serviceGroup);
            
            // 去重：使用接口名+版本+分组作为key，确保不同服务接口都被保留
            // 即使它们来自同一个地址和端口（同一个应用可能提供多个服务）
            for (com.pajk.mcpmetainfo.core.model.ProviderInfo provider : providers) {
                String key = provider.getInterfaceName() + ":" + 
                            (provider.getVersion() != null ? provider.getVersion() : "") + ":" +
                            (provider.getGroup() != null ? provider.getGroup() : "");
                if (!uniqueProviders.containsKey(key)) {
                    uniqueProviders.put(key, provider);
                    log.info("Added provider: {}:{}:{} at {}:{} (methods: {})", 
                            provider.getInterfaceName(),
                            provider.getVersion(),
                            provider.getGroup(),
                            provider.getAddress(),
                            provider.getPort(),
                            provider.getMethods());
                } else {
                    log.debug("Skipping duplicate provider: {}:{}:{} (already added)", 
                            provider.getInterfaceName(),
                            provider.getVersion(),
                            provider.getGroup());
                }
            }
        }
        
        log.info("Aggregated {} unique providers from {} project services", 
                uniqueProviders.size(), projectServices.size());
        
        return new ArrayList<>(uniqueProviders.values());
    }
    
    /**
     * 获取虚拟项目的工具列表（用于预览）
     */
    public List<Map<String, Object>> getVirtualProjectTools(Long virtualProjectId) {
        // 注意：虚拟项目存储在ProjectManagementService中（通过addProjectService时同步）
        // 但Project对象可能不在projectCache中，需要从projectServiceCache中获取服务列表
        List<ProjectService> projectServices = projectManagementService.getProjectServices(virtualProjectId);
        if (projectServices == null || projectServices.isEmpty()) {
            log.warn("Virtual project {} has no services", virtualProjectId);
            return Collections.emptyList();
        }
        
        log.info("Getting tools for virtual project {} with {} services", virtualProjectId, projectServices.size());
        
        List<com.pajk.mcpmetainfo.core.model.ProviderInfo> providers = aggregateProviders(projectServices);
        log.info("Aggregated {} providers for virtual project {} (from {} services)", 
                providers.size(), virtualProjectId, projectServices.size());
        
        if (providers.isEmpty()) {
            log.warn("No providers found for virtual project {}", virtualProjectId);
            return Collections.emptyList();
        }
        
        // 生成工具列表（复用NacosMcpRegistrationService的逻辑）
        List<Map<String, Object>> tools = generateToolsFromProviders(providers);
        log.info("Generated {} tools for virtual project {}", tools.size(), virtualProjectId);
        
        return tools;
    }
    
    /**
     * 从Provider生成工具列表
     * 根据实际方法参数生成 inputSchema，而不是固定需要 args 和 timeout
     */
    private List<Map<String, Object>> generateToolsFromProviders(List<com.pajk.mcpmetainfo.core.model.ProviderInfo> providers) {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        for (com.pajk.mcpmetainfo.core.model.ProviderInfo provider : providers) {
            if (provider.getMethods() != null && !provider.getMethods().isEmpty()) {
                String[] methods = provider.getMethods().split(",");
                for (String methodName : methods) {
                    methodName = methodName.trim();
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
            }
        }
        
        return tools;
    }
    
}

