package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.mcp.McpProtocol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Prompts 服务实现
 * 提供提示模板功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpPromptsService {

    // 提示模板存储
    private final Map<String, McpProtocol.McpPrompt> promptTemplates = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializePrompts() {
        initializeDefaultPrompts();
    }

    /**
     * 列出所有可用的提示
     */
    public Mono<McpProtocol.ListPromptsResult> listPrompts(McpProtocol.ListPromptsParams params) {
        log.info("列出提示: cursor={}", params.getCursor());
        
        List<McpProtocol.McpPrompt> prompts = new ArrayList<>(promptTemplates.values());
        
        return Mono.just(McpProtocol.ListPromptsResult.builder()
                .prompts(prompts)
                .nextCursor(null) // 简化实现，不支持分页
                .build());
    }

    /**
     * 获取指定提示
     */
    public Mono<McpProtocol.GetPromptResult> getPrompt(McpProtocol.GetPromptParams params) {
        log.info("获取提示: name={}, arguments={}", params.getName(), params.getArguments());
        
        McpProtocol.McpPrompt prompt = promptTemplates.get(params.getName());
        if (prompt == null) {
            return Mono.error(new RuntimeException("提示不存在: " + params.getName()));
        }
        
        // 根据参数生成提示内容
        List<McpProtocol.McpPromptMessage> messages = generatePromptContent(prompt, params.getArguments());
        
        return Mono.just(McpProtocol.GetPromptResult.builder()
                .messages(messages)
                .metadata(Map.of(
                    "name", params.getName(),
                    "timestamp", System.currentTimeMillis(),
                    "arguments", params.getArguments()
                ))
                .build());
    }

    /**
     * 通知提示列表变更
     */
    public void notifyPromptsListChanged() {
        log.info("通知提示列表变更");
        // 这里可以实现向所有订阅客户端发送通知的逻辑
    }

    /**
     * 添加自定义提示
     */
    public void addPrompt(McpProtocol.McpPrompt prompt) {
        log.info("添加提示: {}", prompt.getName());
        promptTemplates.put(prompt.getName(), prompt);
        notifyPromptsListChanged();
    }

    /**
     * 移除提示
     */
    public void removePrompt(String name) {
        log.info("移除提示: {}", name);
        promptTemplates.remove(name);
        notifyPromptsListChanged();
    }

    /**
     * 初始化默认提示模板
     */
    private void initializeDefaultPrompts() {
        // Dubbo 服务分析提示
        McpProtocol.McpPrompt dubboAnalysisPrompt = McpProtocol.McpPrompt.builder()
                .name("dubbo_analysis")
                .description("分析Dubbo服务提供者的详细信息")
                .arguments(List.of(
                        McpProtocol.McpPromptArgument.builder()
                                .name("provider_name")
                                .description("提供者名称")
                                .required(true)
                                .build(),
                        McpProtocol.McpPromptArgument.builder()
                                .name("analysis_type")
                                .description("分析类型：basic, detailed, performance")
                                .required(false)
                                .build()
                ))
                .build();
        promptTemplates.put("dubbo_analysis", dubboAnalysisPrompt);

        // 服务健康检查提示
        McpProtocol.McpPrompt healthCheckPrompt = McpProtocol.McpPrompt.builder()
                .name("health_check")
                .description("检查Dubbo服务提供者的健康状态")
                .arguments(List.of(
                        McpProtocol.McpPromptArgument.builder()
                                .name("provider_name")
                                .description("提供者名称，为空则检查所有")
                                .required(false)
                                .build()
                ))
                .build();
        promptTemplates.put("health_check", healthCheckPrompt);

        // 服务调用统计提示
        McpProtocol.McpPrompt statisticsPrompt = McpProtocol.McpPrompt.builder()
                .name("service_statistics")
                .description("获取服务调用统计信息")
                .arguments(List.of(
                        McpProtocol.McpPromptArgument.builder()
                                .name("time_range")
                                .description("时间范围：1h, 24h, 7d, 30d")
                                .required(false)
                                .build(),
                        McpProtocol.McpPromptArgument.builder()
                                .name("provider_name")
                                .description("提供者名称，为空则统计所有")
                                .required(false)
                                .build()
                ))
                .build();
        promptTemplates.put("service_statistics", statisticsPrompt);

        // 服务配置管理提示
        McpProtocol.McpPrompt configPrompt = McpProtocol.McpPrompt.builder()
                .name("config_management")
                .description("管理Dubbo服务配置")
                .arguments(List.of(
                        McpProtocol.McpPromptArgument.builder()
                                .name("action")
                                .description("操作类型：get, set, list")
                                .required(true)
                                .build(),
                        McpProtocol.McpPromptArgument.builder()
                                .name("config_key")
                                .description("配置键名")
                                .required(false)
                                .build(),
                        McpProtocol.McpPromptArgument.builder()
                                .name("config_value")
                                .description("配置值")
                                .required(false)
                                .build()
                ))
                .build();
        promptTemplates.put("config_management", configPrompt);

        // 服务监控提示
        McpProtocol.McpPrompt monitoringPrompt = McpProtocol.McpPrompt.builder()
                .name("service_monitoring")
                .description("监控Dubbo服务性能指标")
                .arguments(List.of(
                        McpProtocol.McpPromptArgument.builder()
                                .name("metric_type")
                                .description("指标类型：response_time, throughput, error_rate, cpu_usage")
                                .required(false)
                                .build(),
                        McpProtocol.McpPromptArgument.builder()
                                .name("provider_name")
                                .description("提供者名称")
                                .required(false)
                                .build()
                ))
                .build();
        promptTemplates.put("service_monitoring", monitoringPrompt);
    }

    /**
     * 根据提示模板和参数生成内容
     */
    private List<McpProtocol.McpPromptMessage> generatePromptContent(McpProtocol.McpPrompt prompt, Map<String, Object> arguments) {
        List<McpProtocol.McpPromptMessage> messages = new ArrayList<>();
        
        switch (prompt.getName()) {
            case "dubbo_analysis":
                messages.add(McpProtocol.McpPromptMessage.builder()
                        .role("user")
                        .content(generateDubboAnalysisPrompt(arguments))
                        .build());
                break;
            case "health_check":
            case "service_statistics":
            case "config_management":
            case "service_monitoring":
                messages.add(McpProtocol.McpPromptMessage.builder()
                        .role("user")
                        .content(generateGenericPrompt(prompt, arguments))
                        .build());
                break;
            default:
                messages.add(McpProtocol.McpPromptMessage.builder()
                        .role("user")
                        .content(McpProtocol.McpContent.builder()
                                .type("text")
                                .text("未知的提示模板: " + prompt.getName())
                                .build())
                        .build());
        }
        
        return messages;
    }

    /**
     * 生成Dubbo分析提示
     */
    private McpProtocol.McpContent generateDubboAnalysisPrompt(Map<String, Object> arguments) {
        String providerName = (String) arguments.getOrDefault("provider_name", "");
        String analysisType = (String) arguments.getOrDefault("analysis_type", "basic");
        
        StringBuilder content = new StringBuilder();
        content.append("# Dubbo服务分析报告\n\n");
        content.append("## 基本信息\n");
        content.append("- **提供者名称**: ").append(providerName).append("\n");
        content.append("- **分析类型**: ").append(analysisType).append("\n");
        content.append("- **分析时间**: ").append(new Date()).append("\n\n");
        
        if ("detailed".equals(analysisType)) {
            content.append("## 详细分析\n");
            content.append("请提供以下详细信息：\n");
            content.append("1. 服务接口定义\n");
            content.append("2. 方法签名和参数\n");
            content.append("3. 依赖关系分析\n");
            content.append("4. 性能指标\n");
            content.append("5. 错误日志分析\n\n");
        } else if ("performance".equals(analysisType)) {
            content.append("## 性能分析\n");
            content.append("请分析以下性能指标：\n");
            content.append("1. 响应时间分布\n");
            content.append("2. 吞吐量统计\n");
            content.append("3. 错误率分析\n");
            content.append("4. 资源使用情况\n\n");
        } else {
            content.append("## 基础分析\n");
            content.append("请提供以下基础信息：\n");
            content.append("1. 服务状态\n");
            content.append("2. 基本配置\n");
            content.append("3. 运行环境\n\n");
        }
        
        content.append("## 分析建议\n");
        content.append("基于以上信息，请提供：\n");
        content.append("- 服务健康状态评估\n");
        content.append("- 潜在问题识别\n");
        content.append("- 优化建议\n");
        
        return McpProtocol.McpContent.builder()
                .type("text")
                .text(content.toString())
                .build();
    }

    /**
     * 生成通用提示
     */
    private McpProtocol.McpContent generateGenericPrompt(McpProtocol.McpPrompt prompt, Map<String, Object> arguments) {
        StringBuilder content = new StringBuilder();
        content.append("# ").append(prompt.getDescription()).append("\n\n");
        
        if (prompt.getArguments() != null) {
            content.append("## 参数信息\n");
            for (McpProtocol.McpPromptArgument arg : prompt.getArguments()) {
                Object value = arguments.get(arg.getName());
                content.append("- **").append(arg.getName()).append("**: ");
                content.append(value != null ? value.toString() : "未提供");
                if (arg.getRequired()) {
                    content.append(" (必需)");
                }
                content.append("\n");
            }
            content.append("\n");
        }
        
        content.append("## 操作说明\n");
        content.append("请根据提供的参数执行相应的操作。\n");
        
        return McpProtocol.McpContent.builder()
                .type("text")
                .text(content.toString())
                .build();
    }
}
