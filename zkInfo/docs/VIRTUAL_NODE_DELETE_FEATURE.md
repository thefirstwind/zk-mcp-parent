# 虚拟节点删除功能

**创建日期**: 2025-12-25  
**功能**: 删除虚拟节点（包括从 Nacos 删除持久节点和配置）

---

## 📋 功能概述

虚拟节点在 Nacos 上注册为**持久节点**（`ephemeral=false`），不会自动删除。因此需要提供手动删除功能，确保：

1. ✅ 从 Nacos 服务列表删除服务实例
2. ✅ 从 Nacos 配置中心删除所有相关配置
3. ✅ 从内存缓存中删除虚拟项目
4. ✅ 从项目管理服务中删除服务关联

---

## 🔧 实现细节

### 1. API 端点

**删除虚拟项目**:
```
DELETE /api/virtual-projects/{virtualProjectId}
```

**响应示例**:
```json
{
  "message": "虚拟项目删除成功",
  "virtualProjectId": 1234567890
}
```

### 2. 删除流程

#### 2.1 VirtualProjectController.deleteVirtualProject

**位置**: `VirtualProjectController.java`

**功能**:
- 接收删除请求
- 调用 `VirtualProjectService.deleteVirtualProject`
- 返回删除结果

#### 2.2 VirtualProjectService.deleteVirtualProject

**位置**: `VirtualProjectService.java`

**功能**:
1. 从内存缓存中获取虚拟项目和端点信息
2. 调用 `VirtualProjectRegistrationService.deregisterVirtualProjectFromNacos` 注销 Nacos 服务
3. 清除项目服务关联
4. 从内存缓存中删除虚拟项目和端点

**代码**:
```java
public void deleteVirtualProject(Long virtualProjectId) {
    Project project = virtualProjectCache.get(virtualProjectId);
    if (project == null) {
        return;
    }
    
    // 注销Nacos注册
    VirtualProjectEndpoint endpoint = endpointCache.get(virtualProjectId);
    if (endpoint != null) {
        registrationService.deregisterVirtualProjectFromNacos(endpoint);
    }
    
    // 清除服务关联
    List<ProjectService> services = projectManagementService.getProjectServices(virtualProjectId);
    for (ProjectService service : services) {
        projectManagementService.removeProjectService(
                virtualProjectId,
                service.getServiceInterface(),
                service.getServiceVersion()
        );
    }
    
    // 删除缓存
    virtualProjectCache.remove(virtualProjectId);
    endpointCache.remove(virtualProjectId);
    
    log.info("Deleted virtual project: virtualProjectId={}", virtualProjectId);
}
```

#### 2.3 VirtualProjectRegistrationService.deregisterVirtualProjectFromNacos

**位置**: `VirtualProjectRegistrationService.java`

**功能**:
- 构建服务名称（`virtual-{endpointName}`）
- 调用 `NacosMcpRegistrationService.deregisterVirtualProjectMcpService`

#### 2.4 NacosMcpRegistrationService.deregisterVirtualProjectMcpService

**位置**: `NacosMcpRegistrationService.java`

**功能**:
1. **删除 Nacos 配置中心的配置**:
   - `{serviceId}-{version}-mcp-tools.json` (TOOLS_GROUP)
   - `{serviceId}-mcp-versions.json` (VERSIONS_GROUP)
   - `{serviceId}-{version}-mcp-server.json` (SERVER_GROUP)

2. **删除 Nacos 服务实例**:
   - 优先使用 Nacos v3 API
   - 如果失败，回退到 SDK

**代码**:
```java
public void deregisterVirtualProjectMcpService(String mcpServiceName, String version) {
    try {
        String localIp = getLocalIp();
        String serviceId = generateServiceId(mcpServiceName, version);
        
        // 1. 删除配置
        deleteConfigsFromNacos(serviceId, mcpServiceName, version);
        
        // 2. 删除服务实例
        if (useV3Api && nacosV3ApiService != null) {
            boolean success = nacosV3ApiService.deregisterInstance(
                    mcpServiceName, localIp, serverPort, serviceGroup);
            // ...
        } else {
            namingService.deregisterInstance(mcpServiceName, serviceGroup, localIp, serverPort);
        }
    } catch (Exception e) {
        log.error("❌ Failed to deregister virtual project MCP service: {}", mcpServiceName, e);
    }
}
```

---

## 📊 删除的配置

### 配置列表

虚拟项目注册时会创建以下配置，删除时需要全部删除：

1. **工具配置** (`mcp-tools` 组):
   - DataId: `{serviceId}-{version}-mcp-tools.json`
   - 内容: 工具列表（tools, toolsMeta）

2. **版本配置** (`mcp-server-versions` 组):
   - DataId: `{serviceId}-mcp-versions.json`
   - 内容: 版本信息（id, name, protocol, capabilities, versions）

3. **服务器配置** (`mcp-server` 组):
   - DataId: `{serviceId}-{version}-mcp-server.json`
   - 内容: 服务器配置（id, name, protocol, remoteServerConfig, toolsDescriptionRef）

---

## 🧪 测试

### 测试脚本

