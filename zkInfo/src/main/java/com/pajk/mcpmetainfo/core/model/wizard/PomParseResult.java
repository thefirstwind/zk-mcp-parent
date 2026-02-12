package com.pajk.mcpmetainfo.core.model.wizard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * POM 解析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PomParseResult {
    
    /**
     * 是否成功
     */
    @JsonProperty("success")
    private boolean success;
    
    /**
     * 主项目坐标
     */
    @JsonProperty("groupId")
    private String groupId;
    @JsonProperty("artifactId")
    private String artifactId;
    @JsonProperty("version")
    private String version;

    /**
     * 解析的依赖列表
     */
    @JsonProperty("dependencies")
    private List<MavenDependency> dependencies;

    
    /**
     * 提取的接口列表
     */
    @JsonProperty("interfaces")
    private List<DubboInterfaceInfo> interfaces;
    
    /**
     * JAR 包数量
     */
    @JsonProperty("jarCount")
    private int jarCount;
    
    /**
     * 接口数量
     */
    @JsonProperty("interfaceCount")
    private int interfaceCount;
    
    /**
     * 方法总数
     */
    @JsonProperty("methodCount")
    private int methodCount;
    
    /**
     * 错误信息
     */
    @JsonProperty("errorMessage")
    private String errorMessage;
    
    /**
     * 警告信息列表
     */
    @JsonProperty("warnings")
    private List<String> warnings;
}
