// package com.zkinfo.controller;

// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.zkinfo.mcp.McpProtocol;
// import com.zkinfo.service.McpProtocolService;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;
// import org.springframework.http.MediaType;
// import org.springframework.test.web.servlet.MockMvc;
// import org.springframework.test.web.servlet.MvcResult;
// import org.springframework.test.web.servlet.setup.MockMvcBuilders;
// import reactor.core.publisher.Flux;
// import reactor.core.publisher.Mono;

// import java.time.Duration;
// import java.util.Map;
// import java.util.concurrent.CountDownLatch;
// import java.util.concurrent.TimeUnit;

// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.anyString;
// import static org.mockito.Mockito.when;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// /**
//  * SSE 流式调用完整测试用例
//  * 测试 Server-Sent Events 流式传输功能
//  */
// @ExtendWith(MockitoExtension.class)
// class SseStreamingTest {

//     @Mock
//     private McpProtocolService mcpProtocolService;

//     private MockMvc mockMvc;
//     private ObjectMapper objectMapper;

//     @BeforeEach
//     void setUp() {
//         McpController mcpController = new McpController(
//             mcpProtocolService, null, null, null, new ObjectMapper()
//         );
//         mockMvc = MockMvcBuilders.standaloneSetup(mcpController).build();
//         objectMapper = new ObjectMapper();
//     }

//     @Test
//     void testCreateStreamEndpoint() throws Exception {
//         // 准备测试数据
//         McpProtocol.JsonRpcRequest request = McpProtocol.JsonRpcRequest.builder()
//                 .jsonrpc("2.0")
//                 .id("test-stream-1")
//                 .method("tools/call")
//                 .params(Map.of(
//                     "name", "com.zkinfo.demo.service.UserService.getUserById",
//                     "arguments", Map.of("args", new Object[]{1}),
//                     "stream", true
//                 ))
//                 .build();

//         // Mock 流式调用响应
//         McpProtocol.CallToolResult mockResult = McpProtocol.CallToolResult.builder()
//                 .streamId("stream_123")
//                 .hasMore(true)
//                 .content(java.util.List.of(McpProtocol.McpContent.builder()
//                     .type("text")
//                     .text("流式调用已启动，streamId: stream_123")
//                     .build()))
//                 .build();

//         McpProtocol.JsonRpcResponse mockResponse = McpProtocol.JsonRpcResponse.builder()
//                 .jsonrpc("2.0")
//                 .id("test-stream-1")
//                 .result(mockResult)
//                 .build();

//         when(mcpProtocolService.handleRequest(any(McpProtocol.JsonRpcRequest.class)))
//                 .thenReturn(Mono.just(mockResponse));

//         // 执行测试
//         MvcResult result = mockMvc.perform(post("/mcp/stream")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(request)))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                 .andExpect(jsonPath("$.streamId").value("stream_123"))
//                 .andExpect(jsonPath("$.sseUrl").value("/mcp/stream/stream_123"))
//                 .andExpect(jsonPath("$.status").value("streaming"))
//                 .andReturn();

//         String responseContent = result.getResponse().getContentAsString();
//         assertNotNull(responseContent);
//         assertTrue(responseContent.contains("stream_123"));
//     }

//     @Test
//     void testSseStreamDataEndpoint() throws Exception {
//         String streamId = "test_stream_456";
        
//         // Mock 流式数据
//         McpProtocol.StreamChunk chunk1 = McpProtocol.StreamChunk.builder()
//                 .id(streamId)
//                 .type("data")
//                 .data(Map.of("progress", 25, "message", "开始处理..."))
//                 .timestamp(String.valueOf(System.currentTimeMillis()))
//                 .isLast(false)
//                 .build();

//         McpProtocol.StreamChunk chunk2 = McpProtocol.StreamChunk.builder()
//                 .id(streamId)
//                 .type("data")
//                 .data(Map.of("progress", 50, "message", "处理中..."))
//                 .timestamp(String.valueOf(System.currentTimeMillis()))
//                 .isLast(false)
//                 .build();

