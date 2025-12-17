package com.zkinfo.service;

import com.zkinfo.model.Project;
import com.zkinfo.model.ProjectService;
import com.zkinfo.model.VirtualProjectEndpoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 虚拟项目管理服务
 * 
 * 负责虚拟项目的创建、管理和服务编排
 * 虚拟项目可以组合不同实际项目的服务，对应 mcp-router-v3 的 endpoint
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-01-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualProjectService {
    
    private final ProjectManagementService projectManagementService;
    private final VirtualProjectRegistrationService registrationService;
    
    // 虚拟项目缓存：virtualProjectId -> Project
    private final Map<Long, Project> virtualProjectCache = new ConcurrentHashMap<>();
    
    // 虚拟项目Endpoint映射缓存：virtualProjectId -> VirtualProjectEndpoint
    private final Map<Long, VirtualProjectEndpoint> endpointCache = new ConcurrentHashMap<>();
    
    /**
     * 创建虚拟项目
     * 
     * @param request 创建请求
     * @return 创建的虚拟项目
     */
    public VirtualProjectInfo createVirtualProject(CreateVirtualProjectRequest request) {
        // 1. 创建项目记录（类型为VIRTUAL）
        Project project = Project.builder()
                .id(System.currentTimeMillis()) // 临时ID生成
                .projectCode("VIRTUAL_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .projectName(request.getName())
                .projectType(Project.ProjectType.VIRTUAL)
                .description(request.getDescription())
                .status(Project.ProjectStatus.ACTIVE)
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();
        
        virtualProjectCache.put(project.getId(), project);
        // 同时存储到ProjectManagementService，以便统一管理
        projectManagementService.createProject(project);
        log.info("Created virtual project: id={}, code={}, name={}", 
                project.getId(), project.getProjectCode(), project.getProjectName());
        
        // 2. 关联服务（从不同实际项目中选择）
        for (ServiceSelection selection : request.getServices()) {
            log.info("Processing service selection: interface={}, version={}, group={}", 
                    selection.getServiceInterface(), selection.getVersion(), selection.getGroup());
            
            ProjectService projectService = ProjectService.builder()
                    .projectId(project.getId())
                    .serviceInterface(selection.getServiceInterface())
                    .serviceVersion(selection.getVersion())
                    .serviceGroup(selection.getGroup())
                    .priority(selection.getPriority() != null ? selection.getPriority() : 0)
                    .enabled(true)
                    .addedAt(java.time.LocalDateTime.now())
                    .build();
            
            log.info("Created ProjectService: interface={}, version={}, group={}", 
                    projectService.getServiceInterface(), 
                    projectService.getServiceVersion(), 
                    projectService.getServiceGroup());
            
            projectManagementService.addProjectService(projectService);
        }
        
        // 3. 创建Endpoint映射
        VirtualProjectEndpoint endpoint = VirtualProjectEndpoint.builder()
                .virtualProjectId(project.getId())
                .endpointName(request.getEndpointName())
                .endpointPath("/sse/" + request.getEndpointName())
                .mcpServiceName("mcp-" + request.getEndpointName())
                .description(request.getDescription())
                .status(VirtualProjectEndpoint.EndpointStatus.ACTIVE)
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();
        
        endpointCache.put(project.getId(), endpoint);
        log.info("Created virtual project endpoint: endpointName={}, mcpServiceName={}", 
                endpoint.getEndpointName(), endpoint.getMcpServiceName());
        
        // 4. 注册到Nacos（作为独立的MCP服务）
        if (request.isAutoRegister()) {
            registrationService.registerVirtualProjectToNacos(project, endpoint);
        }
        
        return buildVirtualProjectInfo(project, endpoint);
    }
    
    /**
     * 获取虚拟项目
     */
    public VirtualProjectInfo getVirtualProject(Long virtualProjectId) {
        Project project = virtualProjectCache.get(virtualProjectId);
        if (project == null) {
            return null;
        }
        
        VirtualProjectEndpoint endpoint = endpointCache.get(virtualProjectId);
        return buildVirtualProjectInfo(project, endpoint);
    }
    
    /**
     * 获取所有虚拟项目
     */
    public List<VirtualProjectInfo> getAllVirtualProjects() {
        return virtualProjectCache.values().stream()
                .map(project -> {
                    VirtualProjectEndpoint endpoint = endpointCache.get(project.getId());
                    return buildVirtualProjectInfo(project, endpoint);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 更新虚拟项目的服务列表
     */
    public void updateVirtualProjectServices(Long virtualProjectId, List<ServiceSelection> services) {
        Project project = virtualProjectCache.get(virtualProjectId);
        if (project == null) {
            throw new IllegalArgumentException("Virtual project not found: " + virtualProjectId);
        }
        
        // 清除旧的服务关联
        List<ProjectService> oldServices = projectManagementService.getProjectServices(virtualProjectId);
        for (ProjectService oldService : oldServices) {
            projectManagementService.removeProjectService(
                    virtualProjectId,
                    oldService.getServiceInterface(),
                    oldService.getServiceVersion()
            );
        }
        
        // 添加新的服务关联
        for (ServiceSelection selection : services) {
            ProjectService projectService = ProjectService.builder()
                    .projectId(virtualProjectId)
                    .serviceInterface(selection.getServiceInterface())
                    .serviceVersion(selection.getVersion())
                    .serviceGroup(selection.getGroup())
                    .priority(selection.getPriority() != null ? selection.getPriority() : 0)
                    .enabled(true)
                    .addedAt(java.time.LocalDateTime.now())
                    .build();
            
            projectManagementService.addProjectService(projectService);
        }
        
        // 重新注册到Nacos
        VirtualProjectEndpoint endpoint = endpointCache.get(virtualProjectId);
        if (endpoint != null) {
            registrationService.reregisterVirtualProjectToNacos(project, endpoint);
        }
        
        log.info("Updated virtual project services: virtualProjectId={}, serviceCount={}", 
                virtualProjectId, services.size());
    }
    
    /**
     * 删除虚拟项目
     */
    public void deleteVirtualProject(Long virtualProjectId) {
        Project project = virtualProjectCache.get(virtualProjectId);
        if (project == null) {
            return;
        }
        
        // 注销Nacos注册
        VirtualProjectEndpoint endpoint = endpointCache.get(virtualProjectId);
        if (endpoint != null) {
            registrationService.deregisterVirtualProjectFromNacos(endpoint);
        }
        
        // 清除服务关联
        List<ProjectService> services = projectManagementService.getProjectServices(virtualProjectId);
        for (ProjectService service : services) {
            projectManagementService.removeProjectService(
                    virtualProjectId,
                    service.getServiceInterface(),
                    service.getServiceVersion()
            );
        }
        
        // 删除缓存
        virtualProjectCache.remove(virtualProjectId);
        endpointCache.remove(virtualProjectId);
        
        log.info("Deleted virtual project: virtualProjectId={}", virtualProjectId);
    }
    
    /**
     * 根据endpointName获取虚拟项目
     */
    public VirtualProjectInfo getVirtualProjectByEndpointName(String endpointName) {
        for (Map.Entry<Long, VirtualProjectEndpoint> entry : endpointCache.entrySet()) {
            if (endpointName.equals(entry.getValue().getEndpointName())) {
                Project project = virtualProjectCache.get(entry.getKey());
                if (project != null) {
                    return buildVirtualProjectInfo(project, entry.getValue());
                }
            }
        }
        return null;
    }
    
    /**
     * 根据endpointName获取Endpoint
     */
    public VirtualProjectEndpoint getEndpointByEndpointName(String endpointName) {
        for (VirtualProjectEndpoint endpoint : endpointCache.values()) {
            if (endpointName.equals(endpoint.getEndpointName())) {
                return endpoint;
            }
        }
        return null;
    }
    
    /**
     * 根据projectId获取Endpoint
     */
    public VirtualProjectEndpoint getEndpointByProjectId(Long projectId) {
        return endpointCache.get(projectId);
    }
    
    /**
     * 构建虚拟项目信息
     */
    private VirtualProjectInfo buildVirtualProjectInfo(Project project, VirtualProjectEndpoint endpoint) {
        List<ProjectService> services = projectManagementService.getProjectServices(project.getId());
        
        return VirtualProjectInfo.builder()
                .project(project)
                .endpoint(endpoint)
                .services(services)
                .serviceCount(services.size())
                .build();
    }
    
    /**
     * 创建虚拟项目请求
     */
    public static class CreateVirtualProjectRequest {
        @com.fasterxml.jackson.annotation.JsonProperty("projectName")
        private String name;
        
        private String description;
        private String endpointName;
        private List<ServiceSelection> services;
        private boolean autoRegister = true;
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getEndpointName() { return endpointName; }
        public void setEndpointName(String endpointName) { this.endpointName = endpointName; }
        
        public List<ServiceSelection> getServices() { return services; }
        public void setServices(List<ServiceSelection> services) { this.services = services; }
        
        public boolean isAutoRegister() { return autoRegister; }
        public void setAutoRegister(boolean autoRegister) { this.autoRegister = autoRegister; }
    }
    
    /**
     * 服务选择
     */
    public static class ServiceSelection {
        @com.fasterxml.jackson.annotation.JsonProperty("serviceInterface")
        private String serviceInterface;
        
        @com.fasterxml.jackson.annotation.JsonProperty("serviceVersion")
        private String version;
        
        @com.fasterxml.jackson.annotation.JsonProperty("serviceGroup")
        private String group;
        
        private Integer priority;
        
        // Getters and Setters
        public String getServiceInterface() { return serviceInterface; }
        public void setServiceInterface(String serviceInterface) { this.serviceInterface = serviceInterface; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
        
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
    }
    
    /**
     * 虚拟项目信息
     */
    public static class VirtualProjectInfo {
        private Project project;
        private VirtualProjectEndpoint endpoint;
        private List<ProjectService> services;
        private int serviceCount;
        
        // Builder
        public static VirtualProjectInfoBuilder builder() {
            return new VirtualProjectInfoBuilder();
        }
        
        // Getters and Setters
        public Project getProject() { return project; }
        public void setProject(Project project) { this.project = project; }
        
        public VirtualProjectEndpoint getEndpoint() { return endpoint; }
        public void setEndpoint(VirtualProjectEndpoint endpoint) { this.endpoint = endpoint; }
        
        public List<ProjectService> getServices() { return services; }
        public void setServices(List<ProjectService> services) { this.services = services; }
        
        public int getServiceCount() { return serviceCount; }
        public void setServiceCount(int serviceCount) { this.serviceCount = serviceCount; }
        
        public static class VirtualProjectInfoBuilder {
            private Project project;
            private VirtualProjectEndpoint endpoint;
            private List<ProjectService> services;
            private int serviceCount;
            
            public VirtualProjectInfoBuilder project(Project project) {
                this.project = project;
                return this;
            }
            
            public VirtualProjectInfoBuilder endpoint(VirtualProjectEndpoint endpoint) {
                this.endpoint = endpoint;
                return this;
            }
            
            public VirtualProjectInfoBuilder services(List<ProjectService> services) {
                this.services = services;
                return this;
            }
            
            public VirtualProjectInfoBuilder serviceCount(int serviceCount) {
                this.serviceCount = serviceCount;
                return this;
            }
            
            public VirtualProjectInfo build() {
                VirtualProjectInfo info = new VirtualProjectInfo();
                info.setProject(project);
                info.setEndpoint(endpoint);
                info.setServices(services);
                info.setServiceCount(serviceCount);
                return info;
            }
        }
    }
}

