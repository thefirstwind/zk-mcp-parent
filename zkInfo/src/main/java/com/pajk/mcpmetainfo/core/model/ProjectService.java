package com.pajk.mcpmetainfo.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 项目服务关联实体
 * service + version 为最小粒度
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectService {
    
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
     * 关联的 Dubbo 服务 ID（引用 zk_dubbo_service.id，可选）
     * 如果存在，可以直接通过 service_id 查询，提高效率
     */
    private Long serviceId;
    
    /**
     * 构建服务唯一标识
     */
    public String buildServiceKey() {
        return String.format("%s:%s:%s",
                serviceInterface,
                serviceVersion != null ? serviceVersion : "default",
                serviceGroup != null ? serviceGroup : "default"
        );
    }
}

