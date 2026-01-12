#!/bin/bash

echo "=========================================="
echo "验证服务注册状态"
echo "=========================================="

NACOS_SERVER=${NACOS_SERVER_ADDR:-127.0.0.1:8848}
NACOS_USERNAME=${NACOS_USERNAME:-nacos}
NACOS_PASSWORD=${NACOS_PASSWORD:-nacos}

# 1. 检查 zkInfo 节点注册
echo "1. 检查 zkInfo 节点注册..."
curl -s -u ${NACOS_USERNAME}:${NACOS_PASSWORD} \
  "http://${NACOS_SERVER}/nacos/v1/ns/instance/list?serviceName=zkinfo&namespaceId=public" | \
  jq -r '.hosts[]? | "  IP: \(.ip), Port: \(.port), Healthy: \(.healthy), Enabled: \(.enabled)"' || \
  echo "  ❌ 未找到 zkInfo 节点"

# 2. 检查 MCP 服务注册
echo ""
echo "2. 检查 MCP 服务注册..."
SERVICES=$(curl -s -u ${NACOS_USERNAME}:${NACOS_PASSWORD} \
  "http://${NACOS_SERVER}/nacos/v1/ns/service/list?pageNo=1&pageSize=100&namespaceId=public" | \
  jq -r '.doms[]? | select(. | startswith("zk-mcp-") or startswith("virtual-"))')

if [ -n "$SERVICES" ]; then
    echo "$SERVICES" | while read service; do
        echo "  ✅ $service"
    done
else
    echo "  ❌ 未找到 MCP 服务"
fi

# 3. 检查工具配置
echo ""
echo "3. 检查工具配置..."
SERVICE_NAME="zk-mcp-com.pajk.provider2.service.OrderService-1.0.0"
TOOLS_COUNT=$(curl -s -u ${NACOS_USERNAME}:${NACOS_PASSWORD} \
  "http://${NACOS_SERVER}/nacos/v1/cs/configs?dataId=${SERVICE_NAME}-tools.json&group=mcp-server-tools&namespaceId=public" | \
  jq -r '.tools | length' 2>/dev/null || echo "0")

if [ "$TOOLS_COUNT" != "0" ] && [ "$TOOLS_COUNT" != "null" ]; then
    echo "  ✅ 服务 ${SERVICE_NAME} 有 ${TOOLS_COUNT} 个工具"
else
    echo "  ❌ 服务 ${SERVICE_NAME} 未找到工具配置"
fi

echo ""
echo "=========================================="


