#!/bin/bash

# 诊断 mcp-router-v3 的两个关键接口
# 1. GET /mcp/router/tools/{serviceName} - 获取工具列表
# 2. POST /mcp/router/route/{serviceName} - 调用工具

set -e

ROUTER_URL="${ROUTER_URL:-http://mcp-bridge.test/mcp-bridge}"
SERVICE_NAME="${SERVICE_NAME:-mcp-data-analysis}"

echo "=========================================="
echo "诊断 mcp-router-v3 接口"
echo "=========================================="
echo "Router URL: $ROUTER_URL"
echo "Service Name: $SERVICE_NAME"
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试 1: 检查服务是否在 Nacos 中注册
echo "=========================================="
echo "步骤 1: 检查服务注册状态"
echo "=========================================="
echo "检查服务: $SERVICE_NAME"
echo ""

# 测试 2: 直接调用 zkInfo 的接口（通过 mcp-bridge.test）
echo "=========================================="
echo "步骤 2: 直接调用 zkInfo 接口（通过 mcp-bridge.test）"
echo "=========================================="
echo "测试: POST http://mcp-bridge.test/mcp-bridge/mcp/message?sessionId=test-123"
echo ""

TEST_SESSION_ID="test-$(date +%s)"
TEST_DATA='{
    "jsonrpc": "2.0",
    "id": "test-001",
    "method": "tools/list",
    "params": {}
}'

echo "Request:"
echo "$TEST_DATA" | jq .
echo ""

response=$(curl -s -w "\n%{http_code}" -X POST "http://mcp-bridge.test/mcp-bridge/mcp/message?sessionId=$TEST_SESSION_ID" \
    -H "Content-Type: application/json" \
    -H "X-Service-Name: $SERVICE_NAME" \
    -d "$TEST_DATA" 2>&1)

http_code=$(echo "$response" | tail -n 1)
body=$(echo "$response" | sed '$d')

echo "HTTP Status: $http_code"
echo "Response Body:"
echo "$body" | jq . 2>/dev/null || echo "$body"
echo ""

if [ "$http_code" = "200" ]; then
    echo -e "${GREEN}✅ zkInfo 接口正常${NC}"
else
    echo -e "${RED}❌ zkInfo 接口异常: HTTP $http_code${NC}"
fi

echo ""

# 测试 3: 通过 mcp-router-v3 调用
echo "=========================================="
echo "步骤 3: 通过 mcp-router-v3 调用"
echo "=========================================="
echo "测试: GET $ROUTER_URL/mcp/router/tools/$SERVICE_NAME"
echo ""

response=$(curl -s -w "\n%{http_code}" "$ROUTER_URL/mcp/router/tools/$SERVICE_NAME" 2>&1)
http_code=$(echo "$response" | tail -n 1)
body=$(echo "$response" | sed '$d')

echo "HTTP Status: $http_code"
echo "Response Body:"
echo "$body" | jq . 2>/dev/null || echo "$body"
echo ""

if [ "$http_code" = "200" ]; then
    echo -e "${GREEN}✅ mcp-router-v3 接口正常${NC}"
else
    echo -e "${RED}❌ mcp-router-v3 接口异常: HTTP $http_code${NC}"
fi

echo ""

# 测试 4: POST /mcp/router/route/{serviceName}
echo "=========================================="
echo "步骤 4: POST /mcp/router/route/{serviceName}"
echo "=========================================="
echo "测试: POST $ROUTER_URL/mcp/router/route/$SERVICE_NAME"
echo ""

CALL_DATA='{
    "id": "1005",
    "method": "tools/call",
    "params": {
        "name": "com.zkinfo.demo.service.UserService.getAllUsers",
        "arguments": {}
    }
}'

echo "Request:"
echo "$CALL_DATA" | jq .
echo ""

response=$(curl -s -w "\n%{http_code}" -X POST "$ROUTER_URL/mcp/router/route/$SERVICE_NAME" \
    -H "Content-Type: application/json" \
    -d "$CALL_DATA" 2>&1)

http_code=$(echo "$response" | tail -n 1)
body=$(echo "$response" | sed '$d')

echo "HTTP Status: $http_code"
echo "Response Body:"
echo "$body" | jq . 2>/dev/null || echo "$body"
echo ""

if [ "$http_code" = "200" ] || [ "$http_code" = "202" ]; then
    echo -e "${GREEN}✅ mcp-router-v3 route 接口正常${NC}"
else
    echo -e "${RED}❌ mcp-router-v3 route 接口异常: HTTP $http_code${NC}"
fi

echo ""
echo "=========================================="
echo "诊断完成"
echo "=========================================="

