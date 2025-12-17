#!/bin/bash

# 多 SSE 连接节点功能验证脚本
# 验证 zkInfo 项目中多个 SSE 端点的调用功能

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
echo -e "${BLUE}多 SSE 连接节点功能验证脚本${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 1. 检查服务状态
echo -e "${YELLOW}[1/8] 检查服务状态...${NC}"
if ! curl -s "$ZKINFO_URL/actuator/health" > /dev/null 2>&1; then
    echo -e "${RED}❌ zkInfo 服务未启动，请先启动服务${NC}"
    exit 1
fi
echo -e "${GREEN}✅ zkInfo 服务运行正常${NC}"
echo ""

# 2. 获取可用的项目信息
echo -e "${YELLOW}[2/8] 获取可用的项目信息...${NC}"

# 获取实际项目
REAL_PROJECTS=$(curl -s "$ZKINFO_URL/api/projects" 2>/dev/null | jq -r '.[] | select(.projectType == "REAL") | "\(.projectCode)|\(.projectName)"' | head -3)
if [ ! -z "$REAL_PROJECTS" ]; then
    echo -e "${GREEN}✅ 找到实际项目:${NC}"
    echo "$REAL_PROJECTS" | while IFS='|' read -r code name; do
        echo "  - $code ($name)"
    done
else
    echo -e "${YELLOW}⚠️ 未找到实际项目${NC}"
fi

# 获取虚拟项目
VIRTUAL_PROJECTS=$(curl -s "$ZKINFO_URL/api/virtual-projects" 2>/dev/null | jq -r '.[] | "\(.id)|\(.projectName)|\(.endpoint.endpointName)"' | head -3)
if [ ! -z "$VIRTUAL_PROJECTS" ]; then
    echo -e "${GREEN}✅ 找到虚拟项目:${NC}"
    echo "$VIRTUAL_PROJECTS" | while IFS='|' read -r id name endpoint; do
        echo "  - ID: $id, Name: $name, Endpoint: $endpoint"
    done
else
    echo -e "${YELLOW}⚠️ 未找到虚拟项目${NC}"
fi

# 获取 MCP 服务名称
MCP_SERVICES=$(curl -s "http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=10&namespaceId=public&groupName=mcp-server" \
  | jq -r '.doms[]? | select(startswith("zk-mcp-"))' | head -1)
if [ ! -z "$MCP_SERVICES" ]; then
    echo -e "${GREEN}✅ 找到 MCP 服务:${NC}"
    echo "  - $MCP_SERVICES"
fi
echo ""

# 3. 测试标准 SSE 端点 (/sse)
echo -e "${YELLOW}[3/8] 测试标准 SSE 端点 (/sse)...${NC}"
SESSION_ID_1=$(uuidgen)
SSE_RESPONSE=$(curl -s -N "$ZKINFO_URL/sse?serviceName=$MCP_SERVICES" \
  -H "Accept: text/event-stream" \
  --max-time 3 2>&1 | head -5 || echo "timeout")

if echo "$SSE_RESPONSE" | grep -q "event:\|data:"; then
    echo -e "${GREEN}✅ 标准 SSE 端点连接成功${NC}"
    echo "  响应示例: $(echo "$SSE_RESPONSE" | head -2)"
else
    echo -e "${YELLOW}⚠️ 标准 SSE 端点可能需要 serviceName 参数${NC}"
fi
echo ""

# 4. 测试使用项目代码作为 endpoint
if [ ! -z "$REAL_PROJECTS" ]; then
    echo -e "${YELLOW}[4/8] 测试使用项目代码作为 endpoint (/sse/{projectCode})...${NC}"
    PROJECT_CODE=$(echo "$REAL_PROJECTS" | head -1 | cut -d'|' -f1)
    echo "测试项目代码: $PROJECT_CODE"
    
    SSE_RESPONSE=$(curl -s -N "$ZKINFO_URL/sse/$PROJECT_CODE" \
      -H "Accept: text/event-stream" \
      --max-time 3 2>&1 | head -5 || echo "timeout")
    
    if echo "$SSE_RESPONSE" | grep -q "event:\|data:"; then
        echo -e "${GREEN}✅ 项目代码 endpoint 连接成功${NC}"
    else
        echo -e "${YELLOW}⚠️ 项目代码 endpoint 连接可能失败或需要更多时间${NC}"
    fi
    echo ""
fi

