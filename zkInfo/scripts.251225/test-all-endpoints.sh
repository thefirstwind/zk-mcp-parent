#!/bin/bash

# 完整测试所有 endpoint 的脚本

set -e

echo "=========================================="
echo "完整测试所有 Endpoint"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PASSED=0
FAILED=0

# 测试函数
test_tools_list() {
    local endpoint="$1"
    local name="$2"
    
    echo "测试: $name"
    echo "Endpoint: $endpoint"
    
    response=$(curl -s "http://mcp-bridge.test/mcp-bridge/mcp/router/tools/$endpoint" 2>&1)
    
    if echo "$response" | jq -e '.tools | length' >/dev/null 2>&1; then
        tools_count=$(echo "$response" | jq -r '.tools | length' 2>/dev/null || echo "0")
        if [ "$tools_count" != "0" ] && [ "$tools_count" != "null" ]; then
            echo -e "${GREEN}✅ 通过：找到 $tools_count 个 tools${NC}"
            ((PASSED++))
            return 0
        else
            echo -e "${RED}❌ 失败：tools 为空${NC}"
            echo "$response" | head -5
            ((FAILED++))
            return 1
        fi
    else
        echo -e "${RED}❌ 失败：响应格式错误${NC}"
        echo "$response" | head -5
        ((FAILED++))
        return 1
    fi
}

test_tools_call() {
    local endpoint="$1"
    local name="$2"
    
    echo "测试: $name"
    echo "Endpoint: $endpoint"
    
    response=$(curl -s --location "http://mcp-bridge.test/mcp-bridge/mcp/router/route/$endpoint" \
        --header 'Content-Type: application/json' \
        --data '{"id": "1005", "method": "tools/call", "params": {"name": "com.zkinfo.demo.service.UserService.getAllUsers", "arguments": {}}}' 2>&1)
    
    if echo "$response" | jq -e '.result' >/dev/null 2>&1; then
        echo -e "${GREEN}✅ 通过：tools/call 成功${NC}"
        ((PASSED++))
        return 0
    elif echo "$response" | grep -q "No healthy services found"; then
        echo -e "${YELLOW}⚠️ 服务未找到（可能需要等待缓存过期）${NC}"
        ((FAILED++))
        return 1
    elif echo "$response" | grep -q "Endpoint not found"; then
        echo -e "${RED}❌ 失败：endpoint 解析失败${NC}"
        echo "$response" | jq '.error' 2>/dev/null || echo "$response" | head -5
        ((FAILED++))
        return 1
    else
        echo -e "${RED}❌ 失败：tools/call 失败${NC}"
        echo "$response" | head -10
        ((FAILED++))
        return 1
    fi
}

echo "1. 测试 tools/list"
echo "----------------------------------------"

test_tools_list "data-analysis" "GET /mcp/router/tools/data-analysis"
echo ""

test_tools_list "mcp-data-analysis" "GET /mcp/router/tools/mcp-data-analysis"
echo ""

test_tools_list "zk-mcp-com-zkinfo-demo-service-userservice-1.0.0" "GET /mcp/router/tools/zk-mcp-com-zkinfo-demo-service-userservice-1.0.0"
echo ""

echo "2. 测试 tools/call"
echo "----------------------------------------"

test_tools_call "data-analysis" "POST /mcp/router/route/data-analysis"
echo ""

test_tools_call "mcp-data-analysis" "POST /mcp/router/route/mcp-data-analysis"
echo ""

test_tools_call "zk-mcp-com-zkinfo-demo-service-userservice-1.0.0" "POST /mcp/router/route/zk-mcp-com-zkinfo-demo-service-userservice-1.0.0"
echo ""

echo "=========================================="
echo "测试结果"
echo "=========================================="
echo -e "${GREEN}通过: $PASSED${NC}"
echo -e "${RED}失败: $FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✅ 所有测试通过！${NC}"
    exit 0
else
    echo -e "${RED}❌ 部分测试失败，请检查日志${NC}"
    exit 1
fi

