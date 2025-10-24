# DeepSeek 直连 - 快速开始

## 一分钟启动指南

### 1. 设置 API Key

```bash
export DEEPSEEK_API_KEY=sk-your-actual-deepseek-key
```

### 2. 测试连接（可选）

```bash
./test-deepseek-api.sh
```

### 3. 启动应用

```bash
./start.sh
```

### 4. 访问应用

浏览器打开: http://localhost:8081/swagger-ui.html

---

## 完整工作流程

```bash
# 进入项目目录
cd /Users/shine/projects/zk-mcp-parent/mcp-ai-client

# 配置 API Key（三选一）

# 方式 1: 使用配置助手（推荐）
./setup-api-key.sh

# 方式 2: 环境变量
export DEEPSEEK_API_KEY=sk-xxxxxxxxxxxxx

# 方式 3: 编辑配置文件
# 编辑 src/main/resources/application.yml
# 将 api-key 改为你的真实 Key

# 测试 API 连接
./test-deepseek-api.sh

# 启动应用
./start.sh
```

---

## API 测试示例

启动成功后测试：

```bash
# 创建会话
curl -X POST http://localhost:8081/api/chat/session

# 返回示例:
# {"sessionId":"xxx-xxx-xxx","message":"会话创建成功","timestamp":1698765432000}

# 发送消息（替换 SESSION_ID）
curl -X POST "http://localhost:8081/api/chat/session/SESSION_ID/message" \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，请介绍一下你自己"}'
```

---

## 获取 DeepSeek API Key

1. 访问 https://platform.deepseek.com/
2. 注册/登录（支持中国手机号）
3. 进入 **API Keys** 页面
4. 点击 **创建 API Key**
5. 复制生成的 Key（格式：`sk-xxxxx...`）

---

## 常见问题

### Q: 启动时提示 "未设置 DEEPSEEK_API_KEY"？

```bash
# 检查环境变量
echo $DEEPSEEK_API_KEY

# 如果为空，设置它
export DEEPSEEK_API_KEY=sk-your-key
```

### Q: 提示 "Invalid API-key provided"？

1. 检查 Key 是否正确（去掉首尾空格）
2. 访问 DeepSeek 平台确认 Key 是否有效
3. 运行测试脚本验证：`./test-deepseek-api.sh`

### Q: 如何永久保存 API Key？

```bash
# 添加到 shell 配置文件
echo 'export DEEPSEEK_API_KEY=sk-your-key' >> ~/.zshrc
source ~/.zshrc
```

---

## 配置说明

### 核心配置（application.yml）

```yaml
spring:
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com/v1
      chat:
        options:
          model: deepseek-chat
          temperature: 0.7
          max-tokens: 4000
```

### 参数说明

- **api-key**: DeepSeek API Key
- **base-url**: DeepSeek API 端点（固定）
- **model**: 使用的模型（deepseek-chat）
- **temperature**: 0-2，越高越有创造性
- **max-tokens**: 单次最大输出 token 数

---

## 项目结构

```
mcp-ai-client/
├── setup-api-key.sh          # API Key 配置助手
├── test-deepseek-api.sh      # API 连接测试
├── start.sh                   # 启动脚本
├── DEEPSEEK_CONFIG.md         # 详细配置文档
├── QUICK_START_DEEPSEEK.md    # 本文件
└── src/
    └── main/
        ├── java/
        │   └── com/zkinfo/ai/
        │       ├── service/
        │       │   └── AiConversationService.java
        │       └── ...
        └── resources/
            ├── application.yml
            ├── application-dev.yml
            └── application-prod.yml
```

---

## 与之前配置的区别

| 项目 | 阿里云 DashScope | DeepSeek 直连 |
|------|------------------|---------------|
| Maven 依赖 | spring-ai-alibaba-starter | spring-ai-openai-spring-boot-starter |
| API 端点 | dashscope.aliyuncs.com | api.deepseek.com |
| 环境变量 | DASHSCOPE_API_KEY | DEEPSEEK_API_KEY |
| 配置前缀 | spring.ai.dashscope | spring.ai.openai |
| 获取 Key | 阿里云账号 | DeepSeek 账号 |

---

## 下一步

完整文档请参考：
- **详细配置**: [DEEPSEEK_CONFIG.md](./DEEPSEEK_CONFIG.md)
- **使用指南**: [USAGE_GUIDE.md](./USAGE_GUIDE.md)
- **项目总结**: [PROJECT_SUMMARY.md](./PROJECT_SUMMARY.md)

需要帮助？
- DeepSeek 文档: https://platform.deepseek.com/docs
- Spring AI 文档: https://docs.spring.io/spring-ai/reference/

