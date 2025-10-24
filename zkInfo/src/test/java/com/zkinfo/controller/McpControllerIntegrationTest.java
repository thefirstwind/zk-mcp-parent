// package com.zkinfo.controller;

// import com.zkinfo.mcp.McpProtocol;
// import com.zkinfo.service.McpProtocolService;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.boot.test.mock.mockito.MockBean;
// import org.springframework.http.MediaType;
// import org.springframework.test.context.ActiveProfiles;
// import org.springframework.test.web.reactive.server.WebTestClient;
// import reactor.core.publisher.Flux;
// import reactor.core.publisher.Mono;

// import java.time.Duration;
// import java.util.List;
// import java.util.Map;

// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.Mockito.when;

// /**
//  * McpController 集成测试
//  * 测试完整的 MCP 协议流程
//  */
// @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// @AutoConfigureWebTestClient
// @ActiveProfiles("test")
// class McpControllerIntegrationTest {

//     @Autowired
//     private WebTestClient webTestClient;

//     @MockBean
//     private McpProtocolService mcpProtocolService;


//     @BeforeEach
//     void setUp() {
//         // 测试设置
//     }

//     @Test
//     void testInitializeProtocol() {
//         // 准备初始化请求
//         McpProtocol.InitializeParams initParams = McpProtocol.InitializeParams.builder()
//                 .protocolVersion("2024-11-05")
//                 .capabilities(Map.of("tools", Map.of()))
//                 .clientInfo(McpProtocol.ClientInfo.builder()
//                         .name("test-client")
//                         .version("1.0.0")
//                         .build())
//                 .build();

//         McpProtocol.JsonRpcRequest request = McpProtocol.JsonRpcRequest.builder()
//                 .id("init-1")
//                 .method("initialize")
//                 .params(initParams)
//                 .build();

//         McpProtocol.InitializeResult initResult = McpProtocol.InitializeResult.builder()
//                 .protocolVersion("2024-11-05")
//                 .capabilities(Map.of("tools", Map.of()))
//                 .serverInfo(McpProtocol.ServerInfo.builder()
//                         .name("zkInfo-MCP-Server")
//                         .version("1.0.0")
//                         .build())
//                 .build();

//         McpProtocol.JsonRpcResponse response = McpProtocol.JsonRpcResponse.builder()
//                 .id("init-1")
//                 .result(initResult)
//                 .build();

//         when(mcpProtocolService.handleRequest(any(McpProtocol.JsonRpcRequest.class)))
//                 .thenReturn(Mono.just(response));

//         // 执行测试
//         webTestClient.post()
//                 .uri("/mcp/jsonrpc")
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .bodyValue(request)
//                 .exchange()
//                 .expectStatus().isOk()
//                 .expectHeader().contentType(MediaType.APPLICATION_JSON)
//                 .expectBody()
//                 .jsonPath("$.id").isEqualTo("init-1")
//                 .jsonPath("$.result.protocolVersion").isEqualTo("2024-11-05")
//                 .jsonPath("$.result.serverInfo.name").isEqualTo("zkInfo-MCP-Server");
//     }

//     @Test
//     void testListTools() {
//         // 准备工具列表请求
//         McpProtocol.ListToolsParams listParams = McpProtocol.ListToolsParams.builder()
//                 .build();

//         McpProtocol.JsonRpcRequest request = McpProtocol.JsonRpcRequest.builder()
//                 .id("tools-1")
//                 .method("tools/list")
//                 .params(listParams)
//                 .build();

//         McpProtocol.McpTool tool1 = McpProtocol.McpTool.builder()
//                 .name("com.example.service.UserService.getUserById")
//                 .description("获取用户信息")
//                 .inputSchema(Map.of(
//                     "type", "object",
//                     "properties", Map.of(
//                         "args", Map.of("type", "array", "items", Map.of("type", "integer"))
//                     ),
//                     "required", List.of("args")
//                 ))
//                 .build();

//         McpProtocol.ListToolsResult listResult = McpProtocol.ListToolsResult.builder()
//                 .tools(List.of(tool1))
//                 .build();

//         McpProtocol.JsonRpcResponse response = McpProtocol.JsonRpcResponse.builder()
//                 .id("tools-1")
//                 .result(listResult)
//                 .build();

