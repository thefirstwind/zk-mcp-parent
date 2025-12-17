# ZK-MCP 快速启动指南

## 🚀 一键启动

```bash
# 启动所有服务（Zookeeper + Demo Provider + MCP Server + AI Client）
./start-all-services.sh

# 运行完整测试
./test-all-interfaces.sh
```

## 📋 服务端口

| 服务 | 端口 | 用途 |
|------|------|------|
| Zookeeper | 2181 | 服务注册中心 |
| Demo Provider | 20883 | Dubbo 服务提供者 |
| MCP Server | 3000 | MCP 协议服务器 |
| MCP AI Client | 8081 | Web API + AI 对话 |

## 💬 使用示例

### 方式一：通过 Web API

```bash
# 1. 创建会话
SESSION_RESPONSE=$(curl -s -X POST http://localhost:8081/api/chat/session)
SESSION_ID=$(echo $SESSION_RESPONSE | jq -r '.sessionId')

# 2. 发送问题
curl -X POST "http://localhost:8081/api/chat/session/$SESSION_ID/message" \
  -H "Content-Type: application/json" \
  -d '{"message": "查询所有用户"}'

# 3. 查看历史
curl "http://localhost:8081/api/chat/session/$SESSION_ID/history"
```

### 方式二：通过 Web 界面

打开浏览器访问：http://localhost:8081

### 方式三：使用 MCP Inspector

```bash
cd mcp-server
npx @modelcontextprotocol/inspector node dist/index.js
```

## 🎯 可用服务

### UserService - 用户服务

```bash
# 查询单个用户
"查询用户ID为1的信息"

# 查询所有用户
"列出所有用户"
"有多少个用户？"

# 删除用户
"删除用户ID为3"
```

### ProductService - 产品服务

```bash
# 查询产品
"查询产品1的信息"
"产品1的价格是多少？"

# 搜索产品
"搜索包含iPhone的产品"

# 获取热门产品
"获取前5个热门产品"

# 按分类查询
"查询手机数码类别的产品"
```

### OrderService - 订单服务

```bash
# 查询订单
"查询订单号ORD001"
"查询用户1的所有订单"

# 计算金额
"计算订单ORD001的总金额"

# 取消订单
"取消订单ORD002"

# 复杂查询
"Alice买了什么东西？"
"Bob的订单总共花了多少钱？"
```

## 🔍 测试数据

### 用户数据
- **用户1**: Alice Wang (25岁，女，alice@example.com)
- **用户2**: Bob Chen (30岁，男，bob@example.com)
- **用户3**: Charlie Li (28岁，男，charlie@example.com)

### 产品数据
- **产品1**: iPhone 15 (手机数码，¥7999)
- **产品2**: MacBook Pro (电脑办公，¥1999)
- **产品3**: AirPods Pro (数码配件，¥1999)
- **产品4**: 小米13 (手机数码，¥3999)
- **产品5**: iPad Pro (平板电脑，¥6999)

### 订单数据
- **ORD001**: Alice的订单，总额¥9998（2件商品）
- **ORD002**: Bob的订单，总额¥5998（1件商品）
- **ORD003**: Alice的第二个订单，总额¥2999（1件商品）

## 🛠️ 常用命令

### 服务管理

```bash
# 启动 Zookeeper
brew services start zookeeper

# 启动 Demo Provider
cd demo-provider
mvn spring-boot:run > logs/demo-provider.log 2>&1 &

# 启动 MCP Server
cd mcp-server
npm start > logs/mcp-server.log 2>&1 &

# 启动 MCP AI Client
cd mcp-ai-client
npm start > logs/mcp-ai-client.log 2>&1 &
```

### 查看日志

```bash
# Demo Provider 日志
tail -f demo-provider/logs/demo-provider.log

# MCP Server 日志
tail -f mcp-server/logs/mcp-server.log

# MCP AI Client 日志
tail -f mcp-ai-client/logs/mcp-ai-client.log
```

### 停止服务

```bash
# 停止 Java 进程（Demo Provider）
pkill -f demo-provider

# 停止 Node 进程（MCP Server 和 AI Client）
pkill -f "node.*mcp-server"
pkill -f "node.*mcp-ai-client"

# 停止 Zookeeper
brew services stop zookeeper
```

## 🐛 故障排查

### 问题1: 服务启动失败

```bash
# 检查端口占用
lsof -i :2181  # Zookeeper
lsof -i :20883 # Demo Provider
lsof -i :3000  # MCP Server
lsof -i :8081  # AI Client

# 杀死占用进程
kill -9 <PID>
```

### 问题2: Dubbo 连接失败

```bash
# 检查 Zookeeper 是否运行
nc -zv localhost 2181

# 重启 Zookeeper
brew services restart zookeeper

# 等待几秒后重启 Provider
cd demo-provider
mvn spring-boot:run
```

### 问题3: MCP 调用失败

```bash
# 检查 MCP Server 日志
tail -f mcp-server/logs/mcp-server.log

# 测试 MCP Server 健康状态
curl http://localhost:3000/health

# 重启 MCP Server
pkill -f "node.*mcp-server"
cd mcp-server
npm start
```

### 问题4: AI Client 无响应

```bash
# 检查 AI Client 日志
tail -f mcp-ai-client/logs/mcp-ai-client.log

# 测试 AI Client 健康状态
curl http://localhost:8081/health

# 重启 AI Client
pkill -f "node.*mcp-ai-client"
cd mcp-ai-client
npm start
```

## 📚 更多文档

- **完整文档**: [MCP-AI-CLIENT-README.md](./MCP-AI-CLIENT-README.md)
- **测试报告**: [TEST-REPORT.md](./TEST-REPORT.md)
- **架构设计**: 查看各项目的 README 文件

## 🎓 学习路径

1. **了解架构**: 阅读 MCP-AI-CLIENT-README.md 的架构部分
2. **启动系统**: 使用 start-all-services.sh 一键启动
3. **简单测试**: 通过浏览器访问 http://localhost:8081 尝试对话
4. **深入学习**: 使用 MCP Inspector 查看工具调用细节
5. **自定义扩展**: 在 demo-provider 中添加自己的服务

## 💡 提示

- 首次启动可能需要几秒钟，请耐心等待所有服务就绪
- AI 响应时间取决于查询复杂度，通常在 1-3 秒
- 支持中文和英文自然语言查询
- 可以在一个会话中进行连续对话，AI 会记住上下文

## 🔗 相关链接

- [Model Context Protocol](https://modelcontextprotocol.io/)
- [Apache Dubbo](https://dubbo.apache.org/)
- [Zookeeper](https://zookeeper.apache.org/)
- [Anthropic Claude](https://www.anthropic.com/)

---

**Happy Coding! 🎉**


