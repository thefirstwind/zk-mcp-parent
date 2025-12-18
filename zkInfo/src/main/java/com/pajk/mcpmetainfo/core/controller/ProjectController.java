package com.pajk.mcpmetainfo.core.controller;

import com.pajk.mcpmetainfo.core.model.Project;
import com.pajk.mcpmetainfo.core.model.ProjectService;
import com.pajk.mcpmetainfo.core.service.ProjectManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目管理API控制器
 * 
 * 提供项目的创建、查询、更新、删除等REST API
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-01-15
 */
@Slf4j
@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ProjectController {
    
    private final ProjectManagementService projectManagementService;
    
    /**
     * 创建项目
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createProject(@RequestBody CreateProjectRequest request) {
        try {
            Project project = Project.builder()
                    .projectCode(request.getProjectCode())
                    .projectName(request.getProjectName())
                    .projectType(request.getProjectType() != null ? 
                            Project.ProjectType.valueOf(request.getProjectType()) : 
                            Project.ProjectType.REAL)
                    .description(request.getDescription())
                    .ownerId(request.getOwnerId())
                    .ownerName(request.getOwnerName())
                    .status(request.getStatus() != null ? 
                            Project.ProjectStatus.valueOf(request.getStatus()) : 
                            Project.ProjectStatus.ACTIVE)
                    .createdAt(java.time.LocalDateTime.now())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();
            
            Project created = projectManagementService.createProject(project);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", created.getId());
            response.put("projectCode", created.getProjectCode());
            response.put("projectName", created.getProjectName());
            response.put("projectType", created.getProjectType());
            response.put("status", created.getStatus());
            response.put("message", "项目创建成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("创建项目失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "创建项目失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 获取所有项目
     */
    @GetMapping
    public ResponseEntity<List<Project>> getAllProjects() {
        try {
            List<Project> projects = projectManagementService.getAllProjects();
            return ResponseEntity.ok(projects);
        } catch (Exception e) {
            log.error("获取项目列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取项目详情
     */
    @GetMapping("/{projectId}")
    public ResponseEntity<Project> getProject(@PathVariable Long projectId) {
        try {
            Project project = projectManagementService.getProject(projectId);
            if (project == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(project);
        } catch (Exception e) {
            log.error("获取项目详情失败: {}", projectId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取活跃项目
     */
    @GetMapping("/active")
    public ResponseEntity<List<Project>> getActiveProjects() {
        try {
            List<Project> projects = projectManagementService.getActiveProjects();
            return ResponseEntity.ok(projects);
        } catch (Exception e) {
            log.error("获取活跃项目列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 添加服务到项目
     */
    @PostMapping("/{projectId}/services")
    public ResponseEntity<Map<String, Object>> addProjectService(
            @PathVariable Long projectId,
            @RequestBody AddServiceRequest request) {
        try {
            ProjectService projectService = ProjectService.builder()
                    .projectId(projectId)
                    .serviceInterface(request.getServiceInterface())
                    .serviceVersion(request.getServiceVersion())
                    .serviceGroup(request.getServiceGroup())
                    .priority(request.getPriority() != null ? request.getPriority() : 0)
                    .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                    .addedAt(java.time.LocalDateTime.now())
                    .addedBy(request.getAddedBy())
                    .build();
            
            projectManagementService.addProjectService(projectService);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "服务关联成功");
            response.put("projectId", projectId);
            response.put("serviceInterface", request.getServiceInterface());
            response.put("serviceVersion", request.getServiceVersion());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("添加项目服务失败: projectId={}", projectId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "添加项目服务失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 获取项目的所有服务
     */
    @GetMapping("/{projectId}/services")
    public ResponseEntity<List<ProjectService>> getProjectServices(@PathVariable Long projectId) {
        try {
            List<ProjectService> services = projectManagementService.getProjectServices(projectId);
            return ResponseEntity.ok(services);
        } catch (Exception e) {
            log.error("获取项目服务列表失败: projectId={}", projectId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 移除项目服务
     */
    @DeleteMapping("/{projectId}/services")
    public ResponseEntity<Map<String, Object>> removeProjectService(
            @PathVariable Long projectId,
            @RequestParam String serviceInterface,
            @RequestParam String version) {
        try {
            projectManagementService.removeProjectService(projectId, serviceInterface, version);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "服务关联已移除");
            response.put("projectId", projectId);
            response.put("serviceInterface", serviceInterface);
            response.put("version", version);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("移除项目服务失败: projectId={}", projectId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "移除项目服务失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 检查服务是否在项目中
     */
    @GetMapping("/{projectId}/services/check")
    public ResponseEntity<Map<String, Object>> checkServiceInProject(
            @PathVariable Long projectId,
            @RequestParam String serviceInterface,
            @RequestParam String version) {
        try {
            boolean inProject = projectManagementService.isServiceInProject(
                    serviceInterface, version, projectId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("projectId", projectId);
            response.put("serviceInterface", serviceInterface);
            response.put("version", version);
            response.put("inProject", inProject);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("检查服务是否在项目中失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取服务所属的项目列表
     */
    @GetMapping("/by-service")
    public ResponseEntity<List<Project>> getProjectsByService(
            @RequestParam String serviceInterface,
            @RequestParam String version) {
        try {
            List<Project> projects = projectManagementService.getProjectsByService(
                    serviceInterface, version);
            return ResponseEntity.ok(projects);
        } catch (Exception e) {
            log.error("获取服务所属项目列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 创建项目请求
     */
    public static class CreateProjectRequest {
        private String projectCode;
        private String projectName;
        private String projectType; // REAL, VIRTUAL
        private String description;
        private Long ownerId;
        private String ownerName;
        private String status; // ACTIVE, INACTIVE, DELETED
        
        // Getters and Setters
        public String getProjectCode() { return projectCode; }
        public void setProjectCode(String projectCode) { this.projectCode = projectCode; }
        
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
        
        public String getProjectType() { return projectType; }
        public void setProjectType(String projectType) { this.projectType = projectType; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public Long getOwnerId() { return ownerId; }
        public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
        
        public String getOwnerName() { return ownerName; }
        public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
    
    /**
     * 添加服务请求
     */
    public static class AddServiceRequest {
        private String serviceInterface;
        private String serviceVersion;
        private String serviceGroup;
        private Integer priority;
        private Boolean enabled;
        private Long addedBy;
        
        // Getters and Setters
        public String getServiceInterface() { return serviceInterface; }
        public void setServiceInterface(String serviceInterface) { this.serviceInterface = serviceInterface; }
        
        public String getServiceVersion() { return serviceVersion; }
        public void setServiceVersion(String serviceVersion) { this.serviceVersion = serviceVersion; }
        
        public String getServiceGroup() { return serviceGroup; }
        public void setServiceGroup(String serviceGroup) { this.serviceGroup = serviceGroup; }
        
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
        
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        
        public Long getAddedBy() { return addedBy; }
        public void setAddedBy(Long addedBy) { this.addedBy = addedBy; }
    }
}