//         when(mcpProtocolService.handleRequest(any(McpProtocol.JsonRpcRequest.class)))
//                 .thenReturn(Mono.just(response));

//         // 执行测试
//         webTestClient.post()
//                 .uri("/mcp/jsonrpc")
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .bodyValue(request)
//                 .exchange()
//                 .expectStatus().isOk()
//                 .expectHeader().contentType(MediaType.APPLICATION_JSON)
//                 .expectBody()
//                 .jsonPath("$.id").isEqualTo("tools-1")
//                 .jsonPath("$.result.tools").isArray()
//                 .jsonPath("$.result.tools[0].name").isEqualTo("com.example.service.UserService.getUserById")
//                 .jsonPath("$.result.tools[0].description").isEqualTo("获取用户信息");
//     }

//     @Test
//     void testCallTool() {
//         // 准备工具调用请求
//         McpProtocol.CallToolParams callParams = McpProtocol.CallToolParams.builder()
//                 .name("com.example.service.UserService.getUserById")
//                 .arguments(Map.of("args", List.of(1)))
//                 .build();

//         McpProtocol.JsonRpcRequest request = McpProtocol.JsonRpcRequest.builder()
//                 .id("call-1")
//                 .method("tools/call")
//                 .params(callParams)
//                 .build();

//         McpProtocol.CallToolResult callResult = McpProtocol.CallToolResult.builder()
//                 .content(List.of(
//                     McpProtocol.McpContent.builder()
//                             .type("text")
//                             .text("{\"id\":1,\"name\":\"张三\",\"email\":\"zhangsan@example.com\"}")
//                             .build()
//                 ))
//                 .isError(false)
//                 .build();

//         McpProtocol.JsonRpcResponse response = McpProtocol.JsonRpcResponse.builder()
//                 .id("call-1")
//                 .result(callResult)
//                 .build();

//         when(mcpProtocolService.handleRequest(any(McpProtocol.JsonRpcRequest.class)))
//                 .thenReturn(Mono.just(response));

//         // 执行测试
//         webTestClient.post()
//                 .uri("/mcp/jsonrpc")
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .bodyValue(request)
//                 .exchange()
//                 .expectStatus().isOk()
//                 .expectHeader().contentType(MediaType.APPLICATION_JSON)
//                 .expectBody()
//                 .jsonPath("$.id").isEqualTo("call-1")
//                 .jsonPath("$.result.content").isArray()
//                 .jsonPath("$.result.content[0].type").isEqualTo("text")
//                 .jsonPath("$.result.isError").isEqualTo(false);
//     }

//     @Test
//     void testStreamingCall() {
//         // 准备流式调用请求
//         McpProtocol.CallToolParams callParams = McpProtocol.CallToolParams.builder()
//                 .name("com.example.service.DataService.streamData")
//                 .arguments(Map.of("args", List.of("large-dataset")))
//                 .build();

//         McpProtocol.JsonRpcRequest request = McpProtocol.JsonRpcRequest.builder()
//                 .id("stream-call-1")
//                 .method("tools/call")
//                 .params(callParams)
//                 .build();

//         McpProtocol.CallToolResult callResult = McpProtocol.CallToolResult.builder()
//                 .streamId("stream-123")
//                 .content(List.of(
//                     McpProtocol.McpContent.builder()
//                             .type("text")
//                             .text("开始流式传输数据...")
//                             .build()
//                 ))
//                 .isError(false)
//                 .build();

//         McpProtocol.JsonRpcResponse response = McpProtocol.JsonRpcResponse.builder()
//                 .id("stream-call-1")
//                 .result(callResult)
//                 .build();

//         when(mcpProtocolService.handleRequest(any(McpProtocol.JsonRpcRequest.class)))
//                 .thenReturn(Mono.just(response));

//         // 执行测试
//         webTestClient.post()
//                 .uri("/mcp/stream")
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .bodyValue(request)
//                 .exchange()
//                 .expectStatus().isOk()
//                 .expectHeader().contentType(MediaType.APPLICATION_JSON)
//                 .expectBody()
//                 .jsonPath("$.streamId").isEqualTo("stream-123")
//                 .jsonPath("$.sseUrl").isEqualTo("/mcp/stream/stream-123")
//                 .jsonPath("$.status").isEqualTo("streaming");
//     }

