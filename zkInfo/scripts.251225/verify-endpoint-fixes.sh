#!/bin/bash

# 验证 endpoint 修复的脚本

set -e

echo "=========================================="
echo "验证 Endpoint 修复"
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
test_case() {
    local name="$1"
    local url="$2"
    local expected="$3"
    
    echo "测试: $name"
    echo "URL: $url"
    
    response=$(curl -s "$url" 2>&1)
    
    if echo "$response" | grep -q "$expected"; then
        echo -e "${GREEN}✅ 通过${NC}"
        ((PASSED++))
    else
        echo -e "${RED}❌ 失败${NC}"
        echo "Response:"
        echo "$response" | head -20
        ((FAILED++))
    fi
    echo ""
}

echo "1. 检查 data-analysis 服务在 Nacos 中的注册状态"
echo "----------------------------------------"

# 检查服务是否注册（应该注册为 data-analysis，不是 mcp-data-analysis）
for group in "mcp-server" "mcp-endpoints"; do
    echo "检查 group: $group"
    
    # 检查 data-analysis（正确）
    response_data=$(curl -s "http://mcp-bridge.test:8848/nacos/v3/client/ns/instance/list?namespaceId=public&groupName=${group}&serviceName=data-analysis&healthyOnly=false")
    count_data=$(echo "$response_data" | jq -r '.data | length' 2>/dev/null || echo "0")
    
    # 检查 mcp-data-analysis（旧名称，应该不存在或将被迁移）
    response_mcp=$(curl -s "http://mcp-bridge.test:8848/nacos/v3/client/ns/instance/list?namespaceId=public&groupName=${group}&serviceName=mcp-data-analysis&healthyOnly=false")
    count_mcp=$(echo "$response_mcp" | jq -r '.data | length' 2>/dev/null || echo "0")
    
    if [ "$count_data" != "0" ] && [ "$count_data" != "null" ]; then
        echo -e "${GREEN}✅ 找到 $count_data 个 data-analysis 实例在 group: $group${NC}"
        echo "$response_data" | jq '.data[] | {ip, port, healthy, enabled, metadata}' 2>/dev/null || echo "$response_data"
    elif [ "$count_mcp" != "0" ] && [ "$count_mcp" != "null" ]; then
        echo -e "${YELLOW}⚠️ 找到 $count_mcp 个 mcp-data-analysis 实例（旧名称），需要重新注册为 data-analysis${NC}"
    else
        echo -e "${RED}❌ 未找到 data-analysis 或 mcp-data-analysis 实例在 group: $group${NC}"
    fi
    echo ""
done

echo "2. 测试 tools/list 接口"
echo "----------------------------------------"

# 测试 data-analysis（新名称，应该工作）
echo "测试: GET /mcp/router/tools/data-analysis"
response1=$(curl -s "http://mcp-bridge.test/mcp-bridge/mcp/router/tools/data-analysis" 2>&1)
if echo "$response1" | jq -e '.tools | length' >/dev/null 2>&1; then
    tools_count=$(echo "$response1" | jq -r '.tools | length' 2>/dev/null || echo "0")
    if [ "$tools_count" != "0" ] && [ "$tools_count" != "null" ]; then
        echo -e "${GREEN}✅ 通过：找到 $tools_count 个 tools${NC}"
        ((PASSED++))
    else
        echo -e "${RED}❌ 失败：tools 为空${NC}"
        echo "$response1" | head -10
        ((FAILED++))
    fi
else
    echo -e "${RED}❌ 失败：响应格式错误${NC}"
    echo "$response1" | head -10
    ((FAILED++))
fi
echo ""

# 测试 mcp-data-analysis（旧名称，应该自动转换为 data-analysis）
echo "测试: GET /mcp/router/tools/mcp-data-analysis"
response2=$(curl -s "http://mcp-bridge.test/mcp-bridge/mcp/router/tools/mcp-data-analysis" 2>&1)
if echo "$response2" | jq -e '.tools | length' >/dev/null 2>&1; then
    tools_count=$(echo "$response2" | jq -r '.tools | length' 2>/dev/null || echo "0")
    if [ "$tools_count" != "0" ] && [ "$tools_count" != "null" ]; then
        echo -e "${GREEN}✅ 通过：找到 $tools_count 个 tools（自动转换 mcp-data-analysis -> data-analysis）${NC}"
        ((PASSED++))
    else
        echo -e "${YELLOW}⚠️ tools 为空（可能需要重新注册服务）${NC}"
        echo "$response2" | head -10
        ((FAILED++))
    fi
