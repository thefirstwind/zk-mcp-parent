# 📞 zkInfo MCP服务调用完整指南

## 🎯 **概述**

zkInfo项目现在支持通过MCP (Model Context Protocol) 格式直接调用Dubbo Provider服务！这使得AI系统和其他客户端可以通过标准化的REST API调用传统的RPC服务。

## 🔧 **完整的调用链路**

```
AI/Client -> MCP API -> zkInfo执行器 -> Dubbo泛化调用 -> Provider服务 -> 返回结果
```

## 🌐 **API接口详解**

### **1. 获取可用的MCP工具**

```bash
# 获取所有应用的MCP工具
curl -s "http://localhost:9091/api/mcp" | jq '.'

# 获取指定应用的MCP工具
curl -s "http://localhost:9091/api/applications/demo-provider/mcp" | jq '.'
```

**响应示例：**
```json
{
  "tools": [
    {
      "name": "com.zkinfo.demo.service.ProductService.getProductById",
      "description": "调用 com.zkinfo.demo.service.ProductService 服务的 getProductById 方法",
      "type": "function",
      "inputSchema": {
        "type": "object",
        "properties": {
          "args": {
            "type": "array",
            "description": "方法参数列表",
            "items": {"type": "any"}
          },
          "timeout": {
            "type": "integer",
            "description": "调用超时时间(毫秒)",
            "default": 3000
          }
        },
        "required": ["args"]
      },
      "version": "1.0.0",
      "group": "demo",
      "provider": "198.18.0.1:20883",
      "online": true
    }
  ],
  "services": [
    {
      "name": "com.zkinfo.demo.service.ProductService",
      "description": "Dubbo服务: com.zkinfo.demo.service.ProductService",
      "interface": "com.zkinfo.demo.service.ProductService",
      "methods": ["getProductById", "searchProducts", "updateStock"],
      "version": "1.0.0",
      "group": "demo",
      "protocol": "dubbo",
      "status": "online"
    }
  ],
  "metadata": {
    "protocolVersion": "1.0",
    "timestamp": "2025-10-28 11:51:00",
    "totalTools": 68,
    "totalServices": 3,
    "onlineProviders": 12,
    "totalProviders": 12,
    "applicationStatus": "online"
  }
}
```

### **2. 同步调用MCP工具**

```bash
curl -X POST "http://localhost:9091/api/mcp/call" \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "com.zkinfo.demo.service.ProductService.getProductById",
    "args": [1],
    "timeout": 5000
  }' | jq '.'
```

**响应示例：**
```json
{
  "success": true,
  "executionTime": 156,
  "result": {
    "realName": "Alice Wang",
    "gender": "F",
    "phone": "13800138001",
    "createTime": "2025-10-27T17:54:34.382741",
    "updateTime": "2025-10-27T17:54:34.382743",
    "id": 1,
    "class": "com.zkinfo.demo.model.User",
    "email": "alice@example.com",
    "age": 25,
    "status": "ACTIVE",
    "username": "alice"
  }
}
```

### **3. 异步调用MCP工具**

```bash
curl -X POST "http://localhost:9091/api/mcp/call-async" \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "com.zkinfo.demo.service.OrderService.createOrder",
    "args": [
      {
        "userId": 456,
        "productId": 123,
        "quantity": 2
      }
    ],
    "timeout": 10000
  }' | jq '.'
```

**响应示例：**
```json
{
  "accepted": true,
  "message": "调用请求已接受，正在异步执行",
  "toolName": "com.zkinfo.demo.service.OrderService.createOrder"
}
```

## 🛠️ **实际调用示例**

### **示例1：查询用户信息**

```bash
# 1. 先查看可用的用户服务工具
curl -s "http://localhost:9091/api/applications/demo-provider/mcp" | \
  jq '.tools[] | select(.name | contains("UserService"))' | head -20

# 2. 调用获取用户信息
curl -X POST "http://localhost:9091/api/mcp/call" \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "com.zkinfo.demo.service.UserService.getUserById",
    "args": [1],
    "timeout": 3000
  }' | jq '.'
```

### **示例2：创建订单**

```bash
# 调用创建订单服务
curl -X POST "http://localhost:9091/api/mcp/call" \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "com.zkinfo.demo.service.OrderService.createOrder",
    "args": [
      {
        "userId": 1,
        "productId": 123,
        "quantity": 2,
        "totalAmount": 1999.98
      }
    ],
    "timeout": 5000
  }' | jq '.'
```

### **示例3：搜索产品**

```bash
# 调用产品搜索服务
curl -X POST "http://localhost:9091/api/mcp/call" \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "com.zkinfo.demo.service.ProductService.searchProducts",
    "args": ["iPhone"],
    "timeout": 3000
  }' | jq '.'
```

