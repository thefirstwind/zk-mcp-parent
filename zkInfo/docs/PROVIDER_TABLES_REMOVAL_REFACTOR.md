# 废弃 Provider 相关表重构说明

**重构日期**: 2025-12-25  
**目标**: 废弃 `zk_provider_info`、`zk_provider_method`、`zk_provider_parameter` 三个表，简化逻辑

---

## 📋 重构概述

### 废弃的表
1. `zk_provider_info` - Provider信息表
2. `zk_provider_method` - Provider方法表
3. `zk_provider_parameter` - Provider参数表

### 功能迁移
- **心跳和状态信息** → 迁移到 `zk_dubbo_service_node` 表
  - `registration_time` - 注册时间
  - `last_heartbeat_time` - 最后心跳时间
  - `is_online` - 是否在线
  - `is_healthy` - 是否健康

- **方法和参数信息** → 从 `zk_dubbo_service_method` 和 `zk_dubbo_method_parameter` 查询
  - 不再单独存储，直接关联查询

---

## 🔧 数据库变更

### 1. 扩展 `zk_dubbo_service_node` 表

```sql
ALTER TABLE `zk_dubbo_service_node` 
    ADD COLUMN `registration_time` DATETIME COMMENT '注册时间',
    ADD COLUMN `last_heartbeat_time` DATETIME COMMENT '最后心跳时间',
    ADD COLUMN `is_online` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否在线',
    ADD COLUMN `is_healthy` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否健康';

-- 添加索引
ALTER TABLE `zk_dubbo_service_node`
    ADD INDEX `idx_is_online` (`is_online`),
    ADD INDEX `idx_is_healthy` (`is_healthy`),
    ADD INDEX `idx_last_heartbeat` (`last_heartbeat_time`);
```

### 2. 数据迁移

从 `zk_provider_info` 迁移数据到 `zk_dubbo_service_node`：

```sql
UPDATE `zk_dubbo_service_node` dsn
INNER JOIN `zk_provider_info` pi ON dsn.service_id = pi.service_id AND dsn.id = pi.node_id
SET 
    dsn.registration_time = pi.registration_time,
    dsn.last_heartbeat_time = pi.last_heartbeat_time,
    dsn.is_online = pi.is_online,
    dsn.is_healthy = pi.is_healthy;
```

---

## 📝 代码变更

### 1. 实体类变更

**DubboServiceNodeEntity**:
- 添加字段：`registrationTime`, `lastHeartbeatTime`, `isOnline`, `isHealthy`
- 更新构造函数和更新方法，支持从 `ProviderInfo` 设置这些字段

### 2. Mapper 变更

**DubboServiceNodeMapper**:
- 添加新方法：
  - `updateLastHeartbeat()` - 更新最后心跳时间
  - `updateOnlineStatus()` - 更新在线状态
  - `updateHealthStatus()` - 更新健康状态
  - `markOffline()` - 标记节点为离线
  - `findOnlineNodes()` - 查找在线节点
  - `findNodesByHealthCheckTimeout()` - 查找健康检查超时的节点
  - `countOnlineNodes()` - 统计在线节点数量
  - `countHealthyNodes()` - 统计健康节点数量
  - `deleteOfflineNodesBefore()` - 删除指定时间之前的离线节点

### 3. Service 变更

**DubboServiceDbService**:
- `convertToProviderInfo()` 方法重构：
  - 直接从 `DubboServiceNodeEntity` 获取心跳和状态信息
  - 从 `zk_dubbo_service_method` 和 `zk_dubbo_method_parameter` 查询方法和参数
  - 移除对 `zk_provider_info`、`zk_provider_method`、`zk_provider_parameter` 的依赖

**ProviderInfoDbService** (废弃或重构):
- 建议废弃或重构为 `NodeStatusService`
- 将心跳和状态更新方法迁移到 `DubboServiceDbService` 或新的服务类

**HeartbeatMonitorService**:
- 更新心跳检测逻辑，使用 `DubboServiceNodeMapper` 更新状态
- 移除对 `ProviderInfoDbService` 的依赖

**ZkWatcherSchedulerService**:
- 移除对 `ProviderInfoEntity` 的查询
- 使用 `DubboServiceNodeEntity` 和 `DubboServiceEntity` 判断审批状态

**McpExecutorService**:
- 更新 Provider 查找逻辑，使用 `DubboServiceDbService` 查询

---

## 🔄 迁移步骤

### 步骤1: 执行数据库迁移脚本
```bash
mysql -u username -p database_name < migration_remove_provider_tables.sql
```

### 步骤2: 更新代码
1. 更新实体类
2. 更新 Mapper XML 和接口
3. 重构 Service 类
4. 更新所有使用废弃表的地方

### 步骤3: 测试验证
1. 验证心跳检测功能
2. 验证服务调用功能
3. 验证虚拟项目功能
4. 验证审批流程

### 步骤4: 清理废弃表（可选）
```sql
-- 确认数据迁移成功后执行
DROP TABLE IF EXISTS `zk_provider_parameter`;
DROP TABLE IF EXISTS `zk_provider_method`;
DROP TABLE IF EXISTS `zk_provider_info`;
```

---

## ⚠️ 注意事项

1. **数据迁移**: 确保在迁移前备份数据
2. **兼容性**: 某些代码可能仍在使用废弃的表，需要逐步迁移
3. **性能**: 方法和参数信息改为关联查询，可能影响性能，建议添加缓存
4. **测试**: 充分测试所有相关功能，确保重构后功能正常

---

## ✅ 优势

1. **简化架构**: 减少表数量，降低维护成本
2. **数据一致性**: 心跳和状态信息与节点信息在同一张表，避免数据不一致
3. **查询优化**: 减少关联查询，提高查询效率
4. **逻辑清晰**: 节点信息集中管理，逻辑更加清晰

---

## 📚 相关文件

- `migration_remove_provider_tables.sql` - 数据库迁移脚本
- `DubboServiceNodeEntity.java` - 实体类
- `DubboServiceNodeMapper.xml` - Mapper XML
- `DubboServiceDbService.java` - 服务类


