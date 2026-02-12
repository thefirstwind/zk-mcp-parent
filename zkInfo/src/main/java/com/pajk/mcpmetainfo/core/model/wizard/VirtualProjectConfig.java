package com.pajk.mcpmetainfo.core.model.wizard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 最终提交的虚拟项目配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualProjectConfig {
    @JsonProperty("projectName")
    private String projectName;
    
    @JsonProperty("mcpServiceName")
    private String mcpServiceName;
    
    @JsonProperty("description")
    private String description;

    @JsonProperty("owner")
    private String owner; // 可选
    
    // 最终生成的工具列表（包含用户编辑后的 schema 和 description）
    @JsonProperty("tools")
    private List<Map<String, Object>> tools;

    // 原始 Git 信息（可选，用于追溯）
    @JsonProperty("gitUrl")
    private String gitUrl;
    
    @JsonProperty("branch")
    private String branch;
}
