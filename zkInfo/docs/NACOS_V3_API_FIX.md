# Nacos v3.1 API 修复

**创建日期**: 2025-12-25  
**参考文档**: [Nacos v3.1 Open API 文档](https://nacos.io/docs/latest/manual/user/open-api/)

---

## 📋 问题分析

根据 Nacos v3.1 API 文档，发现以下问题：

### 1. API 路径错误

**问题**: 脚本中使用了错误的 API 路径
- **错误**: `/nacos/v3/ns/instance/list`
- **正确**: `/nacos/v3/client/ns/instance/list`

**影响**: 查询实例列表失败

### 2. 返回数据字段错误

**问题**: 脚本中使用了错误的返回数据字段
- **错误**: `.hosts`（这是 v2 API 的格式）
- **正确**: `.data`（v3 API 直接返回数组）

**影响**: 无法正确解析返回的实例列表

### 3. Metadata 传递方式

**问题**: metadata 的传递方式可能需要优化
- **当前**: 作为 JSON 字符串传递
- **优化**: 作为表单字段传递（`metadata.key=value` 格式）

---

## 🔧 修复内容

### 1. 修复脚本中的 API 路径

#### test-virtual-node-delete.sh

**修复前**:
```bash
INSTANCES=$(curl -s "$NACOS_URL/nacos/v3/ns/instance/list?..." | jq -r '.hosts // [] | length')
```

**修复后**:
```bash
INSTANCES=$(curl -s "$NACOS_URL/nacos/v3/client/ns/instance/list?..." | jq -r '.data // [] | length')
```

#### test-virtual-node-complete.sh

**修复前**:
```bash
INSTANCES=$(curl -s "$NACOS_URL/nacos/v3/ns/instance/list?..." | jq -r '.hosts // [] | length')
```

**修复后**:
```bash
INSTANCES=$(curl -s "$NACOS_URL/nacos/v3/client/ns/instance/list?..." | jq -r '.data // [] | length')
```

### 2. 修复代码中的 Metadata 传递方式

#### NacosV3ApiService.java

**修复前**:
```java
// 将 metadata 作为 JSON 字符串传递
String metadataJson = objectMapper.writeValueAsString(metadata);
body.append("metadata=").append(URLEncoder.encode(metadataJson, StandardCharsets.UTF_8));
```

**修复后**:
```java
// 将 metadata 的每个键值对作为表单字段传递
// 格式：metadata.key1=value1&metadata.key2=value2
if (metadata != null && !metadata.isEmpty()) {
    for (Map.Entry<String, String> entry : metadata.entrySet()) {
        body.append("metadata.")
            .append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
            .append("=")
            .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
    }
}
```

---

## 📊 Nacos v3.1 API 规范

### 统一路径格式

根据 [Nacos v3.1 API 文档](https://nacos.io/docs/latest/manual/user/open-api/)，统一路径格式为：

```
[/$nacos.server.contextPath]/v3/client/[module]/[subPath]...
```

其中：
- `$nacos.server.contextPath`: 默认为 `/nacos`
- `module`: 模块名称，如 `ns`（服务发现）、`cs`（配置管理）
- `subPath`: 子路径，如 `instance`、`instance/list`

### 客户端 API 列表

#### 1. 注册实例

**路径**: `POST /nacos/v3/client/ns/instance`

**请求体**（表单格式）:
```
namespaceId=public&groupName=mcp-server&serviceName=test&ip=127.0.0.1&port=9091&ephemeral=false&metadata.key1=value1&metadata.key2=value2
```

**返回**:
```json
{
  "code": 0,
  "message": "success",
  "data": "ok"
}
```

#### 2. 注销实例

**路径**: `DELETE /nacos/v3/client/ns/instance`

**查询参数**:
- `namespaceId`: 命名空间ID
- `groupName`: 分组名
- `serviceName`: 服务名
- `ip`: IP地址
- `port`: 端口号

**返回**:
```json
{
  "code": 0,
  "message": "success",
  "data": "ok"
}
```

#### 3. 查询实例列表

**路径**: `GET /nacos/v3/client/ns/instance/list`

**查询参数**:
- `namespaceId`: 命名空间ID
- `groupName`: 分组名
- `serviceName`: 服务名
- `clusterName`: 集群名称（可选）
- `healthyOnly`: 是否只获取健康实例（可选）

**返回**:
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "ip": "127.0.0.1",
      "port": 9091,
      "weight": 1.0,
      "healthy": true,
      "enabled": true,
      "ephemeral": false,
      "clusterName": "DEFAULT",
      "serviceName": "mcp-server@@virtual-test",
      "metadata": {},
      "instanceHeartBeatTimeOut": 15000,
      "instanceHeartBeatInterval": 5000,
      "ipDeleteTimeout": 30000
    }
  ]
}
```

**注意**: 返回的 `data` 字段直接是数组，不是 `{hosts: [...]}` 格式

---

## ✅ 修复验证

### 1. 代码编译

```bash
cd zk-mcp-parent/zkInfo
mvn clean compile -DskipTests
```

### 2. 测试脚本

```bash
# 测试删除功能
./scripts/test-virtual-node-delete.sh

