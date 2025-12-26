#!/bin/bash

# 验证 mcp-router-v3 的两个关键接口
# 1. GET /mcp/router/tools/{serviceName} - 获取工具列表
# 2. POST /mcp/router/route/{serviceName} - 调用工具

set -e

ROUTER_URL="${ROUTER_URL:-http://mcp-bridge.test/mcp-bridge}"
SERVICE_NAME="${SERVICE_NAME:-mcp-data-analysis}"

echo "=========================================="
echo "验证 mcp-router-v3 接口"
echo "=========================================="
echo "Router URL: $ROUTER_URL"
echo "Service Name: $SERVICE_NAME"
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试结果
PASSED=0
FAILED=0

# 测试函数
test_endpoint() {
    local name=$1
    local method=$2
    local url=$3
    local data=$4
    
    echo "----------------------------------------"
    echo "测试: $name"
    echo "URL: $url"
    echo "Method: $method"
    if [ -n "$data" ]; then
        echo "Data: $data"
    fi
    echo ""
    
    if [ "$method" = "GET" ]; then
        response=$(curl -s -w "\n%{http_code}" "$url" 2>&1)
    else
        response=$(curl -s -w "\n%{http_code}" -X POST "$url" \
            -H "Content-Type: application/json" \
            -d "$data" 2>&1)
    fi
    
    http_code=$(echo "$response" | tail -n 1)
    body=$(echo "$response" | sed '$d')
    
    echo "HTTP Status: $http_code"
    echo "Response Body:"
    echo "$body" | jq . 2>/dev/null || echo "$body"
    echo ""
    
    if [ "$http_code" = "200" ] || [ "$http_code" = "202" ]; then
        # 检查响应是否包含错误
        if echo "$body" | grep -q '"error"'; then
            error_msg=$(echo "$body" | jq -r '.error.message // .error' 2>/dev/null || echo "Unknown error")
            echo -e "${RED}❌ 失败: 响应包含错误: $error_msg${NC}"
            FAILED=$((FAILED + 1))
            return 1
        else
            echo -e "${GREEN}✅ 成功${NC}"
            PASSED=$((PASSED + 1))
            return 0
        fi
    else
        echo -e "${RED}❌ 失败: HTTP $http_code${NC}"
        FAILED=$((FAILED + 1))
        return 1
    fi
}

# 测试 1: GET /mcp/router/tools/{serviceName}
echo "=========================================="
echo "测试 1: 获取工具列表"
echo "=========================================="
test_endpoint "获取工具列表" \
    "GET" \
    "$ROUTER_URL/mcp/router/tools/$SERVICE_NAME"

echo ""

# 测试 2: POST /mcp/router/route/{serviceName} - tools/call
echo "=========================================="
echo "测试 2: 调用工具 (tools/call)"
echo "=========================================="
CALL_DATA='{
    "id": "1005",
    "method": "tools/call",
    "params": {
        "name": "com.zkinfo.demo.service.UserService.getAllUsers",
        "arguments": {}
    }
}'

test_endpoint "调用工具 (getAllUsers)" \
    "POST" \
    "$ROUTER_URL/mcp/router/route/$SERVICE_NAME" \
    "$CALL_DATA"

echo ""

# 测试 3: POST /mcp/router/route/{serviceName} - tools/call with parameters
echo "=========================================="
echo "测试 3: 调用工具 (带参数)"
echo "=========================================="
CALL_DATA_WITH_PARAMS='{
    "id": "1006",
    "method": "tools/call",
    "params": {
        "name": "com.zkinfo.demo.service.UserService.getUserById",
        "arguments": {
            "id": 1
        }
    }
}'

test_endpoint "调用工具 (getUserById)" \
    "POST" \
    "$ROUTER_URL/mcp/router/route/$SERVICE_NAME" \
    "$CALL_DATA_WITH_PARAMS"

echo ""

# 测试 4: POST /mcp/router/route/{serviceName} - tools/list
echo "=========================================="
echo "测试 4: 通过 route 接口获取工具列表"
echo "=========================================="
LIST_DATA='{
    "id": "1007",
    "method": "tools/list",
    "params": {}
}'

test_endpoint "通过 route 获取工具列表" \
    "POST" \
    "$ROUTER_URL/mcp/router/route/$SERVICE_NAME" \
    "$LIST_DATA"

echo ""

# 总结
echo "=========================================="
echo "测试总结"
echo "=========================================="
echo -e "${GREEN}通过: $PASSED${NC}"
echo -e "${RED}失败: $FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✅ 所有测试通过！${NC}"
    exit 0
else
    echo -e "${RED}❌ 有测试失败，请检查日志${NC}"
    exit 1
fi

