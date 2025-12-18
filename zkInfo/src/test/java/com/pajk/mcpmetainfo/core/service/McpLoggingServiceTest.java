// package com.pajk.mcpmetainfo.core.service;

// import com.pajk.mcpmetainfo.core.mcp.McpProtocol;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.junit.jupiter.MockitoExtension;
// import reactor.core.publisher.Mono;
// import reactor.test.StepVerifier;

// import java.util.List;

// /**
//  * MCP Logging 服务测试
//  */
// @ExtendWith(MockitoExtension.class)
// class McpLoggingServiceTest {

//     private McpLoggingService mcpLoggingService;

//     @BeforeEach
//     void setUp() {
//         mcpLoggingService = new McpLoggingService();
//     }

//     @Test
//     void testLogMessage() {
//         // 执行测试
//         McpProtocol.LogMessageParams params = McpProtocol.LogMessageParams.builder()
//                 .level("info")
//                 .data("测试日志消息")
//                 .logger("test-logger")
//                 .build();

//         Mono<Void> result = mcpLoggingService.log(params);

//         // 验证结果
//         StepVerifier.create(result)
//                 .verifyComplete();
//     }

//     @Test
//     void testLogMessageWithAllLevels() {
//         String[] levels = {"debug", "info", "notice", "warning", "error", "critical", "alert", "emergency"};
        
//         for (String level : levels) {
//             McpProtocol.LogMessageParams params = McpProtocol.LogMessageParams.builder()
//                     .level(level)
//                     .data("测试 " + level + " 级别日志")
//                     .logger("test-logger")
//                     .build();

//             Mono<Void> result = mcpLoggingService.log(params);

//             StepVerifier.create(result)
//                     .verifyComplete();
//         }
//     }

//     @Test
//     void testLogMessageWithStructuredData() {
//         // 执行测试
//         McpProtocol.LogMessageParams params = McpProtocol.LogMessageParams.builder()
//                 .level("info")
//                 .data("{\"message\": \"结构化日志\", \"timestamp\": \"2025-01-21T10:00:00Z\", \"level\": \"info\"}")
//                 .logger("structured-logger")
//                 .build();

//         Mono<Void> result = mcpLoggingService.log(params);

//         // 验证结果
//         StepVerifier.create(result)
//                 .verifyComplete();
//     }

//     @Test
//     void testLogMessageWithNullLogger() {
//         // 执行测试
//         McpProtocol.LogMessageParams params = McpProtocol.LogMessageParams.builder()
//                 .level("info")
//                 .data("测试默认日志记录器")
//                 .logger(null)
//                 .build();

//         Mono<Void> result = mcpLoggingService.log(params);

//         // 验证结果
//         StepVerifier.create(result)
//                 .verifyComplete();
//     }

//     @Test
//     void testLogMessageWithEmptyData() {
//         // 执行测试
//         McpProtocol.LogMessageParams params = McpProtocol.LogMessageParams.builder()
//                 .level("info")
//                 .data("")
//                 .logger("test-logger")
//                 .build();

//         Mono<Void> result = mcpLoggingService.log(params);

//         // 验证结果
//         StepVerifier.create(result)
//                 .verifyComplete();
//     }

//     @Test
//     void testLogMessageWithSpecialCharacters() {
//         // 执行测试
//         McpProtocol.LogMessageParams params = McpProtocol.LogMessageParams.builder()
//                 .level("warning")
//                 .data("测试特殊字符: !@#$%^&*()_+-=[]{}|;':\",./<>?")
//                 .logger("special-chars-logger")
//                 .build();

//         Mono<Void> result = mcpLoggingService.log(params);

//         // 验证结果
//         StepVerifier.create(result)
//                 .verifyComplete();
//     }

//     @Test
//     void testLogMessageWithLongData() {
//         // 执行测试
//         StringBuilder longData = new StringBuilder();
//         for (int i = 0; i < 1000; i++) {
//             longData.append("这是一条很长的日志消息，用于测试系统对长消息的处理能力。");
//         }

//         McpProtocol.LogMessageParams params = McpProtocol.LogMessageParams.builder()
//                 .level("info")
//                 .data(longData.toString())
//                 .logger("long-data-logger")
//                 .build();

//         Mono<Void> result = mcpLoggingService.log(params);

//         // 验证结果
//         StepVerifier.create(result)
//                 .verifyComplete();
//     }

//     @Test
//     void testLogMessageWithUnicodeCharacters() {
//         // 执行测试
//         McpProtocol.LogMessageParams params = McpProtocol.LogMessageParams.builder()
//                 .level("info")
//                 .data("测试Unicode字符: 🚀🔥💡⭐️🎉中文测试")
//                 .logger("unicode-logger")
//                 .build();

//         Mono<Void> result = mcpLoggingService.log(params);

//         // 验证结果
//         StepVerifier.create(result)
//                 .verifyComplete();
//     }

//     @Test
//     void testLogMessageWithJsonData() {
//         // 执行测试
//         String jsonData = "{\n" +
//                 "  \"message\": \"JSON格式日志\",\n" +
//                 "  \"timestamp\": \"2025-01-21T10:00:00Z\",\n" +
//                 "  \"level\": \"info\",\n" +
//                 "  \"logger\": \"json-logger\",\n" +
//                 "  \"metadata\": {\n" +
//                 "    \"userId\": 12345,\n" +
//                 "    \"sessionId\": \"abc123\",\n" +
//                 "    \"requestId\": \"req-456\"\n" +
//                 "  }\n" +
//                 "}";

//         McpProtocol.LogMessageParams params = McpProtocol.LogMessageParams.builder()
//                 .level("info")
//                 .data(jsonData)
//                 .logger("json-logger")
//                 .build();

//         Mono<Void> result = mcpLoggingService.log(params);

//         // 验证结果
//         StepVerifier.create(result)
//                 .verifyComplete();
//     }

//     @Test
//     void testLogMessageWithNullData() {
//         // 执行测试
//         McpProtocol.LogMessageParams params = McpProtocol.LogMessageParams.builder()
//                 .level("info")
//                 .data(null)
//                 .logger("null-data-logger")
//                 .build();

//         Mono<Void> result = mcpLoggingService.log(params);

//         // 验证结果
//         StepVerifier.create(result)
//                 .verifyComplete();
//     }

//     @Test
//     void testLogMessageWithInvalidLevel() {
//         // 执行测试
//         McpProtocol.LogMessageParams params = McpProtocol.LogMessageParams.builder()
//                 .level("invalid_level")
//                 .data("测试无效级别")
//                 .logger("invalid-level-logger")
//                 .build();

//         Mono<Void> result = mcpLoggingService.log(params);

//         // 验证结果
//         StepVerifier.create(result)
//                 .verifyComplete();
//     }
// }

