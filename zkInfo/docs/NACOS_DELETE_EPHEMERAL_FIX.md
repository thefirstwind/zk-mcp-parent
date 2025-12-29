# Nacos 删除实例 ephemeral 参数修复

**创建日期**: 2025-12-25  
**问题**: 删除 Nacos 实例时，`ephemeral` 参数是必填的

---

## 📋 问题描述

在删除 Nacos 实例时，发现以下问题：

1. **参数缺失**: 删除持久节点（`ephemeral=false`）时，必须在 DELETE 请求中添加 `ephemeral=false` 参数
2. **删除失败**: 如果不提供 `ephemeral` 参数，删除操作会失败
3. **必填参数**: 根据 Nacos v3.1 API 文档，删除实例时以下参数都是必填的：
   - `namespaceId`
   - `groupName`
   - `serviceName`
   - `ip`
   - `port`
   - `ephemeral` (新增)

---

## 🔧 修复内容

### 1. NacosV3ApiService.deregisterInstance

**文件**: `zk-mcp-parent/zkInfo/src/main/java/com/pajk/mcpmetainfo/core/service/NacosV3ApiService.java`

**变更**:
- 添加 `ephemeral` 参数到方法签名
- 在 DELETE 请求的查询参数中添加 `ephemeral` 参数

**修复前**:
```java
public boolean deregisterInstance(String serviceName, String ip, int port, String groupName) {
    // ...
    queryParams.append("&port=").append(port);
    // 缺少 ephemeral 参数
}
```

**修复后**:
```java
public boolean deregisterInstance(String serviceName, String ip, int port, String groupName, boolean ephemeral) {
    // ...
    queryParams.append("&port=").append(port);
    queryParams.append("&ephemeral=").append(ephemeral); // 必填参数
}
```

### 2. NacosMcpRegistrationService.deregisterVirtualProjectMcpService

**文件**: `zk-mcp-parent/zkInfo/src/main/java/com/pajk/mcpmetainfo/core/service/NacosMcpRegistrationService.java`

**变更**:
- 在删除前先查询实例的 `ephemeral` 状态
- 使用查询到的 `ephemeral` 值调用 `deregisterInstance`

**修复逻辑**:
```java
// 1. 查询实例的 ephemeral 状态
boolean ephemeral = true; // 默认值：新创建的虚拟节点都是临时节点
if (useV3Api && nacosV3ApiService != null) {
    List<Map<String, Object>> instances = nacosV3ApiService.getInstanceList(
            mcpServiceName, serviceGroup, null, false);
    for (Map<String, Object> instance : instances) {
        String instanceIp = (String) instance.get("ip");
        Integer instancePort = (Integer) instance.get("port");
        if (localIp.equals(instanceIp) && serverPort == instancePort) {
            // 获取 ephemeral 状态
            Object ephemeralObj = instance.get("ephemeral");
            if (ephemeralObj instanceof Boolean) {
                ephemeral = (Boolean) ephemeralObj;
            } else if (ephemeralObj instanceof String) {
                ephemeral = Boolean.parseBoolean((String) ephemeralObj);
            }
            break;
        }
    }
}

// 2. 使用正确的 ephemeral 值删除
nacosV3ApiService.deregisterInstance(
        mcpServiceName, localIp, serverPort, serviceGroup, ephemeral);
```

### 3. NacosMcpRegistrationService.deregisterMcpService

**文件**: `zk-mcp-parent/zkInfo/src/main/java/com/pajk/mcpmetainfo/core/service/NacosMcpRegistrationService.java`

**变更**:
- 同样在删除前查询实例的 `ephemeral` 状态
- 使用查询到的 `ephemeral` 值调用 `deregisterInstance`

---

## 📝 API 请求格式

### 删除临时节点（ephemeral=true）

```bash
curl -X DELETE "http://127.0.0.1:8848/nacos/v3/client/ns/instance?serviceName=virtual-data-analysis&ip=127.0.0.1&port=9091&groupName=mcp-server&namespaceId=public&ephemeral=true"
```

### 删除持久节点（ephemeral=false）

```bash
curl -X DELETE "http://127.0.0.1:8848/nacos/v3/client/ns/instance?serviceName=virtual-data-analysis2&ip=127.0.0.1&port=9091&groupName=mcp-server&namespaceId=public&ephemeral=false"
```

**必填参数**:
- `namespaceId`: 命名空间 ID（默认: `public`）
- `groupName`: 服务分组（默认: `DEFAULT_GROUP`）
- `serviceName`: 服务名称
- `ip`: 实例 IP 地址
- `port`: 实例端口
- `ephemeral`: 是否为临时节点（`true` 或 `false`）

---

## ✅ 修复效果

### 修复前
- ❌ 删除持久节点失败（缺少 `ephemeral` 参数）
- ❌ 删除临时节点可能失败（缺少 `ephemeral` 参数）
- ❌ 无法正确删除 Nacos 中的实例

### 修复后
- ✅ 自动查询实例的 `ephemeral` 状态
- ✅ 使用正确的 `ephemeral` 参数删除实例
- ✅ 支持删除临时节点和持久节点
- ✅ 兼容旧的持久节点和新的临时节点

---

## 🔍 验证步骤

### 1. 创建虚拟项目（临时节点）

```bash
curl -X POST http://localhost:9091/api/virtual-projects \
  -H "Content-Type: application/json" \
  -d '{
    "endpointName": "test-delete",
    "projectName": "Test Delete Project",
    "services": [...],
    "autoRegister": true
  }'
```

### 2. 验证实例类型

```bash
curl "http://localhost:8848/nacos/v3/client/ns/instance/list?serviceName=virtual-test-delete&namespaceId=public&groupName=mcp-server" | jq '.data[0].ephemeral'
# 应该返回: true
```

### 3. 删除虚拟项目

```bash
curl -X DELETE http://localhost:9091/api/virtual-projects/{virtualProjectId}
```

### 4. 验证删除结果

```bash
curl "http://localhost:8848/nacos/v3/client/ns/instance/list?serviceName=virtual-test-delete&namespaceId=public&groupName=mcp-server" | jq '.data | length'
# 应该返回: 0（实例已被删除）
```

---

## ⚠️ 注意事项

1. **自动查询**: 代码会自动查询实例的 `ephemeral` 状态，无需手动指定
2. **兼容性**: 支持删除临时节点（`ephemeral=true`）和持久节点（`ephemeral=false`）
3. **默认值**: 如果查询失败，默认使用 `ephemeral=true`（新创建的虚拟节点都是临时节点）
4. **错误处理**: 如果查询失败，会记录警告日志，但仍会尝试使用默认值删除

---

## 📚 相关文档

- `docs/NACOS_V3_API_FIX.md` - Nacos v3.1 API 修复文档
- `docs/VIRTUAL_NODE_EPHEMERAL_CHANGE.md` - 虚拟节点改为临时节点文档
- `docs/VIRTUAL_NODE_DELETE_FEATURE.md` - 虚拟节点删除功能文档

---



