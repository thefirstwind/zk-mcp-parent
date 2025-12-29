# zkInfo 核心功能完整测试脚本使用说明

## 📋 概述

`test-all-core-functions.sh` 是一个全面的测试脚本，用于验证 zkInfo 项目的所有核心功能。该脚本设计为**必须100%通过**，确保所有核心功能正常工作。

## 🎯 测试覆盖范围

### 1. 环境检查 ✅

#### 1.1 检查 zkInfo 服务健康状态
```bash
curl -s -f --max-time 5 "${ZKINFO_URL}/actuator/health"
```
**预期响应**: `{"status":"UP"}`

#### 1.2 检查 Nacos 服务状态
```bash
curl -s -f --max-time 5 "${NACOS_URL}/nacos/v1/ns/service/list?pageNo=1&pageSize=10"
```

#### 1.3 检查 ZooKeeper 连接
```bash
curl -s -f --max-time 5 "${ZKINFO_URL}/api/debug/zk-tree"
```

#### 1.4 检查数据库连接
```bash
curl -s -f --max-time 5 "${ZKINFO_URL}/api/dubbo-services?page=1&size=1"
```

---

### 2. 服务发现与同步 ✅

#### 2.1 查询所有 Dubbo 服务
```bash
curl -s "${ZKINFO_URL}/api/dubbo-services?page=1&size=10"
```

#### 2.2 查询服务节点信息
```bash
# 替换 {serviceId} 为实际的服务ID
curl -s "${ZKINFO_URL}/api/dubbo-services/{serviceId}/nodes"
```

#### 2.3 查询服务方法信息
```bash
curl -s "${ZKINFO_URL}/api/dubbo-services/{serviceId}/methods"
```

#### 2.4 手动同步服务节点
```bash
curl -s -X POST "${ZKINFO_URL}/api/dubbo-services/{serviceId}/sync-nodes"
```

---

### 3. 项目管理 ✅

#### 3.1 创建实际项目
```bash
export ZKINFO_URL=http://127.0.0.1:9091
curl -s -X POST "${ZKINFO_URL}/api/projects" \
  -H "Content-Type: application/json" \
  -d '{
    "projectCode": "test-project-001",
    "projectName": "Test Project",
    "projectType": "REAL",
    "description": "Test project for validation",
    "ownerId": 1,
    "ownerName": "Test User"
  }'
```

#### 3.2 查询项目详情
```bash
# 替换 {projectId} 为实际的项目ID
curl -s "${ZKINFO_URL}/api/projects/{projectId}"
```

#### 3.3 添加服务到项目
```bash
curl -s -X POST "${ZKINFO_URL}/api/projects/{projectId}/services" \
  -H "Content-Type: application/json" \
  -d '{
    "serviceInterface": "com.example.Service",
    "version": "1.0.0",
    "group": "demo"
  }'
```

#### 3.4 查询项目服务列表
```bash
curl -s "${ZKINFO_URL}/api/projects/{projectId}/services"
```

---

### 4. 虚拟项目管理 ✅

#### 4.1 创建虚拟项目
```bash
export ZKINFO_URL=http://127.0.0.1:9091
curl -s -X POST "${ZKINFO_URL}/api/virtual-projects" \
  -H "Content-Type: application/json" \
  -d '{
    "endpointName": "test-virtual-endpoint251229",
    "description": "Test virtual project",
    "services": [
      {
        "serviceInterface": "com.pajk.provider2.service.OrderService",
        "version": "1.0.0",
        "priority": 0
      },
      {
        "serviceInterface": "com.pajk.provider2.service.ProductService",
        "version": "1.0.0",
        "priority": 0
      }
    ],
    "autoRegister": true
  }'




```

#### 4.2 查询虚拟项目详情
```bash
# 替换 {virtualProjectId} 为实际的虚拟项目ID
curl -s "${ZKINFO_URL}/api/virtual-projects/{virtualProjectId}"
```

#### 4.3 查询虚拟项目端点信息
```bash
curl -s "${ZKINFO_URL}/api/virtual-projects/{virtualProjectId}/endpoint"
```

#### 4.4 查询虚拟项目关联的服务
```bash
curl -s "${ZKINFO_URL}/api/virtual-projects/{virtualProjectId}/services"
```

#### 4.5 验证 Nacos 注册
```bash
# 替换 {endpointName} 为实际的端点名称
curl -s "${NACOS_URL}/nacos/v3/client/ns/instance/list?namespaceId=${NACOS_NAMESPACE}&groupName=${NACOS_GROUP}&serviceName=virtual-{endpointName}" \
  -H "Content-Type: application/json" \
  -H "User-Agent: Nacos-Bash-Client"
```

---

### 5. 服务审批 ✅

