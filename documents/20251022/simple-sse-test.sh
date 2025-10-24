#!/bin/bash

GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

BASE_URL="http://localhost:9091"

echo -e "${BLUE}简化SSE测试${NC}"
echo "===================="

# 测试 1: 创建流式调用
echo -e "${BLUE}1. 创建流式调用${NC}"
RESPONSE=$(curl -s -X POST "$BASE_URL/mcp/stream" \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc": "2.0", "id": "test", "method": "tools/call", "params": {"name": "com.zkinfo.demo.service.UserService.getUserById", "arguments": {"args": [1]}, "stream": true}}')

echo "响应: $RESPONSE"

STREAM_ID=$(echo "$RESPONSE" | grep -o '"streamId":"[^"]*"' | sed 's/"streamId":"\([^"]*\)"/\1/')

if [ -n "$STREAM_ID" ]; then
    echo -e "${GREEN}✅ 创建成功: $STREAM_ID${NC}"
else
    echo -e "${RED}❌ 创建失败${NC}"
    exit 1
fi

# 测试 2: 连接SSE并接收数据
echo -e "${BLUE}2. 连接SSE流${NC}"
echo "URL: $BASE_URL/mcp/stream/$STREAM_ID"

SSE_DATA=$(timeout 5 curl -s -N --no-buffer -H "Accept: text/event-stream" \
    "$BASE_URL/mcp/stream/$STREAM_ID" 2>&1)

echo "收到的SSE数据:"
echo "$SSE_DATA"
echo ""

if echo "$SSE_DATA" | grep -qE "(^id:|^event:|^data:)"; then
    echo -e "${GREEN}✅ SSE数据接收成功${NC}"
    
    echo -e "${BLUE}数据格式验证:${NC}"
    echo "$SSE_DATA" | grep -E "^(id:|event:|data:)" | head -n 5
else
    echo -e "${RED}❌ SSE数据接收失败${NC}"
    exit 1
fi

# 测试 3: 验证SSE事件格式
echo -e "${BLUE}3. 验证SSE事件格式${NC}"

ID_COUNT=$(echo "$SSE_DATA" | grep -c "^id:" || true)
EVENT_COUNT=$(echo "$SSE_DATA" | grep -c "^event:" || true)
DATA_COUNT=$(echo "$SSE_DATA" | grep -c "^data:" || true)

echo "  - id: 字段数量: $ID_COUNT"
echo "  - event: 字段数量: $EVENT_COUNT"
echo "  - data: 字段数量: $DATA_COUNT"

if [ $ID_COUNT -gt 0 ] && [ $EVENT_COUNT -gt 0 ] && [ $DATA_COUNT -gt 0 ]; then
    echo -e "${GREEN}✅ SSE格式正确${NC}"
else
    echo -e "${RED}❌ SSE格式不完整${NC}"
fi

echo ""
echo -e "${GREEN}🎉 SSE连接测试完成！${NC}"
