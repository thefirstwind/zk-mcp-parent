# MCP AI Client 快速入门

## 5 分钟快速开始

### 步骤 1: 获取 API Key（3 分钟）

1. 访问 [阿里云 DashScope](https://dashscope.aliyun.com/)
2. 使用阿里云账号登录
3. 点击"控制台" → "API-KEY管理"
4. 创建新的 API Key 或复制现有的
5. 保存 API Key（格式类似: `sk-xxxxxxxxxxxxx`）

### 步骤 2: 启动服务（2 分钟）

```bash
# 1. 设置 API Key（替换为你的实际 Key）
export DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxx

# 2. 进入项目目录
cd /Users/shine/projects/zk-mcp-parent/mcp-ai-client

# 3. 运行启动脚本
./start.sh
```

### 步骤 3: 开始使用（即刻）

打开浏览器访问：**http://localhost:8081**

在对话框中输入：
```
有哪些 Dubbo 服务？
```

就这么简单！🎉

---

## 详细说明

### 前置条件检查

在开始之前，确保：

✅ JDK 17+ 已安装
```bash
java -version  # 应显示 17 或更高版本
```

✅ zkInfo MCP Server 已运行
```bash
# 新开一个终端窗口
cd /Users/shine/projects/zk-mcp-parent/zkInfo
mvn spring-boot:run
```

### 如果启动脚本失败

手动启动：

```bash
# 1. 构建项目
mvn clean package -DskipTests

# 2. 运行
export DASHSCOPE_API_KEY=your-api-key
java -jar target/mcp-ai-client-1.0.0.jar
```

### 验证服务运行

检查这些端点：

1. **健康检查**: http://localhost:8081/api/chat/health
2. **API 文档**: http://localhost:8081/swagger-ui.html
3. **主界面**: http://localhost:8081

### 第一次对话示例

**示例 1: 查询服务**
```
你: 有哪些 Dubbo 服务？
AI: [自动调用工具并返回服务列表]
```

**示例 2: 查看详情**
```
你: UserService 的详细信息是什么？
AI: [返回服务的详细元数据]
```

**示例 3: 健康检查**
```
你: 所有服务健康吗？
AI: [检查并返回健康状态]
```

### 使用 REST API

如果你更喜欢使用 API：

```bash
# 创建会话
curl -X POST http://localhost:8081/api/chat/session

# 发送消息（替换 SESSION_ID）
curl -X POST http://localhost:8081/api/chat/session/SESSION_ID/message \
  -H "Content-Type: application/json" \
  -d '{"message": "查询所有服务"}'
```

### 常见问题

**Q: 提示 "未设置 DASHSCOPE_API_KEY"？**

A: 运行：
```bash
export DASHSCOPE_API_KEY=your-actual-key
```

**Q: 无法连接到 MCP Server？**

A: 确保 zkInfo 在运行：
```bash
curl http://localhost:9091/mcp/health
```

**Q: 响应很慢？**

A: LLM 调用通常需要 2-5 秒，这是正常的。

**Q: 页面显示连接错误？**

A: 检查：
1. 服务是否在运行: `curl http://localhost:8081/api/chat/health`
2. 端口 8081 是否被占用
3. 查看日志: `tail -f logs/mcp-ai-client.log`

### 停止服务

按 `Ctrl+C` 停止服务

### 下一步

- 📖 阅读 [使用指南](USAGE_GUIDE.md) 了解更多功能
- 📚 查看 [完整文档](README.md) 了解技术细节
- 🔧 访问 [Swagger UI](http://localhost:8081/swagger-ui.html) 探索 API

### 完整启动检查清单

```bash
# ✅ 检查 1: Java 版本
java -version

# ✅ 检查 2: zkInfo MCP Server
curl http://localhost:9091/mcp/health

# ✅ 检查 3: 设置 API Key
echo $DASHSCOPE_API_KEY

# ✅ 检查 4: 启动服务
./start.sh

# ✅ 检查 5: 验证运行
curl http://localhost:8081/api/chat/health
```

全部成功？开始享受 AI 驱动的 MCP 客户端吧！🚀

