package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.Project;
import com.pajk.mcpmetainfo.core.model.ProjectService;
import com.pajk.mcpmetainfo.core.model.VirtualProjectEndpoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


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
    private final ProviderService providerService; // 保留，用于其他场景
    private final DubboServiceDbService dubboServiceDbService; // 用于从 zk_dubbo_* 表查询
    
    @Autowired
    private VirtualProjectService virtualProjectService; // 使用字段注入避免循环依赖
    

    
    @Autowired(required = false)
    private InterfaceWhitelistService interfaceWhitelistService;
    
    @Autowired(required = false)
    private com.pajk.mcpmetainfo.persistence.mapper.DubboServiceMethodMapper dubboServiceMethodMapper;
    
    @Autowired(required = false)
    private com.pajk.mcpmetainfo.persistence.mapper.VirtualProjectEndpointMapper virtualProjectEndpointMapper;
    
    
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
     * 通过服务名称直接从 Nacos 注销虚拟项目
     * 用于删除内存中不存在的虚拟项目
     */
    public void deregisterVirtualProjectFromNacosByServiceName(String serviceName, String version) {
        try {
            nacosMcpRegistrationService.deregisterVirtualProjectMcpService(serviceName, version);
            log.info("✅ Deregistered virtual project from Nacos by serviceName: {}", serviceName);
        } catch (Exception e) {
            log.error("❌ Failed to deregister virtual project from Nacos by serviceName: {}", serviceName, e);
            throw new RuntimeException("Failed to deregister virtual project from Nacos", e);
        }
    }
    
    /**
     * 当 Dubbo 服务提供者发生变化时，刷新所有包含该服务的虚拟项目
     * 
     * @param serviceInterface 接口名
     * @param version 版本
     */
    public void refreshVirtualProjectsByService(String serviceInterface, String version) {
        log.info("🔄 Refreshing virtual projects containing service: {}:{}", serviceInterface, version);
        
        // 1. 查找包含该服务的所有项目
        List<Project> projects = projectManagementService.getProjectsByService(serviceInterface, version);
        
        if (projects.isEmpty()) {
            log.debug("No virtual projects found containing service: {}:{}", serviceInterface, version);
            return;
        }
        
        // 2. 对每个项目，重新注册到 Nacos（更新 metadata 和 tools）
        for (Project project : projects) {
            if (project.getProjectType() == Project.ProjectType.VIRTUAL) {
                VirtualProjectEndpoint endpoint = virtualProjectService.getEndpointByProjectId(project.getId());
                if (endpoint != null) {
                    log.info("🔄 Re-registering virtual project due to service update: {}", project.getProjectName());
                    // 直接调用注册逻辑
                    registerVirtualProjectToNacos(project, endpoint);
                }
            }
        }
    }
    
    /**
     * 聚合所有服务的Provider
     * 
     * 从不同实际项目的服务中收集Provider，去重后返回
     * 优化：优先使用 service_id 直接查询，提高效率
     */
    private List<com.pajk.mcpmetainfo.core.model.ProviderInfo> aggregateProviders(List<ProjectService> projectServices) {
        Map<String, com.pajk.mcpmetainfo.core.model.ProviderInfo> uniqueProviders = new LinkedHashMap<>();
        
        log.info("🔍 Starting to aggregate providers from {} project services", projectServices.size());
        
        for (ProjectService projectService : projectServices) {
            if (!projectService.getEnabled()) {
                log.warn("⚠️ Skipping disabled service: {}:{}:{}", 
                        projectService.getServiceInterface(),
                        projectService.getServiceVersion(),
                        projectService.getServiceGroup());
                continue;
            }
            
            log.info("📋 Processing service: {}:{}:{} (serviceId={}, enabled={})", 
                    projectService.getServiceInterface(),
                    projectService.getServiceVersion(),
                    projectService.getServiceGroup(),
                    projectService.getServiceId(),
                    projectService.getEnabled());
            
            List<com.pajk.mcpmetainfo.core.model.ProviderInfo> providers;
            
            // 优化：优先使用 service_id 直接查询（如果存在）
            if (projectService.getServiceId() != null) {
                log.info("Using service_id {} to query providers directly: {}:{}:{}", 
                        projectService.getServiceId(),
                        projectService.getServiceInterface(),
                        projectService.getServiceVersion(),
                        projectService.getServiceGroup());
                
                providers = dubboServiceDbService.getProvidersByServiceId(projectService.getServiceId());
                log.info("Found {} providers using service_id {}", providers.size(), projectService.getServiceId());
            } else {
                // 回退到模糊匹配（原有逻辑）
                String serviceGroup = projectService.getServiceGroup();
                log.info("Using fuzzy matching to query providers: {}:{}:{}", 
                        projectService.getServiceInterface(),
                        projectService.getServiceVersion(),
                        serviceGroup);
                
                List<com.pajk.mcpmetainfo.core.model.ProviderInfo> allProviders = dubboServiceDbService.getAllProvidersFromDubboTables();
                log.debug("Total providers available from zk_dubbo_* tables: {}", allProviders.size());
                
                // 记录匹配前的统计信息
                String targetInterface = projectService.getServiceInterface();
                long interfaceMatchCount = allProviders.stream()
                    .filter(p -> p.getInterfaceName() != null && p.getInterfaceName().equals(targetInterface))
                    .count();
                log.info("📊 Matching statistics for {}: totalProviders={}, interfaceMatch={}", 
                        targetInterface, allProviders.size(), interfaceMatchCount);
                
                // 如果接口名匹配的Provider数量为0，记录所有可用的接口名（用于调试）
                if (interfaceMatchCount == 0 && !allProviders.isEmpty()) {
                    Set<String> availableInterfaces = allProviders.stream()
                        .map(com.pajk.mcpmetainfo.core.model.ProviderInfo::getInterfaceName)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                    log.warn("⚠️ No providers found with interface '{}'. Available interfaces: {}", 
                            targetInterface, availableInterfaces);
                }
                
                providers = allProviders.stream()
                    .filter(p -> {
                        // 接口名匹配
                        boolean interfaceMatch = Objects.equals(p.getInterfaceName(), projectService.getServiceInterface());
                        if (!interfaceMatch) {
                            log.debug("Interface mismatch: expected={}, actual={}", 
                                    projectService.getServiceInterface(), p.getInterfaceName());
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
                        
                        // 在线状态检查
                        boolean online = p.isOnline();
                        if (!online) {
                            log.debug("Provider is offline: {}:{}:{}", p.getInterfaceName(), p.getVersion(), p.getGroup());
                            return false;
                        }
                        
                        log.debug("✅ Provider matched: {}:{}:{} at {}:{}", 
                                p.getInterfaceName(), p.getVersion(), p.getGroup(), p.getAddress(), p.getPort());
                        return true;
                    })
                    .collect(Collectors.toList());
            }
            
            log.info("Found {} providers for service {}:{}:{} (serviceId={})", 
                    providers.size(), 
                    projectService.getServiceInterface(), 
                    projectService.getServiceVersion(),
                    projectService.getServiceGroup(),
                    projectService.getServiceId());
            
            // 数据完整性检查：如果 methods 为空，尝试从数据库补全
            for (com.pajk.mcpmetainfo.core.model.ProviderInfo provider : providers) {
                // 检查 methods 是否为空
                if (provider.getMethods() == null || provider.getMethods().isEmpty()) {
                    // 尝试从 zk_dubbo_service_method 查询
                    if (projectService.getServiceId() != null) {
                        try {
                            List<com.pajk.mcpmetainfo.persistence.entity.DubboServiceMethodEntity> methods = 
                                dubboServiceMethodMapper.findByServiceId(projectService.getServiceId());
                            if (methods != null && !methods.isEmpty()) {
                                String methodsStr = methods.stream()
                                    .map(com.pajk.mcpmetainfo.persistence.entity.DubboServiceMethodEntity::getMethodName)
                                    .collect(Collectors.joining(","));
                                provider.setMethods(methodsStr);
                                log.info("✅ Fixed methods for provider: {} -> {} methods", 
                                        provider.getInterfaceName(), methods.size());
                            }
                        } catch (Exception e) {
                            log.warn("Failed to fix methods for provider: {}", provider.getInterfaceName(), e);
                        }
                    }
                }
            }
            
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
     * 获取虚拟项目的工具列表（从 Nacos 查询）
     * 不再依赖 zk_project 和 zk_project_service，直接从 Nacos 查询工具配置
     */
    public List<Map<String, Object>> getVirtualProjectToolsByEndpointName(String endpointName) {
        if (endpointName == null || endpointName.isEmpty()) {
            log.warn("Endpoint name is null or empty");
            return Collections.emptyList();
        }
        
        // 去掉 virtual- 前缀（如果存在）
        String actualEndpoint = endpointName;
        if (endpointName.startsWith("virtual-")) {
            actualEndpoint = endpointName.substring("virtual-".length());
        }
        
        log.info("🔍 Getting tools for virtual project by endpointName: {}", actualEndpoint);
        
        // 1. 验证 endpoint 是否存在（从数据库查询）
        if (virtualProjectEndpointMapper != null) {
            try {
                com.pajk.mcpmetainfo.persistence.entity.VirtualProjectEndpointEntity entity = 
                        virtualProjectEndpointMapper.findByEndpointName(actualEndpoint);
                if (entity == null || entity.getStatus() != com.pajk.mcpmetainfo.core.model.VirtualProjectEndpoint.EndpointStatus.ACTIVE) {
                    log.warn("⚠️ Virtual project endpoint not found or not active: {}", actualEndpoint);
                    return Collections.emptyList();
                }
            } catch (Exception e) {
                log.warn("⚠️ Failed to verify endpoint from database: {}, error: {}", actualEndpoint, e.getMessage());
            }
        }
        
        // 2. 从 Nacos 查询工具配置
        // 服务名格式：virtual-{endpointName}
        String serviceName = "virtual-" + actualEndpoint;
        String serviceGroup = nacosMcpRegistrationService.getServiceGroup();
        
        log.info("📦 Querying tools from Nacos: serviceName={}, serviceGroup={}", serviceName, serviceGroup);
        
        try {
            // 从 Nacos 配置中心获取工具配置
            List<Map<String, Object>> tools = nacosMcpRegistrationService.getToolsFromNacosConfig(serviceName, serviceGroup);
            if (tools != null && !tools.isEmpty()) {
                log.info("✅ Got {} tools from Nacos for virtual project: {}", tools.size(), actualEndpoint);
                return tools;
            }
            
            log.warn("⚠️ No tools found in Nacos config for service: {}", serviceName);
            return Collections.emptyList();
            
        } catch (Exception e) {
            log.error("❌ Failed to get tools from Nacos for service: {}, error: {}", serviceName, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    
    
    
}

