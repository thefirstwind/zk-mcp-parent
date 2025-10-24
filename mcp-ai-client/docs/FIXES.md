# 🔧 MCP AI Client 修复说明

## 问题描述

启动 `mcp-ai-client` 时出现连接错误：

```
Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: 
Connection refused: localhost/[0:0:0:0:0:0:0:1]:8080
```

## 根本原因

**配置错误**：`mcp-ai-client` 配置的 MCP Server 地址和端口不正确。

### 错误配置
```yaml
mcp:
  server:
    url: http://localhost:8080  # ❌ 错误端口
```

### 正确配置
根据 zkInfo MCP Server 的实际配置：

1. **zkInfo 服务端口**: `9091` (在 `zkInfo/src/main/resources/application.yml` 中定义)
2. **MCP 协议端点**: `POST /mcp/jsonrpc`
3. **健康检查端点**: `GET /mcp/health`
4. **服务器信息端点**: `GET /mcp/info`

---

## 🛠️ 已修复的问题

### 1. MCP Server URL 端口错误

**文件**: `mcp-ai-client/src/main/resources/application.yml`

```diff
mcp:
  server:
-   url: http://localhost:8080
+   url: http://localhost:9091
    timeout: 30000
```

**文件**: `mcp-ai-client/src/main/resources/application-dev.yml`

```diff
mcp:
  server:
-   url: http://localhost:8080
+   url: http://localhost:9091
```

### 2. 增强错误处理

**文件**: `mcp-ai-client/src/main/java/com/zkinfo/ai/service/McpClientService.java`

添加了错误回退机制：

```java
public Mono<Map<String, Object>> getServerInfo() {
    return mcpWebClient.get()
            .uri("/mcp/info")
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .doOnSuccess(info -> log.info("MCP Server信息: {}", info))
            .doOnError(error -> log.error("获取Server信息失败", error))
            .onErrorReturn(Map.of("error", "获取Server信息失败"));  // ✅ 新增
}
```

---

## ✅ 验证 zkInfo MCP Server 端点

### 端点列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/mcp/jsonrpc` | MCP JSON-RPC 主端点 |
| GET | `/mcp/health` | 健康检查 |
| GET | `/mcp/info` | 服务器信息 |
| GET | `/mcp/stream/{streamId}` | SSE 流式传输 |
| POST | `/mcp/stream` | 创建流式调用 |
| GET | `/mcp/resources` | 列出资源 |
| GET | `/mcp/prompts` | 列出提示 |
| POST | `/mcp/logging/log` | 记录日志 |

### 支持的 MCP 方法

通过 `/mcp/jsonrpc` 端点处理的 JSON-RPC 方法：

```javascript
// 生命周期
"initialize"
"ping"

// 工具相关
"tools/list"        // ← mcp-ai-client 使用此方法
"tools/call"        // ← mcp-ai-client 使用此方法
"tools/stream"

// 资源相关
"resources/list"
"resources/read"
"resources/subscribe"
"resources/unsubscribe"

// 提示相关
"prompts/list"
"prompts/get"

// 日志
"logging/log"
```

---

## 🧪 测试验证

### 1. 启动 zkInfo MCP Server

```bash
cd zkInfo
mvn spring-boot:run
```

**期望输出**：
```
Tomcat started on port(s): 9091 (http)
```

### 2. 验证 zkInfo 健康状态

```bash
curl http://localhost:9091/mcp/health
```

**期望响应**：
```json
{
  "status": "UP",
  "protocol": "MCP 2024-11-05",
  "capabilities": ["tools", "streaming", "sse", "websocket"],
  "activeSessions": 0,
  "timestamp": 1729675200000
}
```

### 3. 测试 MCP JSON-RPC 调用

```bash
curl -X POST http://localhost:9091/mcp/jsonrpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-1",
    "method": "tools/list",
    "params": {}
  }'
```

**期望响应**：
```json
{
  "jsonrpc": "2.0",
  "id": "test-1",
  "result": {
    "tools": [
      {
        "name": "com.example.service.Method",
        "description": "...",
        "inputSchema": {...}
      }
    ]
  }
}
```

### 4. 启动 mcp-ai-client

```bash
# 设置 API Key
export DASHSCOPE_API_KEY=your-api-key

# 启动客户端
cd mcp-ai-client
./start.sh
```

**期望输出**：
```
✓ DASHSCOPE_API_KEY 已设置
✓ Java 版本: 17
✓ 构建成功
启动 MCP AI Client...
访问地址: http://localhost:8081
```

