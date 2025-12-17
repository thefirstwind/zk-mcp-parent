# SSE 路由修复说明 V2

## 问题描述

SSE 端点返回 404 错误，即使日志显示 "Creating multi-endpoint MCP router function" 已创建。

## 根本原因

在 Spring Boot 中，当同时使用 WebMVC (`spring-boot-starter-web`) 和 WebFlux (`spring-boot-starter-webflux`) 时：
1. 默认使用 WebMVC 模式（`web-application-type: servlet`）
2. `RouterFunction` 是 WebFlux 的功能，需要 WebFlux 的 `DispatcherHandler` 来处理
3. WebFlux 的 `DispatcherHandler` 可能没有被正确初始化

## 修复内容

### 1. WebFluxConfig.java

添加了一个占位符 `RouterFunction` Bean，确保 WebFlux 基础设施被初始化：

```java
@Bean
public RouterFunction<ServerResponse> webFluxRouterFunction() {
    log.info("Registering WebFlux RouterFunction infrastructure");
    return RouterFunctions.route()
            .GET("/health-check-webflux", request -> 
                ServerResponse.ok().bodyValue("WebFlux is enabled"))
            .build();
}
```

### 2. application.yml

添加了 `webflux.enabled: true` 配置：

```yaml
webflux:
  base-path: /
  enabled: true
```

### 3. MultiEndpointMcpRouterConfig.java

已添加：
- `@Order(HIGHEST_PRECEDENCE)` - 确保路由优先级
- `@ConditionalOnMissingBean(name = "mcpRouterFunction")` - 避免 Bean 冲突

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

### 步骤 3: 验证 WebFlux 是否启用

```bash
# 测试 WebFlux 健康检查端点
curl http://localhost:9091/health-check-webflux

# 应该返回: "WebFlux is enabled"
```

### 步骤 4: 验证 SSE 端点

```bash
# 测试 SSE 端点
curl -N "http://localhost:9091/sse/zk-mcp-com-zkinfo-demo-service-orderservice-1.0.0" \
  -H "Accept: text/event-stream" \
  --max-time 3

# 应该返回:
# event:endpoint
# data:http://localhost:9091/mcp/message?sessionId=xxx
```

## 如果仍然失败

如果修复后仍然返回 404，可能需要：

### 方案 A: 使用 WebMVC Controller 包装

创建一个 WebMVC Controller 来转发请求到 WebFlux：

```java
@RestController
public class SseController {
    
    @Autowired
    private WebClient webClient;
    
    @GetMapping(value = "/sse/{endpoint}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<String>>> sse(@PathVariable String endpoint) {
        // 转发到 WebFlux handler
    }
}
```

### 方案 B: 完全使用 WebFlux

移除 `spring-boot-starter-web`，只使用 `spring-boot-starter-webflux`：

```xml
<!-- 移除 -->
<!-- <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency> -->

<!-- 保留 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

并修改 `application.yml`：

```yaml
spring:
  main:
    web-application-type: reactive  # 改为 reactive
```

## 验证清单

- [ ] 编译成功
- [ ] 服务启动成功
- [ ] 日志中显示 "Registering WebFlux RouterFunction infrastructure"
- [ ] 日志中显示 "Creating multi-endpoint MCP router function"
- [ ] `/health-check-webflux` 端点返回 "WebFlux is enabled"
- [ ] `/sse/{endpoint}` 端点返回 200 而不是 404
- [ ] SSE 连接能够建立
- [ ] `initialize` 请求正确处理
- [ ] `tools/list` 请求返回工具列表

## 调试命令

```bash
# 检查所有注册的路由
curl http://localhost:9091/actuator/mappings | jq '.contexts.application.mappings'

# 检查 WebFlux 是否启用
curl http://localhost:9091/health-check-webflux

# 测试 SSE 端点
curl -v "http://localhost:9091/sse/test" -H "Accept: text/event-stream"
```

