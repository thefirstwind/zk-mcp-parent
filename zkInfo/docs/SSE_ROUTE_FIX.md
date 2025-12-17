# SSE 路由修复说明

## 问题描述

SSE 端点返回 404 错误，所有 `/sse/{endpoint}` 格式的请求都无法访问。

## 根本原因

`MultiEndpointMcpRouterConfig` 和 `McpServerConfig` 中的 `RouterFunction` Bean 可能存在冲突，导致路由未正确注册。

## 修复内容

### 1. MultiEndpointMcpRouterConfig.java

添加了以下注解：
- `@Order(HIGHEST_PRECEDENCE)`: 确保路由优先级最高
- `@ConditionalOnMissingBean(name = "mcpRouterFunction")`: 避免与 `McpServerConfig` 的 Bean 冲突

```java
@Slf4j
@Configuration
@RequiredArgsConstructor
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class MultiEndpointMcpRouterConfig {
    
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(name = "mcpRouterFunction")
    public RouterFunction<ServerResponse> multiEndpointRouterFunction() {
        // ...
    }
}
```

### 2. McpServerConfig.java

更新了 `mcpServerTransportProvider` Bean 的条件检查：

```java
@Bean
@ConditionalOnMissingBean(name = "multiEndpointRouterFunction")
public McpServerTransportProvider mcpServerTransportProvider(ObjectMapper objectMapper) {
    // ...
}
```

## 应用修复

### 步骤 1: 重新编译

```bash
cd /Users/shine/projects.mcp-router-sse-parent/zk-mcp-parent/zkInfo
mvn clean compile
```

### 步骤 2: 重启服务

```bash
# 停止当前服务
kill $(lsof -t -i:9091)

# 重新启动
mvn spring-boot:run
```

### 步骤 3: 验证修复

```bash
# 测试 SSE 端点
curl -N "http://localhost:9091/sse/zk-mcp-com-zkinfo-demo-service-orderservice-1.0.0" \
  -H "Accept: text/event-stream" \
  --max-time 3

# 应该返回:
# event:endpoint
# data:http://localhost:9091/mcp/message?sessionId=xxx
```

或者运行完整测试脚本：

```bash
cd /Users/shine/projects.mcp-router-sse-parent
./zk-mcp-parent/zkInfo/test-sse-endpoints-complete.sh
```

## 预期结果

修复后，以下所有 endpoint 格式都应该正常工作：

1. ✅ `GET /sse?serviceName={serviceName}` - 标准端点
2. ✅ `GET /sse/{projectCode}` - 项目代码
3. ✅ `GET /sse/{projectName}` - 项目名称
4. ✅ `GET /sse/{endpointName}` - 虚拟项目 endpoint 名称
5. ✅ `GET /sse/{virtualProjectId}` - 虚拟项目 ID
6. ✅ `GET /sse/{mcpServiceName}` - MCP 服务名称

## 验证清单

- [ ] 服务重启成功
- [ ] 日志中显示 "Creating multi-endpoint MCP router function"
- [ ] `/sse/{endpoint}` 端点返回 200 而不是 404
- [ ] SSE 连接能够建立
- [ ] `initialize` 请求正确处理
- [ ] `tools/list` 请求返回工具列表
- [ ] `tools/call` 请求正确调用 Dubbo 服务

## 注意事项

1. **必须重启服务**: 路由配置更改需要重启 Spring Boot 应用才能生效
2. **检查日志**: 启动后检查日志中是否有 "Creating multi-endpoint MCP router function" 消息
3. **端口占用**: 确保端口 9091 没有被其他进程占用

