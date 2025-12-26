#!/bin/bash

# 多 SSE 连接节点功能完整验证脚本
# 验证 zkInfo 项目中多个 SSE 端点的完整调用功能

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
ZKINFO_URL="http://localhost:9091"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}多 SSE 连接节点功能完整验证${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 1. 检查服务状态
echo -e "${YELLOW}[1/6] 检查服务状态...${NC}"
if ! curl -s "$ZKINFO_URL/actuator/health" > /dev/null 2>&1; then
    echo -e "${RED}❌ zkInfo 服务未启动${NC}"
    exit 1
fi
echo -e "${GREEN}✅ zkInfo 服务运行正常${NC}"
echo ""

# 2. 获取测试用的 endpoint
echo -e "${YELLOW}[2/6] 获取测试用的 endpoint...${NC}"

# 获取 MCP 服务名称（最可靠的测试方式）
MCP_SERVICE=$(curl -s "http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=10&namespaceId=public&groupName=mcp-server" \
  | jq -r '.doms[]? | select(startswith("zk-mcp-"))' | head -1)

if [ -z "$MCP_SERVICE" ]; then
    echo -e "${RED}❌ 未找到 MCP 服务${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 使用 MCP 服务: $MCP_SERVICE${NC}"
echo ""

# 3. 测试不同的 endpoint 格式
echo -e "${YELLOW}[3/6] 测试不同的 endpoint 格式...${NC}"

# 3.1 测试标准 SSE 端点（带 serviceName 参数）
echo "  测试 1: GET /sse?serviceName=$MCP_SERVICE"
RESPONSE=$(curl -s -w "\n%{http_code}" "$ZKINFO_URL/sse?serviceName=$MCP_SERVICE" \
  -H "Accept: text/event-stream" \
  --max-time 2 2>&1 | tail -1)
if [ "$RESPONSE" = "200" ]; then
    echo -e "  ${GREEN}✅ 标准 SSE 端点连接成功${NC}"
else
    echo -e "  ${YELLOW}⚠️ 标准 SSE 端点返回: $RESPONSE${NC}"
fi

# 3.2 测试使用 MCP 服务名称作为 endpoint
echo "  测试 2: GET /sse/$MCP_SERVICE"
RESPONSE=$(curl -s -w "\n%{http_code}" "$ZKINFO_URL/sse/$MCP_SERVICE" \
  -H "Accept: text/event-stream" \
  --max-time 2 2>&1 | tail -1)
if [ "$RESPONSE" = "200" ]; then
    echo -e "  ${GREEN}✅ MCP 服务名称 endpoint 连接成功${NC}"
else
    echo -e "  ${YELLOW}⚠️ MCP 服务名称 endpoint 返回: $RESPONSE${NC}"
fi

# 3.3 测试项目代码 endpoint（如果有）
PROJECT_CODE=$(curl -s "$ZKINFO_URL/api/projects" 2>/dev/null | jq -r '.[] | select(.projectType == "REAL") | .projectCode' | head -1)
if [ ! -z "$PROJECT_CODE" ] && [ "$PROJECT_CODE" != "null" ]; then
    echo "  测试 3: GET /sse/$PROJECT_CODE"
    RESPONSE=$(curl -s -w "\n%{http_code}" "$ZKINFO_URL/sse/$PROJECT_CODE" \
      -H "Accept: text/event-stream" \
      --max-time 2 2>&1 | tail -1)
    if [ "$RESPONSE" = "200" ]; then
        echo -e "  ${GREEN}✅ 项目代码 endpoint 连接成功${NC}"
    else
        echo -e "  ${YELLOW}⚠️ 项目代码 endpoint 返回: $RESPONSE${NC}"
    fi
fi

# 3.4 测试虚拟项目 endpoint（如果有）
ENDPOINT_NAME=$(curl -s "$ZKINFO_URL/api/virtual-projects" 2>/dev/null | jq -r '.[] | .endpoint.endpointName // empty' | grep -v "null" | head -1)
if [ ! -z "$ENDPOINT_NAME" ]; then
    echo "  测试 4: GET /sse/$ENDPOINT_NAME"
    RESPONSE=$(curl -s -w "\n%{http_code}" "$ZKINFO_URL/sse/$ENDPOINT_NAME" \
      -H "Accept: text/event-stream" \
      --max-time 2 2>&1 | tail -1)
    if [ "$RESPONSE" = "200" ]; then
        echo -e "  ${GREEN}✅ 虚拟项目 endpoint 连接成功${NC}"
    else
        echo -e "  ${YELLOW}⚠️ 虚拟项目 endpoint 返回: $RESPONSE${NC}"
    fi
fi

echo ""

# 4. 完整测试：建立 SSE 连接并发送 MCP 消息
echo -e "${YELLOW}[4/6] 完整测试：建立 SSE 连接并发送 MCP 消息...${NC}"

SESSION_ID=$(uuidgen)
ENDPOINT="$MCP_SERVICE"

echo "  Endpoint: $ENDPOINT"
echo "  Session ID: $SESSION_ID"
echo ""

# 4.1 建立 SSE 连接（后台运行，收集响应）
echo "  步骤 1: 建立 SSE 连接..."
curl -s -N "$ZKINFO_URL/sse/$ENDPOINT" \
  -H "Accept: text/event-stream" \
  > /tmp/sse_test_$$.txt 2>&1 &
SSE_PID=$!

# 等待连接建立
sleep 2

# 检查 SSE 连接是否建立
if ps -p $SSE_PID > /dev/null 2>&1; then
    echo -e "  ${GREEN}✅ SSE 连接已建立${NC}"
    
    # 检查是否有响应数据
    if [ -f /tmp/sse_test_$$.txt ] && [ -s /tmp/sse_test_$$.txt ]; then
        echo -e "  ${GREEN}✅ 收到 SSE 响应数据${NC}"
        echo "  响应示例:"
        head -3 /tmp/sse_test_$$.txt | sed 's/^/    /'
    fi
else
    echo -e "  ${RED}❌ SSE 连接失败${NC}"
    kill $SSE_PID 2>/dev/null || true
    rm -f /tmp/sse_test_$$.txt
    exit 1
fi

echo ""

# 4.2 发送 initialize 请求
echo "  步骤 2: 发送 initialize 请求..."
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

HTTP_CODE=$(echo "$INIT_RESPONSE" | grep -oP '\d{3}' | tail -1 || echo "unknown")

if echo "$INIT_RESPONSE" | grep -q "accepted\|202\|200" || [ "$HTTP_CODE" = "202" ] || [ "$HTTP_CODE" = "200" ]; then
    echo -e "  ${GREEN}✅ Initialize 请求已接受 (HTTP $HTTP_CODE)${NC}"
else
    echo -e "  ${YELLOW}⚠️ Initialize 响应: $INIT_RESPONSE${NC}"
fi

# 等待响应通过 SSE 发送
sleep 1

# 检查 SSE 响应中是否有 initialize 响应
if [ -f /tmp/sse_test_$$.txt ]; then
    INIT_COUNT=$(grep -c "initialize\|protocolVersion" /tmp/sse_test_$$.txt || echo "0")
    if [ "$INIT_COUNT" -gt 0 ]; then
        echo -e "  ${GREEN}✅ 从 SSE 流中收到 initialize 响应${NC}"
    fi
fi

echo ""

# 4.3 发送 tools/list 请求
echo "  步骤 3: 发送 tools/list 请求..."
TOOLS_RESPONSE=$(curl -s -X POST "$ZKINFO_URL/mcp/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/list",
    "params": {}
  }' 2>&1)

HTTP_CODE=$(echo "$TOOLS_RESPONSE" | grep -oP '\d{3}' | tail -1 || echo "unknown")

if echo "$TOOLS_RESPONSE" | grep -q "accepted\|202\|200" || [ "$HTTP_CODE" = "202" ] || [ "$HTTP_CODE" = "200" ]; then
    echo -e "  ${GREEN}✅ Tools/list 请求已接受 (HTTP $HTTP_CODE)${NC}"
else
    echo -e "  ${YELLOW}⚠️ Tools/list 响应: $TOOLS_RESPONSE${NC}"
fi

# 等待响应通过 SSE 发送
sleep 2

# 检查 SSE 响应中是否有工具列表
if [ -f /tmp/sse_test_$$.txt ]; then
    TOOLS_COUNT=$(grep -c "tools\|tool" /tmp/sse_test_$$.txt || echo "0")
    if [ "$TOOLS_COUNT" -gt 0 ]; then
        echo -e "  ${GREEN}✅ 从 SSE 流中收到工具列表${NC}"
        # 显示工具列表片段
        grep -i "tool" /tmp/sse_test_$$.txt | head -2 | sed 's/^/    /' || true
    else
        echo -e "  ${YELLOW}⚠️ 未从 SSE 流中检测到工具列表${NC}"
    fi
fi

echo ""

# 4.4 发送 tools/call 请求
echo "  步骤 4: 发送 tools/call 请求..."

# 从接口名提取方法名
INTERFACE_NAME=$(echo "$MCP_SERVICE" | sed 's/^zk-mcp-//' | sed 's/-[0-9]\+\.[0-9]\+\.[0-9]\+$//' | sed 's/-/./g')
# 将最后一个单词首字母大写
LAST_WORD=$(echo "$INTERFACE_NAME" | awk -F'.' '{print $NF}')
REST=$(echo "$INTERFACE_NAME" | sed "s/\\.$LAST_WORD\$//")
LAST_WORD_CAP=$(echo "$LAST_WORD" | sed 's/^\(.\)/\U\1/')
INTERFACE_NAME="${REST}.${LAST_WORD_CAP}"

# 选择合适的方法和参数
if [[ "$INTERFACE_NAME" == *"OrderService"* ]]; then
    METHOD="getOrderById"
    ARGS='["ORD001"]'
elif [[ "$INTERFACE_NAME" == *"UserService"* ]]; then
    METHOD="getUserById"
    ARGS='[1]'
elif [[ "$INTERFACE_NAME" == *"ProductService"* ]]; then
    METHOD="getProductById"
    ARGS='[1]'
else
    METHOD="getOrderById"
    ARGS='["ORD001"]'
fi

TOOL_NAME="$INTERFACE_NAME.$METHOD"

CALL_RESPONSE=$(curl -s -X POST "$ZKINFO_URL/mcp/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d "{
    \"jsonrpc\": \"2.0\",
    \"id\": \"3\",
    \"method\": \"tools/call\",
    \"params\": {
      \"name\": \"$TOOL_NAME\",
      \"arguments\": $ARGS
    }
  }" 2>&1)

HTTP_CODE=$(echo "$CALL_RESPONSE" | grep -oP '\d{3}' | tail -1 || echo "unknown")

if echo "$CALL_RESPONSE" | grep -q "accepted\|202\|200" || [ "$HTTP_CODE" = "202" ] || [ "$HTTP_CODE" = "200" ]; then
    echo -e "  ${GREEN}✅ Tools/call 请求已接受 (HTTP $HTTP_CODE)${NC}"
    echo "  调用工具: $TOOL_NAME"
else
    echo -e "  ${YELLOW}⚠️ Tools/call 响应: $CALL_RESPONSE${NC}"
fi

# 等待响应通过 SSE 发送
sleep 2

# 检查 SSE 响应中是否有调用结果
if [ -f /tmp/sse_test_$$.txt ]; then
    RESULT_COUNT=$(grep -c "result\|content" /tmp/sse_test_$$.txt || echo "0")
    if [ "$RESULT_COUNT" -gt 0 ]; then
        echo -e "  ${GREEN}✅ 从 SSE 流中收到调用结果${NC}"
        # 显示结果片段
        grep -i "result\|content" /tmp/sse_test_$$.txt | head -2 | sed 's/^/    /' || true
    else
        echo -e "  ${YELLOW}⚠️ 未从 SSE 流中检测到调用结果${NC}"
    fi
fi

# 清理
kill $SSE_PID 2>/dev/null || true
sleep 1
rm -f /tmp/sse_test_$$.txt

echo ""

# 5. 测试多个 endpoint 同时连接
echo -e "${YELLOW}[5/6] 测试多个 endpoint 同时连接...${NC}"

ENDPOINTS=()
if [ ! -z "$MCP_SERVICE" ]; then
    ENDPOINTS+=("$MCP_SERVICE")
fi
if [ ! -z "$PROJECT_CODE" ] && [ "$PROJECT_CODE" != "null" ]; then
    ENDPOINTS+=("$PROJECT_CODE")
fi
if [ ! -z "$ENDPOINT_NAME" ]; then
    ENDPOINTS+=("$ENDPOINT_NAME")
fi

SUCCESS_COUNT=0
for endpoint in "${ENDPOINTS[@]}"; do
    echo "  测试 endpoint: $endpoint"
    SESSION_ID=$(uuidgen)
    
    RESPONSE=$(curl -s -w "\n%{http_code}" "$ZKINFO_URL/sse/$endpoint" \
      -H "Accept: text/event-stream" \
      --max-time 2 2>&1 | tail -1)
    
    if [ "$RESPONSE" = "200" ]; then
        echo -e "    ${GREEN}✅ 连接成功${NC}"
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    else
        echo -e "    ${YELLOW}⚠️ 连接返回: $RESPONSE${NC}"
    fi
done

echo ""
echo -e "${GREEN}成功连接的 endpoint 数量: $SUCCESS_COUNT/${#ENDPOINTS[@]}${NC}"
echo ""

# 6. 验证总结
echo -e "${YELLOW}[6/6] 验证总结...${NC}"
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}✅ 多 SSE 端点功能验证完成！${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "支持的 endpoint 格式:"
echo "  1. GET /sse?serviceName={serviceName} - 标准端点（需要 serviceName 参数）"
echo "  2. GET /sse/{projectCode} - 项目代码"
echo "  3. GET /sse/{projectName} - 项目名称"
echo "  4. GET /sse/{endpointName} - 虚拟项目 endpoint 名称"
echo "  5. GET /sse/{virtualProjectId} - 虚拟项目 ID（数字）"
echo "  6. GET /sse/{mcpServiceName} - MCP 服务名称（如 zk-mcp-...）"
echo ""
echo "MCP 消息端点:"
echo "  POST /mcp/message?sessionId={sessionId} - 通用消息端点（通过 sessionId 查找 endpoint）"
echo "  POST /mcp/{endpoint}/message?sessionId={sessionId} - 指定 endpoint 的消息端点"
echo ""
echo "调用流程:"
echo "  1. 建立 SSE 连接: GET /sse/{endpoint}"
echo "  2. 获取 sessionId（从 SSE 响应或自动生成）"
echo "  3. 发送 MCP 消息: POST /mcp/message?sessionId={sessionId}"
echo "  4. 接收响应: 通过 SSE 流接收"
echo ""

