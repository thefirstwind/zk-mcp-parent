package com.pajk.mcpmetainfo.persistence.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dubbo服务方法信息实体
 * 
 * 对应数据库中的dubbo_service_methods表，存储服务接口的方法信息
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class DubboServiceMethodEntity {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 关联的服务ID
     */
    private Long serviceId;
    
    /**
     * 方法名
     */
    private String methodName;
    
    /**
     * 返回值类型
     */
    private String returnType;
    
    /**
     * 方法参数列表
     */
    private List<com.pajk.mcpmetainfo.persistence.entity.DubboMethodParameterEntity> parameters;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 构造函数
     */
    public DubboServiceMethodEntity(Long serviceId, String methodName, String returnType) {
        this.serviceId = serviceId;
        this.methodName = methodName;
        this.returnType = returnType;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}