## 🔄 **AI系统集成示例**

### **Python客户端示例**

```python
import requests
import json

class ZkInfoMcpClient:
    def __init__(self, base_url="http://localhost:9091"):
        self.base_url = base_url
    
    def get_available_tools(self, application=None):
        """获取可用的MCP工具"""
        if application:
            url = f"{self.base_url}/api/applications/{application}/mcp"
        else:
            url = f"{self.base_url}/api/mcp"
        
        response = requests.get(url)
        return response.json()
    
    def call_tool(self, tool_name, args=None, timeout=3000):
        """调用MCP工具"""
        url = f"{self.base_url}/api/mcp/call"
        payload = {
            "toolName": tool_name,
            "args": args or [],
            "timeout": timeout
        }
        
        response = requests.post(url, json=payload)
        return response.json()
    
    def call_tool_async(self, tool_name, args=None, timeout=3000):
        """异步调用MCP工具"""
        url = f"{self.base_url}/api/mcp/call-async"
        payload = {
            "toolName": tool_name,
            "args": args or [],
            "timeout": timeout
        }
        
        response = requests.post(url, json=payload)
        return response.json()

# 使用示例
client = ZkInfoMcpClient()

# 获取用户信息
result = client.call_tool(
    "com.zkinfo.demo.service.UserService.getUserById",
    args=[1]
)
print("用户信息:", result)

# 创建订单
order_result = client.call_tool(
    "com.zkinfo.demo.service.OrderService.createOrder",
    args=[{
        "userId": 1,
        "productId": 123,
        "quantity": 2
    }]
)
print("订单创建结果:", order_result)
```

### **JavaScript/Node.js客户端示例**

```javascript
class ZkInfoMcpClient {
    constructor(baseUrl = 'http://localhost:9091') {
        this.baseUrl = baseUrl;
    }
    
    async getAvailableTools(application = null) {
        const url = application 
            ? `${this.baseUrl}/api/applications/${application}/mcp`
            : `${this.baseUrl}/api/mcp`;
        
        const response = await fetch(url);
        return await response.json();
    }
    
    async callTool(toolName, args = [], timeout = 3000) {
        const response = await fetch(`${this.baseUrl}/api/mcp/call`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                toolName,
                args,
                timeout
            })
        });
        
        return await response.json();
    }
}

// 使用示例
const client = new ZkInfoMcpClient();

// 获取产品信息
client.callTool(
    'com.zkinfo.demo.service.ProductService.getProductById',
    [123]
).then(result => {
    console.log('产品信息:', result);
});
```

## 🚀 **高级功能**

### **1. 批量调用**

```python
# 批量调用多个服务
tools_to_call = [
    ("com.zkinfo.demo.service.UserService.getUserById", [1]),
    ("com.zkinfo.demo.service.ProductService.getProductById", [123]),
    ("com.zkinfo.demo.service.OrderService.getOrdersByUserId", [1])
]

results = []
for tool_name, args in tools_to_call:
    result = client.call_tool(tool_name, args)
    results.append(result)

print("批量调用结果:", results)
```

### **2. 错误处理**

```python
def safe_call_tool(client, tool_name, args):
    try:
        result = client.call_tool(tool_name, args)
        
        if result.get('success'):
            return result['result']
        else:
            print(f"调用失败: {result.get('error')}")
            return None
            
    except Exception as e:
        print(f"网络错误: {e}")
        return None

# 安全调用示例
user_info = safe_call_tool(
    client, 
    "com.zkinfo.demo.service.UserService.getUserById", 
    [1]
)
```

## 📊 **监控和调试**

### **1. 查看服务状态**

```bash
# 查看所有服务统计信息
curl -s "http://localhost:9091/api/stats" | jq '.'

# 查看特定应用信息
curl -s "http://localhost:9091/api/applications" | jq '.[] | select(.applicationName == "demo-provider")'
```

### **2. 调试ZooKeeper结构**

```bash
# 查看ZooKeeper树结构 (如果支持)
curl -s "http://localhost:9091/api/debug/zk-tree" 2>/dev/null | jq '.' || echo "该端点可能不可用"
```

## 🎯 **最佳实践**

1. **参数类型匹配**: 确保传递的参数类型与Dubbo服务期望的类型匹配
2. **超时设置**: 根据服务复杂度合理设置超时时间
3. **错误处理**: 始终检查返回结果的success字段
4. **异步调用**: 对于耗时操作使用异步调用接口
5. **连接池**: zkInfo会自动管理Dubbo连接池，无需手动处理

## 🔧 **故障排查**

### **常见错误及解决方案**

