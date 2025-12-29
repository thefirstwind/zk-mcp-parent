#!/bin/bash
# 模拟 mcp inspector 的连接流程（参考 mcp-router-v3 的实现标准）
# 使用方式: ./test-mcp-inspector-flow.sh <endpoint-name>
# 示例: ./test-mcp-inspector-flow.sh test-virtual-endpoint122704

ENDPOINT_NAME="${1:-test-virtual-endpoint122704}"
BASE_URL="http://192.168.0.101:9091"
SSE_ENDPOINT="${BASE_URL}/sse/${ENDPOINT_NAME}"

echo "=========================================="
echo "测试 MCP Inspector 连接流程"
echo "参考 mcp-router-v3 的实现标准"
echo "=========================================="
echo ""
echo "Endpoint: ${ENDPOINT_NAME}"
echo "SSE URL: ${SSE_ENDPOINT}"
echo ""

# 步骤 1: 建立 SSE 连接并获取 endpoint
echo "步骤 1: 建立 SSE 连接并获取 message endpoint..."
SSE_RESPONSE=$(curl -s -N -m 5 "${SSE_ENDPOINT}" 2>&1 | head -5)

echo "SSE 响应:"
echo "${SSE_RESPONSE}"
echo ""

# 提取 endpoint URL 和 sessionId
ENDPOINT_URL=$(echo "${SSE_RESPONSE}" | grep "^data:" | head -1 | sed 's/^data://' | tr -d ' ')
SESSION_ID=$(echo "${ENDPOINT_URL}" | sed -n 's/.*sessionId=\([^&]*\).*/\1/p')

if [ -z "${ENDPOINT_URL}" ] || [ -z "${SESSION_ID}" ]; then
    echo "❌ 无法提取 endpoint URL 或 sessionId"
    exit 1
fi

echo "✅ Message Endpoint URL: ${ENDPOINT_URL}"
echo "✅ Session ID: ${SESSION_ID}"
echo ""

# 步骤 2: 在后台保持 SSE 连接（重要：必须保持连接才能接收响应）
echo "步骤 2: 保持 SSE 连接（后台运行）..."
curl -s -N "${SSE_ENDPOINT}?sessionId=${SESSION_ID}" > /tmp/sse_response_${SESSION_ID}.log 2>&1 &
SSE_PID=$!
sleep 1
echo "✅ SSE 连接已建立 (PID: ${SSE_PID})"
echo ""

# 步骤 3: 发送 initialize 请求（参考 mcp-router-v3 的标准）
echo "步骤 3: 发送 initialize 请求..."
INIT_REQUEST='{
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
}'

INIT_RESPONSE=$(curl -s -X POST "${ENDPOINT_URL}" \
    -H "Content-Type: application/json" \
    -d "${INIT_REQUEST}")

echo "Initialize HTTP 响应（202 Accepted）:"
echo "${INIT_RESPONSE}" | head -3
echo ""

# 等待一下，让响应通过 SSE 发送
echo "等待 initialize 响应通过 SSE 发送..."
sleep 2

# 检查 SSE 响应中的 initialize 结果
echo "从 SSE 流中读取 initialize 响应:"
if [ -f "/tmp/sse_response_${SESSION_ID}.log" ]; then
    tail -20 "/tmp/sse_response_${SESSION_ID}.log" | grep -A 5 "initialize" || echo "（未找到 initialize 响应，可能还在传输中）"
fi
echo ""

# 步骤 4: 发送 tools/list 请求（参考 mcp-router-v3 的标准）
echo "步骤 4: 发送 tools/list 请求..."
TOOLS_LIST_REQUEST='{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "params": {},
    "id": 2
}'

TOOLS_RESPONSE=$(curl -s -X POST "${ENDPOINT_URL}" \
    -H "Content-Type: application/json" \
    -d "${TOOLS_LIST_REQUEST}")

echo "Tools/list HTTP 响应（202 Accepted）:"
echo "${TOOLS_RESPONSE}" | head -3
echo ""

# 等待一下，让响应通过 SSE 发送
echo "等待 tools/list 响应通过 SSE 发送..."
sleep 2

# 检查 SSE 响应中的 tools/list 结果
echo "从 SSE 流中读取 tools/list 响应:"
if [ -f "/tmp/sse_response_${SESSION_ID}.log" ]; then
    tail -50 "/tmp/sse_response_${SESSION_ID}.log" | grep -A 10 "tools/list" || echo "（未找到 tools/list 响应，可能还在传输中）"
fi
echo ""

# 步骤 5: 发送 resources/list 请求（可选，参考 mcp-router-v3 的标准）
echo "步骤 5: 发送 resources/list 请求（可选）..."
RESOURCES_LIST_REQUEST='{
    "jsonrpc": "2.0",
    "method": "resources/list",
    "params": {},
    "id": 3
}'

RESOURCES_RESPONSE=$(curl -s -X POST "${ENDPOINT_URL}" \
    -H "Content-Type: application/json" \
    -d "${RESOURCES_LIST_REQUEST}")

echo "Resources/list HTTP 响应（202 Accepted）:"
echo "${RESOURCES_RESPONSE}" | head -3
echo ""

# 等待一下，让响应通过 SSE 发送
sleep 2

# 步骤 6: 发送 prompts/list 请求（可选，参考 mcp-router-v3 的标准）
echo "步骤 6: 发送 prompts/list 请求（可选）..."
PROMPTS_LIST_REQUEST='{
    "jsonrpc": "2.0",
    "method": "prompts/list",
    "params": {},
    "id": 4
}'

PROMPTS_RESPONSE=$(curl -s -X POST "${ENDPOINT_URL}" \
    -H "Content-Type: application/json" \
    -d "${PROMPTS_LIST_REQUEST}")

echo "Prompts/list HTTP 响应（202 Accepted）:"
echo "${PROMPTS_RESPONSE}" | head -3
echo ""

# 等待一下，让响应通过 SSE 发送
sleep 2

# 显示完整的 SSE 响应日志
echo "=========================================="
echo "完整的 SSE 响应日志:"
echo "=========================================="
if [ -f "/tmp/sse_response_${SESSION_ID}.log" ]; then
    cat "/tmp/sse_response_${SESSION_ID}.log"
else
    echo "（SSE 响应日志文件不存在）"
fi
echo ""

# 检查 SSE 连接是否还在运行
if kill -0 ${SSE_PID} 2>/dev/null; then
    echo "✅ SSE 连接仍然活跃 (PID: ${SSE_PID})"
else
    echo "❌ SSE 连接已断开"
fi

# 清理
echo ""
echo "清理资源..."
kill ${SSE_PID} 2>/dev/null
rm -f "/tmp/sse_response_${SESSION_ID}.log" 2>/dev/null

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="
echo ""
echo "📋 MCP Inspector 标准流程总结："
echo "1. 建立 SSE 连接: GET ${SSE_ENDPOINT}"
echo "2. 收到 endpoint 事件，获取 message endpoint URL"
echo "3. 保持 SSE 连接（后台运行）"
echo "4. 发送 initialize 请求: POST ${ENDPOINT_URL}"
echo "5. 发送 tools/list 请求: POST ${ENDPOINT_URL}"
echo "6. （可选）发送 resources/list 请求"
echo "7. （可选）发送 prompts/list 请求"
echo ""
echo "所有响应都通过 SSE 流返回，HTTP POST 请求返回 202 Accepted"


