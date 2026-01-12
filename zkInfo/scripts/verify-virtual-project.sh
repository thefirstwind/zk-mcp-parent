#!/bin/bash

ENDPOINT_NAME=${1:-test-virtual-endpoint001}

echo "=========================================="
echo "验证虚拟项目: ${ENDPOINT_NAME}"
echo "=========================================="

ZKINFO_URL=${ZKINFO_URL:-http://192.168.0.101:9091}

# 1. 查询虚拟项目
echo "1. 查询虚拟项目..."
RESPONSE=$(curl -s "${ZKINFO_URL}/api/virtual-projects?endpointName=${ENDPOINT_NAME}")
if echo "$RESPONSE" | jq -e '.data' > /dev/null 2>&1; then
    echo "$RESPONSE" | jq '.data | {id, name, code, endpoint: .endpoint.endpointName}'
else
    echo "  ❌ 未找到虚拟项目: ${ENDPOINT_NAME}"
fi

# 2. 查询虚拟项目工具
echo ""
echo "2. 查询虚拟项目工具..."
TOOLS_RESPONSE=$(curl -s "${ZKINFO_URL}/api/virtual-projects/${ENDPOINT_NAME}/tools")
TOOLS_COUNT=$(echo "$TOOLS_RESPONSE" | jq -r '.tools | length' 2>/dev/null || echo "0")

if [ "$TOOLS_COUNT" != "0" ] && [ "$TOOLS_COUNT" != "null" ]; then
    echo "  ✅ 找到 ${TOOLS_COUNT} 个工具"
    echo "$TOOLS_RESPONSE" | jq -r '.tools[] | "  - \(.name): \(.description)"' | head -5
else
    echo "  ❌ 未找到工具"
fi

# 3. 测试 SSE 连接
echo ""
echo "3. 测试 SSE 连接..."
SESSION_ID=$(uuidgen 2>/dev/null || echo "test-$(date +%s)")
TIMEOUT=3

curl -N -H "Accept: text/event-stream" \
  --max-time ${TIMEOUT} \
  "${ZKINFO_URL}/sse/virtual-${ENDPOINT_NAME}" \
  -o /tmp/sse-response-${SESSION_ID}.txt 2>/dev/null &

SSE_PID=$!
sleep 2
kill $SSE_PID 2>/dev/null
wait $SSE_PID 2>/dev/null

if [ -f /tmp/sse-response-${SESSION_ID}.txt ]; then
    if grep -q "event: endpoint" /tmp/sse-response-${SESSION_ID}.txt; then
        echo "  ✅ SSE 连接成功"
        echo "  响应内容:"
        cat /tmp/sse-response-${SESSION_ID}.txt | head -3
    else
        echo "  ❌ SSE 连接失败或响应格式不正确"
    fi
    rm /tmp/sse-response-${SESSION_ID}.txt
else
    echo "  ❌ SSE 连接失败"
fi

echo ""
echo "=========================================="


