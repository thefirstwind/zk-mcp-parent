package com.pajk.mcpmetainfo.core.util;

import com.pajk.mcpmetainfo.core.model.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

/**
 * 权限校验工具类
 * 
 * 通过MDC获取用户ID，并根据用户ID获取角色进行权限校验
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-01-20
 */
@Slf4j
public class PermissionChecker {
    
    /**
     * 用户角色映射（示例实现，后续可以改为从数据库或配置中心获取）
     * Key: staffId, Value: UserRole
     */
    private static final Map<String, UserRole> USER_ROLE_MAP = new HashMap<>();
    
    /**
     * 默认角色
     */
    private static final UserRole DEFAULT_ROLE = UserRole.ADMIN;
    
    static {
        // 示例数据，后续可以通过配置或数据库加载
        // USER_ROLE_MAP.put("admin001", UserRole.ADMIN);
        // USER_ROLE_MAP.put("approver001", UserRole.APPROVER);
        // USER_ROLE_MAP.put("operator001", UserRole.OPERATOR);
    }
    
    /**
     * 获取当前用户ID
     * 
     * @return 用户ID，如果不存在返回null
     */
    public static String getCurrentStaffId() {
        return MDC.get("staffId");
    }
    
    /**
     * 获取当前用户角色
     * 
     * @return 用户角色，如果用户不存在则返回默认角色VIEWER
     */
    public static UserRole getCurrentUserRole() {
        String staffId = getCurrentStaffId();
        if (staffId == null || staffId.isEmpty()) {
            log.warn("无法获取当前用户ID，使用默认角色: {}", DEFAULT_ROLE);
            return DEFAULT_ROLE;
        }
        
        UserRole role = USER_ROLE_MAP.get(staffId);
        if (role == null) {
            log.warn("用户 {} 没有配置角色，使用默认角色: {}", staffId, DEFAULT_ROLE);
            return DEFAULT_ROLE;
        }
        
        return role;
    }
    
    /**
     * 检查当前用户是否有指定权限
     * 
     * @param permission 权限
     * @return true if has permission, false otherwise
     */
    public static boolean hasPermission(UserRole.Permission permission) {
        UserRole role = getCurrentUserRole();
        boolean hasPermission = role.hasPermission(permission);
        
        String staffId = getCurrentStaffId();
        log.debug("权限检查 - 用户: {}, 角色: {}, 权限: {}, 结果: {}", 
            staffId, role, permission, hasPermission);
        
        return hasPermission;
    }
    
    /**
     * 检查当前用户是否有指定权限，如果没有则抛出异常
     * 
     * @param permission 权限
     * @throws SecurityException 如果没有权限
     */
    public static void checkPermission(UserRole.Permission permission) throws SecurityException {
        if (!hasPermission(permission)) {
            String staffId = getCurrentStaffId();
            UserRole role = getCurrentUserRole();
            throw new SecurityException(
                String.format("用户 %s (角色: %s) 没有权限执行操作: %s", 
                    staffId != null ? staffId : "未知", role, permission.getDescription()));
        }
    }
    
    /**
     * 设置用户角色（用于测试或动态配置）
     * 
     * @param staffId 用户ID
     * @param role 角色
     */
    public static void setUserRole(String staffId, UserRole role) {
        if (staffId != null && !staffId.isEmpty() && role != null) {
            USER_ROLE_MAP.put(staffId, role);
            log.info("设置用户 {} 的角色为: {}", staffId, role);
        }
    }
    
    /**
     * 获取所有用户角色映射（用于管理界面）
     * 
     * @return 用户角色映射的副本
     */
    public static Map<String, UserRole> getAllUserRoles() {
        return new HashMap<>(USER_ROLE_MAP);
    }
}

