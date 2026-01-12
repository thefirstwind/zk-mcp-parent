# SSE 连接地址 Context-Path 修复

**问题描述**: 虚拟节点创建 SSE 连接后，返回的连接 message 地址没有 context-path，在生产环境中项目是带 context-path 的，而且直接用域名不需要端口号。

**修复日期**: 2025-12-26

---

## 🔍 问题分析

### 问题现象

1. **缺少 context-path**: SSE 连接返回的 message endpoint URL 没有包含 `server.servlet.context-path` 配置
2. **端口号处理**: 生产环境使用域名时，标准端口（80/443）应该被移除，但代码没有正确处理
3. **URL 构建**: `buildBaseUrlFromRequest()` 方法没有考虑 context-path 配置

### 影响范围

- 虚拟项目创建的 SSE 连接
- 标准 SSE 端点（`/sse` 和 `/sse/{endpoint}`）
- MCP 客户端初始化时收到的 endpoint URL

---

## ✅ 修复方案

### 1. 添加 Environment 注入

在 `SseController` 中注入 `Environment` 以获取配置：

```java
private final Environment environment;
```

### 2. 修复 buildBaseUrlFromRequest() 方法

主要修改点：

1. **获取 context-path 配置**:
   ```java
   String contextPath = environment.getProperty("server.servlet.context-path", "");
   // 规范化处理：确保以 / 开头，但不以 / 结尾（除非是根路径）
   if (contextPath != null && !contextPath.isEmpty() && !contextPath.equals("/")) {
       if (!contextPath.startsWith("/")) {
           contextPath = "/" + contextPath;
       }
       if (contextPath.endsWith("/") && contextPath.length() > 1) {
           contextPath = contextPath.substring(0, contextPath.length() - 1);
       }
   } else {
       contextPath = "";
   }
   ```

2. **在构建 baseUrl 时添加 context-path**:
   ```java
   // 使用代理头时
   String baseUrl = scheme + "://" + hostPort + contextPath;
   
   // 使用 Host 头时
   String baseUrl = reqScheme + "://" + hostWithoutPort + contextPath;
   
   // 默认配置时
   String baseUrl = "http://127.0.0.1:" + defaultPort + contextPath;
   ```

3. **优化端口号处理**:
   - 标准端口（80/443）自动移除，适用于生产环境使用域名的情况
   - 非标准端口保留，适用于开发环境

---

## 📋 配置示例

### 开发环境（带端口）

```yaml
server:
  port: 9091
  servlet:
    context-path: /
```

**生成的 URL**: `http://localhost:9091/mcp/message?sessionId=xxx`

### 生产环境（域名 + context-path）

```yaml
server:
  port: 8080
  servlet:
    context-path: /zkinfo
```

**请求头**:
- `X-Forwarded-Host: example.com`
- `X-Forwarded-Proto: https`
- `X-Forwarded-Port: 443` (可选，标准端口会被忽略)

**生成的 URL**: `https://example.com/zkinfo/mcp/message?sessionId=xxx`

### 生产环境（域名 + 根路径）

```yaml
server:
  port: 8080
  servlet:
    context-path: /
```

**请求头**:
- `X-Forwarded-Host: example.com`
- `X-Forwarded-Proto: https`

**生成的 URL**: `https://example.com/mcp/message?sessionId=xxx`

---

## 🧪 测试验证

### 测试场景 1: 开发环境（localhost + 端口）

```bash
# 请求
curl -v "http://localhost:9091/sse/test-endpoint"

# 预期响应（endpoint 事件）
event: endpoint
data: http://localhost:9091/mcp/message?sessionId=xxx
```

### 测试场景 2: 生产环境（域名 + context-path）

```bash
# 请求（通过反向代理）
curl -v "https://example.com/zkinfo/sse/test-endpoint" \
  -H "X-Forwarded-Host: example.com" \
  -H "X-Forwarded-Proto: https"

# 预期响应（endpoint 事件）
event: endpoint
data: https://example.com/zkinfo/mcp/message?sessionId=xxx
```

### 测试场景 3: 生产环境（域名 + 非标准端口）

```bash
# 请求（通过反向代理，非标准端口）
curl -v "https://example.com:8443/zkinfo/sse/test-endpoint" \
  -H "X-Forwarded-Host: example.com" \
  -H "X-Forwarded-Proto: https" \
  -H "X-Forwarded-Port: 8443"

# 预期响应（endpoint 事件）
event: endpoint
data: https://example.com:8443/zkinfo/mcp/message?sessionId=xxx
```

---

## 🔧 配置说明

### application.yml 配置

```yaml
server:
  port: 9091
  servlet:
    context-path: /zkinfo  # 生产环境设置 context-path
```

### 环境变量配置

```bash
# 生产环境
export SERVER_SERVLET_CONTEXT_PATH=/zkinfo
```

### Nginx 反向代理配置示例

```nginx
server {
    listen 443 ssl;
    server_name example.com;
    
    location /zkinfo/ {
        proxy_pass http://localhost:9091/;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Port $server_port;
        
        # SSE 支持
        proxy_buffering off;
        proxy_cache off;
        proxy_set_header Connection '';
        proxy_http_version 1.1;
        chunked_transfer_encoding off;
    }
}
```

---

## 📝 相关代码

### 修改文件

- `zk-mcp-parent/zkInfo/src/main/java/com/pajk/mcpmetainfo/core/controller/SseController.java`
  - 添加 `Environment` 注入
  - 修复 `buildBaseUrlFromRequest()` 方法

### 关键方法

- `buildBaseUrlFromRequest()`: 从请求头构建 base URL，支持 context-path 和域名配置

---

## ✅ 验证清单

- [x] context-path 正确添加到 base URL
- [x] 标准端口（80/443）在生产环境被移除
- [x] 非标准端口在开发环境保留
- [x] 支持 X-Forwarded-* 代理头
- [x] 支持 Host 头回退
- [x] 默认配置回退正常工作

---

## 🚀 部署说明

1. **更新配置**: 确保生产环境的 `application.yml` 中配置了正确的 `context-path`
2. **重启服务**: 重启 zkInfo 服务使配置生效
3. **验证连接**: 创建虚拟项目并建立 SSE 连接，检查返回的 endpoint URL 是否包含 context-path

---

**修复版本**: 1.0.0  
**修复日期**: 2025-12-26



