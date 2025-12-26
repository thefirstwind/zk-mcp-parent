# 虚拟节点删除功能增强

**创建日期**: 2025-12-25  
**增强内容**: 支持删除内存中不存在的虚拟节点（从 Nacos 删除）

---

## 📋 问题背景

### 原有问题

1. **持久节点特性**: 虚拟节点在 Nacos 上注册为**持久节点**（`ephemeral=false`），不会自动删除
2. **内存丢失**: zkInfo 服务重启后，内存中的虚拟项目信息会丢失
3. **删除限制**: 原有删除逻辑只能通过 `virtualProjectId` 删除，如果内存中没有虚拟项目，就无法删除
4. **残留问题**: 导致 Nacos 中残留虚拟节点注册信息，无法清理

### 解决方案

增强删除功能，支持：
1. ✅ 通过 `endpointName` 删除
2. ✅ 通过 `serviceName` 删除
3. ✅ 即使内存中没有虚拟项目，也能从 Nacos 删除

---

## 🔧 新增 API

### 1. 通过 endpointName 删除

**端点**:
```
DELETE /api/virtual-projects/by-endpoint/{endpointName}
```

**示例**:
```bash
curl -X DELETE http://localhost:9091/api/virtual-projects/by-endpoint/data-analysis
```

**响应**:
```json
{
  "message": "虚拟项目删除成功",
  "endpointName": "data-analysis",
  "deletedFromNacos": true
}
```

### 2. 通过 serviceName 删除

**端点**:
```
DELETE /api/virtual-projects/by-service/{serviceName}
```

**示例**:
```bash
# serviceName 可以是 virtual-{endpointName} 或 {endpointName}
curl -X DELETE http://localhost:9091/api/virtual-projects/by-service/virtual-data-analysis
curl -X DELETE http://localhost:9091/api/virtual-projects/by-service/data-analysis
```

**响应**:
```json
{
  "message": "虚拟项目删除成功",
  "serviceName": "virtual-data-analysis",
  "deletedFromNacos": true
}
```

---

## 🔄 删除流程

### 场景 1: 内存中有虚拟项目

1. 从内存缓存中查找虚拟项目
2. 获取 endpoint 信息
3. 调用 `deregisterVirtualProjectFromNacos` 删除
4. 清除内存缓存和服务关联

### 场景 2: 内存中没有虚拟项目（服务重启后）

1. 尝试从内存中查找，未找到
2. 直接通过 `serviceName` 从 Nacos 删除
3. 删除服务实例和所有配置
4. 记录日志

---

## 📊 实现细节

### 1. VirtualProjectService 增强

#### 1.1 deleteVirtualProjectByEndpointName

```java
public boolean deleteVirtualProjectByEndpointName(String endpointName) {
    // 1. 先尝试从内存中查找
    VirtualProjectEndpoint endpoint = getEndpointByEndpointName(endpointName);
    if (endpoint != null) {
        // 找到虚拟项目，使用完整的删除流程
        Long virtualProjectId = endpoint.getVirtualProjectId();
        deleteVirtualProject(virtualProjectId);
        return true;
    }
    
    // 2. 内存中没有，直接从 Nacos 删除
    log.warn("Virtual project not found in memory: endpointName={}, will delete from Nacos directly", endpointName);
    try {
        String serviceName = "virtual-" + endpointName;
        registrationService.deregisterVirtualProjectFromNacosByServiceName(serviceName, "1.0.0");
        return true;
    } catch (Exception e) {
        log.error("❌ Failed to delete virtual project from Nacos: endpointName={}", endpointName, e);
        return false;
    }
}
```

#### 1.2 deleteVirtualProjectByServiceName

```java
public boolean deleteVirtualProjectByServiceName(String serviceName) {
    // 如果 serviceName 以 virtual- 开头，提取 endpointName
    String endpointName = serviceName;
    if (serviceName.startsWith("virtual-")) {
        endpointName = serviceName.substring("virtual-".length());
    }
    
    // 尝试通过 endpointName 删除
    return deleteVirtualProjectByEndpointName(endpointName);
}
```

