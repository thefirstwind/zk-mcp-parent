# MCP AI Client 项目说明

## 项目概述

`mcp-ai-client` 是一个智能的 MCP（Model Context Protocol）客户端，它集成了 Spring AI Alibaba 和 DeepSeek LLM，能够通过自然语言对话的方式调用 zkInfo MCP Server 提供的工具和服务。

## 核心特性

### 1. 🤖 AI 驱动的交互
- 使用 DeepSeek LLM 理解用户的自然语言输入
- 无需记忆复杂的 API 调用格式
- 智能分析用户意图并自动选择合适的工具

### 2. 🔧 自动工具调用
- 自动从 zkInfo MCP Server 获取可用工具列表
- 根据用户问题智能选择并调用工具
- 将工具执行结果整合到对话中返回

### 3. 💬 会话管理
- 支持多会话并发
- 保持对话历史和上下文
- 支持上下文理解和多轮对话

### 4. 🎨 友好的用户界面
- 现代化的 Web 界面
- 实时对话展示
- 工具列表可视化

## 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                         用户                                │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    Web 界面 / REST API                       │
│                  (AiChatController)                          │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│               AiConversationService                          │
│  ┌───────────────────┐      ┌──────────────────┐           │
│  │   ChatMemory      │      │   System Prompt   │           │
│  │  (对话历史)       │      │   (工具信息)      │           │
│  └───────────────────┘      └──────────────────┘           │
└────────────────────────┬────────────────────────────────────┘
                         │
                    ┌────┴────┐
                    ▼         ▼
         ┌──────────────┐  ┌──────────────────┐
         │  DeepSeek    │  │ McpClientService │
         │    LLM       │  │  (MCP 调用)      │
         └──────────────┘  └─────────┬────────┘
                                     │
                                     ▼
                          ┌──────────────────┐
                          │  zkInfo MCP      │
                          │    Server        │
                          └─────────┬────────┘
                                    │
                          ┌─────────┴────────┐
                          ▼                  ▼
                    ┌──────────┐      ┌──────────┐
                    │ZooKeeper │      │  Dubbo   │
                    └──────────┘      └──────────┘
```

## 项目结构

```
mcp-ai-client/
├── src/
│   ├── main/
│   │   ├── java/com/zkinfo/ai/
│   │   │   ├── McpAiClientApplication.java      # 主应用
│   │   │   ├── config/
│   │   │   │   └── McpClientConfig.java         # 配置
│   │   │   ├── controller/
│   │   │   │   └── AiChatController.java        # REST API
│   │   │   ├── model/
│   │   │   │   └── McpProtocol.java             # MCP 数据模型
│   │   │   └── service/
│   │   │       ├── McpClientService.java        # MCP 客户端
│   │   │       └── AiConversationService.java   # AI 对话
│   │   └── resources/
│   │       ├── application.yml                   # 主配置
│   │       ├── application-dev.yml              # 开发配置
│   │       ├── application-prod.yml             # 生产配置
│   │       └── static/
│   │           └── index.html                   # Web 界面
│   └── test/
│       └── java/com/zkinfo/ai/
│           └── McpAiClientApplicationTests.java
├── pom.xml                                      # Maven 配置
├── README.md                                    # 项目文档
├── USAGE_GUIDE.md                               # 使用指南
└── start.sh                                     # 启动脚本
```

## 核心组件说明

### 1. McpClientService

负责与 zkInfo MCP Server 通信：

- `listTools()` - 获取可用工具列表
- `callTool()` - 调用指定的 MCP 工具
- `getServerInfo()` - 获取服务器信息
- `healthCheck()` - 健康检查

### 2. AiConversationService

管理 AI 对话流程：

- `createSession()` - 创建新会话
- `chat()` - 处理用户消息
- `buildSystemPrompt()` - 构建包含工具信息的系统提示词
- `processAiResponse()` - 解析 AI 响应并执行工具调用

### 3. AiChatController

提供 REST API 接口：

- `POST /api/chat/session` - 创建会话
- `POST /api/chat/session/{sessionId}/message` - 发送消息
- `GET /api/chat/session/{sessionId}/history` - 获取历史
- `GET /api/chat/session/{sessionId}/tools` - 获取工具
- `DELETE /api/chat/session/{sessionId}` - 清除会话

## 工作流程

### 1. 初始化流程

```
1. 用户访问 Web 界面
   ↓
2. 前端调用 POST /api/chat/session 创建会话
   ↓
3. 后端创建 sessionId 并异步加载工具列表
   ↓
4. 调用 mcpClientService.listTools() 获取 MCP 工具
   ↓
5. 工具列表存储在 sessionTools 中
   ↓
6. 返回 sessionId 给前端
```

### 2. 对话流程

```
1. 用户输入: "有哪些 Dubbo 服务？"
   ↓
2. 前端调用 POST /api/chat/session/{sessionId}/message
   ↓
3. AiConversationService.chat() 处理
   ↓
4. 构建包含工具信息的 System Prompt
   ↓
5. 调用 DeepSeek LLM 分析用户意图
   ↓
6. LLM 返回: "TOOL_CALL: {"tool": "dubbo_list_services", "arguments": {}}"
   ↓
7. processAiResponse() 解析工具调用
   ↓
8. 调用 mcpClientService.callTool("dubbo_list_services", {})
   ↓
9. MCP Client 向 zkInfo Server 发送 JSON-RPC 请求
   ↓
10. zkInfo Server 执行工具并返回结果
   ↓
11. 结果整合到对话中返回给用户
```

## 配置说明

### Spring AI Alibaba 配置

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}  # DashScope API Key
      chat:
        options:
          model: deepseek-chat         # 模型选择
          temperature: 0.7             # 温度参数 (0-1)
          max-tokens: 2000            # 最大输出长度
```

