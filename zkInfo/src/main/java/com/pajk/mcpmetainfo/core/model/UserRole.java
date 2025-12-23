package com.pajk.mcpmetainfo.core.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 用户角色枚举
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-01-20
 */
public enum UserRole {
    /**
     * 管理员 - 拥有所有权限
     */
    ADMIN("管理员", new HashSet<>(Arrays.asList(
        Permission.VIEW_SERVICE,
        Permission.CREATE_SERVICE,
        Permission.UPDATE_SERVICE,
        Permission.DELETE_SERVICE,
        Permission.APPROVE_SERVICE,
        Permission.REJECT_SERVICE,
        Permission.VIEW_PARAMETER,
        Permission.CREATE_PARAMETER,
        Permission.UPDATE_PARAMETER,
        Permission.DELETE_PARAMETER,
        Permission.SUBMIT_FOR_REVIEW
    ))),
    
    /**
     * 审批员 - 可以审批和查看
     */
    APPROVER("审批员", new HashSet<>(Arrays.asList(
        Permission.VIEW_SERVICE,
        Permission.APPROVE_SERVICE,
        Permission.REJECT_SERVICE,
        Permission.VIEW_PARAMETER
    ))),
    
    /**
     * 操作员 - 可以增删改查和提交审核
     */
    OPERATOR("操作员", new HashSet<>(Arrays.asList(
        Permission.VIEW_SERVICE,
        Permission.CREATE_SERVICE,
        Permission.UPDATE_SERVICE,
        Permission.DELETE_SERVICE,
        Permission.VIEW_PARAMETER,
        Permission.CREATE_PARAMETER,
        Permission.UPDATE_PARAMETER,
        Permission.DELETE_PARAMETER,
        Permission.SUBMIT_FOR_REVIEW
    ))),
    
    /**
     * 查看者 - 只能查看
     */
    VIEWER("查看者", new HashSet<>(Arrays.asList(
        Permission.VIEW_SERVICE,
        Permission.VIEW_PARAMETER
    )));
    
    private final String description;
    private final Set<Permission> permissions;
    
    UserRole(String description, Set<Permission> permissions) {
        this.description = description;
        this.permissions = permissions;
    }
    
    public String getDescription() {
        return description;
    }
    
    public Set<Permission> getPermissions() {
        return permissions;
    }
    
    /**
     * 检查是否有指定权限
     */
    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }
    
    /**
     * 权限枚举
     */
    public enum Permission {
        VIEW_SERVICE("查看服务"),
        CREATE_SERVICE("创建服务"),
        UPDATE_SERVICE("更新服务"),
        DELETE_SERVICE("删除服务"),
        APPROVE_SERVICE("审批服务"),
        REJECT_SERVICE("拒绝服务"),
        VIEW_PARAMETER("查看参数"),
        CREATE_PARAMETER("创建参数"),
        UPDATE_PARAMETER("更新参数"),
        DELETE_PARAMETER("删除参数"),
        SUBMIT_FOR_REVIEW("提交审核");
        
        private final String description;
        
        Permission(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}

