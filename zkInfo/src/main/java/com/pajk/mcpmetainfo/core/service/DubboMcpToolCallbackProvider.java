package com.pajk.mcpmetainfo.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import com.pajk.mcpmetainfo.core.util.McpToolSchemaGenerator;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dubbo MCP Tool Callback Provider
 * 将Dubbo服务转换为MCP Tools，供Spring AI MCP Server使用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DubboMcpToolCallbackProvider implements ToolCallbackProvider {

    private final ProviderService providerService;
    private final McpExecutorService mcpExecutorService;
    private final ObjectMapper objectMapper;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.pajk.mcpmetainfo.core.util.McpToolSchemaGenerator mcpToolSchemaGenerator;

    @Override
    public ToolCallback[] getToolCallbacks() {
        log.debug("Getting tool callbacks from Dubbo providers");
        
        // 获取所有在线的Provider
        List<ProviderInfo> providers = providerService.getAllProviders().stream()
                .filter(ProviderInfo::isOnline)
                .collect(Collectors.toList());
        
        if (providers.isEmpty()) {
            log.warn("No online providers found, returning empty tool callbacks");
            return new ToolCallback[0];
        }
        
        // 去重：同一个接口的多个provider可能有相同的方法
        Map<String, DubboMcpToolCallback> toolCallbacks = new LinkedHashMap<>();
        
        for (ProviderInfo provider : providers) {
            if (provider.getMethods() == null || provider.getMethods().isEmpty()) {
                continue;
            }
            
            String[] methods = provider.getMethods().split(",");
            for (String method : methods) {
                String methodName = method.trim();
                if (methodName.isEmpty()) {
                    continue;
                }
                
                // 工具名称：接口名.方法名
                String toolName = provider.getInterfaceName() + "." + methodName;
                
                // 如果已存在同名工具，跳过（保留第一个）
                if (toolCallbacks.containsKey(toolName)) {
                    continue;
                }
                
                // 创建工具回调
                DubboMcpToolCallback toolCallback = new DubboMcpToolCallback(
                        toolName,
                        provider.getInterfaceName(),
                        methodName,
                        provider.getVersion(),
                        provider.getGroup(),
                        mcpExecutorService,
                        objectMapper,
                        mcpToolSchemaGenerator
                );
                
                toolCallbacks.put(toolName, toolCallback);
            }
        }
        
        log.info("Created {} tool callbacks from {} providers", toolCallbacks.size(), providers.size());
        
        return toolCallbacks.values().toArray(new ToolCallback[0]);
    }
    
    /**
     * Dubbo MCP Tool Callback实现
     */
    private class DubboMcpToolCallback implements ToolCallback {
        
        private final String toolName;
        private final String interfaceName;
        private final String methodName;
        private final String version;
        private final String group;
        private final McpExecutorService executorService;
        private final ObjectMapper objectMapper;
        private final com.pajk.mcpmetainfo.core.util.McpToolSchemaGenerator mcpToolSchemaGenerator;
        private final ToolDefinition toolDefinition;
        
        public DubboMcpToolCallback(String toolName, String interfaceName, String methodName,
                                   String version, String group, McpExecutorService executorService,
                                   ObjectMapper objectMapper,
                                   com.pajk.mcpmetainfo.core.util.McpToolSchemaGenerator mcpToolSchemaGenerator) {
            this.toolName = toolName;
            this.interfaceName = interfaceName;
            this.methodName = methodName;
            this.version = version;
            this.group = group;
            this.executorService = executorService;
            this.objectMapper = objectMapper;
            this.mcpToolSchemaGenerator = mcpToolSchemaGenerator;
            
            // 根据实际方法参数生成 inputSchema
            Map<String, Object> inputSchema = mcpToolSchemaGenerator.createInputSchemaFromMethod(
                    interfaceName, methodName);
            
            // 构建ToolDefinition
            this.toolDefinition = ToolDefinition.builder()
                    .name(toolName)
                    .description(String.format("调用 %s 服务的 %s 方法", interfaceName, methodName))
                    .inputSchema(objectMapper.valueToTree(inputSchema).toString())
                    .build();
        }
        
        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }
        
        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }
        
        @Override
        public String call(String toolInput, ToolContext toolContext) {
            try {
                log.debug("Calling tool: {} with input: {}", toolName, toolInput);
                
                // 解析输入参数
                @SuppressWarnings("unchecked")
                Map<String, Object> params = objectMapper.readValue(toolInput, Map.class);
                
                // 根据方法签名从 params 中提取参数数组
                Object[] args = mcpToolSchemaGenerator.extractMethodParameters(
                        interfaceName, methodName, params);
                
                // 获取超时时间（默认3000ms）
                Integer timeout = params.containsKey("timeout") ? 
                        ((Number) params.get("timeout")).intValue() : 3000;
                
                // 执行调用
                McpExecutorService.McpCallResult result = executorService.executeToolCallSync(toolName, args, timeout);
                
                if (result.isSuccess()) {
                    // 将结果转换为JSON字符串
                    return objectMapper.writeValueAsString(result.getResult());
                } else {
                    throw new RuntimeException("Dubbo call failed: " + result.getErrorMessage());
                }
                        
            } catch (Exception e) {
                log.error("Failed to execute tool call: {}", toolName, e);
                throw new RuntimeException("Tool call failed: " + e.getMessage(), e);
            }
        }
    }
}