### 5. 测试 AI Client 健康检查

```bash
curl http://localhost:8081/api/chat/health
```

**期望响应**：
```json
{
  "status": "UP",
  "mcpServer": {
    "status": "UP",
    "protocol": "MCP 2024-11-05",
    "capabilities": ["tools", "streaming", "sse", "websocket"]
  },
  "llm": {
    "provider": "DashScope",
    "model": "deepseek-chat",
    "status": "READY"
  }
}
```

---

## 📊 完整架构图

```
┌─────────────────────┐
│   用户 / AI         │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  mcp-ai-client      │
│  Port: 8081         │
│  ├─ REST API        │
│  ├─ Web UI          │
│  ├─ AI Service      │
│  └─ MCP Client ─────┼──────┐
└─────────────────────┘      │
                             │ HTTP 
                             │ http://localhost:9091
                             │
                             ▼
                    ┌─────────────────────┐
                    │  zkInfo MCP Server  │
                    │  Port: 9091         │
                    │  ├─ /mcp/jsonrpc    │ ← MCP Protocol
                    │  ├─ /mcp/health     │ ← Health Check
                    │  ├─ /mcp/info       │ ← Server Info
                    │  └─ /mcp/stream/*   │ ← SSE Streaming
                    └──────────┬──────────┘
                               │
                               ▼
                      ┌─────────────────┐
                      │  Dubbo Provider │
                      │  ZooKeeper      │
                      └─────────────────┘
```

---

## 🎯 关键配置对照表

| 配置项 | mcp-ai-client | zkInfo MCP Server |
|--------|---------------|-------------------|
| **服务端口** | 8081 | 9091 |
| **MCP 端点** | - | `/mcp/jsonrpc` |
| **健康检查** | `/api/chat/health` | `/mcp/health` |
| **Web 界面** | `/` | `/` (zkInfo管理界面) |
| **API 文档** | `/swagger-ui.html` | `/swagger-ui.html` |
| **协议** | HTTP Client | HTTP Server (MCP) |

---

## 🔍 常见问题

### Q1: 为什么连接 localhost:8080 失败？

**A**: zkInfo MCP Server 运行在端口 `9091`，不是 `8080`。已在配置文件中修复。

### Q2: 如何确认 zkInfo 正在运行？

**A**: 执行以下命令：
```bash
curl http://localhost:9091/mcp/health
```

如果返回 JSON 响应，说明服务正常运行。

### Q3: 为什么使用 `/mcp/jsonrpc` 而不是 `/mcp`？

**A**: 根据 zkInfo 的 `McpController.java` 定义：
```java
@PostMapping(value = "/jsonrpc", ...)
public Mono<McpProtocol.JsonRpcResponse> handleJsonRpc(...)
```

完整路径是 `@RequestMapping("/mcp")` + `/jsonrpc` = `/mcp/jsonrpc`

### Q4: MCP 协议版本是什么？

**A**: zkInfo 实现的是 **MCP 2024-11-05** 版本，这是 Model Context Protocol 的标准版本。

---

## 📝 相关文档

- **zkInfo 配置**: `zkInfo/src/main/resources/application.yml`
- **zkInfo MCP Controller**: `zkInfo/src/main/java/com/zkinfo/controller/McpController.java`
- **zkInfo MCP Protocol**: `zkInfo/src/main/java/com/zkinfo/mcp/McpProtocol.java`
- **zkInfo MCP Service**: `zkInfo/src/main/java/com/zkinfo/service/McpProtocolService.java`
- **AI Client 配置**: `mcp-ai-client/src/main/resources/application.yml`
- **AI Client MCP Service**: `mcp-ai-client/src/main/java/com/zkinfo/ai/service/McpClientService.java`

---

## ✨ 修复总结

| 问题 | 状态 | 修复方式 |
|------|------|----------|
| 端口配置错误 (8080 → 9091) | ✅ 已修复 | 更新配置文件 |
| MCP 端点路径正确 | ✅ 已确认 | `/mcp/jsonrpc` |
| 错误处理优化 | ✅ 已完成 | 添加 `onErrorReturn` |
| 类型转换警告 | ✅ 已修复 | 使用 `ParameterizedTypeReference` |
| 编译成功 | ✅ 通过 | `mvn clean package` |
| 文档更新 | ✅ 完成 | 本文档 |

---

**修复时间**: 2025-10-23  
**修复版本**: 1.0.0  
**验证状态**: ✅ 通过编译，待运行时验证



