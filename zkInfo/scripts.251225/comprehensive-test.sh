#!/bin/bash

# 全面测试脚本 - 确保100%通过

set -e

echo "=========================================="
echo "全面测试 - 确保100%通过"
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
vp_id=$(echo "$vp_response" | jq -r '.[] | select(.endpoint != null and .endpoint.endpointName != null) | .project.id' 2>/dev/null | head -1)

# 如果还是找不到，尝试查找所有虚拟项目
if [ -z "$vp_id" ] || [ "$vp_id" = "null" ]; then
    vp_id=$(echo "$vp_response" | jq -r '.[] | .project.id' 2>/dev/null | head -1)
fi

if [ -z "$vp_id" ] || [ "$vp_id" = "null" ]; then
    echo -e "${RED}❌ 未找到虚拟项目${NC}"
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
echo "步骤 2: 检查 Nacos 服务注册状态"
echo "----------------------------------------"

# 检查 virtual-data-analysis 在 mcp-server 组（虚拟项目使用 virtual- 前缀）
nacos_check1=$(curl -s "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=virtual-data-analysis&groupName=mcp-server&healthyOnly=true" 2>&1)
healthy_count=$(echo "$nacos_check1" | jq '[.hosts[]? | select(.healthy == true)] | length' 2>/dev/null || echo "0")

if [ "$healthy_count" != "0" ] && [ "$healthy_count" != "null" ]; then
    echo -e "${GREEN}✅ virtual-data-analysis 在 mcp-server 组中有 $healthy_count 个健康实例${NC}"
    ((PASSED++))
else
    echo -e "${RED}❌ virtual-data-analysis 在 mcp-server 组中未找到健康实例${NC}"
    ((FAILED++))
fi

echo ""
echo "步骤 3: 测试 zkInfo 直接调用"
echo "----------------------------------------"

# 测试 1: 直接调用 zkInfo tools/list（带 X-Service-Name header）
response1=$(curl -s "http://mcp-bridge.test:9091/mcp/message?sessionId=test123" \
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
echo "步骤 4: 测试 mcp-router-v3 调用（需要重启 mcp-router-v3）"
echo "----------------------------------------"

echo -e "${YELLOW}⚠️ 注意：如果 mcp-router-v3 刚刚重启，请等待 5 秒让缓存刷新${NC}"
sleep 5

# 测试 2: 通过 mcp-router-v3 调用（支持 virtual- 前缀和直接 endpoint 名称）
response2=$(curl -s "http://mcp-bridge.test/mcp-bridge/mcp/router/tools/data-analysis" 2>&1)
# 如果失败，尝试使用 virtual-data-analysis
if ! echo "$response2" | jq -e '.tools | length' >/dev/null 2>&1; then
    response2=$(curl -s "http://mcp-bridge.test/mcp-bridge/mcp/router/tools/virtual-data-analysis" 2>&1)
fi

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
elif echo "$response2" | grep -q "目标服务不可用"; then
    echo -e "${YELLOW}⚠️ mcp-router-v3 返回 '目标服务不可用'，可能需要：${NC}"
    echo -e "${YELLOW}   1. 重启 mcp-router-v3 服务${NC}"
    echo -e "${YELLOW}   2. 等待缓存过期（30秒）${NC}"
    echo -e "${YELLOW}   3. 检查 mcp-router-v3 日志${NC}"
    ((FAILED++))
else
    echo -e "${RED}❌ mcp-router-v3 调用失败：响应格式错误${NC}"
    echo "$response2" | head -10
    ((FAILED++))
fi

echo ""
echo "步骤 5: 测试 tools/call"
echo "----------------------------------------"

# 测试 3: tools/call 到 data-analysis
response3=$(curl -s --location 'http://mcp-bridge.test/mcp-bridge/mcp/router/route/data-analysis' \
    --header 'Content-Type: application/json' \
    --data '{"id": "1005", "method": "tools/call", "params": {"name": "com.zkinfo.demo.service.UserService.getAllUsers", "arguments": {}}}' 2>&1)

# 检查 result 是否存在且不为 null（支持多种格式：result.content[].text 或 result[]）
if echo "$response3" | jq -e '.result != null and .error == null' >/dev/null 2>&1; then
    # 检查是否有内容（可能是数组或对象）
    result_type=$(echo "$response3" | jq -r 'if .result | type == "array" then "array" elif .result.content then "content" else "other" end' 2>/dev/null)
    if [ "$result_type" = "array" ] || [ "$result_type" = "content" ] || [ "$result_type" = "other" ]; then
        echo -e "${GREEN}✅ tools/call 到 data-analysis 成功${NC}"
        ((PASSED++))
    else
        echo -e "${RED}❌ tools/call 失败：result 格式不正确${NC}"
        echo "$response3" | jq '.result' 2>/dev/null || echo "$response3" | head -5
        ((FAILED++))
    fi
elif echo "$response3" | grep -q "No healthy services found"; then
    echo -e "${YELLOW}⚠️ 服务未找到（可能需要重启 mcp-router-v3）${NC}"
    ((FAILED++))
else
    echo -e "${RED}❌ tools/call 失败${NC}"
    echo "$response3" | jq '.error' 2>/dev/null || echo "$response3" | head -10
    ((FAILED++))
fi

echo ""
echo "步骤 6: 测试 zk-mcp-* 服务"
echo "----------------------------------------"

# 测试 4: zk-mcp-* 服务
response4=$(curl -s --location 'http://mcp-bridge.test/mcp-bridge/mcp/router/route/zk-mcp-com-zkinfo-demo-service-userservice-1.0.0' \
    --header 'Content-Type: application/json' \
    --data '{"id": "1005", "method": "tools/call", "params": {"name": "com.zkinfo.demo.service.UserService.getAllUsers", "arguments": {}}}' 2>&1)

if echo "$response4" | jq -e '.result' >/dev/null 2>&1 && [ "$(echo "$response4" | jq -r '.error')" = "null" ]; then
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
    echo ""
    echo "修复建议："
    echo "1. 如果 mcp-router-v3 调用失败，请重启 mcp-router-v3 服务"
    echo "2. 等待缓存过期（30秒）"
    echo "3. 检查 mcp-router-v3 和 zkInfo 的日志"
    exit 1
fi

