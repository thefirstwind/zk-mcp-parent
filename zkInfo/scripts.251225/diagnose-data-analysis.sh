#!/bin/bash

# 诊断 data-analysis 虚拟项目的服务注册和发现问题

ENDPOINT_NAME="data-analysis"
SERVICE_NAME="mcp-data-analysis"
ZKINFO_URL="http://localhost:9091"
ROUTER_URL="http://localhost:8052"
NACOS_URL="http://localhost:8848/nacos"

echo "=========================================="
echo "诊断 data-analysis 虚拟项目服务注册问题"
echo "=========================================="
echo ""

# 1. 检查虚拟项目是否存在
echo "1. 检查虚拟项目是否存在..."
VIRTUAL_PROJECTS=$(curl -s "${ZKINFO_URL}/api/virtual-projects" | jq '.')
if [ "$?" -ne 0 ] || [ -z "$VIRTUAL_PROJECTS" ]; then
    echo "❌ 无法连接到 zkInfo API: ${ZKINFO_URL}"
    exit 1
fi

DATA_ANALYSIS_PROJECT=$(echo "$VIRTUAL_PROJECTS" | jq ".[] | select(.endpoint.endpointName == \"${ENDPOINT_NAME}\")")
if [ -z "$DATA_ANALYSIS_PROJECT" ] || [ "$DATA_ANALYSIS_PROJECT" == "null" ]; then
    echo "❌ 虚拟项目 '${ENDPOINT_NAME}' 不存在"
    echo ""
    echo "请先创建虚拟项目："
    echo "curl -X POST ${ZKINFO_URL}/api/virtual-projects \\"
    echo "  -H \"Content-Type: application/json\" \\"
    echo "  -d '{\"name\": \"数据分析场景\", \"endpointName\": \"${ENDPOINT_NAME}\", ...}'"
    exit 1
fi

PROJECT_ID=$(echo "$DATA_ANALYSIS_PROJECT" | jq -r '.project.id')
ENDPOINT=$(echo "$DATA_ANALYSIS_PROJECT" | jq -r '.endpoint.endpointName')
MCP_SERVICE_NAME=$(echo "$DATA_ANALYSIS_PROJECT" | jq -r '.endpoint.mcpServiceName')

echo "✅ 虚拟项目存在:"
echo "   - 项目ID: ${PROJECT_ID}"
echo "   - Endpoint: ${ENDPOINT}"
echo "   - MCP服务名: ${MCP_SERVICE_NAME}"
echo ""

# 2. 检查虚拟项目的服务列表
echo "2. 检查虚拟项目的服务列表..."
PROJECT_SERVICES=$(curl -s "${ZKINFO_URL}/api/projects/${PROJECT_ID}/services" | jq '.')
SERVICE_COUNT=$(echo "$PROJECT_SERVICES" | jq 'length')
echo "   - 服务数量: ${SERVICE_COUNT}"
if [ "$SERVICE_COUNT" -eq 0 ]; then
    echo "⚠️  警告: 虚拟项目没有关联的服务"
fi
echo ""

# 3. 检查已注册的服务
echo "3. 检查已注册到 Nacos 的服务..."
REGISTERED_SERVICES=$(curl -s "${ZKINFO_URL}/api/registered-services" | jq '.registeredServices')
if echo "$REGISTERED_SERVICES" | jq -e ".[] | select(. == \"${MCP_SERVICE_NAME}\")" > /dev/null; then
    echo "✅ 服务 '${MCP_SERVICE_NAME}' 已注册到 Nacos"
else
    echo "❌ 服务 '${MCP_SERVICE_NAME}' 未在已注册服务列表中"
    echo "   已注册的服务:"
    echo "$REGISTERED_SERVICES" | jq -r '.[]' | sed 's/^/     - /'
fi
echo ""

# 4. 检查 mcp-router-v3 是否能发现服务
echo "4. 检查 mcp-router-v3 是否能发现服务..."
ROUTER_SERVICES=$(curl -s "${ROUTER_URL}/mcp/router/services?serviceGroup=mcp-server" | jq '.')
if [ "$?" -ne 0 ] || [ -z "$ROUTER_SERVICES" ]; then
    echo "⚠️  无法连接到 mcp-router-v3: ${ROUTER_URL}"
