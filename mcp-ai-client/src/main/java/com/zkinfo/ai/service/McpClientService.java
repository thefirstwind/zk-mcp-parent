package com.zkinfo.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkinfo.ai.model.McpProtocol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP客户端服务
 * 负责与zkInfo MCP Server通信
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpClientService {

    private final WebClient mcpWebClient;
    private final ObjectMapper objectMapper;

    /**
     * 获取可用的工具列表
     */
    public Mono<List<McpProtocol.Tool>> listTools() {
        log.info("获取MCP工具列表");
        
        McpProtocol.JsonRpcRequest request = McpProtocol.JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(UUID.randomUUID().toString())
                .method("tools/list")
                .params(Map.of())
                .build();

        return mcpWebClient.post()
                .uri("/mcp/jsonrpc")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(McpProtocol.JsonRpcResponse.class)
                .map(response -> {
                    if (response.getError() != null) {
                        log.error("获取工具列表失败: {}", response.getError().getMessage());
                        return List.<McpProtocol.Tool>of();
                    }
                    
                    try {
                        Map<String, Object> resultMap = objectMapper.convertValue(
                                response.getResult(), Map.class);
                        List<Map<String, Object>> toolsList = (List<Map<String, Object>>) resultMap.get("tools");
                        
                        return toolsList.stream()
                                .map(toolMap -> McpProtocol.Tool.builder()
                                        .name((String) toolMap.get("name"))
                                        .description((String) toolMap.get("description"))
                                        .inputSchema((Map<String, Object>) toolMap.get("inputSchema"))
                                        .build())
                                .toList();
                    } catch (Exception e) {
                        log.error("解析工具列表失败", e);
                        return List.<McpProtocol.Tool>of();
                    }
                })
                .doOnSuccess(tools -> log.info("成功获取 {} 个工具", tools.size()))
                .doOnError(error -> log.error("调用MCP Server失败", error));
    }

    /**
     * 调用指定的工具
     */
    public Mono<String> callTool(String toolName, Map<String, Object> arguments) {
        log.info("调用MCP工具: name={}, arguments={}", toolName, arguments);

        McpProtocol.CallToolParams params = McpProtocol.CallToolParams.builder()
                .name(toolName)
                .arguments(arguments)
                .build();

        McpProtocol.JsonRpcRequest request = McpProtocol.JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(UUID.randomUUID().toString())
                .method("tools/call")
                .params(params)
                .build();

        return mcpWebClient.post()
                .uri("/mcp/jsonrpc")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(McpProtocol.JsonRpcResponse.class)
                .map(response -> {
                    log.debug("收到MCP响应: {}", response);
                    
                    if (response.getError() != null) {
                        String errorMsg = "调用工具失败: " + response.getError().getMessage();
                        log.error(errorMsg);
                        return errorMsg;
                    }

                    try {
                        log.debug("响应结果类型: {}", response.getResult() != null ? response.getResult().getClass() : "null");
                        
                        Map<String, Object> resultMap = objectMapper.convertValue(
                                response.getResult(), Map.class);
                        
                        log.debug("resultMap: {}", resultMap);
                        List<Map<String, Object>> contentList = 
                                (List<Map<String, Object>>) resultMap.get("content");
                        
                        log.debug("contentList: {}", contentList);
                        
                        if (contentList != null && !contentList.isEmpty()) {
                            Map<String, Object> firstContent = contentList.get(0);
                            String type = (String) firstContent.get("type");
                            
                            log.debug("content类型: {}, firstContent: {}", type, firstContent);
                            
                            // 处理不同类型的响应
                            if ("text".equals(type)) {
                                String text = (String) firstContent.get("text");
                                log.info("工具调用成功 (text): {}", text);
                                return text;
                            } else if ("json".equals(type)) {
                                Object data = firstContent.get("data");
                                String jsonResult = objectMapper.writeValueAsString(data);
                                log.info("工具调用成功 (json): {}", jsonResult);
                                return jsonResult;
                            } else if ("error".equals(type)) {
                                String errorText = (String) firstContent.get("text");
                                log.error("工具执行失败: {}", errorText);
                                return "工具执行失败: " + errorText;
                            } else {
                                // 未知类型，尝试返回整个内容
                                String result = objectMapper.writeValueAsString(firstContent);
                                log.info("工具调用成功 ({}): {}", type, result);
                                return result;
                            }
                        }
                        
                        log.warn("工具调用成功，但未返回内容");
                        return "工具调用成功，但未返回内容";
                    } catch (Exception e) {
                        log.error("解析工具调用结果失败", e);
                        return "解析结果失败: " + e.getMessage();
                    }
                })
                .doOnError(error -> {
                    log.error("调用MCP工具失败: {}", toolName, error);
                })
                .onErrorResume(error -> {
                    String errorMsg = "调用失败: " + error.getMessage();
                    log.error(errorMsg);
                    return Mono.just(errorMsg);
                });
    }

    /**
     * 获取MCP Server信息
     */
    public Mono<Map<String, Object>> getServerInfo() {
        log.info("获取MCP Server信息");
        
        return mcpWebClient.get()
                .uri("/mcp/info")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnSuccess(info -> log.info("MCP Server信息: {}", info))
                .doOnError(error -> log.error("获取Server信息失败", error))
                .onErrorReturn(Map.of("error", "获取Server信息失败"));
    }

    /**
     * 健康检查
     */
    public Mono<Map<String, Object>> healthCheck() {
        return mcpWebClient.get()
                .uri("/mcp/health")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnSuccess(health -> log.debug("MCP Server健康状态: {}", health))
                .doOnError(error -> log.error("健康检查失败", error))
                .onErrorReturn(Map.of("status", "ERROR", "message", "健康检查失败"));
    }
}

