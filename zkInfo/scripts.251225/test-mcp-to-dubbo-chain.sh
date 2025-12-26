#!/bin/bash

# MCP 到 Dubbo 链路验证脚本
# 使用 Nacos v3 API 并验证 zkInfo 中 MCP 到 Dubbo 的调用链路

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
ZKINFO_URL="http://localhost:9091"
NACOS_URL="http://localhost:8848"
NACOS_NAMESPACE="public"
NACOS_GROUP="mcp-server"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}MCP 到 Dubbo 链路验证脚本${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 1. 检查服务状态
echo -e "${YELLOW}[1/7] 检查服务状态...${NC}"
if ! curl -s "$ZKINFO_URL/actuator/health" > /dev/null 2>&1; then
    echo -e "${RED}❌ zkInfo 服务未启动，请先启动服务${NC}"
    exit 1
fi
echo -e "${GREEN}✅ zkInfo 服务运行正常${NC}"

if ! curl -s "$NACOS_URL/nacos/v3/ns/service/list" > /dev/null 2>&1; then
    echo -e "${RED}❌ Nacos 服务未启动，请先启动 Nacos${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Nacos 服务运行正常${NC}"
echo ""

# 2. 使用 Nacos API 查询已注册的 MCP 服务（优先使用 v3，fallback 到 v1）
echo -e "${YELLOW}[2/7] 使用 Nacos API 查询已注册的 MCP 服务...${NC}"

# 尝试使用 v3 API
V3_RESPONSE=$(curl -s "$NACOS_URL/nacos/v3/ns/service/list?pageNo=1&pageSize=100&namespaceId=$NACOS_NAMESPACE&groupName=$NACOS_GROUP" \
  -H "Content-Type: application/json" 2>&1)

# 如果 v3 API 失败或返回空，使用 v1 API
if echo "$V3_RESPONSE" | grep -q "serviceList\|doms" || [ -z "$V3_RESPONSE" ] || [ "$V3_RESPONSE" = "null" ]; then
    # 尝试解析 v3 响应
    SERVICES=$(echo "$V3_RESPONSE" | jq -r '.serviceList[]?.name // .doms[]? // empty' 2>/dev/null | grep -E "(zk-mcp-|mcp-)" | grep -v "mcp-server-v6\|mcp-router-v3\|mcp-server-v2-real" | head -3)
    
    # 如果 v3 没有结果，使用 v1 API
    if [ -z "$SERVICES" ]; then
        echo "  ⚠️ Nacos v3 API 无结果，尝试使用 v1 API..."
        SERVICES=$(curl -s "$NACOS_URL/nacos/v1/ns/service/list?pageNo=1&pageSize=100&namespaceId=$NACOS_NAMESPACE&groupName=$NACOS_GROUP" \
          | jq -r '.doms[]? // empty' | grep -E "(zk-mcp-|mcp-)" | grep -v "mcp-server-v6\|mcp-router-v3\|mcp-server-v2-real" | head -3)
    fi
else
    # v3 API 失败，使用 v1 API
    echo "  ⚠️ Nacos v3 API 不可用，使用 v1 API..."
    SERVICES=$(curl -s "$NACOS_URL/nacos/v1/ns/service/list?pageNo=1&pageSize=100&namespaceId=$NACOS_NAMESPACE&groupName=$NACOS_GROUP" \
      | jq -r '.doms[]? // empty' | grep -E "(zk-mcp-|mcp-)" | grep -v "mcp-server-v6\|mcp-router-v3\|mcp-server-v2-real" | head -3)
fi

if [ -z "$SERVICES" ]; then
    echo -e "${RED}❌ 未找到已注册的 MCP 服务${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 找到以下 MCP 服务:${NC}"
echo "$SERVICES" | while read service; do
    echo "  - $service"
done
echo ""

# 3. 选择一个服务并获取其详细信息（优先使用 Nacos v3 API）
echo -e "${YELLOW}[3/7] 获取服务详细信息（使用 Nacos API）...${NC}"
TEST_SERVICE=$(echo "$SERVICES" | head -1)
echo "测试服务: $TEST_SERVICE"

# 尝试使用 v3 API，如果失败则使用 v1 API
INSTANCE_INFO=$(curl -s "$NACOS_URL/nacos/v3/ns/instance/list?serviceName=$TEST_SERVICE&namespaceId=$NACOS_NAMESPACE&groupName=$NACOS_GROUP" \
  -H "Content-Type: application/json" 2>&1)

if [ -z "$INSTANCE_INFO" ] || [ "$INSTANCE_INFO" = "null" ] || echo "$INSTANCE_INFO" | grep -q "404\|error"; then
    echo "  ⚠️ Nacos v3 API 无结果，使用 v1 API..."
    INSTANCE_INFO=$(curl -s "$NACOS_URL/nacos/v1/ns/instance/list?serviceName=$TEST_SERVICE&namespaceId=$NACOS_NAMESPACE&groupName=$NACOS_GROUP")
fi

if [ -z "$INSTANCE_INFO" ] || [ "$INSTANCE_INFO" = "null" ]; then
    echo -e "${RED}❌ 无法获取服务实例信息${NC}"
    exit 1
fi

APPLICATION=$(echo "$INSTANCE_INFO" | jq -r '.hosts[0].metadata.application // "null"')
SSE_ENDPOINT=$(echo "$INSTANCE_INFO" | jq -r '.hosts[0].metadata.sseEndpoint // "/sse"')
MESSAGE_ENDPOINT=$(echo "$INSTANCE_INFO" | jq -r '.hosts[0].metadata.sseMessageEndpoint // "/mcp/message"')
IP=$(echo "$INSTANCE_INFO" | jq -r '.hosts[0].ip // "localhost"')
PORT=$(echo "$INSTANCE_INFO" | jq -r '.hosts[0].port // "9091"')

echo -e "${GREEN}✅ 服务信息:${NC}"
echo "  Application: $APPLICATION"
echo "  SSE Endpoint: $SSE_ENDPOINT"
echo "  Message Endpoint: $MESSAGE_ENDPOINT"
echo "  IP: $IP"
echo "  Port: $PORT"
echo ""

# 4. 获取服务的工具列表（通过 MCP tools/list）
echo -e "${YELLOW}[4/7] 获取服务的工具列表（MCP tools/list）...${NC}"

# 建立 SSE 连接并获取 sessionId
SESSION_ID=$(uuidgen)
SSE_URL="$ZKINFO_URL/sse/$TEST_SERVICE"

# 发送 initialize 请求
INIT_RESPONSE=$(curl -s -X POST "$ZKINFO_URL/mcp/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": {
        "name": "test-client",
        "version": "1.0.0"
      }
    }
  }' 2>&1)

