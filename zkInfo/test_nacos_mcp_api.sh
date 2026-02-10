#!/bin/bash

# 测试直接调用 Nacos MCP API

NACOS_ADDR="127.0.0.1:8848"
USERNAME="nacos"
PASSWORD="nacos"
NAMESPACE="public"

echo "=== Nacos MCP API 测试 ==="
echo

# 1. 获取 accessToken
echo "1. 获取 accessToken"
LOGIN_RESPONSE=$(curl -s -X POST "http://${NACOS_ADDR}/nacos/v1/auth/login" \
  -d "username=${USERNAME}&password=${PASSWORD}")

echo "Login Response: $LOGIN_RESPONSE"
ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"accessToken":"[^"]*"' | sed 's/"accessToken":"//;s/"//')
echo "Access Token: ${ACCESS_TOKEN:0:30}..."
echo

# 2. 尝试调用 MCP Server 创建 API
echo "2. 测试创建 MCP Server (需要找到正确的 API 端点)"

# 尝试可能的端点
ENDPOINTS=(
  "/nacos/v1/ai/mcp/server"
  "/nacos/v1/mcp/server"
  "/nacos/v2/ai/mcp/server"
  "/v1/ai/mcp/server"
)

for endpoint in "${ENDPOINTS[@]}"; do
  echo "Trying endpoint: $endpoint"
  RESPONSE=$(curl -s -X POST "http://${NACOS_ADDR}${endpoint}" \
    -H "Content-Type: application/json" \
    -d "{
      \"namespace\": \"${NAMESPACE}\",
      \"mcpName\": \"test-mcp-server\",
      \"accessToken\": \"${ACCESS_TOKEN}\"
    }" 2>&1)
  
  echo "Response: $RESPONSE"
  echo "---"
done

echo
echo "=== 测试完成 ==="
