# 多 SSE 连接节点功能验证文档

## 📋 概述

本文档说明如何验证 zkInfo 项目中多个 SSE 连接节点功能的调用是否正常。

## 🔗 支持的 Endpoint 格式

zkInfo 支持以下多种 endpoint 格式：

1. **标准 SSE 端点**: `GET /sse?serviceName={serviceName}`
   - 需要 `serviceName` 查询参数
   - 兼容 `mcp-router-v3`

2. **项目代码**: `GET /sse/{projectCode}`
   - 例如: `GET /sse/TEST_PROJECT_001`

3. **项目名称**: `GET /sse/{projectName}`
   - 例如: `GET /sse/测试项目1`

4. **虚拟项目 endpoint 名称**: `GET /sse/{endpointName}`
   - 例如: `GET /sse/data-analysis`

5. **虚拟项目 ID**: `GET /sse/{virtualProjectId}`
   - 例如: `GET /sse/1765793892492`

6. **MCP 服务名称**: `GET /sse/{mcpServiceName}`
   - 例如: `GET /sse/zk-mcp-com-zkinfo-demo-service-orderservice-1.0.0`

## 📨 MCP 消息端点

- **通用消息端点**: `POST /mcp/message?sessionId={sessionId}`
  - 通过 `sessionId` 自动查找对应的 endpoint

- **指定 endpoint 的消息端点**: `POST /mcp/{endpoint}/message?sessionId={sessionId}`
  - 直接指定 endpoint

## 🧪 验证方法

### 方法 1: 使用自动化测试脚本

```bash
# 运行完整验证脚本
cd /Users/shine/projects.mcp-router-sse-parent
./zk-mcp-parent/zkInfo/test-sse-endpoints-complete.sh
```

### 方法 2: 手动验证步骤

#### 步骤 1: 建立 SSE 连接

```bash
# 使用 MCP 服务名称
curl -N "http://localhost:9091/sse/zk-mcp-com-zkinfo-demo-service-orderservice-1.0.0" \
  -H "Accept: text/event-stream"

# 使用项目代码
curl -N "http://localhost:9091/sse/TEST_PROJECT_001" \
  -H "Accept: text/event-stream"

# 使用虚拟项目 endpoint 名称
curl -N "http://localhost:9091/sse/data-analysis" \
  -H "Accept: text/event-stream"

# 标准端点（需要 serviceName）
curl -N "http://localhost:9091/sse?serviceName=zk-mcp-com-zkinfo-demo-service-orderservice-1.0.0" \
  -H "Accept: text/event-stream"
```

**预期响应**:
```
event:endpoint
data:http://localhost:9091/mcp/message?sessionId=xxx-xxx-xxx

event:heartbeat
data:{"type":"heartbeat","timestamp":1234567890}
```

#### 步骤 2: 发送 initialize 请求

```bash
SESSION_ID="your-session-id-from-sse-response"

curl -X POST "http://localhost:9091/mcp/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": {
        "name": "test-client",
        "version": "1.0.0"
      }
    }
  }'
```

**预期响应**: HTTP 202 Accepted（响应通过 SSE 流发送）

#### 步骤 3: 发送 tools/list 请求

```bash
curl -X POST "http://localhost:9091/mcp/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/list",
    "params": {}
  }'
```

**预期响应**: HTTP 202 Accepted（工具列表通过 SSE 流发送）

#### 步骤 4: 发送 tools/call 请求

```bash
curl -X POST "http://localhost:9091/mcp/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "3",
    "method": "tools/call",
    "params": {
      "name": "service.com.pajk.provider2.OrderService.getOrderById",
      "arguments": ["ORD001"]
    }
  }'
```

**预期响应**: HTTP 202 Accepted（调用结果通过 SSE 流发送）

## 🔍 验证要点

### 1. SSE 连接建立

- ✅ 所有 endpoint 格式都能成功建立 SSE 连接
- ✅ SSE 响应包含 `event:endpoint` 和 `data:` 字段
- ✅ 定期发送心跳消息（`event:heartbeat`）

### 2. Endpoint 解析

- ✅ 虚拟项目 endpoint 名称正确解析
- ✅ 虚拟项目 ID 正确解析
- ✅ 项目代码正确解析
- ✅ 项目名称正确解析
- ✅ MCP 服务名称正确解析

### 3. MCP 消息处理

- ✅ `initialize` 请求正确处理
- ✅ `tools/list` 请求返回正确的工具列表
- ✅ `tools/call` 请求正确调用 Dubbo 服务并返回结果
- ✅ 所有响应都通过 SSE 流发送

### 4. 多端点并发

- ✅ 多个不同的 endpoint 可以同时建立连接
- ✅ 每个连接有独立的 `sessionId`
- ✅ 消息不会混淆

## 🐛 常见问题排查

### 问题 1: SSE 端点返回 404

**原因**: `RouterFunction` Bean 冲突或未正确注册

**排查**:
1. 检查是否有多个 `RouterFunction` Bean
2. 确认 `MultiEndpointMcpRouterConfig.multiEndpointRouterFunction()` 已注册
3. 检查日志中是否有路由创建信息

### 问题 2: Endpoint 无法解析

**原因**: Endpoint 不存在或格式不正确

**排查**:
```bash
# 检查项目是否存在
curl "http://localhost:9091/api/projects" | jq '.[] | select(.projectCode == "TEST_PROJECT_001")'

# 检查虚拟项目是否存在
curl "http://localhost:9091/api/virtual-projects" | jq '.[] | select(.endpoint.endpointName == "data-analysis")'
```

### 问题 3: MCP 消息无响应

**原因**: `sessionId` 不匹配或 SSE 连接已断开

**排查**:
1. 确认 `sessionId` 来自 SSE 连接的响应
2. 检查 SSE 连接是否仍然活跃
3. 查看日志中的 session 管理信息

## 📝 测试脚本位置

- **完整测试脚本**: `zk-mcp-parent/zkInfo/test-sse-endpoints-complete.sh`
- **基础测试脚本**: `zk-mcp-parent/zkInfo/test-multi-sse-endpoints.sh`

## 🔄 调用流程

```
1. 客户端建立 SSE 连接
   GET /sse/{endpoint}
   ↓
2. 服务器返回 sessionId
   event:endpoint
   data:http://localhost:9091/mcp/message?sessionId=xxx
   ↓
3. 客户端发送 MCP 消息
   POST /mcp/message?sessionId=xxx
   ↓
4. 服务器处理消息并返回响应
   通过 SSE 流发送响应
   ↓
5. 客户端接收响应
   从 SSE 流中读取响应
```

## ✅ 验证清单

- [ ] 标准 SSE 端点 (`/sse?serviceName=xxx`) 正常工作
- [ ] 项目代码 endpoint (`/sse/{projectCode}`) 正常工作
- [ ] 项目名称 endpoint (`/sse/{projectName}`) 正常工作
- [ ] 虚拟项目 endpoint (`/sse/{endpointName}`) 正常工作
- [ ] 虚拟项目 ID endpoint (`/sse/{id}`) 正常工作
- [ ] MCP 服务名称 endpoint (`/sse/{serviceName}`) 正常工作
- [ ] `initialize` 请求正确处理
- [ ] `tools/list` 请求返回正确结果
- [ ] `tools/call` 请求正确调用 Dubbo 服务
- [ ] 多个 endpoint 可以同时连接
- [ ] 每个连接有独立的 session

