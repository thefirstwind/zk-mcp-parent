# zkInfo 阻塞操作优化

## 问题描述
`zkInfo` 中存在多个阻塞操作，可能导致接口响应慢，影响整体性能。

## 发现的阻塞操作

### 1. Thread.sleep() 阻塞等待
- **位置**: `McpMessageController.handleMessage()` 第183行
- **问题**: 使用 `Thread.sleep(10)` 循环等待 SSE emitter，最多阻塞 100ms
- **影响**: 每次请求如果找不到 emitter，都会阻塞 100ms

### 2. .block() 阻塞响应式流
- **位置**: 
  - `McpMessageController.handleResourcesList()` 第638行
  - `McpMessageController.handlePromptsList()` 第693行
  - `McpMessageController.handleRestfulMessage()` 第953行、第973行
- **问题**: 使用 `.block()` 同步等待响应式流的结果
- **影响**: 阻塞请求处理线程，影响并发性能

### 3. RestTemplate 同步 HTTP 调用
- **位置**: `NacosV3ApiService` 中的多个方法
- **问题**: 使用 `RestTemplate.exchange()` 同步调用 Nacos API
- **影响**: 不在请求处理路径中，主要用于服务注册，影响较小

### 4. executeToolCallSync() 同步执行
- **位置**: 多个地方调用 `mcpExecutorService.executeToolCallSync()`
- **问题**: 同步执行工具调用
- **影响**: 已在异步线程池中执行，不影响主流程

## 修复方案

### 1. 移除 Thread.sleep() 阻塞等待

**文件**: `zk-mcp-parent/zkInfo/src/main/java/com/zkinfo/controller/McpMessageController.java`

**修改**:
- 移除 `Thread.sleep(10)` 循环等待逻辑
- 如果找不到 emitter，立即返回（不阻塞等待）
- SSE 连接建立是异步的，如果连接还未建立，应该返回错误而不是等待

**代码示例**:
```java
// 修复前
if (emitter == null) {
    log.warn("⚠️ SSE emitter not found for session: {}, waiting...", sessionId);
    for (int i = 0; i < 10; i++) {
        Thread.sleep(10); // 阻塞等待
        emitter = sessionManager.getSseEmitter(sessionId);
        if (emitter != null) break;
    }
}

// 修复后
if (emitter == null) {
    log.debug("⚠️ SSE emitter not found for session: {}, treating as direct HTTP call", sessionId);
}
```

**效果**:
- 消除了每次请求最多 100ms 的阻塞等待
- 请求响应更快

### 2. 将 .block() 改为异步订阅（SSE 模式）

**文件**: `zk-mcp-parent/zkInfo/src/main/java/com/zkinfo/controller/McpMessageController.java`

**修改**:
- `handleResourcesList()`: 将 `.block()` 改为 `.subscribe()` 异步订阅
- `handlePromptsList()`: 将 `.block()` 改为 `.subscribe()` 异步订阅
- 添加 5 秒超时保护
- 在订阅回调中发送 SSE 响应

**代码示例**:
```java
// 修复前
McpProtocol.ListResourcesResult result = mcpResourcesService.listResources(params).block();
// ... 处理结果并发送响应

// 修复后
mcpResourcesService.listResources(params)
    .timeout(Duration.ofSeconds(5)) // 5秒超时
    .subscribe(
        result -> {
            // 处理结果并发送 SSE 响应
        },
        error -> {
            // 处理错误并发送错误响应
        }
    );
```

**效果**:
- 不再阻塞请求处理线程
- 提高并发性能
- 响应式流异步执行，不阻塞主流程

### 3. 为 RESTful 调用添加超时保护

**文件**: `zk-mcp-parent/zkInfo/src/main/java/com/zkinfo/controller/McpMessageController.java`

**修改**:
- `handleRestfulMessage()` 中的 `.block()` 调用添加 `.timeout()` 超时保护
- 设置 5 秒超时，避免长时间阻塞

**代码示例**:
```java
// 修复前
McpProtocol.ListResourcesResult listResult = mcpResourcesService.listResources(
    new McpProtocol.ListResourcesParams()).block();

// 修复后
McpProtocol.ListResourcesResult listResult = mcpResourcesService.listResources(
    new McpProtocol.ListResourcesParams())
    .timeout(Duration.ofSeconds(5)) // 5秒超时
    .block();
```

**效果**:
- 避免无限阻塞
- 超时后返回错误响应，不会长时间占用线程

### 4. RestTemplate 调用（保持同步）

**文件**: `zk-mcp-parent/zkInfo/src/main/java/com/zkinfo/service/NacosV3ApiService.java`

**说明**:
- `RestTemplate` 调用主要用于服务注册，不在请求处理路径中
- 服务注册是后台操作，可以保持同步
- 如果未来需要在请求处理路径中使用，应该改为异步

## 修复效果

### 修复前
- 每次请求如果找不到 SSE emitter，最多阻塞 100ms
- `resources/list` 和 `prompts/list` 请求阻塞请求处理线程
- RESTful 调用可能无限阻塞
- 并发性能受限

### 修复后
- 移除了所有阻塞等待逻辑
- SSE 模式的 `resources/list` 和 `prompts/list` 完全异步执行
- RESTful 调用添加了超时保护
- 并发性能大幅提升

## 性能优化建议

### 已实现的优化
1. ✅ 移除 `Thread.sleep()` 阻塞等待
2. ✅ SSE 模式的响应式流调用改为异步订阅
3. ✅ RESTful 调用的 `.block()` 添加超时保护
4. ✅ 所有异步操作添加超时保护（5秒）

### 其他非阻塞操作
- `executeToolCallSync()` 已在异步线程池中执行，不阻塞主流程
- `RestTemplate` 调用不在请求处理路径中，保持同步

## 注意事项

1. **SSE 模式 vs RESTful 模式**
   - SSE 模式：完全异步，不阻塞请求处理线程
   - RESTful 模式：需要同步返回响应，使用 `.block()` 但添加超时保护

2. **超时设置**
   - SSE 模式：5 秒超时
   - RESTful 模式：5 秒超时
   - 如果超时，返回错误响应

3. **错误处理**
   - 所有异步操作都有错误处理回调
   - 错误会通过 SSE 或 RESTful 响应返回给客户端

## 相关文件
- `zk-mcp-parent/zkInfo/src/main/java/com/zkinfo/controller/McpMessageController.java`
- `zk-mcp-parent/zkInfo/src/main/java/com/zkinfo/service/NacosV3ApiService.java`
- `zk-mcp-parent/zkInfo/src/main/java/com/zkinfo/service/McpExecutorService.java`