//     @Test
//     void testStreamData() {
//         // 准备流式数据
//         McpProtocol.StreamChunk chunk1 = McpProtocol.StreamChunk.builder()
//                 .id("chunk-1")
//                 .type("data")
//                 .data("第一批数据")
//                 .build();

//         McpProtocol.StreamChunk chunk2 = McpProtocol.StreamChunk.builder()
//                 .id("chunk-2")
//                 .type("data")
//                 .data("第二批数据")
//                 .build();

//         when(mcpProtocolService.getStreamData("stream-123"))
//                 .thenReturn(Flux.just(chunk1, chunk2)
//                         .delayElements(Duration.ofMillis(100)));

//         // 执行测试
//         webTestClient.get()
//                 .uri("/mcp/stream/stream-123")
//                 .accept(MediaType.TEXT_EVENT_STREAM)
//                 .exchange()
//                 .expectStatus().isOk()
//                 .expectHeader().contentType(MediaType.TEXT_EVENT_STREAM_VALUE)
//                 .expectBody()
//                 .consumeWith(result -> {
//                     String body = new String(result.getResponseBody());
//                     // 验证 SSE 格式
//                     assert body.contains("id: chunk-1");
//                     assert body.contains("event: data");
//                     assert body.contains("data:");
//                 });
//     }

//     @Test
//     void testErrorHandling() {
//         // 准备错误请求
//         McpProtocol.JsonRpcRequest request = McpProtocol.JsonRpcRequest.builder()
//                 .id("error-1")
//                 .method("invalid/method")
//                 .params(Map.of())
//                 .build();

//         McpProtocol.JsonRpcError error = McpProtocol.JsonRpcError.builder()
//                 .code(-32601)
//                 .message("Method not found")
//                 .data(Map.of("method", "invalid/method"))
//                 .build();

//         McpProtocol.JsonRpcResponse response = McpProtocol.JsonRpcResponse.builder()
//                 .id("error-1")
//                 .error(error)
//                 .build();

//         when(mcpProtocolService.handleRequest(any(McpProtocol.JsonRpcRequest.class)))
//                 .thenReturn(Mono.just(response));

//         // 执行测试
//         webTestClient.post()
//                 .uri("/mcp/jsonrpc")
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .bodyValue(request)
//                 .exchange()
//                 .expectStatus().isOk()
//                 .expectHeader().contentType(MediaType.APPLICATION_JSON)
//                 .expectBody()
//                 .jsonPath("$.id").isEqualTo("error-1")
//                 .jsonPath("$.error.code").isEqualTo(-32601)
//                 .jsonPath("$.error.message").isEqualTo("Method not found")
//                 .jsonPath("$.error.data.method").isEqualTo("invalid/method");
//     }

//     @Test
//     void testHealthEndpoint() {
//         // 执行测试
//         webTestClient.get()
//                 .uri("/mcp/health")
//                 .exchange()
//                 .expectStatus().isOk()
//                 .expectHeader().contentType(MediaType.APPLICATION_JSON)
//                 .expectBody()
//                 .jsonPath("$.status").isEqualTo("UP")
//                 .jsonPath("$.protocol").isEqualTo("MCP 2024-11-05")
//                 .jsonPath("$.capabilities").isArray()
//                 .jsonPath("$.activeSessions").exists()
//                 .jsonPath("$.timestamp").exists();
//     }

//     @Test
//     void testServerInfoEndpoint() {
//         // 执行测试
//         webTestClient.get()
//                 .uri("/mcp/info")
//                 .exchange()
//                 .expectStatus().isOk()
//                 .expectHeader().contentType(MediaType.APPLICATION_JSON)
//                 .expectBody()
//                 .jsonPath("$.name").isEqualTo("zkInfo-MCP-Server")
//                 .jsonPath("$.version").isEqualTo("1.0.0")
//                 .jsonPath("$.description").exists()
//                 .jsonPath("$.capabilities").isArray()
//                 .jsonPath("$.metadata").exists()
//                 .jsonPath("$.metadata.dubbo.version").isEqualTo("3.2.0")
//                 .jsonPath("$.metadata.zookeeper.enabled").isEqualTo(true);
//     }
// }
