#!/bin/bash

# 检查 mcp-data-analysis 服务在 Nacos 中的注册状态

set -e

NACOS_URL="${NACOS_URL:-http://mcp-bridge.test:8848}"
SERVICE_NAME="${SERVICE_NAME:-mcp-data-analysis}"
NAMESPACE="${NAMESPACE:-public}"

echo "=========================================="
echo "检查服务注册状态"
echo "=========================================="
echo "Nacos URL: $NACOS_URL"
echo "Service Name: $SERVICE_NAME"
echo "Namespace: $NAMESPACE"
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查的 groups
GROUPS=("mcp-server" "mcp-endpoints" "DEFAULT_GROUP")

for group in "${GROUPS[@]}"; do
    echo "----------------------------------------"
    echo "检查 group: $group"
    echo "----------------------------------------"
    
    url="${NACOS_URL}/nacos/v3/client/ns/instance/list?namespaceId=${NAMESPACE}&groupName=${group}&serviceName=${SERVICE_NAME}&healthyOnly=true"
    
    response=$(curl -s "$url" 2>&1)
    
    echo "Response:"
    echo "$response" | jq . 2>/dev/null || echo "$response"
    echo ""
    
    count=$(echo "$response" | jq -r '.data | length' 2>/dev/null || echo "0")
    
    if [ "$count" != "0" ] && [ "$count" != "null" ]; then
        echo -e "${GREEN}✅ 找到 $count 个实例在 group: $group${NC}"
        echo ""
        echo "实例详情:"
        echo "$response" | jq '.data[] | {ip, port, healthy, enabled, metadata}' 2>/dev/null || echo "$response"
    else
        echo -e "${RED}❌ 未找到实例在 group: $group${NC}"
    fi
    echo ""
done

echo "=========================================="
echo "检查完成"
echo "=========================================="