//         McpProtocol.StreamChunk chunk3 = McpProtocol.StreamChunk.builder()
//                 .id(streamId)
//                 .type("result")
//                 .data(Map.of("userId", 1, "name", "张三", "email", "zhangsan@example.com"))
//                 .timestamp(String.valueOf(System.currentTimeMillis()))
//                 .isLast(true)
//                 .build();

//         Flux<McpProtocol.StreamChunk> mockStream = Flux.just(chunk1, chunk2, chunk3)
//                 .delayElements(Duration.ofMillis(100));

//         when(mcpProtocolService.getStreamData(streamId)).thenReturn(mockStream);

//         // 执行测试 - 验证 SSE 端点响应
//         mockMvc.perform(get("/mcp/stream/" + streamId))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE))
//                 .andExpect(header().string("Cache-Control", "no-cache"))
//                 .andExpect(header().string("Connection", "keep-alive"));
//     }

//     @Test
//     void testSseStreamWithError() throws Exception {
//         String streamId = "error_stream_789";
        
//         // Mock 错误流
//         Flux<McpProtocol.StreamChunk> errorStream = Flux.error(
//             new RuntimeException("流式会话不存在: " + streamId)
//         );

//         when(mcpProtocolService.getStreamData(streamId)).thenReturn(errorStream);

//         // 执行测试 - 验证错误处理
//         mockMvc.perform(get("/mcp/stream/" + streamId))
//                 .andExpect(status().isOk()) // SSE 连接建立成功
//                 .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
//     }

//     @Test
//     void testSseStreamWithLongRunningTask() throws Exception {
//         String streamId = "long_running_stream";
        
//         // Mock 长时间运行的流式任务
//         Flux<McpProtocol.StreamChunk> longStream = Flux.range(1, 10)
//                 .map(i -> McpProtocol.StreamChunk.builder()
//                     .id(streamId)
//                     .type("progress")
//                     .data(Map.of("step", i, "total", 10, "message", "步骤 " + i + "/10"))
//                     .timestamp(String.valueOf(System.currentTimeMillis()))
//                     .isLast(i == 10)
//                     .build())
//                 .delayElements(Duration.ofMillis(50));

//         when(mcpProtocolService.getStreamData(streamId)).thenReturn(longStream);

//         // 执行测试
//         mockMvc.perform(get("/mcp/stream/" + streamId))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
//     }

//     @Test
//     void testMultipleStreamSessions() throws Exception {
//         // 测试多个并发流式会话
//         for (int i = 1; i <= 3; i++) {
//             String streamId = "concurrent_stream_" + i;
            
//             McpProtocol.StreamChunk chunk = McpProtocol.StreamChunk.builder()
//                     .id(streamId)
//                     .type("data")
//                     .data(Map.of("sessionId", i, "message", "并发会话 " + i))
//                     .timestamp(String.valueOf(System.currentTimeMillis()))
//                     .isLast(true)
//                     .build();

//             when(mcpProtocolService.getStreamData(streamId))
//                     .thenReturn(Flux.just(chunk));

//             // 验证每个流式会话都能正常工作
//             mockMvc.perform(get("/mcp/stream/" + streamId))
//                     .andExpect(status().isOk())
//                     .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
//         }
//     }

//     @Test
//     void testSseStreamWithDifferentDataTypes() throws Exception {
//         String streamId = "mixed_data_stream";
        
//         // Mock 不同类型的数据
//         McpProtocol.StreamChunk textChunk = McpProtocol.StreamChunk.builder()
//                 .id(streamId)
//                 .type("text")
//                 .data("这是文本数据")
//                 .timestamp(String.valueOf(System.currentTimeMillis()))
//                 .isLast(false)
//                 .build();

