# MCP 到 Dubbo 链路验证文档

## 📋 概述

本文档说明如何验证 zkInfo 项目中从 MCP 到 Dubbo 的完整调用链路是否通畅。

## 🔗 调用链路

```
MCP Client 
  ↓
zkInfo SSE Endpoint (/sse/{endpoint})
  ↓
MCP Router (MultiEndpointMcpRouterConfig)
  ↓
McpExecutorService
  ↓
Dubbo Generic Invocation
  ↓
Dubbo Provider (demo-provider)
```

## 🛠️ 验证方法

### 方法 1: 使用自动化测试脚本（推荐）

```bash
# 运行测试脚本
cd /Users/shine/projects.mcp-router-sse-parent
./zk-mcp-parent/zkInfo/test-dubbo-invoke.sh
```

**脚本功能**：
1. ✅ 检查 zkInfo 和 Nacos 服务状态
2. ✅ 使用 Nacos API 查询已注册的 MCP 服务
3. ✅ 获取服务详细信息（包括 application 字段）
4. ✅ 从 zkInfo API 获取实际的接口信息
5. ✅ 调用 MCP API 执行 Dubbo 调用
6. ✅ 验证调用结果

### 方法 2: 手动验证步骤

#### 步骤 1: 查询已注册的 MCP 服务

```bash
# 使用 Nacos API（当前版本使用 v1，v3 不可用）
curl -s "http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=100&namespaceId=public&groupName=mcp-server" \
  | jq -r '.doms[]? | select(startswith("zk-mcp-"))' | head -3
```

**预期输出**：
```
zk-mcp-com-zkinfo-demo-service-userservice-1.0.0
zk-mcp-com-zkinfo-demo-service-orderservice-1.0.0
zk-mcp-com-zkinfo-demo-service-productservice-1.0.0
```

#### 步骤 2: 获取服务详细信息

```bash
SERVICE_NAME="zk-mcp-com-zkinfo-demo-service-orderservice-1.0.0"

curl -s "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=$SERVICE_NAME&namespaceId=public&groupName=mcp-server" \
  | jq '.hosts[0] | {
    ip,
    port,
    application: .metadata.application,
    sseEndpoint: .metadata.sseEndpoint,
    messageEndpoint: .metadata.sseMessageEndpoint
  }'
```

**预期输出**：
```json
{
  "ip": "127.0.0.1",
  "port": 9091,
  "application": "demo-provider",
  "sseEndpoint": "/sse",
  "messageEndpoint": "/mcp/message"
}
```

#### 步骤 3: 获取接口信息

```bash
# 从 zkInfo API 获取所有 providers
curl -s "http://localhost:9091/api/providers" \
  | jq -r '.[] | select(.interfaceName == "service.com.pajk.provider2.OrderService") | {
    interfaceName,
    version,
    group,
    application
  }'
```

#### 步骤 4: 调用 MCP API 执行 Dubbo 调用

```bash
# 调用 MCP API
curl -X POST "http://localhost:9091/api/mcp/call" \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "service.com.pajk.provider2.OrderService.getOrderById",
    "args": ["ORD001"],
    "timeout": 5000
  }' | jq '.'
```

**预期输出**：
```json
{
  "success": true,
  "executionTime": 123,
  "result": {
    "orderId": "ORD001",
    "userId": 1,
    "status": "PAID",
    "totalAmount": 9998.0,
    ...
  }
}
```

## 📊 Nacos API 版本说明

### 当前状态

- **Nacos v3 API**: 当前 Nacos 版本不支持 v3 API（返回 404）
- **Nacos v1 API**: 正常工作，已用于查询服务

### Nacos API 版本对比

| 功能 | v1 API | v3 API |
|------|--------|--------|
| 查询服务列表 | `/nacos/v1/ns/service/list` | `/nacos/v3/ns/service/list` (不可用) |
| 查询服务实例 | `/nacos/v1/ns/instance/list` | `/nacos/v3/ns/instance/list` (不可用) |
| 注册服务实例 | Java SDK | Java SDK |
| 配置管理 | `/nacos/v1/cs/configs` | `/nacos/v3/cs/configs` (可能不可用) |

### 代码中的 Nacos API 使用

zkInfo 项目主要使用 **Nacos Java SDK**（`NamingService` 和 `ConfigService`），而不是直接调用 HTTP API。这些 SDK 会自动适配 Nacos 服务器版本。

**关键类**：
- `NacosConfig.java`: 配置 Nacos SDK
- `NacosMcpRegistrationService.java`: 使用 SDK 注册服务
- `MultiEndpointMcpRouterConfig.java`: 使用 SDK 查询服务（用于服务发现）

