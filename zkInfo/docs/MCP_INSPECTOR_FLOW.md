# MCP Inspector 连接流程文档

参考 `mcp-router-v3` 的实现标准，说明在 MCP Inspector 上建立 SSE 连接后的完整流程。

## 📋 标准流程

### 1. 建立 SSE 连接

**请求：**
```bash
GET http://192.168.0.101:9091/sse/test-virtual-endpoint122704
Accept: text/event-stream
```

**响应：**
```
event:endpoint
data:http://192.168.0.101:9091/mcp/virtual-test-virtual-endpoint122704/message?sessionId=xxx

:heartbeat
:heartbeat
...
```

**说明：**
- 服务器返回 `event:endpoint` 事件，包含 message endpoint URL
- message endpoint URL 格式：`/mcp/{serviceName}/message?sessionId={sessionId}`
- 对于虚拟项目，`serviceName` 格式为 `virtual-{endpointName}`
- 之后服务器会定期发送 `:heartbeat` 保持连接活跃

---

### 2. 保持 SSE 连接

**重要：** 必须保持 SSE 连接活跃，才能接收后续的响应。

客户端应该在后台保持 SSE 连接运行，同时使用 message endpoint 发送请求。

---

### 3. 发送 initialize 请求

**请求：**
```bash
POST http://192.168.0.101:9091/mcp/virtual-test-virtual-endpoint122704/message?sessionId=xxx
Content-Type: application/json

{
    "jsonrpc": "2.0",
    "method": "initialize",
    "params": {
        "protocolVersion": "2024-11-05",
        "capabilities": {
            "tools": {"listChanged": true},
            "resources": {"listChanged": true},
            "prompts": {"listChanged": true}
        },
        "clientInfo": {
            "name": "mcp-inspector",
            "version": "1.0.0"
        }
    },
    "id": 1
}
```

**HTTP 响应：**
```json
{
    "status": "accepted",
    "message": "Request accepted, response will be sent via SSE"
}
```

**SSE 响应（通过 SSE 流返回）：**
```json
{
    "jsonrpc": "2.0",
    "id": 1,
    "result": {
        "protocolVersion": "2024-11-05",
        "serverInfo": {
            "name": "zkInfo-MCP-Server",
            "version": "1.0.0"
        },
        "capabilities": {
            "tools": {
                "listChanged": true
            },
            "resources": {
                "subscribe": false,
                "listChanged": true
            },
            "prompts": {
                "listChanged": true
            }
        }
    }
}
```

**说明：**
- HTTP POST 请求立即返回 `202 Accepted`
- 实际响应通过 SSE 流发送
- `capabilities` 中的 `listChanged: true` 会触发客户端自动调用 `tools/list`、`resources/list`、`prompts/list`

---

### 4. 发送 tools/list 请求

**请求：**
```bash
POST http://192.168.0.101:9091/mcp/virtual-test-virtual-endpoint122704/message?sessionId=xxx
Content-Type: application/json

{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "params": {},
    "id": 2
}
```

**HTTP 响应：**
```json
{
    "status": "accepted",
    "message": "Request accepted, response will be sent via SSE"
}
```

**SSE 响应（通过 SSE 流返回）：**
```json
{
    "jsonrpc": "2.0",
    "id": 2,
    "result": {
        "tools": [
            {
                "name": "com.pajk.provider2.service.UserService.createUser",
                "description": "调用 com.pajk.provider2.service.UserService 服务的 createUser 方法",
                "inputSchema": {
                    "type": "object",
                    "properties": {}
                }
            },
            ...
        ],
        "toolsMeta": {}
    }
}
```

---

### 5. （可选）发送 resources/list 请求

**请求：**
```bash
POST http://192.168.0.101:9091/mcp/virtual-test-virtual-endpoint122704/message?sessionId=xxx
Content-Type: application/json

{
    "jsonrpc": "2.0",
    "method": "resources/list",
    "params": {},
    "id": 3
}
```

**SSE 响应：**
```json
{
    "jsonrpc": "2.0",
    "id": 3,
    "result": {
        "resources": []
    }
}
```

---

### 6. （可选）发送 prompts/list 请求

**请求：**
```bash
POST http://192.168.0.101:9091/mcp/virtual-test-virtual-endpoint122704/message?sessionId=xxx
Content-Type: application/json

{
    "jsonrpc": "2.0",
    "method": "prompts/list",
    "params": {},
    "id": 4
}
```

**SSE 响应：**
```json
{
    "jsonrpc": "2.0",
    "id": 4,
    "result": {
        "prompts": []
    }
}
```

---

## 🔑 关键点

1. **SSE 连接必须保持活跃**：所有响应都通过 SSE 流返回，如果连接断开，将无法接收响应。

2. **HTTP POST 返回 202 Accepted**：符合 MCP 协议标准，实际响应通过 SSE 发送。

3. **Message Endpoint URL 格式**：
   - 路径参数方式：`/mcp/{serviceName}/message?sessionId={sessionId}`
   - 查询参数方式：`/mcp/message?sessionId={sessionId}`（向后兼容）

4. **标准调用顺序**：
   - `initialize` → `tools/list` → `resources/list` → `prompts/list`
   - 如果 `initialize` 响应中的 `capabilities` 设置了 `listChanged: true`，客户端会自动调用相应的 `list` 方法

5. **响应格式**：所有响应都是 JSON-RPC 2.0 格式，通过 SSE 流的 `data:` 行发送。

---

## 🧪 测试脚本

使用 `test-mcp-inspector-flow.sh` 脚本可以完整测试整个流程：

```bash
cd zk-mcp-parent/zkInfo/scripts
./test-mcp-inspector-flow.sh test-virtual-endpoint122704
```

脚本会自动：
1. 建立 SSE 连接
2. 提取 message endpoint URL
3. 保持 SSE 连接（后台运行）
4. 发送 `initialize` 请求
5. 发送 `tools/list` 请求
6. 发送 `resources/list` 请求（可选）
7. 发送 `prompts/list` 请求（可选）
8. 显示完整的 SSE 响应日志

---

## 📝 参考

- [mcp-router-v3 MCP Inspector 实现](https://github.com/your-org/mcp-router-v3)
- [MCP 官方文档](https://modelcontextprotocol.io)
- [MCP Inspector Tools List Fix](mcp-router-v3/docs/MCP_INSPECTOR_TOOLS_LIST_FIX.md)