### 2. VirtualProjectRegistrationService 增强

#### 2.1 deregisterVirtualProjectFromNacosByServiceName

```java
public void deregisterVirtualProjectFromNacosByServiceName(String serviceName, String version) {
    try {
        nacosMcpRegistrationService.deregisterVirtualProjectMcpService(serviceName, version);
        log.info("✅ Deregistered virtual project from Nacos by serviceName: {}", serviceName);
    } catch (Exception e) {
        log.error("❌ Failed to deregister virtual project from Nacos by serviceName: {}", serviceName, e);
        throw new RuntimeException("Failed to deregister virtual project from Nacos", e);
    }
}
```

### 3. VirtualProjectController 新增端点

#### 3.1 DELETE /api/virtual-projects/by-endpoint/{endpointName}

```java
@DeleteMapping("/by-endpoint/{endpointName}")
public ResponseEntity<Map<String, Object>> deleteVirtualProjectByEndpointName(
        @PathVariable String endpointName) {
    try {
        boolean success = virtualProjectService.deleteVirtualProjectByEndpointName(endpointName);
        // ...
    } catch (Exception e) {
        // ...
    }
}
```

#### 3.2 DELETE /api/virtual-projects/by-service/{serviceName}

```java
@DeleteMapping("/by-service/{serviceName}")
public ResponseEntity<Map<String, Object>> deleteVirtualProjectByServiceName(
        @PathVariable String serviceName) {
    try {
        boolean success = virtualProjectService.deleteVirtualProjectByServiceName(serviceName);
        // ...
    } catch (Exception e) {
        // ...
    }
}
```

---

## 🧪 测试场景

### 场景 1: 正常删除（内存中有）

```bash
# 1. 创建虚拟项目
curl -X POST http://localhost:9091/api/virtual-projects -d '{...}'

# 2. 通过 ID 删除
curl -X DELETE http://localhost:9091/api/virtual-projects/{id}
```

### 场景 2: 服务重启后删除（内存中没有）

```bash
# 1. 创建虚拟项目并注册到 Nacos
curl -X POST http://localhost:9091/api/virtual-projects -d '{...}'

# 2. 重启 zkInfo 服务（内存丢失）

# 3. 通过 endpointName 删除（从 Nacos 删除）
curl -X DELETE http://localhost:9091/api/virtual-projects/by-endpoint/{endpointName}
```

### 场景 3: 通过 serviceName 删除

```bash
# 从 Nacos 查询到 serviceName
# 然后通过 serviceName 删除
curl -X DELETE http://localhost:9091/api/virtual-projects/by-service/virtual-{endpointName}
```

---

## 📝 使用示例

### 查找需要删除的虚拟节点

```bash
# 1. 从 Nacos 查询所有虚拟项目服务
# 注意：查询服务列表需要使用运维 API（Admin API），客户端 API 不支持批量查询
# 这里使用 v1 API 作为示例（如果 v3 运维 API 可用，请使用 v3）
curl "http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=100&namespaceId=public&groupName=mcp-server" \
  | jq -r '.doms[]? | select(startswith("virtual-"))'

# 2. 提取 endpointName（去掉 virtual- 前缀）
ENDPOINT_NAME="data-analysis"

# 3. 删除虚拟项目
curl -X DELETE "http://localhost:9091/api/virtual-projects/by-endpoint/$ENDPOINT_NAME"
```

### 批量删除

