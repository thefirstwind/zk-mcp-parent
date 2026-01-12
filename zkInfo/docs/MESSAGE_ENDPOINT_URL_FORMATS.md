# Message Endpoint URL 格式说明

## 📋 概述

zkInfo 在创建 SSE 连接时，会根据不同的环境（本地、本地域名、生产、生产域名）自动构建正确的 message endpoint URL。

## 🔗 支持的 URL 格式

### 1. 本地环境

#### 1.1 localhost
```
http://localhost:9091/mcp/virtual-test-endpoint003/message?sessionId=xxx
```

**触发条件**：
- 请求头 `Host: localhost:9091`
- 无代理头（X-Forwarded-*）

#### 1.2 127.0.0.1
```
http://127.0.0.1:9091/mcp/virtual-test-endpoint003/message?sessionId=xxx
```

**触发条件**：
- 请求头 `Host: 127.0.0.1:9091`
- 无代理头（X-Forwarded-*）

### 2. 本地域名环境

#### 2.1 本地域名（无端口）
```
http://mcp-bridge.test/mcp/virtual-test-endpoint003/message?sessionId=xxx
```

**触发条件**：
- 请求头 `Host: mcp-bridge.test`（无端口，或端口为 80）
- 无代理头（X-Forwarded-*）
- 或 `X-Forwarded-Host: mcp-bridge.test`（无端口）

### 3. 生产环境

#### 3.1 生产 IP + 端口
```
http://10.138.17.208:8080/mcp/virtual-test-endpoint003/message?sessionId=xxx
```

**触发条件**：
- 请求头 `Host: 10.138.17.208:8080`
- 无代理头（X-Forwarded-*）
- 或 `X-Forwarded-Host: 10.138.17.208` + `X-Forwarded-Port: 8080`

### 4. 生产域名环境

#### 4.1 生产域名 + context-path
```
http://srv.test.pajk.com/mcp-metainfo/mcp/virtual-test-endpoint003/message?sessionId=xxx
```

**触发条件**：
- 请求头 `Host: srv.test.pajk.com`（无端口，或端口为 80/443）
- `X-Forwarded-Prefix: /mcp-metainfo`（或从请求路径中提取）
- 或 `X-Forwarded-Host: srv.test.pajk.com` + `X-Forwarded-Prefix: /mcp-metainfo`

## 🔍 URL 构建逻辑

### 优先级顺序

1. **X-Forwarded-Host + X-Forwarded-Proto + X-Forwarded-Port + X-Forwarded-Prefix**
   - 适用于反向代理环境（Nginx、Kong 等）
   - 最准确，由反向代理设置

2. **Host 头 + 请求 Scheme + Context-Path**
   - 适用于直接访问或简单代理
   - 从请求中获取

3. **默认配置**
   - 回退方案：`http://127.0.0.1:9091` + context-path

### Context-Path 提取逻辑

1. **X-Forwarded-Prefix**（最高优先级）
   - 反向代理通常设置此头
   - 例如：`X-Forwarded-Prefix: /mcp-metainfo`

2. **HttpServletRequest.getContextPath()**
   - 从 Servlet 请求中获取
   - 最准确的方式

3. **配置文件 `server.servlet.context-path`**
   - 从 application.yml 读取
   - 默认值：空（无 context-path）

### 端口处理规则

- **标准端口（80/443）**：不显示在 URL 中
  - `http://example.com`（不是 `http://example.com:80`）
  - `https://example.com`（不是 `https://example.com:443`）

- **非标准端口**：显示在 URL 中
  - `http://localhost:9091`
  - `http://10.138.17.208:8080`

### 协议（Scheme）处理

- **X-Forwarded-Proto**：优先使用（反向代理设置）
- **请求 Scheme**：其次使用（从请求中获取）
- **默认**：`http`

## 📝 代码实现

### SseController.buildBaseUrlFromRequest()

```java
// 1. 提取 context-path
String contextPath = extractContextPath(request);

// 2. 优先使用代理头
if (forwardedHost != null) {
    scheme = forwardedProto != null ? forwardedProto : "http";
    hostPort = forwardedHost;
    // 处理端口（标准端口不显示）
    if (!hostPort.contains(":") && forwardedPort != null) {
        int port = Integer.parseInt(forwardedPort);
        if (!((scheme.equals("http") && port == 80) || 
              (scheme.equals("https") && port == 443))) {
            hostPort = hostPort + ":" + forwardedPort;
        }
    }
    return scheme + "://" + hostPort + contextPath;
}

// 3. 其次使用 Host 头
if (host != null) {
    // 处理端口（标准端口不显示）
    // ...
    return reqScheme + "://" + hostWithoutPort + contextPath;
}

// 4. 回退到默认
return "http://127.0.0.1:9091" + contextPath;
```

## 🧪 测试场景

