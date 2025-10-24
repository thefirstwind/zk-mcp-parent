// package com.zkinfo.controller;

// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.zkinfo.mcp.McpProtocol;
// import com.zkinfo.service.McpProtocolService;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;
// import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
// import org.springframework.boot.test.mock.mockito.MockBean;
// import org.springframework.http.MediaType;
// import org.springframework.test.web.reactive.server.WebTestClient;
// import reactor.core.publisher.Mono;

// import java.util.List;
// import java.util.Map;

// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.Mockito.when;

// /**
//  * McpController 测试类
//  * 测试 MCP 协议控制器的各种功能
//  */
// @ExtendWith(MockitoExtension.class)
// @WebFluxTest(McpController.class)
// class McpControllerTest {

//     @MockBean
//     private McpProtocolService mcpProtocolService;

//     @Mock
//     private ObjectMapper objectMapper;

//     private WebTestClient webTestClient;

//     @BeforeEach
//     void setUp() {
//         webTestClient = WebTestClient.bindToController(new McpController(mcpProtocolService, new ObjectMapper()))
//                 .build();
//     }

//     @Test
//     void testHandleJsonRpc_Success() {
//         // 准备测试数据
//         McpProtocol.JsonRpcRequest request = McpProtocol.JsonRpcRequest.builder()
//                 .id("test-1")
//                 .method("tools/list")
//                 .params(Map.of())
//                 .build();

//         McpProtocol.JsonRpcResponse expectedResponse = McpProtocol.JsonRpcResponse.builder()
//                 .id("test-1")
//                 .result(Map.of("tools", "success"))
//                 .build();

//         when(mcpProtocolService.handleRequest(any(McpProtocol.JsonRpcRequest.class)))
//                 .thenReturn(Mono.just(expectedResponse));

//         // 执行测试
//         webTestClient.post()
//                 .uri("/mcp/jsonrpc")
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .bodyValue(request)
//                 .exchange()
//                 .expectStatus().isOk()
//                 .expectHeader().contentType(MediaType.APPLICATION_JSON)
//                 .expectBody()
//                 .jsonPath("$.id").isEqualTo("test-1")
//                 .jsonPath("$.result.tools").isEqualTo("success");
//     }

//     @Test
//     void testHandleJsonRpc_Error() {
//         // 准备测试数据
//         McpProtocol.JsonRpcRequest request = McpProtocol.JsonRpcRequest.builder()
//                 .id("test-2")
//                 .method("invalid/method")
//                 .params(Map.of())
//                 .build();

//         McpProtocol.JsonRpcResponse errorResponse = McpProtocol.JsonRpcResponse.builder()
//                 .id("test-2")
//                 .error(McpProtocol.JsonRpcError.builder()
//                         .code(-32601)
//                         .message("Method not found")
//                         .build())
//                 .build();

//         when(mcpProtocolService.handleRequest(any(McpProtocol.JsonRpcRequest.class)))
//                 .thenReturn(Mono.just(errorResponse));

//         // 执行测试
//         webTestClient.post()
//                 .uri("/mcp/jsonrpc")
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .bodyValue(request)
//                 .exchange()
//                 .expectStatus().isOk()
//                 .expectHeader().contentType(MediaType.APPLICATION_JSON)
//                 .expectBody()
//                 .jsonPath("$.id").isEqualTo("test-2")
//                 .jsonPath("$.error.code").isEqualTo(-32601)
//                 .jsonPath("$.error.message").isEqualTo("Method not found");
//     }

//     @Test
//     void testCreateStream_Success() {
//         // 准备测试数据
//         McpProtocol.JsonRpcRequest request = McpProtocol.JsonRpcRequest.builder()
//                 .id("stream-1")
//                 .method("tools/call")
//                 .params(Map.of("name", "test-tool"))
//                 .build();

//         McpProtocol.CallToolResult callResult = McpProtocol.CallToolResult.builder()
//                 .streamId("stream-123")
//                 .content(List.of(McpProtocol.McpContent.builder()
//                     .type("text")
//                     .text("Streaming data")
//                     .build()))
//                 .build();

//         McpProtocol.JsonRpcResponse response = McpProtocol.JsonRpcResponse.builder()
//                 .id("stream-1")
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
//     void testCreateStream_NonStreaming() {
//         // 准备测试数据 - 非流式响应
//         McpProtocol.JsonRpcRequest request = McpProtocol.JsonRpcRequest.builder()
//                 .id("normal-1")
//                 .method("tools/list")
//                 .params(Map.of())
//                 .build();

//         McpProtocol.JsonRpcResponse response = McpProtocol.JsonRpcResponse.builder()
//                 .id("normal-1")
//                 .result(Map.of("tools", "list"))
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
//                 .jsonPath("$.id").isEqualTo("normal-1")
//                 .jsonPath("$.result.tools").isEqualTo("list");
//     }

//     @Test
//     void testHealth() {
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
//     void testGetServerInfo() {
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
//                 .jsonPath("$.metadata").exists();
//     }

//     @Test
//     void testGetSessionCount() {
//         // 执行测试
//         webTestClient.get()
//                 .uri("/mcp/sessions/count")
//                 .exchange()
//                 .expectStatus().isOk()
//                 .expectHeader().contentType(MediaType.APPLICATION_JSON)
//                 .expectBody()
//                 .jsonPath("$.activeWebSocketSessions").exists()
//                 .jsonPath("$.timestamp").exists();
//     }
// }
