# ZK MCP Parent - API 调用示例

本文档提供了 ZK MCP Parent 项目中所有 API 接口的详细调用示例，包括请求格式、响应示例和错误处理。

## 📋 目录

- [1. 基础 API 接口](#1-基础-api-接口)
- [2. MCP 协议接口](#2-mcp-协议接口)
- [3. Dubbo 服务调用](#3-dubbo-服务调用)
- [4. WebSocket 和 SSE](#4-websocket-和-sse)
- [5. 系统监控接口](#5-系统监控接口)

## 1. 基础 API 接口

### 1.1 应用管理 API

#### 获取所有应用列表
```bash
curl -X GET "http://localhost:9091/api/applications" \
  -H "Accept: application/json"
```

**响应示例：**
```json
[
  {
    "name": "demo-provider",
    "version": "1.0.0",
    "status": "ACTIVE",
    "providerCount": 3,
    "interfaceCount": 3,
    "lastHeartbeat": "2024-01-01T12:00:00",
    "providers": [
      {
        "interface": "com.zkinfo.demo.service.UserService",
        "url": "dubbo://192.168.1.100:20883/com.zkinfo.demo.service.UserService",
        "status": "ACTIVE"
      }
    ]
  }
]
```

#### 获取指定应用详细信息
```bash
curl -X GET "http://localhost:9091/api/applications/demo-provider" \
  -H "Accept: application/json"
```

**响应示例：**
```json
{
  "name": "demo-provider",
  "version": "1.0.0",
  "status": "ACTIVE",
  "providerCount": 3,
  "interfaceCount": 3,
  "lastHeartbeat": "2024-01-01T12:00:00",
  "providers": [
    {
      "interface": "com.zkinfo.demo.service.UserService",
      "url": "dubbo://192.168.1.100:20883/com.zkinfo.demo.service.UserService",
      "status": "ACTIVE",
      "methods": ["getUserById", "getAllUsers", "createUser", "updateUser", "deleteUser"]
    },
    {
      "interface": "com.zkinfo.demo.service.ProductService",
      "url": "dubbo://192.168.1.100:20883/com.zkinfo.demo.service.ProductService",
      "status": "ACTIVE",
      "methods": ["getProductById", "getProductsByCategory", "searchProducts"]
    },
    {
      "interface": "com.zkinfo.demo.service.OrderService",
      "url": "dubbo://192.168.1.100:20883/com.zkinfo.demo.service.OrderService",
      "status": "ACTIVE",
      "methods": ["getOrderById", "getOrdersByUserId", "createOrder"]
    }
  ]
}
```

#### 获取应用的 MCP 格式数据
```bash
curl -X GET "http://localhost:9091/api/applications/demo-provider/mcp" \
  -H "Accept: application/json"
```

**响应示例：**
```json
{
  "application": "demo-provider",
  "tools": [
    {
      "name": "com.zkinfo.demo.service.UserService.getUserById",
      "description": "根据ID获取用户信息",
      "inputSchema": {
        "type": "object",
        "properties": {
          "userId": {
            "type": "integer",
            "description": "用户ID"
          }
        },
        "required": ["userId"]
      }
    }
  ]
}
```

### 1.2 服务接口 API

#### 获取所有服务接口列表
```bash
curl -X GET "http://localhost:9091/api/interfaces" \
  -H "Accept: application/json"
```

**响应示例：**
```json
[
  "com.zkinfo.demo.service.UserService",
  "com.zkinfo.demo.service.ProductService",
  "com.zkinfo.demo.service.OrderService"
]
```

#### 获取指定接口的提供者列表
```bash
curl -X GET "http://localhost:9091/api/interfaces/com.zkinfo.demo.service.UserService/providers" \
  -H "Accept: application/json"
```

**响应示例：**
```json
[
  {
    "interface": "com.zkinfo.demo.service.UserService",
    "url": "dubbo://192.168.1.100:20883/com.zkinfo.demo.service.UserService",
    "application": "demo-provider",
    "version": "1.0.0",
    "group": "demo",
    "status": "ACTIVE",
    "methods": [
      {
        "name": "getUserById",
        "parameterTypes": ["java.lang.Long"],
        "returnType": "com.zkinfo.demo.model.User"
      },
      {
        "name": "getAllUsers",
        "parameterTypes": [],
        "returnType": "java.util.List"
      }
    ]
  }
]
```

### 1.3 提供者管理 API

#### 获取所有提供者列表
```bash
curl -X GET "http://localhost:9091/api/providers" \
  -H "Accept: application/json"
```

#### 搜索提供者
```bash
curl -X GET "http://localhost:9091/api/providers/search?keyword=user" \
  -H "Accept: application/json"
```

### 1.4 MCP 转换 API

#### 获取所有服务的 MCP 格式数据
```bash
curl -X GET "http://localhost:9091/api/mcp" \
  -H "Accept: application/json"
```

**响应示例：**
```json
[
  {
    "application": "demo-provider",
    "tools": [
      {
        "name": "com.zkinfo.demo.service.UserService.getUserById",
        "description": "根据ID获取用户信息",
        "inputSchema": {
          "type": "object",
          "properties": {
            "userId": {"type": "integer", "description": "用户ID"}
          },
          "required": ["userId"]
        }
      }
    ]
  }
]
```

### 1.5 系统统计 API

#### 获取系统统计信息
```bash
curl -X GET "http://localhost:9091/api/stats" \
  -H "Accept: application/json"
```

**响应示例：**
```json
{
  "totalApplications": 1,
  "totalProviders": 3,
  "totalInterfaces": 3,
  "activeProviders": 3,
  "inactiveProviders": 0,
  "lastUpdateTime": "2024-01-01T12:00:00",
  "systemUptime": "2h 30m 15s"
}
```

## 2. MCP 协议接口

### 2.1 HTTP JSON-RPC 调用

#### 初始化 MCP 会话
```bash
curl -X POST "http://localhost:9091/mcp/jsonrpc" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {
        "tools": {},
        "resources": {},
        "prompts": {},
        "logging": {}
      },
      "clientInfo": {
        "name": "test-client",
        "version": "1.0.0"
      }
    }
  }'
```

**响应示例：**
```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": {
        "listChanged": true
      },
      "resources": {
        "subscribe": true,
        "listChanged": true
      },
      "prompts": {
        "listChanged": true
      },
      "logging": {}
    },
    "serverInfo": {
      "name": "zk-mcp-server",
      "version": "1.0.0"
    }
  }
}
```

#### 列出所有工具
```bash
curl -X POST "http://localhost:9091/mcp/jsonrpc" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/list",
    "params": {}
  }'
```

**响应示例：**
```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "result": {
    "tools": [
      {
        "name": "com.zkinfo.demo.service.UserService.getUserById",
        "description": "根据ID获取用户信息",
        "inputSchema": {
          "type": "object",
          "properties": {
            "userId": {"type": "integer", "description": "用户ID"}
          },
          "required": ["userId"]
        }
      },
      {
        "name": "com.zkinfo.demo.service.UserService.getAllUsers",
        "description": "获取所有用户列表",
        "inputSchema": {
          "type": "object",
          "properties": {}
        }
      }
    ]
  }
}
```

#### 调用工具
```bash
curl -X POST "http://localhost:9091/mcp/jsonrpc" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "3",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.UserService.getUserById",
      "arguments": {
        "userId": 1
      }
    }
  }'
```

**响应示例：**
```json
{
  "jsonrpc": "2.0",
  "id": "3",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "用户信息获取成功"
      }
    ],
    "isError": false,
    "_meta": {
      "result": {
        "id": 1,
        "username": "alice",
        "email": "alice@example.com",
        "phone": "13800138001",
        "realName": "Alice Wang",
        "age": 25,
        "gender": "F",
        "status": "ACTIVE"
      }
    }
  }
}
```

### 2.2 Resources 功能

#### 列出所有资源
```bash
curl -X GET "http://localhost:9091/mcp/resources" \
  -H "Accept: application/json"
```

**响应示例：**
```json
{
  "resources": [
    {
      "uri": "providers://all",
      "name": "所有服务提供者",
      "description": "系统中所有注册的服务提供者信息",
      "mimeType": "application/json"
    },
    {
      "uri": "providers://com.zkinfo.demo.service.UserService",
      "name": "用户服务提供者",
      "description": "用户服务的提供者信息",
      "mimeType": "application/json"
    }
  ]
}
```


### 2.3 Prompts 功能

#### 列出所有提示
```bash
curl -X GET "http://localhost:9091/mcp/prompts" \
  -H "Accept: application/json"
```

**响应示例：**
```json
{
  "prompts": [
    {
      "name": "analyze-service-health",
      "description": "分析服务健康状态",
      "arguments": [
        {
          "name": "serviceName",
          "description": "服务名称",
          "required": true
        }
      ]
    }
  ]
}
```


### 2.4 Logging 功能


#### 获取日志消息
```bash
curl -X GET "http://localhost:9091/mcp/logging/messages?level=info&limit=10" \
  -H "Accept: application/json"
```

#### 获取日志统计
```bash
curl -X GET "http://localhost:9091/mcp/logging/statistics" \
  -H "Accept: application/json"
```

## 3. Dubbo 服务调用

### 3.1 用户服务 (UserService)

#### 根据ID获取用户
```bash
curl -X POST "http://localhost:9091/mcp/jsonrpc" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "user-1",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.UserService.getUserById",
      "arguments": {
        "userId": 1
      }
    }
  }'
```

#### 获取所有用户
```bash
curl -X POST "http://localhost:9091/mcp/jsonrpc" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "user-2",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.UserService.getAllUsers",
      "arguments": {}
    }
  }'
```

#### 创建新用户
```bash
curl -X POST "http://localhost:9091/mcp/jsonrpc" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "user-3",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.UserService.createUser",
      "arguments": {
        "user": {
          "username": "newuser",
          "email": "newuser@example.com",
          "phone": "13800138999",
          "realName": "New User",
          "age": 28,
          "gender": "M"
        }
      }
    }
  }'
```

### 3.2 产品服务 (ProductService)

#### 根据ID获取产品
```bash
curl -X POST "http://localhost:9091/mcp/jsonrpc" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "product-1",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.ProductService.getProductById",
      "arguments": {
        "productId": 1
      }
    }
  }'
```

#### 根据分类获取产品
```bash
curl -X POST "http://localhost:9091/mcp/jsonrpc" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "product-2",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.ProductService.getProductsByCategory",
      "arguments": {
        "category": "electronics"
      }
    }
  }'
```

### 3.3 订单服务 (OrderService)

#### 根据ID获取订单
```bash
curl -X POST "http://localhost:9091/mcp/jsonrpc" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "order-1",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.OrderService.getOrderById",
      "arguments": {
        "orderId": "ORD-001"
      }
    }
  }'
```

#### 创建新订单
```bash
curl -X POST "http://localhost:9091/mcp/jsonrpc" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "order-2",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.OrderService.createOrder",
      "arguments": {
        "order": {
          "userId": 1,
          "items": [
            {
              "productId": 1,
              "quantity": 2,
              "price": 99.99
            }
          ]
        }
      }
    }
  }'
```

## 4. WebSocket 和 SSE

### 4.1 WebSocket 连接示例

#### JavaScript 客户端
```javascript
// 建立 WebSocket 连接
const ws = new WebSocket('ws://localhost:9091/mcp/ws');

ws.onopen = function(event) {
    console.log('WebSocket 连接已建立');
    
    // 发送初始化请求
    ws.send(JSON.stringify({
        jsonrpc: "2.0",
        id: "ws-init",
        method: "initialize",
        params: {
            protocolVersion: "2024-11-05",
            capabilities: {
                tools: {},
                resources: {},
                prompts: {},
                logging: {}
            },
            clientInfo: {
                name: "websocket-client",
                version: "1.0.0"
            }
        }
    }));
};

ws.onmessage = function(event) {
    const response = JSON.parse(event.data);
    console.log('收到响应:', response);
};

ws.onerror = function(error) {
    console.error('WebSocket 错误:', error);
};

ws.onclose = function(event) {
    console.log('WebSocket 连接已关闭');
};

// 调用工具
function callTool(toolName, args) {
    ws.send(JSON.stringify({
        jsonrpc: "2.0",
        id: "tool-call-" + Date.now(),
        method: "tools/call",
        params: {
            name: toolName,
            arguments: args
        }
    }));
}

// 示例调用
callTool("com.zkinfo.demo.service.UserService.getUserById", {userId: 1});
```

### 4.2 SSE 流式传输示例

#### 创建流式调用
```bash
curl -X POST "http://localhost:9091/mcp/stream" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "stream-1",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.ProductService.searchProducts",
      "arguments": {
        "keyword": "laptop"
      }
    }
  }'
```

**响应示例：**
```json
{
  "streamId": "stream-12345",
  "endpoint": "/mcp/stream/stream-12345"
}
```

#### 接收 SSE 数据
```javascript
const eventSource = new EventSource('http://localhost:9091/mcp/stream/stream-12345');

eventSource.onmessage = function(event) {
    const data = JSON.parse(event.data);
    console.log('收到流式数据:', data);
};

eventSource.onerror = function(error) {
    console.error('SSE 错误:', error);
};
```

## 5. 系统监控接口

### 5.1 健康检查

#### 应用健康检查
```bash
curl -X GET "http://localhost:9091/actuator/health" \
  -H "Accept: application/json"
```

**响应示例：**
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 499963174912,
        "free": 91943821312,
        "threshold": 10485760,
        "exists": true
      }
    },
    "zookeeper": {
      "status": "UP",
      "details": {
        "connection": "CONNECTED",
        "sessionId": "0x100000001",
        "sessionTimeout": 30000
      }
    }
  }
}
```

#### MCP 健康检查
```bash
curl -X GET "http://localhost:9091/mcp/health" \
  -H "Accept: application/json"
```

### 5.2 应用信息

```bash
curl -X GET "http://localhost:9091/actuator/info" \
  -H "Accept: application/json"
```

**响应示例：**
```json
{
  "app": {
    "name": "zkInfo",
    "version": "1.0.0",
    "description": "ZooKeeper 服务发现与 MCP 协议转换"
  },
  "build": {
    "time": "2024-01-01T10:00:00Z",
    "version": "1.0.0"
  }
}
```

### 5.3 MCP 会话统计

```bash
curl -X GET "http://localhost:9091/mcp/sessions/count" \
  -H "Accept: application/json"
```

**响应示例：**
```json
{
  "totalSessions": 5,
  "activeSessions": 3,
  "webSocketSessions": 2,
  "httpSessions": 1
}
```

## 错误处理

### 常见错误响应

#### 404 - 资源不存在
```json
{
  "timestamp": "2024-01-01T12:00:00.000+00:00",
  "status": 404,
  "error": "Not Found",
  "message": "应用不存在: unknown-app",
  "path": "/api/applications/unknown-app"
}
```

#### 500 - 服务调用失败
```json
{
  "jsonrpc": "2.0",
  "id": "error-1",
  "error": {
    "code": -32603,
    "message": "Internal error",
    "data": {
      "details": "Dubbo 服务调用超时"
    }
  }
}
```

#### MCP 协议错误
```json
{
  "jsonrpc": "2.0",
  "id": "mcp-error",
  "error": {
    "code": -32601,
    "message": "Method not found",
    "data": {
      "method": "unknown/method"
    }
  }
}
```

## 客户端 SDK 示例

### Python 客户端
```python
import requests
import json
import websocket
import threading

class ZkMcpClient:
    def __init__(self, base_url="http://localhost:9091"):
        self.base_url = base_url
        self.session_id = None
    
    def initialize(self):
        """初始化 MCP 会话"""
        response = self.call_jsonrpc("initialize", {
            "protocolVersion": "2024-11-05",
            "capabilities": {
                "tools": {},
                "resources": {},
                "prompts": {},
                "logging": {}
            },
            "clientInfo": {
                "name": "python-client",
                "version": "1.0.0"
            }
        })
        return response
    
    def call_jsonrpc(self, method, params=None):
        """调用 JSON-RPC 方法"""
        payload = {
            "jsonrpc": "2.0",
            "id": f"py-{method}-{id(params)}",
            "method": method,
            "params": params or {}
        }
        
        response = requests.post(
            f"{self.base_url}/mcp/jsonrpc",
            json=payload,
            headers={"Content-Type": "application/json"}
        )
        
        return response.json()
    
    def list_tools(self):
        """列出所有工具"""
        return self.call_jsonrpc("tools/list")
    
    def call_tool(self, name, arguments):
        """调用工具"""
        return self.call_jsonrpc("tools/call", {
            "name": name,
            "arguments": arguments
        })
    
    def get_applications(self):
        """获取应用列表"""
        response = requests.get(f"{self.base_url}/api/applications")
        return response.json()

# 使用示例
client = ZkMcpClient()

# 初始化
init_result = client.initialize()
print("初始化结果:", init_result)

# 列出工具
tools = client.list_tools()
print("可用工具:", tools)

# 调用用户服务
user_result = client.call_tool(
    "com.zkinfo.demo.service.UserService.getUserById",
    {"userId": 1}
)
print("用户信息:", user_result)
```

### Java 客户端
```java
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ZkMcpClient {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    public ZkMcpClient(String baseUrl) {
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .build();
        this.objectMapper = new ObjectMapper();
    }
    
    public Mono<JsonRpcResponse> callJsonRpc(String method, Object params) {
        JsonRpcRequest request = JsonRpcRequest.builder()
            .jsonrpc("2.0")
            .id("java-" + System.currentTimeMillis())
            .method(method)
            .params(params)
            .build();
            
        return webClient.post()
            .uri("/mcp/jsonrpc")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(JsonRpcResponse.class);
    }
    
    public Mono<List<ApplicationInfo>> getApplications() {
        return webClient.get()
            .uri("/api/applications")
            .retrieve()
            .bodyToFlux(ApplicationInfo.class)
            .collectList();
    }
}
```

这个文档提供了完整的 API 调用示例，涵盖了项目中的所有主要功能和接口。每个示例都包含了详细的请求格式和响应示例，方便开发者快速上手和集成。


