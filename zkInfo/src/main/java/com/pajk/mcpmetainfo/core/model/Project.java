package com.pajk.mcpmetainfo.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 项目实体（实际项目 + 虚拟项目）
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {
    
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
    private ProjectType projectType;
    
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
    private ProjectStatus status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 项目类型枚举
     */
    public enum ProjectType {
        REAL,      // 实际项目
        VIRTUAL    // 虚拟项目
    }
    
    /**
     * 项目状态枚举
     */
    public enum ProjectStatus {
        ACTIVE,    // 活跃
        INACTIVE,  // 非活跃
        DELETED    // 已删除
    }
}

