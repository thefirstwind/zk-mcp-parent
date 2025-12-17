#!/bin/bash

echo "=== 快速验证 ==="
echo ""

echo "1. 检查 Nacos 服务注册："
nacos_check=$(curl -s "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=virtual-data-analysis&groupName=mcp-server&healthyOnly=true")
healthy_count=$(echo "$nacos_check" | jq '[.hosts[]? | select(.healthy == true)] | length' 2>/dev/null || echo "0")
if [ "$healthy_count" != "0" ] && [ "$healthy_count" != "null" ]; then
    echo "  ✅ virtual-data-analysis 有 $healthy_count 个健康实例"
else
    echo "  ❌ virtual-data-analysis 没有健康实例"
    exit 1
fi

echo ""
echo "2. 测试 zkInfo 直接调用："
response=$(curl -s "http://mcp-bridge.test:9091/mcp/message?sessionId=test123" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "X-Service-Name: data-analysis" \
    -d '{"id": "test1", "method": "tools/list", "params": {}}')
tools_count=$(echo "$response" | jq -r '.result.tools | length' 2>/dev/null || echo "0")
if [ "$tools_count" != "0" ] && [ "$tools_count" != "null" ]; then
    echo "  ✅ zkInfo tools/list 成功：找到 $tools_count 个 tools"
else
    echo "  ❌ zkInfo tools/list 失败"
    echo "$response" | head -5
    exit 1
fi

echo ""
echo "3. 测试 mcp-router-v3 调用（可能需要重启）："
response2=$(curl -s --max-time 5 "http://mcp-bridge.test/mcp-bridge/mcp/router/tools/data-analysis" 2>&1)
if echo "$response2" | jq -e '.tools | length' >/dev/null 2>&1; then
    tools_count2=$(echo "$response2" | jq -r '.tools | length' 2>/dev/null || echo "0")
    if [ "$tools_count2" != "0" ] && [ "$tools_count2" != "null" ]; then
        echo "  ✅ mcp-router-v3 tools/list 成功：找到 $tools_count2 个 tools"
    else
        echo "  ⚠️ mcp-router-v3 tools/list 返回空（可能需要重启）"
    fi
elif echo "$response2" | grep -q "目标服务不可用"; then
    echo "  ⚠️ mcp-router-v3 返回 '目标服务不可用'（需要重启 mcp-router-v3 以应用 virtual- 前缀支持）"
else
    echo "  ⚠️ mcp-router-v3 调用失败或超时"
    echo "$response2" | head -3
fi

echo ""
echo "4. 测试 tools/call："
response3=$(curl -s --max-time 10 "http://mcp-bridge.test:9091/mcp/message?sessionId=test123" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "X-Service-Name: data-analysis" \
    -d '{"id": "1005", "method": "tools/call", "params": {"name": "com.zkinfo.demo.service.UserService.getAllUsers", "arguments": {}}}')
if echo "$response3" | jq -e '.result != null and .error == null' >/dev/null 2>&1; then
    echo "  ✅ zkInfo tools/call 成功"
else
    echo "  ❌ zkInfo tools/call 失败"
    echo "$response3" | jq '.error' 2>/dev/null || echo "$response3" | head -3
fi

echo ""
echo "=== 验证完成 ==="

