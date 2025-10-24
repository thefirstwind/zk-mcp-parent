package com.zkinfo.ai.controller;

import com.zkinfo.ai.model.McpProtocol;
import com.zkinfo.ai.service.AiConversationService;
import com.zkinfo.ai.service.McpClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI对话控制器
 * 提供与AI助手交互的REST接口
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "AI Chat", description = "AI对话接口")
public class AiChatController {

    private final AiConversationService aiConversationService;
    private final McpClientService mcpClientService;

    /**
     * 创建新的对话会话
     */
    @PostMapping("/session")
    @Operation(summary = "创建新会话", description = "创建一个新的AI对话会话")
    public ResponseEntity<SessionResponse> createSession() {
        String sessionId = aiConversationService.createSession();
        
        SessionResponse response = new SessionResponse();
        response.setSessionId(sessionId);
        response.setMessage("会话创建成功");
        response.setTimestamp(System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 发送消息到AI助手
     */
    @PostMapping("/session/{sessionId}/message")
    @Operation(summary = "发送消息", description = "向AI助手发送消息")
    public ResponseEntity<ChatResponse> sendMessage(
            @PathVariable String sessionId,
            @RequestBody ChatRequest request) {
        
        log.info("收到会话 {} 的消息: {}", sessionId, request.getMessage());
        
        String aiResponse = aiConversationService.chat(sessionId, request.getMessage());
        
        ChatResponse response = new ChatResponse();
        response.setSessionId(sessionId);
        response.setUserMessage(request.getMessage());
        response.setAiResponse(aiResponse);
        response.setTimestamp(System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取会话历史
     */
    @GetMapping("/session/{sessionId}/history")
    @Operation(summary = "获取会话历史", description = "获取指定会话的历史消息")
    public ResponseEntity<HistoryResponse> getHistory(@PathVariable String sessionId) {
        List<Message> history = aiConversationService.getSessionHistory(sessionId);
        
        HistoryResponse response = new HistoryResponse();
        response.setSessionId(sessionId);
        response.setMessageCount(history.size());
        response.setMessages(history);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取会话可用的工具
     */
    @GetMapping("/session/{sessionId}/tools")
    @Operation(summary = "获取可用工具", description = "获取当前会话可用的MCP工具")
    public ResponseEntity<ToolsResponse> getTools(@PathVariable String sessionId) {
        List<McpProtocol.Tool> tools = aiConversationService.getSessionTools(sessionId);
        
        ToolsResponse response = new ToolsResponse();
        response.setSessionId(sessionId);
        response.setTools(tools);
        response.setToolCount(tools.size());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 清除会话
     */
    @DeleteMapping("/session/{sessionId}")
    @Operation(summary = "清除会话", description = "清除指定的会话及其历史")
    public ResponseEntity<Map<String, String>> clearSession(@PathVariable String sessionId) {
        aiConversationService.clearSession(sessionId);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "会话已清除");
        response.put("sessionId", sessionId);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取MCP Server信息
     */
    @GetMapping("/mcp/info")
    @Operation(summary = "获取MCP Server信息", description = "获取连接的MCP Server的详细信息")
    public ResponseEntity<Map<String, Object>> getMcpInfo() {
        Map<String, Object> info = mcpClientService.getServerInfo().block();
        return ResponseEntity.ok(info);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查AI客户端和MCP Server的健康状态")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "MCP AI Client");
        health.put("timestamp", System.currentTimeMillis());
        
        // 检查MCP Server连接
        try {
            Map<String, Object> mcpHealth = mcpClientService.healthCheck().block();
            health.put("mcpServer", mcpHealth);
        } catch (Exception e) {
            health.put("mcpServer", Map.of("status", "DOWN", "error", e.getMessage()));
        }
        
        return ResponseEntity.ok(health);
    }

    // DTO类

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatRequest {
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionResponse {
        private String sessionId;
        private String message;
        private long timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatResponse {
        private String sessionId;
        private String userMessage;
        private String aiResponse;
        private long timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryResponse {
        private String sessionId;
        private int messageCount;
        private List<Message> messages;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolsResponse {
        private String sessionId;
        private int toolCount;
        private List<McpProtocol.Tool> tools;
    }
}



