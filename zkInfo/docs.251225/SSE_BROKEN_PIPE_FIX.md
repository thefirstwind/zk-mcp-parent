# SSE Broken Pipe 错误修复

## 问题描述
`zkInfo` 建立 SSE 连接后，调用 `tools/call` 时出现 `Broken pipe` 错误，日志中记录为 `ERROR` 级别，导致日志刷屏。

## 问题分析

### 错误场景
1. **客户端断开连接**：客户端主动断开 SSE 连接
2. **服务端尝试发送数据**：在连接已断开的情况下，服务端仍然尝试发送 SSE 事件
3. **Broken pipe 异常**：操作系统抛出 `IOException: Broken pipe`
4. **错误日志级别过高**：`onError` 回调中使用 `log.error`，导致正常断开连接也被记录为错误

### 错误位置
1. **`SseController.onError` 回调**：第242行和第338行，使用 `log.error` 记录所有错误
2. **心跳发送**：第370行，心跳发送时如果连接已断开，会触发 `Broken pipe`
3. **`McpMessageController.sendSseEventSafe`**：第745行，发送 SSE 事件时如果连接已断开，会触发 `Broken pipe`

## 修复方案

### 1. 改进 `onError` 回调的错误处理

**文件**: `zk-mcp-parent/zkInfo/src/main/java/com/zkinfo/controller/SseController.java`

**修改**:
- 在 `onError` 回调中检查异常类型和消息
- 对于 `Broken pipe`（`IOException`）和 `already completed`（`IllegalStateException`），降级为 `DEBUG` 级别
- 只有真正的错误才记录为 `ERROR` 级别

**代码示例**:
```java
emitter.onError((ex) -> {
    // Broken pipe 和 already completed 是正常的客户端断开情况，降级为 DEBUG
    if (ex instanceof IOException && ex.getMessage() != null && ex.getMessage().contains("Broken pipe")) {
        log.debug("ℹ️ Client disconnected (broken pipe) for session: {}", sessionId);
    } else if (ex instanceof IllegalStateException && ex.getMessage() != null && ex.getMessage().contains("already completed")) {
        log.debug("ℹ️ SSE emitter already completed for session: {}", sessionId);
    } else {
        log.error("SSE connection error for session: {}", sessionId, ex);
    }
    cleanupSession(sessionId);
});
```

**效果**:
- `Broken pipe` 和 `already completed` 不再记录为错误
- 减少日志噪音，提高日志可读性
- 真正的错误仍然会被正确记录

### 2. 现有的错误处理（已正确）

**`McpMessageController.sendSseEventSafe`**:
- 已经正确捕获 `IOException` 和 `IllegalStateException`
- 已经将 `Broken pipe` 记录为 `DEBUG` 级别
- 不需要修改

**`SseController.startHeartbeat`**:
- 已经正确捕获 `IOException` 和 `IllegalStateException`
- 已经将 `Broken pipe` 记录为 `DEBUG` 级别
- 已经正确调用 `cleanupSession` 清理会话
- 不需要修改

## 修复效果

### 修复前
- `Broken pipe` 被记录为 `ERROR` 级别
- 日志中大量错误信息，难以识别真正的问题
- 正常断开连接也被视为错误

### 修复后
- `Broken pipe` 和 `already completed` 降级为 `DEBUG` 级别
- 只有真正的错误才记录为 `ERROR`
- 日志更加清晰，便于问题排查

## 正常断开连接的情况

以下情况是正常的客户端断开连接，不应该记录为错误：

1. **客户端主动关闭连接**
   - 用户关闭浏览器标签页
   - 客户端应用主动断开连接
   - 网络中断

2. **连接超时**
   - 客户端长时间无响应
   - 网络延迟导致超时

3. **连接已完成**
   - SSE 流正常结束
   - 客户端正常接收完所有数据

## 注意事项

1. **会话清理**
   - `onError` 回调会自动调用 `cleanupSession` 清理会话
   - 不需要在 `sendSseEventSafe` 中重复清理

2. **错误处理优先级**
   - `onError` 回调是最高优先级的错误处理
   - `sendSseEventSafe` 和 `startHeartbeat` 中的错误处理是补充

3. **日志级别**
   - `DEBUG`：正常断开连接（`Broken pipe`、`already completed`）
   - `WARN`：可恢复的错误（如 IO 错误但不是 `Broken pipe`）
   - `ERROR`：真正的错误（如未知异常）

## 相关文件
- `zk-mcp-parent/zkInfo/src/main/java/com/zkinfo/controller/SseController.java`
- `zk-mcp-parent/zkInfo/src/main/java/com/zkinfo/controller/McpMessageController.java`