```bash
# 获取所有虚拟项目服务名称
# 注意：查询服务列表需要使用运维 API（Admin API），客户端 API 不支持批量查询
# 这里使用 v1 API 作为示例（如果 v3 运维 API 可用，请使用 v3）
SERVICES=$(curl -s "http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=100&namespaceId=public&groupName=mcp-server" \
  | jq -r '.doms[]? | select(startswith("virtual-"))')

# 批量删除
for SERVICE in $SERVICES; do
    ENDPOINT_NAME=${SERVICE#virtual-}  # 去掉 virtual- 前缀
    echo "Deleting: $ENDPOINT_NAME"
    curl -X DELETE "http://localhost:9091/api/virtual-projects/by-endpoint/$ENDPOINT_NAME"
done
```

---

## ⚠️ 注意事项

### 1. 服务名称格式

- **注册时**: 使用 `virtual-{endpointName}` 格式
- **删除时**: 支持两种格式：
  - `virtual-{endpointName}`（完整格式）
  - `{endpointName}`（自动添加 `virtual-` 前缀）

### 2. 内存状态

- **内存中有**: 执行完整删除流程（包括内存缓存清理）
- **内存中没有**: 只从 Nacos 删除，不清理内存（因为内存中本来就没有）

### 3. 错误处理

- 如果删除失败，会记录详细日志
- 部分失败不影响其他操作
- 建议查看应用日志确认删除结果

### 4. 配置删除

- 删除时会删除所有相关配置（tools、versions、server）
- 如果配置不存在，不会报错（Nacos SDK 行为）

---

## 🔍 验证删除结果

### 1. 验证服务实例删除

```bash
# 查询 Nacos 服务实例（使用 v3.1 客户端 API）
curl "http://localhost:8848/nacos/v3/client/ns/instance/list?serviceName=virtual-{endpointName}&namespaceId=public&groupName=mcp-server"
# 应该返回：{"code":0,"message":"success","data":[]}
```

### 2. 验证配置删除

- 查看应用日志，确认配置删除日志
- 或通过 Nacos 控制台检查配置是否已删除

### 3. 验证内存删除（如果内存中有）

```bash
# 查询虚拟项目（应该返回 404）
curl http://localhost:9091/api/virtual-projects/{id}
```

---

## 🔗 相关文档

- [虚拟节点删除功能](./VIRTUAL_NODE_DELETE_FEATURE.md)
- [虚拟节点修复计划](./VIRTUAL_NODE_FIX_PLAN.md)
- [虚拟节点修复实施总结](./VIRTUAL_NODE_FIX_IMPLEMENTATION.md)

---

## ✅ 完成状态

- [x] 通过 endpointName 删除功能
- [x] 通过 serviceName 删除功能
- [x] 支持删除内存中不存在的虚拟项目
- [x] 错误处理和日志
- [x] 测试脚本更新
- [x] 文档更新

---

## 🚀 使用建议

### 推荐使用方式

1. **正常删除**: 使用 `DELETE /api/virtual-projects/{id}`（如果知道 ID）
2. **服务重启后**: 使用 `DELETE /api/virtual-projects/by-endpoint/{endpointName}`
3. **从 Nacos 查询后删除**: 使用 `DELETE /api/virtual-projects/by-service/{serviceName}`

### 清理残留虚拟节点

如果发现 Nacos 中有残留的虚拟节点，可以：

```bash
# 1. 查询所有虚拟项目服务
# 注意：查询服务列表需要使用运维 API（Admin API），客户端 API 不支持批量查询
# 这里使用 v1 API 作为示例（如果 v3 运维 API 可用，请使用 v3）
curl "http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=100&namespaceId=public&groupName=mcp-server" \
  | jq -r '.doms[]? | select(startswith("virtual-"))'

# 2. 逐个删除
for SERVICE in $(...); do
    ENDPOINT_NAME=${SERVICE#virtual-}
    curl -X DELETE "http://localhost:9091/api/virtual-projects/by-endpoint/$ENDPOINT_NAME"
done
```

---

## 📞 支持

如有问题，请查看：
- 应用日志：`logs/zkInfo.log`
- Nacos 控制台：`http://localhost:8848/nacos`
- 测试脚本输出：`./scripts/test-virtual-node-delete.sh`

