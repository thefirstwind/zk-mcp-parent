# zkInfo 持久化方案优化总结

**优化日期**: 2025-12-17  
**参考**: mcp-router-v3 的持久化方案

---

## 📋 优化概述

参考 mcp-router-v3 的 `McpServerPersistenceService` 和 `SessionRedisRepository`，优化 zkInfo 的持久化实现，提供：
1. ✅ 完整的 Provider 持久化服务
2. ✅ 定期清理和健康检查任务
3. ✅ 统计信息和监控指标
4. ⏭️ 会话管理优化（可选，需要 Redis）

---

## 🏗️ 架构对比

### mcp-router-v3 的持久化方案

#### 1. McpServerPersistenceService
- **功能**: 持久化服务器注册信息到数据库
- **特性**:
  - 同步持久化服务器注册/注销信息
  - 定期更新服务器心跳状态
  - 自动清理过期的离线服务器
  - 提供统计信息

#### 2. SessionRedisRepository
- **功能**: 使用 Redis 存储会话信息
- **特性**:
  - 三层结构：instances -> instance -> sessions
  - TTL 管理
  - 自动清理空实例

#### 3. McpConnectionEventListener
- **功能**: 监听 Nacos 服务变化，同步持久化

### zkInfo 优化后的持久化方案

#### 1. ProviderPersistenceService（新增）
- **功能**: 持久化 Provider 注册信息到数据库
- **特性**:
  - ✅ 同步持久化 Provider 注册/注销信息
  - ✅ 定期更新 Provider 心跳状态
  - ✅ 自动清理过期的离线 Provider
  - ✅ 提供统计信息

#### 2. ProviderInfoDbService（已优化）
- **功能**: Provider 信息数据库操作
- **新增方法**:
  - `markProviderOffline()` - 标记 Provider 为离线
  - `updateLastHeartbeat()` - 更新最后心跳时间
  - `updateProviderHealthStatus()` - 更新健康状态
  - `findProvidersByHealthCheckTimeout()` - 查找超时 Provider
  - `deleteOfflineProvidersBefore()` - 删除过期记录
  - `countOnlineProviders()` - 统计在线 Provider
  - `countHealthyProviders()` - 统计健康 Provider

#### 3. ProviderInfoMapper（已优化）
- **新增方法**: 对应 ProviderInfoDbService 的数据库操作

---

## 🔧 实现细节

### 1. ProviderPersistenceService

**核心方法**:
```java
// 持久化 Provider 注册信息
persistProviderRegistration(ProviderInfo providerInfo)

// 持久化 Provider 注销信息
persistProviderDeregistration(String zkPath)

// 更新 Provider 健康检查时间
updateProviderHealthCheck(String zkPath)

// 更新 Provider 健康状态
updateProviderHealthStatus(String zkPath, boolean healthy)
```

**定时任务**:
```java
// 每2分钟检查并标记超时 Provider 为离线
@Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
checkAndMarkTimeoutProviders()

// 每天凌晨3点清理过期的离线 Provider 记录
@Scheduled(cron = "0 0 3 * * ?")
cleanupExpiredOfflineProviders()
```

**统计信息**:
```java
getStatistics() -> Map<String, Object>
  - total_registrations
  - total_deregistrations
  - total_heartbeats
  - failed_operations
  - online_providers
  - healthy_providers
```

### 2. ProviderInfoDbService 优化

**新增方法**:
- `markProviderOffline()` - 标记 Provider 为离线
- `updateLastHeartbeat()` - 更新最后心跳时间
- `updateProviderHealthStatus()` - 更新健康状态
- `findProvidersByHealthCheckTimeout()` - 查找超时 Provider
- `deleteOfflineProvidersBefore()` - 删除过期记录
- `countOnlineProviders()` - 统计在线 Provider
- `countHealthyProviders()` - 统计健康 Provider

### 3. ProviderInfoMapper 优化

**新增方法**:
- `markProviderOffline()` - 标记 Provider 为离线
- `updateLastHeartbeat()` - 更新最后心跳时间
- `updateProviderHealthStatus()` - 更新健康状态
- `findProvidersByHealthCheckTimeout()` - 查找超时 Provider
- `deleteOfflineProvidersBefore()` - 删除过期记录
- `countOnlineProviders()` - 统计在线 Provider
- `countHealthyProviders()` - 统计健康 Provider

### 4. ProviderInfoEntity 优化

**新增方法**:
- `toProviderInfo()` - 转换为 ProviderInfo 对象

---

## 📊 数据流

```
ZooKeeper 服务变化
    ↓
ZooKeeperService 监听
    ↓
ProviderPersistenceService.persistProviderRegistration()
    ├─ DubboServiceDbService.saveOrUpdateService()
    ├─ DubboServiceDbService.saveServiceNode()
    └─ ProviderInfoDbService.saveOrUpdateProvider()
    ↓
数据库持久化
    ↓
定期任务
    ├─ checkAndMarkTimeoutProviders() (每2分钟)
    └─ cleanupExpiredOfflineProviders() (每天凌晨3点)
```

---

## 🎯 关键特性

### 1. 同步持久化
- 注册操作频率低，可以同步持久化确保一致性
- 使用 `@Transactional` 保证事务性

### 2. 定期清理
- **健康检查超时**: 每2分钟检查，标记超过5分钟未健康检查的 Provider 为离线
- **过期记录清理**: 每天凌晨3点执行，删除7天前离线的 Provider 记录

### 3. 统计信息
- 提供完整的统计信息（注册数、注销数、心跳数、失败操作数等）
- 支持在线 Provider 和健康 Provider 数量统计

### 4. 错误处理
- 完善的异常处理和日志记录
- 统计失败操作数量

---

## 📚 相关文件

### 新增文件
- `src/main/java/com/zkinfo/service/ProviderPersistenceService.java`

### 修改文件
- `src/main/java/com/zkinfo/service/ProviderInfoDbService.java` - 添加新方法
- `src/main/java/com/zkinfo/mapper/ProviderInfoMapper.java` - 添加新方法
- `src/main/java/com/zkinfo/model/ProviderInfoEntity.java` - 添加 `toProviderInfo()` 方法

---

## ⏭️ 后续优化（可选）

### 1. Redis 会话管理（参考 SessionRedisRepository）
- 使用 Redis 存储会话信息
- 三层结构：instances -> instance -> sessions
- TTL 管理
- 自动清理空实例

### 2. 异步持久化
- 对于高频操作（如心跳更新），可以考虑异步持久化
- 使用 `@Async` 或消息队列

### 3. 批量操作优化
- 批量更新 Provider 状态
- 批量删除过期记录

### 4. 监控和告警
- 集成 Prometheus 指标
- 添加告警规则

---

## 🎯 总结

✅ **已完成**:
- ProviderPersistenceService 完整实现
- ProviderInfoDbService 方法扩展
- ProviderInfoMapper 方法扩展
- ProviderInfoEntity 转换方法
- 定期清理和健康检查任务
- 统计信息和监控指标

⏭️ **待完善**（可选）:
- Redis 会话管理
- 异步持久化
- 批量操作优化
- 监控和告警

