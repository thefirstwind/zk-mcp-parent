package com.zkinfo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP (Model Context Protocol) 响应格式实体
 * 
 * 定义了符合 MCP 标准的响应数据结构，用于将 Dubbo 服务信息转换为
 * 标准化的 MCP 格式。MCP 是一种面向 AI 系统的服务描述协议，
 * 提供了统一的工具定义和服务描述格式。
 * 
 * <p>MCP 响应结构包含三个主要部分：</p>
 * <ul>
 *   <li><strong>Tools</strong>: 将服务方法转换为可调用的工具</li>
 *   <li><strong>Services</strong>: 服务接口的完整描述信息</li>
 *   <li><strong>Metadata</strong>: 应用和协议的元数据信息</li>
 * </ul>
 * 
 * <p>设计特点：</p>
 * <ul>
 *   <li>标准化格式：符合 MCP 协议规范</li>
 *   <li>完整描述：包含服务的完整信息和状态</li>
 *   <li>AI 友好：便于 AI 系统理解和调用</li>
 *   <li>扩展性强：支持自定义元数据和参数</li>
 * </ul>
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 * @see <a href="https://modelcontextprotocol.io/">Model Context Protocol</a>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpResponse {
    
    /**
     * 应用名称
     */
    @JsonProperty("application")
    private String application;
    
    /**
     * 工具列表
     */
    @JsonProperty("tools")
    private List<McpTool> tools;
    
    /**
     * 服务列表
     */
    @JsonProperty("services")
    private List<McpService> services;
    
    /**
     * 元数据信息
     */
    @JsonProperty("metadata")
    private McpMetadata metadata;
    
    /**
     * MCP工具定义
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpTool {
        /**
         * 工具名称
         */
        @JsonProperty("name")
        private String name;
        
        /**
         * 工具描述
         */
        @JsonProperty("description")
        private String description;
        
        /**
         * 输入参数schema
         */
        @JsonProperty("inputSchema")
        private Map<String, Object> inputSchema;
        
        /**
         * 工具类型
         */
        @JsonProperty("type")
        private String type = "function";
        
        /**
         * 工具版本
         */
        @JsonProperty("version")
        private String version;
        
        /**
         * 工具分组
         */
        @JsonProperty("group")
        private String group;
        
        /**
         * 提供者地址
         */
        @JsonProperty("provider")
        private String provider;
        
        /**
         * 是否在线
         */
        @JsonProperty("online")
        private boolean online;
    }
    
    /**
     * MCP服务定义
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpService {
        /**
         * 服务名称
         */
        @JsonProperty("name")
        private String name;
        
        /**
         * 服务描述
         */
        @JsonProperty("description")
        private String description;
        
        /**
         * 服务接口
         */
        @JsonProperty("interface")
        private String interfaceName;
        
        /**
         * 服务方法列表
         */
        @JsonProperty("methods")
        private List<String> methods;
        
        /**
         * 服务版本
         */
        @JsonProperty("version")
        private String version;
        
        /**
         * 服务分组
         */
        @JsonProperty("group")
        private String group;
        
        /**
         * 协议类型
         */
        @JsonProperty("protocol")
        private String protocol;
        
        /**
         * 提供者列表
         */
        @JsonProperty("providers")
        private List<ServiceProvider> providers;
        
        /**
         * 服务状态
         */
        @JsonProperty("status")
        private String status;
    }
    
    /**
     * 服务提供者信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceProvider {
        /**
         * 提供者地址
         */
        @JsonProperty("address")
        private String address;
        
        /**
         * 是否在线
         */
        @JsonProperty("online")
        private boolean online;
        
        /**
         * 最后心跳时间
         */
        @JsonProperty("lastHeartbeat")
        private String lastHeartbeat;
        
        /**
         * 权重
         */
        @JsonProperty("weight")
        private Integer weight;
    }
    
    /**
     * MCP元数据
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpMetadata {
        /**
         * 协议版本
         */
        @JsonProperty("protocolVersion")
        private String protocolVersion = "1.0";
        
        /**
         * 生成时间
         */
        @JsonProperty("timestamp")
        private String timestamp;
        
        /**
         * 总工具数
         */
        @JsonProperty("totalTools")
        private int totalTools;
        
        /**
         * 总服务数
         */
        @JsonProperty("totalServices")
        private int totalServices;
        
        /**
         * 在线提供者数量
         */
        @JsonProperty("onlineProviders")
        private int onlineProviders;
        
        /**
         * 总提供者数量
         */
        @JsonProperty("totalProviders")
        private int totalProviders;
        
        /**
         * 应用状态
         */
        @JsonProperty("applicationStatus")
        private String applicationStatus;
    }
}