#### 5.1 提交服务审批
```bash
curl -s -X POST "${ZKINFO_URL}/api/dubbo-services/{serviceId}/submit-for-review" \
  -H "Content-Type: application/json" \
  -d '{
    "reviewerId": 1,
    "reviewerName": "Test Reviewer",
    "comment": "Test approval"
  }'
```

#### 5.2 查询待审批列表
```bash
curl -s "${ZKINFO_URL}/api/dubbo-services/pending?page=1&size=10"
```

#### 5.3 查询已审批列表
```bash
curl -s "${ZKINFO_URL}/api/dubbo-services/approved?page=1&size=10"
```

#### 5.4 审批通过服务
```bash
curl -s -X POST "${ZKINFO_URL}/api/dubbo-services/{serviceId}/approve" \
  -H "Content-Type: application/json" \
  -d '{
    "reviewerId": 1,
    "reviewerName": "Admin",
    "comment": "Approved"
  }'
```

#### 5.5 拒绝服务审批
```bash
curl -s -X POST "${ZKINFO_URL}/api/dubbo-services/{serviceId}/reject" \
  -H "Content-Type: application/json" \
  -d '{
    "reviewerId": 1,
    "reviewerName": "Admin",
    "comment": "Rejected"
  }'
```

---

### 6. MCP协议调用 ✅

#### 6.1 MCP Initialize（初始化）
```bash
SESSION_ID="test-session-$(date +%s)"
ENDPOINT_NAME="test-virtual-endpoint"

curl -s -X POST "${ZKINFO_URL}/mcp/message?sessionId=${SESSION_ID}&endpoint=${ENDPOINT_NAME}" \
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

#### 6.2 MCP Tools/List（获取工具列表）
```bash
curl -s -X POST "${ZKINFO_URL}/mcp/message?sessionId=${SESSION_ID}&endpoint=${ENDPOINT_NAME}" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/list",
    "params": {}
  }'
```

#### 6.3 MCP Tools/Call（执行 Dubbo 泛化调用）
```bash
curl -s -X POST "${ZKINFO_URL}/mcp/message?sessionId=${SESSION_ID}&endpoint=${ENDPOINT_NAME}" \
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

---

### 7. SSE端点 ✅

#### 7.1 测试 SSE 连接（通过端点名称）
```bash
# 使用 timeout 命令，3秒后自动断开
timeout 3 curl -N "${ZKINFO_URL}/sse/{endpointName}" \
  -H "Accept: text/event-stream"
```

#### 7.2 测试 SSE 连接（通过虚拟服务名称）
```bash
timeout 3 curl -N "${ZKINFO_URL}/sse/virtual-{endpointName}" \
  -H "Accept: text/event-stream"
```

---

### 8. 接口过滤（白名单）✅

#### 8.1 查询过滤器列表
```bash
curl -s "${ZKINFO_URL}/api/filters"
```

#### 8.2 查询启用的过滤器
```bash
curl -s "${ZKINFO_URL}/api/filters/enabled"
```

#### 8.3 创建过滤器
```bash
curl -s -X POST "${ZKINFO_URL}/api/filters" \
  -H "Content-Type: application/json" \
  -d '{
    "filterName": "Test Whitelist",
    "filterType": "WHITELIST",
    "enabled": true,
    "rules": [
      {
        "ruleType": "INTERFACE_PREFIX",
        "ruleValue": "com.example",
        "action": "INCLUDE",
        "priority": 1
      }
    ]
  }'
```

---

### 9. 心跳检测 ✅

#### 9.1 查询服务节点状态
```bash
curl -s "${ZKINFO_URL}/api/dubbo-services/{serviceId}/nodes"
```
**预期响应**: 包含 `isOnline`、`isHealthy`、`lastHeartbeatTime` 等字段

#### 9.2 查询服务统计信息
```bash
curl -s "${ZKINFO_URL}/api/stats"
```
**预期响应**: 包含 `onlineProviders`、`healthyProviders` 等统计信息

---

### 10. API端点验证 ✅

#### 10.1 应用列表接口
```bash
curl -s "${ZKINFO_URL}/api/applications"
```

#### 10.2 服务统计接口
```bash
curl -s "${ZKINFO_URL}/api/stats"
```

#### 10.3 已注册服务接口
```bash
curl -s "${ZKINFO_URL}/api/registered-services"
```

#### 10.4 项目列表接口
```bash
curl -s "${ZKINFO_URL}/api/projects"
```

#### 10.5 虚拟项目列表接口
```bash
curl -s "${ZKINFO_URL}/api/virtual-projects"
```

## 🚀 使用方法

### 基本用法

```bash
cd /path/to/zk-mcp-parent/zkInfo/scripts
./test-all-core-functions.sh
```

### 自定义配置