## ✅ 验证结果示例

```
========================================
MCP 到 Dubbo 链路验证脚本
========================================

[1/6] 检查服务状态...
✅ zkInfo 服务运行正常
✅ Nacos 服务运行正常

[2/6] 查询已注册的 MCP 服务（使用 Nacos API）...
✅ 找到以下 MCP 服务:
  - zk-mcp-com-zkinfo-demo-service-userservice-1.0.0
  - zk-mcp-com-zkinfo-demo-service-orderservice-1.0.0
  - zk-mcp-com-zkinfo-demo-service-productservice-1.0.0

[3/6] 获取服务详细信息...
测试服务: zk-mcp-com-zkinfo-demo-service-userservice-1.0.0
✅ 服务信息:
  Application: demo-provider
  SSE Endpoint: /sse
  Message Endpoint: /mcp/message

[4/6] 获取实际的接口信息...
✅ 找到接口信息:
    接口名: service.com.pajk.provider2.ProductService
    版本: 1.0.0

[5/6] 直接调用 Dubbo 服务验证链路...
✅ MCP 调用成功！
{
  "success": true,
  "executionTime": 0,
  "result": {
    "id": 1,
    "name": "iPhone 15",
    "price": 7999.0,
    ...
  }
}

[6/6] 验证总结...
✅ MCP 到 Dubbo 链路验证完成！
```

## 🔍 关键验证点

### 1. Application 字段验证

- **普通 Dubbo 服务**: `application` = Dubbo 项目名称（从 Provider URL 提取）
- **虚拟项目**: `application` = 虚拟项目名称（`Project.projectName`）

验证命令：
```bash
# 检查普通服务
curl -s "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=zk-mcp-com-zkinfo-demo-service-orderservice-1.0.0&namespaceId=public&groupName=mcp-server" \
  | jq '.hosts[0].metadata.application'
# 预期: "demo-provider"

# 检查虚拟项目（如果有）
curl -s "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=mcp-{endpoint-name}&namespaceId=public&groupName=mcp-server" \
  | jq '.hosts[0].metadata.application'
# 预期: 虚拟项目名称
```

### 2. MCP 调用链路验证

验证命令：
```bash
# 测试 OrderService
curl -X POST "http://localhost:9091/api/mcp/call" \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "service.com.pajk.provider2.OrderService.getOrderById",
    "args": ["ORD001"],
    "timeout": 5000
  }' | jq '.success'

# 测试 UserService
curl -X POST "http://localhost:9091/api/mcp/call" \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "service.com.pajk.provider2.UserService.getUserById",
    "args": [1],
    "timeout": 5000
  }' | jq '.success'

# 测试 ProductService
curl -X POST "http://localhost:9091/api/mcp/call" \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "service.com.pajk.provider2.ProductService.getProductById",
    "args": [1],
    "timeout": 5000
  }' | jq '.success'
```

**预期结果**: 所有调用返回 `"success": true`

## 🐛 常见问题排查

### 问题 1: MCP 调用返回 "未找到可用的服务提供者"

**原因**: 接口名不正确或 Provider 未注册到 Zookeeper

**排查**:
```bash
# 检查 Provider 是否在 Zookeeper 中
curl -s "http://localhost:9091/api/providers" \
  | jq '.[] | select(.interfaceName == "service.com.pajk.provider2.OrderService")'

# 检查 demo-provider 是否运行
curl -s "http://localhost:8083/actuator/health"
```

### 问题 2: Nacos v3 API 返回 404

**原因**: 当前 Nacos 版本不支持 v3 API

**解决方案**: 使用 v1 API 或 Nacos Java SDK（推荐）

### 问题 3: Application 字段为空

**原因**: 
- Provider URL 中没有 `application` 参数
- 虚拟项目注册时未传递项目名称

**排查**:
```bash
# 检查 Provider 的 application 字段
curl -s "http://localhost:9091/api/providers" \
  | jq '.[] | select(.interfaceName == "service.com.pajk.provider2.OrderService") | .application'

# 检查注册日志
tail -100 zk-mcp-parent/zkInfo/logs/zkinfo.log | grep "Setting application"
```

## 📝 测试脚本位置

- **主测试脚本**: `zk-mcp-parent/zkInfo/test-dubbo-invoke.sh`
- **完整链路测试**: `zk-mcp-parent/zkInfo/test-mcp-to-dubbo-chain.sh`

## 🔄 未来改进

1. **Nacos v3 API 支持**: 当 Nacos 升级到支持 v3 API 的版本时，更新代码以使用 v3 API
2. **更多测试用例**: 添加更多边界情况和错误场景的测试
3. **性能测试**: 添加并发调用和压力测试

