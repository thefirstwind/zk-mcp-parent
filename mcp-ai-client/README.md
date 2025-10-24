# MCP AI Client

基于 Spring AI Alibaba 和 DeepSeek 的智能 MCP 客户端，通过自然语言对话调用 zkInfo MCP Server 服务。

## 功能特性

- 🤖 **AI 驱动**: 使用 DeepSeek LLM 理解用户意图
- 🔧 **工具调用**: 自动识别并调用 MCP Server 的工具
- 💬 **对话管理**: 支持多会话并保持对话历史
- 🔌 **MCP 协议**: 完整实现 MCP 协议客户端
- 📊 **可观测性**: 集成 Actuator 和 Swagger UI

## 技术栈

- Spring Boot 3.2.0
- Spring AI Alibaba 1.0.0-M3.2
- DeepSeek LLM (via DashScope)
- WebFlux (响应式编程)
- SpringDoc OpenAPI 3

## 快速开始

### 1. 前置条件

- JDK 17+
- Maven 3.6+
- 运行中的 zkInfo MCP Server (默认端口 9091)
- DashScope API Key (用于调用 DeepSeek)

### 2. 配置

#### 设置 API Key

```bash
export DASHSCOPE_API_KEY=your-dashscope-api-key
```

或在 `application.yml` 中配置：

```yaml
spring:
  ai:
    dashscope:
      api-key: your-api-key-here
```

#### 配置 MCP Server 地址

```yaml
mcp:
  server:
    url: http://localhost:9091
    timeout: 30000
```

### 3. 构建和运行

```bash
# 构建项目
mvn clean package

# 运行应用
java -jar target/mcp-ai-client-1.0.0.jar

# 或使用 Maven 运行
mvn spring-boot:run
```

应用将在 `http://localhost:8081` 启动。

### 4. 访问接口

- **Swagger UI**: http://localhost:8081/swagger-ui.html
- **API文档**: http://localhost:8081/api-docs
- **健康检查**: http://localhost:8081/api/chat/health

## 使用示例

### 1. 创建会话

```bash
curl -X POST http://localhost:8081/api/chat/session
```

响应：
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "会话创建成功",
  "timestamp": 1698765432000
}
```

### 2. 发送消息

```bash
curl -X POST http://localhost:8081/api/chat/session/{sessionId}/message \
  -H "Content-Type: application/json" \
  -d '{"message": "查询所有的 Dubbo 服务"}'
```

响应：
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "userMessage": "查询所有的 Dubbo 服务",
  "aiResponse": "执行工具 dubbo_list_services 的结果：\n\n找到 5 个 Dubbo 服务...",
  "timestamp": 1698765433000
}
```

### 3. 对话示例

**用户**: "有哪些服务注册在 ZooKeeper 上？"

**AI**: 让我查询一下注册的服务... *(自动调用 dubbo_list_services 工具)*

**用户**: "这些服务的健康状态如何？"

**AI**: 我来检查服务健康状态... *(自动调用 dubbo_check_health 工具)*

**用户**: "告诉我 UserService 的详细信息"

**AI**: 让我获取 UserService 的详细信息... *(自动调用 dubbo_service_metadata 工具)*

### 4. 获取会话工具

```bash
curl http://localhost:8081/api/chat/session/{sessionId}/tools
```

### 5. 查看会话历史

```bash
curl http://localhost:8081/api/chat/session/{sessionId}/history
```

### 6. 清除会话

```bash
curl -X DELETE http://localhost:8081/api/chat/session/{sessionId}
```

## API 文档

### 核心接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/session` | 创建新会话 |
| POST | `/api/chat/session/{sessionId}/message` | 发送消息 |
| GET | `/api/chat/session/{sessionId}/history` | 获取历史 |
| GET | `/api/chat/session/{sessionId}/tools` | 获取可用工具 |
| DELETE | `/api/chat/session/{sessionId}` | 清除会话 |
| GET | `/api/chat/mcp/info` | 获取 MCP Server 信息 |
| GET | `/api/chat/health` | 健康检查 |

## 工作原理

### 架构流程

```
用户 → AI Client → LLM (DeepSeek) → 分析意图 → 调用 MCP 工具 → zkInfo MCP Server → ZooKeeper/Dubbo
                      ↓                           ↑
                  对话历史                    工具执行结果
```

### 处理流程

1. **会话初始化**: 创建会话并加载可用的 MCP 工具列表
2. **消息接收**: 接收用户的自然语言消息
3. **LLM 分析**: DeepSeek 分析用户意图，决定是否需要调用工具
4. **工具调用**: 如果需要，自动调用相应的 MCP 工具
5. **结果返回**: 将工具执行结果整合到对话中返回给用户

### 关键组件

- **McpClientService**: 负责与 zkInfo MCP Server 通信
- **AiConversationService**: 管理对话流程和 LLM 交互
- **AiChatController**: 提供 REST API 接口
- **ChatMemory**: 维护对话历史上下文

## 配置说明

### Spring AI 配置

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: deepseek-chat      # LLM 模型
          temperature: 0.7           # 创造性（0-1）
          max-tokens: 2000          # 最大输出长度
```

### MCP Client 配置

```yaml
mcp:
  server:
    url: http://localhost:9091     # MCP Server 地址
    timeout: 30000                  # 超时时间（毫秒）
```

## 开发指南

### 添加自定义工具处理

可以在 `AiConversationService` 中扩展工具调用逻辑：

```java
@Service
public class AiConversationService {
    
    // 自定义工具调用处理
    private String handleCustomTool(String toolName, Map<String, Object> arguments) {
        // 实现自定义逻辑
        return "工具执行结果";
    }
}
```

### 自定义提示词

修改 `buildSystemPrompt` 方法来定制系统提示词：

```java
private String buildSystemPrompt(List<McpProtocol.Tool> tools) {
    // 自定义提示词逻辑
    return "你是一个...";
}
```

## 监控和日志

### 健康检查

```bash
curl http://localhost:8081/actuator/health
```

### 日志查看

日志文件位置: `logs/mcp-ai-client.log`

```bash
tail -f logs/mcp-ai-client.log
```

### Metrics

```bash
curl http://localhost:8081/actuator/metrics
```

## 常见问题

### Q: 如何获取 DashScope API Key？

A: 访问阿里云 DashScope 控制台申请 API Key。

### Q: 支持哪些 LLM 模型？

A: 目前配置为 DeepSeek，也可以配置为其他通过 DashScope 支持的模型（如 Qwen）。

### Q: 如何扩展支持更多 MCP 工具？

A: MCP 工具列表会自动从 zkInfo MCP Server 获取，无需手动配置。

### Q: 对话历史保存在哪里？

A: 当前使用内存存储，重启后会丢失。可以扩展为使用 Redis 等持久化存储。

## 许可证

MIT License

## 相关项目

- [zkInfo MCP Server](../zkInfo) - ZooKeeper/Dubbo MCP 服务端
- [Spring AI Alibaba](https://github.com/alibaba/spring-ai-alibaba)

