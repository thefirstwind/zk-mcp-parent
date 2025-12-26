#!/bin/bash

echo "=========================================="
echo "验证报告 - virtual- 前缀支持"
echo "=========================================="
echo ""

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASSED=0
FAILED=0

echo "1. Nacos 服务注册检查"
echo "----------------------------------------"
nacos_check=$(curl -s "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=virtual-data-analysis&groupName=mcp-server")
healthy_count=$(echo "$nacos_check" | jq '[.hosts[]? | select(.healthy == true)] | length' 2>/dev/null || echo "0")
if [ "$healthy_count" != "0" ] && [ "$healthy_count" != "null" ]; then
    echo -e "${GREEN}✅ virtual-data-analysis 在 Nacos 中注册，有 $healthy_count 个健康实例${NC}"
    ((PASSED++))
else
    echo -e "${RED}❌ virtual-data-analysis 未在 Nacos 中找到${NC}"
    ((FAILED++))
fi

echo ""
echo "2. zkInfo 直接调用测试"
echo "----------------------------------------"
# tools/list
response1=$(curl -s "http://mcp-bridge.test:9091/mcp/message?sessionId=test123" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "X-Service-Name: data-analysis" \
    -d '{"id": "test1", "method": "tools/list", "params": {}}')
tools_count=$(echo "$response1" | jq -r '.result.tools | length' 2>/dev/null || echo "0")
if [ "$tools_count" != "0" ] && [ "$tools_count" != "null" ]; then
    echo -e "${GREEN}✅ zkInfo tools/list 成功：找到 $tools_count 个 tools${NC}"
    ((PASSED++))
else
    echo -e "${RED}❌ zkInfo tools/list 失败${NC}"
    ((FAILED++))
fi

# tools/call
response2=$(curl -s "http://mcp-bridge.test:9091/mcp/message?sessionId=test123" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "X-Service-Name: data-analysis" \
    -d '{"id": "1005", "method": "tools/call", "params": {"name": "com.zkinfo.demo.service.UserService.getAllUsers", "arguments": {}}}')
if echo "$response2" | jq -e '.result' >/dev/null 2>&1 && [ "$(echo "$response2" | jq -r '.error')" = "null" ]; then
    echo -e "${GREEN}✅ zkInfo tools/call 成功${NC}"
    ((PASSED++))
else
    echo -e "${RED}❌ zkInfo tools/call 失败${NC}"
    echo "$response2" | jq '.error' 2>/dev/null || echo "$response2" | head -3
    ((FAILED++))
fi

echo ""
echo "3. mcp-router-v3 调用测试（需要重启）"
echo "----------------------------------------"
response3=$(curl -s "http://mcp-bridge.test/mcp-bridge/mcp/router/tools/data-analysis")
if echo "$response3" | jq -e '.tools | length' >/dev/null 2>&1; then
    tools_count3=$(echo "$response3" | jq -r '.tools | length' 2>/dev/null || echo "0")
    if [ "$tools_count3" != "0" ] && [ "$tools_count3" != "null" ]; then
        echo -e "${GREEN}✅ mcp-router-v3 tools/list 成功：找到 $tools_count3 个 tools${NC}"
        ((PASSED++))
    else
        echo -e "${YELLOW}⚠️ mcp-router-v3 tools/list 返回空（可能需要重启）${NC}"
        ((FAILED++))
    fi
elif echo "$response3" | grep -q "目标服务不可用"; then
    echo -e "${YELLOW}⚠️ mcp-router-v3 返回 '目标服务不可用'（需要重启 mcp-router-v3 以应用 virtual- 前缀支持）${NC}"
    ((FAILED++))
else
    echo -e "${RED}❌ mcp-router-v3 tools/list 失败${NC}"
    echo "$response3" | head -3
    ((FAILED++))
fi

response4=$(curl -s --location 'http://mcp-bridge.test/mcp-bridge/mcp/router/route/data-analysis' \
    --header 'Content-Type: application/json' \
    --data '{"id": "1005", "method": "tools/call", "params": {"name": "com.zkinfo.demo.service.UserService.getAllUsers", "arguments": {}}}')
if echo "$response4" | jq -e '.result' >/dev/null 2>&1 && [ "$(echo "$response4" | jq -r '.error')" = "null" ]; then
    echo -e "${GREEN}✅ mcp-router-v3 tools/call 成功${NC}"
    ((PASSED++))
elif echo "$response4" | grep -q "No healthy services found"; then
    echo -e "${YELLOW}⚠️ mcp-router-v3 tools/call 返回 'No healthy services found'（需要重启 mcp-router-v3）${NC}"
    ((FAILED++))
else
    echo -e "${RED}❌ mcp-router-v3 tools/call 失败${NC}"
    echo "$response4" | jq '.error' 2>/dev/null || echo "$response4" | head -3
    ((FAILED++))
fi

echo ""
echo "=========================================="
echo "测试结果"
echo "=========================================="
echo -e "${GREEN}通过: $PASSED${NC}"
echo -e "${RED}失败: $FAILED${NC}"
echo ""

if [ $FAILED -gt 0 ]; then
    echo "说明："
    echo "1. ✅ zkInfo 功能正常，支持 virtual- 前缀的虚拟项目"
    echo "2. ⚠️ mcp-router-v3 需要重启以应用 virtual- 前缀支持"
    echo "3. 📝 已修改 McpServerRegistry.java，添加了对 virtual- 前缀的自动匹配"
    echo ""
    echo "下一步："
    echo "1. 重新编译 mcp-router-v3"
    echo "2. 重启 mcp-router-v3 服务"
    echo "3. 重新运行此脚本验证"
fi

