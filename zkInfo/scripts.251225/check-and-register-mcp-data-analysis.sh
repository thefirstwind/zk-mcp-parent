#!/bin/bash

# 检查并注册 mcp-data-analysis 服务

set -e

echo "=========================================="
echo "检查并注册 mcp-data-analysis 服务"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "1. 检查虚拟项目是否存在"
echo "----------------------------------------"

response=$(curl -s "http://mcp-bridge.test:9091/api/virtual-projects" 2>&1)
echo "虚拟项目列表:"
echo "$response" | jq '.' 2>/dev/null || echo "$response"

# 检查是否有 data-analysis 虚拟项目
has_data_analysis=$(echo "$response" | jq -r '.[] | select(.endpointName == "data-analysis" or .mcpServiceName == "mcp-data-analysis") | .id' 2>/dev/null | head -1)

if [ -n "$has_data_analysis" ] && [ "$has_data_analysis" != "null" ]; then
    echo -e "${GREEN}✅ 找到 data-analysis 虚拟项目 (ID: $has_data_analysis)${NC}"
    
    echo ""
    echo "2. 检查虚拟项目详情"
    echo "----------------------------------------"
    project_detail=$(curl -s "http://mcp-bridge.test:9091/api/virtual-projects/$has_data_analysis" 2>&1)
    echo "$project_detail" | jq '.' 2>/dev/null || echo "$project_detail"
    
    echo ""
    echo "3. 触发重新注册到 Nacos"
    echo "----------------------------------------"
    echo "调用重新注册 API..."
    reregister_response=$(curl -s -X POST "http://mcp-bridge.test:9091/api/virtual-projects/$has_data_analysis/reregister" 2>&1)
    echo "$reregister_response" | jq '.' 2>/dev/null || echo "$reregister_response"
    
    echo ""
    echo "4. 等待 2 秒后检查注册状态"
    echo "----------------------------------------"
    sleep 2
    
    for group in "mcp-server" "mcp-endpoints"; do
        echo "检查 group: $group"
        nacos_response=$(curl -s "http://mcp-bridge.test:8848/nacos/v3/client/ns/instance/list?namespaceId=public&groupName=${group}&serviceName=mcp-data-analysis&healthyOnly=false")
        count=$(echo "$nacos_response" | jq -r '.data | length' 2>/dev/null || echo "0")
        
        if [ "$count" != "0" ] && [ "$count" != "null" ]; then
            echo -e "${GREEN}✅ 找到 $count 个实例在 group: $group${NC}"
            echo "$nacos_response" | jq '.data[] | {ip, port, healthy, enabled, metadata}' 2>/dev/null || echo "$nacos_response"
        else
            echo -e "${RED}❌ 未找到实例在 group: $group${NC}"
        fi
        echo ""
    done
else
    echo -e "${RED}❌ 未找到 data-analysis 虚拟项目${NC}"
    echo ""
    echo "需要先创建虚拟项目。请使用以下 API 创建："
    echo ""
    echo "curl -X POST 'http://mcp-bridge.test:9091/api/virtual-projects' \\"
    echo "  --header 'Content-Type: application/json' \\"
    echo "  --data '{"
    echo "    \"projectName\": \"data-analysis\","
    echo "    \"endpointName\": \"data-analysis\","
    echo "    \"autoRegister\": true,"
    echo "    \"serviceIds\": [服务ID列表]"
    echo "  }'"
    echo ""
fi

echo "=========================================="
echo "检查完成"
echo "=========================================="