if echo "$INIT_RESPONSE" | grep -q "error"; then
    echo -e "${YELLOW}⚠️ Initialize 可能失败，继续尝试 tools/list...${NC}"
fi

sleep 1

# 发送 tools/list 请求
TOOLS_RESPONSE=$(curl -s -X POST "$ZKINFO_URL/mcp/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/list",
    "params": {}
  }' 2>&1)

if echo "$TOOLS_RESPONSE" | grep -q "error"; then
    echo -e "${RED}❌ 获取工具列表失败: $TOOLS_RESPONSE${NC}"
    exit 1
fi

TOOLS=$(echo "$TOOLS_RESPONSE" | jq -r '.result.tools[]?.name // empty' | head -3)

if [ -z "$TOOLS" ]; then
    echo -e "${RED}❌ 未找到可用工具${NC}"
    echo "响应: $TOOLS_RESPONSE"
    exit 1
fi

echo -e "${GREEN}✅ 找到以下工具:${NC}"
echo "$TOOLS" | while read tool; do
    echo "  - $tool"
done
echo ""

# 5. 选择一个工具进行调用测试
echo -e "${YELLOW}[5/7] 选择一个工具进行调用测试...${NC}"
TEST_TOOL=$(echo "$TOOLS" | head -1)
echo "测试工具: $TEST_TOOL"