else
    SERVER_COUNT=$(echo "$ROUTER_SERVICES" | jq '.count // 0')
    echo "   - 发现的服务数量: ${SERVER_COUNT}"
    
    if echo "$ROUTER_SERVICES" | jq -e ".servers[]? | select(.name == \"${MCP_SERVICE_NAME}\")" > /dev/null; then
        echo "✅ 服务 '${MCP_SERVICE_NAME}' 已被 mcp-router-v3 发现"
        SERVER_INFO=$(echo "$ROUTER_SERVICES" | jq ".servers[] | select(.name == \"${MCP_SERVICE_NAME}\")")
        echo "   服务信息:"
        echo "$SERVER_INFO" | jq '.' | sed 's/^/     /'
    else
        echo "❌ 服务 '${MCP_SERVICE_NAME}' 未被 mcp-router-v3 发现"
        echo "   已发现的服务:"
        echo "$ROUTER_SERVICES" | jq -r '.servers[]?.name // empty' | sed 's/^/     - /'
    fi
fi
echo ""

# 5. 测试工具列表接口
echo "5. 测试工具列表接口..."
echo "   5.1 使用 endpointName (${ENDPOINT_NAME}):"
TOOLS_RESPONSE1=$(curl -s "${ROUTER_URL}/mcp/router/tools/${ENDPOINT_NAME}")
if echo "$TOOLS_RESPONSE1" | jq -e '.tools' > /dev/null 2>&1; then
    TOOL_COUNT=$(echo "$TOOLS_RESPONSE1" | jq '.tools | length')
    echo "   ✅ 成功获取工具列表，工具数量: ${TOOL_COUNT}"
elif echo "$TOOLS_RESPONSE1" | grep -q "目标服务不可用"; then
    echo "   ❌ 返回: 目标服务不可用"
else
    echo "   ⚠️  响应: ${TOOLS_RESPONSE1}"
fi

echo ""
echo "   5.2 使用 MCP 服务名 (${MCP_SERVICE_NAME}):"
TOOLS_RESPONSE2=$(curl -s "${ROUTER_URL}/mcp/router/tools/${MCP_SERVICE_NAME}")
if echo "$TOOLS_RESPONSE2" | jq -e '.tools' > /dev/null 2>&1; then
    TOOL_COUNT=$(echo "$TOOLS_RESPONSE2" | jq '.tools | length')
    echo "   ✅ 成功获取工具列表，工具数量: ${TOOL_COUNT}"
elif echo "$TOOLS_RESPONSE2" | grep -q "目标服务不可用"; then
    echo "   ❌ 返回: 目标服务不可用"
else
    echo "   ⚠️  响应: ${TOOLS_RESPONSE2}"
fi
echo ""

# 6. 检查 Nacos 中的服务实例（如果 Nacos API 可用）
echo "6. 检查 Nacos 中的服务实例..."
if command -v curl > /dev/null; then
    # 尝试通过 Nacos Open API 查询
    NACOS_NAMESPACE="public"
    NACOS_GROUP="mcp-server"
    
    # 注意：这需要 Nacos 的 Open API，可能需要认证
    echo "   提示: 可以通过 Nacos 控制台检查服务实例:"
    echo "   - 服务名: ${MCP_SERVICE_NAME}"
    echo "   - 服务组: ${NACOS_GROUP}"
    echo "   - 命名空间: ${NACOS_NAMESPACE}"
fi
echo ""

# 7. 建议
echo "=========================================="
echo "诊断总结和建议"
echo "=========================================="
echo ""

if echo "$TOOLS_RESPONSE1" | grep -q "目标服务不可用" && echo "$TOOLS_RESPONSE2" | grep -q "目标服务不可用"; then
    echo "问题: 服务未正确注册或未被发现"
    echo ""
    echo "建议操作:"
    echo "1. 检查虚拟项目是否有可用的 Provider:"
    echo "   curl -s ${ZKINFO_URL}/api/providers | jq '.[] | select(.online == true)'"
    echo ""
    echo "2. 重新注册虚拟项目到 Nacos:"
    echo "   # 先获取虚拟项目ID"
    echo "   PROJECT_ID=\$(curl -s ${ZKINFO_URL}/api/virtual-projects | jq -r '.[] | select(.endpoint.endpointName == \"${ENDPOINT_NAME}\") | .project.id')"
    echo "   # 然后调用重新注册接口（如果存在）"
    echo ""
    echo "3. 检查 zkInfo 日志，查找注册相关的错误:"
    echo "   tail -f logs/zkinfo.log | grep -i 'register\|nacos'"
    echo ""
    echo "4. 检查 mcp-router-v3 日志，查找服务发现相关的错误:"
    echo "   tail -f logs/mcp-router-v3.log | grep -i '${MCP_SERVICE_NAME}\|data-analysis'"
else
    echo "✅ 服务发现正常"
fi

echo ""
echo "=========================================="

