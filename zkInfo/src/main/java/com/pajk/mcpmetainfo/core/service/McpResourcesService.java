package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.mcp.McpProtocol;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP Resources 服务实现
 * 提供资源访问和管理功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpResourcesService {

    private final ProviderService providerService;
    
    // 资源订阅管理
    private final Map<String, Set<String>> resourceSubscriptions = new ConcurrentHashMap<>();
    
    // 资源缓存
    private final Map<String, McpProtocol.McpResource> resourceCache = new ConcurrentHashMap<>();

    /**
     * 列出所有可用资源
     */
    public Mono<McpProtocol.ListResourcesResult> listResources(McpProtocol.ListResourcesParams params) {
        log.info("列出资源: cursor={}", params.getCursor());
        
        List<ProviderInfo> providers = providerService.getAllProviders();
        List<McpProtocol.McpResource> resources = providers.stream()
                .map(this::createResourceFromProvider)
                .collect(Collectors.toList());
        
        // 添加系统资源
        resources.addAll(getSystemResources());
        
        return Mono.just(McpProtocol.ListResourcesResult.builder()
                .resources(resources)
                .nextCursor(null) // 简化实现，不支持分页
                .build());
    }

    /**
     * 读取指定资源
     */
    public Mono<McpProtocol.ReadResourceResult> readResource(McpProtocol.ReadResourceParams params) {
        log.info("读取资源: uri={}", params.getUri());
        
        String uri = params.getUri();
        
        // 检查是否为系统资源
        if (uri.startsWith("system://")) {
            return readSystemResource(uri);
        }
        
        // 检查是否为提供者资源
        if (uri.startsWith("provider://")) {
            return readProviderResource(uri);
        }
        
        // 检查是否为文件资源
        if (uri.startsWith("file://")) {
            return readFileResource(uri);
        }
        
        return Mono.just(McpProtocol.ReadResourceResult.builder()
                .contents(List.of(McpProtocol.McpContent.builder()
                        .type("error")
                        .text("不支持的资源类型: " + uri)
                        .build()))
                .build());
    }

    /**
     * 订阅资源更新
     */
    public Mono<Void> subscribeResource(String clientId, McpProtocol.SubscribeResourceParams params) {
        log.info("订阅资源: clientId={}, uri={}", clientId, params.getUri());
        
        resourceSubscriptions.computeIfAbsent(clientId, k -> ConcurrentHashMap.newKeySet())
                .add(params.getUri());
        
        return Mono.empty();
    }

    /**
     * 取消订阅资源
     */
    public Mono<Void> unsubscribeResource(String clientId, McpProtocol.UnsubscribeResourceParams params) {
        log.info("取消订阅资源: clientId={}, uri={}", clientId, params.getUri());
        
        Set<String> subscriptions = resourceSubscriptions.get(clientId);
        if (subscriptions != null) {
            subscriptions.remove(params.getUri());
        }
        
        return Mono.empty();
    }

    /**
     * 通知资源列表变更
     */
    public void notifyResourcesListChanged() {
        log.info("通知资源列表变更");
        
        // 这里可以实现向所有订阅客户端发送通知的逻辑
        // 由于当前是HTTP/WebSocket架构，实际通知会在WebSocket连接中处理
    }

    /**
     * 从提供者信息创建资源
     */
    private McpProtocol.McpResource createResourceFromProvider(ProviderInfo provider) {
        String uri = "provider://" + provider.getApplication();
        
        return McpProtocol.McpResource.builder()
                .uri(uri)
                .name(provider.getApplication())
                .description("Dubbo服务提供者: " + provider.getInterfaceName())
                .mimeType("application/json")
                .subscribable(true)
                .metadata(Map.of(
                    "provider", provider.getApplication(),
                    "group", provider.getGroup(),
                    "version", provider.getVersion(),
                    "online", provider.isOnline(),
                    "interface", provider.getInterfaceName(),
                    "methods", provider.getMethods()
                ))
                .build();
    }

    /**
     * 获取系统资源
     */
    private List<McpProtocol.McpResource> getSystemResources() {
        return List.of(
                McpProtocol.McpResource.builder()
                        .uri("system://providers")
                        .name("所有提供者")
                        .description("系统资源：所有Dubbo服务提供者列表")
                        .mimeType("application/json")
                        .subscribable(true)
                        .metadata(Map.of("type", "system", "category", "providers"))
                        .build(),
                
                McpProtocol.McpResource.builder()
                        .uri("system://health")
                        .name("系统健康状态")
                        .description("系统资源：MCP服务器健康状态")
                        .mimeType("application/json")
                        .subscribable(true)
                        .metadata(Map.of("type", "system", "category", "health"))
                        .build(),
                
                McpProtocol.McpResource.builder()
                        .uri("system://config")
                        .name("系统配置")
                        .description("系统资源：MCP服务器配置信息")
                        .mimeType("application/json")
                        .subscribable(false)
                        .metadata(Map.of("type", "system", "category", "config"))
                        .build()
        );
    }

    /**
     * 读取系统资源
     */
    private Mono<McpProtocol.ReadResourceResult> readSystemResource(String uri) {
        switch (uri) {
            case "system://providers":
                List<ProviderInfo> providers = providerService.getAllProviders();
                return Mono.just(McpProtocol.ReadResourceResult.builder()
                        .contents(List.of(McpProtocol.McpContent.builder()
                                .type("json")
                                .text(providers.toString())
                                .build()))
                        .build());
            
            case "system://health":
                return Mono.just(McpProtocol.ReadResourceResult.builder()
                        .contents(List.of(McpProtocol.McpContent.builder()
                                .type("json")
                                .text("{\"status\":\"healthy\",\"timestamp\":\"" + System.currentTimeMillis() + "\"}")
                                .build()))
                        .build());
            
            case "system://config":
                return Mono.just(McpProtocol.ReadResourceResult.builder()
                        .contents(List.of(McpProtocol.McpContent.builder()
                                .type("json")
                                .text("{\"version\":\"1.0.0\",\"capabilities\":[\"tools\",\"resources\",\"prompts\",\"logging\"]}")
                                .build()))
                        .build());
            
            default:
                return Mono.just(McpProtocol.ReadResourceResult.builder()
                        .contents(List.of(McpProtocol.McpContent.builder()
                                .type("error")
                                .text("未知的系统资源: " + uri)
                                .build()))
                        .build());
        }
    }

    /**
     * 读取提供者资源
     */
    private Mono<McpProtocol.ReadResourceResult> readProviderResource(String uri) {
        String providerName = uri.substring("provider://".length());
        
        List<ProviderInfo> providers = providerService.getProvidersByInterface(providerName);
        if (providers.isEmpty()) {
            return Mono.just(McpProtocol.ReadResourceResult.builder()
                    .contents(List.of(McpProtocol.McpContent.builder()
                            .type("error")
                            .text("提供者不存在: " + providerName)
                            .build()))
                    .build());
        }
        
        return Mono.just(McpProtocol.ReadResourceResult.builder()
                .contents(List.of(McpProtocol.McpContent.builder()
                        .type("json")
                        .text(providers.toString())
                        .build()))
                .build());
    }

    /**
     * 读取文件资源（占位实现）
     */
    private Mono<McpProtocol.ReadResourceResult> readFileResource(String uri) {
        // 这里可以实现文件系统资源读取
        return Mono.just(McpProtocol.ReadResourceResult.builder()
                .contents(List.of(McpProtocol.McpContent.builder()
                        .type("text")
                        .text("文件资源读取功能待实现: " + uri)
                        .build()))
                .build());
    }
}
