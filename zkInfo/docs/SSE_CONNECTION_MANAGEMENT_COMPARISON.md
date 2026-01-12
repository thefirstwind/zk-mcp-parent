# SSE 连接管理机制对比与完善

## 📋 对比总结

### 1. SSE 连接超时时间

| 项目 | 超时时间 | 说明 |
|------|---------|------|
| **mcp-router-v3** | 600秒（10分钟） | 在 `McpSessionService` 中定义 `SSE_TIMEOUT_MS = 600_000` |
| **zkInfo（修改前）** | 1800秒（30分钟） | `SseEmitter` 创建时设置 `30 * 60 * 1000L` |
| **zkInfo（修改后）** | 600秒（10分钟） | ✅ 已统一为 10 分钟，与 mcp-router-v3 保持一致 |

**修改位置**：
- `SseController.java`: 两处 `SseEmitter` 创建处，从 `30 * 60 * 1000L` 改为 `10 * 60 * 1000L`
- `application.yml`: Redis TTL 从 `PT30M` 改为 `PT10M`

### 2. 心跳间隔

| 项目 | 心跳间隔 | 说明 |
|------|---------|------|
| **mcp-router-v3** | 15秒 | `Flux.interval(Duration.ofSeconds(15))` |
| **zkInfo** | 15秒 | ✅ 已对齐，`scheduleAtFixedRate(..., 15, 15, TimeUnit.SECONDS)` |

### 3. 心跳格式

| 项目 | 心跳格式 | 说明 |
|------|---------|------|
| **mcp-router-v3** | `comment("heartbeat")` 或 `event("heartbeat") + data` | 发送心跳事件到客户端 |
| **zkInfo** | 不发送心跳事件，只 `touch session` | 避免客户端报错（某些 MCP 客户端不识别 "heartbeat" 事件） |

**说明**：
- zkInfo 采用更保守的方式，只更新会话活跃时间，不发送心跳事件
- 这样可以避免客户端报错，同时通过 `touch` 机制保持连接活跃
- 两种方式都能达到保持连接的目的

### 4. 会话超时清理机制

| 项目 | 清理机制 | 说明 |
|------|---------|------|
| **mcp-router-v3** | 定期清理超时会话 | `McpSessionService.getSessionOverview()` 中定义超时时间 |
| **zkInfo（修改前）** | 无定期清理机制 | 仅依赖 Redis TTL 自动过期 |
| **zkInfo（修改后）** | ✅ 新增定期清理服务 | `SessionCleanupService` 每 1 分钟清理超时 10 分钟的会话 |

**新增功能**：
- 创建了 `SessionCleanupService.java`
- 每 1 分钟执行一次，清理超过 10 分钟未活跃的 SSE 会话
- 与 mcp-router-v3 的超时时间保持一致

### 5. 会话管理

| 项目 | 会话存储 | TTL |
|------|---------|-----|
| **mcp-router-v3** | Redis | 10 分钟（SSE 会话） |
| **zkInfo（修改前）** | Redis | 30 分钟 |
| **zkInfo（修改后）** | Redis | ✅ 10 分钟（与 mcp-router-v3 保持一致） |

### 6. 连接断开重连

| 项目 | 重连机制 | 说明 |
|------|---------|------|
| **mcp-router-v3** | 客户端重连 | 客户端检测到连接断开后自动重连 |
| **zkInfo** | 客户端重连 | ✅ 与 mcp-router-v3 一致，由客户端实现重连逻辑 |

**说明**：
- 服务器端不主动重连，由客户端检测连接状态并重连
- 服务器端通过心跳和会话清理机制确保连接健康

## ✅ 已完成的改进

### 1. 统一 SSE 连接超时时间
- ✅ 修改 `SseController.java` 中两处 `SseEmitter` 创建，从 30 分钟改为 10 分钟
- ✅ 修改 `application.yml` 中 Redis TTL，从 `PT30M` 改为 `PT10M`

### 2. 新增会话清理服务
- ✅ 创建 `SessionCleanupService.java`
- ✅ 每 1 分钟清理超过 10 分钟未活跃的 SSE 会话
- ✅ 与 mcp-router-v3 的超时时间保持一致

### 3. 完善心跳机制注释
- ✅ 更新 `startHeartbeat` 方法的注释，说明为什么 zkInfo 不发送心跳事件
- ✅ 明确心跳间隔为 15 秒，与 mcp-router-v3 保持一致

## 📝 配置说明

### application.yml

```yaml
mcp:
  session:
    ttl: PT10M  # 10分钟，与 mcp-router-v3 保持一致（SSE 会话超时时间）
```

### SessionCleanupService

```java
// SSE 会话超时时间：10分钟（600秒），与 mcp-router-v3 保持一致
private static final long SSE_TIMEOUT_MS = 600_000;

// 每 1 分钟执行一次，清理超过 10 分钟未活跃的会话
@Scheduled(fixedRate = 60_000, initialDelay = 60_000)
public void cleanupTimeoutSessions()
```

## 🔄 工作流程对比

### mcp-router-v3

```
1. 客户端建立 SSE 连接
2. 服务器创建 SseEmitter（超时 10 分钟）
3. 每 15 秒发送心跳事件（comment 或 event）
4. 每 15 秒 touch session（更新活跃时间）
5. 定期清理超过 10 分钟未活跃的会话
```

### zkInfo（完善后）

```
1. 客户端建立 SSE 连接
2. 服务器创建 SseEmitter（超时 10 分钟）✅
3. 每 15 秒 touch session（更新活跃时间）✅
4. 定期清理超过 10 分钟未活跃的会话 ✅（新增）
5. Redis TTL 10 分钟自动过期 ✅
```

## 🎯 关键差异说明

### 心跳事件发送

**mcp-router-v3**:
- 发送心跳事件：`comment("heartbeat")` 或 `event("heartbeat") + data`
- 客户端可能不识别，但不会报错（comment 会被忽略）

**zkInfo**:
- 不发送心跳事件，只更新会话活跃时间
- 避免客户端报错（某些 MCP 客户端不识别 "heartbeat" 事件）
- 通过 `touch` 机制保持连接活跃

**结论**：两种方式都能达到保持连接的目的，zkInfo 采用更保守的方式。

## 📊 性能对比

| 指标 | mcp-router-v3 | zkInfo（完善后） |
|------|--------------|----------------|
| SSE 连接超时 | 10 分钟 | ✅ 10 分钟 |
| 心跳间隔 | 15 秒 | ✅ 15 秒 |
| 会话清理频率 | 定期 | ✅ 每 1 分钟 |
| Redis TTL | 10 分钟 | ✅ 10 分钟 |
| 心跳事件发送 | 是 | 否（只 touch） |

## 🚀 后续优化建议

1. **监控和统计**：
   - 添加会话连接数统计
   - 添加会话持续时间统计
   - 添加断开原因分类

2. **客户端重连优化**：
   - 提供客户端重连示例代码
   - 添加重连次数限制
   - 添加指数退避策略

3. **健康检查**：
   - 添加连接健康检查端点
   - 添加会话状态查询接口

## 📚 相关文件

- `SseController.java`: SSE 连接管理
- `SessionCleanupService.java`: 会话清理服务（新增）
- `McpSessionManager.java`: 会话管理器
- `SessionRedisRepository.java`: Redis 会话存储
- `application.yml`: 配置文件


