package com.pajk.mcpmetainfo.core.model.wizard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * POM 解析进度反馈
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PomParseProgress {
    
    /**
     * 当前阶段
     */
    @JsonProperty("currentStage")
    private Stage currentStage;
    
    /**
     * 阶段描述
     */
    @JsonProperty("stageDescription")
    private String stageDescription;
    
    /**
     * 进度百分比（0-100）
     */
    @JsonProperty("progressPercentage")
    private int progressPercentage;
    
    /**
     * 已解析的依赖数量
     */
    @JsonProperty("parsedDependencies")
    private int parsedDependencies;
    
    /**
     * 总依赖数量
     */
    @JsonProperty("totalDependencies")
    private int totalDependencies;
    
    /**
     * 已下载的 JAR 数量
     */
    @JsonProperty("downloadedJars")
    private int downloadedJars;
    
    /**
     * 已提取的接口数量
     */
    @JsonProperty("extractedInterfaces")
    private int extractedInterfaces;
    
    /**
     * 详细日志
     */
    @Builder.Default
    @JsonProperty("logs")
    private List<String> logs = new ArrayList<>();
    
    /**
     * 是否完成
     */
    @JsonProperty("completed")
    private boolean completed;
    
    /**
     * 是否发生错误
     */
    @JsonProperty("hasError")
    private boolean hasError;
    
    /**
     * 错误信息
     */
    @JsonProperty("errorMessage")
    private String errorMessage;
    
    /**
     * 解析结果
     */
    @JsonProperty("result")
    private PomParseResult result;
    
    public enum Stage {
        PARSING_POM("解析 POM 依赖"),
        DOWNLOADING_JARS("下载 JAR 包"),
        EXTRACTING_INTERFACES("提取接口信息"),
        COMPLETED("完成");
        
        private final String description;
        
        Stage(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 添加日志
     */
    public void addLog(String log) {
        if (logs == null) {
            logs = new ArrayList<>();
        }
        logs.add(String.format("[%s] %s", 
            java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")), 
            log));
    }

    /**
     * 获取一条最新日志
     */
    public String getLastLog() {
        if (logs != null && !logs.isEmpty()) {
            return logs.get(logs.size() - 1);
        }
        return "";
    }
}
