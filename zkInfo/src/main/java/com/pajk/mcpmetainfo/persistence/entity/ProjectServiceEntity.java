package com.pajk.mcpmetainfo.persistence.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pajk.mcpmetainfo.core.model.ProjectService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 项目服务关联实体（数据库映射版本）
 * 
 * 对应数据库中的zk_project_service表，存储项目与服务的关系
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-12-17
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectServiceEntity {
    
    /**
     * 关联ID
     */
    private Long id;
    
    /**
     * 项目ID
     */
    private Long projectId;
    
    /**
     * 服务接口（完整路径）
     */
    private String serviceInterface;
    
    /**
     * 服务版本
     */
    private String serviceVersion;
    
    /**
     * 服务分组
     */
    private String serviceGroup;
    
    /**
     * 优先级（虚拟项目中用于排序）
     */
    private Integer priority;
    
    /**
     * 是否启用
     */
    private Boolean enabled;
    
    /**
     * 添加时间
     */
    private LocalDateTime addedAt;
    
    /**
     * 添加人ID
     */
    private Long addedBy;
    
    /**
     * 从 ProjectService 模型转换为 ProjectServiceEntity
     */
    public static ProjectServiceEntity fromProjectService(ProjectService projectService) {
        ProjectServiceEntity entity = new ProjectServiceEntity();
        entity.setId(projectService.getId());
        entity.setProjectId(projectService.getProjectId());
        entity.setServiceInterface(projectService.getServiceInterface());
        entity.setServiceVersion(projectService.getServiceVersion());
        entity.setServiceGroup(projectService.getServiceGroup());
        entity.setPriority(projectService.getPriority());
        entity.setEnabled(projectService.getEnabled());
        entity.setAddedAt(projectService.getAddedAt());
        entity.setAddedBy(projectService.getAddedBy());
        return entity;
    }
    
    /**
     * 转换为 ProjectService 模型
     */
    public ProjectService toProjectService() {
        ProjectService projectService = new ProjectService();
        projectService.setId(this.id);
        projectService.setProjectId(this.projectId);
        projectService.setServiceInterface(this.serviceInterface);
        projectService.setServiceVersion(this.serviceVersion);
        projectService.setServiceGroup(this.serviceGroup);
        projectService.setPriority(this.priority);
        projectService.setEnabled(this.enabled);
        projectService.setAddedAt(this.addedAt);
        projectService.setAddedBy(this.addedBy);
        return projectService;
    }
}





