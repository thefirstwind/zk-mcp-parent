package com.pajk.mcpmetainfo.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpmetainfo.core.mcp.McpProtocol;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.regex.*;

/**
 * MCP协议服务实现
 * 提供标准的MCP JSON-RPC 2.0协议支持
 */
/**
 * MCP 协议处理服务
 * 
 * @traceability
 *   - Requirement: REQ-20260211-003 (虚拟项目参数类型修复)
 *   - Design: docs/requirements/REQ-20260211-003.md
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpProtocolService {

    private final McpConverterService mcpConverterService;
    private final McpExecutorService mcpExecutorService;
    private final McpResourcesService mcpResourcesService;
    private final McpPromptsService mcpPromptsService;
    private final McpLoggingService mcpLoggingService;
    private final VirtualProjectService virtualProjectService;
    private final ObjectMapper objectMapper;
    
    // 流式调用管理
    private final Map<String, StreamSession> streamSessions = new ConcurrentHashMap<>();
    private final AtomicLong streamIdGenerator = new AtomicLong(1);
    
    // MCP服务器信息
    private static final McpProtocol.ServerInfo SERVER_INFO = McpProtocol.ServerInfo.builder()
            .name("zkInfo-MCP-Server")
            .version("1.0.0")
            .description("Dubbo服务的MCP协议适配器，支持完整的MCP 2024-11-05规范")
            .capabilities(Arrays.asList("tools", "resources", "prompts", "logging", "streaming", "sse", "websocket"))
            .metadata(Map.of(
                "dubbo.version", "3.2.0",
                "zookeeper.enabled", true,
                "streaming.supported", true,
                "resources.supported", true,
                "prompts.supported", true,
                "logging.supported", true,
                "mcp.version", "2024-11-05"
            ))
            .build();

    /**
     * 处理MCP JSON-RPC请求
     */
    public Mono<McpProtocol.JsonRpcResponse> handleRequest(McpProtocol.JsonRpcRequest request) {
        try {
            log.info("处理MCP请求: method={}, id={}", request.getMethod(), request.getId());
            
            switch (request.getMethod()) {
                case McpProtocol.Methods.INITIALIZE:
                    return handleInitialize(request);
                case McpProtocol.Methods.LIST_TOOLS:
                    return handleListTools(request);
                case McpProtocol.Methods.CALL_TOOL:
                    return handleCallTool(request);
                case McpProtocol.Methods.STREAM_TOOL:
                    return handleStreamTool(request);
                case McpProtocol.Methods.LIST_RESOURCES:
                    return handleListResources(request);
                case McpProtocol.Methods.READ_RESOURCE:
                    return handleReadResource(request);
                case McpProtocol.Methods.SUBSCRIBE_RESOURCE:
                    return handleSubscribeResource(request);
                case McpProtocol.Methods.UNSUBSCRIBE_RESOURCE:
                    return handleUnsubscribeResource(request);
                case McpProtocol.Methods.LIST_PROMPTS:
                    return handleListPrompts(request);
                case McpProtocol.Methods.GET_PROMPT:
                    return handleGetPrompt(request);
                case McpProtocol.Methods.LOG_MESSAGE:
                    return handleLogMessage(request);
                case McpProtocol.Methods.PING:
                    return handlePing(request);
                default:
                    return Mono.just(createErrorResponse(request.getId(), 
                        McpProtocol.ErrorCodes.METHOD_NOT_FOUND, 
                        "方法不存在: " + request.getMethod()));
            }
        } catch (Exception e) {
            log.error("处理MCP请求失败", e);
            return Mono.just(createErrorResponse(request.getId(), 
                McpProtocol.ErrorCodes.INTERNAL_ERROR, 
                "内部错误: " + e.getMessage()));
        }
    }

    /**
     * 处理初始化请求
     */
    private Mono<McpProtocol.JsonRpcResponse> handleInitialize(McpProtocol.JsonRpcRequest request) {
        try {
            McpProtocol.InitializeParams params = objectMapper.convertValue(
                request.getParams(), McpProtocol.InitializeParams.class);
            
            log.info("MCP客户端初始化: {}", params.getClientInfo().getName());
            
            McpProtocol.InitializeResult result = McpProtocol.InitializeResult.builder()
                    .protocolVersion("2024-11-05")
                    .serverInfo(SERVER_INFO)
                    .capabilities(Map.of(
                        "tools", Map.of("listChanged", true),
                        "resources", Map.of("subscribe", true, "listChanged", true),
                        "prompts", Map.of("listChanged", true),
                        "logging", Map.of(),
                        "streaming", Map.of("supported", true),
                        "sse", Map.of("supported", true)
                    ))
                    .build();
            
            return Mono.just(createSuccessResponse(request.getId(), result));
        } catch (Exception e) {
            return Mono.just(createErrorResponse(request.getId(), 
                McpProtocol.ErrorCodes.INVALID_PARAMS, 
                "初始化参数无效: " + e.getMessage()));
        }
    }

    /**
     * 处理工具列表请求
     */
    private Mono<McpProtocol.JsonRpcResponse> handleListTools(McpProtocol.JsonRpcRequest request) {
        try {
            McpProtocol.ListToolsParams params = request.getParams() != null ? 
                objectMapper.convertValue(request.getParams(), McpProtocol.ListToolsParams.class) :
                new McpProtocol.ListToolsParams();
            
            // 获取所有MCP工具
            List<McpProtocol.McpTool> tools = getAllMcpTools();
            
            // 支持分页（如果有cursor）
            List<McpProtocol.McpTool> pagedTools = tools;
            String nextCursor = null;
            
            if (params.getCursor() != null) {
                // 简单的分页实现
                int startIndex = Integer.parseInt(params.getCursor());
                int pageSize = 50;
                int endIndex = Math.min(startIndex + pageSize, tools.size());
                
                pagedTools = tools.subList(startIndex, endIndex);
                if (endIndex < tools.size()) {
                    nextCursor = String.valueOf(endIndex);
                }
            }
            
            McpProtocol.ListToolsResult result = McpProtocol.ListToolsResult.builder()
                    .tools(pagedTools)
                    .nextCursor(nextCursor)
                    .build();
            
            log.info("返回MCP工具列表: {} 个工具", pagedTools.size());
            return Mono.just(createSuccessResponse(request.getId(), result));
            
        } catch (Exception e) {
            return Mono.just(createErrorResponse(request.getId(), 
                McpProtocol.ErrorCodes.INTERNAL_ERROR, 
                "获取工具列表失败: " + e.getMessage()));
        }
    }

    /**
     * 处理工具调用请求
     */
    private Mono<McpProtocol.JsonRpcResponse> handleCallTool(McpProtocol.JsonRpcRequest request) {
        try {
            McpProtocol.CallToolParams params = objectMapper.convertValue(
                request.getParams(), McpProtocol.CallToolParams.class);
            
            String toolName = params.getName();
            Map<String, Object> arguments = params.getArguments();
            Integer timeout = params.getTimeout() != null ? params.getTimeout() : 5000;
            
            log.info("调用MCP工具: name={}, args={}", toolName, arguments);
            
            // 检查是否为流式调用
            if (Boolean.TRUE.equals(params.getStream())) {
                return handleStreamToolCall(request.getId(), toolName, arguments, timeout);
            }
            
            // 执行同步调用
            return executeToolCall(toolName, arguments, timeout)
                    .map(result -> createSuccessResponse(request.getId(), result))
                    .onErrorResume(error -> {
                        log.error("工具执行出错: toolName={}, error={}", toolName, error.getMessage(), error);
                        return Mono.just(createErrorResponse(request.getId(), 
                            McpProtocol.ErrorCodes.TOOL_EXECUTION_ERROR, 
                            "工具执行失败: " + error.getMessage()));
                    });
                        
        } catch (Exception e) {
            return Mono.just(createErrorResponse(request.getId(), 
                McpProtocol.ErrorCodes.INVALID_PARAMS, 
                "工具调用参数无效: " + e.getMessage()));
        }
    }

    /**
     * 处理流式工具调用
     */
    private Mono<McpProtocol.JsonRpcResponse> handleStreamTool(McpProtocol.JsonRpcRequest request) {
        try {
            McpProtocol.CallToolParams params = objectMapper.convertValue(
                request.getParams(), McpProtocol.CallToolParams.class);
            
            return handleStreamToolCall(request.getId(), params.getName(), 
                params.getArguments(), params.getTimeout());
                
        } catch (Exception e) {
            return Mono.just(createErrorResponse(request.getId(), 
                McpProtocol.ErrorCodes.INVALID_PARAMS, 
                "流式调用参数无效: " + e.getMessage()));
        }
    }

    /**
     * 处理Ping请求
     */
    private Mono<McpProtocol.JsonRpcResponse> handlePing(McpProtocol.JsonRpcRequest request) {
        return Mono.just(createSuccessResponse(request.getId(), Map.of("pong", true)));
    }

    /**
     * 执行流式工具调用
     */
    private Mono<McpProtocol.JsonRpcResponse> handleStreamToolCall(String requestId, String toolName, 
            Map<String, Object> arguments, Integer timeout) {
        
        String streamId = "stream_" + streamIdGenerator.getAndIncrement();
        
        // 创建流式会话
        StreamSession session = new StreamSession(streamId, toolName, arguments);
        streamSessions.put(streamId, session);
        
        // 返回流式调用初始响应
        McpProtocol.CallToolResult result = McpProtocol.CallToolResult.builder()
                .streamId(streamId)
                .hasMore(true)
                .content(List.of(McpProtocol.McpContent.builder()
                    .type("text")
                    .text("流式调用已启动，streamId: " + streamId)
                    .build()))
                .build();
        
        // 异步执行实际调用
        executeStreamingCall(session, timeout);
        
        return Mono.just(createSuccessResponse(requestId, result));
    }

    /**
     * 执行工具调用
     * 
     * @traceability REQ-20260211-003 (支持显式参数类型转换)
     */
    public Mono<McpProtocol.CallToolResult> executeToolCall(String toolName, 
            Map<String, Object> arguments, int timeoutMs) {

        
        return Mono.fromCallable(() -> {
            // 尝试提取参数类型
            String[] explicitParameterTypes = extractParameterTypes(toolName, arguments);
            if (explicitParameterTypes != null) {
                log.info("工具调用 {}: 提取到显式参数类型: {}", toolName, Arrays.toString(explicitParameterTypes));
            } else {
                log.info("工具调用 {}: 未提取到显式参数类型，将使用 Dubbo 自动推断", toolName);
            }
            
            // 转换参数格式
            Object[] args = convertArgumentsToArray(arguments);
            
            // 如果只有提取到了显式参数类型，尝试转换参数值以匹配类型
            // 这是关键：修复 Integer -> Long 的问题
            if (explicitParameterTypes != null && args != null && explicitParameterTypes.length == args.length) {
                for (int i = 0; i < args.length; i++) {
                    String targetType = explicitParameterTypes[i];
                    Object originalValue = args[i];
                    
                    if (originalValue != null && targetType != null) {
                        try {
                            if ("java.lang.Long".equals(targetType) && originalValue instanceof Integer) {
                                args[i] = ((Integer) originalValue).longValue();
                                log.info("参数[{}] 自动转换: Integer {} -> Long {}", i, originalValue, args[i]);
                            } else if ("java.lang.Long".equals(targetType) && originalValue instanceof String) {
                                args[i] = Long.parseLong((String) originalValue);
                                log.info("参数[{}] 自动转换: String {} -> Long {}", i, originalValue, args[i]);
                            } else if ("java.lang.Integer".equals(targetType) && originalValue instanceof Long) {
                                args[i] = ((Long) originalValue).intValue();
                                log.info("参数[{}] 自动转换: Long {} -> Integer {}", i, originalValue, args[i]);
                            } else if ("java.lang.Double".equals(targetType) && originalValue instanceof Integer) {
                                args[i] = ((Integer) originalValue).doubleValue();
                            } else if ("java.lang.Float".equals(targetType) && originalValue instanceof Integer) {
                                args[i] = ((Integer) originalValue).floatValue();
                            }
                            // 可以添加更多类型的转换
                        } catch (Exception e) {
                            log.warn("参数[{}] 类型转换失败: {} -> {}, error={}", i, originalValue.getClass().getName(), targetType, e.getMessage());
                        }
                    }
                }
            }
            
            log.debug("转换后的参数: args={}", args != null ? java.util.Arrays.toString(args) : "null");
            
            // 执行Dubbo调用
            McpExecutorService.McpCallResult result = mcpExecutorService.executeToolCallSync(toolName, args, timeoutMs, explicitParameterTypes);
            log.debug("Dubbo调用结果: success={}, result={}, error={}", 
                result != null && result.isSuccess(), 
                result != null ? result.getResult() : "null",
                result != null ? result.getErrorMessage() : "null");
            
            // 构建MCP响应
            List<McpProtocol.McpContent> content = new ArrayList<>();
            
            if (result != null && result.isSuccess()) {
                log.info("工具调用成功，返回结果: {}", result.getResult());
                content.add(McpProtocol.McpContent.builder()
                    .type("json")
                    .data(result.getResult())
                    .build());
            } else if (result != null && !result.isSuccess()) {
                log.error("工具调用失败: {}", result.getErrorMessage());
                content.add(McpProtocol.McpContent.builder()
                    .type("error")
                    .text("调用失败: " + result.getErrorMessage())
                    .build());
            } else {
                log.warn("工具调用返回null结果");
                content.add(McpProtocol.McpContent.builder()
                    .type("text")
                    .text("调用成功，返回结果为空")
                    .build());
            }
            
            log.debug("构建的contentList大小: {}, content类型: {}", 
                content.size(), 
                content.isEmpty() ? "empty" : content.get(0).getType());
            
            return McpProtocol.CallToolResult.builder()
                    .content(content)
                    .isError(false)
                    .build();
        });
    }

    /**
     * 从工具定义中提取参数类型
     */
    public String[] extractParameterTypes(String toolName, Map<String, Object> arguments) {

        if (arguments == null || arguments.isEmpty()) {
            return null;
        }

        try {
            // 查找工具定义
            List<McpProtocol.McpTool> tools = getAllMcpTools();
            McpProtocol.McpTool tool = tools.stream()
                    .filter(t -> t.getName() != null && t.getName().equals(toolName))
                    .findFirst()
                    .orElse(null);
                    
            if (tool == null) {
                return null;
            }
            
            // ✅ 优先使用 parameterTypes 字段（新方案，最可靠）
            List<String> parameterTypes = tool.getParameterTypes();
            if (parameterTypes != null && !parameterTypes.isEmpty()) {
                log.info("✅ 使用工具定义中的 parameterTypes: {} -> {}", toolName, parameterTypes);
                return parameterTypes.toArray(new String[0]);
            }
            
            // 降级方案：从inputSchema的description解析（旧方案）
            if (tool.getInputSchema() == null) {
                return null;
            }
            
            Map<String, Object> schema = tool.getInputSchema();
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            if (properties == null) {
                return null;
            }
            
            List<String> types = new ArrayList<>();
            // 注意：这里假设 arguments 的迭代顺序与 convertArgumentsToArray 方法中的顺序一致
            // 对于 LinkedHashMap（Jackson默认），这是成立的
            for (String key : arguments.keySet()) {
                // 如果包含 args 键且是唯一键，可能是包装器格式，此时很难确定类型对应关系，跳过推断
                if ("args".equals(key) && arguments.size() == 1) {
                    return null;
                }
                
                Object propObj = properties.get(key);
                if (propObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> prop = (Map<String, Object>) propObj;
                    String desc = (String) prop.get("description");
                    if (desc != null) {
                        // 解析 (类型: Long)
                        Pattern pattern = Pattern.compile("\\(类型:\\s*([a-zA-Z0-9_.]+)\\)");
                        Matcher matcher = pattern.matcher(desc);
                        if (matcher.find()) {
                            String type = matcher.group(1).trim();
                            // 映射常见类型
                            // 注意：这里的类型映射需要与 Dubbo 支持的类型一致
                            if ("Long".equalsIgnoreCase(type)) type = "java.lang.Long";
                            else if ("Integer".equalsIgnoreCase(type)) type = "java.lang.Integer";
                            else if ("String".equalsIgnoreCase(type)) type = "java.lang.String";
                            else if ("Boolean".equalsIgnoreCase(type)) type = "java.lang.Boolean";
                            else if ("Double".equalsIgnoreCase(type)) type = "java.lang.Double";
                            else if ("Float".equalsIgnoreCase(type)) type = "java.lang.Float";
                            else if ("int".equals(type)) type = "int";
                            else if ("long".equals(type)) type = "long";
                            
                            types.add(type);
                        } else {
                            // 如果无法解析类型，添加 null 占位
                            types.add(null);
                        }
                    } else {
                        types.add(null);
                    }
                } else {
                    types.add(null);
                }
            }
            
            // 如果提取到的类型包含 null，说明部分类型未知，最好回退到 Dubbo 自动推断
            if (types.contains(null)) {
                log.warn("⚠️ 从description解析参数类型时包含null: {}", types);
                return null;
            }
            
            log.info("⚠️ 使用从description解析的参数类型（旧方案）: {} -> {}", toolName, types);
            return types.toArray(new String[0]);
            
        } catch (Exception e) {
            log.warn("提取参数类型失败: tool=" + toolName, e);
            return null;
        }
    }

    /**
     * 执行流式调用
     */
    private void executeStreamingCall(StreamSession session, Integer timeout) {
        // 在后台线程执行
        new Thread(() -> {
            try {
                Object[] args = convertArgumentsToArray(session.getArguments());
                McpExecutorService.McpCallResult result = mcpExecutorService.executeToolCallSync(session.getToolName(), args, timeout);
                
                // 模拟流式数据推送
                session.addChunk(McpProtocol.StreamChunk.builder()
                    .id(session.getStreamId())
                    .type("data")
                    .data(Map.of("progress", 50, "message", "执行中..."))
                    .timestamp(getCurrentTimestamp())
                    .isLast(false)
                    .build());
                
                Thread.sleep(100); // 模拟处理时间
                
                session.addChunk(McpProtocol.StreamChunk.builder()
                    .id(session.getStreamId())
                    .type("data")
                    .data(result.isSuccess() ? result.getResult() : Map.of("error", result.getErrorMessage()))
                    .timestamp(getCurrentTimestamp())
                    .isLast(true)
                    .build());
                
                session.setCompleted(true);
                
            } catch (Exception e) {
                session.addChunk(McpProtocol.StreamChunk.builder()
                    .id(session.getStreamId())
                    .type("error")
                    .data(Map.of("error", e.getMessage()))
                    .timestamp(getCurrentTimestamp())
                    .isLast(true)
                    .build());
                session.setCompleted(true);
            }
        }).start();
    }

    /**
     * 获取流式数据
     */
    public Flux<McpProtocol.StreamChunk> getStreamData(String streamId) {
        StreamSession session = streamSessions.get(streamId);
        if (session == null) {
            return Flux.error(new RuntimeException("流式会话不存在: " + streamId));
        }
        
        return session.getFlux()
                .doOnComplete(() -> {
                    // 清理会话
                    streamSessions.remove(streamId);
                    log.info("流式会话已清理: streamId={}", streamId);
                })
                .doOnError(error -> {
                    log.error("流式数据传输错误: streamId=" + streamId, error);
                    streamSessions.remove(streamId);
                });
    }

    /**
     * 获取所有 MCP 工具
     * 
     * @traceability REQ-20260211-003 (加载并合并虚拟配置中的 parameterTypes)
     */
    public List<McpProtocol.McpTool> getAllMcpTools() {
        try {
            // 1. 从 ZooKeeper 获取 Dubbo 服务并转换
            // 使用 LinkedHashMap 保持顺序并去重
            Map<String, McpProtocol.McpTool> uniqueTools = new java.util.LinkedHashMap<>();
            
            try {
                mcpConverterService.convertAllApplicationsToMcp().stream()
                        .flatMap(app -> app.getTools().stream())
                        .map(this::convertToMcpTool)
                        .forEach(tool -> {
                            String toolName = tool.getName();
                            if (!uniqueTools.containsKey(toolName)) {
                                uniqueTools.put(toolName, tool);
                            } else {
                                McpProtocol.McpTool existing = uniqueTools.get(toolName);
                                if (tool.getOnline() && !existing.getOnline()) {
                                    existing.setOnline(true);
                                    existing.setProvider(tool.getProvider());
                                }
                            }
                        });
            } catch (Exception e) {
                log.warn("获取 Dubbo 服务失败", e);
            }

            // 1.5 从 VirtualProjectService 获取虚拟项目并转换
            try {
                if (virtualProjectService != null) {
                    List<VirtualProjectService.VirtualProjectInfo> virtualProjects = virtualProjectService.getAllVirtualProjects();
                    for (VirtualProjectService.VirtualProjectInfo vp : virtualProjects) {
                        try {
                            List<McpProtocol.McpTool> vpTools = mcpConverterService.convertVirtualProjectToMcpTools(vp.getProject().getId())
                                    .stream()
                                    .map(this::convertToMcpTool)
                                    .collect(Collectors.toList());
                            
                            log.info("🔍 Found {} tools for virtual project: {}", vpTools.size(), vp.getProject().getProjectName());
                            
                            for (McpProtocol.McpTool tool : vpTools) {
                                String toolName = tool.getName();
                                if (!uniqueTools.containsKey(toolName)) {
                                    uniqueTools.put(toolName, tool);
                                } else {
                                    // 工具已存在，可能是因为实际项目也被扫描到了
                                    // 确保 parameterTypes 存在
                                    McpProtocol.McpTool existing = uniqueTools.get(toolName);
                                    if (existing.getParameterTypes() == null || existing.getParameterTypes().isEmpty()) {
                                        if (tool.getParameterTypes() != null && !tool.getParameterTypes().isEmpty()) {
                                            existing.setParameterTypes(tool.getParameterTypes());
                                            log.debug("✅ 为已存在的工具补充 parameterTypes (from Virtual Project): {} -> {}", toolName, tool.getParameterTypes());
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("处理虚拟项目工具失败: {}", vp.getProject().getProjectName(), e);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("获取虚拟项目服务失败", e);
            }

            // 2. 扫描本地虚拟项目配置 (virtual-projects/*.json)
            try {
                java.io.File virtualProjectDir = new java.io.File("virtual-projects");
                if (!virtualProjectDir.exists() || !virtualProjectDir.isDirectory()) {
                    virtualProjectDir = new java.io.File("zkInfo/virtual-projects");
                }
                if (!virtualProjectDir.exists() || !virtualProjectDir.isDirectory()) {
                    virtualProjectDir = new java.io.File("zk-mcp-parent/zkInfo/virtual-projects");
                }
                
                log.info("🔍 Scanning virtual project directory: {}", virtualProjectDir.getAbsolutePath());
                if (virtualProjectDir.exists() && virtualProjectDir.isDirectory()) {
                    java.io.File[] files = virtualProjectDir.listFiles((dir, name) -> name.endsWith(".json"));
                    if (files != null) {
                        log.info("🔍 Found {} virtual project files", files.length);
                        for (java.io.File file : files) {
                            try {
                                log.info("🔍 Processing file: {}", file.getName());
                                Map<String, Object> config = objectMapper.readValue(file, Map.class);
                                if (config.containsKey("tools")) {
                                    List<Map<String, Object>> tools = (List<Map<String, Object>>) config.get("tools");
                                    log.info("🔍 File {} contains {} tools", file.getName(), tools.size());
                                    for (Map<String, Object> toolMap : tools) {
                                        String toolName = (String) toolMap.get("toolName");
                                        if (toolName == null) toolName = (String) toolMap.get("name"); // 兼容旧格式
                                        
                                        if (toolName != null) {
                                            // 解析 inputSchema
                                            Map<String, Object> inputSchema = null;
                                            Object schemaObj = toolMap.get("inputSchema");
                                            if (schemaObj instanceof String) {
                                                try {
                                                    // 如果是字符串，解析为 Map
                                                    inputSchema = objectMapper.readValue((String) schemaObj, Map.class);
                                                } catch (Exception e) {
                                                    log.warn("Parsing inputSchema failed for {}", toolName);
                                                }
                                            } else if (schemaObj instanceof Map) {
                                                inputSchema = (Map<String, Object>) schemaObj;
                                            }
                                            
                                            // 解析 parameterTypes list
                                            List<String> parameterTypes = null;
                                            if (toolMap.containsKey("parameterTypes")) {
                                                parameterTypes = (List<String>) toolMap.get("parameterTypes");
                                            }

                                            // 如果工具已经存在，尝试补充信息（如 parameterTypes）
                                            McpProtocol.McpTool existing = uniqueTools.get(toolName);
                                            if (existing != null) {
                                                if (existing.getParameterTypes() == null || existing.getParameterTypes().isEmpty()) {
                                                    if (parameterTypes != null && !parameterTypes.isEmpty()) {
                                                        existing.setParameterTypes(parameterTypes);
                                                        log.debug("✅ 为已存在的工具补充 parameterTypes: {} -> {}", toolName, parameterTypes);
                                                    }
                                                }
                                                // ✅ 总是更新 inputSchema 和 description，因为虚拟项目的定义通常包含更精确的类型推导信息
                                                if (inputSchema != null && !inputSchema.isEmpty()) {
                                                    existing.setInputSchema(inputSchema);
                                                    log.debug("✅ 为已存在的工具更新 inputSchema: {}", toolName);
                                                }
                                                if (toolMap.get("description") != null) {
                                                    existing.setDescription((String) toolMap.get("description"));
                                                }
                                            } else {
                                                // 如果工具不存在（可能因为 Provider 离线或未注册），添加虚拟工具定义
                                                // 这样 extractParameterTypes 就能找到它
                                                McpProtocol.McpTool virtualTool = McpProtocol.McpTool.builder()
                                                        .name(toolName)
                                                        .description((String) toolMap.get("description"))
                                                        .inputSchema(inputSchema) // 可能包含 parameters info
                                                        .parameterTypes(parameterTypes) // 明确的参数类型
                                                        .streamable(isStreamable(toolName))
                                                        .online(true) // 假设虚拟项目都是为了调用在线服务
                                                        .group("virtual")
                                                        .version("1.0.0")
                                                        .build();
                                                
                                                uniqueTools.put(toolName, virtualTool);
                                                log.info("✅ 加载虚拟项目工具定义: {}", toolName);
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("Failed to parse virtual project config: {}", file.getName(), e);
                            }
                        }
                    }
                } else {
                     log.warn("⚠️ virtual-projects directory does not exist or is not a directory: {}", virtualProjectDir.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("Failed to scan virtual projects", e);
            }
            
            return new ArrayList<>(uniqueTools.values());
        } catch (Exception e) {
            log.error("获取MCP工具列表失败", e);
            return new ArrayList<>();
        }
    }


    /**
     * 转换为MCP工具格式
     */
    private McpProtocol.McpTool convertToMcpTool(com.pajk.mcpmetainfo.core.model.McpResponse.McpTool tool) {
        return McpProtocol.McpTool.builder()
                .name(tool.getName())
                .description(tool.getDescription())
                .inputSchema(tool.getInputSchema())
                .parameterTypes(tool.getParameterTypes())
                .streamable(isStreamable(tool.getName()))
                .provider(tool.getProvider())
                .online(tool.isOnline())
                .group(tool.getGroup())
                .version(tool.getVersion())
                .build();
    }

    /**
     * 判断工具是否支持流式调用
     */
    private Boolean isStreamable(String toolName) {
        // 某些特定的服务方法支持流式调用
        return toolName.contains("stream") || 
               toolName.contains("batch") || 
               toolName.contains("export") ||
               toolName.contains("import");
    }

    /**
     * 转换参数格式
     */
    private Object[] convertArgumentsToArray(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return new Object[0];
        }
        
        log.debug("convertArgumentsToArray 输入: {}", arguments);
        
        // 如果参数是按位置传递的
        if (arguments.containsKey("args")) {
            Object argsValue = arguments.get("args");
            log.debug("argsValue 类型: {}, 值: {}", argsValue != null ? argsValue.getClass() : "null", argsValue);
            
            // 如果已经是List，直接转换
            if (argsValue instanceof List) {
                List<?> argsList = (List<?>) argsValue;
                Object[] result = argsList.toArray();
                log.debug("从 List 转换，结果: {}", java.util.Arrays.toString(result));
                return result;
            }
            
            // 如果是String，尝试解析为JSON数组
            if (argsValue instanceof String) {
                String argsStr = (String) argsValue;
                try {
                    // 简单处理数组格式：[1] 或 [1, 2, 3]
                    if (argsStr.startsWith("[") && argsStr.endsWith("]")) {
                        argsStr = argsStr.substring(1, argsStr.length() - 1);
                        if (argsStr.trim().isEmpty()) {
                            return new Object[0];
                        }
                        String[] parts = argsStr.split(",");
                        Object[] result = new Object[parts.length];
                        for (int i = 0; i < parts.length; i++) {
                            String part = parts[i].trim();
                            // 尝试解析为数字
                            try {
                                if (part.contains(".")) {
                                    result[i] = Double.parseDouble(part);
                                } else {
                                    result[i] = Long.parseLong(part);
                                }
                            } catch (NumberFormatException e) {
                                // 保持为字符串，去掉引号
                                result[i] = part.replaceAll("^\"|\"$", "");
                            }
                        }
                        return result;
                    }
                } catch (Exception e) {
                    log.warn("解析args字符串失败: {}", argsStr, e);
                }
            }
            
            // 其他情况，作为单个参数
            return new Object[]{argsValue};
        }
        
        // 否则按参数名传递（需要根据方法签名转换）
        return arguments.values().toArray();
    }

    /**
     * 创建成功响应
     */
    private McpProtocol.JsonRpcResponse createSuccessResponse(String id, Object result) {
        return McpProtocol.JsonRpcResponse.builder()
                .id(id)
                .result(result)
                .build();
    }

    /**
     * 创建错误响应
     */
    private McpProtocol.JsonRpcResponse createErrorResponse(String id, int code, String message) {
        return McpProtocol.JsonRpcResponse.builder()
                .id(id)
                .error(McpProtocol.JsonRpcError.builder()
                    .code(code)
                    .message(message)
                    .build())
                .build();
    }

    /**
     * 获取当前时间戳
     */
    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * 流式会话管理
     */
    @lombok.Data
    private static class StreamSession {
        private final String streamId;
        private final String toolName;
        private final Map<String, Object> arguments;
        private final Sinks.Many<McpProtocol.StreamChunk> sink = Sinks.many().multicast().onBackpressureBuffer();
        private boolean completed = false;
        
        public StreamSession(String streamId, String toolName, Map<String, Object> arguments) {
            this.streamId = streamId;
            this.toolName = toolName;
            this.arguments = arguments;
        }
        
        public void addChunk(McpProtocol.StreamChunk chunk) {
            sink.tryEmitNext(chunk);
            if (chunk.getIsLast() != null && chunk.getIsLast()) {
                sink.tryEmitComplete();
                this.completed = true;
            }
        }
        
        public Flux<McpProtocol.StreamChunk> getFlux() {
            return sink.asFlux();
        }
    }

    // ========== Resources 处理方法 ==========

    /**
     * 处理资源列表请求
     */
    private Mono<McpProtocol.JsonRpcResponse> handleListResources(McpProtocol.JsonRpcRequest request) {
        try {
            McpProtocol.ListResourcesParams params = request.getParams() != null ? 
                objectMapper.convertValue(request.getParams(), McpProtocol.ListResourcesParams.class) :
                new McpProtocol.ListResourcesParams();
            
            return mcpResourcesService.listResources(params)
                .map(result -> createSuccessResponse(request.getId(), result))
                .onErrorReturn(createErrorResponse(request.getId(), 
                    McpProtocol.ErrorCodes.INTERNAL_ERROR, 
                    "获取资源列表失败"));
                    
        } catch (Exception e) {
            return Mono.just(createErrorResponse(request.getId(), 
                McpProtocol.ErrorCodes.INVALID_PARAMS, 
                "资源列表参数无效: " + e.getMessage()));
        }
    }

    /**
     * 处理读取资源请求
     */
    private Mono<McpProtocol.JsonRpcResponse> handleReadResource(McpProtocol.JsonRpcRequest request) {
        try {
            McpProtocol.ReadResourceParams params = objectMapper.convertValue(
                request.getParams(), McpProtocol.ReadResourceParams.class);
            
            return mcpResourcesService.readResource(params)
                .map(result -> createSuccessResponse(request.getId(), result))
                .onErrorReturn(createErrorResponse(request.getId(), 
                    McpProtocol.ErrorCodes.RESOURCE_NOT_FOUND, 
                    "资源不存在或无法访问"));
                    
        } catch (Exception e) {
            return Mono.just(createErrorResponse(request.getId(), 
                McpProtocol.ErrorCodes.INVALID_PARAMS, 
                "读取资源参数无效: " + e.getMessage()));
        }
    }

    /**
     * 处理订阅资源请求
     */
    private Mono<McpProtocol.JsonRpcResponse> handleSubscribeResource(McpProtocol.JsonRpcRequest request) {
        try {
            McpProtocol.SubscribeResourceParams params = objectMapper.convertValue(
                request.getParams(), McpProtocol.SubscribeResourceParams.class);
            
            return mcpResourcesService.subscribeResource("mcp_client", params)
                .then(Mono.just(createSuccessResponse(request.getId(), Map.of("subscribed", true))))
                .onErrorReturn(createErrorResponse(request.getId(), 
                    McpProtocol.ErrorCodes.SUBSCRIPTION_FAILED, 
                    "订阅资源失败"));
                    
        } catch (Exception e) {
            return Mono.just(createErrorResponse(request.getId(), 
                McpProtocol.ErrorCodes.INVALID_PARAMS, 
                "订阅资源参数无效: " + e.getMessage()));
        }
    }

    /**
     * 处理取消订阅资源请求
     */
    private Mono<McpProtocol.JsonRpcResponse> handleUnsubscribeResource(McpProtocol.JsonRpcRequest request) {
        try {
            McpProtocol.UnsubscribeResourceParams params = objectMapper.convertValue(
                request.getParams(), McpProtocol.UnsubscribeResourceParams.class);
            
            return mcpResourcesService.unsubscribeResource("mcp_client", params)
                .then(Mono.just(createSuccessResponse(request.getId(), Map.of("unsubscribed", true))))
                .onErrorReturn(createErrorResponse(request.getId(), 
                    McpProtocol.ErrorCodes.SUBSCRIPTION_FAILED, 
                    "取消订阅资源失败"));
                    
        } catch (Exception e) {
            return Mono.just(createErrorResponse(request.getId(), 
                McpProtocol.ErrorCodes.INVALID_PARAMS, 
                "取消订阅资源参数无效: " + e.getMessage()));
        }
    }

    // ========== Prompts 处理方法 ==========

    /**
     * 处理提示列表请求
     */
    private Mono<McpProtocol.JsonRpcResponse> handleListPrompts(McpProtocol.JsonRpcRequest request) {
        try {
            McpProtocol.ListPromptsParams params = request.getParams() != null ? 
                objectMapper.convertValue(request.getParams(), McpProtocol.ListPromptsParams.class) :
                new McpProtocol.ListPromptsParams();
            
            return mcpPromptsService.listPrompts(params)
                .map(result -> createSuccessResponse(request.getId(), result))
                .onErrorReturn(createErrorResponse(request.getId(), 
                    McpProtocol.ErrorCodes.INTERNAL_ERROR, 
                    "获取提示列表失败"));
                    
        } catch (Exception e) {
            return Mono.just(createErrorResponse(request.getId(), 
                McpProtocol.ErrorCodes.INVALID_PARAMS, 
                "提示列表参数无效: " + e.getMessage()));
        }
    }

    /**
     * 处理获取提示请求
     */
    private Mono<McpProtocol.JsonRpcResponse> handleGetPrompt(McpProtocol.JsonRpcRequest request) {
        try {
            McpProtocol.GetPromptParams params = objectMapper.convertValue(
                request.getParams(), McpProtocol.GetPromptParams.class);
            
            return mcpPromptsService.getPrompt(params)
                .map(result -> createSuccessResponse(request.getId(), result))
                .onErrorReturn(createErrorResponse(request.getId(), 
                    McpProtocol.ErrorCodes.PROMPT_NOT_FOUND, 
                    "提示不存在或参数无效"));
                    
        } catch (Exception e) {
            return Mono.just(createErrorResponse(request.getId(), 
                McpProtocol.ErrorCodes.INVALID_PARAMS, 
                "获取提示参数无效: " + e.getMessage()));
        }
    }

    // ========== Logging 处理方法 ==========

    /**
     * 处理日志消息请求
     */
    private Mono<McpProtocol.JsonRpcResponse> handleLogMessage(McpProtocol.JsonRpcRequest request) {
        try {
            McpProtocol.LogMessageParams params = objectMapper.convertValue(
                request.getParams(), McpProtocol.LogMessageParams.class);
            
            return mcpLoggingService.logMessage(params)
                .then(Mono.just(createSuccessResponse(request.getId(), Map.of("logged", true))))
                .onErrorReturn(createErrorResponse(request.getId(), 
                    McpProtocol.ErrorCodes.LOGGING_ERROR, 
                    "记录日志失败"));
                    
        } catch (Exception e) {
            return Mono.just(createErrorResponse(request.getId(), 
                McpProtocol.ErrorCodes.INVALID_PARAMS, 
                "日志参数无效: " + e.getMessage()));
        }
    }

    // ========== 公共方法（用于测试） ==========

    /**
     * 列出资源
     */
    public Mono<McpProtocol.ListResourcesResult> listResources(McpProtocol.ListResourcesParams params) {
        return mcpResourcesService.listResources(params);
    }

    /**
     * 读取资源
     */
    public Mono<McpProtocol.ReadResourceResult> readResource(McpProtocol.ReadResourceParams params) {
        return mcpResourcesService.readResource(params);
    }

    /**
     * 订阅资源
     */
    public Mono<Void> subscribeResource(String clientId, McpProtocol.SubscribeResourceParams params) {
        return mcpResourcesService.subscribeResource(clientId, params);
    }

    /**
     * 取消订阅资源
     */
    public Mono<Void> unsubscribeResource(String clientId, McpProtocol.UnsubscribeResourceParams params) {
        return mcpResourcesService.unsubscribeResource(clientId, params);
    }

    /**
     * 列出提示
     */
    public Mono<McpProtocol.ListPromptsResult> listPrompts(McpProtocol.ListPromptsParams params) {
        return mcpPromptsService.listPrompts(params);
    }

    /**
     * 获取提示
     */
    public Mono<McpProtocol.GetPromptResult> getPrompt(McpProtocol.GetPromptParams params) {
        return mcpPromptsService.getPrompt(params);
    }

    /**
     * 记录日志
     */
    public Mono<Void> log(McpProtocol.LogParams params) {
        // 将LogParams转换为LogMessageParams
        McpProtocol.LogMessageParams logMessageParams = McpProtocol.LogMessageParams.builder()
                .level(params.getLevel())
                .data(params.getData())
                .logger(params.getLogger())
                .build();
        return mcpLoggingService.logMessage(logMessageParams);
    }
}
