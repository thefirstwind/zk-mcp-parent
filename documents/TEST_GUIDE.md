# ZK-MCP 项目完整测试指南

**创建日期**: 2025-12-15  
**版本**: 1.0.0  
**目的**: 提供完整的功能测试步骤，逐步验证所有功能

---

## 📋 目录

1. [测试环境准备](#1-测试环境准备)
2. [基础功能测试](#2-基础功能测试)
3. [三层过滤机制测试](#3-三层过滤机制测试)
4. [项目管理功能测试](#4-项目管理功能测试)
5. [虚拟项目功能测试](#5-虚拟项目功能测试)
6. [多 SSE 端点功能测试](#6-多-sse-端点功能测试)
7. [ZooKeeper监听优化测试](#7-zookeeper监听优化测试)
8. [Nacos注册功能测试](#8-nacos注册功能测试)
9. [集成测试](#9-集成测试)
10. [性能测试](#10-性能测试)
11. [问题排查](#11-问题排查)

---

## 1. 测试环境准备

### 1.1 前置条件检查

```bash
# 检查Java版本（需要17+）
java -version

# 检查Maven版本
mvn -version

# 检查ZooKeeper是否运行
nc -z localhost 2181 && echo "ZooKeeper运行中" || echo "ZooKeeper未运行"

# 检查Nacos是否运行
nc -z localhost 8848 && echo "Nacos运行中" || echo "Nacos未运行"

# 检查MySQL是否运行（mcp-router-v3需要）
nc -z localhost 3306 && echo "MySQL运行中" || echo "MySQL未运行"
```

### 1.2 启动依赖服务

#### 启动ZooKeeper
```bash
# 如果使用Docker
docker-compose -f zookeeper/docker-compose.yml up -d

# 或者使用本地安装的ZooKeeper
zkServer.sh start
```

#### 启动Nacos
```bash
# 进入Nacos目录
cd nacos/bin

# Linux/Mac
sh startup.sh -m standalone

# Windows
startup.cmd -m standalone
```

#### 启动MySQL（可选，mcp-router-v3需要）
```bash
# 使用Docker
docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8.0

# 创建数据库
mysql -h localhost -u root -p -e "CREATE DATABASE IF NOT EXISTS mcp_bridge;"
```

### 1.3 编译项目

```bash
cd /Users/shine/projects.mcp-router-sse-parent/zk-mcp-parent/zkInfo
mvn clean compile -DskipTests
```

---

## 2. 基础功能测试

### 2.1 启动zkInfo服务

```bash
cd /Users/shine/projects.mcp-router-sse-parent/zk-mcp-parent/zkInfo
bash start-and-verify.sh
```

**验证步骤**:
1. ✅ 检查服务是否启动成功
2. ✅ 访问健康检查端点: `http://localhost:9091/actuator/health`
3. ✅ 访问API文档: `http://localhost:9091/v3/api-docs`
4. ✅ 访问Swagger UI: `http://localhost:9091/swagger-ui.html`

**预期结果**:
- 服务启动时间 < 5秒
- 健康检查返回 `{"status":"UP"}`
- API文档可访问
- Swagger UI可访问

### 2.2 验证基础API端点

```bash
# 1. 健康检查
curl http://localhost:9091/actuator/health | jq

# 2. 统计信息
curl http://localhost:9091/api/stats | jq

# 3. Provider列表
curl http://localhost:9091/api/providers | jq

# 4. 已注册服务列表
curl http://localhost:9091/api/registered-services | jq

# 5. 应用列表
curl http://localhost:9091/api/applications | jq

# 6. 接口列表
curl http://localhost:9091/api/interfaces | jq
```

**预期结果**:
- 所有端点返回200状态码
- 返回JSON格式数据
- 无错误信息

---

## 3. 三层过滤机制测试

### 3.1 项目级过滤测试

#### 步骤1: 创建项目

```bash
# 创建项目（API已实现）
curl -X POST http://localhost:9091/api/projects \
  -H "Content-Type: application/json" \
  -d '{
    "projectCode": "TEST_PROJECT_001",
    "projectName": "测试项目1",
    "projectType": "REAL",
    "description": "用于测试的项目",
    "status": "ACTIVE"
  }'

# 预期响应:
# {
#   "id": 1765778146620,
#   "projectCode": "TEST_PROJECT_001",
#   "projectName": "测试项目1",
#   "projectType": "REAL",
#   "status": "ACTIVE",
#   "message": "项目创建成功"
# }
```

#### 步骤2: 关联服务到项目

```bash
# 关联服务到项目
curl -X POST http://localhost:9091/api/projects/1/services \
  -H "Content-Type: application/json" \
  -d '{
    "serviceInterface": "com.zkinfo.demo.service.UserService",
    "serviceVersion": "1.0.0",
    "serviceGroup": "demo",
    "enabled": true
  }'
```

#### 步骤3: 验证过滤效果

```bash
# 检查服务是否在项目中
curl http://localhost:9091/api/projects/1/services | jq

# 验证过滤服务是否正确识别
# 应该只有项目关联的服务被采集
```

**预期结果**:
- 项目创建成功
- 服务关联成功
- 只有项目关联的服务被采集

### 3.2 服务级过滤测试

#### 步骤1: 添加过滤规则

```bash
# 添加排除规则（排除test开头的服务）（API已实现）
curl -X POST http://localhost:9091/api/filters \
  -H "Content-Type: application/json" \
  -d '{
    "filterType": "PREFIX",
    "filterValue": "test",
    "filterOperator": "EXCLUDE",
    "priority": 10,
    "enabled": true,
    "description": "排除test开头的服务"
  }'

# 预期响应:
# {
#   "id": 1,
#   "filterType": "PREFIX",
#   "filterValue": "test",
#   "filterOperator": "EXCLUDE",
#   "priority": 10,
#   "enabled": true,
#   "message": "过滤规则创建成功"
# }
```

#### 步骤2: 验证过滤效果

```bash
# 检查过滤规则是否生效
curl http://localhost:9091/api/filters | jq

# 验证test开头的服务是否被过滤
```

**预期结果**:
- 过滤规则添加成功
- test开头的服务被排除
- 其他服务正常采集

### 3.3 审批级过滤测试

#### 步骤1: 提交服务审批

```bash
# 提交服务审批申请（API已实现）
curl -X POST http://localhost:9091/api/approvals \
  -H "Content-Type: application/json" \
  -d '{
    "serviceInterface": "com.zkinfo.demo.service.OrderService",
    "serviceVersion": "1.0.0",
    "serviceGroup": "demo",
    "applicantId": 1,
    "applicantName": "测试用户",
    "reason": "需要接入MCP系统"
  }'

# 预期响应:
# {
#   "id": 1,
#   "serviceInterface": "com.zkinfo.demo.service.OrderService",
#   "serviceVersion": "1.0.0",
#   "status": "PENDING",
#   "message": "审批申请创建成功"
# }
```

#### 步骤2: 审批通过

```bash
# 审批通过（API已实现）
curl -X PUT http://localhost:9091/api/approvals/1/approve \
  -H "Content-Type: application/json" \
  -d '{
    "approverId": 2,
    "approverName": "管理员",
    "comment": "同意"
  }'

# 预期响应:
# {
#   "id": 1,
#   "status": "APPROVED",
#   "approverName": "管理员",
#   "message": "审批通过"
# }
```

#### 步骤3: 验证审批效果

```bash
# 检查服务是否已审批
curl http://localhost:9091/api/approvals/1 | jq

# 验证只有审批通过的服务被采集
```

**预期结果**:
- 审批申请提交成功
- 审批通过后服务被采集
- 未审批的服务被过滤

---

## 4. 项目管理功能测试

### 4.1 创建实际项目

```bash
# 创建实际项目
curl -X POST http://localhost:9091/api/projects \
  -H "Content-Type: application/json" \
  -d '{
    "projectCode": "USER_CENTER",
    "projectName": "用户中心项目",
    "projectType": "REAL",
    "description": "用户中心相关服务",
    "ownerId": 1,
    "ownerName": "张三",
    "status": "ACTIVE"
  }'
```

**验证**:
```bash
# 获取项目列表
curl http://localhost:9091/api/projects | jq

# 获取项目详情
curl http://localhost:9091/api/projects/1 | jq
```

### 4.2 关联服务到项目

```bash
# 关联多个服务
curl -X POST http://localhost:9091/api/projects/1/services \
  -H "Content-Type: application/json" \
  -d '{
    "serviceInterface": "com.zkinfo.demo.service.UserService",
    "serviceVersion": "1.0.0",
    "serviceGroup": "demo",
    "priority": 10,
    "enabled": true
  }'

curl -X POST http://localhost:9091/api/projects/1/services \
  -H "Content-Type: application/json" \
  -d '{
    "serviceInterface": "com.zkinfo.demo.service.OrderService",
    "serviceVersion": "1.0.0",
    "serviceGroup": "demo",
    "priority": 5,
    "enabled": true
  }'
```

**验证**:
```bash
# 获取项目的所有服务
curl http://localhost:9091/api/projects/1/services | jq

# 验证服务数量
curl http://localhost:9091/api/projects/1/services | jq 'length'
```

### 4.3 更新项目服务

```bash
# 更新服务优先级
curl -X PUT http://localhost:9091/api/projects/1/services/1 \
  -H "Content-Type: application/json" \
  -d '{
    "priority": 20,
    "enabled": true
  }'
```

### 4.4 删除项目服务

```bash
# 删除服务关联
curl -X DELETE http://localhost:9091/api/projects/1/services/1
```

---

## 5. 虚拟项目功能测试

### 5.1 创建虚拟项目

```bash
# 创建虚拟项目
curl -X POST http://localhost:9091/api/virtual-projects \
  -H "Content-Type: application/json" \
  -d '{
    "name": "数据分析场景",
    "description": "用于数据分析的虚拟项目",
    "endpointName": "data-analysis",
    "services": [
      {
        "serviceInterface": "com.zkinfo.demo.service.UserService",
        "version": "1.0.0",
        "group": "demo",
        "priority": 10
      },
      {
        "serviceInterface": "com.zkinfo.demo.service.OrderService",
        "version": "1.0.0",
        "group": "demo",
        "priority": 10
      },
      {
        "serviceInterface": "com.zkinfo.demo.service.ProductService",
        "version": "1.0.0",
        "group": "demo",
        "priority": 5
      }
    ],
    "autoRegister": true
  }'
```

**验证**:
```bash
# 获取虚拟项目列表（注意：虚拟项目ID是时间戳，不是1）
curl http://localhost:9091/api/virtual-projects | jq

# 获取虚拟项目ID（从列表中提取）
VIRTUAL_PROJECT_ID=$(curl -s http://localhost:9091/api/virtual-projects | jq -r '.[0].project.id')
echo "虚拟项目ID: $VIRTUAL_PROJECT_ID"

# 获取虚拟项目详情（使用实际的虚拟项目ID）
curl http://localhost:9091/api/virtual-projects/$VIRTUAL_PROJECT_ID | jq

# 或者直接使用第一个虚拟项目的ID
curl http://localhost:9091/api/virtual-projects/$(curl -s http://localhost:9091/api/virtual-projects | jq -r '.[0].project.id') | jq

# 检查Endpoint映射
curl http://localhost:9091/api/virtual-projects/$VIRTUAL_PROJECT_ID/endpoint | jq
```

**预期结果**:
- 虚拟项目创建成功
- Endpoint映射创建成功
- 服务关联成功
- 自动注册到Nacos（如果autoRegister=true）

### 5.2 更新虚拟项目服务

```bash
# 获取虚拟项目ID
VIRTUAL_PROJECT_ID=$(curl -s http://localhost:9091/api/virtual-projects | jq -r '.[0].project.id')

# 更新虚拟项目的服务列表
curl -X PUT http://localhost:9091/api/virtual-projects/$VIRTUAL_PROJECT_ID/services \
  -H "Content-Type: application/json" \
  -d '{
    "services": [
      {
        "serviceInterface": "com.zkinfo.demo.service.UserService",
        "version": "1.0.0",
        "group": "demo",
        "priority": 10
      },
      {
        "serviceInterface": "com.zkinfo.demo.service.ProductService",
        "version": "1.0.0",
        "group": "demo",
        "priority": 10
      }
    ]
  }'
```

**验证**:
```bash
# 获取虚拟项目ID
VIRTUAL_PROJECT_ID=$(curl -s http://localhost:9091/api/virtual-projects | jq -r '.[0].project.id')

# 检查服务列表是否更新
curl http://localhost:9091/api/virtual-projects/$VIRTUAL_PROJECT_ID/services | jq

# 验证Nacos注册是否更新
curl http://localhost:9091/api/registered-services | jq
```

### 5.3 预览虚拟项目工具列表

```bash
# 获取虚拟项目ID
VIRTUAL_PROJECT_ID=$(curl -s http://localhost:9091/api/virtual-projects | jq -r '.[0].project.id')

# 获取虚拟项目的工具列表（预览）
curl http://localhost:9091/api/virtual-projects/$VIRTUAL_PROJECT_ID/tools | jq
```

**预期结果**:
- 返回所有服务的工具列表
- 工具格式符合MCP标准
- 包含完整的输入输出schema

### 5.4 删除虚拟项目

```bash
# 获取虚拟项目ID
VIRTUAL_PROJECT_ID=$(curl -s http://localhost:9091/api/virtual-projects | jq -r '.[0].project.id')

# 删除虚拟项目
curl -X DELETE http://localhost:9091/api/virtual-projects/$VIRTUAL_PROJECT_ID
```

**验证**:
```bash
# 验证虚拟项目已删除（应该返回404）
curl http://localhost:9091/api/virtual-projects/$VIRTUAL_PROJECT_ID

# 验证Nacos注册已注销
curl http://localhost:9091/api/registered-services | jq
```

### 5.5 测试虚拟项目的 SSE 端点

虚拟项目创建后，可以通过多种方式访问其 SSE 端点：

```bash
# 获取虚拟项目信息
VIRTUAL_PROJECT_ID=$(curl -s http://localhost:9091/api/virtual-projects | jq -r '.[0].project.id')
ENDPOINT_NAME=$(curl -s http://localhost:9091/api/virtual-projects | jq -r '.[0].endpoint.endpointName')

echo "虚拟项目ID: $VIRTUAL_PROJECT_ID"
echo "Endpoint名称: $ENDPOINT_NAME"
```

#### 方式1: 使用虚拟项目 ID

```bash
# 建立 SSE 连接（使用虚拟项目 ID）
curl -N "http://localhost:9091/sse/$VIRTUAL_PROJECT_ID" \
  -H "Accept: text/event-stream" \
  --max-time 5
```

#### 方式2: 使用 endpoint 名称

```bash
# 建立 SSE 连接（使用 endpoint 名称）
curl -N "http://localhost:9091/sse/$ENDPOINT_NAME" \
  -H "Accept: text/event-stream" \
  --max-time 5
```

**预期响应**:
```
event:endpoint
data:http://localhost:9091/mcp/message?sessionId=xxx-xxx-xxx

event:heartbeat
data:{"type":"heartbeat","timestamp":1234567890}
```

#### 方式3: 发送 MCP 消息

```bash
# 1. 建立 SSE 连接并获取 sessionId（从响应中提取）
SESSION_ID=$(curl -s -N "http://localhost:9091/sse/$ENDPOINT_NAME" \
  -H "Accept: text/event-stream" \
  --max-time 2 | grep "sessionId" | head -1 | sed 's/.*sessionId=\([^&]*\).*/\1/')

echo "Session ID: $SESSION_ID"

# 2. 发送 initialize 请求
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

# 3. 发送 tools/list 请求
curl -X POST "http://localhost:9091/mcp/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/list",
    "params": {}
  }'

# 4. 发送 tools/call 请求（调用虚拟项目中的工具）
curl -X POST "http://localhost:9091/mcp/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "3",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.OrderService.getOrderById",
      "arguments": ["ORD001"]
    }
  }'
```

**预期结果**:
- ✅ SSE 连接成功建立
- ✅ Initialize 请求返回 HTTP 202 Accepted
- ✅ Tools/list 返回虚拟项目中所有服务的工具列表
- ✅ Tools/call 成功调用 Dubbo 服务并返回结果

---

## 6. 多 SSE 端点功能测试

### 6.1 支持的 Endpoint 格式

zkInfo 支持以下多种 endpoint 格式来建立 SSE 连接：

#### 格式1: 标准 SSE 端点（需要 serviceName 参数）

```bash
# 使用查询参数指定服务名称
curl -N "http://localhost:9091/sse?serviceName=zk-mcp-com-zkinfo-demo-service-orderservice-1.0.0" \
  -H "Accept: text/event-stream"
```

#### 格式2: 使用项目代码

```bash
# 获取项目代码
PROJECT_CODE=$(curl -s http://localhost:9091/api/projects | jq -r '.[] | select(.projectType == "REAL") | .projectCode' | head -1)

# 使用项目代码建立 SSE 连接
curl -N "http://localhost:9091/sse/$PROJECT_CODE" \
  -H "Accept: text/event-stream"
```

#### 格式3: 使用项目名称

```bash
# 获取项目名称
PROJECT_NAME=$(curl -s http://localhost:9091/api/projects | jq -r '.[] | select(.projectType == "REAL") | .projectName' | head -1)

# 使用项目名称建立 SSE 连接
curl -N "http://localhost:9091/sse/$PROJECT_NAME" \
  -H "Accept: text/event-stream"
```

#### 格式4: 使用虚拟项目 endpoint 名称

```bash
# 获取虚拟项目 endpoint 名称
ENDPOINT_NAME=$(curl -s http://localhost:9091/api/virtual-projects | jq -r '.[] | .endpoint.endpointName // empty' | grep -v "null" | head -1)

# 使用 endpoint 名称建立 SSE 连接
curl -N "http://localhost:9091/sse/$ENDPOINT_NAME" \
  -H "Accept: text/event-stream"
```

#### 格式5: 使用虚拟项目 ID

```bash
# 获取虚拟项目 ID
VIRTUAL_PROJECT_ID=$(curl -s http://localhost:9091/api/virtual-projects | jq -r '.[] | .project.id' | head -1)

# 使用虚拟项目 ID 建立 SSE 连接
curl -N "http://localhost:9091/sse/$VIRTUAL_PROJECT_ID" \
  -H "Accept: text/event-stream"
```

#### 格式6: 使用 MCP 服务名称

```bash
# 获取 MCP 服务名称
MCP_SERVICE=$(curl -s "http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=10&namespaceId=public&groupName=mcp-server" \
  | jq -r '.doms[]? | select(startswith("zk-mcp-"))' | head -1)

# 使用 MCP 服务名称建立 SSE 连接
curl -N "http://localhost:9091/sse/$MCP_SERVICE" \
  -H "Accept: text/event-stream"
```

### 6.2 完整测试流程

```bash
# 使用自动化测试脚本
cd /Users/shine/projects.mcp-router-sse-parent
./zk-mcp-parent/zkInfo/test-sse-endpoints-complete.sh
```

### 6.3 验证多个 Endpoint 同时连接

```bash
# 测试多个不同的 endpoint 同时连接
ENDPOINTS=(
  "zk-mcp-com-zkinfo-demo-service-orderservice-1.0.0"
  "TEST_PROJECT_001"
  "data-analysis"
)

for endpoint in "${ENDPOINTS[@]}"; do
  echo "测试 endpoint: $endpoint"
  curl -s -w "\nHTTP状态码: %{http_code}\n" \
    "http://localhost:9091/sse/$endpoint" \
    -H "Accept: text/event-stream" \
    --max-time 2 > /dev/null 2>&1
done
```

**预期结果**:
- ✅ 所有 endpoint 格式都能成功建立 SSE 连接
- ✅ 每个连接返回独立的 sessionId
- ✅ 多个 endpoint 可以同时连接
- ✅ 每个连接的消息不会混淆

### 6.4 MCP 消息端点

#### 通用消息端点（推荐）

```bash
# 通过 sessionId 自动查找对应的 endpoint
curl -X POST "http://localhost:9091/mcp/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/list",
    "params": {}
  }'
```

#### 指定 endpoint 的消息端点

```bash
# 直接指定 endpoint
curl -X POST "http://localhost:9091/mcp/$ENDPOINT_NAME/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/list",
    "params": {}
  }'
```

---

## 7. ZooKeeper监听优化测试

### 6.1 验证监听优化效果

#### 步骤1: 启动demo-provider

```bash
cd /Users/shine/projects.mcp-router-sse-parent/zk-mcp-parent/demo-provider
mvn spring-boot:run
```

#### 步骤2: 检查zkInfo日志

```bash
# 查看zkInfo日志，确认只监听了项目包含的服务
tail -f /Users/shine/projects.mcp-router-sse-parent/zk-mcp-parent/zkInfo/logs/zkinfo.log | grep -E "(发现新服务|跳过监听|被过滤)"
```

**预期结果**:
- 只有项目包含的服务被监听
- 其他服务被跳过（日志显示"跳过监听"）
- 监听数量大幅减少

### 6.2 验证服务发现

```bash
# 检查发现的Provider
curl http://localhost:9091/api/providers | jq

# 检查统计信息
curl http://localhost:9091/api/stats | jq
```

**预期结果**:
- 只显示项目包含的服务
- Provider数量符合预期
- 统计信息准确

### 6.3 验证实时监听

#### 步骤1: 创建项目并关联服务

```bash
# 创建项目
curl -X POST http://localhost:9091/api/projects ...

# 关联服务
curl -X POST http://localhost:9091/api/projects/1/services ...
```

#### 步骤2: 启动新的Provider

```bash
# 启动包含该服务的Provider
# 应该立即被zkInfo发现并处理
```

#### 步骤3: 验证实时发现

```bash
# 检查Provider是否被立即发现
watch -n 1 'curl -s http://localhost:9091/api/providers | jq length'
```

**预期结果**:
- 新Provider被立即发现
- 应用过滤规则
- 只有通过过滤的服务被处理

---

## 8. Nacos注册功能测试

### 7.1 验证服务注册到Nacos

#### 步骤1: 创建项目并关联服务

```bash
# 创建项目
curl -X POST http://localhost:9091/api/projects ...

# 关联服务
curl -X POST http://localhost:9091/api/projects/1/services ...
```

#### 步骤2: 审批服务

```bash
# 审批服务
curl -X PUT http://localhost:9091/api/approvals/1/approve ...
```

#### 步骤3: 验证Nacos注册

```bash
# 检查已注册服务
curl http://localhost:9091/api/registered-services | jq

# 检查Nacos控制台
# 访问: http://localhost:8848/nacos
# 用户名/密码: nacos/nacos
# 查看"服务管理" -> "服务列表"
```

**预期结果**:
- 服务注册到Nacos成功
- Nacos中可以看到MCP服务
- 服务实例信息正确

### 7.2 验证虚拟项目注册

#### 步骤1: 创建虚拟项目

```bash
curl -X POST http://localhost:9091/api/virtual-projects ...
```

#### 步骤2: 验证Nacos注册

```bash
# 检查虚拟项目是否注册到Nacos
curl http://localhost:9091/api/registered-services | jq

# 在Nacos中查找虚拟项目的服务名称
# 服务名称格式: mcp-{endpointName}
```

**预期结果**:
- 虚拟项目注册为独立的MCP服务
- 服务名称符合格式: `mcp-{endpointName}`
- 包含所有编排服务的工具

### 7.3 验证服务更新

#### 步骤1: 更新项目服务列表

```bash
# 添加新服务到项目
curl -X POST http://localhost:9091/api/projects/1/services ...
```

#### 步骤2: 验证Nacos更新

```bash
# 检查服务是否更新
curl http://localhost:9091/api/registered-services | jq

# 检查Nacos中的服务配置是否更新
```

**预期结果**:
- 服务配置自动更新
- 工具列表更新
- Nacos中的配置同步更新

---

## 9. 集成测试

### 8.1 端到端测试流程

#### 完整流程测试

```bash
# 1. 创建项目
PROJECT_ID=$(curl -s -X POST http://localhost:9091/api/projects \
  -H "Content-Type: application/json" \
  -d '{
    "projectCode": "E2E_TEST",
    "projectName": "端到端测试项目",
    "projectType": "REAL",
    "status": "ACTIVE"
  }' | jq -r '.id')

echo "项目ID: $PROJECT_ID"

# 2. 关联服务
curl -X POST http://localhost:9091/api/projects/$PROJECT_ID/services \
  -H "Content-Type: application/json" \
  -d '{
    "serviceInterface": "com.zkinfo.demo.service.UserService",
    "serviceVersion": "1.0.0",
    "serviceGroup": "demo",
    "enabled": true
  }'

# 3. 提交审批
APPROVAL_ID=$(curl -s -X POST http://localhost:9091/api/approvals \
  -H "Content-Type: application/json" \
  -d '{
    "serviceInterface": "com.zkinfo.demo.service.UserService",
    "serviceVersion": "1.0.0",
    "serviceGroup": "demo",
    "projectId": '$PROJECT_ID'
  }' | jq -r '.id')

echo "审批ID: $APPROVAL_ID"

# 4. 审批通过
curl -X PUT http://localhost:9091/api/approvals/$APPROVAL_ID/approve \
  -H "Content-Type: application/json" \
  -d '{
    "approverId": 1,
    "approverName": "管理员"
  }'

# 5. 验证服务被采集
sleep 5
curl http://localhost:9091/api/providers | jq '.[] | select(.interfaceName == "com.zkinfo.demo.service.UserService")'

# 6. 验证服务注册到Nacos
curl http://localhost:9091/api/registered-services | jq

# 7. 创建虚拟项目
VIRTUAL_PROJECT_ID=$(curl -s -X POST http://localhost:9091/api/virtual-projects \
  -H "Content-Type: application/json" \
  -d '{
    "name": "端到端测试虚拟项目",
    "endpointName": "e2e-test",
    "services": [{
      "serviceInterface": "com.zkinfo.demo.service.UserService",
      "version": "1.0.0",
      "group": "demo"
    }]
  }' | jq -r '.project.id')

echo "虚拟项目ID: $VIRTUAL_PROJECT_ID"

# 8. 验证虚拟项目注册
curl http://localhost:9091/api/registered-services | jq
```

**预期结果**:
- 所有步骤执行成功
- 服务被正确采集
- 服务注册到Nacos
- 虚拟项目创建并注册成功

### 8.2 mcp-router-v3集成测试

#### 步骤1: 启动mcp-router-v3

```bash
cd /Users/shine/projects.mcp-router-sse-parent/mcp-router-v3
mvn spring-boot:run
```

#### 步骤2: 验证路由功能

```bash
# 测试SSE连接
curl -N http://localhost:8052/sse/data-analysis

# 测试消息端点
curl -X POST http://localhost:8052/mcp/data-analysis/message?sessionId=ecc7fb6b-c680-475d-ad83-80fe9d3f60db \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list"
  }'
```

**预期结果**:
- mcp-router-v3可以路由到虚拟项目
- 返回正确的工具列表
- SSE连接正常

---

## 10. 性能测试

### 9.1 监听性能测试

#### 测试场景: 大量服务场景

```bash
# 1. 创建多个项目
for i in {1..10}; do
  curl -X POST http://localhost:9091/api/projects \
    -H "Content-Type: application/json" \
    -d "{
      \"projectCode\": \"PERF_TEST_$i\",
      \"projectName\": \"性能测试项目$i\",
      \"projectType\": \"REAL\",
      \"status\": \"ACTIVE\"
    }"
done

# 2. 关联服务到项目
for i in {1..10}; do
  curl -X POST http://localhost:9091/api/projects/$i/services \
    -H "Content-Type: application/json" \
    -d '{
      "serviceInterface": "com.zkinfo.demo.service.UserService",
      "serviceVersion": "1.0.0",
      "serviceGroup": "demo"
    }'
done

# 3. 监控监听性能
# 查看日志中的监听数量
tail -f logs/zkinfo.log | grep -c "发现新服务"
```

**预期结果**:
- 只监听项目包含的服务
- 监听数量大幅减少（90%+）
- 性能提升明显

### 9.2 过滤性能测试

```bash
# 测试过滤规则的性能
time curl http://localhost:9091/api/providers | jq length

# 测试大量过滤规则下的性能
for i in {1..100}; do
  curl -X POST http://localhost:9091/api/filters \
    -H "Content-Type: application/json" \
    -d "{
      \"filterType\": \"PATTERN\",
      \"filterValue\": \"test$i.*\",
      \"filterOperator\": \"EXCLUDE\",
      \"priority\": $i
    }"
done

# 再次测试性能
time curl http://localhost:9091/api/providers | jq length
```

---

## 11. 问题排查

### 10.1 服务启动失败

**症状**: 服务无法启动

**排查步骤**:
1. 检查日志文件
   ```bash
   tail -100 logs/zkinfo.log
   ```

2. 检查端口占用
   ```bash
   lsof -i :9091
   ```

3. 检查依赖服务
   ```bash
   # ZooKeeper
   nc -z localhost 2181
   
   # Nacos
   nc -z localhost 8848
   ```

4. 检查循环依赖
   ```bash
   # 查看启动日志中的循环依赖错误
   grep -i "circular\|循环" logs/zkinfo.log
   ```

### 10.2 服务未被采集

**症状**: ZooKeeper中有服务，但zkInfo未采集

**排查步骤**:
1. 检查服务是否在项目中
   ```bash
   curl http://localhost:9091/api/projects | jq
   curl http://localhost:9091/api/projects/1/services | jq
   ```

2. 检查过滤规则
   ```bash
   curl http://localhost:9091/api/filters | jq
   ```

3. 检查审批状态
   ```bash
   curl http://localhost:9091/api/approvals | jq
   ```

4. 检查ZooKeeper连接
   ```bash
   curl http://localhost:9091/api/stats | jq '.zkConnected'
   ```

### 10.3 服务未注册到Nacos

**症状**: 服务已采集，但未注册到Nacos

**排查步骤**:
1. 检查Nacos连接
   ```bash
   # 查看日志
   grep -i "nacos" logs/zkinfo.log | tail -20
   ```

2. 检查自动注册配置
   ```bash
   # 查看配置
   grep "auto-register" src/main/resources/application.yml
   ```

3. 检查服务是否通过过滤
   ```bash
   curl http://localhost:9091/api/registered-services | jq
   ```

4. 手动触发注册
   ```bash
   # 审批服务后应该自动注册
   curl -X PUT http://localhost:9091/api/approvals/1/approve ...
   ```

### 10.4 虚拟项目注册失败

**症状**: 虚拟项目创建成功，但未注册到Nacos

**排查步骤**:
1. 检查虚拟项目详情
   ```bash
   curl http://localhost:9091/api/virtual-projects/1 | jq
   ```

2. 检查Endpoint映射
   ```bash
   curl http://localhost:9091/api/virtual-projects/1/endpoint | jq
   ```

3. 检查服务列表
   ```bash
   curl http://localhost:9091/api/virtual-projects/1/services | jq
   ```

4. 检查Nacos注册日志
   ```bash
   grep -i "virtual\|endpoint" logs/zkinfo.log | tail -20
   ```

---

## 11. 测试检查清单

### 基础功能 ✅
- [ ] 服务启动成功
- [ ] 健康检查正常
- [ ] API端点可访问
- [ ] Swagger UI可访问

### 三层过滤机制 ✅
- [ ] 项目级过滤正常
- [ ] 服务级过滤正常
- [ ] 审批级过滤正常
- [ ] 过滤规则优先级正确

### 项目管理 ✅
- [ ] 项目创建成功
- [ ] 服务关联成功
- [ ] 项目更新成功
- [ ] 项目删除成功

### 虚拟项目 ✅
- [ ] 虚拟项目创建成功
- [ ] 服务编排正常
- [ ] Endpoint映射正确
- [ ] Nacos注册成功

### ZooKeeper监听 ✅
- [ ] 只监听项目服务
- [ ] 实时监听正常
- [ ] 过滤规则生效
- [ ] 性能优化明显

### Nacos注册 ✅
- [ ] 服务注册成功
- [ ] 配置发布成功
- [ ] 虚拟项目注册成功
- [ ] 服务更新同步

### 集成测试 ✅
- [ ] 端到端流程正常
- [ ] mcp-router-v3集成正常
- [ ] SSE连接正常
- [ ] 消息路由正常

---

## 12. 测试数据准备

### 12.1 测试项目数据

```json
{
  "projects": [
    {
      "projectCode": "USER_CENTER",
      "projectName": "用户中心项目",
      "projectType": "REAL",
      "services": [
        "com.zkinfo.demo.service.UserService:1.0.0:demo"
      ]
    },
    {
      "projectCode": "ORDER_CENTER",
      "projectName": "订单中心项目",
      "projectType": "REAL",
      "services": [
        "com.zkinfo.demo.service.OrderService:1.0.0:demo"
      ]
    }
  ]
}
```

### 12.2 测试虚拟项目数据

```json
{
  "virtualProjects": [
    {
      "name": "数据分析场景",
      "endpointName": "data-analysis",
      "services": [
        "com.zkinfo.demo.service.UserService:1.0.0:demo",
        "com.zkinfo.demo.service.OrderService:1.0.0:demo",
        "com.zkinfo.demo.service.ProductService:1.0.0:demo"
      ]
    },
    {
      "name": "报表生成场景",
      "endpointName": "report-generation",
      "services": [
        "com.zkinfo.demo.service.OrderService:1.0.0:demo",
        "com.zkinfo.demo.service.ProductService:1.0.0:demo"
      ]
    }
  ]
}
```

### 12.3 测试过滤规则数据

```json
{
  "filters": [
    {
      "filterType": "PREFIX",
      "filterValue": "test",
      "filterOperator": "EXCLUDE",
      "priority": 10,
      "description": "排除test开头的服务"
    },
    {
      "filterType": "PATTERN",
      "filterValue": ".*Test.*",
      "filterOperator": "EXCLUDE",
      "priority": 5,
      "description": "排除包含Test的服务"
    }
  ]
}
```

---

## 13. 快速测试脚本

### 13.1 一键测试脚本

创建 `quick-test.sh`:

```bash
#!/bin/bash

BASE_URL="http://localhost:9091"

echo "=== 快速功能测试 ==="

# 1. 健康检查
echo "1. 健康检查..."
curl -s $BASE_URL/actuator/health | jq -r '.status' && echo "✅" || echo "❌"

# 2. 统计信息
echo "2. 统计信息..."
curl -s $BASE_URL/api/stats | jq -r '.zkConnected' && echo "✅" || echo "❌"

# 3. Provider列表
echo "3. Provider列表..."
PROVIDER_COUNT=$(curl -s $BASE_URL/api/providers | jq 'length')
echo "Provider数量: $PROVIDER_COUNT"

# 4. 已注册服务
echo "4. 已注册服务..."
REGISTERED_COUNT=$(curl -s $BASE_URL/api/registered-services | jq '.count')
echo "已注册服务数量: $REGISTERED_COUNT"

echo "=== 测试完成 ==="
```

---

## 14. 测试报告模板

### 测试执行记录

| 测试项 | 状态 | 执行时间 | 备注 |
|--------|------|----------|------|
| 基础功能测试 | ✅/❌ | | |
| 三层过滤机制 | ✅/❌ | | |
| 项目管理功能 | ✅/❌ | | |
| 虚拟项目功能 | ✅/❌ | | |
| 多 SSE 端点功能 | ✅/❌ | | |
| ZooKeeper监听优化 | ✅/❌ | | |
| Nacos注册功能 | ✅/❌ | | |
| 集成测试 | ✅/❌ | | |
| 性能测试 | ✅/❌ | | |

### 问题记录

| 问题描述 | 严重程度 | 状态 | 解决方案 |
|---------|---------|------|---------|
| | | | |

---

**文档版本**: 1.0.0  
**最后更新**: 2025-12-15  
**维护者**: ZkInfo Team


