// package com.pajk.mcpmetainfo.core.integration;

// import com.pajk.mcpmetainfo.core.mcp.McpProtocol;
// import com.pajk.mcpmetainfo.core.model.ProviderInfo;
// import com.pajk.mcpmetainfo.core.service.McpProtocolService;
// import com.pajk.mcpmetainfo.core.service.ProviderService;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;
// import reactor.core.publisher.Mono;
// import reactor.test.StepVerifier;

// import java.util.Arrays;
// import java.util.List;

// import static org.mockito.Mockito.when;

// /**
//  * MCP 集成测试
//  * 测试完整的 MCP 协议流程
//  */
// @ExtendWith(MockitoExtension.class)
// class McpIntegrationTest {

//     @Mock
//     private ProviderService providerService;

//     private McpProtocolService mcpProtocolService;

//     @BeforeEach
//     void setUp() {
//         mcpProtocolService = new McpProtocolService(providerService);
//     }

//     @Test
//     void testCompleteMcpWorkflow() {
//         // 准备测试数据
//         ProviderInfo provider1 = new ProviderInfo();
//         provider1.setInterfaceName("com.example.UserService");
//         provider1.setApplication("user-service");
//         provider1.setAddress("192.168.1.100:20880");
//         provider1.setOnline(true);

//         ProviderInfo provider2 = new ProviderInfo();
//         provider2.setInterfaceName("com.example.OrderService");
//         provider2.setApplication("order-service");
//         provider2.setAddress("192.168.1.101:20881");
//         provider2.setOnline(false);

//         List<ProviderInfo> providers = Arrays.asList(provider1, provider2);
//         when(providerService.getAllProviders()).thenReturn(providers);

//         // 1. 测试初始化
//         McpProtocol.InitializeParams initParams = McpProtocol.InitializeParams.builder()
//                 .protocolVersion("2024-11-05")
//                 .capabilities(McpProtocol.McpClientCapabilities.builder()
//                         .roots(McpProtocol.McpRootsCapability.builder()
//                                 .listChanged(true)
//                                 .build())
//                         .sampling(McpProtocol.McpSamplingCapability.builder().build())
//                         .build())
//                 .clientInfo(McpProtocol.McpClientInfo.builder()
//                         .name("test-client")
//                         .version("1.0.0")
//                         .build())
//                 .build();

//         Mono<McpProtocol.InitializeResult> initResult = mcpProtocolService.initialize(initParams);

//         StepVerifier.create(initResult)
//                 .assertNext(result -> {
//                     assert result.getProtocolVersion().equals("2024-11-05");
//                     assert result.getCapabilities() != null;
//                     assert result.getServerInfo() != null;
//                 })
//                 .verifyComplete();

//         // 2. 测试资源列表
//         McpProtocol.ListResourcesParams listParams = McpProtocol.ListResourcesParams.builder()
//                 .cursor(null)
//                 .build();

//         Mono<McpProtocol.ListResourcesResult> listResult = mcpProtocolService.listResources(listParams);

//         StepVerifier.create(listResult)
//                 .assertNext(result -> {
//                     assert result.getResources() != null;
//                     assert result.getResources().size() >= 2; // 至少包含2个提供者资源
//                 })
//                 .verifyComplete();

//         // 3. 测试资源读取
//         McpProtocol.ReadResourceParams readParams = McpProtocol.ReadResourceParams.builder()
//                 .uri("system://health")
//                 .build();

//         Mono<McpProtocol.ReadResourceResult> readResult = mcpProtocolService.readResource(readParams);

//         StepVerifier.create(readResult)
//                 .assertNext(result -> {
//                     assert result.getContents() != null;
//                     assert result.getContents().size() == 1;
//                 })
//                 .verifyComplete();

//         // 4. 测试提示列表
//         McpProtocol.ListPromptsParams promptsParams = McpProtocol.ListPromptsParams.builder()
//                 .build();

//         Mono<McpProtocol.ListPromptsResult> promptsResult = mcpProtocolService.listPrompts(promptsParams);

//         StepVerifier.create(promptsResult)
//                 .assertNext(result -> {
//                     assert result.getPrompts() != null;
//                     assert result.getPrompts().size() >= 5; // 至少包含5个预定义提示
//                 })
//                 .verifyComplete();

//         // 5. 测试获取提示
//         McpProtocol.GetPromptParams getPromptParams = McpProtocol.GetPromptParams.builder()
//                 .name("dubbo_analysis")
//                 .arguments(Map.of(
//                         "providerName", "user-service",
//                         "analysisType", "detailed"))
//                 .build();

