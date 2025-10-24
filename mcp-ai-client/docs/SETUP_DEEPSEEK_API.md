# DeepSeek API 快速设置指南

## 🎯 一键设置（推荐）

### Step 1: 获取 API Key

访问 [DeepSeek Platform](https://platform.deepseek.com/) 并创建 API Key。

### Step 2: 设置环境变量

```bash
# 设置环境变量（替换为你的真实 API Key）
export DEEPSEEK_API_KEY=sk-your-real-deepseek-api-key-here

# 验证设置
echo $DEEPSEEK_API_KEY
```

### Step 3: 重启应用

```bash
# 停止当前运行的应用
lsof -ti:8081 | xargs kill -9 2>/dev/null

# 启动应用
mvn spring-boot:run
```

### Step 4: 测试

等待应用启动（约 10-15 秒），然后运行：

```bash
./test-deepseek-integration.sh
```

---

## 📝 永久设置（推荐用于开发环境）

### 对于 zsh（macOS 默认）

```bash
echo 'export DEEPSEEK_API_KEY=sk-your-real-deepseek-api-key-here' >> ~/.zshrc
source ~/.zshrc
```

### 对于 bash

```bash
echo 'export DEEPSEEK_API_KEY=sk-your-real-deepseek-api-key-here' >> ~/.bashrc
source ~/.bashrc
```

### 对于 fish

```bash
echo 'set -gx DEEPSEEK_API_KEY sk-your-real-deepseek-api-key-here' >> ~/.config/fish/config.fish
source ~/.config/fish/config.fish
```

---

## 🐳 Docker 环境

### docker-compose.yml

```yaml
version: '3.8'
services:
  mcp-ai-client:
    image: mcp-ai-client:latest
    ports:
      - "8081:8081"
    environment:
      - DEEPSEEK_API_KEY=${DEEPSEEK_API_KEY}
    # 或直接指定
    # environment:
    #   - DEEPSEEK_API_KEY=sk-your-real-deepseek-api-key-here
```

### Docker Run

```bash
docker run -d \
  -p 8081:8081 \
  -e DEEPSEEK_API_KEY=sk-your-real-deepseek-api-key-here \
  mcp-ai-client:latest
```

---

## ☸️ Kubernetes 部署

### 创建 Secret

```bash
kubectl create secret generic deepseek-api-key \
  --from-literal=DEEPSEEK_API_KEY=sk-your-real-deepseek-api-key-here
```

### deployment.yaml

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mcp-ai-client
spec:
  replicas: 1
  selector:
    matchLabels:
      app: mcp-ai-client
  template:
    metadata:
      labels:
        app: mcp-ai-client
    spec:
      containers:
      - name: mcp-ai-client
        image: mcp-ai-client:latest
        ports:
        - containerPort: 8081
        env:
        - name: DEEPSEEK_API_KEY
          valueFrom:
            secretKeyRef:
              name: deepseek-api-key
              key: DEEPSEEK_API_KEY
```

---

## 🔒 生产环境最佳实践

### 1. 使用密钥管理服务

#### AWS Secrets Manager

```java
@Configuration
public class SecretsConfig {
    @Bean
    public String deepseekApiKey() {
        AWSSecretsManager client = AWSSecretsManagerClientBuilder.standard()
            .withRegion("us-east-1")
            .build();
        
        GetSecretValueRequest request = new GetSecretValueRequest()
            .withSecretId("deepseek-api-key");
        
        GetSecretValueResult result = client.getSecretValue(request);
        return result.getSecretString();
    }
}
```

#### Azure Key Vault

```java
@Configuration
public class SecretsConfig {
    @Bean
    public String deepseekApiKey() {
        SecretClient secretClient = new SecretClientBuilder()
            .vaultUrl("https://your-vault.vault.azure.net")
            .credential(new DefaultAzureCredentialBuilder().build())
            .buildClient();
        
        return secretClient.getSecret("deepseek-api-key").getValue();
    }
}
```

### 2. 环境变量注入

确保在生产环境中通过安全的方式注入环境变量，避免硬编码。

### 3. API Key 轮换

定期轮换 API Key 以提高安全性：

1. 在 DeepSeek Platform 创建新的 API Key
2. 更新环境变量
3. 重启应用
4. 删除旧的 API Key

---

## ✅ 验证安装

### 方法 1: 使用测试脚本

```bash
./test-deepseek-integration.sh
```

**预期输出**：
```
✓ 应用运行正常
✓ base-url 配置正确
✓ Jackson 配置正确
✓ API Key 已设置
✓ 会话创建成功
✓ 消息发送成功

🎉 所有测试通过！DeepSeek API 集成成功！
```

### 方法 2: 手动测试

```bash
# 1. 检查应用健康状态
curl http://localhost:8081/actuator/health

# 2. 创建会话
SESSION_ID=$(curl -s -X POST http://localhost:8081/api/chat/session \
  -H "Content-Type: application/json" \
  -d '{"sessionName":"测试"}' | jq -r '.sessionId')

echo "会话 ID: $SESSION_ID"

# 3. 发送消息
curl -s -X POST "http://localhost:8081/api/chat/session/$SESSION_ID/message" \
  -H "Content-Type: application/json" \
  -d '{"message":"你好，请做一个自我介绍"}' | jq .
```

**成功响应示例**：
```json
{
  "sessionId": "xxx-xxx-xxx",
  "userMessage": "你好，请做一个自我介绍",
  "aiResponse": "你好！我是 DeepSeek，一个由深度求索公司开发的人工智能助手...",
  "timestamp": 1761220000000
}
```

---

## 🐛 常见问题

### Q1: 提示 "Authentication Fails"

**原因**: API Key 无效或未设置

**解决**:
```bash
# 检查环境变量
echo $DEEPSEEK_API_KEY

# 重新设置
export DEEPSEEK_API_KEY=sk-your-real-key

# 重启应用
lsof -ti:8081 | xargs kill -9
mvn spring-boot:run
```

### Q2: 提示 "404 Not Found"

**原因**: base-url 配置错误

**解决**: 确保配置文件中 `base-url` 为：
```yaml
base-url: https://api.deepseek.com
```

**不要**添加 `/v1` 后缀！

### Q3: JSON 反序列化错误

**原因**: Jackson 配置缺失

**解决**: 确保配置文件包含：
```yaml
spring:
  jackson:
    deserialization:
      fail-on-unknown-properties: false
```

### Q4: 应用启动失败

**检查日志**:
```bash
tail -100 logs/mcp-ai-client.log
```

**常见原因**:
- 端口 8081 被占用
- MCP Server (9091) 未启动
- 配置文件格式错误

---

## 📊 监控和日志

### 查看实时日志

```bash
tail -f logs/mcp-ai-client.log
```

### 查看 API 调用统计

访问: http://localhost:8081/actuator/metrics

### 查看健康状态

```bash
curl http://localhost:8081/actuator/health | jq .
```

---

## 💰 DeepSeek API 定价（参考）

| 模型 | 输入价格 | 输出价格 |
|------|----------|----------|
| deepseek-chat | ¥0.001/1K tokens | ¥0.002/1K tokens |

> 注意: 价格可能变动，请访问 [DeepSeek Platform](https://platform.deepseek.com/pricing) 获取最新信息。

---

## 📚 相关文档

- [DeepSeek Platform](https://platform.deepseek.com/)
- [DeepSeek API 文档](https://platform.deepseek.com/api-docs)
- [Spring AI 文档](https://docs.spring.io/spring-ai/reference/)
- [项目 README](./README.md)
- [修复总结](./DEEPSEEK_FIX_SUMMARY.md)

---

**最后更新**: 2025-10-23  
**维护者**: AI Assistant



