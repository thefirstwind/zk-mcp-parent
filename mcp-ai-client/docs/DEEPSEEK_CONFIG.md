# DeepSeek 直连 API 配置指南

本项目已配置为直连 DeepSeek API，无需通过阿里云 DashScope。

## 配置说明

### 1. 获取 DeepSeek API Key

1. 访问 [DeepSeek 平台](https://platform.deepseek.com/)
2. 注册/登录账号
3. 进入 **API Keys** 管理页面
4. 创建新 Key 或复制现有 Key
5. Key 格式：`sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`

### 2. 配置 API Key

#### 方法 1：使用配置助手（推荐）

```bash
cd /Users/shine/projects/zk-mcp-parent/mcp-ai-client
./setup-api-key.sh
```

按照提示输入你的 DeepSeek API Key。

#### 方法 2：手动设置环境变量

```bash
# 临时设置（仅当前终端有效）
export DEEPSEEK_API_KEY=sk-your-actual-key-here

# 永久设置（添加到 shell 配置文件）
echo 'export DEEPSEEK_API_KEY=sk-your-actual-key-here' >> ~/.zshrc
source ~/.zshrc
```

#### 方法 3：直接编辑配置文件

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  ai:
    openai:
      api-key: sk-your-actual-key-here  # 替换为你的真实 Key
      base-url: https://api.deepseek.com/v1
      chat:
        options:
          model: deepseek-chat
          temperature: 0.7
          max-tokens: 4000
```

⚠️ **警告**：如果使用方法 3，请勿将包含真实 API Key 的文件提交到 Git！

### 3. 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                   MCP AI Client                         │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │         Spring AI OpenAI Starter                 │  │
│  │  (OpenAI 兼容接口，支持 DeepSeek)                 │  │
│  └──────────────────────────────────────────────────┘  │
│                          ↓                              │
│              配置 base-url 指向 DeepSeek               │
│                          ↓                              │
│         https://api.deepseek.com/v1/chat/completions   │
└─────────────────────────────────────────────────────────┘
                          ↓
              直接调用 DeepSeek API
          （无需阿里云 DashScope 代理）
```

### 4. 配置参数说明

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `api-key` | DeepSeek API Key | `${DEEPSEEK_API_KEY}` |
| `base-url` | API 基础地址 | `https://api.deepseek.com/v1` |
| `model` | 使用的模型 | `deepseek-chat` |
| `temperature` | 温度参数 (0-2) | `0.7` |
| `max-tokens` | 最大输出 token 数 | `4000` |

### 5. 启动应用

```bash
# 确保已设置环境变量
export DEEPSEEK_API_KEY=sk-your-actual-key-here

# 启动应用
cd /Users/shine/projects/zk-mcp-parent/mcp-ai-client
./start.sh
```

### 6. 验证配置

启动后访问：

- **Swagger UI**: http://localhost:8081/swagger-ui.html
- **健康检查**: http://localhost:8081/api/chat/health

创建会话并测试：

```bash
# 创建会话
SESSION_ID=$(curl -s -X POST http://localhost:8081/api/chat/session | jq -r '.sessionId')

# 发送消息
curl -X POST "http://localhost:8081/api/chat/session/$SESSION_ID/message" \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，请介绍一下你自己"}'
```

### 7. 常见问题

#### Q: 报错 "Invalid API-key provided"？

A: 请检查：
- API Key 是否正确
- 环境变量是否已设置：`echo $DEEPSEEK_API_KEY`
- 配置文件中的占位符是否已替换

#### Q: 如何切换回阿里云 DashScope？

A: 需要修改以下内容：

1. **pom.xml**：
   ```xml
   <!-- 改回 Spring AI Alibaba -->
   <dependency>
       <groupId>com.alibaba.cloud.ai</groupId>
       <artifactId>spring-ai-alibaba-starter</artifactId>
       <version>1.0.0-M3.2</version>
   </dependency>
   ```

2. **application.yml**：
   ```yaml
   spring:
     ai:
       dashscope:
         api-key: ${DASHSCOPE_API_KEY}
         chat:
           options:
             model: deepseek-chat
   ```

#### Q: DeepSeek API 有哪些限制？

A: 请查看 [DeepSeek 官方文档](https://platform.deepseek.com/docs)：
- 免费额度限制
- 请求频率限制
- Token 使用限制

### 8. 安全最佳实践

✅ **推荐做法**：
- 使用环境变量存储 API Key
- 将 `.env` 文件加入 `.gitignore`
- 定期轮换 API Key
- 不要在日志中打印完整 Key

❌ **避免做法**：
- 不要硬编码 API Key 到代码
- 不要将 API Key 提交到 Git
- 不要在公共场合分享 API Key
- 不要使用明文方式存储 Key

### 9. 依赖说明

本项目使用：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.0.0-M3</version>
</dependency>
```

该依赖支持所有 OpenAI 兼容的 API，包括：
- OpenAI 官方 API
- DeepSeek API
- Azure OpenAI
- 其他兼容 OpenAI 协议的服务

### 10. 性能优化

配置建议：

```yaml
spring:
  ai:
    openai:
      chat:
        options:
          # 开发环境：更高创造性
          temperature: 0.7
          
          # 生产环境：更稳定输出
          # temperature: 0.5
          
          # 调整最大 token 避免超额
          max-tokens: 4000
          
          # 启用流式输出（可选）
          # stream: true
```

---

**需要帮助？**

- DeepSeek 官方文档：https://platform.deepseek.com/docs
- 项目 Issues：请在项目仓库提交 Issue
- Spring AI 文档：https://docs.spring.io/spring-ai/reference/

