# Nacos gRPC 端口连接问题修复

## 问题描述

在局域网环境中，虽然可以通过 `telnet` 连接到 Nacos 的 gRPC 端口（9848），但项目启动后无法连接。

## 问题原因

Nacos Java SDK 的工作流程：
1. 首先通过 HTTP 端口（8848）连接 Nacos 服务器
2. 从服务器获取服务器信息
3. 自动尝试连接 gRPC 端口（9848）进行服务注册和发现

**问题**：当 SDK 从服务器获取地址时，可能会获取到 `localhost` 或 `127.0.0.1`，而不是配置的 IP 地址（如 `192.168.0.101`），导致 gRPC 连接失败。

## 解决方案

使用系统属性设置 Nacos 客户端 IP，确保 SDK 使用正确的 IP 地址连接 gRPC 端口，而不是 localhost。

### 修复代码

已在以下配置类中添加了客户端 IP 设置：

1. **zkInfo**: `com.pajk.mcpmetainfo.core.config.NacosConfig`
2. **mcp-router-v3**: `com.pajk.mcpbridge.core.config.NacosMcpRegistryConfig`

### 配置逻辑

```java
// 解析 serverAddr，提取主机地址
String[] addrParts = serverAddr.split(":");
if (addrParts.length == 2) {
    String host = addrParts[0];
    // 如果配置的是 IP 地址而不是 localhost，设置客户端 IP
    if (!host.equals("localhost") && !host.equals("127.0.0.1")) {
        System.setProperty("nacos.client.ip", host);
        System.setProperty("com.alibaba.nacos.client.naming.client.ip", host);
    }
}
```

### 工作原理

- 当 `serverAddr` 配置为 `192.168.0.101:8848` 时
- 设置系统属性 `nacos.client.ip=192.168.0.101`
- Nacos SDK 在连接 gRPC 端口时会使用这个 IP，而不是 localhost

## 验证方法

### 1. 检查日志

启动应用后，查看日志中是否有：

```
Setting Nacos client IP for gRPC connection: 192.168.0.101
✅ Nacos NamingService created successfully
```

### 2. 检查连接

如果连接成功，应该能看到：
- 服务注册成功
- 服务发现正常
- 没有 gRPC 连接错误

### 3. 常见错误

#### 错误 1: `MalformedURLException: Error at index 4 in: "9848:8080"`

**原因**：之前错误地设置了 `endpoint` 属性，格式不正确。

**解决**：已移除 `endpoint` 配置，改用系统属性设置客户端 IP。

#### 错误 2: `Failed to connect to Nacos server: 127.0.0.1:9848`

**原因**：SDK 仍然在使用 localhost。

**解决**：检查：
1. `serverAddr` 配置是否正确（应该是 `192.168.0.101:8848`）
2. 系统属性是否设置成功（查看日志）
3. 是否有其他配置覆盖了设置

## 其他解决方案

### 方案一：使用系统属性

在启动应用时设置系统属性：

```bash
-Dnacos.client.ip=192.168.0.101
-Dcom.alibaba.nacos.client.naming.client.ip=192.168.0.101
```

### 方案二：修改 Nacos 服务器配置

在 Nacos 服务器端配置 `application.properties`：

```properties
# 确保 Nacos 服务器监听所有网络接口
server.address=0.0.0.0
```

### 方案三：使用 Nacos 集群模式

如果使用 Nacos 集群，确保所有节点都配置了正确的网络地址。

## 相关配置

### application.yml

```yaml
nacos:
  server-addr: ${NACOS_SERVER_ADDR:192.168.0.101:8848}
  namespace: ${NACOS_NAMESPACE:public}
  username: ${NACOS_USERNAME:nacos}
  password: ${NACOS_PASSWORD:nacos}
```

### 环境变量

```bash
export NACOS_SERVER_ADDR=192.168.0.101:8848
```

## 注意事项

1. **端口要求**：
   - HTTP 端口：8848（必须开放）
   - gRPC 端口：9848（必须开放）

2. **防火墙配置**：
   ```bash
   # 确保防火墙开放两个端口
   sudo firewall-cmd --permanent --add-port=8848/tcp
   sudo firewall-cmd --permanent --add-port=9848/tcp
   sudo firewall-cmd --reload
   ```

3. **网络连通性**：
   ```bash
   # 测试 HTTP 端口
   curl http://192.168.0.101:8848/nacos/v1/console/health
   
   # 测试 gRPC 端口
   telnet 192.168.0.101 9848
   ```

4. **Nacos 版本**：
   - Nacos 2.x 版本使用 gRPC 进行服务注册和发现
   - 确保 Nacos 服务器版本支持 gRPC

## 参考文档

- [Nacos Java SDK 文档](https://nacos.io/docs/latest/guide/user/sdk/)
- [Nacos 服务发现原理](https://nacos.io/docs/latest/guide/user/discovery/)

