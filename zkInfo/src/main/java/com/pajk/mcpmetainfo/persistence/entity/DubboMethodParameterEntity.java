package com.pajk.mcpmetainfo.persistence.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Dubbo服务方法参数信息实体
 * 
 * 对应数据库中的dubbo_method_parameters表，存储服务方法的参数信息
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
public class DubboMethodParameterEntity {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 关联的方法ID
     */
    private Long methodId;
    
    /**
     * 参数名
     */
    private String parameterName;
    
    /**
     * 参数类型
     */
    private String parameterType;
    
    /**
     * 参数顺序
     */
    private Integer parameterOrder;
    
    /**
     * 参数描述
     */
    private String parameterDescription;
    
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
    public DubboMethodParameterEntity(Long methodId, String parameterName, String parameterType, 
                                     Integer parameterOrder, String parameterDescription) {
        this.methodId = methodId;
        this.parameterName = parameterName;
        this.parameterType = parameterType;
        this.parameterOrder = parameterOrder;
        this.parameterDescription = parameterDescription;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}