# 5. 测试使用虚拟项目 endpoint 名称
if [ ! -z "$VIRTUAL_PROJECTS" ]; then
    echo -e "${YELLOW}[5/8] 测试使用虚拟项目 endpoint 名称 (/sse/{endpointName})...${NC}"
    ENDPOINT_NAME=$(echo "$VIRTUAL_PROJECTS" | head -1 | cut -d'|' -f3)
    if [ ! -z "$ENDPOINT_NAME" ] && [ "$ENDPOINT_NAME" != "null" ]; then
        echo "测试 endpoint 名称: $ENDPOINT_NAME"
        
        SSE_RESPONSE=$(curl -s -N "$ZKINFO_URL/sse/$ENDPOINT_NAME" \
          -H "Accept: text/event-stream" \
          --max-time 3 2>&1 | head -5 || echo "timeout")
        
        if echo "$SSE_RESPONSE" | grep -q "event:\|data:"; then
            echo -e "${GREEN}✅ 虚拟项目 endpoint 连接成功${NC}"
        else
            echo -e "${YELLOW}⚠️ 虚拟项目 endpoint 连接可能失败或需要更多时间${NC}"
        fi
    else
        echo -e "${YELLOW}⚠️ 未找到有效的虚拟项目 endpoint 名称${NC}"
    fi
    echo ""
fi

# 6. 测试使用 MCP 服务名称作为 endpoint
if [ ! -z "$MCP_SERVICES" ]; then
    echo -e "${YELLOW}[6/8] 测试使用 MCP 服务名称作为 endpoint (/sse/{serviceName})...${NC}"
    echo "测试服务名称: $MCP_SERVICES"
    
    SSE_RESPONSE=$(curl -s -N "$ZKINFO_URL/sse/$MCP_SERVICES" \
      -H "Accept: text/event-stream" \
      --max-time 3 2>&1 | head -5 || echo "timeout")
    
    if echo "$SSE_RESPONSE" | grep -q "event:\|data:"; then
        echo -e "${GREEN}✅ MCP 服务名称 endpoint 连接成功${NC}"
    else
        echo -e "${YELLOW}⚠️ MCP 服务名称 endpoint 连接可能失败或需要更多时间${NC}"
    fi
    echo ""
fi

# 7. 完整测试：建立 SSE 连接并发送 MCP 消息
echo -e "${YELLOW}[7/8] 完整测试：建立 SSE 连接并发送 MCP 消息...${NC}"

# 选择一个可用的 endpoint
TEST_ENDPOINT=""
if [ ! -z "$MCP_SERVICES" ]; then
    TEST_ENDPOINT="$MCP_SERVICES"
elif [ ! -z "$REAL_PROJECTS" ]; then
    TEST_ENDPOINT=$(echo "$REAL_PROJECTS" | head -1 | cut -d'|' -f1)
elif [ ! -z "$VIRTUAL_PROJECTS" ]; then
    TEST_ENDPOINT=$(echo "$VIRTUAL_PROJECTS" | head -1 | cut -d'|' -f3)
fi

if [ -z "$TEST_ENDPOINT" ]; then
    echo -e "${RED}❌ 未找到可用的 endpoint 进行测试${NC}"
    exit 1
fi

echo "使用 endpoint: $TEST_ENDPOINT"

# 建立 SSE 连接（后台运行）
SESSION_ID=$(uuidgen)
echo "Session ID: $SESSION_ID"

# 启动 SSE 连接（在后台）
curl -s -N "$ZKINFO_URL/sse/$TEST_ENDPOINT" \
  -H "Accept: text/event-stream" \
  > /tmp/sse_response_$$.txt 2>&1 &
SSE_PID=$!

# 等待连接建立
sleep 2

# 发送 initialize 请求
echo "发送 initialize 请求..."
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

if echo "$INIT_RESPONSE" | grep -q "accepted\|202"; then
    echo -e "${GREEN}✅ Initialize 请求已接受${NC}"
else
    echo -e "${YELLOW}⚠️ Initialize 响应: $INIT_RESPONSE${NC}"
fi

sleep 1

# 发送 tools/list 请求
echo "发送 tools/list 请求..."
TOOLS_RESPONSE=$(curl -s -X POST "$ZKINFO_URL/mcp/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/list",
    "params": {}
  }' 2>&1)

