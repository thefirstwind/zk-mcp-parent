#!/bin/bash

# 🌐 测试浏览器SSE连接的脚本

echo "📊 测试浏览器式SSE连接"
echo "=================================="
echo ""

# 1. 创建流式调用
echo "1️⃣ 创建流式调用..."
RESPONSE=$(curl -s -X POST http://localhost:9091/mcp/jsonrpc \
    -H "Content-Type: application/json" \
    -d '{
        "jsonrpc": "2.0",
        "id": "browser-test",
        "method": "tools/call",
        "params": {
            "name": "com.zkinfo.demo.service.UserService.getUserById",
            "arguments": {"args": [1]},
            "stream": true
        }
    }')

echo "响应: $RESPONSE"
echo ""

# 提取streamId
STREAM_ID=$(echo "$RESPONSE" | jq -r '.result.streamId // empty')

if [ -z "$STREAM_ID" ]; then
    echo "❌ 未能获取stream_id"
    echo "响应内容: $RESPONSE"
    exit 1
fi

echo "✅ 获取stream_id: $STREAM_ID"
echo ""

# 2. 模拟浏览器EventSource连接
echo "2️⃣ 模拟浏览器EventSource连接..."
echo "URL: http://localhost:9091/mcp/stream/$STREAM_ID"
echo ""

# 使用curl模拟EventSource请求
echo "接收到的SSE数据:"
echo "=================================="

timeout 5 curl -N -H "Accept: text/event-stream" \
    -H "Cache-Control: no-cache" \
    -H "Connection: keep-alive" \
    "http://localhost:9091/mcp/stream/$STREAM_ID" 2>&1

echo ""
echo "=================================="
echo ""
echo "✅ 测试完成"
echo ""
echo "💡 您可以在浏览器中访问: http://localhost:9091/mcp-client.html"
echo "   然后在\"SSE流式调用\"标签页测试SSE功能"