通过环境变量自定义配置：

```bash
# 设置 zkInfo 服务地址
export ZKINFO_URL=http://localhost:9091

# 设置 Nacos 服务地址
export NACOS_URL=http://localhost:8848

# 设置 Nacos 命名空间
export NACOS_NAMESPACE=public

# 设置 Nacos 服务组
export NACOS_GROUP=mcp-server

# 执行测试
./test-all-core-functions.sh
```

### 一键测试（带环境变量）

```bash
ZKINFO_URL=http://localhost:9091 \
NACOS_URL=http://localhost:8848 \
./test-all-core-functions.sh
```

## 📊 测试结果

### 成功示例

```
═══════════════════════════════════════════════════════════
  测试结果汇总
═══════════════════════════════════════════════════════════
总测试用例数: 45
通过: 45
失败: 0
跳过: 0

[SUCCESS] ✅ 所有测试用例通过！
```

### 失败处理

如果测试失败，脚本会：
1. 显示失败的测试用例
2. 对于关键测试，立即停止执行
3. 对于非关键测试，继续执行并记录失败
4. 在最后输出详细的失败统计

## 🔧 前置条件

### 1. 必需的服务

- **zkInfo 服务**: 必须运行在 `http://localhost:9091`（或自定义地址）
- **Nacos**: 必须运行在 `http://localhost:8848`（或自定义地址）
- **ZooKeeper**: 必须运行在 `localhost:2181`（或自定义地址）
- **MySQL**: 数据库必须可访问

### 2. 必需的工具

- `curl`: HTTP 请求工具（必需）
- `jq`: JSON 解析工具（可选，脚本已实现纯 bash JSON 解析）

安装方法：

```bash
# Ubuntu/Debian
sudo apt-get install curl

# macOS
brew install curl

# CentOS/RHEL
sudo yum install curl
```

**注意**: 脚本已实现纯 bash JSON 解析功能，不依赖 `jq`，可在生产环境直接使用。

### 3. 测试数据准备

- **Dubbo 服务**: 建议启动 `demo-provider` 项目，提供测试服务
- **数据库**: 确保数据库已初始化，包含必要的表结构

## 📝 测试流程

### 完整测试流程示例

```bash
# 1. 设置环境变量
export ZKINFO_URL="http://localhost:9091"
export NACOS_URL="http://localhost:8848"
export NACOS_NAMESPACE="public"
export NACOS_GROUP="mcp-server"

# 2. 环境检查
curl -s "${ZKINFO_URL}/actuator/health"
curl -s "${NACOS_URL}/nacos/v1/ns/service/list?pageNo=1&pageSize=10"
curl -s "${ZKINFO_URL}/api/debug/zk-tree"

# 3. 查询可用服务
SERVICES=$(curl -s "${ZKINFO_URL}/api/dubbo-services?page=1&size=1")
# 从响应中提取 serviceId（使用 grep/sed）

# 4. 创建测试项目
PROJECT_RESPONSE=$(curl -s -X POST "${ZKINFO_URL}/api/projects" \
  -H "Content-Type: application/json" \
  -d '{
    "projectCode": "test-001",
    "projectName": "Test",
    "projectType": "REAL",
    "ownerId": 1,
    "ownerName": "Test"
  }')
# 从响应中提取 projectId

# 5. 创建虚拟项目
VIRTUAL_RESPONSE=$(curl -s -X POST "${ZKINFO_URL}/api/virtual-projects" \
  -H "Content-Type: application/json" \
  -d '{
    "endpointName": "test-endpoint",
    "projectName": "Test Virtual",
    "services": [],
    "autoRegister": true
  }')
# 从响应中提取 virtualProjectId 和 endpointName

# 6. 测试 MCP 调用
SESSION_ID="test-$(date +%s)"
curl -s -X POST "${ZKINFO_URL}/mcp/message?sessionId=${SESSION_ID}&endpoint=test-endpoint" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}'

# 7. 清理测试数据
curl -s -X DELETE "${ZKINFO_URL}/api/virtual-projects/{virtualProjectId}"
curl -s -X DELETE "${ZKINFO_URL}/api/projects/{projectId}"
```

### 自动化测试流程

脚本会自动执行以下步骤：

1. **环境检查**: 验证所有必需服务是否可用
2. **服务发现**: 检查是否有可用的 Dubbo 服务
3. **项目管理**: 创建测试项目和虚拟项目
4. **功能验证**: 依次验证各个核心功能
5. **清理**: 自动清理测试数据

## ⚠️ 注意事项

1. **测试数据**: 脚本会自动创建测试数据，并在结束时自动清理
   ```bash
   # 如果脚本异常退出，可以手动清理
   curl -s -X DELETE "${ZKINFO_URL}/api/virtual-projects/{virtualProjectId}"
   curl -s -X DELETE "${ZKINFO_URL}/api/projects/{projectId}"
   ```