//         McpProtocol.StreamChunk jsonChunk = McpProtocol.StreamChunk.builder()
//                 .id(streamId)
//                 .type("json")
//                 .data(Map.of("key", "value", "number", 42, "boolean", true))
//                 .timestamp(String.valueOf(System.currentTimeMillis()))
//                 .isLast(false)
//                 .build();

//         McpProtocol.StreamChunk binaryChunk = McpProtocol.StreamChunk.builder()
//                 .id(streamId)
//                 .type("binary")
//                 .data(Map.of("encoding", "base64", "data", "SGVsbG8gV29ybGQ="))
//                 .timestamp(String.valueOf(System.currentTimeMillis()))
//                 .isLast(true)
//                 .build();

//         Flux<McpProtocol.StreamChunk> mixedStream = Flux.just(textChunk, jsonChunk, binaryChunk)
//                 .delayElements(Duration.ofMillis(100));

//         when(mcpProtocolService.getStreamData(streamId)).thenReturn(mixedStream);

//         // 执行测试
//         mockMvc.perform(get("/mcp/stream/" + streamId))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
//     }

//     @Test
//     void testSseStreamTimeout() throws Exception {
//         String streamId = "timeout_stream";
        
//         // Mock 超时流
//         Flux<McpProtocol.StreamChunk> timeoutStream = Flux.never(); // 永不发送数据

//         when(mcpProtocolService.getStreamData(streamId)).thenReturn(timeoutStream);

//         // 执行测试 - 验证连接建立但无数据
//         mockMvc.perform(get("/mcp/stream/" + streamId))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
//     }

//     @Test
//     void testSseStreamWithUnicodeData() throws Exception {
//         String streamId = "unicode_stream";
        
//         // Mock Unicode 数据
//         McpProtocol.StreamChunk unicodeChunk = McpProtocol.StreamChunk.builder()
//                 .id(streamId)
//                 .type("text")
//                 .data("测试Unicode字符: 🚀🔥💡⭐️🎉 中文测试 العربية 日本語 한국어")
//                 .timestamp(String.valueOf(System.currentTimeMillis()))
//                 .isLast(true)
//                 .build();

//         when(mcpProtocolService.getStreamData(streamId))
//                 .thenReturn(Flux.just(unicodeChunk));

//         // 执行测试
//         mockMvc.perform(get("/mcp/stream/" + streamId))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE))
//                 .andExpect(content().encoding("UTF-8"));
//     }

//     @Test
//     void testSseStreamWithLargeData() throws Exception {
//         String streamId = "large_data_stream";
        
//         // Mock 大数据块
//         StringBuilder largeData = new StringBuilder();
//         for (int i = 0; i < 1000; i++) {
//             largeData.append("这是一个很长的数据块，用于测试SSE处理大数据的能力。");
//         }

//         McpProtocol.StreamChunk largeChunk = McpProtocol.StreamChunk.builder()
//                 .id(streamId)
//                 .type("large_data")
//                 .data(largeData.toString())
//                 .timestamp(String.valueOf(System.currentTimeMillis()))
//                 .isLast(true)
//                 .build();

//         when(mcpProtocolService.getStreamData(streamId))
//                 .thenReturn(Flux.just(largeChunk));

//         // 执行测试
//         mockMvc.perform(get("/mcp/stream/" + streamId))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
//     }

//     @Test
//     void testInvalidStreamId() throws Exception {
//         String invalidStreamId = "non_existent_stream";
        
//         // Mock 不存在的流ID
//         when(mcpProtocolService.getStreamData(invalidStreamId))
//                 .thenReturn(Flux.error(new RuntimeException("流式会话不存在: " + invalidStreamId)));

//         // 执行测试
//         mockMvc.perform(get("/mcp/stream/" + invalidStreamId))
//                 .andExpect(status().isOk()) // SSE 连接建立，但会在流中发送错误
//                 .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
//     }
// }
