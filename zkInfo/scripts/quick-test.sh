#!/bin/bash

echo "=== 快速测试 ==="
echo ""

echo "1. 检查 Nacos 中的服务注册："
curl -s "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=virtual-data-analysis&groupName=mcp-server" | jq -r '.hosts[]? | "  - \(.ip):\(.port) (healthy: \(.healthy))"'

echo ""
echo "2. 测试 mcp-router-v3 查找 data-analysis："
response=$(curl -s "http://mcp-bridge.test/mcp-bridge/mcp/router/tools/data-analysis")
if echo "$response" | grep -q "目标服务不可用"; then
    echo "  ❌ 返回: 目标服务不可用"
else
    tools_count=$(echo "$response" | jq -r '.tools | length' 2>/dev/null || echo "0")
    if [ "$tools_count" != "0" ] && [ "$tools_count" != "null" ]; then
        echo "  ✅ 成功，找到 $tools_count 个 tools"
    else
        echo "  ⚠️ 响应: $response"
    fi
fi

echo ""
echo "3. 测试 mcp-router-v3 查找 virtual-data-analysis："
response2=$(curl -s "http://mcp-bridge.test/mcp-bridge/mcp/router/tools/virtual-data-analysis")
tools_count2=$(echo "$response2" | jq -r '.tools | length' 2>/dev/null || echo "0")
if [ "$tools_count2" != "0" ] && [ "$tools_count2" != "null" ]; then
    echo "  ✅ 成功，找到 $tools_count2 个 tools"
else
    echo "  ⚠️ 响应: $response2" | head -3
fi

echo ""
echo "4. 测试 zkInfo 直接调用："
response3=$(curl -s "http://mcp-bridge.test:9091/mcp/message?sessionId=test123" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "X-Service-Name: data-analysis" \
    -d '{"id": "test1", "method": "tools/list", "params": {}}')
tools_count3=$(echo "$response3" | jq -r '.result.tools | length' 2>/dev/null || echo "0")
if [ "$tools_count3" != "0" ] && [ "$tools_count3" != "null" ]; then
    echo "  ✅ 成功，找到 $tools_count3 个 tools"
else
    echo "  ⚠️ 响应: $response3" | head -5
fi