2. **并发测试**: 如果同时运行多个测试实例，可能会产生冲突
   ```bash
   # 检查是否有其他测试在运行
   ps aux | grep test-all-core-functions
   ```

3. **服务状态**: 确保所有服务正常运行，否则测试可能失败
   ```bash
   # 快速检查所有服务
   curl -s "${ZKINFO_URL}/actuator/health" && \
   curl -s "${NACOS_URL}/nacos/v1/console/health" && \
   curl -s "${ZKINFO_URL}/api/debug/zk-tree" && \
   echo "所有服务正常"
   ```

4. **网络延迟**: 某些测试可能需要等待服务注册，脚本已包含重试机制
   ```bash
   # 手动等待 Nacos 注册（最多等待10秒）
   for i in {1..10}; do
     sleep 1
     INSTANCES=$(curl -s "${NACOS_URL}/nacos/v3/client/ns/instance/list?serviceName=virtual-{endpointName}")
     if echo "$INSTANCES" | grep -q '{'; then
       echo "服务已注册"
       break
     fi
   done
   ```

5. **生产环境使用**: 脚本使用纯 bash JSON 解析，不依赖 `jq`，可在生产环境直接使用
   ```bash
   # 验证脚本可以在生产环境运行
   ./test-all-core-functions.sh
   ```

## 🐛 故障排查

### 问题1: 连接失败

```
[ERROR] 检查 zkInfo 服务状态失败
```

**解决方案**:
```bash
# 手动检查服务状态
curl -s "${ZKINFO_URL}/actuator/health"

# 检查服务是否运行
ps aux | grep zkinfo

# 检查端口是否监听
netstat -tlnp | grep 9091
# 或
ss -tlnp | grep 9091
```

### 问题2: 没有可用服务

```
[WARNING] 未找到可用的Dubbo服务
```

**解决方案**:
```bash
# 检查服务列表
curl -s "${ZKINFO_URL}/api/dubbo-services?page=1&size=10"

# 检查 ZooKeeper 连接
curl -s "${ZKINFO_URL}/api/debug/zk-tree"

# 检查白名单配置
curl -s "${ZKINFO_URL}/api/filters/enabled"
```

### 问题3: 权限错误

```
[WARNING] 权限不足或接口错误
```

**解决方案**:
```bash
# 检查 API 响应
curl -v "${ZKINFO_URL}/api/dubbo-services?page=1&size=10"

# 检查应用日志
tail -f logs/zkinfo.log | grep -i "permission\|403"
```

### 问题4: Nacos 注册失败

```
[WARNING] 服务未在10秒内注册到Nacos
```

**解决方案**:
```bash
# 检查 Nacos 服务状态
curl -s "${NACOS_URL}/nacos/v1/console/health"

# 手动查询服务注册状态
curl -s "${NACOS_URL}/nacos/v3/client/ns/instance/list?namespaceId=${NACOS_NAMESPACE}&groupName=${NACOS_GROUP}&serviceName=virtual-{endpointName}" \
  -H "Content-Type: application/json" \
  -H "User-Agent: Nacos-Bash-Client"

# 检查虚拟项目端点
curl -s "${ZKINFO_URL}/api/virtual-projects/{virtualProjectId}/endpoint"
```

### 问题5: JSON 解析错误

如果遇到 JSON 解析相关的问题，可以手动验证响应：

```bash
# 查看原始响应
curl -s "${ZKINFO_URL}/api/dubbo-services?page=1&size=1" | head -20

# 检查响应格式
curl -s "${ZKINFO_URL}/api/dubbo-services?page=1&size=1" | grep -o '"total"'
```

## 📈 性能指标

- **总测试时间**: 约 1-2 分钟（取决于服务响应速度）
- **重试机制**: 每个测试最多重试 3 次
- **超时设置**: 默认 30 秒超时

## 🔄 持续集成

可以在 CI/CD 流程中使用此脚本：

```yaml
# GitHub Actions 示例
- name: Run zkInfo Core Function Tests
  run: |
    cd zk-mcp-parent/zkInfo/scripts
    ./test-all-core-functions.sh
```

## 📚 相关文档

- [zkInfo 项目文档](../../docs/readme.md)
- [虚拟项目测试脚本](./test-virtual-node-complete.sh)
- [复杂对象参数测试脚本](./test-complex-object-parameters.sh)

## 🤝 贡献

如果发现测试脚本的问题或需要添加新的测试用例，请：

1. 检查现有测试是否已覆盖该功能
2. 添加新的测试函数
3. 更新测试统计
4. 提交 Pull Request

## 📄 许可证

与 zkInfo 项目保持一致。

