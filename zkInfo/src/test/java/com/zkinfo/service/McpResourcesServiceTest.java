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

// import static org.mockito.Mockito.when;

// /**
//  * MCP Resources 服务测试
//  */
// @ExtendWith(MockitoExtension.class)
// class McpResourcesServiceTest {

//     @Mock
//     private ProviderService providerService;

//     private McpResourcesService mcpResourcesService;

//     @BeforeEach
//     void setUp() {
//         mcpResourcesService = new McpResourcesService(providerService);
//     }

//     @Test
//     void testListResources() {
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

//         // 执行测试
//         McpProtocol.ListResourcesParams params = McpProtocol.ListResourcesParams.builder()
//                 .cursor(null)
//                 .build();

//         Mono<McpProtocol.ListResourcesResult> result = mcpResourcesService.listResources(params);

//         // 验证结果
//         StepVerifier.create(result)
//                 .assertNext(listResult -> {
//                     assert listResult.getResources() != null;
//                     assert listResult.getResources().size() >= 2; // 至少包含2个提供者资源
//                     assert listResult.getNextCursor() == null;
//                 })
//                 .verifyComplete();
//     }

//     @Test
//     void testReadSystemResource() {
//         // 测试系统健康资源
//         McpProtocol.ReadResourceParams params = McpProtocol.ReadResourceParams.builder()
//                 .uri("system://health")
//                 .build();

//         Mono<McpProtocol.ReadResourceResult> result = mcpResourcesService.readResource(params);

//         StepVerifier.create(result)
//                 .assertNext(readResult -> {
//                     assert readResult.getContents() != null;
//                     assert readResult.getContents().size() == 1;
//                     assert readResult.getContents().get(0).getType().equals("json");
//                 })
//                 .verifyComplete();
//     }

//     @Test
//     void testSubscribeResource() {
//         // 测试资源订阅
//         McpProtocol.SubscribeResourceParams params = McpProtocol.SubscribeResourceParams.builder()
//                 .uri("system://providers")
//                 .build();

//         Mono<Void> result = mcpResourcesService.subscribeResource("test_client", params);

//         StepVerifier.create(result)
//                 .verifyComplete();
//     }

//     @Test
//     void testUnsubscribeResource() {
//         // 测试取消资源订阅
//         McpProtocol.UnsubscribeResourceParams params = McpProtocol.UnsubscribeResourceParams.builder()
//                 .uri("system://providers")
//                 .build();

//         Mono<Void> result = mcpResourcesService.unsubscribeResource("test_client", params);

//         StepVerifier.create(result)
//                 .verifyComplete();
//     }
// }





