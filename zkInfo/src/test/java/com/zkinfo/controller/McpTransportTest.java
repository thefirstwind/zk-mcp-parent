// package com.zkinfo.controller;

// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.zkinfo.mcp.McpProtocol;
// import com.zkinfo.model.ProviderInfo;
// import com.zkinfo.service.ProviderService;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;
// import org.springframework.http.MediaType;
// import org.springframework.test.web.servlet.MockMvc;
// import org.springframework.test.web.servlet.setup.MockMvcBuilders;
// import reactor.core.publisher.Mono;

// import java.util.Arrays;
// import java.util.List;

// import static org.mockito.Mockito.when;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// /**
//  * MCP 传输层测试
//  * 测试 HTTP REST API、WebSocket、SSE 等传输方式
//  */
// @ExtendWith(MockitoExtension.class)
// class McpTransportTest {

//     @Mock
//     private ProviderService providerService;

//     private MockMvc mockMvc;
//     private ObjectMapper objectMapper;

//     @BeforeEach
//     void setUp() {
//         McpController mcpController = new McpController(providerService);
//         mockMvc = MockMvcBuilders.standaloneSetup(mcpController).build();
//         objectMapper = new ObjectMapper();
//     }

//     @Test
//     void testHttpJsonRpcEndpoint() throws Exception {
//         // 准备测试数据
//         ProviderInfo provider = new ProviderInfo();
//         provider.setInterfaceName("com.example.UserService");
//         provider.setApplication("user-service");
//         provider.setAddress("192.168.1.100:20880");
//         provider.setOnline(true);

//         when(providerService.getAllProviders()).thenReturn(Arrays.asList(provider));

//         // 测试初始化请求
//         McpProtocol.McpRequest request = McpProtocol.McpRequest.builder()
//                 .jsonrpc("2.0")
//                 .id("1")
//                 .method("initialize")
//                 .params(McpProtocol.InitializeParams.builder()
//                         .protocolVersion("2024-11-05")
//                         .capabilities(McpProtocol.McpClientCapabilities.builder()
//                                 .roots(McpProtocol.McpRootsCapability.builder()
//                                         .listChanged(true)
//                                         .build())
//                                 .sampling(McpProtocol.McpSamplingCapability.builder().build())
//                                 .build())
//                         .clientInfo(McpProtocol.McpClientInfo.builder()
//                                 .name("test-client")
//                                 .version("1.0.0")
//                                 .build())
//                         .build())
//                 .build();

//         mockMvc.perform(post("/mcp/jsonrpc")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(request)))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                 .andExpect(jsonPath("$.jsonrpc").value("2.0"))
//                 .andExpect(jsonPath("$.id").value("1"))
//                 .andExpect(jsonPath("$.result").exists());
//     }

//     @Test
//     void testHttpResourcesEndpoints() throws Exception {
//         // 准备测试数据
//         ProviderInfo provider = new ProviderInfo();
//         provider.setInterfaceName("com.example.UserService");
//         provider.setApplication("user-service");
//         provider.setAddress("192.168.1.100:20880");
//         provider.setOnline(true);

//         when(providerService.getAllProviders()).thenReturn(Arrays.asList(provider));

//         // 测试列出资源
//         mockMvc.perform(get("/mcp/resources"))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                 .andExpect(jsonPath("$.resources").isArray())
//                 .andExpect(jsonPath("$.resources.length()").value(greaterThan(0)));

//         // 测试读取系统健康资源
//         mockMvc.perform(get("/mcp/resources/system://health"))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                 .andExpect(jsonPath("$.contents").isArray())
//                 .andExpect(jsonPath("$.contents.length()").value(1));

//         // 测试订阅资源
//         McpProtocol.SubscribeResourceParams subscribeParams = McpProtocol.SubscribeResourceParams.builder()
//                 .uri("system://providers")
//                 .build();

//         mockMvc.perform(post("/mcp/resources/subscribe")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(subscribeParams)))
//                 .andExpect(status().isOk());

//         // 测试取消订阅资源
//         McpProtocol.UnsubscribeResourceParams unsubscribeParams = McpProtocol.UnsubscribeResourceParams.builder()
//                 .uri("system://providers")
//                 .build();

//         mockMvc.perform(post("/mcp/resources/unsubscribe")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(unsubscribeParams)))
//                 .andExpect(status().isOk());
//     }

//     @Test
//     void testHttpPromptsEndpoints() throws Exception {
//         // 测试列出提示
//         mockMvc.perform(get("/mcp/prompts"))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                 .andExpect(jsonPath("$.prompts").isArray())
//                 .andExpect(jsonPath("$.prompts.length()").value(greaterThan(0)));

//         // 测试获取提示
//         McpProtocol.GetPromptParams getPromptParams = McpProtocol.GetPromptParams.builder()
//                 .name("dubbo_analysis")
//                 .arguments(Map.of(
//                         "providerName", "user-service",
//                         "analysisType", "detailed"))
//                 .build();

//         mockMvc.perform(post("/mcp/prompts/get")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(getPromptParams)))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                 .andExpect(jsonPath("$.messages").isArray())
//                 .andExpect(jsonPath("$.messages.length()").value(greaterThan(0)));