//         Mono<McpProtocol.GetPromptResult> getPromptResult = mcpProtocolService.getPrompt(getPromptParams);

//         StepVerifier.create(getPromptResult)
//                 .assertNext(result -> {
//                     assert result.getMessages() != null;
//                     assert result.getMessages().size() >= 1;
//                 })
//                 .verifyComplete();

//         // 6. 测试日志记录
//         McpProtocol.LogMessageParams logParams = McpProtocol.LogMessageParams.builder()
//                 .level("info")
//                 .data("MCP 集成测试完成")
//                 .logger("integration-test")
//                 .build();

//         Mono<Void> logResult = mcpProtocolService.log(logParams);

//         StepVerifier.create(logResult)
//                 .verifyComplete();
//     }

//     @Test
//     void testResourcesWorkflow() {
//         // 准备测试数据
//         ProviderInfo provider = new ProviderInfo();
//         provider.setInterfaceName("com.example.TestService");
//         provider.setApplication("test-service");
//         provider.setAddress("192.168.1.200:20882");
//         provider.setOnline(true);

//         when(providerService.getAllProviders()).thenReturn(Arrays.asList(provider));

//         // 1. 列出所有资源
//         McpProtocol.ListResourcesParams listParams = McpProtocol.ListResourcesParams.builder()
//                 .build();

//         Mono<McpProtocol.ListResourcesResult> listResult = mcpProtocolService.listResources(listParams);

//         StepVerifier.create(listResult)
//                 .assertNext(result -> {
//                     assert result.getResources() != null;
//                     assert result.getResources().size() >= 1;
                    
//                     // 验证包含系统资源
//                     boolean hasSystemResource = result.getResources().stream()
//                             .anyMatch(resource -> resource.getUri().startsWith("system://"));
//                     assert hasSystemResource;
                    
//                     // 验证包含提供者资源
//                     boolean hasProviderResource = result.getResources().stream()
//                             .anyMatch(resource -> resource.getUri().startsWith("provider://"));
//                     assert hasProviderResource;
//                 })
//                 .verifyComplete();

//         // 2. 读取系统健康资源
//         McpProtocol.ReadResourceParams readHealthParams = McpProtocol.ReadResourceParams.builder()
//                 .uri("system://health")
//                 .build();

//         Mono<McpProtocol.ReadResourceResult> readHealthResult = mcpProtocolService.readResource(readHealthParams);

//         StepVerifier.create(readHealthResult)
//                 .assertNext(result -> {
//                     assert result.getContents() != null;
//                     assert result.getContents().size() == 1;
//                     assert result.getContents().get(0).getType().equals("json");
//                 })
//                 .verifyComplete();

//         // 3. 读取提供者资源
//         McpProtocol.ReadResourceParams readProviderParams = McpProtocol.ReadResourceParams.builder()
//                 .uri("provider://com.example.TestService")
//                 .build();

//         Mono<McpProtocol.ReadResourceResult> readProviderResult = mcpProtocolService.readResource(readProviderParams);

//         StepVerifier.create(readProviderResult)
//                 .assertNext(result -> {
//                     assert result.getContents() != null;
//                     assert result.getContents().size() == 1;
//                     assert result.getContents().get(0).getType().equals("json");
//                 })
//                 .verifyComplete();

//         // 4. 订阅资源
//         McpProtocol.SubscribeResourceParams subscribeParams = McpProtocol.SubscribeResourceParams.builder()
//                 .uri("system://providers")
//                 .build();

//         Mono<Void> subscribeResult = mcpProtocolService.subscribeResource("test_client", subscribeParams);

//         StepVerifier.create(subscribeResult)
//                 .verifyComplete();

//         // 5. 取消订阅资源
//         McpProtocol.UnsubscribeResourceParams unsubscribeParams = McpProtocol.UnsubscribeResourceParams.builder()
//                 .uri("system://providers")
//                 .build();

//         Mono<Void> unsubscribeResult = mcpProtocolService.unsubscribeResource("test_client", unsubscribeParams);

//         StepVerifier.create(unsubscribeResult)
//                 .verifyComplete();
//     }

//     @Test
//     void testPromptsWorkflow() {
//         // 1. 列出所有提示
//         McpProtocol.ListPromptsParams listParams = McpProtocol.ListPromptsParams.builder()
//                 .build();

//         Mono<McpProtocol.ListPromptsResult> listResult = mcpProtocolService.listPrompts(listParams);

//         StepVerifier.create(listResult)
//                 .assertNext(result -> {
//                     assert result.getPrompts() != null;
//                     assert result.getPrompts().size() >= 5;
                    
