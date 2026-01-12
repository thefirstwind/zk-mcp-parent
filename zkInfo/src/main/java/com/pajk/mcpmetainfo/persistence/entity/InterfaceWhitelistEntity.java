package com.pajk.mcpmetainfo.persistence.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 接口白名单实体
 * 
 * 对应数据库中的 zk_interface_whitelist 表，存储接口白名单前缀
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-12-25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterfaceWhitelistEntity {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 接口名前缀（左匹配）
     * 例如：com.zkinfo.demo
     */
    private String prefix;
    
    /**
     * 白名单描述
     */
    private String description;
    
    /**
     * 是否启用：true-启用，false-禁用
     */
    private Boolean enabled;
    
    /**
     * 创建人
     */
    private String createdBy;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新人
     */
    private String updatedBy;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}





