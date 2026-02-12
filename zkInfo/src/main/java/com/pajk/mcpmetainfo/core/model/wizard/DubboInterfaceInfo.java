package com.pajk.mcpmetainfo.core.model.wizard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Dubbo 接口信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DubboInterfaceInfo {
    
    /**
     * 接口全限定名
     */
    @JsonProperty("interfaceName")
    private String interfaceName;
    
    /**
     * 接口简单名
     */
    @JsonProperty("simpleClassName")
    private String simpleClassName;
    
    /**
     * 所属 JAR 包
     */
    @JsonProperty("jarName")
    private String jarName;
    
    /**
     * 接口描述（从 JavaDoc 提取）
     */
    @JsonProperty("description")
    private String description;
    
    /**
     * 方法列表
     */
    @JsonProperty("methods")
    private List<MethodInfo> methods;
    
    /**
     * 是否被选中
     */
    @JsonProperty("selected")
    private boolean selected;
}
