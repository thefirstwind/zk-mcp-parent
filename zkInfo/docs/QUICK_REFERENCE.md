# zkInfo 生产环境快速参考

## 🔧 环境配置

```bash
export ZKINFO_URL="http://your-host:9091"
export NACOS_URL="http://your-nacos:8848"
export NACOS_NAMESPACE="public"
export NACOS_GROUP="mcp-server"
```

## 📋 常用命令速查

### 健康检查

```bash
# 服务健康状态
curl "${ZKINFO_URL}/actuator/health"

# 服务统计
curl "${ZKINFO_URL}/api/stats"
```

### 服务管理

```bash
# 查询服务列表
curl "${ZKINFO_URL}/api/dubbo-services?page=1&size=10"

# 查询服务详情
curl "${ZKINFO_URL}/api/dubbo-services/{serviceId}"

# 查询服务节点
curl "${ZKINFO_URL}/api/dubbo-services/{serviceId}/nodes"

# 查询服务方法
curl "${ZKINFO_URL}/api/dubbo-services/{serviceId}/methods"
```

### 项目管理

```bash
# 创建项目
curl -X POST "${ZKINFO_URL}/api/projects" \
  -H "Content-Type: application/json" \
  -d '{"projectCode":"test","projectName":"测试","projectType":"REAL"}'

# 查询项目列表
curl "${ZKINFO_URL}/api/projects"

# 添加服务到项目
curl -X POST "${ZKINFO_URL}/api/projects/{projectId}/services" \
  -H "Content-Type: application/json" \
  -d '{"serviceInterface":"com.example.Service","version":"1.0.0","group":"demo"}'
```

### 虚拟项目管理

```bash
# 创建虚拟项目
curl -X POST "${ZKINFO_URL}/api/virtual-projects" \
  -H "Content-Type: application/json" \
  -d '{
    "endpointName":"my-endpoint",
    "projectName":"我的项目",
    "services":[],
    "autoRegister":true
  }'

# 查询虚拟项目
curl "${ZKINFO_URL}/api/virtual-projects"

# 删除虚拟项目（通过端点名）
curl -X DELETE "${ZKINFO_URL}/api/virtual-projects/by-endpoint/{endpointName}"
```

### 服务审批

```bash
# 提交审批
curl -X POST "${ZKINFO_URL}/api/dubbo-services/{serviceId}/submit-for-review" \
  -H "Content-Type: application/json" \
  -d '{"reviewerId":1,"reviewerName":"管理员","comment":"申请审批"}'

# 审批通过
curl -X POST "${ZKINFO_URL}/api/dubbo-services/{serviceId}/approve" \
  -H "Content-Type: application/json" \
  -d '{"reviewerId":1,"reviewerName":"管理员","comment":"通过"}'

# 查询待审批
curl "${ZKINFO_URL}/api/dubbo-services/pending"
```

### MCP协议调用

```bash
# Initialize
curl -X POST "${ZKINFO_URL}/mcp/message?sessionId=test&endpoint=my-endpoint" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":"1",
    "method":"initialize",
    "params":{"protocolVersion":"2024-11-05"}
  }'

# Tools/List
curl -X POST "${ZKINFO_URL}/mcp/message?sessionId=test&endpoint=my-endpoint" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"2","method":"tools/list","params":{}}'

# Tools/Call
curl -X POST "${ZKINFO_URL}/mcp/message?sessionId=test&endpoint=my-endpoint" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":"3",
    "method":"tools/call",
    "params":{"name":"com.example.Service.method","arguments":{}}
  }'
```

### SSE端点

```bash
# 连接SSE
curl -N "${ZKINFO_URL}/sse/{endpointName}" \
  -H "Accept: text/event-stream"
```

### Nacos验证

```bash
# 查询服务列表
curl "${NACOS_URL}/nacos/v1/ns/service/list?pageNo=1&pageSize=10"

# 查询实例列表
curl "${NACOS_URL}/nacos/v3/client/ns/instance/list?namespaceId=${NACOS_NAMESPACE}&groupName=${NACOS_GROUP}&serviceName=virtual-{endpointName}" \
  -H "Content-Type: application/json" \
  -H "User-Agent: Nacos-Bash-Client"
```

## 🚀 快速验证脚本

```bash
# 验证所有功能
./scripts/production-verification.sh

# 验证特定功能
./scripts/production-verification.sh health
./scripts/production-verification.sh services
./scripts/production-verification.sh virtual
./scripts/production-verification.sh mcp
```

## 📖 完整文档

详细操作手册请参考: [PRODUCTION_OPERATION_MANUAL.md](./PRODUCTION_OPERATION_MANUAL.md)


