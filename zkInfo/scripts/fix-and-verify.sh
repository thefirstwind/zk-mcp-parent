#!/bin/bash

# 修复并验证 endpoint 问题的脚本

set -e

echo "=========================================="
echo "修复并验证 Endpoint 问题"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "步骤 1: 检查虚拟项目"
echo "----------------------------------------"

# 获取虚拟项目列表
vp_response=$(curl -s "http://mcp-bridge.test:9091/api/virtual-projects" 2>&1)
vp_id=$(echo "$vp_response" | jq -r '.[] | select(.endpoint.endpointName == "data-analysis") | .project.id' 2>/dev/null | head -1)

if [ -z "$vp_id" ] || [ "$vp_id" = "null" ]; then
    echo -e "${RED}❌ 未找到 data-analysis 虚拟项目${NC}"
    echo "请先创建虚拟项目"
    exit 1
fi

echo -e "${GREEN}✅ 找到虚拟项目 (ID: $vp_id)${NC}"
echo ""

echo "步骤 2: 重新注册服务（使用 virtual-data-analysis 格式）"
echo "----------------------------------------"

# 重新注册
reregister_response=$(curl -s -X POST "http://mcp-bridge.test:9091/api/virtual-projects/$vp_id/reregister" 2>&1)
echo "$reregister_response" | jq '.' 2>/dev/null || echo "$reregister_response"

if echo "$reregister_response" | grep -q "重新注册成功\|reregister"; then
    echo -e "${GREEN}✅ 重新注册成功${NC}"
else
    echo -e "${RED}❌ 重新注册失败${NC}"
    exit 1
fi

echo ""
echo "步骤 3: 等待服务注册完成（3秒）"
echo "----------------------------------------"
sleep 3

echo ""
echo "步骤 4: 验证服务注册状态"
echo "----------------------------------------"

# 检查 data-analysis（新名称，应该存在）
for group in "mcp-server" "mcp-endpoints"; do
    echo "检查 group: $group"
    
    # 检查 data-analysis
    response_data=$(curl -s "http://mcp-bridge.test:8848/nacos/v3/client/ns/instance/list?namespaceId=public&groupName=${group}&serviceName=data-analysis&healthyOnly=false")
    count_data=$(echo "$response_data" | jq -r '.data | length' 2>/dev/null || echo "0")
    
    if [ "$count_data" != "0" ] && [ "$count_data" != "null" ]; then
        echo -e "${GREEN}✅ 找到 $count_data 个 data-analysis 实例在 group: $group${NC}"
        echo "$response_data" | jq '.data[] | {ip, port, healthy, enabled, metadata}' 2>/dev/null || echo "$response_data"
    else
        echo -e "${RED}❌ 未找到 data-analysis 实例在 group: $group${NC}"
    fi
    echo ""
done

echo "步骤 5: 测试接口"
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
    echo -e "${YELLOW}⚠️ 服务未找到（可能需要等待缓存过期）${NC}"
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
else
    echo -e "${YELLOW}⚠️ 其他错误${NC}"
    echo "$response3" | head -10
fi
echo ""

echo "=========================================="
echo "修复和验证完成"
echo "=========================================="
echo ""
echo "注意：如果 mcp-router-v3 仍然找不到服务，可能需要："
echo "1. 等待缓存过期（30秒）"
echo "2. 重启 mcp-router-v3"
echo "3. 检查 mcp-router-v3 日志"

