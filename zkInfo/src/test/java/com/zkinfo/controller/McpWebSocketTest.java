// package com.zkinfo.controller;

// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.zkinfo.mcp.McpProtocol;
// import com.zkinfo.service.McpProtocolService;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.boot.test.mock.mockito.MockBean;
// import org.springframework.test.context.ActiveProfiles;
// import org.springframework.web.socket.*;
// import org.springframework.web.socket.client.standard.StandardWebSocketClient;

// import java.net.URI;
// import java.util.Map;
// import java.util.concurrent.CountDownLatch;
// import java.util.concurrent.TimeUnit;

// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.Mockito.when;

// /**
//  * McpController WebSocket 测试
//  * 测试 WebSocket 连接和消息处理
//  */
// @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// @ActiveProfiles("test")
// class McpWebSocketTest {

//     @MockBean
//     private McpProtocolService mcpProtocolService;

//     @Autowired
//     private ObjectMapper objectMapper;

//     private String webSocketUrl;
//     private CountDownLatch latch;
//     private String receivedMessage;

//     @BeforeEach
//     void setUp() {
//         webSocketUrl = "ws://localhost:8080/mcp/ws";
//         latch = new CountDownLatch(1);
//         receivedMessage = null;
//     }

//     @Test
//     void testWebSocketConnection() throws Exception {
//         // 准备测试数据
//         McpProtocol.JsonRpcRequest request = McpProtocol.JsonRpcRequest.builder()
//                 .id("ws-test-1")
//                 .method("tools/list")
//                 .params(Map.of())
//                 .build();

//         McpProtocol.JsonRpcResponse response = McpProtocol.JsonRpcResponse.builder()
//                 .id("ws-test-1")
//                 .result(Map.of("tools", "success"))
//                 .build();

//         when(mcpProtocolService.handleRequest(any(McpProtocol.JsonRpcRequest.class)))
//                 .thenReturn(reactor.core.publisher.Mono.just(response));

//         // 创建 WebSocket 客户端
//         StandardWebSocketClient client = new StandardWebSocketClient();
        
//         WebSocketSession session = client.doHandshake(new WebSocketHandler() {
//             @Override
//             public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//                 System.out.println("WebSocket 连接建立");
                
//                 // 发送测试消息
//                 String requestJson = objectMapper.writeValueAsString(request);
//                 session.sendMessage(new TextMessage(requestJson));
//             }

//             @Override
//             public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
//                 System.out.println("收到消息: " + message.getPayload());
//                 receivedMessage = message.getPayload().toString();
//                 latch.countDown();
//             }

//             @Override
//             public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
//                 System.out.println("传输错误: " + exception.getMessage());
//                 latch.countDown();
//             }

//             @Override
//             public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
//                 System.out.println("连接关闭: " + closeStatus);
//             }

//             @Override
//             public boolean supportsPartialMessages() {
//                 return false;
//             }
//         }, new org.springframework.web.socket.WebSocketHttpHeaders(), URI.create(webSocketUrl)).get();

//         // 等待响应
//         assertTrue(latch.await(5, TimeUnit.SECONDS), "WebSocket 响应超时");
        
//         // 验证响应
//         assertNotNull(receivedMessage, "未收到 WebSocket 响应");
//         assertTrue(receivedMessage.contains("ws-test-1"), "响应 ID 不匹配");
//         assertTrue(receivedMessage.contains("success"), "响应内容不匹配");

//         // 关闭连接
//         session.close();
//     }

//     @Test
//     void testWebSocketErrorHandling() throws Exception {
//         // 准备错误测试数据
//         McpProtocol.JsonRpcRequest request = McpProtocol.JsonRpcRequest.builder()
//                 .id("ws-error-1")
//                 .method("invalid/method")
//                 .params(Map.of())
//                 .build();

//         McpProtocol.JsonRpcError error = McpProtocol.JsonRpcError.builder()
//                 .code(-32601)
//                 .message("Method not found")
//                 .build();

//         McpProtocol.JsonRpcResponse response = McpProtocol.JsonRpcResponse.builder()
//                 .id("ws-error-1")
//                 .error(error)
//                 .build();

//         when(mcpProtocolService.handleRequest(any(McpProtocol.JsonRpcRequest.class)))
//                 .thenReturn(reactor.core.publisher.Mono.just(response));

//         // 创建 WebSocket 客户端
//         StandardWebSocketClient client = new StandardWebSocketClient();
        
//         WebSocketSession session = client.doHandshake(new WebSocketHandler() {
//             @Override
//             public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//                 System.out.println("WebSocket 连接建立");
                
//                 // 发送错误测试消息
//                 String requestJson = objectMapper.writeValueAsString(request);
//                 session.sendMessage(new TextMessage(requestJson));
//             }

//             @Override
//             public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
//                 System.out.println("收到错误消息: " + message.getPayload());
//                 receivedMessage = message.getPayload().toString();
//                 latch.countDown();
//             }

//             @Override
//             public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
//                 System.out.println("传输错误: " + exception.getMessage());
//                 latch.countDown();
//             }

//             @Override
//             public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
//                 System.out.println("连接关闭: " + closeStatus);
//             }

//             @Override
//             public boolean supportsPartialMessages() {
//                 return false;
//             }
//         }, new org.springframework.web.socket.WebSocketHttpHeaders(), URI.create(webSocketUrl)).get();

//         // 等待响应
//         assertTrue(latch.await(5, TimeUnit.SECONDS), "WebSocket 错误响应超时");
        
//         // 验证错误响应
//         assertNotNull(receivedMessage, "未收到 WebSocket 错误响应");
//         assertTrue(receivedMessage.contains("ws-error-1"), "错误响应 ID 不匹配");
//         assertTrue(receivedMessage.contains("-32601"), "错误代码不匹配");
//         assertTrue(receivedMessage.contains("Method not found"), "错误消息不匹配");

//         // 关闭连接
//         session.close();
//     }

//     @Test
//     void testWebSocketWelcomeMessage() throws Exception {
//         // 创建 WebSocket 客户端
//         StandardWebSocketClient client = new StandardWebSocketClient();
        
//         WebSocketSession session = client.doHandshake(new WebSocketHandler() {
//             @Override
//             public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//                 System.out.println("WebSocket 连接建立，等待欢迎消息");
//             }

//             @Override
//             public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
//                 System.out.println("收到欢迎消息: " + message.getPayload());
//                 receivedMessage = message.getPayload().toString();
//                 latch.countDown();
//             }

//             @Override
//             public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
//                 System.out.println("传输错误: " + exception.getMessage());
//                 latch.countDown();
//             }

//             @Override
//             public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
//                 System.out.println("连接关闭: " + closeStatus);
//             }

//             @Override
//             public boolean supportsPartialMessages() {
//                 return false;
//             }
//         }, new org.springframework.web.socket.WebSocketHttpHeaders(), URI.create(webSocketUrl)).get();

//         // 等待欢迎消息
//         assertTrue(latch.await(3, TimeUnit.SECONDS), "WebSocket 欢迎消息超时");
        
//         // 验证欢迎消息
//         assertNotNull(receivedMessage, "未收到 WebSocket 欢迎消息");
//         assertTrue(receivedMessage.contains("欢迎使用zkInfo MCP服务"), "欢迎消息内容不匹配");
//         assertTrue(receivedMessage.contains("sessionId"), "欢迎消息缺少 sessionId");
//         assertTrue(receivedMessage.contains("protocolVersion"), "欢迎消息缺少 protocolVersion");

//         // 关闭连接
//         session.close();
//     }
// }