1. **"未找到可用的服务提供者"**
   ```bash
   # 检查服务是否在线
   curl -s "http://localhost:9091/api/applications" | jq '.[] | {app: .applicationName, status: .status}'
   
   # 检查ZooKeeper连接状态
   curl -s "http://localhost:9091/api/stats" | jq '.zkConnected'
   ```

2. **"调用超时"**
   ```bash
   # 增加超时时间并测试连接
   curl -X POST "http://localhost:9091/api/mcp/call" \
     -H "Content-Type: application/json" \
     -d '{
       "toolName": "com.zkinfo.demo.service.UserService.getUserById",
       "args": [1],
       "timeout": 10000
     }' | jq '.'
   
   # 检查服务响应时间
   time curl -s "http://localhost:9091/api/stats" > /dev/null
   ```

3. **"参数类型错误"**
   ```bash
   # 查看具体工具的参数定义
   curl -s "http://localhost:9091/api/mcp" | \
     jq '.[0].tools[] | select(.name == "com.zkinfo.demo.service.UserService.getUserById") | .inputSchema' | head -20
   
   # 测试正确的参数格式
   curl -X POST "http://localhost:9091/api/mcp/call" \
     -H "Content-Type: application/json" \
     -d '{
       "toolName": "com.zkinfo.demo.service.UserService.getUserById",
       "args": [1],
       "timeout": 3000
     }' | jq '.'
   ```

## 🛠️ **实用Bash脚本示例**

### **快速检查脚本**

```bash
#!/bin/bash
# zkinfo-health-check.sh - 快速检查zkInfo服务状态

BASE_URL="http://localhost:9091"

echo "🔍 检查zkInfo服务状态..."

# 检查服务是否运行
if curl -s "$BASE_URL/api/stats" > /dev/null; then
    echo "✅ zkInfo服务正在运行"
else
    echo "❌ zkInfo服务不可用"
    exit 1
fi

# 检查ZooKeeper连接
ZK_STATUS=$(curl -s "$BASE_URL/api/stats" | jq -r '.zkConnected')
if [ "$ZK_STATUS" = "true" ]; then
    echo "✅ ZooKeeper连接正常"
else
    echo "❌ ZooKeeper连接异常"
fi

# 显示服务统计
echo "
📊 服务统计:"
curl -s "$BASE_URL/api/stats" | jq '{
    在线应用: .onlineApplications,
    总应用数: .totalApplications,
    在线提供者: .onlineProviders,
    总提供者: .totalProviders,
    MCP工具数: .mcpMetadata.totalTools
}'
```

### **批量调用脚本**

```bash
#!/bin/bash
# batch-call.sh - 批量调用MCP工具

BASE_URL="http://localhost:9091"

# 定义要调用的工具列表
declare -a TOOLS=(
    "com.zkinfo.demo.service.UserService.getAllUsers:[]"
    "com.zkinfo.demo.service.UserService.getUserById:[1]"
    "com.zkinfo.demo.service.ProductService.getProductById:[123]"
)

echo "🚀 开始批量调用MCP工具..."

for tool_call in "${TOOLS[@]}"; do
    IFS=':' read -r tool_name args <<< "$tool_call"
    
    echo "
🔧 调用: $tool_name"
    
    response=$(curl -s -X POST "$BASE_URL/api/mcp/call" \
        -H "Content-Type: application/json" \
        -d "{
            \"toolName\": \"$tool_name\",
            \"args\": $args,
            \"timeout\": 5000
        }")
    
    success=$(echo "$response" | jq -r '.success // false')
    
    if [ "$success" = "true" ]; then
        echo "✅ 调用成功"
        echo "$response" | jq '.result' | head -5
    else
        echo "❌ 调用失败"
        echo "$response" | jq '.error // .'
    fi
done

echo "
✨ 批量调用完成！"
```

### **服务发现脚本**

```bash
#!/bin/bash
# discover-services.sh - 发现可用的MCP服务

BASE_URL="http://localhost:9091"

echo "🔍 发现MCP服务..."

# 获取所有应用
echo "
📱 可用应用:"
curl -s "$BASE_URL/api/applications" | jq -r '.[] | "- \(.applicationName) (\(.status))"'

# 获取所有MCP工具
echo "
🔧 可用MCP工具:"
curl -s "$BASE_URL/api/mcp" | jq -r '.[0].tools[] | "- \(.name)"' | sort

# 按服务分组显示
echo "
📦 按服务分组:"
curl -s "$BASE_URL/api/mcp" | jq -r '.[0].services[] | "
服务: \(.name)
状态: \(.status)
方法: \(.methods | join(", "))
提供者: \(.providers | length)
"'
```

通过这套完整的MCP调用机制，zkInfo成功地将传统的Dubbo RPC服务转换为现代化的、AI友好的API接口！🚀

