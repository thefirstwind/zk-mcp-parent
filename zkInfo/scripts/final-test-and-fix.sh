#!/bin/bash

# 最终测试和修复脚本

set -e

echo "=========================================="
echo "最终测试和修复"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PASSED=0
FAILED=0

echo "步骤 1: 检查虚拟项目并重新注册"
echo "----------------------------------------"

vp_response=$(curl -s "http://mcp-bridge.test:9091/api/virtual-projects" 2>&1)
vp_id=$(echo "$vp_response" | jq -r '.[] | select(.endpoint.endpointName == "data-analysis") | .project.id' 2>/dev/null | head -1)

if [ -z "$vp_id" ] || [ "$vp_id" = "null" ]; then
    echo -e "${RED}❌ 未找到 data-analysis 虚拟项目${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 找到虚拟项目 (ID: $vp_id)${NC}"

# 重新注册
reregister_response=$(curl -s -X POST "http://mcp-bridge.test:9091/api/virtual-projects/$vp_id/reregister" 2>&1)
if echo "$reregister_response" | grep -q "重新注册成功\|reregister"; then
    echo -e "${GREEN}✅ 重新注册成功${NC}"
else
    echo -e "${RED}❌ 重新注册失败${NC}"
    exit 1
fi

sleep 3

echo ""
echo "步骤 2: 测试 zkInfo 直接调用"
echo "----------------------------------------"

# 测试 1: 直接调用 zkInfo tools/list（带 X-Service-Name header）
response1=$(curl -s "http://mcp-bridge.test:9091/mcp/message" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "X-Service-Name: data-analysis" \
    -d '{"id": "test1", "method": "tools/list", "params": {}}' 2>&1)

tools_count=$(echo "$response1" | jq -r '.result.tools | length' 2>/dev/null || echo "0")

if [ "$tools_count" != "0" ] && [ "$tools_count" != "null" ] && [ "$tools_count" != "" ]; then
    echo -e "${GREEN}✅ zkInfo 直接调用成功：找到 $tools_count 个 tools${NC}"
    ((PASSED++))
else
    echo -e "${RED}❌ zkInfo 直接调用失败：tools 为空${NC}"
    echo "$response1" | head -10
    ((FAILED++))
fi

echo ""
echo "步骤 3: 测试 mcp-router-v3 调用"
echo "----------------------------------------"

# 等待缓存过期
echo "等待 mcp-router-v3 缓存过期（35秒）..."
sleep 35

# 测试 2: 通过 mcp-router-v3 调用
response2=$(curl -s "http://mcp-bridge.test/mcp-bridge/mcp/router/tools/data-analysis" 2>&1)

if echo "$response2" | jq -e '.tools | length' >/dev/null 2>&1; then
    tools_count2=$(echo "$response2" | jq -r '.tools | length' 2>/dev/null || echo "0")
    if [ "$tools_count2" != "0" ] && [ "$tools_count2" != "null" ]; then
        echo -e "${GREEN}✅ mcp-router-v3 调用成功：找到 $tools_count2 个 tools${NC}"
        ((PASSED++))
    else
        echo -e "${RED}❌ mcp-router-v3 调用失败：tools 为空${NC}"
        echo "$response2" | head -10
        ((FAILED++))
    fi
else
    echo -e "${RED}❌ mcp-router-v3 调用失败：响应格式错误${NC}"
    echo "$response2" | head -10
    ((FAILED++))
fi

echo ""
echo "步骤 4: 测试 tools/call"
echo "----------------------------------------"

# 测试 3: tools/call
response3=$(curl -s --location 'http://mcp-bridge.test/mcp-bridge/mcp/router/route/data-analysis' \
    --header 'Content-Type: application/json' \
    --data '{"id": "1005", "method": "tools/call", "params": {"name": "com.zkinfo.demo.service.UserService.getAllUsers", "arguments": {}}}' 2>&1)

if echo "$response3" | jq -e '.result' >/dev/null 2>&1; then
    echo -e "${GREEN}✅ tools/call 成功${NC}"
    ((PASSED++))
elif echo "$response3" | grep -q "No healthy services found"; then
    echo -e "${YELLOW}⚠️ 服务未找到（可能需要重启 mcp-router-v3）${NC}"
    ((FAILED++))
else
    echo -e "${RED}❌ tools/call 失败${NC}"
    echo "$response3" | jq '.error' 2>/dev/null || echo "$response3" | head -10
    ((FAILED++))
fi

echo ""
echo "步骤 5: 测试 zk-mcp-* 服务"
echo "----------------------------------------"

# 测试 4: zk-mcp-* 服务
response4=$(curl -s --location 'http://mcp-bridge.test/mcp-bridge/mcp/router/route/zk-mcp-com-zkinfo-demo-service-userservice-1.0.0' \
    --header 'Content-Type: application/json' \
    --data '{"id": "1005", "method": "tools/call", "params": {"name": "com.zkinfo.demo.service.UserService.getAllUsers", "arguments": {}}}' 2>&1)

if echo "$response4" | jq -e '.result' >/dev/null 2>&1; then
    echo -e "${GREEN}✅ zk-mcp-* 服务调用成功${NC}"
    ((PASSED++))
elif echo "$response4" | grep -q "Endpoint not found"; then
    echo -e "${RED}❌ zk-mcp-* 服务 endpoint 解析失败${NC}"
    echo "$response4" | jq '.error' 2>/dev/null || echo "$response4" | head -10
    ((FAILED++))
else
    echo -e "${YELLOW}⚠️ zk-mcp-* 服务调用失败（其他错误）${NC}"
    echo "$response4" | head -10
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

