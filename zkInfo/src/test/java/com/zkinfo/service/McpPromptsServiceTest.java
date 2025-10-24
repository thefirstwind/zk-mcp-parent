// package com.zkinfo.service;

// import com.zkinfo.mcp.McpProtocol;
// import com.zkinfo.model.ProviderInfo;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;
// import reactor.core.publisher.Mono;
// import reactor.test.StepVerifier;

// import java.util.Arrays;
// import java.util.List;
// import java.util.Map;

// import static org.mockito.Mockito.when;

// /**
//  * MCP Prompts 服务测试
//  */
// @ExtendWith(MockitoExtension.class)
// class McpPromptsServiceTest {

//     @Mock
//     private ProviderService providerService;

//     private McpPromptsService mcpPromptsService;

//     @BeforeEach
//     void setUp() {
//         mcpPromptsService = new McpPromptsService();
//         mcpPromptsService.initializePrompts();
//     }

//     @Test
//     void testListPrompts() {
//         // 执行测试
//         McpProtocol.ListPromptsParams params = McpProtocol.ListPromptsParams.builder()
//                 .build();

//         Mono<McpProtocol.ListPromptsResult> result = mcpPromptsService.listPrompts(params);

//         // 验证结果
//         StepVerifier.create(result)
//                 .assertNext(listResult -> {
//                     assert listResult.getPrompts() != null;
//                     assert listResult.getPrompts().size() >= 5; // 至少包含5个预定义提示
                    
//                     // 验证包含预定义提示
//                     List<String> promptNames = listResult.getPrompts().stream()
//                             .map(McpProtocol.McpPrompt::getName)
//                             .toList();
//                     assert promptNames.contains("dubbo_analysis");
//                     assert promptNames.contains("health_check");
//                     assert promptNames.contains("service_statistics");
//                     assert promptNames.contains("config_management");
//                     assert promptNames.contains("service_monitoring");
//                 })
//                 .verifyComplete();
//     }

//     @Test
//     void testGetDubboAnalysisPrompt() {
//         // 准备测试数据
//         ProviderInfo provider = new ProviderInfo();
//         provider.setInterfaceName("com.example.UserService");
//         provider.setApplication("user-service");
//         provider.setAddress("192.168.1.100:20880");
//         provider.setOnline(true);

//         when(providerService.getAllProviders()).thenReturn(Arrays.asList(provider));

//         // 执行测试
//         McpProtocol.GetPromptParams params = McpProtocol.GetPromptParams.builder()
//                 .name("dubbo_analysis")
//                 .arguments(Map.of(
//                         "providerName", "user-service",
//                         "analysisType", "detailed"))
//                 .build();

//         Mono<McpProtocol.GetPromptResult> result = mcpPromptsService.getPrompt(params);

//         // 验证结果
//         StepVerifier.create(result)
//                 .assertNext(promptResult -> {
//                     assert promptResult.getMessages() != null;
//                     assert promptResult.getMessages().size() >= 1;
                    
//                     McpProtocol.McpPromptMessage message = promptResult.getMessages().get(0);
//                     assert message.getRole().equals("user");
//                     assert message.getContent() != null;
//                     assert message.getContent().getText() != null;
//                     assert message.getContent().getText().contains("user-service");
//                     assert message.getContent().getText().contains("Dubbo 服务分析");
//                 })
//                 .verifyComplete();
//     }

//     @Test
//     void testGetHealthCheckPrompt() {
//         // 执行测试
//         McpProtocol.GetPromptParams params = McpProtocol.GetPromptParams.builder()
//                 .name("health_check")
//                 .arguments(Map.of(
//                         "serviceName", "user-service",
//                         "checkType", "comprehensive"))
//                 .build();

//         Mono<McpProtocol.GetPromptResult> result = mcpPromptsService.getPrompt(params);

//         // 验证结果
//         StepVerifier.create(result)
//                 .assertNext(promptResult -> {
//                     assert promptResult.getMessages() != null;
//                     assert promptResult.getMessages().size() >= 1;
                    
