# SSE 路由修复 V3 - WebMVC Controller 实现

## 问题描述

在 WebMVC 模式下，WebFlux 的 `RouterFunction` 无法正常工作，导致 SSE 端点返回 404 错误。

## 解决方案

采用 **WebMVC Controller** 方式实现 SSE 端点，完全绕过 WebFlux 的路由机制。

## 修复内容

### 1. 创建 `SseController`（WebMVC SSE 端点）

**文件**: `src/main/java/com/zkinfo/controller/SseController.java`

**功能**:
- `GET /sse?serviceName={serviceName}` - 标准 SSE 端点
- `GET /sse/{endpoint}` - 动态 SSE 端点（支持虚拟项目ID/名称、实际项目code/name、MCP服务名称）

**实现要点**:
- 使用 Spring WebMVC 的 `SseEmitter` 替代 WebFlux 的 `ServerSentEvent`
- 通过 `McpSessionManager` 管理 SSE 会话
- 发送 `endpoint` 事件，包含消息端点 URL
- 实现心跳机制（每 15 秒）

### 2. 创建 `McpMessageController`（WebMVC MCP 消息处理）

**文件**: `src/main/java/com/zkinfo/controller/McpMessageController.java`

**功能**:
- `POST /mcp/message?sessionId=xxx` - 处理 MCP 消息（initialize、tools/list、tools/call）

**实现要点**:
- 从 `McpSessionManager` 获取 `SseEmitter`
- 通过 `SseEmitter.send()` 发送响应
- HTTP 响应返回 `202 Accepted`（响应通过 SSE 异步发送）

### 3. 更新 `McpSessionManager`（支持 WebMVC SseEmitter）

**文件**: `src/main/java/com/zkinfo/service/McpSessionManager.java`

**新增方法**:
- `registerSseEmitter(String sessionId, String endpoint, SseEmitter emitter)` - 注册 WebMVC SseEmitter
- `getSseEmitter(String sessionId)` - 获取 WebMVC SseEmitter

**存储结构**:
- `sseEmitterMap: Map<String, SseEmitter>` - 存储 WebMVC 模式的 SseEmitter

## 代码结构

```
zkInfo/
├── src/main/java/com/zkinfo/
│   ├── controller/
│   │   ├── SseController.java          # WebMVC SSE 端点
│   │   └── McpMessageController.java   # WebMVC MCP 消息处理
│   └── service/
│       └── McpSessionManager.java      # 会话管理（支持 WebFlux Sink 和 WebMVC SseEmitter）
```

## 工作流程

### SSE 连接建立

1. 客户端请求: `GET /sse/{endpoint}`
2. `SseController.handleSse()` 解析 endpoint
3. 创建 `SseEmitter` 并生成 `sessionId`
4. 注册到 `McpSessionManager` 和 `SseController.sseEmitterMap`
5. 发送 `endpoint` 事件（包含消息端点 URL）
6. 启动心跳任务

### MCP 消息处理

1. 客户端请求: `POST /mcp/message?sessionId=xxx`（JSON-RPC 消息）
2. `McpMessageController.handleMessage()` 获取 `SseEmitter`
3. 根据 `method` 调用相应处理函数:
   - `initialize` → `handleInitialize()`
   - `tools/list` → `handleToolsList()`
   - `tools/call` → `handleToolCall()`
4. 通过 `SseEmitter.send()` 发送响应
5. HTTP 返回 `202 Accepted`

## 与 WebFlux 的兼容性

- **WebFlux RouterFunction** (`MultiEndpointMcpRouterConfig`) 仍然存在，但优先级较低
- **WebMVC Controller** (`SseController`, `McpMessageController`) 优先处理请求
- 两种模式可以共存，但 WebMVC 模式优先

## 测试验证

### 1. SSE 端点测试

```bash
# 标准 SSE 端点
curl -N -H "Accept: text/event-stream" "http://localhost:9091/sse?serviceName=test"

# 动态 SSE 端点
curl -N -H "Accept: text/event-stream" "http://localhost:9091/sse/test"
```

### 2. MCP 消息测试

```bash
# 1. 建立 SSE 连接，获取 sessionId
SESSION_ID=$(curl -s -N "http://localhost:9091/sse/test" | grep -oP 'sessionId=\K[^"]+' | head -1)

# 2. 发送 initialize 消息
curl -X POST "http://localhost:9091/mcp/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'

# 3. 发送 tools/list 消息
curl -X POST "http://localhost:9091/mcp/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

## 注意事项

1. **ObjectMapper 初始化**: `SseController` 中直接创建 `ObjectMapper` 实例（不使用 Spring 注入，避免循环依赖）

2. **Session 清理**: SSE 连接关闭时，需要同时清理 `SseController.sseEmitterMap` 和 `McpSessionManager` 中的记录

3. **心跳机制**: 心跳任务在 `SseEmitter` 关闭时不会自动停止，需要在 `onCompletion`/`onTimeout`/`onError` 回调中手动停止

4. **Base URL**: 当前硬编码为 `http://127.0.0.1:9091`，后续应从配置中读取

## 后续优化

1. 从配置文件读取 `baseUrl`
2. 优化心跳任务的停止机制
3. 添加 SSE 连接数限制和超时管理
4. 统一 WebFlux 和 WebMVC 两种模式的代码（如果可能）

