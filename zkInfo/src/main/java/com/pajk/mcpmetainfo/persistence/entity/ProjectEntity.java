package com.pajk.mcpmetainfo.persistence.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pajk.mcpmetainfo.core.model.Project;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 项目实体（数据库映射版本）
 * 
 * 对应数据库中的zk_project表，存储项目信息（实际项目 + 虚拟项目）
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
public class ProjectEntity {
    
    /**
     * 项目ID
     */
    private Long id;
    
    /**
     * 项目代码（唯一标识）
     */
    private String projectCode;
    
    /**
     * 项目名称
     */
    private String projectName;
    
    /**
     * 项目类型：REAL（实际项目）, VIRTUAL（虚拟项目）
     */
    private Project.ProjectType projectType;
    
    /**
     * 项目描述
     */
    private String description;
    
    /**
     * 项目负责人ID
     */
    private Long ownerId;
    
    /**
     * 项目负责人姓名
     */
    private String ownerName;
    
    /**
     * 状态：ACTIVE, INACTIVE, DELETED
     */
    private Project.ProjectStatus status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 从 Project 模型转换为 ProjectEntity
     */
    public static ProjectEntity fromProject(Project project) {
        ProjectEntity entity = new ProjectEntity();
        entity.setId(project.getId());
        entity.setProjectCode(project.getProjectCode());
        entity.setProjectName(project.getProjectName());
        entity.setProjectType(project.getProjectType());
        entity.setDescription(project.getDescription());
        entity.setOwnerId(project.getOwnerId());
        entity.setOwnerName(project.getOwnerName());
        entity.setStatus(project.getStatus());
        entity.setCreatedAt(project.getCreatedAt());
        entity.setUpdatedAt(project.getUpdatedAt());
        return entity;
    }
    
    /**
     * 转换为 Project 模型
     */
    public Project toProject() {
        Project project = new Project();
        project.setId(this.id);
        project.setProjectCode(this.projectCode);
        project.setProjectName(this.projectName);
        project.setProjectType(this.projectType);
        project.setDescription(this.description);
        project.setOwnerId(this.ownerId);
        project.setOwnerName(this.ownerName);
        project.setStatus(this.status);
        project.setCreatedAt(this.createdAt);
        project.setUpdatedAt(this.updatedAt);
        return project;
    }
}

