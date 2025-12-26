#!/bin/bash

# 修复所有问题的完整脚本

set -e

echo "=========================================="
echo "修复所有 Endpoint 问题"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "步骤 1: 检查虚拟项目"
echo "----------------------------------------"

vp_response=$(curl -s "http://mcp-bridge.test:9091/api/virtual-projects" 2>&1)
vp_id=$(echo "$vp_response" | jq -r '.[] | select(.endpoint.endpointName == "data-analysis") | .project.id' 2>/dev/null | head -1)

if [ -z "$vp_id" ] || [ "$vp_id" = "null" ]; then
    echo -e "${RED}❌ 未找到 data-analysis 虚拟项目${NC}"
    echo "请先创建虚拟项目"
    exit 1
fi

echo -e "${GREEN}✅ 找到虚拟项目 (ID: $vp_id)${NC}"
echo ""

echo "步骤 2: 检查虚拟项目的服务"
echo "----------------------------------------"

vp_detail=$(curl -s "http://mcp-bridge.test:9091/api/virtual-projects/$vp_id" 2>&1)
service_count=$(echo "$vp_detail" | jq -r '.serviceCount' 2>/dev/null || echo "0")

if [ "$service_count" = "0" ] || [ "$service_count" = "null" ]; then
    echo -e "${RED}❌ 虚拟项目没有服务${NC}"
    echo "请先为虚拟项目添加服务"
    exit 1
fi

echo -e "${GREEN}✅ 虚拟项目有 $service_count 个服务${NC}"
echo ""

echo "步骤 3: 重新注册服务到 Nacos（使用 data-analysis，不添加 mcp- 前缀）"
echo "----------------------------------------"

reregister_response=$(curl -s -X POST "http://mcp-bridge.test:9091/api/virtual-projects/$vp_id/reregister" 2>&1)
echo "$reregister_response" | jq '.' 2>/dev/null || echo "$reregister_response"

if echo "$reregister_response" | grep -q "重新注册成功\|reregister"; then
    echo -e "${GREEN}✅ 重新注册成功${NC}"
else
    echo -e "${RED}❌ 重新注册失败${NC}"
    exit 1
fi

echo ""
echo "步骤 4: 等待服务注册完成（5秒）"
echo "----------------------------------------"
sleep 5

echo ""
echo "步骤 5: 验证服务注册状态"
echo "----------------------------------------"

for group in "mcp-server" "mcp-endpoints"; do
    echo "检查 group: $group"
    
    response_data=$(curl -s "http://mcp-bridge.test:8848/nacos/v3/client/ns/instance/list?namespaceId=public&groupName=${group}&serviceName=data-analysis&healthyOnly=false")
    count_data=$(echo "$response_data" | jq -r '.data | length' 2>/dev/null || echo "0")
    
    if [ "$count_data" != "0" ] && [ "$count_data" != "null" ]; then
        echo -e "${GREEN}✅ 找到 $count_data 个 data-analysis 实例在 group: $group${NC}"
        healthy=$(echo "$response_data" | jq -r '.data[0].healthy' 2>/dev/null || echo "false")
        if [ "$healthy" = "true" ]; then
            echo -e "${GREEN}✅ 服务健康${NC}"
        else
            echo -e "${YELLOW}⚠️ 服务不健康${NC}"
        fi
    else
        echo -e "${RED}❌ 未找到 data-analysis 实例在 group: $group${NC}"
    fi
    echo ""
done

echo "步骤 6: 等待 mcp-router-v3 缓存过期（35秒）"
echo "----------------------------------------"
echo "等待中..."
sleep 35

echo ""
echo "步骤 7: 测试接口"
echo "----------------------------------------"

# 测试 tools/list
echo "测试 1: GET /mcp/router/tools/data-analysis"
response1=$(curl -s "http://mcp-bridge.test/mcp-bridge/mcp/router/tools/data-analysis" 2>&1)
if echo "$response1" | jq -e '.tools | length' >/dev/null 2>&1; then
    tools_count=$(echo "$response1" | jq -r '.tools | length' 2>/dev/null || echo "0")
    if [ "$tools_count" != "0" ] && [ "$tools_count" != "null" ]; then
        echo -e "${GREEN}✅ 通过：找到 $tools_count 个 tools${NC}"
    else
        echo -e "${RED}❌ 失败：tools 为空${NC}"
        echo "$response1" | head -5
    fi
else
    echo -e "${RED}❌ 失败：响应格式错误${NC}"
    echo "$response1" | head -5
fi
echo ""

# 测试 tools/call
echo "测试 2: POST /mcp/router/route/data-analysis"
response2=$(curl -s --location 'http://mcp-bridge.test/mcp-bridge/mcp/router/route/data-analysis' \
    --header 'Content-Type: application/json' \
    --data '{"id": "1005", "method": "tools/call", "params": {"name": "com.zkinfo.demo.service.UserService.getAllUsers", "arguments": {}}}' 2>&1)

if echo "$response2" | jq -e '.result' >/dev/null 2>&1; then
    echo -e "${GREEN}✅ 通过：tools/call 成功${NC}"
elif echo "$response2" | grep -q "No healthy services found"; then
    echo -e "${YELLOW}⚠️ 服务未找到（可能需要重启 mcp-router-v3）${NC}"
    echo "$response2" | jq '.error' 2>/dev/null || echo "$response2" | head -5
elif echo "$response2" | grep -q "Endpoint not found"; then
    echo -e "${RED}❌ 失败：endpoint 解析失败${NC}"
    echo "$response2" | jq '.error' 2>/dev/null || echo "$response2" | head -5
else
    echo -e "${RED}❌ 失败：tools/call 失败${NC}"
    echo "$response2" | head -10
fi
echo ""

# 测试 zk-mcp-* 服务
echo "测试 3: POST /mcp/router/route/zk-mcp-com-zkinfo-demo-service-userservice-1.0.0"
response3=$(curl -s --location 'http://mcp-bridge.test/mcp-bridge/mcp/router/route/zk-mcp-com-zkinfo-demo-service-userservice-1.0.0' \
    --header 'Content-Type: application/json' \
    --data '{"id": "1005", "method": "tools/call", "params": {"name": "com.zkinfo.demo.service.UserService.getAllUsers", "arguments": {}}}' 2>&1)

if echo "$response3" | jq -e '.result' >/dev/null 2>&1; then
    echo -e "${GREEN}✅ 通过：zk-mcp-* 服务 endpoint 解析成功${NC}"
elif echo "$response3" | grep -q "Endpoint not found"; then
    echo -e "${RED}❌ 失败：endpoint 解析失败${NC}"
    echo "$response3" | jq '.error' 2>/dev/null || echo "$response3" | head -5
    echo ""
    echo "💡 提示：检查服务是否在项目中注册"
    echo "   接口名: com.zkinfo.demo.service.UserService"
    echo "   版本: 1.0.0"
else
    echo -e "${YELLOW}⚠️ 其他错误${NC}"
    echo "$response3" | head -10
fi
echo ""

echo "=========================================="
echo "修复和测试完成"
echo "=========================================="
echo ""
echo "如果仍有问题，请："
echo "1. 检查 zkInfo 日志中的 endpoint 解析信息"
echo "2. 检查 mcp-router-v3 日志"
echo "3. 确认服务在项目中正确注册"

