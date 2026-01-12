package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.Project;
import com.pajk.mcpmetainfo.core.model.ProjectService;
import com.pajk.mcpmetainfo.core.model.VirtualProjectEndpoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private VirtualProjectRegistrationService registrationService; // 使用 @Lazy 延迟加载避免循环依赖
    
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private InterfaceWhitelistService interfaceWhitelistService;
    
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.pajk.mcpmetainfo.persistence.mapper.ProjectMapper projectMapper;
    
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.pajk.mcpmetainfo.persistence.mapper.VirtualProjectEndpointMapper virtualProjectEndpointMapper;
    
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.pajk.mcpmetainfo.persistence.mapper.ProjectServiceMapper projectServiceMapper;
    
    // 虚拟项目缓存：virtualProjectId -> Project
    private final Map<Long, Project> virtualProjectCache = new ConcurrentHashMap<>();
    
    // 虚拟项目Endpoint映射缓存：virtualProjectId -> VirtualProjectEndpoint
    private final Map<Long, VirtualProjectEndpoint> endpointCache = new ConcurrentHashMap<>();
    
    /**
     * 应用启动完成后，从数据库加载虚拟项目到内存缓存
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadVirtualProjectsFromDatabase() {
        if (projectMapper == null || virtualProjectEndpointMapper == null) {
            log.warn("ProjectMapper or VirtualProjectEndpointMapper is not available, skip loading virtual projects from database");
            return;
        }
        
        // 异步执行，避免阻塞启动
        CompletableFuture.runAsync(() -> {
            try {
                log.info("🚀 开始从数据库加载虚拟项目...");
                long startTime = System.currentTimeMillis();
                
                // 1. 查询所有虚拟项目（project_type = 'VIRTUAL'）
                List<com.pajk.mcpmetainfo.persistence.entity.ProjectEntity> projectEntities = 
                        projectMapper.findByProjectType("VIRTUAL");
                
                Map<Long, Project> projects = new HashMap<>();
                for (com.pajk.mcpmetainfo.persistence.entity.ProjectEntity entity : projectEntities) {
                    if (entity.getStatus() == Project.ProjectStatus.ACTIVE) {
                        Project project = entity.toProject();
                        projects.put(project.getId(), project);
                    }
                }
                
                log.info("从数据库加载到 {} 个虚拟项目", projects.size());
                
                // 2. 查询所有虚拟项目 endpoint
                List<com.pajk.mcpmetainfo.persistence.entity.VirtualProjectEndpointEntity> endpointEntities = 
                        virtualProjectEndpointMapper.findByStatus("ACTIVE");
                
                Map<Long, VirtualProjectEndpoint> endpoints = new HashMap<>();
                for (com.pajk.mcpmetainfo.persistence.entity.VirtualProjectEndpointEntity entity : endpointEntities) {
                    VirtualProjectEndpoint endpoint = entity.toVirtualProjectEndpoint();
                    endpoints.put(endpoint.getVirtualProjectId(), endpoint);
                }
                
                log.info("从数据库加载到 {} 个虚拟项目 endpoint", endpoints.size());
                
                // 3. 查询所有 ProjectService（如果 ProjectServiceMapper 可用）
                Map<Long, List<ProjectService>> projectServicesMap = new HashMap<>();
                if (projectServiceMapper != null) {
                    List<com.pajk.mcpmetainfo.persistence.entity.ProjectServiceEntity> serviceEntities = 
                            projectServiceMapper.findAll();
                    for (com.pajk.mcpmetainfo.persistence.entity.ProjectServiceEntity entity : serviceEntities) {
                        Long projectId = entity.getProjectId();
                        if (projects.containsKey(projectId)) { // 只加载虚拟项目的服务
                            projectServicesMap.computeIfAbsent(projectId, k -> new ArrayList<>())
                                    .add(entity.toProjectService());
                        }
                    }
                    log.info("从数据库加载到 {} 个 ProjectService 关联", serviceEntities.size());
                } else {
                    log.warn("⚠️ ProjectServiceMapper is not available, skip loading ProjectService from database");
                }
                
                // 4. 加载到内存缓存
                for (Map.Entry<Long, Project> entry : projects.entrySet()) {
                    Long projectId = entry.getKey();
                    Project project = entry.getValue();
                    
                    // 加载到 VirtualProjectService 缓存
                    virtualProjectCache.put(projectId, project);
                    
                    // 加载到 ProjectManagementService 缓存
                    projectManagementService.createProject(project);
                    
                    // 加载 endpoint
                    VirtualProjectEndpoint endpoint = endpoints.get(projectId);
                    if (endpoint != null) {
                        endpointCache.put(projectId, endpoint);
                    }
                    
                    // 加载 ProjectService
                    List<ProjectService> projectServices = projectServicesMap.get(projectId);
                    if (projectServices != null && !projectServices.isEmpty()) {
                        for (ProjectService projectService : projectServices) {
                            projectManagementService.addProjectService(projectService);
                        }
                        log.info("✅ 加载了 {} 个 ProjectService 到项目 {} (projectId={})", 
                                projectServices.size(), project.getProjectName(), projectId);
                    } else {
                        log.warn("⚠️ 项目 {} (projectId={}) 没有 ProjectService，可能服务未正确保存到数据库", 
                                project.getProjectName(), projectId);
                    }
                }
                
                long duration = System.currentTimeMillis() - startTime;
                log.info("✅ 虚拟项目加载完成，共加载 {} 个项目，{} 个 endpoint，{} 个 ProjectService，总耗时: {}ms", 
                        projects.size(), endpoints.size(), 
                        projectServicesMap.values().stream().mapToInt(List::size).sum(), 
                        duration);
                
            } catch (Exception e) {
                log.error("❌ 从数据库加载虚拟项目失败", e);
            }
        });
    }
    
    /**
     * 创建虚拟项目
     * 
     * @param request 创建请求
     * @return 创建的虚拟项目
     */
    public VirtualProjectInfo createVirtualProject(CreateVirtualProjectRequest request) {
        // 1. 创建项目记录（类型为VIRTUAL）
        // 确保 projectName 不为 null（数据库字段是 NOT NULL）
        String projectName = request.getName();
        if (projectName == null || projectName.trim().isEmpty()) {
            projectName = "Virtual Project " + System.currentTimeMillis(); // 使用时间戳作为默认值
            log.warn("⚠️ Project name is null or empty, using default: {}", projectName);
        }
        
        String projectCode = "VIRTUAL_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Project project = Project.builder()
                .id(null) // 不设置ID，让数据库自动生成（使用AUTO_INCREMENT）
                .projectCode(projectCode)
                .projectName(projectName)
                .projectType(Project.ProjectType.VIRTUAL)
                .description(request.getDescription())
                .status(Project.ProjectStatus.ACTIVE)
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();
        
        // 3. 创建Endpoint映射（先创建，但 virtualProjectId 会在持久化后设置）
        // 注意：mcpServiceName 使用 "virtual-{endpointName}" 格式，与 VirtualProjectRegistrationService 保持一致
        VirtualProjectEndpoint endpoint = VirtualProjectEndpoint.builder()
                .virtualProjectId(null) // 先设置为 null，持久化后会更新
                .endpointName(request.getEndpointName())
                .endpointPath("/sse/" + request.getEndpointName())
                .mcpServiceName("virtual-" + request.getEndpointName())
                .description(request.getDescription())
                .status(VirtualProjectEndpoint.EndpointStatus.ACTIVE)
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();
        
        // 4. 先持久化到数据库获取ID（在注册到 Nacos 之前，确保数据已保存）
        persistVirtualProjectToDatabase(project, endpoint);
        
        // 持久化后，project.id 和 endpoint.virtualProjectId 已经被设置
        // 现在可以安全地放入缓存和创建关联对象
        
        virtualProjectCache.put(project.getId(), project);
        // 同时存储到ProjectManagementService，以便统一管理
        projectManagementService.createProject(project);
        log.info("Created virtual project: id={}, code={}, name={}", 
                project.getId(), project.getProjectCode(), project.getProjectName());
        
        endpointCache.put(project.getId(), endpoint);
        log.info("Created virtual project endpoint: endpointName={}, mcpServiceName={}", 
                endpoint.getEndpointName(), endpoint.getMcpServiceName());
        
        // 2. 关联服务（从不同实际项目中选择）
        // 检查所需服务是否在白名单中（如果配置了白名单）
        if (interfaceWhitelistService != null && interfaceWhitelistService.isWhitelistConfigured()) {
            for (ServiceSelection selection : request.getServices()) {
                if (!interfaceWhitelistService.isAllowed(selection.getServiceInterface())) {
                    log.warn("⚠️ Service {} is not in whitelist, virtual project may not work correctly. " +
                            "Please add it to whitelist or ensure the service is already persisted.", 
                            selection.getServiceInterface());
                }
            }
        }
        
        for (ServiceSelection selection : request.getServices()) {
            log.info("Processing service selection: interface={}, version={}, group={}", 
                    selection.getServiceInterface(), selection.getVersion(), selection.getGroup());
            
            ProjectService projectService = ProjectService.builder()
                    .projectId(project.getId()) // 此时 project.getId() 已经有值了
                    .serviceInterface(selection.getServiceInterface())
                    .serviceVersion(selection.getVersion())
                    .serviceGroup(selection.getGroup())
                    .priority(selection.getPriority() != null ? selection.getPriority() : 0)
                    .enabled(true)
                    .addedAt(java.time.LocalDateTime.now())
                    .build();
            
            log.info("Created ProjectService: interface={}, version={}, group={}, projectId={}", 
                    projectService.getServiceInterface(), 
                    projectService.getServiceVersion(), 
                    projectService.getServiceGroup(),
                    projectService.getProjectId());
            
            projectManagementService.addProjectService(projectService);
            log.debug("✅ Added ProjectService to ProjectManagementService cache: projectId={}, service={}", 
                    project.getId(), projectService.buildServiceKey());
        }
        
        // 验证服务是否已添加到缓存
        List<ProjectService> addedServices = projectManagementService.getProjectServices(project.getId());
        log.info("📋 After adding services, project {} has {} services in cache", 
                project.getId(), addedServices != null ? addedServices.size() : 0);
        
        // 5. 注册到Nacos（作为独立的MCP服务）
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
     * 删除虚拟项目（通过 ID）
     */
    public void deleteVirtualProject(Long virtualProjectId) {
        Project project = virtualProjectCache.get(virtualProjectId);
        if (project == null) {
            log.warn("Virtual project not found in memory: virtualProjectId={}, will try to delete from Nacos", virtualProjectId);
            // 即使内存中没有，也尝试从 Nacos 删除（可能服务重启后内存丢失）
            // 但无法确定 endpointName，所以只能记录警告
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
     * 删除虚拟项目（通过 endpointName）
     * 即使内存中没有虚拟项目，也能从 Nacos 删除
     */
    public boolean deleteVirtualProjectByEndpointName(String endpointName) {
        // 1. 先尝试从内存中查找
        VirtualProjectEndpoint endpoint = getEndpointByEndpointName(endpointName);
        if (endpoint != null) {
            // 找到虚拟项目，使用完整的删除流程
            Long virtualProjectId = endpoint.getVirtualProjectId();
            deleteVirtualProject(virtualProjectId);
            return true;
        }
        
        // 2. 内存中没有，直接从 Nacos 删除
        log.warn("Virtual project not found in memory: endpointName={}, will delete from Nacos directly", endpointName);
        try {
            // 构建服务名称（virtual-{endpointName}）
            String serviceName = "virtual-" + endpointName;
            registrationService.deregisterVirtualProjectFromNacosByServiceName(serviceName, "1.0.0");
            log.info("✅ Deleted virtual project from Nacos (not in memory): endpointName={}, serviceName={}", 
                    endpointName, serviceName);
            return true;
        } catch (Exception e) {
            log.error("❌ Failed to delete virtual project from Nacos: endpointName={}", endpointName, e);
            return false;
        }
    }
    
    /**
     * 删除虚拟项目（通过 serviceName，从 Nacos 查询）
     * 支持删除内存中不存在的虚拟项目
     */
    public boolean deleteVirtualProjectByServiceName(String serviceName) {
        // 如果 serviceName 以 virtual- 开头，提取 endpointName
        String endpointName = serviceName;
        if (serviceName.startsWith("virtual-")) {
            endpointName = serviceName.substring("virtual-".length());
        }
        
        // 尝试通过 endpointName 删除
        return deleteVirtualProjectByEndpointName(endpointName);
    }
    
    /**
     * 根据endpointName获取虚拟项目
     * 多节点环境下，如果内存缓存中没有，尝试从数据库加载
     */
    public VirtualProjectInfo getVirtualProjectByEndpointName(String endpointName) {
        // 1. 先从内存缓存中查找
        for (Map.Entry<Long, VirtualProjectEndpoint> entry : endpointCache.entrySet()) {
            if (endpointName.equals(entry.getValue().getEndpointName())) {
                Project project = virtualProjectCache.get(entry.getKey());
                if (project != null) {
                    return buildVirtualProjectInfo(project, entry.getValue());
                }
            }
        }
        
        // 2. 如果内存缓存中没有，尝试从数据库加载（多节点环境下，不同节点的缓存可能不同步）
        if (virtualProjectEndpointMapper != null) {
            try {
                com.pajk.mcpmetainfo.persistence.entity.VirtualProjectEndpointEntity endpointEntity = 
                        virtualProjectEndpointMapper.findByEndpointName(endpointName);
                if (endpointEntity != null && endpointEntity.getStatus() == 
                        com.pajk.mcpmetainfo.core.model.VirtualProjectEndpoint.EndpointStatus.ACTIVE) {
                    Long projectId = endpointEntity.getVirtualProjectId();
                    if (projectId != null && projectMapper != null) {
                        // 从数据库加载项目信息
                        com.pajk.mcpmetainfo.persistence.entity.ProjectEntity projectEntity = 
                                projectMapper.findById(projectId);
                        if (projectEntity != null && projectEntity.getProjectType() == 
                                com.pajk.mcpmetainfo.core.model.Project.ProjectType.VIRTUAL) {
                            Project project = projectEntity.toProject();
                            VirtualProjectEndpoint endpoint = endpointEntity.toVirtualProjectEndpoint();
                            
                            // 加载到内存缓存（供后续使用）
                            virtualProjectCache.put(projectId, project);
                            endpointCache.put(projectId, endpoint);
                            
                            log.info("✅ Loaded virtual project from database: endpointName={}, projectId={}", 
                                    endpointName, projectId);
                            return buildVirtualProjectInfo(project, endpoint);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("⚠️ Failed to load virtual project from database for endpoint '{}': {}", 
                        endpointName, e.getMessage());
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
     * 持久化虚拟项目到数据库
     * 保存 Project 和 VirtualProjectEndpoint 到数据库
     * 使用 MyBatis Mapper，与其他服务保持一致
     */
    private void persistVirtualProjectToDatabase(Project project, VirtualProjectEndpoint endpoint) {
        if (projectMapper == null || virtualProjectEndpointMapper == null) {
            log.warn("ProjectMapper or VirtualProjectEndpointMapper is not available, skip persisting virtual project to database");
            return;
        }
        
        try {
            // 1. 保存 Project 到 zk_project 表
            // 确保 projectName 不为 null（数据库字段是 NOT NULL）
            String projectName = project.getProjectName();
            if (projectName == null || projectName.trim().isEmpty()) {
                projectName = project.getProjectCode(); // 使用 projectCode 作为默认值
                log.warn("⚠️ Project name is null or empty, using projectCode as default: {}", projectName);
                project.setProjectName(projectName);
            }
            
            com.pajk.mcpmetainfo.persistence.entity.ProjectEntity projectEntity = 
                    com.pajk.mcpmetainfo.persistence.entity.ProjectEntity.fromProject(project);
            projectMapper.insert(projectEntity);
            // 插入后，数据库会自动生成ID并设置到 projectEntity.id 中（useGeneratedKeys=true）
            // 需要更新 project 对象的 id，以便后续使用
            if (projectEntity.getId() != null) {
                project.setId(projectEntity.getId());
                log.info("✅ Persisted virtual project to database: projectId={}, projectName={}", 
                        project.getId(), projectName);
            } else {
                log.warn("⚠️ Project inserted but ID not generated: projectName={}", projectName);
            }
            
            // 2. 保存 VirtualProjectEndpoint 到 zk_virtual_project_endpoint 表
            // 确保 endpoint 的 virtualProjectId 已设置（使用刚才生成的 project.id）
            if (endpoint.getVirtualProjectId() == null && project.getId() != null) {
                endpoint.setVirtualProjectId(project.getId());
            }
            com.pajk.mcpmetainfo.persistence.entity.VirtualProjectEndpointEntity endpointEntity = 
                    com.pajk.mcpmetainfo.persistence.entity.VirtualProjectEndpointEntity.fromVirtualProjectEndpoint(endpoint);
            virtualProjectEndpointMapper.insert(endpointEntity);
            log.info("✅ Persisted virtual project endpoint to database: endpointName={}, virtualProjectId={}", 
                    endpoint.getEndpointName(), endpoint.getVirtualProjectId());
            
                // 3. 保存 ProjectService 到 zk_project_service 表
            if (projectServiceMapper != null) {
                // 先删除该项目的所有旧服务关联（避免重复）
                projectServiceMapper.deleteByProjectId(project.getId());
                
                // 获取项目的所有服务
                List<ProjectService> projectServices = projectManagementService.getProjectServices(project.getId());
                if (projectServices != null && !projectServices.isEmpty()) {
                    log.info("📋 Saving {} ProjectService(s) to database for projectId={}", 
                            projectServices.size(), project.getId());
                    for (ProjectService projectService : projectServices) {
                        com.pajk.mcpmetainfo.persistence.entity.ProjectServiceEntity serviceEntity = 
                                com.pajk.mcpmetainfo.persistence.entity.ProjectServiceEntity.fromProjectService(projectService);
                        projectServiceMapper.insert(serviceEntity);
                        log.debug("✅ Persisted ProjectService to database: projectId={}, service={}", 
                                project.getId(), projectService.buildServiceKey());
                    }
                    log.info("✅ Persisted {} ProjectService(s) to database: projectId={}", 
                            projectServices.size(), project.getId());
                } else {
                    log.warn("⚠️ No ProjectService to persist for projectId={} (services may not have been added to ProjectManagementService)", 
                            project.getId());
                }
            } else {
                log.warn("⚠️ ProjectServiceMapper is not available, skip persisting ProjectService to database");
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to persist virtual project to database: projectId={}, endpointName={}", 
                    project.getId(), endpoint != null ? endpoint.getEndpointName() : "null", e);
            // 不抛出异常，允许继续执行（注册到 Nacos 等后续操作）
        }
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

