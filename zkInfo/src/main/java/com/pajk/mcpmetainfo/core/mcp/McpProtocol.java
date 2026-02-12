package com.pajk.mcpmetainfo.core.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP (Model Context Protocol) 标准协议定义
 * 基于 JSON-RPC 2.0 规范
 */
public class McpProtocol {

    /**
     * JSON-RPC 2.0 请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcRequest {
        @JsonProperty("jsonrpc")
        @Builder.Default
        private String jsonrpc = "2.0";
        
        private String id;
        private String method;
        private Object params;
    }

    /**
     * JSON-RPC 2.0 响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcResponse {
        @JsonProperty("jsonrpc")
        @Builder.Default
        private String jsonrpc = "2.0";
        
        private String id;
        private Object result;
        private JsonRpcError error;
    }

    /**
     * JSON-RPC 2.0 错误
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JsonRpcError {
        private int code;
        private String message;
        private Object data;
    }

    /**
     * MCP 工具列表请求参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListToolsParams {
        private String cursor;
    }

    /**
     * MCP 工具列表响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListToolsResult {
        private List<McpTool> tools;
        
        @JsonProperty("nextCursor")
        private String nextCursor;
    }

    /**
     * MCP 工具定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class McpTool {
        private String name;
        private String description;
        
        @JsonProperty("inputSchema")
        private Map<String, Object> inputSchema;
        
        // 扩展字段：参数类型列表（用于精确的Dubbo泛化调用）
        @JsonProperty("parameterTypes")
        private List<String> parameterTypes;
        
        // 扩展字段：支持流式调用
        private Boolean streamable;
        
        // 扩展字段：提供者信息
        private String provider;
        private Boolean online;
        private String group;
        private String version;
    }

    /**
     * MCP 工具调用请求参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallToolParams {
        private String name;
        private Map<String, Object> arguments;
        
        // 扩展字段：支持流式调用
        private Boolean stream;
        private Integer timeout;
    }

    /**
     * MCP 工具调用响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CallToolResult {
        private List<McpContent> content;
        
        @JsonProperty("isError")
        private Boolean isError;
        
        // 扩展字段：流式调用支持
        private String streamId;
        private Boolean hasMore;
    }

    /**
     * MCP 内容块
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpContent {
        private String type; // "text", "json", "error", "resource"
        private String text;
        private Object data;
        
        // 资源相关字段
        private String resource;
        private String mimeType;
        private String uri;
    }

    /**
     * MCP 资源定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class McpResource {
        private String uri;
        private String name;
        private String description;
        private String mimeType;
        
        // 扩展字段
        private Boolean subscribable;
        private Map<String, Object> metadata;
    }

    /**
     * MCP 资源列表请求参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListResourcesParams {
        private String cursor;
    }

    /**
     * MCP 资源列表响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListResourcesResult {
        private List<McpResource> resources;
        
        @JsonProperty("nextCursor")
        private String nextCursor;
    }

    /**
     * MCP 资源读取请求参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadResourceParams {
        private String uri;
    }

    /**
     * MCP 资源读取响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadResourceResult {
        private List<McpContent> contents;
    }

    /**
     * MCP 资源订阅请求参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribeResourceParams {
        private String uri;
    }

    /**
     * MCP 资源取消订阅请求参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnsubscribeResourceParams {
        private String uri;
    }

    /**
     * MCP 提示定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class McpPrompt {
        private String name;
        private String description;
        
        @JsonProperty("arguments")
        private List<McpPromptArgument> arguments;
    }

    /**
     * MCP 提示参数定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpPromptArgument {
        private String name;
        private String description;
        private Boolean required;
    }

    /**
     * MCP 提示列表请求参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListPromptsParams {
        private String cursor;
    }

    /**
     * MCP 提示列表响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListPromptsResult {
        private List<McpPrompt> prompts;
        
        @JsonProperty("nextCursor")
        private String nextCursor;
    }

    /**
     * MCP 获取提示请求参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetPromptParams {
        private String name;
        private Map<String, Object> arguments;
    }

    /**
     * MCP 获取提示响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetPromptResult {
        private List<McpPromptMessage> messages;
        private Map<String, Object> metadata;
    }

    /**
     * MCP 提示消息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpPromptMessage {
        private String role; // "user", "assistant", "system"
        private McpContent content;
    }

    /**
     * MCP 通知消息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpNotification {
        private String method;
        private Object params;
    }

    /**
     * MCP 日志消息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpLogMessage {
        private String level; // "debug", "info", "notice", "warning", "error", "critical", "alert", "emergency"
        private String data;
        private String logger;
    }

    /**
     * MCP 日志记录请求参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogMessageParams {
        private String level;
        private String data;
        private String logger;
    }

    /**
     * MCP 日志参数（别名，用于测试兼容性）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogParams {
        private String level;
        private String data;
        private String logger;
    }

    /**
     * MCP 能力定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class McpCapabilities {
        // 服务器能力
        private ToolsCapability tools;
        private ResourcesCapability resources;
        private PromptsCapability prompts;
        private LoggingCapability logging;
        
        // 客户端能力
        private SamplingCapability sampling;
        private ExperimentalCapability experimental;
    }

    /**
     * Tools 能力
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolsCapability {
        private Boolean listChanged;
    }

    /**
     * Resources 能力
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourcesCapability {
        private Boolean subscribe;
        private Boolean listChanged;
    }

    /**
     * Prompts 能力
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptsCapability {
        private Boolean listChanged;
    }

    /**
     * Logging 能力
     */
    @Data
    @Builder
    @NoArgsConstructor
    public static class LoggingCapability {
        // 无特殊字段
    }

