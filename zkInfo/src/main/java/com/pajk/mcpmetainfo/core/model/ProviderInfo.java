package com.pajk.mcpmetainfo.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Dubbo 服务提供者信息实体
 * 
 * 封装了 Dubbo 服务提供者的完整信息，包括服务接口、网络地址、注册参数、
 * 运行状态、心跳信息等。该实体是系统中服务提供者数据的核心载体。
 * 
 * <p>主要属性：</p>
 * <ul>
 *   <li>基本信息：接口名、地址、应用名等</li>
 *   <li>注册信息：注册时间、ZooKeeper 路径、注册参数</li>
 *   <li>状态信息：在线状态、最后心跳时间</li>
 *   <li>扩展信息：版本、分组、协议等 Dubbo 参数</li>
 * </ul>
 * 
 * <p>状态管理：</p>
 * <ul>
 *   <li>online：标识服务提供者是否在线</li>
 *   <li>lastHeartbeat：记录最后一次心跳检测时间</li>
 *   <li>registerTime：记录服务注册时间</li>
 * </ul>
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderInfo {
    
    /**
     * 服务接口名
     */
    private String interfaceName;
    
    /**
     * 提供者地址 (IP:Port)
     */
    private String address;
    
    /**
     * 协议类型
     */
    private String protocol;
    
    /**
     * 服务版本
     */
    private String version;
    
    /**
     * 服务分组
     */
    private String group;
    
    /**
     * 应用名称
     */
    private String application;
    
    /**
     * 服务方法列表
     */
    private String methods;
    
    /**
     * 其他参数
     */
    private Map<String, String> parameters;
    
    /**
     * 注册时间
     */
    private LocalDateTime registerTime;
    
    /**
     * 注册时间（别名，与 registerTime 相同）
     */
    private LocalDateTime registrationTime;
    
    /**
     * 最后心跳时间
     */
    private LocalDateTime lastHeartbeat;
    
    /**
     * 是否在线
     */
    private Boolean online;
    
    /**
     * 是否健康
     */
    private Boolean healthy;
    
    /**
     * ZooKeeper节点路径
     */
    private String zkPath;
    
    /**
     * 获取IP地址
     */
    public String getIp() {
        if (address != null && address.contains(":")) {
            return address.split(":")[0];
        }
        return address;
    }
    
    /**
     * 获取端口
     */
    public Integer getPort() {
        if (address != null && address.contains(":")) {
            try {
                return Integer.parseInt(address.split(":")[1]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * 获取服务唯一标识
     */
    public String getServiceKey() {
        StringBuilder key = new StringBuilder(interfaceName);
        if (version != null && !version.isEmpty()) {
            key.append(":").append(version);
        }
        if (group != null && !group.isEmpty()) {
            key.append(":").append(group);
        }
        return key.toString();
    }
    
    /**
     * 是否在线（兼容性方法，用于 boolean 类型的 isOnline() 调用）
     * 
     * @return 是否在线，如果为 null 则返回 false
     */
    public boolean isOnline() {
        return online != null && online;
    }
    
    /**
     * 是否健康（兼容性方法）
     * 
     * @return 是否健康，如果为 null 则返回 true
     */
    public boolean isHealthy() {
        return healthy != null && healthy;
    }
}