else
    echo -e "${YELLOW}⚠️ 响应格式错误或服务未找到${NC}"
    echo "$response2" | head -10
    ((FAILED++))
fi
echo ""

echo "3. 测试 tools/call 接口"
echo "----------------------------------------"

# 测试 data-analysis（新名称）
echo "测试: POST /mcp/router/route/data-analysis"
response3=$(curl -s --location 'http://mcp-bridge.test/mcp-bridge/mcp/router/route/data-analysis' \
    --header 'Content-Type: application/json' \
    --data '{"id": "1005", "method": "tools/call", "params": {"name": "com.zkinfo.demo.service.UserService.getAllUsers", "arguments": {}}}' 2>&1)

if echo "$response3" | jq -e '.result' >/dev/null 2>&1; then
    echo -e "${GREEN}✅ 通过：tools/call 成功${NC}"
    echo "$response3" | jq '.result' 2>/dev/null || echo "$response3"
    ((PASSED++))
elif echo "$response3" | grep -q "No healthy services found"; then
    echo -e "${YELLOW}⚠️ 服务未找到（可能需要重新注册）${NC}"
    echo "$response3" | head -5
    ((FAILED++))
else
    echo -e "${RED}❌ 失败：tools/call 失败${NC}"
    echo "$response3" | head -10
    ((FAILED++))
fi
echo ""

# 测试 mcp-data-analysis（旧名称，应该自动转换）
echo "测试: POST /mcp/router/route/mcp-data-analysis"
response4=$(curl -s --location 'http://mcp-bridge.test/mcp-bridge/mcp/router/route/mcp-data-analysis' \
    --header 'Content-Type: application/json' \
    --data '{"id": "1005", "method": "tools/call", "params": {"name": "com.zkinfo.demo.service.UserService.getAllUsers", "arguments": {}}}' 2>&1)

if echo "$response4" | jq -e '.result' >/dev/null 2>&1; then
    echo -e "${GREEN}✅ 通过：tools/call 成功（自动转换 mcp-data-analysis -> data-analysis）${NC}"
    echo "$response4" | jq '.result' 2>/dev/null || echo "$response4"
    ((PASSED++))
elif echo "$response4" | grep -q "No healthy services found"; then
    echo -e "${YELLOW}⚠️ 服务未找到（可能需要重新注册）${NC}"
    echo "$response4" | head -5
    ((FAILED++))
else
    echo -e "${RED}❌ 失败：tools/call 失败${NC}"
    echo "$response4" | head -10
    ((FAILED++))
fi
echo ""

echo "4. 测试 zk-mcp-com-zkinfo-demo-service-userservice-1.0.0 endpoint 解析"
echo "----------------------------------------"

echo "测试: POST /mcp/router/route/zk-mcp-com-zkinfo-demo-service-userservice-1.0.0"
response5=$(curl -s --location 'http://mcp-bridge.test/mcp-bridge/mcp/router/route/zk-mcp-com-zkinfo-demo-service-userservice-1.0.0' \
    --header 'Content-Type: application/json' \
    --data '{"id": "1005", "method": "tools/call", "params": {"name": "com.zkinfo.demo.service.UserService.getAllUsers", "arguments": {}}}' 2>&1)

if echo "$response5" | jq -e '.result' >/dev/null 2>&1; then
    echo -e "${GREEN}✅ 通过：zk-mcp-* 服务 endpoint 解析成功${NC}"
    echo "$response5" | jq '.result' 2>/dev/null || echo "$response5"
    ((PASSED++))
elif echo "$response5" | grep -q "Endpoint not found"; then
    echo -e "${RED}❌ 失败：endpoint 解析失败${NC}"
    echo "$response5" | head -10
    ((FAILED++))
else
    echo -e "${YELLOW}⚠️ 其他错误${NC}"
    echo "$response5" | head -10
    ((FAILED++))
fi
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

