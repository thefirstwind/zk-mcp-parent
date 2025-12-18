package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.Project;
import com.pajk.mcpmetainfo.core.model.VirtualProjectEndpoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Endpoint解析器
 * 根据endpoint名称或ID查找对应的项目（虚拟项目或实际项目）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EndpointResolver {
    
    private final VirtualProjectService virtualProjectService;
    private final ProjectManagementService projectManagementService;
    private final NacosV3ApiService nacosV3ApiService;
    
    /**
     * Endpoint信息
     */
    public static class EndpointInfo {
        private final Project project;
        private final VirtualProjectEndpoint endpoint;
        private final String mcpServiceName;
        private final boolean isVirtualProject;
        
        public EndpointInfo(Project project, VirtualProjectEndpoint endpoint, 
                          String mcpServiceName, boolean isVirtualProject) {
            this.project = project;
            this.endpoint = endpoint;
            this.mcpServiceName = mcpServiceName;
            this.isVirtualProject = isVirtualProject;
        }
        
        public Project getProject() {
            return project;
        }
        
        public VirtualProjectEndpoint getEndpoint() {
            return endpoint;
        }
        
        public String getMcpServiceName() {
            return mcpServiceName;
        }
        
        public boolean isVirtualProject() {
            return isVirtualProject;
        }
        
        public Long getProjectId() {
            return project != null ? project.getId() : null;
        }
    }
    
    /**
     * 根据endpoint解析项目信息
     * endpoint可以是：
     * 1. 虚拟项目的endpointName（如：data-analysis）
     * 2. 虚拟项目的ID（数字字符串，如：1765780528182）
     * 3. 实际项目的projectCode或projectName
     * 
     * @param endpoint endpoint标识
     * @return EndpointInfo，如果未找到则返回null
     */
    public Optional<EndpointInfo> resolveEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return Optional.empty();
        }
        
        log.debug("Resolving endpoint: {}", endpoint);
        
        // 1. 尝试作为虚拟项目的endpointName查找
        // 如果 endpoint 以 virtual- 开头，去掉前缀再查找
        // 如果 endpoint 以 mcp- 开头，也去掉前缀（向后兼容）
        String actualEndpoint = endpoint;
        if (endpoint.startsWith("virtual-")) {
            actualEndpoint = endpoint.substring("virtual-".length());
            log.debug("🔍 Endpoint '{}' starts with virtual-, using '{}' for lookup", endpoint, actualEndpoint);
        } else if (endpoint.startsWith("mcp-")) {
            actualEndpoint = endpoint.substring("mcp-".length());
            log.debug("🔍 Endpoint '{}' starts with mcp-, using '{}' for lookup", endpoint, actualEndpoint);
        }
        
        VirtualProjectService.VirtualProjectInfo virtualProject = 
                virtualProjectService.getVirtualProjectByEndpointName(actualEndpoint);
        if (virtualProject != null && virtualProject.getProject() != null) {
            Project project = virtualProject.getProject();
            VirtualProjectEndpoint virtualEndpoint = virtualProject.getEndpoint();
            // 使用 virtual-{endpointName} 作为 mcpServiceName
            String mcpServiceName = "virtual-" + actualEndpoint;
            log.info("✅ Resolved endpoint '{}' as virtual project endpoint: {} (service name: {})", 
                    endpoint, actualEndpoint, mcpServiceName);
            return Optional.of(new EndpointInfo(project, virtualEndpoint, mcpServiceName, true));
        }
        
        // 如果内存中找不到虚拟项目，尝试从 Nacos 查找 virtual-{actualEndpoint} 服务
        // 这样可以支持服务已注册到 Nacos 但内存中还没有缓存的情况
        String virtualServiceName = "virtual-" + actualEndpoint;
        try {
            List<Map<String, Object>> instances = nacosV3ApiService.getInstanceList(
                    virtualServiceName, "mcp-server", null, true);
            if (instances != null && !instances.isEmpty()) {
                // 找到健康实例，说明服务已注册到 Nacos
                log.info("✅ Found virtual project service '{}' in Nacos (not in memory cache), " +
                        "using service name: {}", actualEndpoint, virtualServiceName);
                // 返回 EndpointInfo，project 和 endpoint 为 null，但 mcpServiceName 正确
                return Optional.of(new EndpointInfo(null, null, virtualServiceName, true));
            }
        } catch (Exception e) {
            log.debug("⚠️ Failed to check Nacos for virtual service '{}': {}", virtualServiceName, e.getMessage());
        }
        
        // 2. 尝试作为虚拟项目的ID查找（数字字符串）
        try {
            Long projectId = Long.parseLong(endpoint);
            VirtualProjectService.VirtualProjectInfo vp = virtualProjectService.getVirtualProject(projectId);
            if (vp != null) {
                Project project = projectManagementService.getProject(projectId);
                if (project != null) {
                    VirtualProjectEndpoint virtualEndpoint = virtualProjectService.getEndpointByProjectId(projectId);
                    // 使用 endpointName 作为 mcpServiceName（不添加 mcp- 前缀）
                    String mcpServiceName = virtualEndpoint != null ? virtualEndpoint.getEndpointName() : endpoint;
                    log.debug("Resolved endpoint '{}' as virtual project ID: {} (service name: {})", 
                            endpoint, projectId, mcpServiceName);
                    return Optional.of(new EndpointInfo(project, virtualEndpoint, mcpServiceName, true));
                }
            }
        } catch (NumberFormatException e) {
            // 不是数字，继续查找
        }
        
        // 3. 尝试作为实际项目的projectCode查找
        Project project = projectManagementService.getProjectByCode(endpoint);
        if (project != null && project.getProjectType() == Project.ProjectType.REAL) {
            // 实际项目使用 zk-mcp-{interface}-{version} 格式的服务名
            // 但这里我们需要一个统一的MCP服务名，可以使用项目名称
            String mcpServiceName = "zk-mcp-project-" + endpoint.toLowerCase().replaceAll("[^a-z0-9]", "-");
            log.debug("Resolved endpoint '{}' as real project code: {}", endpoint, mcpServiceName);
            return Optional.of(new EndpointInfo(project, null, mcpServiceName, false));
        }
        
        // 4. 尝试作为实际项目的projectName查找
        for (Project p : projectManagementService.getAllProjects()) {
            if (p.getProjectType() == Project.ProjectType.REAL && 
                endpoint.equalsIgnoreCase(p.getProjectName())) {
                String mcpServiceName = "zk-mcp-project-" + p.getProjectCode().toLowerCase().replaceAll("[^a-z0-9]", "-");
                log.debug("Resolved endpoint '{}' as real project name: {}", endpoint, mcpServiceName);
                return Optional.of(new EndpointInfo(p, null, mcpServiceName, false));
            }
        }
        
        // 5. 尝试作为 MCP 服务名称解析（格式：zk-mcp-{interface}-{version}）
        // 例如：zk-mcp-com-zkinfo-demo-service-orderservice-1.0.0
        if (endpoint.startsWith("zk-mcp-")) {
            String withoutPrefix = endpoint.substring("zk-mcp-".length());
            // 提取版本号（格式：x.y.z），版本号在最后，用最后一个 "-" 分隔
            int lastDash = withoutPrefix.lastIndexOf("-");
            if (lastDash > 0) {
                String interfacePart = withoutPrefix.substring(0, lastDash);
                String version = withoutPrefix.substring(lastDash + 1);
                // 将连字符替换为点，得到接口名（小写）
                String interfaceNameLower = interfacePart.replace("-", ".");
                
                log.info("🔍 Extracting from MCP service name '{}': interface={}, version={}", endpoint, interfaceNameLower, version);
                
                // 查找包含该接口和版本的项目（先尝试小写）
                // 注意：getProjectsByService 只使用 interface:version，不包含 group
                List<Project> matchingProjects = projectManagementService.getProjectsByService(interfaceNameLower, version);
                
                // 如果没找到，尝试驼峰命名（假设最后一个单词是 Service）
                if ((matchingProjects == null || matchingProjects.isEmpty()) && interfaceNameLower.contains(".")) {
                    String[] parts = interfaceNameLower.split("\\.");
                    if (parts.length > 0) {
                        String lastPart = parts[parts.length - 1];
                        
                        // 如果以 "service" 结尾，尝试转换为驼峰命名
                        // orderservice -> OrderService
                        // userservice -> UserService
                        // productservice -> ProductService
                        if (lastPart.endsWith("service") && lastPart.length() > 7) {
                            String prefix = lastPart.substring(0, lastPart.length() - 7); // 去掉 "service"
                            if (!prefix.isEmpty()) {
                                // 将前缀首字母大写，加上 Service
                                String camelCaseLastPart = prefix.substring(0, 1).toUpperCase() + 
                                        (prefix.length() > 1 ? prefix.substring(1) : "") + 
                                        "Service";
                                parts[parts.length - 1] = camelCaseLastPart;
                                String interfaceNameCamelCase = String.join(".", parts);
                                log.info("🔍 Trying camelCase interface name: {}", interfaceNameCamelCase);
                                matchingProjects = projectManagementService.getProjectsByService(interfaceNameCamelCase, version);
                            }
                        }
                        
                        // 如果还是没找到，尝试简单首字母大写
                        if ((matchingProjects == null || matchingProjects.isEmpty())) {
                            String camelCaseSimple = lastPart.substring(0, 1).toUpperCase() + lastPart.substring(1);
                            parts[parts.length - 1] = camelCaseSimple;
                            String interfaceNameCamelCaseSimple = String.join(".", parts);
                            log.info("🔍 Trying simple camelCase interface name: {}", interfaceNameCamelCaseSimple);
                            matchingProjects = projectManagementService.getProjectsByService(interfaceNameCamelCaseSimple, version);
                        }
                    }
                }
                
                if (matchingProjects != null && !matchingProjects.isEmpty()) {
                    Project matchedProject = matchingProjects.get(0);
                    log.info("✅ Resolved MCP service name '{}' to project: {} (projectId: {})", 
                            endpoint, matchedProject.getProjectCode(), matchedProject.getId());
                    return Optional.of(new EndpointInfo(matchedProject, null, endpoint, false));
                } else {
                    log.warn("⚠️ No project found for service: {}:{} (tried both lowercase and camelCase). " +
                            "Please ensure the service is registered in a project.", interfaceNameLower, version);
                }
            } else {
                log.warn("⚠️ Invalid MCP service name format: {} (cannot extract version)", endpoint);
            }
        }
        
        log.warn("Endpoint not found: {}", endpoint);
        return Optional.empty();
    }
    
    /**
     * 根据endpoint获取MCP服务名称
     */
    public Optional<String> getMcpServiceName(String endpoint) {
        return resolveEndpoint(endpoint).map(EndpointInfo::getMcpServiceName);
    }
}

