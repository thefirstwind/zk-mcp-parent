package com.zkinfo.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkinfo.ai.model.McpProtocol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI对话服务
 * 使用DeepSeek LLM分析用户意图并调用MCP工具
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConversationService {

    private final ChatModel chatModel;
    private final McpClientService mcpClientService;
    private final ChatMemory chatMemory = new InMemoryChatMemory();
    
    // 存储每个会话的工具列表
    private final Map<String, List<McpProtocol.Tool>> sessionTools = new ConcurrentHashMap<>();

    /**
     * 创建新的对话会话
     */
    public String createSession() {
        String sessionId = UUID.randomUUID().toString();
        log.info("创建新会话: {}", sessionId);
        
        // 异步加载工具列表
        mcpClientService.listTools()
                .subscribe(tools -> {
                    sessionTools.put(sessionId, tools);
                    log.info("会话 {} 已加载 {} 个工具", sessionId, tools.size());
                });
        
        return sessionId;
    }

    /**
     * 处理用户消息
     */
    public String chat(String sessionId, String userMessage) {
        log.info("处理会话 {} 的消息: {}", sessionId, userMessage);

        try {
            // 获取可用工具
            List<McpProtocol.Tool> tools = sessionTools.get(sessionId);
            if (tools == null || tools.isEmpty()) {
                return "正在初始化工具列表，请稍后再试...";
            }

            // 构建系统提示词，包含工具信息
            String systemPrompt = buildSystemPrompt(tools);
            
            // 创建ChatClient
            ChatClient chatClient = ChatClient.builder(chatModel)
                    .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory, sessionId, 10))
                    .build();

            // 调用LLM
            String aiResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();

            log.info("AI响应: {}", aiResponse);

            // 解析AI响应，查找是否需要调用工具
            String finalResponse = processAiResponse(aiResponse);
            
            return finalResponse;

        } catch (Exception e) {
            log.error("处理对话失败", e);
            return "抱歉，处理您的请求时出现错误: " + e.getMessage();
        }
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(List<McpProtocol.Tool> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个智能助手，可以帮助用户管理和查询ZooKeeper和Dubbo服务信息。\n\n");
        sb.append("你可以使用以下工具：\n\n");
        
        for (McpProtocol.Tool tool : tools) {
            sb.append("工具名称: ").append(tool.getName()).append("\n");
            sb.append("描述: ").append(tool.getDescription()).append("\n");
            if (tool.getInputSchema() != null) {
                sb.append("参数: ").append(tool.getInputSchema()).append("\n");
            }
            sb.append("\n");
        }
        
        sb.append("当用户询问相关信息时，请按以下格式调用工具：\n");
        sb.append("TOOL_CALL: {\"tool\": \"工具名称\", \"arguments\": {参数对象}}\n\n");
        sb.append("如果不需要调用工具，请直接回答用户的问题。\n");
        sb.append("请用中文回复。");
        
        return sb.toString();
    }

    /**
     * 处理AI响应，提取并执行工具调用
     */
    private String processAiResponse(String aiResponse) {
        // 查找工具调用标记
        Pattern pattern = Pattern.compile("TOOL_CALL:\\s*\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(aiResponse);
        
        if (matcher.find()) {
            try {
                String toolCallJson = "{" + matcher.group(1) + "}";
                // 简单解析JSON（生产环境应使用更健壮的解析方法）
                String toolName = extractValue(toolCallJson, "tool");
                String argumentsJson = extractValue(toolCallJson, "arguments");
                
                if (toolName != null) {
                    log.info("检测到工具调用: {}", toolName);
                    
                    // 解析参数
                    Map<String, Object> arguments = parseArguments(argumentsJson);
                    
                    // 调用工具
                    String toolResult = mcpClientService.callTool(toolName, arguments).block();
                    
                    // 返回工具执行结果
                    return "执行工具 " + toolName + " 的结果：\n\n" + toolResult;
                }
            } catch (Exception e) {
                log.error("解析工具调用失败", e);
                return aiResponse + "\n\n(工具调用解析失败: " + e.getMessage() + ")";
            }
        }
        
        // 没有工具调用，直接返回AI响应
        return aiResponse;
    }

    /**
     * 从JSON字符串中提取值（简单实现）
     */
    private String extractValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // 尝试匹配对象值
        pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\\{([^}]+)\\}");
        matcher = pattern.matcher(json);
        if (matcher.find()) {
            return "{" + matcher.group(1) + "}";
        }
        
        return null;
    }

    /**
     * 解析参数（使用Jackson进行完整JSON解析）
     */
    private Map<String, Object> parseArguments(String argumentsJson) {
        Map<String, Object> arguments = new HashMap<>();
        
        if (argumentsJson == null || argumentsJson.isEmpty() || argumentsJson.equals("{}")) {
            return arguments;
        }
        
        try {
            // 使用Jackson进行完整的JSON解析
            ObjectMapper objectMapper = new ObjectMapper();
            TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};
            arguments = objectMapper.readValue(argumentsJson, typeRef);
            log.debug("JSON解析成功: {}", arguments);
            return arguments;
        } catch (Exception e) {
            log.warn("JSON解析失败，使用简单解析: {}", e.getMessage());
            
            // 回退到简单的键值对解析
            argumentsJson = argumentsJson.replaceAll("[{}]", "");
            String[] pairs = argumentsJson.split(",");
            
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    String key = kv[0].replaceAll("\"", "").trim();
                    String value = kv[1].replaceAll("\"", "").trim();
                    arguments.put(key, value);
                }
            }
        }
        
        return arguments;
    }

    /**
     * 获取会话历史
     */
    public List<Message> getSessionHistory(String sessionId) {
        return chatMemory.get(sessionId, 100);
    }

    /**
     * 清除会话
     */
    public void clearSession(String sessionId) {
        log.info("清除会话: {}", sessionId);
        chatMemory.clear(sessionId);
        sessionTools.remove(sessionId);
    }

    /**
     * 获取会话的工具列表
     */
    public List<McpProtocol.Tool> getSessionTools(String sessionId) {
        return sessionTools.getOrDefault(sessionId, List.of());
    }
}