//         // 测试添加自定义提示
//         McpProtocol.McpPrompt customPrompt = McpProtocol.McpPrompt.builder()
//                 .name("custom_prompt")
//                 .description("自定义提示")
//                 .arguments(Map.of(
//                         "customField", "test"))
//                 .build();

//         mockMvc.perform(post("/mcp/prompts/add")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(customPrompt)))
//                 .andExpect(status().isOk());

//         // 测试删除提示
//         mockMvc.perform(delete("/mcp/prompts/custom_prompt"))
//                 .andExpect(status().isOk());
//     }

//     @Test
//     void testHttpLoggingEndpoints() throws Exception {
//         // 测试记录日志
//         McpProtocol.LogParams logParams = McpProtocol.LogParams.builder()
//                 .level("info")
//                 .data("测试日志消息")
//                 .logger("test-logger")
//                 .build();

//         mockMvc.perform(post("/mcp/logging/log")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(logParams)))
//                 .andExpect(status().isOk());

//         // 测试获取日志消息
//         mockMvc.perform(get("/mcp/logging/messages"))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                 .andExpect(jsonPath("$.messages").isArray());

//         // 测试获取日志统计
//         mockMvc.perform(get("/mcp/logging/statistics"))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                 .andExpect(jsonPath("$.statistics").exists());

//         // 测试清空日志
//         mockMvc.perform(delete("/mcp/logging/clear"))
//                 .andExpect(status().isOk());
//     }

//     @Test
//     void testHttpStreamingEndpoints() throws Exception {
//         // 测试创建流式调用
//         McpProtocol.StreamParams streamParams = McpProtocol.StreamParams.builder()
//                 .method("resources/list")
//                 .params(McpProtocol.ListResourcesParams.builder().build())
//                 .build();

//         mockMvc.perform(post("/mcp/stream")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(streamParams)))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                 .andExpect(jsonPath("$.streamId").exists());

//         // 测试获取流式数据
//         mockMvc.perform(get("/mcp/stream/test-stream-id"))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType("text/event-stream"));
//     }

//     @Test
//     void testHttpHealthAndInfoEndpoints() throws Exception {
//         // 测试健康检查
//         mockMvc.perform(get("/mcp/health"))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                 .andExpect(jsonPath("$.status").value("UP"));

//         // 测试服务器信息
//         mockMvc.perform(get("/mcp/info"))
//                 .andExpect(status().isOk())
//                 .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                 .andExpect(jsonPath("$.name").exists())
//                 .andExpect(jsonPath("$.version").exists());
//     }

//     @Test
//     void testErrorHandling() throws Exception {
//         // 测试不存在的资源
//         mockMvc.perform(get("/mcp/resources/non_existent://resource"))
//                 .andExpect(status().isNotFound());

//         // 测试不存在的提示
//         McpProtocol.GetPromptParams getPromptParams = McpProtocol.GetPromptParams.builder()
//                 .name("non_existent_prompt")
//                 .build();

//         mockMvc.perform(post("/mcp/prompts/get")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(getPromptParams)))
//                 .andExpect(status().isNotFound());

//         // 测试无效的 JSON-RPC 请求
//         String invalidJson = "{\"invalid\": \"json\"}";

//         mockMvc.perform(post("/mcp/jsonrpc")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(invalidJson))
//                 .andExpect(status().isBadRequest());
//     }

//     @Test
//     void testContentTypeHandling() throws Exception {
//         // 测试不同的 Content-Type
//         McpProtocol.LogParams logParams = McpProtocol.LogParams.builder()
//                 .level("info")
//                 .data("测试不同 Content-Type")
//                 .logger("content-type-test")
//                 .build();

//         // 测试 application/json
//         mockMvc.perform(post("/mcp/logging/log")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(logParams)))
//                 .andExpect(status().isOk());

//         // 测试 application/json;charset=UTF-8
//         mockMvc.perform(post("/mcp/logging/log")
//                         .contentType("application/json;charset=UTF-8")
//                         .content(objectMapper.writeValueAsString(logParams)))
//                 .andExpect(status().isOk());
//     }

//     @Test
//     void testLargePayloadHandling() throws Exception {
//         // 测试大负载处理
//         StringBuilder largeData = new StringBuilder();
//         for (int i = 0; i < 1000; i++) {
//             largeData.append("这是一条很长的日志消息，用于测试系统对大负载的处理能力。");
//         }

//         McpProtocol.LogParams logParams = McpProtocol.LogParams.builder()
//                 .level("info")
//                 .data(largeData.toString())
//                 .logger("large-payload-test")
//                 .build();

//         mockMvc.perform(post("/mcp/logging/log")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(logParams)))
//                 .andExpect(status().isOk());
//     }

//     @Test
//     void testUnicodeHandling() throws Exception {
//         // 测试 Unicode 字符处理
//         McpProtocol.LogParams logParams = McpProtocol.LogParams.builder()
//                 .level("info")
//                 .data("测试Unicode字符: 🚀🔥💡⭐️🎉中文测试")
//                 .logger("unicode-test")
//                 .build();

//         mockMvc.perform(post("/mcp/logging/log")
//                         .contentType(MediaType.APPLICATION_JSON)
//                         .content(objectMapper.writeValueAsString(logParams)))
//                 .andExpect(status().isOk());
//     }
// }