//                     McpProtocol.McpPromptMessage message = promptResult.getMessages().get(0);
//                     assert message.getRole().equals("user");
//                     assert message.getContent() != null;
//                     assert message.getContent().getText() != null;
//                     assert message.getContent().getText().contains("健康检查");
//                     assert message.getContent().getText().contains("user-service");
//                 })
//                 .verifyComplete();
//     }

//     @Test
//     void testGetServiceStatisticsPrompt() {
//         // 执行测试
//         McpProtocol.GetPromptParams params = McpProtocol.GetPromptParams.builder()
//                 .name("service_statistics")
//                 .arguments(Map.of(
//                         "timeRange", "24h",
//                         "statisticsType", "performance"
//                         ))
//                 .build();

//         Mono<McpProtocol.GetPromptResult> result = mcpPromptsService.getPrompt(params);

//         // 验证结果
//         StepVerifier.create(result)
//                 .assertNext(promptResult -> {
//                     assert promptResult.getMessages() != null;
//                     assert promptResult.getMessages().size() >= 1;
                    
//                     McpProtocol.McpPromptMessage message = promptResult.getMessages().get(0);
//                     assert message.getRole().equals("user");
//                     assert message.getContent() != null;
//                     assert message.getContent().getText() != null;
//                     assert message.getContent().getText().contains("服务调用统计");
//                     assert message.getContent().getText().contains("24h");
//                 })
//                 .verifyComplete();
//     }

//     @Test
//     void testGetConfigManagementPrompt() {
//         // 执行测试
//         McpProtocol.GetPromptParams params = McpProtocol.GetPromptParams.builder()
//                 .name("config_management")
//                 .arguments(Map.of(
//                         "configType", "dubbo",
//                         "operation", "update"
//                         ))
//                 .build();

//         Mono<McpProtocol.GetPromptResult> result = mcpPromptsService.getPrompt(params);

//         // 验证结果
//         StepVerifier.create(result)
//                 .assertNext(promptResult -> {
//                     assert promptResult.getMessages() != null;
//                     assert promptResult.getMessages().size() >= 1;
                    
//                     McpProtocol.McpPromptMessage message = promptResult.getMessages().get(0);
//                     assert message.getRole().equals("user");
//                     assert message.getContent() != null;
//                     assert message.getContent().getText() != null;
//                     assert message.getContent().getText().contains("服务配置管理");
//                     assert message.getContent().getText().contains("dubbo");
//                 })
//                 .verifyComplete();
//     }

//     @Test
//     void testGetServiceMonitoringPrompt() {
//         // 执行测试
//         McpProtocol.GetPromptParams params = McpProtocol.GetPromptParams.builder()
//                 .name("service_monitoring")
//                 .arguments(Map.of(
//                         "monitoringType", "performance",
//                         "alertLevel", "warning"
//                         ))
//                 .build();

//         Mono<McpProtocol.GetPromptResult> result = mcpPromptsService.getPrompt(params);

//         // 验证结果
//         StepVerifier.create(result)
//                 .assertNext(promptResult -> {
//                     assert promptResult.getMessages() != null;
//                     assert promptResult.getMessages().size() >= 1;
                    
//                     McpProtocol.McpPromptMessage message = promptResult.getMessages().get(0);
//                     assert message.getRole().equals("user");
//                     assert message.getContent() != null;
//                     assert message.getContent().getText() != null;
//                     assert message.getContent().getText().contains("服务性能监控");
//                     assert message.getContent().getText().contains("performance");
//                 })
//                 .verifyComplete();
//     }

//     @Test
//     void testGetNonExistentPrompt() {
//         // 执行测试
//         McpProtocol.GetPromptParams params = McpProtocol.GetPromptParams.builder()
//                 .name("non_existent_prompt")
//                 .build();

//         Mono<McpProtocol.GetPromptResult> result = mcpPromptsService.getPrompt(params);

//         // 验证结果 - 应该返回错误
//         StepVerifier.create(result)
//                 .expectError()
//                 .verify();
//     }
// }

