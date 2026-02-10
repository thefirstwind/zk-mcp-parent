#!/bin/bash

# 测试 Nacos MCP API 端点发现

NACOS_ADDR="127.0.0.1:8848"
USERNAME="nacos"
PASSWORD="nacos"
NAMESPACE="public"

echo "=== 查找 Nacos MCP API 端点 ==="
echo

# 1. 获取 accessToken
LOGIN_RESPONSE=$(curl -s -X POST "http://${NACOS_ADDR}/nacos/v1/auth/login" \
  -d "username=${USERNAME}&password=${PASSWORD}")
ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"accessToken":"[^"]*"' | sed 's/"accessToken":"//;s/"//')
echo "✅ Access Token obtained: ${ACCESS_TOKEN:0:30}..."
echo

# 2. 尝试可能的 MCP 端点
ENDPOINTS=(
  "/nacos/v1/ns/ai/mcp/server"
  "/nacos/v2/console/ai/mcp/server"
  "/nacos/v1/console/ai/mcp/server"
  "/nacos/v1/ns/mcp/server"
  "/nacos/v2/ns/ai/mcp/server"
)

echo "2. 测试各种可能的 API 端点..."
echo

for endpoint in "${ENDPOINTS[@]}"; do
  echo "Testing: POST ${endpoint}"
  RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST "http://${NACOS_ADDR}${endpoint}?accessToken=${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{
      \"namespace\": \"${NAMESPACE}\",
      \"mcpName\": \"test-mcp-from-curl\",
      \"serverSpec\": {
        \"name\": \"test-mcp-from-curl\",
        \"description\": \"Test MCP Server\",
        \"protocol\": \"sse\",
        \"frontProtocol\": \"sse\"
      }
    }")
  
  HTTP_STATUS=$(echo "$RESPONSE" | grep "HTTP_STATUS" | cut -d':' -f2)
  BODY=$(echo "$RESPONSE" | sed '/HTTP_STATUS/d')
  
  echo "  Status: $HTTP_STATUS"
  echo "  Body: ${BODY:0:200}"
  echo
done

# 3. 尝试 GET 请求查看 MCP 列表
echo "3. 尝试获取 MCP 服务列表..."
LIST_ENDPOINTS=(
  "/nacos/v1/ns/ai/mcp/servers"
  "/nacos/v2/console/ai/mcp/servers"
  "/nacos/v1/console/ai/mcp/list"
)

for endpoint in "${LIST_ENDPOINTS[@]}"; do
  echo "Testing: GET ${endpoint}"
  RESPONSE=$(curl -s "http://${NACOS_ADDR}${endpoint}?namespaceId=${NAMESPACE}&accessToken=${ACCESS_TOKEN}")
  echo "  Response: ${RESPONSE:0:200}"
  echo
done

echo "=== 测试完成 ==="
