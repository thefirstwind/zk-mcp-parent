# 虚拟项目持久节点修复

## 问题描述
虚拟节点 `virtual-data-analysis` 创建后不久会被 Nacos 判定为非健康节点，继而被删除。

## 问题原因
1. **临时节点需要心跳**：虚拟项目被注册为临时节点（`ephemeral=true`），临时节点需要定期发送心跳来保持健康状态
2. **缺少心跳机制**：`zkInfo` 没有为虚拟项目实现心跳机制
3. **Nacos 健康检查**：Nacos 的健康检查会检测到心跳停止，将实例标记为不健康并删除

## 解决方案
将虚拟项目注册为持久节点（`ephemeral=false`），持久节点不需要心跳机制，不会被 Nacos 自动删除。

## 修复内容

### 1. NacosMcpRegistrationService.java
- **文件**: `zk-mcp-parent/zkInfo/src/main/java/com/zkinfo/service/NacosMcpRegistrationService.java`
- **修改**:
  - `registerInstanceToNacos` 方法添加 `ephemeral` 参数
  - `registerVirtualProjectAsMcp` 方法调用时传递 `ephemeral=false`
  - `registerDubboServiceAsMcp` 方法调用时传递 `ephemeral=true`（保持原有行为）
  - 添加日志记录，标识虚拟项目使用持久节点

### 2. 修复效果

#### 修复前
- 虚拟项目注册为临时节点（`ephemeral=true`）
- 需要心跳机制保持健康状态
- 如果没有心跳，Nacos 会将其标记为不健康并删除

#### 修复后
- 虚拟项目注册为持久节点（`ephemeral=false`）
- 不需要心跳机制
- Nacos 不会自动删除持久节点，除非手动删除

## 节点类型说明

### 临时节点（ephemeral=true）
- **用途**：普通 Dubbo 服务
- **特点**：需要定期发送心跳保持健康状态
- **删除机制**：如果心跳停止，Nacos 会自动删除

### 持久节点（ephemeral=false）
- **用途**：虚拟项目
- **特点**：不需要心跳机制
- **删除机制**：只能手动删除，不会被 Nacos 自动删除

## 注意事项
1. 虚拟项目由 `zkInfo` 管理，不需要心跳机制
2. 虚拟项目的生命周期由 `zkInfo` 控制，使用持久节点可以防止被 Nacos 自动删除
3. 普通 Dubbo 服务仍然使用临时节点，保持原有行为

## 相关文件
- `zk-mcp-parent/zkInfo/src/main/java/com/zkinfo/service/NacosMcpRegistrationService.java`
- `zk-mcp-parent/zkInfo/src/main/java/com/zkinfo/service/NacosV3ApiService.java`

