#!/bin/bash

# 诊断 tools 为空的问题

set -e

SERVICE_NAME="${1:-zk-mcp-com-zkinfo-demo-service-orderservice-1.0.0}"
ROUTER_URL="${ROUTER_URL:-http://mcp-bridge.test/mcp-bridge}"

echo "=========================================="
echo "诊断 tools 为空问题"
echo "=========================================="
echo "Service Name: $SERVICE_NAME"
echo "Router URL: $ROUTER_URL"
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "1. 检查服务是否在 Nacos 中注册"
echo "----------------------------------------"
response=$(curl -s "${ROUTER_URL}/mcp/router/tools/${SERVICE_NAME}" 2>&1)
echo "Response:"
echo "$response" | jq . 2>/dev/null || echo "$response"
echo ""

# 检查是否是错误消息
if echo "$response" | grep -q "目标服务不可用"; then
    echo -e "${RED}❌ 服务未找到或不可用${NC}"
    echo ""
    echo "2. 检查服务在 Nacos 中的注册状态"
    echo "----------------------------------------"
    
    # 检查各个 group
    for group in "mcp-server" "mcp-endpoints" "DEFAULT_GROUP"; do
        echo "检查 group: $group"
        nacos_url="http://mcp-bridge.test:8848/nacos/v3/client/ns/instance/list?namespaceId=public&groupName=${group}&serviceName=${SERVICE_NAME}&healthyOnly=true"
        nacos_response=$(curl -s "$nacos_url" 2>&1)
        count=$(echo "$nacos_response" | jq -r '.data | length' 2>/dev/null || echo "0")
        if [ "$count" != "0" ] && [ "$count" != "null" ]; then
            echo -e "${GREEN}✅ 找到 $count 个实例在 group: $group${NC}"
            echo "$nacos_response" | jq '.data[] | {ip, port, healthy, enabled}' 2>/dev/null || echo "$nacos_response"
        else
            echo -e "${RED}❌ 未找到实例在 group: $group${NC}"
        fi
        echo ""
    done
else
    # 检查 tools 是否为空
    tools_count=$(echo "$response" | jq -r '.tools | length' 2>/dev/null || echo "0")
    if [ "$tools_count" = "0" ] || [ "$tools_count" = "null" ]; then
        echo -e "${RED}❌ Tools 为空 (count: $tools_count)${NC}"
        echo ""
        echo "2. 检查 Nacos 中的 tools 配置"
        echo "----------------------------------------"
        
        # 尝试从服务实例获取 serviceId
        for group in "mcp-server" "mcp-endpoints" "DEFAULT_GROUP"; do
            nacos_url="http://mcp-bridge.test:8848/nacos/v3/client/ns/instance/list?namespaceId=public&groupName=${group}&serviceName=${SERVICE_NAME}&healthyOnly=true"
            nacos_response=$(curl -s "$nacos_url" 2>&1)
            instances=$(echo "$nacos_response" | jq -r '.data | length' 2>/dev/null || echo "0")
            
            if [ "$instances" != "0" ] && [ "$instances" != "null" ]; then
                echo "找到实例在 group: $group"
                # 获取第一个实例的 metadata
                server_id=$(echo "$nacos_response" | jq -r '.data[0].metadata.serverId' 2>/dev/null || echo "")
                version=$(echo "$nacos_response" | jq -r '.data[0].metadata.version' 2>/dev/null || echo "1.0.0")
                
                if [ -n "$server_id" ] && [ "$server_id" != "null" ]; then
                    echo "Service ID: $server_id"
                    echo "Version: $version"
                    
                    # 检查 tools 配置
                    tools_data_id="${server_id}-${version}-mcp-tools.json"
                    echo "检查 tools 配置: $tools_data_id (group: mcp-tools)"
                    
                    tools_config_url="http://mcp-bridge.test:8848/nacos/v3/client/cs/config?namespaceId=public&groupName=mcp-tools&dataId=${tools_data_id}"
                    tools_config_response=$(curl -s "$tools_config_url" 2>&1)
                    
                    tools_config_data=$(echo "$tools_config_response" | jq -r '.data' 2>/dev/null || echo "")
                    if [ -n "$tools_config_data" ] && [ "$tools_config_data" != "null" ]; then
                        echo -e "${GREEN}✅ 找到 tools 配置${NC}"
                        tools_count_in_config=$(echo "$tools_config_data" | jq -r '.tools | length' 2>/dev/null || echo "0")
                        echo "Tools count in config: $tools_count_in_config"
                        if [ "$tools_count_in_config" = "0" ] || [ "$tools_count_in_config" = "null" ]; then
                            echo -e "${RED}❌ Tools 配置中 tools 数组为空${NC}"
                        else
                            echo "Tools in config:"
                            echo "$tools_config_data" | jq '.tools[] | {name, description}' 2>/dev/null || echo "$tools_config_data"
                        fi
                    else
                        echo -e "${RED}❌ 未找到 tools 配置${NC}"
                    fi
                else
                    echo -e "${YELLOW}⚠️ 无法获取 serviceId${NC}"
                fi
                break
            fi
        done
    else
        echo -e "${GREEN}✅ Tools 不为空 (count: $tools_count)${NC}"
        echo "Tools:"
        echo "$response" | jq '.tools[] | {name, description}' 2>/dev/null || echo "$response"
    fi
fi

echo ""
echo "=========================================="
echo "诊断完成"
echo "=========================================="

