package com.pajk.mcpmetainfo.core.model.wizard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * MCP Tool 生成结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolDefinition {
    @JsonProperty("interfaceName")
    private String interfaceName;
    @JsonProperty("methodName")
    private String methodName;
    @JsonProperty("toolName")
    private String toolName;
    @JsonProperty("description")
    private String description;
    
    /**
     * JSON Schema 字符串
     */
    @JsonProperty("inputSchema")
    private String inputSchema;
}
