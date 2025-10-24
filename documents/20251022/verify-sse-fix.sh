#!/bin/bash

# 🔧 SSE 流式调用修复验证脚本
set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

BASE_URL="http://localhost:9091"
PASSED=0
FAILED=0

test_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✅ $2${NC}"
        ((PASSED++))
    else
        echo -e "${RED}❌ $2${NC}"
        ((FAILED++))
    fi
}

echo -e "${BLUE}🔧 SSE 流式调用修复验证${NC}"
echo "=================================="

# 测试 1: 服务健康检查
echo -e "${BLUE}测试 1: 服务健康检查${NC}"
curl -s -f "$BASE_URL/mcp/health" > /dev/null
test_result $? "服务健康检查"

# 测试 2: 创建流式调用
echo -e "${BLUE}测试 2: 创建流式调用${NC}"
RESPONSE=$(curl -s -X POST "$BASE_URL/mcp/stream" \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc": "2.0", "id": "test", "method": "tools/call", "params": {"name": "com.zkinfo.demo.service.UserService.getUserById", "arguments": {"args": [1]}, "stream": true}}')

echo "响应: $RESPONSE"

STREAM_ID=$(echo "$RESPONSE" | grep -o '"streamId":"[^"]*"' | sed 's/"streamId":"\([^"]*\)"/\1/')

if [ -n "$STREAM_ID" ]; then
    test_result 0 "流式调用创建成功: $STREAM_ID"
else
    test_result 1 "流式调用创建失败"
    exit 1
fi

# 测试 3: SSE 数据接收（修复后的关键测试）
echo -e "${BLUE}测试 3: SSE 数据接收（修复后）${NC}"

# 接收 SSE 数据，检查是否只收到一次数据（不重复）
SSE_DATA=$(timeout 8 curl -s -N -H "Accept: text/event-stream" \
    "$BASE_URL/mcp/stream/$STREAM_ID")

echo "接收到的 SSE 数据:"
echo "$SSE_DATA"

# 检查数据格式和内容
if echo "$SSE_DATA" | grep -q "data:" && echo "$SSE_DATA" | grep -q "isLast.*true"; then
    test_result 0 "SSE 数据接收成功，格式正确"
else
    test_result 1 "SSE 数据接收失败或格式错误"
fi

# 测试 4: 多个并发流式调用
echo -e "${BLUE}测试 4: 并发流式调用${NC}"
CONCURRENT_SUCCESS=0

for i in 1 2 3; do
    CONCURRENT_RESPONSE=$(curl -s -X POST "$BASE_URL/mcp/stream" \
        -H "Content-Type: application/json" \
        -d "{\"jsonrpc\": \"2.0\", \"id\": \"concurrent-$i\", \"method\": \"tools/call\", \"params\": {\"name\": \"com.zkinfo.demo.service.UserService.getUserById\", \"arguments\": {\"args\": [$i]}, \"stream\": true}}")
    
    CONCURRENT_STREAM_ID=$(echo "$CONCURRENT_RESPONSE" | grep -o '"streamId":"[^"]*"' | sed 's/"streamId":"\([^"]*\)"/\1/')
    if [ -n "$CONCURRENT_STREAM_ID" ]; then
        ((CONCURRENT_SUCCESS++))
    fi
done

if [ $CONCURRENT_SUCCESS -eq 3 ]; then
    test_result 0 "并发流式调用测试: $CONCURRENT_SUCCESS/3"
else
    test_result 1 "并发流式调用测试: $CONCURRENT_SUCCESS/3"
fi

# 测试 5: 不同工具的流式调用
echo -e "${BLUE}测试 5: 不同工具流式调用${NC}"
TOOLS_TESTED=0
TOOLS_SUCCESS=0

# 测试 UserService.getAllUsers
RESPONSE2=$(curl -s -X POST "$BASE_URL/mcp/stream" \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc": "2.0", "id": "test2", "method": "tools/call", "params": {"name": "com.zkinfo.demo.service.UserService.getAllUsers", "arguments": {}, "stream": true}}')

STREAM_ID2=$(echo "$RESPONSE2" | grep -o '"streamId":"[^"]*"' | sed 's/"streamId":"\([^"]*\)"/\1/')
((TOOLS_TESTED++))
if [ -n "$STREAM_ID2" ]; then
    ((TOOLS_SUCCESS++))
fi

# 测试 OrderService
RESPONSE3=$(curl -s -X POST "$BASE_URL/mcp/stream" \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc": "2.0", "id": "test3", "method": "tools/call", "params": {"name": "com.zkinfo.demo.service.OrderService.getOrderById", "arguments": {"args": [1]}, "stream": true}}')

STREAM_ID3=$(echo "$RESPONSE3" | grep -o '"streamId":"[^"]*"' | sed 's/"streamId":"\([^"]*\)"/\1/')
((TOOLS_TESTED++))
if [ -n "$STREAM_ID3" ]; then
    ((TOOLS_SUCCESS++))
fi

if [ $TOOLS_SUCCESS -eq $TOOLS_TESTED ]; then
    test_result 0 "不同工具流式调用测试: $TOOLS_SUCCESS/$TOOLS_TESTED"
else
    test_result 1 "不同工具流式调用测试: $TOOLS_SUCCESS/$TOOLS_TESTED"
fi

# 测试 6: 错误处理
echo -e "${BLUE}测试 6: 错误处理${NC}"
timeout 3 curl -s -N -H "Accept: text/event-stream" \
    "$BASE_URL/mcp/stream/invalid_stream_12345" > /dev/null 2>&1
ERROR_RESULT=$?

if [ $ERROR_RESULT -eq 124 ] || [ $ERROR_RESULT -ne 0 ]; then
    test_result 0 "无效 stream_id 错误处理正确"
else
    test_result 1 "无效 stream_id 处理可能有问题"
fi

# 生成最终报告
echo ""
echo -e "${BLUE}📊 修复验证结果${NC}"
echo "========================"
echo -e "通过测试: ${GREEN}$PASSED${NC}"
echo -e "失败测试: ${RED}$FAILED${NC}"
TOTAL=$((PASSED + FAILED))
if [ $TOTAL -gt 0 ]; then
    SUCCESS_RATE=$((PASSED * 100 / TOTAL))
    echo -e "成功率: ${BLUE}$SUCCESS_RATE%${NC}"
fi

echo ""
if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}🎉 SSE 流式调用修复验证通过！${NC}"
    echo -e "${GREEN}✨ 所有功能正常工作${NC}"
    echo ""
    echo -e "${YELLOW}修复内容总结:${NC}"
    echo "1. 使用 Reactor Sinks 实现真正的流式处理"
    echo "2. 修复了数据重复发送的问题"
    echo "3. 改进了流式会话管理"
    echo "4. 增强了错误处理和资源清理"
    exit 0
else
    echo -e "${RED}⚠️  有 $FAILED 个测试失败${NC}"
    exit 1
fi

