# Nacos v3 API 实现说明

## 概述

zkInfo 已实现使用 Nacos v3 版本的客户端 HTTP API 进行服务注册、发现和配置管理。

参考文档: [Nacos v3 客户端 API](https://nacos.io/docs/latest/manual/user/open-api/)

## 实现内容

### 1. NacosV3ApiService

新建服务类 `com.zkinfo.service.NacosV3ApiService`，实现了以下功能：

#### 1.1 服务实例注册
- **接口**: `POST /nacos/v3/client/ns/instance`
- **方法**: `registerInstance(String serviceName, String ip, int port, String groupName, String clusterName, boolean ephemeral, Map<String, String> metadata)`
- **功能**: 注册服务实例到 Nacos

#### 1.2 服务实例注销
- **接口**: `DELETE /nacos/v3/client/ns/instance`
- **方法**: `deregisterInstance(String serviceName, String ip, int port, String groupName)`
- **功能**: 从 Nacos 注销服务实例

#### 1.3 查询服务实例列表
- **接口**: `GET /nacos/v3/client/ns/instance/list`
- **方法**: `getInstanceList(String serviceName, String groupName, String clusterName, boolean healthyOnly)`
- **功能**: 查询指定服务的实例列表

#### 1.4 获取配置
- **接口**: `GET /nacos/v3/client/cs/config`
- **方法**: `getConfig(String dataId, String group)`
- **功能**: 获取指定配置

### 2. NacosMcpRegistrationService 更新

更新了 `NacosMcpRegistrationService`，支持使用 v3 API：

- **向后兼容**: 保留 SDK 方式，可通过配置切换
- **自动回退**: 如果 v3 API 失败，自动回退到 SDK
- **配置项**: `nacos.v3.api.enabled` 控制是否使用 v3 API（默认: true）

### 3. 配置说明

在 `application.yml` 中添加以下配置：

```yaml
nacos:
  server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
  namespace: ${NACOS_NAMESPACE:public}
  username: ${NACOS_USERNAME:nacos}
  password: ${NACOS_PASSWORD:nacos}
  server:
    context-path: ${NACOS_CONTEXT_PATH:/nacos}  # Nacos 上下文路径，默认为 /nacos
  v3:
    api:
      enabled: ${NACOS_V3_API_ENABLED:true}  # 是否使用 v3 API，默认 true
```

## API 路径格式

根据 Nacos v3 文档，统一路径格式为：
```
[/$nacos.server.contextPath]/v3/client/[module]/[subPath]...
```

例如：
- 服务注册: `/nacos/v3/client/ns/instance`
- 服务查询: `/nacos/v3/client/ns/instance/list`
- 配置获取: `/nacos/v3/client/cs/config`

## 使用方式

### 自动使用 v3 API

默认情况下，`nacos.v3.api.enabled=true`，系统会自动使用 v3 API。

### 回退到 SDK

如果需要使用 SDK 方式，设置：
```yaml
nacos:
  v3:
    api:
      enabled: false
```

## 注意事项

1. **配置发布**: Nacos v3 客户端 API 不提供配置发布接口，配置发布仍使用 SDK 的 `ConfigService.publishConfig()`

2. **查询所有服务**: Nacos v3 客户端 API 不提供查询所有服务的接口，需要使用 Admin API 或维护已注册服务列表

3. **Metadata 传递**: metadata 作为 JSON 字符串传递，格式可能需要根据实际 Nacos 版本调整

4. **错误处理**: 实现了自动回退机制，如果 v3 API 失败，会自动使用 SDK 方式

## 测试验证

编译成功后，重启 zkInfo 服务，查看日志确认：
- `✅ Successfully registered instance to Nacos v3` - 使用 v3 API 注册成功
- `✅ Registered instance to Nacos (SDK)` - 使用 SDK 注册（v3 API 未启用或失败）

## 参考文档

- [Nacos v3 客户端 API 文档](https://nacos.io/docs/latest/manual/user/open-api/)