    /**
     * Sampling 能力
     */
    @Data
    @Builder
    @NoArgsConstructor
    public static class SamplingCapability {
        // 无特殊字段
    }

    /**
     * Experimental 能力
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentalCapability {
        private Map<String, Object> experimental;
    }

    /**
     * MCP 流式数据块
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamChunk {
        private String id;
        private String type; // "data", "error", "end"
        private Object data;
        private String timestamp;
        private Boolean isLast;
    }

    /**
     * MCP 服务器信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServerInfo {
        private String name;
        private String version;
        private String description;
        private List<String> capabilities;
        private Map<String, Object> metadata;
    }

    /**
     * MCP 初始化请求参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InitializeParams {
        @JsonProperty("protocolVersion")
        private String protocolVersion;
        
        @JsonProperty("clientInfo")
        private ClientInfo clientInfo;
        
        private Map<String, Object> capabilities;
    }

    /**
     * MCP 客户端信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientInfo {
        private String name;
        private String version;
    }

    /**
     * MCP 初始化响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InitializeResult {
        @JsonProperty("protocolVersion")
        private String protocolVersion;
        
        @JsonProperty("serverInfo")
        private ServerInfo serverInfo;
        
        private Map<String, Object> capabilities;
    }

    // MCP 标准方法名常量
    public static class Methods {
        // 生命周期管理
        public static final String INITIALIZE = "initialize";
        public static final String INITIALIZED = "initialized";
        public static final String PING = "ping";
        
        // Tools 原语
        public static final String LIST_TOOLS = "tools/list";
        public static final String CALL_TOOL = "tools/call";
        public static final String STREAM_TOOL = "tools/stream";
        
        // Resources 原语
        public static final String LIST_RESOURCES = "resources/list";
        public static final String READ_RESOURCE = "resources/read";
        public static final String SUBSCRIBE_RESOURCE = "resources/subscribe";
        public static final String UNSUBSCRIBE_RESOURCE = "resources/unsubscribe";
        
        // Prompts 原语
        public static final String LIST_PROMPTS = "prompts/list";
        public static final String GET_PROMPT = "prompts/get";
        
        // Notifications
        public static final String TOOLS_LIST_CHANGED = "notifications/tools/list_changed";
        public static final String RESOURCES_LIST_CHANGED = "notifications/resources/list_changed";
        public static final String PROMPTS_LIST_CHANGED = "notifications/prompts/list_changed";
        
        // Logging
        public static final String LOG_MESSAGE = "logging/log";
    }

    // MCP 错误代码常量
    public static class ErrorCodes {
        // JSON-RPC 2.0 标准错误
        public static final int PARSE_ERROR = -32700;
        public static final int INVALID_REQUEST = -32600;
        public static final int METHOD_NOT_FOUND = -32601;
        public static final int INVALID_PARAMS = -32602;
        public static final int INTERNAL_ERROR = -32603;
        
        // MCP 特定错误
        public static final int TOOL_NOT_FOUND = -32001;
        public static final int TOOL_EXECUTION_ERROR = -32002;
        public static final int TIMEOUT_ERROR = -32003;
        public static final int RESOURCE_NOT_FOUND = -32004;
        public static final int RESOURCE_ACCESS_DENIED = -32005;
        public static final int PROMPT_NOT_FOUND = -32006;
        public static final int INVALID_PROMPT_ARGUMENTS = -32007;
        public static final int SUBSCRIPTION_FAILED = -32008;
        public static final int LOGGING_ERROR = -32009;
        public static final int CAPABILITY_NOT_SUPPORTED = -32010;
    }
}
