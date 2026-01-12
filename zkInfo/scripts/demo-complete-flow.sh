#!/bin/bash

set -e

echo "=========================================="
echo "zkInfo 完整流程演示"
echo "=========================================="

ZKINFO_URL=${ZKINFO_URL:-http://192.168.0.101:9091}
NACOS_SERVER=${NACOS_SERVER_ADDR:-127.0.0.1:8848}
NACOS_USERNAME=${NACOS_USERNAME:-nacos}
NACOS_PASSWORD=${NACOS_PASSWORD:-nacos}

# 步骤 1: 检查服务状态
echo "步骤 1: 检查 zkInfo 服务状态..."
HEALTH=$(curl -s "${ZKINFO_URL}/actuator/health" 2>/dev/null || echo "{}")
if echo "$HEALTH" | jq -e '.status' > /dev/null 2>&1; then
    echo "$HEALTH" | jq '.'
    echo "✅ zkInfo 服务运行正常"
else
    echo "❌ zkInfo 服务不可用，请先启动服务"
    exit 1
fi

# 步骤 2: 查询已注册的服务
echo ""
echo "步骤 2: 查询已注册的 Dubbo 服务..."
SERVICES=$(curl -s "${ZKINFO_URL}/api/dubbo-services?page=1&size=10" 2>/dev/null || echo "{}")
if echo "$SERVICES" | jq -e '.data' > /dev/null 2>&1; then
    echo "$SERVICES" | jq '.data[] | {id, interfaceName, version, status}' | head -20
else
    echo "⚠️ 未找到已注册的服务"
fi

# 步骤 3: 创建虚拟项目
echo ""
echo "步骤 3: 创建虚拟项目..."
ENDPOINT_NAME="demo-endpoint-$(date +%s)"
VIRTUAL_PROJECT_RESPONSE=$(curl -s -X POST "${ZKINFO_URL}/api/virtual-projects" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"演示虚拟项目\",
    \"code\": \"demo-virtual-project\",
    \"endpointName\": \"${ENDPOINT_NAME}\",
    \"serviceIds\": [1, 2]
  }" 2>/dev/null || echo "{}")

if echo "$VIRTUAL_PROJECT_RESPONSE" | jq -e '.data' > /dev/null 2>&1; then
    echo "$VIRTUAL_PROJECT_RESPONSE" | jq '.data | {id, name, code, endpoint: .endpoint.endpointName}'
    echo "✅ 虚拟项目创建成功"
else
    echo "❌ 虚拟项目创建失败"
    echo "$VIRTUAL_PROJECT_RESPONSE" | jq '.'
    exit 1
fi

# 步骤 4: 等待注册完成
echo ""
echo "步骤 4: 等待虚拟项目注册到 Nacos..."
sleep 5

# 步骤 5: 验证 Nacos 注册
echo ""
echo "步骤 5: 验证 Nacos 注册..."
NACOS_INSTANCES=$(curl -s -u ${NACOS_USERNAME}:${NACOS_PASSWORD} \
  "http://${NACOS_SERVER}/nacos/v1/ns/instance/list?serviceName=virtual-${ENDPOINT_NAME}&namespaceId=public" 2>/dev/null || echo "{}")

if echo "$NACOS_INSTANCES" | jq -e '.hosts' > /dev/null 2>&1; then
    INSTANCE_COUNT=$(echo "$NACOS_INSTANCES" | jq '.hosts | length')
    echo "✅ 找到 ${INSTANCE_COUNT} 个服务实例"
    echo "$NACOS_INSTANCES" | jq '.hosts[] | {ip, port, healthy}'
else
    echo "⚠️ 未找到服务实例"
fi

# 步骤 6: 查询工具列表
echo ""
echo "步骤 6: 查询虚拟项目工具列表..."
TOOLS_RESPONSE=$(curl -s "${ZKINFO_URL}/api/virtual-projects/${ENDPOINT_NAME}/tools" 2>/dev/null || echo "{}")
TOOLS_COUNT=$(echo "$TOOLS_RESPONSE" | jq -r '.tools | length' 2>/dev/null || echo "0")

if [ "$TOOLS_COUNT" != "0" ] && [ "$TOOLS_COUNT" != "null" ]; then
    echo "✅ 找到 ${TOOLS_COUNT} 个工具"
    echo "$TOOLS_RESPONSE" | jq -r '.tools[] | "  - \(.name)"' | head -10
else
    echo "⚠️ 未找到工具"
fi

# 步骤 7: 测试 MCP 连接
echo ""
echo "步骤 7: 测试 MCP 连接..."
SESSION_ID=$(uuidgen 2>/dev/null || echo "test-$(date +%s)")

# 建立 SSE 连接（后台运行）
curl -N -H "Accept: text/event-stream" \
  --max-time 2 \
  "${ZKINFO_URL}/sse/virtual-${ENDPOINT_NAME}" \
  -o /tmp/sse-${SESSION_ID}.txt 2>/dev/null &
SSE_PID=$!
sleep 1
kill $SSE_PID 2>/dev/null || true

# 发送 initialize 请求
echo ""
echo "发送 initialize 请求..."
INIT_RESPONSE=$(curl -s -X POST \
  "${ZKINFO_URL}/mcp/virtual-${ENDPOINT_NAME}/message?sessionId=${SESSION_ID}" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": {
        "name": "demo-client",
        "version": "1.0.0"
      }
    },
    "jsonrpc": "2.0",
    "id": 0
  }' 2>/dev/null || echo "{}")

if echo "$INIT_RESPONSE" | jq -e '.result' > /dev/null 2>&1; then
    echo "✅ initialize 成功"
    echo "$INIT_RESPONSE" | jq '.result | {protocolVersion, serverInfo}'
else
    echo "❌ initialize 失败"
    echo "$INIT_RESPONSE" | jq '.'
fi

# 发送 tools/list 请求
echo ""
echo "发送 tools/list 请求..."
TOOLS_LIST_RESPONSE=$(curl -s -X POST \
  "${ZKINFO_URL}/mcp/virtual-${ENDPOINT_NAME}/message?sessionId=${SESSION_ID}" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "tools/list",
    "params": {},
    "jsonrpc": "2.0",
    "id": 1
  }' 2>/dev/null || echo "{}")

TOOLS_LIST_COUNT=$(echo "$TOOLS_LIST_RESPONSE" | jq -r '.result.tools | length' 2>/dev/null || echo "0")
if [ "$TOOLS_LIST_COUNT" != "0" ] && [ "$TOOLS_LIST_COUNT" != "null" ]; then
    echo "✅ tools/list 成功，找到 ${TOOLS_LIST_COUNT} 个工具"
else
    echo "⚠️ tools/list 返回空列表或失败"
    echo "$TOOLS_LIST_RESPONSE" | jq '.'
fi

# 清理
rm -f /tmp/sse-${SESSION_ID}.txt

echo ""
echo "=========================================="
echo "演示完成！"
echo "=========================================="