已创建测试脚本：`scripts/test-virtual-node-delete.sh`

**测试步骤**:
1. 检查服务状态
2. 查找或创建测试用的虚拟项目
3. 验证虚拟项目在 Nacos 中存在
4. 删除虚拟项目
5. 验证虚拟项目已从内存中删除
6. 验证虚拟项目已从 Nacos 中删除

### 运行测试

```bash
cd zk-mcp-parent/zkInfo
./scripts/test-virtual-node-delete.sh
```

### 手动测试

```bash
# 1. 创建虚拟项目
curl -X POST http://localhost:9091/api/virtual-projects \
  -H "Content-Type: application/json" \
  -d '{
    "endpointName": "test-delete",
    "projectName": "Test Delete Project",
    "services": [...],
    "autoRegister": true
  }'

# 2. 获取虚拟项目 ID
VIRTUAL_PROJECT_ID=$(curl -s http://localhost:9091/api/virtual-projects | jq -r '.[0].project.id')

# 3. 删除虚拟项目
curl -X DELETE http://localhost:9091/api/virtual-projects/$VIRTUAL_PROJECT_ID

# 4. 验证删除
curl http://localhost:9091/api/virtual-projects/$VIRTUAL_PROJECT_ID
# 应该返回 404
```

---

## ⚠️ 注意事项

### 1. 持久节点删除

- **持久节点**不会自动删除，需要手动调用删除 API
- 删除操作会立即从 Nacos 服务列表中移除实例
- 如果删除失败，可能需要通过 Nacos 控制台手动删除

### 2. 配置删除

- 配置删除使用 `ConfigService.removeConfig(dataId, group)`
- 如果配置不存在，删除操作不会报错（Nacos SDK 行为）
- 删除配置的日志会记录在应用日志中

### 3. 错误处理

- 删除操作中的任何错误都会被捕获并记录日志
- 即使部分操作失败（如配置删除失败），服务实例删除仍会继续
- 建议查看应用日志确认所有操作是否成功

### 4. 内存缓存

- 虚拟项目信息存储在内存缓存中
- 删除后立即从缓存中移除
- 服务重启后，未持久化的虚拟项目会丢失（这是当前设计）

---

## 🔍 验证删除结果

### 1. 验证内存删除

```bash
# 查询虚拟项目（应该返回 404）
curl http://localhost:9091/api/virtual-projects/{virtualProjectId}
```

### 2. 验证 Nacos 服务删除

```bash
# 查询 Nacos 服务实例（使用 v3.1 客户端 API）
curl "http://localhost:8848/nacos/v3/client/ns/instance/list?serviceName=virtual-{endpointName}&namespaceId=public&groupName=mcp-server"
# 应该返回：{"code":0,"message":"success","data":[]}
```

### 3. 验证配置删除

- 查看应用日志，确认配置删除日志
- 或通过 Nacos 控制台检查配置是否已删除

---

## 📝 日志示例

### 成功删除日志

```
✅ Deleted tools config: {serviceId}-1.0.0-mcp-tools.json
✅ Deleted versions config: {serviceId}-mcp-versions.json
✅ Deleted server config: {serviceId}-1.0.0-mcp-server.json
✅ Successfully deleted all configs for virtual project: virtual-{endpointName} (serviceId: {serviceId})
✅ Deregistered virtual project MCP service instance (v3 API): virtual-{endpointName} from Nacos
✅ Successfully deregistered virtual project MCP service: virtual-{endpointName} (serviceId: {serviceId})
✅ Deregistered virtual project from Nacos: {endpointName} -> virtual-{endpointName}
Deleted virtual project: virtualProjectId={virtualProjectId}
```

### 部分失败日志

```
⚠️ Failed to delete tools config: {dataId} - {error}
✅ Deleted versions config: {dataId}
✅ Deleted server config: {dataId}
⚠️ Failed to deregister via v3 API, falling back to SDK
✅ Deregistered virtual project MCP service instance (SDK): virtual-{endpointName} from Nacos
```

---

## 🔗 相关文档

- [虚拟节点修复计划](./VIRTUAL_NODE_FIX_PLAN.md)
- [虚拟节点修复实施总结](./VIRTUAL_NODE_FIX_IMPLEMENTATION.md)
- [虚拟项目持久节点修复](./VIRTUAL_PROJECT_EPHEMERAL_FIX.md)

---

## ✅ 完成状态

- [x] 删除服务实例功能
- [x] 删除配置功能
- [x] 从内存缓存删除
- [x] 从项目管理服务删除
- [x] 错误处理和日志
- [x] 测试脚本
- [x] 文档

---

## 🚀 使用示例

### 通过 API 删除

```bash
# 删除虚拟项目
curl -X DELETE http://localhost:9091/api/virtual-projects/1234567890
```

### 通过测试脚本删除

```bash
# 运行删除测试脚本
./scripts/test-virtual-node-delete.sh
```

---

## 📞 支持

如有问题，请查看：
- 应用日志：`logs/zkInfo.log`
- Nacos 控制台：`http://localhost:8848/nacos`
- 测试脚本输出

