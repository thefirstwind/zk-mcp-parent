package com.pajk.mcpmetainfo.persistence.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pajk.mcpmetainfo.core.model.VirtualProjectEndpoint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 虚拟项目端点实体（数据库映射版本）
 * 
 * 对应数据库中的zk_virtual_project_endpoint表，存储虚拟项目的端点映射信息
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
public class VirtualProjectEndpointEntity {
    
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
    private VirtualProjectEndpoint.EndpointStatus status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 从 VirtualProjectEndpoint 模型转换为 VirtualProjectEndpointEntity
     */
    public static VirtualProjectEndpointEntity fromVirtualProjectEndpoint(VirtualProjectEndpoint endpoint) {
        VirtualProjectEndpointEntity entity = new VirtualProjectEndpointEntity();
        entity.setId(endpoint.getId());
        entity.setVirtualProjectId(endpoint.getVirtualProjectId());
        entity.setEndpointName(endpoint.getEndpointName());
        entity.setEndpointPath(endpoint.getEndpointPath());
        entity.setMcpServiceName(endpoint.getMcpServiceName());
        entity.setDescription(endpoint.getDescription());
        entity.setStatus(endpoint.getStatus());
        entity.setCreatedAt(endpoint.getCreatedAt());
        entity.setUpdatedAt(endpoint.getUpdatedAt());
        return entity;
    }
    
    /**
     * 转换为 VirtualProjectEndpoint 模型
     */
    public VirtualProjectEndpoint toVirtualProjectEndpoint() {
        VirtualProjectEndpoint endpoint = new VirtualProjectEndpoint();
        endpoint.setId(this.id);
        endpoint.setVirtualProjectId(this.virtualProjectId);
        endpoint.setEndpointName(this.endpointName);
        endpoint.setEndpointPath(this.endpointPath);
        endpoint.setMcpServiceName(this.mcpServiceName);
        endpoint.setDescription(this.description);
        endpoint.setStatus(this.status);
        endpoint.setCreatedAt(this.createdAt);
        endpoint.setUpdatedAt(this.updatedAt);
        return endpoint;
    }
}


