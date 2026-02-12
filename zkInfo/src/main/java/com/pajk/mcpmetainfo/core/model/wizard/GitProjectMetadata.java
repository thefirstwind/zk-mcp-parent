package com.pajk.mcpmetainfo.core.model.wizard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Git 项目元数据分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitProjectMetadata {
    /**
     * 应用名称 (from duddo.application.name or spring.application.name)
     */
    @JsonProperty("applicationName")
    private String applicationName;
    
    /**
     * Maven GroupId (from pom.xml)
     */
    @JsonProperty("groupId")
    private String groupId;
    
    /**
     * Maven ArtifactId (from pom.xml)
     */
    @JsonProperty("artifactId")
    private String artifactId;

    /**
     * Maven Version (from pom.xml)
     */
    @JsonProperty("version")
    private String version;

    /**
     * 项目名称 (from pom.xml name)
     */
    @JsonProperty("projectName")
    private String projectName;
    /**
     * MCP 服务名称 (from mcp.service.name)
     */
    @JsonProperty("mcpServiceName")
    private String mcpServiceName;
    
    /**
     * 项目描述 (from pom.xml description)
     */
    @JsonProperty("description")
    private String description;
    
    /**
     * 项目负责人 (from git last committer or author)
     */
    @JsonProperty("owner")
    private String owner;
    
    /**
     * Dubbo 服务端口
     */
    @JsonProperty("dubboPort")
    private Integer dubboPort;
    
    /**
     * Web 服务端口
     */
    @JsonProperty("serverPort")
    private Integer serverPort;

    /**
     * 接口 Javadoc (Key: FullInterfaceName)
     */
    @JsonProperty("interfaceJavadocMap")
    private java.util.Map<String, String> interfaceJavadocMap;
    
    /**
     * 方法 Javadoc (Key: FullInterfaceName#MethodName)
     */
    @JsonProperty("methodJavadocMap")
    private java.util.Map<String, String> methodJavadocMap;
    
    /**
     * 方法参数名列表 (Key: FullInterfaceName#MethodName)
     */
    @JsonProperty("methodParameterNamesMap")
    private java.util.Map<String, List<String>> methodParameterNamesMap;

    /**
     * 方法参数 Javadoc (Key: FullInterfaceName#MethodName#ParamName)
     */
    @JsonProperty("methodParamJavadocMap")
    private java.util.Map<String, String> methodParamJavadocMap;

    /**
     * 方法返回值 Javadoc (Key: FullInterfaceName#MethodName)
     */
    @JsonProperty("methodReturnJavadocMap")
    private java.util.Map<String, String> methodReturnJavadocMap;

    
    /**
     * 分析状态
     */
    @JsonProperty("success")
    private boolean success;
    
    /**
     * 错误信息
     */
    @JsonProperty("errorMessage")
    private String errorMessage;
}
