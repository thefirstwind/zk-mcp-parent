package com.pajk.mcpmetainfo.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 虚拟项目Endpoint映射实体
 * 虚拟项目对应 mcp-router-v3 的 endpoint
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualProjectEndpoint {
    
    /**
     * 映射ID
     */
    private Long id;
    
    /**
     * 虚拟项目ID
     */
    private Long virtualProjectId;
    
    /**
     * Endpoint名称（对应mcp-router-v3的serviceName）
     */
    private String endpointName;
    
    /**
     * Endpoint路径（如：/sse/{endpointName}）
     */
    private String endpointPath;
    
    /**
     * MCP服务名称（注册到Nacos的名称）
     */
    private String mcpServiceName;
    
    /**
     * Endpoint描述
     */
    private String description;
    
    /**
     * 状态：ACTIVE, INACTIVE
     */
    private EndpointStatus status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * Endpoint状态枚举
     */
    public enum EndpointStatus {
        ACTIVE,    // 活跃
        INACTIVE   // 非活跃
    }
}

