package com.pajk.mcpmetainfo.core.model.wizard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 参数信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParameterInfo {
    
    /**
     * 参数名
     */
    @JsonProperty("name")
    public String name;
    
    /**
     * 参数类型
     */
    @JsonProperty("type")
    public String type;
    
    /**
     * 参数类型简单名
     */
    @JsonProperty("typeSimpleName")
    public String typeSimpleName;
    
    /**
     * 参数描述
     */
    @JsonProperty("description")
    public String description;
    
    /**
     * 示例值
     */
    @JsonProperty("example")
    public String example;
    
    /**
     * 是否必填
     */
    @Builder.Default
    @JsonProperty("required")
    public boolean required = true;
}
