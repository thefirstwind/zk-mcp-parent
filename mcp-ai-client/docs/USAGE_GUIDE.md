# MCP AI Client 使用指南

## 概述

MCP AI Client 是一个智能对话系统，它使用 DeepSeek LLM 理解用户的自然语言请求，并自动调用 zkInfo MCP Server 提供的工具来完成任务。

## 快速开始

### 1. 环境准备

确保已启动 zkInfo MCP Server：

```bash
cd ../zkInfo
mvn spring-boot:run
```

### 2. 配置 API Key

获取 DashScope API Key（用于调用 DeepSeek）：

1. 访问 [阿里云 DashScope](https://dashscope.aliyun.com/)
2. 注册并获取 API Key
3. 设置环境变量：

```bash
export DASHSCOPE_API_KEY=your-api-key-here
```

### 3. 启动服务

使用启动脚本：

```bash
./start.sh
```

或手动启动：

```bash
mvn spring-boot:run
```

### 4. 访问界面

打开浏览器访问：http://localhost:8081

## 使用示例

### Web 界面对话

1. 打开 http://localhost:8081
2. 等待会话初始化（会自动创建）
3. 在输入框中输入问题，例如：

**示例对话 1: 查询服务**

```
你: 有哪些 Dubbo 服务注册在 ZooKeeper 上？
AI: 让我查询一下... [自动调用 dubbo_list_services]
    找到以下 Dubbo 服务：
    1. com.example.UserService
    2. com.example.OrderService
    3. ...
```

**示例对话 2: 服务详情**

```
你: UserService 的详细信息是什么？
AI: 让我获取详细信息... [自动调用 dubbo_service_metadata]
    UserService 详细信息：
    - 接口: com.example.UserService
    - 版本: 1.0.0
    - 提供者: 192.168.1.100:20880
    - 方法: getUserById, createUser, updateUser
```

**示例对话 3: 健康检查**

```
你: 这些服务健康吗？
AI: 让我检查服务状态... [自动调用 dubbo_check_health]
    服务健康状态：
    - UserService: 健康 ✓
    - OrderService: 健康 ✓
    所有服务运行正常
```

### REST API 调用

#### 创建会话

```bash
curl -X POST http://localhost:8081/api/chat/session
```

响应：
```json
{
  "sessionId": "abc123...",
  "message": "会话创建成功",
  "timestamp": 1698765432000
}
```

#### 发送消息

```bash
curl -X POST http://localhost:8081/api/chat/session/abc123.../message \
  -H "Content-Type: application/json" \
  -d '{
    "message": "查询所有 Dubbo 服务"
  }'
```

响应：
```json
{
  "sessionId": "abc123...",
  "userMessage": "查询所有 Dubbo 服务",
  "aiResponse": "执行工具 dubbo_list_services 的结果：\n\n找到 5 个服务...",
  "timestamp": 1698765433000
}
```

## 功能特性

### 1. 智能意图识别

AI 会自动理解你的意图，无需记忆复杂的命令：

- ❌ 传统方式: `curl -X POST ... -d '{"method": "dubbo_list_services", ...}'`
- ✅ AI 方式: "有哪些服务？"

### 2. 上下文理解

AI 会记住对话历史，理解上下文：

```
你: 查询所有服务
AI: [列出服务]

你: 第一个服务的详细信息是什么？
AI: [自动知道指的是之前列表中的第一个服务]
```

### 3. 多轮对话

支持复杂的多轮对话：

```
你: 我想了解 UserService
AI: UserService 的基本信息是...

你: 它有哪些方法？
AI: [展示方法列表]

你: 调用 getUserById 需要什么参数？
AI: [展示参数信息]
```

### 4. 自动工具选择

AI 会根据问题自动选择合适的工具：

| 用户问题 | 自动调用的工具 |
|---------|--------------|
| "有哪些服务？" | `dubbo_list_services` |
| "服务健康吗？" | `dubbo_check_health` |
| "UserService 的详情？" | `dubbo_service_metadata` |
| "查询消费者信息" | `dubbo_list_consumers` |

## 可用工具

MCP AI Client 会自动从 zkInfo MCP Server 获取可用工具。常见工具包括：

1. **dubbo_list_services** - 列出所有 Dubbo 服务
2. **dubbo_service_metadata** - 获取服务元数据
3. **dubbo_list_providers** - 列出服务提供者
4. **dubbo_list_consumers** - 列出服务消费者
5. **dubbo_check_health** - 检查服务健康状态

查看完整工具列表：

```bash
curl http://localhost:8081/api/chat/session/{sessionId}/tools
```

## 提示技巧

### 好的问题示例

✅ "有哪些 Dubbo 服务？"
✅ "UserService 的详细信息"
✅ "检查所有服务的健康状态"
✅ "谁在消费 OrderService？"

### 避免的问题示例

❌ "帮我" (太模糊)
❌ "查询" (不明确查询什么)
❌ "服务" (没有具体动作)

### 最佳实践

1. **明确目标**: 清楚说明你想知道什么
2. **具体名称**: 提到具体的服务名称
3. **分步提问**: 复杂问题可以分成多个简单问题
4. **查看工具**: 在 Web 界面查看可用工具列表

## 配置选项

### application.yml

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: deepseek-chat    # 可选: qwen-plus, qwen-max
          temperature: 0.7         # 0-1, 控制回答的创造性
          max-tokens: 2000        # 最大输出长度

mcp:
  server:
    url: http://localhost:9091   # MCP Server 地址
    timeout: 30000                # 超时时间（毫秒）
```

### 环境变量

- `DASHSCOPE_API_KEY` - DashScope API Key（必需）
- `MCP_SERVER_URL` - MCP Server 地址（可选）
- `SPRING_PROFILES_ACTIVE` - 激活的配置文件（dev/prod）

## 监控和调试

### 查看日志

```bash
tail -f logs/mcp-ai-client.log
```

### 健康检查

```bash
curl http://localhost:8081/api/chat/health
```

### Swagger UI

访问 http://localhost:8081/swagger-ui.html 查看完整 API 文档

### 调试模式

启用 DEBUG 日志：

```yaml
logging:
  level:
    com.zkinfo: DEBUG
    org.springframework.ai: DEBUG
```

## 常见问题

### Q: AI 没有调用工具？

A: 可能是问题不够明确，尝试更具体的描述，例如：
- ❌ "查询一下"
- ✅ "查询所有 Dubbo 服务"

### Q: 响应很慢？

A: 可能原因：
1. LLM 响应较慢（正常，通常 2-5 秒）
2. MCP Server 未启动或响应慢
3. 网络问题

### Q: 显示 "工具列表初始化中"？

A: 启动后需要 2-3 秒加载工具列表，请稍等片刻

### Q: 如何重置会话？

A: 刷新页面会自动创建新会话，或调用：

```bash
curl -X DELETE http://localhost:8081/api/chat/session/{sessionId}
```

## 进阶使用

### 自定义提示词

修改 `AiConversationService.java` 中的 `buildSystemPrompt` 方法

### 扩展工具处理

在 `AiConversationService.java` 中添加自定义工具处理逻辑

### 持久化会话

默认会话存储在内存中，可以扩展为 Redis 等持久化存储

## 示例场景

### 场景 1: 服务巡检

```
你: 检查所有服务的健康状态
AI: [调用 dubbo_check_health]
    所有服务健康状态：...

你: 有没有异常的服务？
AI: 根据检查结果，所有服务都正常运行
```

### 场景 2: 问题排查

```
你: UserService 有问题，帮我看看
AI: [调用 dubbo_service_metadata]
    UserService 详细信息：...

你: 有哪些消费者在调用它？
AI: [调用 dubbo_list_consumers]
    消费者列表：...
```

### 场景 3: 服务发现

```
你: 我想找一个用户相关的服务
AI: [调用 dubbo_list_services]
    找到以下用户相关服务：
    - com.example.UserService
    - com.example.UserAuthService
    
你: UserService 提供哪些方法？
AI: [调用 dubbo_service_metadata]
    UserService 方法列表：...
```

## 获取帮助

- 查看日志: `logs/mcp-ai-client.log`
- API 文档: http://localhost:8081/swagger-ui.html
- 项目 README: `README.md`

## 下一步

1. 探索更多对话场景
2. 查看 API 文档了解更多功能
3. 自定义配置和提示词
4. 集成到您的系统中

