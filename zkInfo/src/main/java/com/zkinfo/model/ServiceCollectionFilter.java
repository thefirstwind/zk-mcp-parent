package com.zkinfo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 服务采集过滤规则实体
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCollectionFilter {
    
    /**
     * 规则ID
     */
    private Long id;
    
    /**
     * 过滤类型：PROJECT（项目级）, SERVICE（服务级）, PATTERN（模式匹配）
     */
    private FilterType filterType;
    
    /**
     * 过滤值
     */
    private String filterValue;
    
    /**
     * 操作符：INCLUDE（包含）, EXCLUDE（排除）
     */
    private FilterOperator filterOperator;
    
    /**
     * 优先级（数字越大优先级越高）
     */
    private Integer priority;
    
    /**
     * 是否启用
     */
    private Boolean enabled;
    
    /**
     * 过滤规则描述
     */
    private String description;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 过滤类型枚举
     */
    public enum FilterType {
        PROJECT,   // 项目级
        SERVICE,   // 服务级
        PATTERN,   // 模式匹配
        PREFIX,    // 前缀匹配
        SUFFIX     // 后缀匹配
    }
    
    /**
     * 过滤操作符枚举
     */
    public enum FilterOperator {
        INCLUDE,   // 包含
        EXCLUDE    // 排除
    }
}

