# ZK-MCP: AI-Powered Dubbo Service Interface

<div align="center">

![Version](https://img.shields.io/badge/version-1.0.0-blue)
![Java](https://img.shields.io/badge/Java-17-orange)
![Node](https://img.shields.io/badge/Node.js-20+-green)
![License](https://img.shields.io/badge/license-MIT-brightgreen)

**让 AI 理解和调用微服务，让自然语言成为系统的接口**

[快速开始](#-快速开始) • [功能特性](#-功能特性) • [架构设计](#-架构设计) • [文档](#-文档) • [示例](#-示例)

</div>

---

## 📖 项目简介

ZK-MCP 是一个创新的项目，它将 **Anthropic 的 Model Context Protocol (MCP)** 与 **Apache Dubbo 微服务框架** 无缝集成，让用户能够通过自然语言与微服务进行交互。

### 核心价值

- 🗣️ **自然语言接口**: 用普通话问"有多少个用户？"就能查询数据
- 🤖 **AI 智能理解**: Claude AI 自动理解意图并选择正确的服务调用
- 🔌 **无缝集成**: 自动发现和注册 Dubbo 服务，无需手动配置
- 🚀 **开箱即用**: 一键启动所有服务，立即体验

### 使用场景

- 🎯 **快速原型**: 通过自然语言快速测试微服务接口
- 📊 **数据查询**: 让非技术人员也能查询系统数据
- 🔧 **运维管理**: 用对话方式管理和监控微服务
- 🎓 **学习演示**: 理解 AI Agent 和微服务架构的最佳实践

---

## 🚀 快速开始

### 前置要求

- ✅ Java 17+
- ✅ Node.js 20+
- ✅ Maven 3.6+
- ✅ Zookeeper 3.8+
- ✅ Anthropic API Key

### 一键启动

```bash
# 1. 克隆项目（如果需要）
cd /Users/shine/projects/zk-mcp-parent

# 2. 配置 API Key
export ANTHROPIC_API_KEY="your-api-key-here"

# 3. 启动所有服务
./start-all-services.sh

# 4. 等待启动完成后，访问
open http://localhost:8081
```

### 第一个查询

在浏览器中输入：

```
"有多少个用户？"
```

AI 将自动理解您的意图，调用相应的服务，并返回结果！

---

## ✨ 功能特性

### 🎯 核心功能

| 功能 | 描述 | 状态 |
|------|------|------|
| 🤖 自然语言查询 | 用中英文问问题，AI 自动理解并调用服务 | ✅ 完成 |
| 🔄 自动服务发现 | 自动发现 Zookeeper 中注册的 Dubbo 服务 | ✅ 完成 |
| 🛠️ 动态工具注册 | 将服务方法自动转换为 MCP 工具 | ✅ 完成 |
| 💬 会话管理 | 支持多会话并发，保持上下文连续性 | ✅ 完成 |
| 📊 Web 界面 | 友好的聊天界面，实时响应 | ✅ 完成 |
| 🔍 RESTful API | 完整的 HTTP API，便于集成 | ✅ 完成 |

### 🎨 技术亮点

1. **MCP 协议标准实现**
   - 完整支持 MCP 协议规范
   - stdio 通信方式
   - 工具调用和结果处理

2. **Dubbo 泛化调用**
   - 无需服务接口 JAR 包
   - 动态参数解析
   - 跨语言调用支持

3. **AI 智能推理**
   - 多步骤任务分解
   - 上下文理解
   - 自然语言生成

4. **微服务架构**
   - 服务注册与发现
   - 负载均衡
   - 分布式调用

---

## 🏗️ 架构设计

### 系统架构图

```
┌─────────────┐
│   用户/浏览器 │
└──────┬──────┘
       │ HTTP/JSON
       ▼
┌──────────────────────┐
│  MCP AI Client       │  ← 🌐 Web UI + REST API
│  (Port: 8081)        │  ← 🤖 Claude AI Integration
└──────┬───────────────┘
       │ MCP Protocol (stdio)
       ▼
┌──────────────────────┐
│  MCP Server          │  ← 🔧 Tool Registry
│  (Port: 3000)        │  ← 🔌 Dubbo Generic Invocation
└──────┬───────────────┘
       │ Dubbo RPC
       ▼
┌──────────────────────┐
│  Demo Provider       │  ← 💼 Business Services
│  (Port: 20883)       │     - UserService
└──────┬───────────────┘     - ProductService
       │                     - OrderService
       ▼
┌──────────────────────┐
│  Zookeeper           │  ← 🗂️ Service Registry
│  (Port: 2181)        │
└──────────────────────┘
```

### 数据流

```
用户输入: "查询用户Alice的所有订单"
    ↓
[AI Client] 创建/获取会话，连接 MCP Server
    ↓
[Claude AI] 分析意图：
    1️⃣ 需要先找到 Alice 的用户ID
    2️⃣ 再用ID查询订单
    ↓
[AI Client] 调用 getAllUsers() 工具
    ↓
[MCP Server] 执行 Dubbo 泛化调用
    ↓
[Demo Provider] UserService.getAllUsers()
    ↓
[返回] 用户列表 → 找到 Alice (ID=1)
    ↓
[AI Client] 调用 getOrdersByUserId(1) 工具
    ↓
[MCP Server] 再次执行 Dubbo 调用
    ↓
[Demo Provider] OrderService.getOrdersByUserId(1)
    ↓
[返回] Alice 的订单列表
    ↓
[Claude AI] 将结果转换为自然语言
    ↓
[用户看到] "Alice 有 2 个订单：ORD001 (总额¥9998) 和 ORD003 (总额¥2999)"
```

---

## 📚 文档

| 文档 | 描述 |
|------|------|
| [📘 快速启动指南](./QUICK-START.md) | 5分钟快速上手指南 |
| [📗 完整技术文档](./MCP-AI-CLIENT-README.md) | 详细的技术文档和 API 参考 |
| [📊 测试报告](./interface_test_report.md) | 全面的功能测试和性能报告 |
| [🐛 Bug报告](./BUG_REPORT.md) | 已发现问题的详细分析报告 |
| [📋 项目总结](./PROJECT-SUMMARY.md) | 项目回顾和技术总结 |

### 子项目文档

- [Demo Provider](./demo-provider/) - Dubbo 服务提供者
- [MCP Server](./mcp-server/) - MCP 协议服务器
- [MCP AI Client](./mcp-ai-client/) - AI 客户端应用

---

## 💡 使用示例

### 示例 1: 基础查询

```bash
用户: "查询用户ID为1的信息"

AI 执行:
- 调用工具: UserService.getUserById(1)

返回结果:
{
  "id": 1,
  "username": "alice",
  "realName": "Alice Wang",
  "age": 25,
  "gender": "F",
  "email": "alice@example.com"
}
```

### 示例 2: 列表查询

```bash
用户: "有多少个用户？"

AI 执行:
- 调用工具: UserService.getAllUsers()
- 统计数量

AI 回答: "系统中有 3 个用户"
```

### 示例 3: 复杂查询

```bash
用户: "Alice买了什么东西？"

AI 执行:
1. 调用 UserService.getAllUsers() 找到 Alice 的 ID
2. 调用 OrderService.getOrdersByUserId(1) 获取订单
3. 分析订单内容

AI 回答: "Alice 有 2 个订单，购买了 iPhone 15 等商品，总消费 ¥12997"
```

### 示例 4: 搜索功能

```bash
用户: "搜索包含iPhone的产品"

AI 执行:
- 调用工具: ProductService.searchProducts("iPhone")

返回结果:
[
  {
    "id": 1,
    "name": "iPhone 15",
    "price": 7999.0,
    "category": "手机数码"
  }
]
```

---

## 🎯 可用服务

### UserService - 用户服务

| 方法 | 描述 | 示例 |
|------|------|------|
| `getUserById(Long)` | 根据ID查询用户 | "查询用户1的信息" |
| `getAllUsers()` | 获取所有用户 | "列出所有用户" |
| `deleteUser(Long)` | 删除用户 | "删除用户3" |

### ProductService - 产品服务

| 方法 | 描述 | 示例 |
|------|------|------|
| `getProductById(Long)` | 根据ID查询产品 | "查询产品1" |
| `searchProducts(String)` | 搜索产品 | "搜索iPhone" |
| `getProductsByCategory(String)` | 按分类查询 | "查询手机数码类产品" |
| `getPopularProducts(int)` | 获取热门产品 ⚠️ | "前5个热门产品" (已知Bug) |
| `getProductPrice(Long)` | 获取产品价格 | "产品1多少钱" |

### OrderService - 订单服务

| 方法 | 描述 | 示例 |
|------|------|------|
| `getOrderById(String)` | 根据ID查询订单 | "查询订单ORD001" |
| `getOrdersByUserId(Long)` | 查询用户订单 | "用户1的订单" |
| `cancelOrder(String)` | 取消订单 | "取消订单ORD002" |
| `calculateOrderTotal(String)` | 计算订单金额 | "订单ORD001总额" |

---

## ⚠️ 已知问题

### 🐛 Bug #1: getPopularProducts 调用失败

**问题描述**: ProductService.getPopularProducts 方法在运行时调用失败  
**错误信息**: `No such method getPopularProducts in class interface com.zkinfo.demo.service.ProductService`  
**影响范围**: 热门产品查询功能不可用  
**状态**: 🔍 调查中  
**优先级**: 中等  

**临时解决方案**: 使用其他产品查询方法替代  
**详细信息**: 参见 [Bug报告](./BUG_REPORT.md)

---

## 🧪 测试

### 运行完整测试

```bash
./test-all-interfaces.sh
```

### 手动测试

```bash
# 创建会话
curl -X POST http://localhost:8081/api/chat/session

# 发送消息（替换 SESSION_ID）
curl -X POST "http://localhost:8081/api/chat/session/{SESSION_ID}/message" \
  -H "Content-Type: application/json" \
  -d '{"message": "查询所有用户"}'

# 查看历史
curl "http://localhost:8081/api/chat/session/{SESSION_ID}/history"
```

### 测试结果

- 📊 **17 个服务方法**
- ✅ **16 个测试通过** (94.1%)
- ❌ **1 个已知Bug** (getPopularProducts)
- ✅ **AI 理解准确率 95%+**
- ✅ **平均响应时间 100-300ms**

详见 [完整测试报告](./interface_test_report.md) 和 [Bug报告](./BUG_REPORT.md)

---

## 🛠️ 开发指南

### 添加新服务

1. **在 demo-provider 中创建服务接口**
```java
public interface MyService {
    String myMethod(String param);
}
```

2. **实现服务**
```java
@DubboService(version = "1.0.0", group = "demo")
public class MyServiceImpl implements MyService {
    @Override
    public String myMethod(String param) {
        return "Result: " + param;
    }
}
```

3. **重启服务**
```bash
./start-all-services.sh
```

服务会自动注册到 MCP Server，立即可用！

### 扩展 AI 能力

修改 `mcp-ai-client/src/index.ts`:

```typescript
// 自定义系统提示词
const systemPrompt = `
你是一个微服务助手...
特殊指令：
- 当用户问"健康状态"时，检查所有服务
- 当用户问"性能报告"时，分析调用统计
`;
```

---

## 📊 性能指标

| 指标 | 数值 | 评级 |
|------|------|------|
| 服务启动时间 | < 30s | ⭐⭐⭐⭐ |
| 简单查询响应 | 100-200ms | ⭐⭐⭐⭐⭐ |
| 复杂查询响应 | 500-1000ms | ⭐⭐⭐⭐ |
| 接口成功率 | 94.1% (16/17) | ⭐⭐⭐⭐ |
| 并发支持 | 多会话 | ⭐⭐⭐⭐⭐ |
| 内存占用 | ~500MB | ⭐⭐⭐⭐ |
| AI 准确率 | 95%+ | ⭐⭐⭐⭐⭐ |

---

## 🤝 贡献指南

欢迎贡献！以下是一些可以帮助的方向：

- 🐛 报告 Bug
- 💡 提出新功能建议
- 📝 改进文档
- 🔧 提交代码修复
- ⭐ Star 这个项目

---

## 🔮 未来规划

### v1.1 (即将发布)
- [ ] 🐛 **修复getPopularProducts调用失败问题** (高优先级)
- [ ] 支持复杂对象参数
- [ ] 增强错误处理和调试信息

### v1.2
- [ ] 支持更多注册中心（Nacos、Consul）
- [ ] 添加性能监控
- [ ] 实现数据持久化

### v2.0
- [ ] 支持更多 AI 模型
- [ ] 微服务治理功能
- [ ] 可视化管理界面

详见 [项目总结](./PROJECT-SUMMARY.md#-未来展望)

---

## 📄 许可证

本项目采用 MIT 许可证。详见 [LICENSE](./LICENSE) 文件。

---

## 🙏 致谢

感谢以下开源项目：

- [Anthropic MCP](https://modelcontextprotocol.io/) - Model Context Protocol
- [Apache Dubbo](https://dubbo.apache.org/) - 微服务 RPC 框架
- [Spring Boot](https://spring.io/projects/spring-boot) - Java 应用框架
- [Claude AI](https://www.anthropic.com/) - 强大的 AI 能力

---

## 📞 联系方式

- 📧 Email: [联系邮箱]
- 🐛 Issues: [GitHub Issues]
- 💬 Discussions: [GitHub Discussions]

---

<div align="center">

**如果觉得这个项目有帮助，请给个 ⭐️ Star！**

Made with ❤️ by [Your Name]

</div>
