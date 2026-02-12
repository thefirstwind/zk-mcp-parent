package com.pajk.mcpmetainfo.core.model.wizard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * AI 补全后的元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrichedMetadata {
    /**
     * AI 建议的项目描述
     */
    @JsonProperty("suggestedDescription")
    private String suggestedDescription;
    
    /**
     * 为方法生成的 MCP Tool 定义
     */
    @JsonProperty("tools")
    private List<McpToolDefinition> tools;
}