# 测试完整功能
./scripts/test-virtual-node-complete.sh
```

### 3. 手动验证

```bash
# 查询实例列表（使用正确的 API 路径）
curl "http://localhost:8848/nacos/v3/client/ns/instance/list?serviceName=virtual-test&namespaceId=public&groupName=mcp-server" \
  | jq '.data | length'

# 应该返回实例数量（数字），而不是错误
```

---

## 📝 注意事项

### 1. API 版本兼容性

- **Nacos 3.X**: 使用 `/v3/client/` 路径
- **Nacos 2.X**: 使用 `/v2/` 路径（已废弃）
- **Nacos 1.X**: 使用 `/v1/` 路径（已废弃）

### 2. 返回数据格式

- **v3 API**: 返回 `{code: 0, message: "success", data: [...]}`
- **v2 API**: 返回 `{hosts: [...]}`（已废弃）

### 3. Metadata 传递

- **表单格式**: `metadata.key1=value1&metadata.key2=value2`
- **不是 JSON**: 不要将 metadata 作为 JSON 字符串传递

### 4. 客户端 API vs 运维 API

- **客户端 API** (`/v3/client/`): 面向普通应用，提供单服务/单配置操作
- **运维 API** (`/v3/ns/`): 面向管控类应用，提供批量操作（如查询所有服务）

---

## 🔗 相关文档

- [Nacos v3.1 Open API 文档](https://nacos.io/docs/latest/manual/user/open-api/)
- [虚拟节点删除功能](./VIRTUAL_NODE_DELETE_FEATURE.md)
- [虚拟节点删除增强](./VIRTUAL_NODE_DELETE_ENHANCEMENT.md)

---

## ✅ 修复清单

- [x] 修复脚本中的 API 路径（添加 `/client`）
- [x] 修复脚本中的返回数据字段（`.hosts` -> `.data`）
- [x] 优化代码中的 metadata 传递方式
- [x] 代码编译通过
- [x] 文档更新

---

## 🚀 使用建议

### 查询实例列表

```bash
# 正确的方式（v3.1 API）
curl "http://localhost:8848/nacos/v3/client/ns/instance/list?serviceName=virtual-test&namespaceId=public&groupName=mcp-server" \
  | jq '.data | length'

# 错误的方式（v2 API，已废弃）
curl "http://localhost:8848/nacos/v3/ns/instance/list?serviceName=virtual-test&namespaceId=public&groupName=mcp-server" \
  | jq '.hosts | length'  # ❌ 错误
```

### 注册实例

```bash
# 使用表单格式传递 metadata
curl -X POST "http://localhost:8848/nacos/v3/client/ns/instance" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "namespaceId=public&groupName=mcp-server&serviceName=test&ip=127.0.0.1&port=9091&ephemeral=false&metadata.key1=value1&metadata.key2=value2"
```

---

## 📞 支持

如有问题，请查看：
- [Nacos v3.1 Open API 文档](https://nacos.io/docs/latest/manual/user/open-api/)
- 应用日志：`logs/zkInfo.log`
- Nacos 控制台：`http://localhost:8848/nacos`