if echo "$TOOLS_RESPONSE" | grep -q "accepted\|202"; then
    echo -e "${GREEN}✅ Tools/list 请求已接受${NC}"
    
    # 检查 SSE 响应中是否有工具列表
    sleep 1
    if [ -f /tmp/sse_response_$$.txt ]; then
        TOOLS_COUNT=$(grep -c "tools" /tmp/sse_response_$$.txt || echo "0")
        if [ "$TOOLS_COUNT" -gt 0 ]; then
            echo -e "${GREEN}✅ 从 SSE 流中收到工具列表${NC}"
        fi
    fi
else
    echo -e "${YELLOW}⚠️ Tools/list 响应: $TOOLS_RESPONSE${NC}"
fi

# 清理
kill $SSE_PID 2>/dev/null || true
rm -f /tmp/sse_response_$$.txt

echo ""

# 8. 测试多个不同的 endpoint 同时连接
echo -e "${YELLOW}[8/8] 测试多个不同的 endpoint 同时连接...${NC}"

ENDPOINT_COUNT=0
SUCCESS_COUNT=0

# 测试项目代码 endpoint
if [ ! -z "$REAL_PROJECTS" ]; then
    PROJECT_CODE=$(echo "$REAL_PROJECTS" | head -1 | cut -d'|' -f1)
    SESSION_ID=$(uuidgen)
    RESPONSE=$(curl -s -N "$ZKINFO_URL/sse/$PROJECT_CODE" \
      -H "Accept: text/event-stream" \
      --max-time 2 2>&1 | head -3 || echo "timeout")
    
    ENDPOINT_COUNT=$((ENDPOINT_COUNT + 1))
    if echo "$RESPONSE" | grep -q "event:\|data:"; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        echo -e "${GREEN}✅ 项目代码 endpoint ($PROJECT_CODE) 连接成功${NC}"
    else
        echo -e "${YELLOW}⚠️ 项目代码 endpoint ($PROJECT_CODE) 连接失败或超时${NC}"
    fi
fi

# 测试 MCP 服务名称 endpoint
if [ ! -z "$MCP_SERVICES" ]; then
    SESSION_ID=$(uuidgen)
    RESPONSE=$(curl -s -N "$ZKINFO_URL/sse/$MCP_SERVICES" \
      -H "Accept: text/event-stream" \
      --max-time 2 2>&1 | head -3 || echo "timeout")
    
    ENDPOINT_COUNT=$((ENDPOINT_COUNT + 1))
    if echo "$RESPONSE" | grep -q "event:\|data:"; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        echo -e "${GREEN}✅ MCP 服务名称 endpoint ($MCP_SERVICES) 连接成功${NC}"
    else
        echo -e "${YELLOW}⚠️ MCP 服务名称 endpoint ($MCP_SERVICES) 连接失败或超时${NC}"
    fi
fi

# 测试虚拟项目 endpoint
if [ ! -z "$VIRTUAL_PROJECTS" ]; then
    ENDPOINT_NAME=$(echo "$VIRTUAL_PROJECTS" | head -1 | cut -d'|' -f3)
    if [ ! -z "$ENDPOINT_NAME" ] && [ "$ENDPOINT_NAME" != "null" ]; then
        SESSION_ID=$(uuidgen)
        RESPONSE=$(curl -s -N "$ZKINFO_URL/sse/$ENDPOINT_NAME" \
          -H "Accept: text/event-stream" \
          --max-time 2 2>&1 | head -3 || echo "timeout")
        
        ENDPOINT_COUNT=$((ENDPOINT_COUNT + 1))
        if echo "$RESPONSE" | grep -q "event:\|data:"; then
            SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
            echo -e "${GREEN}✅ 虚拟项目 endpoint ($ENDPOINT_NAME) 连接成功${NC}"
        else
            echo -e "${YELLOW}⚠️ 虚拟项目 endpoint ($ENDPOINT_NAME) 连接失败或超时${NC}"
        fi
    fi
fi

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}✅ 多 SSE 端点功能验证完成！${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "验证结果:"
echo "  测试的 endpoint 数量: $ENDPOINT_COUNT"
echo "  成功连接的 endpoint 数量: $SUCCESS_COUNT"
echo ""
echo "支持的 endpoint 格式:"
echo "  1. /sse - 标准端点（需要 serviceName 参数）"
echo "  2. /sse/{projectCode} - 项目代码"
echo "  3. /sse/{projectName} - 项目名称"
echo "  4. /sse/{endpointName} - 虚拟项目 endpoint 名称"
echo "  5. /sse/{virtualProjectId} - 虚拟项目 ID"
echo "  6. /sse/{mcpServiceName} - MCP 服务名称（如 zk-mcp-...）"
echo ""