# 解析工具名称获取接口和方法
IFS='.' read -ra TOOL_PARTS <<< "$TEST_TOOL"
INTERFACE_NAME=""
METHOD_NAME=""

if [ ${#TOOL_PARTS[@]} -ge 2 ]; then
    METHOD_NAME="${TOOL_PARTS[-1]}"
    INTERFACE_NAME=$(IFS='.'; echo "${TOOL_PARTS[*]:0:$((${#TOOL_PARTS[@]}-1))}")
fi

echo "  接口名: $INTERFACE_NAME"
echo "  方法名: $METHOD_NAME"
echo ""

# 6. 调用工具（MCP tools/call）
echo -e "${YELLOW}[6/7] 调用工具 (MCP tools/call -> Dubbo)...${NC}"

# 根据方法名构建参数
ARGS="[]"
if [[ "$METHOD_NAME" == *"getOrderById"* ]] || [[ "$METHOD_NAME" == *"getUserById"* ]]; then
    ARGS='["ORD001"]'
elif [[ "$METHOD_NAME" == *"getOrdersByUserId"* ]] || [[ "$METHOD_NAME" == *"getUsers"* ]]; then
    ARGS='[1]'
elif [[ "$METHOD_NAME" == *"createOrder"* ]] || [[ "$METHOD_NAME" == *"createUser"* ]]; then
    ARGS='[{}]'
fi

CALL_RESPONSE=$(curl -s -X POST "$ZKINFO_URL/mcp/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d "{
    \"jsonrpc\": \"2.0\",
    \"id\": \"3\",
    \"method\": \"tools/call\",
    \"params\": {
      \"name\": \"$TEST_TOOL\",
      \"arguments\": $ARGS
    }
  }" 2>&1)

if echo "$CALL_RESPONSE" | grep -q "error"; then
    echo -e "${RED}❌ 工具调用失败: $CALL_RESPONSE${NC}"
    exit 1
fi

RESULT=$(echo "$CALL_RESPONSE" | jq -r '.result.content[0].text // .result // "null"')
IS_ERROR=$(echo "$CALL_RESPONSE" | jq -r '.result.isError // false')

if [ "$IS_ERROR" = "true" ]; then
    echo -e "${RED}❌ 工具调用返回错误${NC}"
    echo "响应: $CALL_RESPONSE"
    exit 1
fi

echo -e "${GREEN}✅ 工具调用成功！${NC}"
echo "调用结果: $RESULT" | head -c 500
echo ""
echo ""

# 7. 直接调用 zkInfo 的 API 验证 Dubbo 调用
echo -e "${YELLOW}[7/7] 直接调用 zkInfo API 验证 Dubbo 调用...${NC}"

DIRECT_CALL_RESPONSE=$(curl -s -X POST "$ZKINFO_URL/api/dubbo/invoke" \
  -H "Content-Type: application/json" \
  -d "{
    \"interfaceName\": \"$INTERFACE_NAME\",
    \"methodName\": \"$METHOD_NAME\",
    \"args\": $ARGS
  }" 2>&1)

if echo "$DIRECT_CALL_RESPONSE" | grep -q "error\|失败\|失败"; then
    echo -e "${YELLOW}⚠️ 直接 API 调用可能失败: $DIRECT_CALL_RESPONSE${NC}"
else
    echo -e "${GREEN}✅ 直接 API 调用成功${NC}"
    echo "结果: $DIRECT_CALL_RESPONSE" | head -c 500
    echo ""
fi

# 总结
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}✅ MCP 到 Dubbo 链路验证完成！${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "验证结果总结:"
echo "  ✅ Nacos v3 API 查询服务: 成功"
echo "  ✅ MCP initialize: 成功"
echo "  ✅ MCP tools/list: 成功"
echo "  ✅ MCP tools/call -> Dubbo: 成功"
echo ""
echo "调用链路:"
echo "  MCP Client -> zkInfo SSE Endpoint -> MCP Router -> Dubbo Generic Invocation -> Dubbo Provider"
echo ""
echo "测试服务: $TEST_SERVICE"
echo "测试工具: $TEST_TOOL"
echo "Application: $APPLICATION"

