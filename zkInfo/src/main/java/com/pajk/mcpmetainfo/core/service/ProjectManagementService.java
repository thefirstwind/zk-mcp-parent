package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.Project;
import com.pajk.mcpmetainfo.core.model.ProjectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 项目管理服务
 * 
 * 负责项目的创建、管理和服务关联
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-01-15
 */
@Slf4j
@Service
public class ProjectManagementService {
    
    private ServiceCollectionFilterService filterService;
    
    // 项目缓存：projectId -> Project
    private final Map<Long, Project> projectCache = new ConcurrentHashMap<>();
    
    // 项目服务关联缓存：projectId -> List<ProjectService>
    private final Map<Long, List<ProjectService>> projectServiceCache = new ConcurrentHashMap<>();
    
    // 服务到项目的反向索引：serviceKey -> Set<projectId>
    private final Map<String, Set<Long>> serviceToProjectsIndex = new ConcurrentHashMap<>();
    
    @Autowired(required = false)
    @Lazy
    public void setFilterService(ServiceCollectionFilterService filterService) {
        this.filterService = filterService;
    }
    
    /**
     * 创建项目
     */
    public Project createProject(Project project) {
        if (project.getId() == null) {
            project.setId(System.currentTimeMillis()); // 临时ID生成
        }
        
        projectCache.put(project.getId(), project);
        log.info("Created project: id={}, code={}, name={}", 
                project.getId(), project.getProjectCode(), project.getProjectName());
        
        return project;
    }
    
    /**
     * 获取项目
     */
    public Project getProject(Long projectId) {
        return projectCache.get(projectId);
    }
    
    /**
     * 根据项目代码获取项目
     */
    public Project getProjectByCode(String projectCode) {
        return projectCache.values().stream()
                .filter(p -> projectCode.equals(p.getProjectCode()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 获取所有项目
     */
    public List<Project> getAllProjects() {
        return new ArrayList<>(projectCache.values());
    }
    
    /**
     * 获取活跃项目
     */
    public List<Project> getActiveProjects() {
        return projectCache.values().stream()
                .filter(p -> p.getStatus() == Project.ProjectStatus.ACTIVE)
                .collect(Collectors.toList());
    }
    
    /**
     * 添加项目服务关联
     */
    public void addProjectService(ProjectService projectService) {
        Long projectId = projectService.getProjectId();
        
        // 添加到项目服务列表
        projectServiceCache.computeIfAbsent(projectId, k -> new ArrayList<>())
                .add(projectService);
        
        // 更新反向索引
        String serviceKey = projectService.buildServiceKey();
        serviceToProjectsIndex.computeIfAbsent(serviceKey, k -> ConcurrentHashMap.newKeySet())
                .add(projectId);
        
        // 同步到过滤服务
        if (filterService != null) {
            filterService.addProjectService(projectId, 
                    projectService.getServiceInterface(), 
                    projectService.getServiceVersion());
        }
        
        log.info("Added project service: projectId={}, service={}", 
                projectId, serviceKey);
    }
    
    /**
     * 移除项目服务关联
     */
    public void removeProjectService(Long projectId, String serviceInterface, String version) {
        List<ProjectService> services = projectServiceCache.get(projectId);
        if (services != null) {
            services.removeIf(ps -> 
                    ps.getServiceInterface().equals(serviceInterface) &&
                    Objects.equals(ps.getServiceVersion(), version));
        }
        
        // 更新反向索引
        String serviceKey = String.format("%s:%s", serviceInterface, version);
        Set<Long> projectIds = serviceToProjectsIndex.get(serviceKey);
        if (projectIds != null) {
            projectIds.remove(projectId);
            if (projectIds.isEmpty()) {
                serviceToProjectsIndex.remove(serviceKey);
            }
        }
        
        // 同步到过滤服务
        if (filterService != null) {
            filterService.removeProjectService(projectId, serviceInterface, version);
        }
        
        log.info("Removed project service: projectId={}, service={}", 
                projectId, serviceKey);
    }
    
    /**
     * 获取项目的所有服务
     */
    public List<ProjectService> getProjectServices(Long projectId) {
        return new ArrayList<>(projectServiceCache.getOrDefault(projectId, Collections.emptyList()));
    }
    
    /**
     * 检查服务是否在项目中
     */
    public boolean isServiceInProject(String serviceInterface, String version, Long projectId) {
        String serviceKey = String.format("%s:%s", serviceInterface, version);
        Set<Long> projectIds = serviceToProjectsIndex.get(serviceKey);
        return projectIds != null && projectIds.contains(projectId);
    }
    
    /**
     * 检查服务是否在任何项目中
     */
    public boolean isServiceInAnyProject(String serviceInterface, String version) {
        String serviceKey = String.format("%s:%s", serviceInterface, version);
        Set<Long> projectIds = serviceToProjectsIndex.get(serviceKey);
        return projectIds != null && !projectIds.isEmpty();
    }
    
    /**
     * 获取服务所属的项目列表
     * 注意：serviceToProjectsIndex 使用的 key 格式是 interface:version:group
     * 但这里只提供 interface 和 version，需要匹配所有 group
     */
    public List<Project> getProjectsByService(String serviceInterface, String version) {
        // 由于 serviceToProjectsIndex 的 key 格式是 interface:version:group
        // 我们需要查找所有匹配的 key（忽略 group）
        String baseKey = String.format("%s:%s", serviceInterface, version);
        Set<Long> projectIds = new HashSet<>();
        
        log.debug("🔍 Searching for projects by service: {}:{} (baseKey: {})", serviceInterface, version, baseKey);
        log.debug("🔍 serviceToProjectsIndex size: {}", serviceToProjectsIndex.size());
        
        // 遍历所有 serviceKey，查找匹配的
        for (Map.Entry<String, Set<Long>> entry : serviceToProjectsIndex.entrySet()) {
            String key = entry.getKey();
            // 检查 key 是否以 baseKey 开头（忽略 group）
            // 支持两种格式：interface:version:group 或 interface:version（无 group）
            if (key.startsWith(baseKey + ":")) {
                projectIds.addAll(entry.getValue());
                log.debug("✅ Matched key: {} -> {} projects", key, entry.getValue().size());
            } else if (key.equals(baseKey)) {
                // 完全匹配（无 group）
                projectIds.addAll(entry.getValue());
                log.debug("✅ Matched exact key: {} -> {} projects", key, entry.getValue().size());
            } else if (key.equals(baseKey + ":default")) {
                // 也匹配 default group
                projectIds.addAll(entry.getValue());
                log.debug("✅ Matched default group key: {} -> {} projects", key, entry.getValue().size());
            }
        }
        
        log.info("🔍 Found {} projects for service {}:{}", projectIds.size(), serviceInterface, version);
        
        if (projectIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        return projectIds.stream()
                .map(this::getProject)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 清除所有缓存
     */
    public void clearCache() {
        projectCache.clear();
        projectServiceCache.clear();
        serviceToProjectsIndex.clear();
        log.info("Cleared all project caches");
    }
}