### 场景 1：本地 localhost
```bash
curl -N "http://localhost:9091/sse/test-endpoint" \
  -H "Accept: text/event-stream"
```
**预期 endpoint**：`http://localhost:9091/mcp/virtual-test-endpoint/message?sessionId=xxx`

### 场景 2：本地 127.0.0.1
```bash
curl -N "http://127.0.0.1:9091/sse/test-endpoint" \
  -H "Accept: text/event-stream"
```
**预期 endpoint**：`http://127.0.0.1:9091/mcp/virtual-test-endpoint/message?sessionId=xxx`

### 场景 3：本地域名
```bash
curl -N "http://mcp-bridge.test/sse/test-endpoint" \
  -H "Accept: text/event-stream"
```
**预期 endpoint**：`http://mcp-bridge.test/mcp/virtual-test-endpoint/message?sessionId=xxx`

### 场景 4：生产 IP
```bash
curl -N "http://10.138.17.208:8080/sse/test-endpoint" \
  -H "Accept: text/event-stream"
```
**预期 endpoint**：`http://10.138.17.208:8080/mcp/virtual-test-endpoint/message?sessionId=xxx`

### 场景 5：生产域名 + context-path
```bash
curl -N "http://srv.test.pajk.com/mcp-metainfo/sse/test-endpoint" \
  -H "Accept: text/event-stream" \
  -H "X-Forwarded-Prefix: /mcp-metainfo"
```
**预期 endpoint**：`http://srv.test.pajk.com/mcp-metainfo/mcp/virtual-test-endpoint/message?sessionId=xxx`

## 🔧 配置说明

### application.yml

```yaml
server:
  port: 9091
  servlet:
    context-path: /mcp-metainfo  # 生产环境设置，本地环境通常为空
```

### Nginx 配置示例

```nginx
location /mcp-metainfo/ {
    proxy_pass http://zkInfo-backend/;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header X-Forwarded-Port $server_port;
    proxy_set_header X-Forwarded-Prefix /mcp-metainfo;
}
```

## 📊 环境识别流程

```
请求到达
  ↓
检查 X-Forwarded-Host?
  ├── 是 → 使用 X-Forwarded-Proto + X-Forwarded-Host + X-Forwarded-Port + X-Forwarded-Prefix
  └── 否 → 检查 Host 头?
      ├── 是 → 使用 Host + Scheme + Context-Path
      └── 否 → 使用默认配置 (127.0.0.1:9091)
  ↓
提取 Context-Path
  ├── X-Forwarded-Prefix（最高优先级）
  ├── HttpServletRequest.getContextPath()
  └── 配置文件 server.servlet.context-path
  ↓
处理端口
  ├── 标准端口（80/443）→ 不显示
  └── 非标准端口 → 显示
  ↓
构建最终 URL
  scheme://host[:port][/context-path]/mcp/{serviceName}/message?sessionId=xxx
```

## ✅ 验证方法

### 1. 查看日志

zkInfo 会在日志中记录构建的 base URL：

```
✅ Built base URL from forwarded headers: http://srv.test.pajk.com/mcp-metainfo
✅ Built base URL from Host header: http://localhost:9091
⚠️ Built base URL from default config (fallback): http://127.0.0.1:9091
```

### 2. 检查 SSE 响应

SSE 连接建立后，会发送 `event:endpoint` 事件，包含完整的 message endpoint URL：

```
event:endpoint
data:http://srv.test.pajk.com/mcp-metainfo/mcp/virtual-test-endpoint003/message?sessionId=xxx
```

## 🔄 mcp-router-v3 感知

mcp-router-v3 在调用虚拟项目时，会从 Nacos metadata 中读取 `sseMessageEndpoint` 和 `contextPath`：

```json
{
  "sseMessageEndpoint": "/mcp/virtual-test-endpoint003/message",
  "contextPath": "/mcp-metainfo"  // 如果配置了 context-path
}
```

mcp-router-v3 的 URL 构建逻辑：
1. 从 `serverInfo` 获取 IP 和端口：`http://{ip}:{port}`
2. 从 metadata 读取 `contextPath`（如果存在）：`http://{ip}:{port}{contextPath}`
3. 从 metadata 读取 `sseMessageEndpoint`：`/mcp/{serviceName}/message`
4. 拼接完整 URL：`baseUrl + sseMessageEndpoint + ?sessionId=xxx`

**示例**：
- 无 context-path：`http://10.138.17.208:8080/mcp/virtual-test-endpoint003/message?sessionId=xxx`
- 有 context-path：`http://srv.test.pajk.com/mcp-metainfo/mcp/virtual-test-endpoint003/message?sessionId=xxx`

## 📚 相关文件

- `SseController.java`: WebMVC 模式的 SSE 连接处理
- `MultiEndpointMcpRouterConfig.java`: WebFlux 模式的 SSE 连接处理（已禁用）
- `application.yml`: 配置文件

