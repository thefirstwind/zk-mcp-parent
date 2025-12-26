#!/bin/bash

# 诊断 endpoint 问题的脚本

set -e

echo "=========================================="
echo "诊断 Endpoint 问题"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "1. 检查 mcp-data-analysis 服务在 Nacos 中的注册状态"
echo "----------------------------------------"

for group in "mcp-server" "mcp-endpoints"; do
    echo "检查 group: $group"
    response=$(curl -s "http://mcp-bridge.test:8848/nacos/v3/client/ns/instance/list?namespaceId=public&groupName=${group}&serviceName=mcp-data-analysis&healthyOnly=true")
    count=$(echo "$response" | jq -r '.data | length' 2>/dev/null || echo "0")
    
    if [ "$count" != "0" ] && [ "$count" != "null" ]; then
        echo -e "${GREEN}✅ 找到 $count 个实例在 group: $group${NC}"
        echo "$response" | jq '.data[] | {ip, port, healthy, enabled, metadata}' 2>/dev/null || echo "$response"
    else
        echo -e "${RED}❌ 未找到实例在 group: $group${NC}"
    fi
    echo ""
done

echo "2. 检查 zk-mcp-com-zkinfo-demo-service-userservice-1.0.0 服务在 Nacos 中的注册状态"
echo "----------------------------------------"

SERVICE_NAME="zk-mcp-com-zkinfo-demo-service-userservice-1.0.0"

for group in "mcp-server" "mcp-endpoints"; do
    echo "检查 group: $group"
    response=$(curl -s "http://mcp-bridge.test:8848/nacos/v3/client/ns/instance/list?namespaceId=public&groupName=${group}&serviceName=${SERVICE_NAME}&healthyOnly=true")
    count=$(echo "$response" | jq -r '.data | length' 2>/dev/null || echo "0")
    
    if [ "$count" != "0" ] && [ "$count" != "null" ]; then
        echo -e "${GREEN}✅ 找到 $count 个实例在 group: $group${NC}"
        echo "$response" | jq '.data[] | {ip, port, healthy, enabled, metadata}' 2>/dev/null || echo "$response"
    else
        echo -e "${RED}❌ 未找到实例在 group: $group${NC}"
    fi
    echo ""
done

echo "3. 检查虚拟项目是否存在"
echo "----------------------------------------"

response=$(curl -s "http://mcp-bridge.test:9091/api/virtual-projects" 2>&1)
if echo "$response" | jq -e '. | length' >/dev/null 2>&1; then
    echo -e "${GREEN}✅ 找到虚拟项目列表${NC}"
    echo "$response" | jq '.[] | {id, projectName, endpointName, mcpServiceName}' 2>/dev/null || echo "$response"
    
    # 检查是否有 data-analysis 虚拟项目
    has_data_analysis=$(echo "$response" | jq -r '.[] | select(.endpointName == "data-analysis" or .mcpServiceName == "mcp-data-analysis") | .endpointName' 2>/dev/null || echo "")
    if [ -n "$has_data_analysis" ]; then
        echo -e "${GREEN}✅ 找到 data-analysis 虚拟项目${NC}"
    else
        echo -e "${RED}❌ 未找到 data-analysis 虚拟项目${NC}"
        echo -e "${YELLOW}💡 提示：需要创建虚拟项目并注册到 Nacos${NC}"
    fi
else
    echo -e "${RED}❌ 无法获取虚拟项目列表${NC}"
    echo "Response: $response"
fi
echo ""

echo "4. 检查项目中的服务注册"
echo "----------------------------------------"

# 检查是否有包含 UserService 的项目
response=$(curl -s "http://mcp-bridge.test:9091/api/projects" 2>&1)
if echo "$response" | jq -e '. | length' >/dev/null 2>&1; then
    echo "项目列表："
    echo "$response" | jq '.[] | {id, projectCode, projectName, projectType}' 2>/dev/null || echo "$response"
    
    # 尝试解析服务名
    echo ""
    echo "解析服务名: $SERVICE_NAME"
    # 去掉 zk-mcp- 前缀
    without_prefix="${SERVICE_NAME#zk-mcp-}"
    # 提取版本号（最后一个 - 分隔）
    version="${without_prefix##*-}"
    interface_part="${without_prefix%-*}"
    interface_lower="${interface_part//-/.}"
    
    echo "  提取的接口名（小写）: $interface_lower"
    echo "  提取的版本: $version"
    
    # 尝试驼峰命名
    if [[ "$interface_lower" == *".userservice" ]]; then
        interface_camel="${interface_lower%.userservice}.UserService"
        echo "  尝试驼峰命名: $interface_camel"
    fi
else
    echo -e "${RED}❌ 无法获取项目列表${NC}"
    echo "Response: $response"
fi
echo ""

echo "=========================================="
echo "诊断完成"
echo "=========================================="
echo ""
echo "修复建议："
echo "1. 如果 mcp-data-analysis 未注册："
echo "   - 检查虚拟项目是否存在"
echo "   - 检查虚拟项目是否包含服务"
echo "   - 检查虚拟项目是否调用了注册方法"
echo ""
echo "2. 如果 zk-mcp-* 服务 endpoint 解析失败："
echo "   - 检查服务是否在项目中注册"
echo "   - 检查接口名是否匹配（注意大小写）"
echo "   - 检查版本号是否匹配"
echo "   - 查看 zkInfo 日志中的详细解析信息"