**参数说明：**

- `model`: 可选 `deepseek-chat`, `qwen-plus`, `qwen-max` 等
- `temperature`: 控制输出的随机性，0 = 确定性，1 = 最大创造性
- `max-tokens`: 限制单次输出的最大 token 数量

### MCP Server 配置

```yaml
mcp:
  server:
    url: http://localhost:8080      # zkInfo MCP Server 地址
    timeout: 30000                   # 超时时间（毫秒）
```

## 部署指南

### 开发环境

```bash
# 1. 设置 API Key
export DASHSCOPE_API_KEY=your-api-key

# 2. 确保 zkInfo MCP Server 已启动
cd ../zkInfo
mvn spring-boot:run

# 3. 启动 AI Client
cd ../mcp-ai-client
./start.sh
```

### 生产环境

```bash
# 1. 构建 JAR 包
mvn clean package -DskipTests

# 2. 运行
export DASHSCOPE_API_KEY=your-api-key
export MCP_SERVER_URL=http://your-mcp-server:8080

java -jar target/mcp-ai-client-1.0.0.jar \
  --spring.profiles.active=prod \
  --server.port=8081
```

### Docker 部署（可选）

创建 `Dockerfile`:

```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/mcp-ai-client-1.0.0.jar app.jar

ENV DASHSCOPE_API_KEY=""
ENV MCP_SERVER_URL="http://localhost:8080"

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
```

构建和运行：

```bash
docker build -t mcp-ai-client .
docker run -d \
  -p 8081:8081 \
  -e DASHSCOPE_API_KEY=your-api-key \
  -e MCP_SERVER_URL=http://mcp-server:8080 \
  mcp-ai-client
```

## API 使用示例

### cURL 示例

```bash
# 创建会话
SESSION_ID=$(curl -s -X POST http://localhost:8081/api/chat/session | jq -r '.sessionId')

# 发送消息
curl -X POST "http://localhost:8081/api/chat/session/$SESSION_ID/message" \
  -H "Content-Type: application/json" \
  -d '{"message": "查询所有 Dubbo 服务"}'

# 获取历史
curl "http://localhost:8081/api/chat/session/$SESSION_ID/history"

# 获取工具
curl "http://localhost:8081/api/chat/session/$SESSION_ID/tools"
```

### JavaScript 示例

```javascript
// 创建会话
const session = await fetch('/api/chat/session', { method: 'POST' })
  .then(r => r.json());

// 发送消息
const response = await fetch(`/api/chat/session/${session.sessionId}/message`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ message: '有哪些服务？' })
}).then(r => r.json());

console.log(response.aiResponse);
```

### Python 示例

```python
import requests

# 创建会话
session = requests.post('http://localhost:8081/api/chat/session').json()
session_id = session['sessionId']

# 发送消息
response = requests.post(
    f'http://localhost:8081/api/chat/session/{session_id}/message',
    json={'message': '查询所有 Dubbo 服务'}
).json()

print(response['aiResponse'])
```

## 扩展开发

### 添加自定义工具处理逻辑

在 `AiConversationService.java` 中：

```java
private String processAiResponse(String aiResponse) {
    // 现有逻辑...
    
    // 添加自定义处理
    if (toolName.equals("custom_tool")) {
        return handleCustomTool(arguments);
    }
    
    // ...
}

private String handleCustomTool(Map<String, Object> arguments) {
    // 实现自定义逻辑
    return "自定义处理结果";
}
```

### 自定义提示词模板

修改 `buildSystemPrompt` 方法：

```java
private String buildSystemPrompt(List<McpProtocol.Tool> tools) {
    StringBuilder sb = new StringBuilder();
    sb.append("你是一个专业的系统管理助手...\n");
    
    // 添加自定义指令
    sb.append("重要规则：\n");
    sb.append("1. 始终保持专业和礼貌\n");
    sb.append("2. 如果不确定，请明确告知用户\n");
    
    // 工具信息...
    
    return sb.toString();
}
```

### 集成其他 LLM

修改 `application.yml`:

```yaml
spring:
  ai:
    dashscope:
      chat:
        options:
          model: qwen-max  # 切换到千问模型
```

## 监控和调试

### 日志配置

```yaml
logging:
  level:
    com.zkinfo: DEBUG              # AI Client 日志
    org.springframework.ai: DEBUG  # Spring AI 日志
    org.springframework.web: INFO
```

### 查看实时日志

```bash
tail -f logs/mcp-ai-client.log | grep -E "(AI|MCP|TOOL)"
```

### 性能监控

访问 Actuator 端点：

- http://localhost:8081/actuator/health
- http://localhost:8081/actuator/metrics
- http://localhost:8081/actuator/info

## 常见问题

### 1. DeepSeek 响应慢

**原因**: LLM API 调用通常需要 2-5 秒

**解决方案**:
- 使用流式响应（future enhancement）
- 调整 `max-tokens` 参数
- 考虑使用更快的模型

### 2. 工具调用解析失败

**原因**: LLM 返回格式不符合预期

**解决方案**:
- 优化 System Prompt 中的格式说明
- 添加更健壮的 JSON 解析
- 使用 Function Calling 功能（future enhancement）

### 3. 会话丢失

**原因**: 使用内存存储，重启后会丢失

**解决方案**:
- 扩展为 Redis 存储
- 实现会话持久化

## 未来增强

- [ ] 流式响应支持（Server-Sent Events）
- [ ] Function Calling 集成
- [ ] 会话持久化（Redis）
- [ ] 多模态支持（图片、文件）
- [ ] 工具执行历史记录
- [ ] 用户认证和权限管理
- [ ] 批量操作支持
- [ ] 更智能的错误处理和重试

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

项目维护者: [您的信息]



