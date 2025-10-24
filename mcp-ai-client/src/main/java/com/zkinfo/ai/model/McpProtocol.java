package com.zkinfo.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP协议数据模型
 */
public class McpProtocol {

    /**
     * JSON-RPC请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcRequest {
        @Builder.Default
        private String jsonrpc = "2.0";
        private String id;
        private String method;
        private Object params;
    }

    /**
     * JSON-RPC响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcResponse {
        @Builder.Default
        private String jsonrpc = "2.0";
        private String id;
        private Object result;
        private JsonRpcError error;
    }

    /**
     * JSON-RPC错误
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
     * 工具列表结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListToolsResult {
        private List<Tool> tools;
        private String nextCursor;
    }

    /**
     * 工具定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tool {
        private String name;
        private String description;
        private Map<String, Object> inputSchema;
    }

    /**
     * 调用工具参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallToolParams {
        private String name;
        private Map<String, Object> arguments;
    }

    /**
     * 调用工具结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallToolResult {
        private List<ToolContent> content;
        private boolean isError;
    }

    /**
     * 工具内容
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolContent {
        private String type;
        private String text;
    }
}

