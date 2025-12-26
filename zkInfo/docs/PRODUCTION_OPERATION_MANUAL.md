# zkInfo 生产环境核心功能验证操作手册

**版本**: 1.0.0  
**更新日期**: 2025-12-26  
**适用环境**: 生产环境逐步验证

---

## 📋 目录

1. [环境准备](#1-环境准备)
2. [基础健康检查](#2-基础健康检查)
3. [服务发现与同步](#3-服务发现与同步)
4. [Dubbo服务管理](#4-dubbo服务管理)
5. [项目管理](#5-项目管理)
6. [虚拟项目管理](#6-虚拟项目管理)
7. [服务审批流程](#7-服务审批流程)
8. [接口过滤（白名单）](#8-接口过滤白名单)
9. [MCP协议调用](#9-mcp协议调用)
10. [SSE端点验证](#10-sse端点验证)
11. [Nacos注册验证](#11-nacos注册验证)
12. [心跳检测验证](#12-心跳检测验证)
13. [故障排查](#13-故障排查)

---

## 配置说明

在执行以下命令前，请根据实际环境设置以下变量：

```bash
# 设置 zkInfo 服务地址
export ZKINFO_URL="http://your-zkinfo-host:9091"

# 设置 Nacos 服务地址
export NACOS_URL="http://your-nacos-host:8848"

# 设置 Nacos 命名空间和分组
export NACOS_NAMESPACE="public"
export NACOS_GROUP="mcp-server"
```

---

## 1. 环境准备

### 1.1 检查 zkInfo 服务是否运行

```bash
curl -X GET "${ZKINFO_URL}/actuator/health" \
  -H "Content-Type: application/json"
```

**预期响应**:
```json
{
  "status": "UP"
}
```

### 1.2 检查服务基本信息

```bash
curl -X GET "${ZKINFO_URL}/actuator/info" \
  -H "Content-Type: application/json"
```

### 1.3 检查 ZooKeeper 连接状态

```bash
curl -X GET "${ZKINFO_URL}/api/debug/zk-tree" \
  -H "Content-Type: application/json"
```

**预期响应**: 返回 ZooKeeper 树结构

---

## 2. 基础健康检查

### 2.1 检查服务统计信息

```bash
curl -X GET "${ZKINFO_URL}/api/stats" \
  -H "Content-Type: application/json"
```

**预期响应**:
```json
{
  "totalServices": 10,
  "totalProviders": 25,
  "onlineProviders": 20,
  "offlineProviders": 5
}
```

### 2.2 检查已注册服务

```bash
curl -X GET "${ZKINFO_URL}/api/registered-services" \
  -H "Content-Type: application/json"
```

### 2.3 检查应用列表

```bash
curl -X GET "${ZKINFO_URL}/api/applications" \
  -H "Content-Type: application/json"
```

---

## 3. 服务发现与同步

### 3.1 查询所有 Dubbo 服务（分页）

```bash
curl -X GET "${ZKINFO_URL}/api/dubbo-services?page=1&size=10" \
  -H "Content-Type: application/json"
```

**预期响应**:
```json
{
  "data": [
    {
      "id": 1,
      "interfaceName": "com.example.Service",
      "version": "1.0.0",
      "group": "demo",
      "approvalStatus": "APPROVED"
    }
  ],
  "total": 10,
  "page": 1,
  "size": 10
}
```

### 3.2 根据ID查询服务详情

```bash
# 替换 {serviceId} 为实际的服务ID
curl -X GET "${ZKINFO_URL}/api/dubbo-services/{serviceId}" \
  -H "Content-Type: application/json"
```

### 3.3 查询服务的节点信息

```bash
curl -X GET "${ZKINFO_URL}/api/dubbo-services/{serviceId}/nodes" \
  -H "Content-Type: application/json"
```

**预期响应**:
```json
[
  {
    "id": 1,
    "address": "192.168.1.100:20880",
    "isOnline": true,
    "isHealthy": true,
    "lastHeartbeatTime": "2025-12-26 10:00:00"
  }
]
```

### 3.4 查询服务的方法列表

```bash
curl -X GET "${ZKINFO_URL}/api/dubbo-services/{serviceId}/methods" \
  -H "Content-Type: application/json"
```

### 3.5 手动同步服务节点

```bash
curl -X POST "${ZKINFO_URL}/api/dubbo-services/{serviceId}/sync-nodes" \
  -H "Content-Type: application/json"
```

**预期响应**:
```json
{
  "message": "节点同步成功",
  "syncedCount": 3
}
```

---

## 4. Dubbo服务管理

### 4.1 查询待审批服务

```bash
curl -X GET "${ZKINFO_URL}/api/dubbo-services/pending?page=1&size=10" \
  -H "Content-Type: application/json"
```

### 4.2 查询已审批服务

```bash
curl -X GET "${ZKINFO_URL}/api/dubbo-services/approved?page=1&size=10" \
  -H "Content-Type: application/json"
```

### 4.3 提交服务审批

```bash
curl -X POST "${ZKINFO_URL}/api/dubbo-services/{serviceId}/submit-for-review" \
  -H "Content-Type: application/json" \
  -d '{
    "reviewerId": 1,
    "reviewerName": "管理员",
    "comment": "申请审批此服务"
  }'
```

### 4.4 审批通过服务

```bash
curl -X POST "${ZKINFO_URL}/api/dubbo-services/{serviceId}/approve" \
  -H "Content-Type: application/json" \
  -d '{
    "reviewerId": 1,
    "reviewerName": "管理员",
    "comment": "审批通过"
  }'
```

**预期响应**:
```json
{
  "message": "服务审批通过成功"
}
```

### 4.5 拒绝服务审批

```bash
curl -X POST "${ZKINFO_URL}/api/dubbo-services/{serviceId}/reject" \
  -H "Content-Type: application/json" \
  -d '{
    "reviewerId": 1,
    "reviewerName": "管理员",
    "comment": "拒绝原因：不符合规范"
  }'
```

### 4.6 标记服务为离线

```bash
curl -X POST "${ZKINFO_URL}/api/dubbo-services/{serviceId}/offline" \
  -H "Content-Type: application/json"
```

### 4.7 标记服务为在线

```bash
curl -X POST "${ZKINFO_URL}/api/dubbo-services/{serviceId}/online" \
  -H "Content-Type: application/json"
```

---

## 5. 项目管理

### 5.1 创建实际项目

```bash
curl -X POST "${ZKINFO_URL}/api/projects" \
  -H "Content-Type: application/json" \
  -d '{
    "projectCode": "prod-project-001",
    "projectName": "生产项目001",
    "projectType": "REAL",
    "description": "生产环境测试项目",
    "ownerId": 1,
    "ownerName": "管理员"
  }'
```

**预期响应**:
```json
{
  "id": 1,
  "projectCode": "prod-project-001",
  "projectName": "生产项目001",
  "status": "ACTIVE"
}
```

**保存项目ID**: `export PROJECT_ID=1`

### 5.2 查询所有项目

```bash
curl -X GET "${ZKINFO_URL}/api/projects" \
  -H "Content-Type: application/json"
```

### 5.3 查询项目详情

```bash
curl -X GET "${ZKINFO_URL}/api/projects/${PROJECT_ID}" \
  -H "Content-Type: application/json"
```

### 5.4 添加服务到项目

```bash
curl -X POST "${ZKINFO_URL}/api/projects/${PROJECT_ID}/services" \
  -H "Content-Type: application/json" \
  -d '{
    "serviceInterface": "com.example.Service",
    "version": "1.0.0",
    "group": "demo"
  }'
```

### 5.5 查询项目关联的服务

```bash
curl -X GET "${ZKINFO_URL}/api/projects/${PROJECT_ID}/services" \
  -H "Content-Type: application/json"
```

### 5.6 检查服务是否在项目中

```bash
curl -X GET "${ZKINFO_URL}/api/projects/${PROJECT_ID}/services/check?interfaceName=com.example.Service&version=1.0.0&group=demo" \
  -H "Content-Type: application/json"
```

### 5.7 根据服务查询所属项目

```bash
curl -X GET "${ZKINFO_URL}/api/projects/by-service?interfaceName=com.example.Service&version=1.0.0&group=demo" \
  -H "Content-Type: application/json"
```

---

## 6. 虚拟项目管理

### 6.1 创建虚拟项目

```bash
curl -X POST "${ZKINFO_URL}/api/virtual-projects" \
  -H "Content-Type: application/json" \
  -d '{
    "endpointName": "prod-data-analysis",
    "projectName": "生产数据分析项目",
    "projectCode": "prod-virtual-001",
    "description": "生产环境虚拟项目",
    "services": [
      {
        "serviceInterface": "com.example.Service1",
        "version": "1.0.0",
        "group": "demo",
        "priority": 0
      },
      {
        "serviceInterface": "com.example.Service2",
        "version": "1.0.0",
        "group": "demo",
        "priority": 1
      }
    ],
    "autoRegister": true
  }'
```

**预期响应**:
```json
{
  "project": {
    "id": 2,
    "projectCode": "prod-virtual-001",
    "projectName": "生产数据分析项目"
  },
  "endpoint": {
    "endpointName": "prod-data-analysis",
    "mcpServiceName": "virtual-prod-data-analysis"
  },
  "serviceCount": 2,
  "message": "虚拟项目创建成功"
}
```

**保存虚拟项目ID和端点名称**:
```bash
export VIRTUAL_PROJECT_ID=2
export ENDPOINT_NAME="prod-data-analysis"
```

### 6.2 查询所有虚拟项目

```bash
curl -X GET "${ZKINFO_URL}/api/virtual-projects" \
  -H "Content-Type: application/json"
```

### 6.3 查询虚拟项目详情

```bash
curl -X GET "${ZKINFO_URL}/api/virtual-projects/${VIRTUAL_PROJECT_ID}" \
  -H "Content-Type: application/json"
```

### 6.4 查询虚拟项目端点信息

```bash
curl -X GET "${ZKINFO_URL}/api/virtual-projects/${VIRTUAL_PROJECT_ID}/endpoint" \
  -H "Content-Type: application/json"
```

### 6.5 查询虚拟项目关联的服务

```bash
curl -X GET "${ZKINFO_URL}/api/virtual-projects/${VIRTUAL_PROJECT_ID}/services" \
  -H "Content-Type: application/json"
```

### 6.6 更新虚拟项目服务列表

```bash
curl -X PUT "${ZKINFO_URL}/api/virtual-projects/${VIRTUAL_PROJECT_ID}/services" \
  -H "Content-Type: application/json" \
  -d '{
    "services": [
      {
        "serviceInterface": "com.example.Service1",
        "version": "1.0.0",
        "group": "demo",
        "priority": 0
      }
    ]
  }'
```

### 6.7 查询虚拟项目的工具列表

```bash
curl -X GET "${ZKINFO_URL}/api/virtual-projects/${VIRTUAL_PROJECT_ID}/tools" \
  -H "Content-Type: application/json"
```

### 6.8 重新注册虚拟项目到Nacos

```bash
curl -X POST "${ZKINFO_URL}/api/virtual-projects/${VIRTUAL_PROJECT_ID}/reregister" \
  -H "Content-Type: application/json"
```

### 6.9 根据端点名称删除虚拟项目

```bash
curl -X DELETE "${ZKINFO_URL}/api/virtual-projects/by-endpoint/${ENDPOINT_NAME}" \
  -H "Content-Type: application/json"
```

### 6.10 根据服务名称删除虚拟项目

```bash
curl -X DELETE "${ZKINFO_URL}/api/virtual-projects/by-service/virtual-${ENDPOINT_NAME}" \
  -H "Content-Type: application/json"
```

### 6.11 根据ID删除虚拟项目

```bash
curl -X DELETE "${ZKINFO_URL}/api/virtual-projects/${VIRTUAL_PROJECT_ID}" \
  -H "Content-Type: application/json"
```

---

## 7. 服务审批流程

### 7.1 查询所有审批记录

```bash
curl -X GET "${ZKINFO_URL}/api/approvals?page=1&size=10" \
  -H "Content-Type: application/json"
```

### 7.2 查询待审批记录

```bash
curl -X GET "${ZKINFO_URL}/api/approvals/pending" \
  -H "Content-Type: application/json"
```

### 7.3 查询审批记录详情

```bash
# 替换 {approvalId} 为实际的审批ID
curl -X GET "${ZKINFO_URL}/api/approvals/{approvalId}" \
  -H "Content-Type: application/json"
```

### 7.4 审批通过

```bash
curl -X PUT "${ZKINFO_URL}/api/approvals/{approvalId}/approve" \
  -H "Content-Type: application/json" \
  -d '{
    "reviewerId": 1,
    "reviewerName": "管理员",
    "comment": "审批通过"
  }'
```

### 7.5 审批拒绝

```bash
curl -X PUT "${ZKINFO_URL}/api/approvals/{approvalId}/reject" \
  -H "Content-Type: application/json" \
  -d '{
    "reviewerId": 1,
    "reviewerName": "管理员",
    "comment": "拒绝原因"
  }'
```

### 7.6 取消审批

```bash
curl -X PUT "${ZKINFO_URL}/api/approvals/{approvalId}/cancel" \
  -H "Content-Type: application/json" \
  -d '{
    "operatorId": 1,
    "operatorName": "申请人",
    "comment": "取消申请"
  }'
```

### 7.7 检查服务审批状态

```bash
curl -X GET "${ZKINFO_URL}/api/approvals/check?serviceId={serviceId}" \
  -H "Content-Type: application/json"
```

---

## 8. 接口过滤（白名单）

### 8.1 查询所有过滤器

```bash
curl -X GET "${ZKINFO_URL}/api/filters" \
  -H "Content-Type: application/json"
```

### 8.2 查询启用的过滤器

```bash
curl -X GET "${ZKINFO_URL}/api/filters/enabled" \
  -H "Content-Type: application/json"
```

### 8.3 创建过滤器

```bash
curl -X POST "${ZKINFO_URL}/api/filters" \
  -H "Content-Type: application/json" \
  -d '{
    "filterName": "生产环境白名单",
    "filterType": "WHITELIST",
    "enabled": true,
    "rules": [
      {
        "ruleType": "INTERFACE_PREFIX",
        "ruleValue": "com.example.prod",
        "action": "INCLUDE",
        "priority": 1
      }
    ]
  }'
```

### 8.4 查询过滤器详情

```bash
# 替换 {filterId} 为实际的过滤器ID
curl -X GET "${ZKINFO_URL}/api/filters/{filterId}" \
  -H "Content-Type: application/json"
```

### 8.5 更新过滤器

```bash
curl -X PUT "${ZKINFO_URL}/api/filters/{filterId}" \
  -H "Content-Type: application/json" \
  -d '{
    "filterName": "生产环境白名单（更新）",
    "enabled": true,
    "rules": [
      {
        "ruleType": "INTERFACE_PREFIX",
        "ruleValue": "com.example.prod",
        "action": "INCLUDE",
        "priority": 1
      }
    ]
  }'
```

### 8.6 测试过滤器

```bash
curl -X POST "${ZKINFO_URL}/api/filters/test" \
  -H "Content-Type: application/json" \
  -d '{
    "interfaceName": "com.example.prod.Service",
    "version": "1.0.0",
    "group": "demo"
  }'
```

### 8.7 删除过滤器

```bash
curl -X DELETE "${ZKINFO_URL}/api/filters/{filterId}" \
  -H "Content-Type: application/json"
```

---

## 9. MCP协议调用

### 9.1 MCP Initialize（初始化）

```bash
export SESSION_ID="prod-session-$(date +%s)"

curl -X POST "${ZKINFO_URL}/mcp/message?sessionId=${SESSION_ID}&endpoint=${ENDPOINT_NAME}" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": {
        "name": "production-client",
        "version": "1.0.0"
      }
    }
  }'
```

**预期响应**:
```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "serverInfo": {
      "name": "zkInfo",
      "version": "1.0.0"
    }
  }
}
```

### 9.2 MCP Tools/List（获取工具列表）

```bash
curl -X POST "${ZKINFO_URL}/mcp/message?sessionId=${SESSION_ID}&endpoint=${ENDPOINT_NAME}" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/list",
    "params": {}
  }'
```

**预期响应**:
```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "result": {
    "tools": [
      {
        "name": "com.example.Service.methodName",
        "description": "方法描述",
        "inputSchema": {
          "type": "object",
          "properties": {}
        }
      }
    ]
  }
}
```

### 9.3 MCP Tools/Call（调用工具）

```bash
curl -X POST "${ZKINFO_URL}/mcp/message?sessionId=${SESSION_ID}&endpoint=${ENDPOINT_NAME}" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "3",
    "method": "tools/call",
    "params": {
      "name": "com.example.Service.methodName",
      "arguments": {
        "param1": "value1",
        "param2": 123
      }
    }
  }'
```

**预期响应**:
```json
{
  "jsonrpc": "2.0",
  "id": "3",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "调用结果"
      }
    ],
    "isError": false
  }
}
```

### 9.4 MCP Resources/List（列出资源）

```bash
curl -X GET "${ZKINFO_URL}/mcp/resources" \
  -H "Content-Type: application/json"
```

### 9.5 MCP Prompts/List（列出提示）

```bash
curl -X GET "${ZKINFO_URL}/mcp/prompts" \
  -H "Content-Type: application/json"
```

---

## 10. SSE端点验证

### 10.1 测试SSE连接（通过端点名称）

```bash
# 使用 timeout 命令，3秒后自动断开
timeout 3 curl -N "${ZKINFO_URL}/sse/${ENDPOINT_NAME}" \
  -H "Accept: text/event-stream"
```

### 10.2 测试SSE连接（通过虚拟服务名称）

```bash
timeout 3 curl -N "${ZKINFO_URL}/sse/virtual-${ENDPOINT_NAME}" \
  -H "Accept: text/event-stream"
```

### 10.3 通过SSE发送MCP消息

```bash
# 在一个终端启动SSE连接
curl -N "${ZKINFO_URL}/sse/${ENDPOINT_NAME}" \
  -H "Accept: text/event-stream" \
  -H "X-Session-Id: ${SESSION_ID}"

# 在另一个终端发送MCP消息（需要通过WebSocket或其他方式）
```

---

## 11. Nacos注册验证

### 11.1 查询Nacos服务列表

```bash
curl -X GET "${NACOS_URL}/nacos/v1/ns/service/list?pageNo=1&pageSize=10" \
  -H "Content-Type: application/json"
```

### 11.2 查询虚拟项目在Nacos中的实例

```bash
curl -X GET "${NACOS_URL}/nacos/v3/client/ns/instance/list?namespaceId=${NACOS_NAMESPACE}&groupName=${NACOS_GROUP}&serviceName=virtual-${ENDPOINT_NAME}" \
  -H "Content-Type: application/json" \
  -H "User-Agent: Nacos-Bash-Client"
```

**预期响应**:
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "ip": "127.0.0.1",
      "port": 9091,
      "healthy": true,
      "enabled": true
    }
  ]
}
```

### 11.3 手动注册服务到Nacos

```bash
curl -X POST "${NACOS_URL}/nacos/v3/client/ns/instance" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "serviceName=virtual-${ENDPOINT_NAME}&ip=127.0.0.1&port=9091&groupName=${NACOS_GROUP}&namespaceId=${NACOS_NAMESPACE}"
```

### 11.4 从Nacos注销服务

```bash
curl -X DELETE "${NACOS_URL}/nacos/v3/client/ns/instance?serviceName=virtual-${ENDPOINT_NAME}&ip=127.0.0.1&port=9091&groupName=${NACOS_GROUP}&namespaceId=${NACOS_NAMESPACE}&ephemeral=false" \
  -H "Content-Type: application/json"
```

---

## 12. 心跳检测验证

### 12.1 查询服务节点状态

```bash
curl -X GET "${ZKINFO_URL}/api/dubbo-services/{serviceId}/nodes" \
  -H "Content-Type: application/json" | jq '.[] | {address, isOnline, isHealthy, lastHeartbeatTime}'
```

### 12.2 查询在线节点数量

```bash
curl -X GET "${ZKINFO_URL}/api/stats" \
  -H "Content-Type: application/json" | jq '.onlineProviders'
```

### 12.3 查询健康节点数量

```bash
curl -X GET "${ZKINFO_URL}/api/stats" \
  -H "Content-Type: application/json" | jq '.healthyProviders'
```

---

## 13. 故障排查

### 13.1 检查服务日志

```bash
# 查看应用日志（根据实际部署方式调整）
tail -f /var/log/zkinfo/zkinfo.log

# 或使用 kubectl（如果部署在K8s）
kubectl logs -f deployment/zkinfo
```

### 13.2 检查数据库连接

```bash
# 通过健康检查接口
curl -X GET "${ZKINFO_URL}/actuator/health" | jq '.components.db'
```

### 13.3 检查ZooKeeper连接

```bash
# 测试ZooKeeper连接
curl -X GET "${ZKINFO_URL}/api/debug/zk-tree" | jq '.status'
```

### 13.4 检查Nacos连接

```bash
# 测试Nacos连接
curl -X GET "${NACOS_URL}/nacos/v1/console/health" \
  -H "Content-Type: application/json"
```

### 13.5 验证服务是否在白名单中

```bash
# 查询接口列表
curl -X GET "${ZKINFO_URL}/api/interfaces" | jq '.[] | select(startswith("com.example"))'
```

### 13.6 检查服务审批状态

```bash
# 查询服务详情
curl -X GET "${ZKINFO_URL}/api/dubbo-services/{serviceId}" | jq '.approvalStatus'
```

---

## 14. 完整验证流程示例

以下是一个完整的生产环境验证流程：

```bash
#!/bin/bash

# 设置环境变量
export ZKINFO_URL="http://your-zkinfo-host:9091"
export NACOS_URL="http://your-nacos-host:8848"
export NACOS_NAMESPACE="public"
export NACOS_GROUP="mcp-server"

# 1. 环境检查
echo "=== 1. 环境检查 ==="
curl -s "${ZKINFO_URL}/actuator/health" | jq '.status'

# 2. 查询服务列表
echo "=== 2. 查询服务列表 ==="
SERVICE_ID=$(curl -s "${ZKINFO_URL}/api/dubbo-services?page=1&size=1" | jq -r '.data[0].id')
echo "Service ID: $SERVICE_ID"

# 3. 创建虚拟项目
echo "=== 3. 创建虚拟项目 ==="
VIRTUAL_PROJECT=$(curl -s -X POST "${ZKINFO_URL}/api/virtual-projects" \
  -H "Content-Type: application/json" \
  -d '{
    "endpointName": "prod-test-'$(date +%s)'",
    "projectName": "生产测试项目",
    "projectCode": "prod-test-'$(date +%s)'",
    "description": "测试",
    "services": [],
    "autoRegister": true
  }')
VIRTUAL_PROJECT_ID=$(echo $VIRTUAL_PROJECT | jq -r '.project.id')
ENDPOINT_NAME=$(echo $VIRTUAL_PROJECT | jq -r '.endpoint.endpointName')
echo "Virtual Project ID: $VIRTUAL_PROJECT_ID"
echo "Endpoint Name: $ENDPOINT_NAME"

# 4. 验证Nacos注册
echo "=== 4. 验证Nacos注册 ==="
sleep 5
curl -s "${NACOS_URL}/nacos/v3/client/ns/instance/list?namespaceId=${NACOS_NAMESPACE}&groupName=${NACOS_GROUP}&serviceName=virtual-${ENDPOINT_NAME}" \
  -H "Content-Type: application/json" \
  -H "User-Agent: Nacos-Bash-Client" | jq '.data | length'

# 5. MCP调用测试
echo "=== 5. MCP调用测试 ==="
SESSION_ID="test-$(date +%s)"
curl -s -X POST "${ZKINFO_URL}/mcp/message?sessionId=${SESSION_ID}&endpoint=${ENDPOINT_NAME}" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/list",
    "params": {}
  }' | jq '.result.tools | length'

# 6. 清理测试数据
echo "=== 6. 清理测试数据 ==="
curl -s -X DELETE "${ZKINFO_URL}/api/virtual-projects/${VIRTUAL_PROJECT_ID}" | jq '.message'

echo "=== 验证完成 ==="
```

---

## 15. 注意事项

### 15.1 生产环境建议

1. **逐步验证**: 先验证基础功能，再验证高级功能
2. **备份数据**: 在执行删除操作前，确保已备份重要数据
3. **监控日志**: 实时监控服务日志，及时发现问题
4. **权限控制**: 确保API访问权限配置正确
5. **性能测试**: 在生产环境验证时，注意观察系统性能

### 15.2 常见问题

1. **服务未注册到Nacos**: 检查Nacos连接配置和网络连通性
2. **MCP调用失败**: 检查服务是否已审批通过，节点是否在线
3. **SSE连接失败**: 检查端点名称是否正确，服务是否已注册
4. **审批流程异常**: 检查服务状态和审批记录

### 15.3 安全建议

1. **使用HTTPS**: 生产环境建议使用HTTPS协议
2. **API认证**: 配置API访问认证机制
3. **日志脱敏**: 确保日志中不包含敏感信息
4. **访问控制**: 限制API访问来源IP

---

## 16. 附录

### 16.1 常用jq命令

```bash
# 提取JSON字段
curl ... | jq '.field'

# 提取数组元素
curl ... | jq '.data[0]'

# 过滤数组
curl ... | jq '.data[] | select(.status == "ACTIVE")'

# 格式化输出
curl ... | jq -r '.field'  # 输出原始字符串（无引号）
```

### 16.2 环境变量模板

```bash
# .env 文件示例
ZKINFO_URL=http://zkinfo.example.com:9091
NACOS_URL=http://nacos.example.com:8848
NACOS_NAMESPACE=production
NACOS_GROUP=mcp-server
```

### 16.3 快速参考

| 功能 | 方法 | 路径 |
|------|------|------|
| 健康检查 | GET | `/actuator/health` |
| 服务列表 | GET | `/api/dubbo-services` |
| 创建虚拟项目 | POST | `/api/virtual-projects` |
| MCP调用 | POST | `/mcp/message` |
| SSE连接 | GET | `/sse/{endpoint}` |

---

**文档维护**: 如有问题或建议，请联系开发团队。

