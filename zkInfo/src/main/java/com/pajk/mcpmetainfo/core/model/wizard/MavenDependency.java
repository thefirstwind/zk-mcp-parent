package com.pajk.mcpmetainfo.core.model.wizard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Maven 依赖信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MavenDependency {
    
    /**
     * Group ID
     */
    @JsonProperty("groupId")
    private String groupId;
    
    /**
     * Artifact ID
     */
    @JsonProperty("artifactId")
    private String artifactId;
    
    /**
     * 版本号
     */
    @JsonProperty("version")
    private String version;
    
    /**
     * 依赖类型（jar/pom等）
     */
    @JsonProperty("type")
    private String type;
    
    /**
     * 作用域（compile/test等）
     */
    @JsonProperty("scope")
    private String scope;
    
    /**
     * 下载状态
     */
    @JsonProperty("downloadStatus")
    private DownloadStatus downloadStatus;
    
    /**
     * JAR 文件本地路径
     */
    @JsonProperty("localPath")
    private String localPath;
    
    /**
     * 错误信息
     */
    @JsonProperty("errorMessage")
    private String errorMessage;
    
    public enum DownloadStatus {
        PENDING,      // 待下载
        DOWNLOADING,  // 下载中
        SUCCESS,      // 下载成功
        FAILED        // 下载失败
    }
    
    /**
     * 获取坐标字符串
     */
    public String getCoordinate() {
        return String.format("%s:%s:%s", groupId, artifactId, version);
    }
}
