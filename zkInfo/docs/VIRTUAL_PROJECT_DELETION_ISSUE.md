# 虚拟节点删除问题排查报告

## 问题描述
虚拟节点 `virtual-data-analysis` 被意外删除或注销。

## 问题原因

### 1. 多个虚拟项目使用相同的 endpointName
- 当前有两个虚拟项目都使用 `data-analysis` 作为 `endpointName`
- 当重新注册其中一个虚拟项目时，会注销 Nacos 中的 `virtual-data-analysis` 服务
- 这可能导致另一个虚拟项目的服务也被注销

### 2. 测试脚本中的 deregister 调用
- `fix-and-verify.sh` 脚本中调用了不存在的 `deregister` API
- 虽然调用会失败（脚本使用了 `|| true`），但这不是最佳实践

### 3. reregister 操作的副作用
- `reregisterVirtualProjectToNacos` 方法会先注销再注册
- 如果有多个虚拟项目使用相同的 `endpointName`，重新注册一个会影响其他

## 解决方案

### 1. 修复测试脚本
- ✅ 已移除 `fix-and-verify.sh` 中不必要的 `deregister` 调用
- ✅ 简化了重新注册流程

### 2. 建议的最佳实践

#### 2.1 确保 endpointName 唯一性
- 每个虚拟项目应该使用唯一的 `endpointName`
- 如果必须使用相同的名称，应该合并虚拟项目而不是创建多个

#### 2.2 保护虚拟项目不被删除
- 虚拟项目只能通过明确的 `DELETE /api/virtual-projects/{id}` API 删除
- 没有自动删除机制
- `reregister` 操作不会删除虚拟项目，只会注销和重新注册 Nacos 服务

#### 2.3 重新注册时的注意事项
- `reregister` 操作会暂时注销 Nacos 服务，然后重新注册
- 如果有多个虚拟项目使用相同的 `endpointName`，建议：
  1. 先删除重复的虚拟项目
  2. 或者为每个虚拟项目使用唯一的 `endpointName`

## 当前状态

### 虚拟项目列表
```bash
curl -s "http://mcp-bridge.test:9091/api/virtual-projects" | jq -r '.[] | "ID: \(.project.id), endpointName: \(.endpoint.endpointName)"'
```

### Nacos 服务状态
```bash
curl -s "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=virtual-data-analysis&groupName=mcp-server" | jq '{serviceName: .name, healthyCount: [.hosts[]? | select(.healthy == true)] | length}'
```

## 修复措施

1. ✅ 修复了 `fix-and-verify.sh` 脚本，移除了不必要的 `deregister` 调用
2. ✅ 添加了本文档说明问题和解决方案
3. ⚠️ 建议清理重复的虚拟项目，确保每个虚拟项目有唯一的 `endpointName`

## 预防措施

1. **代码层面**：
   - 虚拟项目删除只能通过明确的 DELETE API
   - `reregister` 操作不会删除虚拟项目本身

2. **操作层面**：
   - 创建虚拟项目前检查是否已存在相同 `endpointName` 的虚拟项目
   - 定期清理重复的虚拟项目
   - 使用 `reregister` 时要谨慎，确保不会影响其他虚拟项目

3. **监控层面**：
   - 监控 Nacos 中的服务注册状态
   - 监控虚拟项目的创建和删除操作