//                     // 验证包含预定义提示
//                     List<String> promptNames = result.getPrompts().stream()
//                             .map(McpProtocol.McpPrompt::getName)
//                             .toList();
//                     assert promptNames.contains("dubbo_analysis");
//                     assert promptNames.contains("health_check");
//                     assert promptNames.contains("service_statistics");
//                     assert promptNames.contains("config_management");
//                     assert promptNames.contains("service_monitoring");
//                 })
//                 .verifyComplete();

//         // 2. 获取 Dubbo 分析提示
//         McpProtocol.GetPromptParams getAnalysisParams = McpProtocol.GetPromptParams.builder()
//                 .name("dubbo_analysis")
//                 .arguments(Map.of(
//                         "providerName", "test-service",
//                         "analysisType", "detailed"))
//                 .build();

//         Mono<McpProtocol.GetPromptResult> getAnalysisResult = mcpProtocolService.getPrompt(getAnalysisParams);

//         StepVerifier.create(getAnalysisResult)
//                 .assertNext(result -> {
//                     assert result.getMessages() != null;
//                     assert result.getMessages().size() >= 1;
//                     assert result.getMessages().get(0).getRole().equals("user");
//                 })
//                 .verifyComplete();

//         // 3. 获取健康检查提示
//         McpProtocol.GetPromptParams getHealthParams = McpProtocol.GetPromptParams.builder()
//                 .name("health_check")
//                 .arguments(Map.of(
//                         "serviceName", "test-service",
//                         "checkType", "comprehensive"))
//                 .build();

//         Mono<McpProtocol.GetPromptResult> getHealthResult = mcpProtocolService.getPrompt(getHealthParams);

//         StepVerifier.create(getHealthResult)
//                 .assertNext(result -> {
//                     assert result.getMessages() != null;
//                     assert result.getMessages().size() >= 1;
//                     assert result.getMessages().get(0).getRole().equals("user");
//                 })
//                 .verifyComplete();
//     }

//     @Test
//     void testLoggingWorkflow() {
//         // 1. 记录不同级别的日志
//         String[] levels = {"debug", "info", "warning", "error"};
        
//         for (String level : levels) {
//             McpProtocol.LogMessageParams logParams = McpProtocol.LogMessageParams.builder()
//                     .level(level)
//                     .data("测试 " + level + " 级别日志")
//                     .logger("integration-test")
//                     .build();

//             Mono<Void> logResult = mcpProtocolService.log(logParams);

//             StepVerifier.create(logResult)
//                     .verifyComplete();
//         }

//         // 2. 记录结构化日志
//         McpProtocol.LogParams structuredLogParams = McpProtocol.LogParams.builder()
//                 .level("info")
//                 .data("{\"message\": \"结构化日志\", \"timestamp\": \"2025-01-21T10:00:00Z\", \"level\": \"info\"}")
//                 .logger("structured-logger")
//                 .build();

//         Mono<Void> structuredLogResult = mcpProtocolService.log(structuredLogParams);

//         StepVerifier.create(structuredLogResult)
//                 .verifyComplete();

//         // 3. 记录带特殊字符的日志
//         McpProtocol.LogParams specialCharLogParams = McpProtocol.LogParams.builder()
//                 .level("warning")
//                 .data("测试特殊字符: !@#$%^&*()_+-=[]{}|;':\",./<>?")
//                 .logger("special-chars-logger")
//                 .build();

//         Mono<Void> specialCharLogResult = mcpProtocolService.log(specialCharLogParams);

//         StepVerifier.create(specialCharLogResult)
//                 .verifyComplete();
//     }

//     @Test
//     void testErrorHandling() {
//         // 1. 测试不存在的资源
//         McpProtocol.ReadResourceParams readParams = McpProtocol.ReadResourceParams.builder()
//                 .uri("non_existent://resource")
//                 .build();

//         Mono<McpProtocol.ReadResourceResult> readResult = mcpProtocolService.readResource(readParams);

//         StepVerifier.create(readResult)
//                 .expectError()
//                 .verify();

//         // 2. 测试不存在的提示
//         McpProtocol.GetPromptParams getPromptParams = McpProtocol.GetPromptParams.builder()
//                 .name("non_existent_prompt")
//                 .build();

//         Mono<McpProtocol.GetPromptResult> getPromptResult = mcpProtocolService.getPrompt(getPromptParams);

//         StepVerifier.create(getPromptResult)
//                 .expectError()
//                 .verify();
//     }
// }

