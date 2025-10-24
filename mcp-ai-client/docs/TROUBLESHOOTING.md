# 🔧 故障排查指南

## 问题诊断报告

### ❌ 当前问题
**工具调用失败：DeepSeek API Key 无效**

```
错误信息: 401 - Authentication Fails, Your api key: ****-key is invalid
当前 API Key: test-key (无效的测试值)
```

### 🔍 问题原因

AI 服务正常工作，已经：
1. ✅ 成功创建会话
2. ✅ 正确识别用户意图
3. ✅ 选择了正确的工具: `getUserById`
4. ✅ 提取了正确的参数: `userId=1`
5. ❌ **调用 DeepSeek API 时认证失败**

### 💡 解决方案

#### 方法 1: 设置真实的 API Key（推荐）

1. **获取 DeepSeek API Key**
   ```bash
   # 访问 DeepSeek 平台获取 API Key
   open https://platform.deepseek.com/
   ```

2. **设置环境变量**
   ```bash
   # 临时设置（当前终端有效）
   export DEEPSEEK_API_KEY=sk-your-real-api-key-here
   
   # 永久设置（添加到 ~/.zshrc）
   echo 'export DEEPSEEK_API_KEY=sk-your-real-api-key-here' >> ~/.zshrc
   source ~/.zshrc
   ```

3. **重启应用**
   ```bash
   cd /Users/shine/projects/zk-mcp-parent/mcp-ai-client
   
   # 停止当前进程
   lsof -ti:8081 | xargs kill -9
   
   # 重新启动
   mvn spring-boot:run
   ```

#### 方法 2: 使用本地 Mock 模式（测试用）

如果暂时没有 API Key，可以修改代码使用 Mock 模式：

```java
// 在 AiConversationService 中添加 Mock 逻辑
if (apiKey.equals("test-key")) {
    // 返回 Mock 响应
    return mockAiResponse(userMessage, tools);
}
```

### 🧪 验证修复

设置真实 API Key 后，重新测试：

```bash
# 1. 创建会话
SESSION_ID=$(curl -s -X POST http://localhost:8081/api/chat/session | jq -r '.sessionId')

# 2. 发送查询
curl -X POST "http://localhost:8081/api/chat/session/$SESSION_ID/message" \
  -H "Content-Type: application/json" \
  -d '{"message": "I wanna find a user, the id is 1, please find user information"}' \
  | jq '.'

# 预期结果：应该返回用户信息
# {
#   "sessionId": "...",
#   "userMessage": "...",
#   "aiResponse": "用户信息：姓名：Alice Wang, 邮箱：alice@example.com, ..."
# }
```

### 📊 完整测试流程

```bash
# 使用自动化测试脚本
/tmp/test-find-user.sh

# 预期输出：
# ✅ 会话创建成功
# ✅ AI 正确理解意图
# ✅ 调用 getUserById 工具
# ✅ 返回用户详细信息
```

### 🔗 相关文档

- DeepSeek API 文档: https://platform.deepseek.com/docs
- 项目 README: `README.md`
- API Key 设置脚本: `setup-api-key.sh`

### 📞 需要帮助？

如果问题仍然存在，请检查：
1. API Key 是否正确复制（包括 `sk-` 前缀）
2. API Key 是否有足够的配额
3. 网络是否可以访问 api.deepseek.com
4. 环境变量是否正确加载（重启终端）

---
**最后更新**: 2025-10-23
