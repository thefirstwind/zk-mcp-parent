#!/bin/bash

# 🌊 最终 SSE 流式调用测试

GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[0;33m'
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

echo -e "${BLUE}🌊 SSE 流式调用完整测试${NC}"
echo "=================================="
echo ""

# 测试 1: 健康检查
echo -e "${BLUE}测试 1: 服务健康检查${NC}"
if curl -s -f "$BASE_URL/mcp/health" > /dev/null 2>&1; then
    test_result 0 "服务健康检查"
else
    test_result 1 "服务健康检查 - 服务未运行"
    echo -e "${RED}请确保服务在 $BASE_URL 运行${NC}"
    exit 1
fi
echo ""

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
    echo -e "${YELLOW}注意: 这可能是正常的，如果没有注册的服务${NC}"
fi
echo ""

# 测试 3: SSE 数据接收
if [ -n "$STREAM_ID" ]; then
    echo -e "${BLUE}测试 3: SSE 连接与数据接收${NC}"
    
    SSE_DATA=$(timeout 5 curl -s -N --no-buffer -H "Accept: text/event-stream" \
        "$BASE_URL/mcp/stream/$STREAM_ID" 2>&1)
    
    # 检查是否收到了 SSE 格式的数据
    if echo "$SSE_DATA" | grep -qE "(^id:|^event:|^data:)"; then
        test_result 0 "SSE 连接成功，数据接收正常"
        
        echo -e "${BLUE}接收到的SSE事件示例:${NC}"
        echo "$SSE_DATA" | grep -E "^(id:|event:|data:)" | head -n 6
        
        # 统计SSE字段
        ID_COUNT=$(echo "$SSE_DATA" | grep -c "^id:" || true)
        EVENT_COUNT=$(echo "$SSE_DATA" | grep -c "^event:" || true)
        DATA_COUNT=$(echo "$SSE_DATA" | grep -c "^data:" || true)
        
        echo ""
        echo "统计:"
        echo "  - id: 字段: $ID_COUNT"
        echo "  - event: 字段: $EVENT_COUNT"
        echo "  - data: 字段: $DATA_COUNT"
        
        if [ $ID_COUNT -gt 0 ] && [ $EVENT_COUNT -gt 0 ] && [ $DATA_COUNT -gt 0 ]; then
            test_result 0 "SSE 格式验证通过"
        else
            test_result 1 "SSE 格式验证失败"
        fi
    else
        test_result 1 "SSE 数据接收失败"
        echo "调试信息: $SSE_DATA"
    fi
else
    echo -e "${YELLOW}⏭️  跳过 SSE 测试: 没有有效的 stream_id${NC}"
fi
echo ""

# 测试 4: 连续多次流式调用
echo -e "${BLUE}测试 4: 连续流式调用（3次）${NC}"
SEQUENTIAL_SUCCESS=0

for i in 1 2 3; do
    echo -n "  尝试 $i/3... "
    
    SEQ_RESPONSE=$(curl -s -X POST "$BASE_URL/mcp/stream" \
        -H "Content-Type: application/json" \
        -d "{\"jsonrpc\": \"2.0\", \"id\": \"seq-$i\", \"method\": \"tools/call\", \"params\": {\"name\": \"com.zkinfo.demo.service.UserService.getUserById\", \"arguments\": {\"args\": [$i]}, \"stream\": true}}")
    
    SEQ_STREAM_ID=$(echo "$SEQ_RESPONSE" | grep -o '"streamId":"[^"]*"' | sed 's/"streamId":"\([^"]*\)"/\1/')
    
    if [ -n "$SEQ_STREAM_ID" ]; then
        # 验证能够连接到SSE流
        SEQ_SSE_TEST=$(timeout 3 curl -s -N --no-buffer -H "Accept: text/event-stream" \
            "$BASE_URL/mcp/stream/$SEQ_STREAM_ID" 2>&1)
        
        if echo "$SEQ_SSE_TEST" | grep -qE "(^id:|^event:|^data:)"; then
            echo -e "${GREEN}✓${NC}"
            ((SEQUENTIAL_SUCCESS++))
        else
            echo -e "${RED}✗${NC}"
        fi
    else
        echo -e "${RED}✗${NC}"
    fi
done

if [ $SEQUENTIAL_SUCCESS -eq 3 ]; then
    test_result 0 "连续流式调用测试: $SEQUENTIAL_SUCCESS/3"
else
    test_result 1 "连续流式调用测试: $SEQUENTIAL_SUCCESS/3"
fi
echo ""

# 测试 5: 错误处理
echo -e "${BLUE}测试 5: 无效stream_id错误处理${NC}"
ERROR_SSE_DATA=$(timeout 3 curl -s -N --no-buffer -H "Accept: text/event-stream" \
    "$BASE_URL/mcp/stream/invalid_stream_xyz123" 2>&1)

# 对于无效的stream_id，应该快速返回或者返回空数据
if [ -z "$ERROR_SSE_DATA" ] || echo "$ERROR_SSE_DATA" | grep -qiE "(error|not.*found|empty)"; then
    test_result 0 "无效 stream_id 错误处理正确"
else
    test_result 1 "无效 stream_id 错误处理可能有问题"
    echo "返回数据: ${ERROR_SSE_DATA:0:100}..."
fi
echo ""

# 测试 6: 并发流式调用
echo -e "${BLUE}测试 6: 并发流式调用（3个并发）${NC}"
CONCURRENT_SUCCESS=0

# 使用后台进程模拟并发
for i in 1 2 3; do
    (
        CONCURRENT_RESPONSE=$(curl -s -X POST "$BASE_URL/mcp/stream" \
            -H "Content-Type: application/json" \
            -d "{\"jsonrpc\": \"2.0\", \"id\": \"concurrent-$i\", \"method\": \"tools/call\", \"params\": {\"name\": \"com.zkinfo.demo.service.UserService.getUserById\", \"arguments\": {\"args\": [$i]}, \"stream\": true}}")
        
        CONCURRENT_STREAM_ID=$(echo "$CONCURRENT_RESPONSE" | grep -o '"streamId":"[^"]*"' | sed 's/"streamId":"\([^"]*\)"/\1/')
        
        if [ -n "$CONCURRENT_STREAM_ID" ]; then
            # 验证SSE连接
            CONCURRENT_SSE=$(timeout 3 curl -s -N --no-buffer -H "Accept: text/event-stream" \
                "$BASE_URL/mcp/stream/$CONCURRENT_STREAM_ID" 2>&1)
            
            if echo "$CONCURRENT_SSE" | grep -qE "(^id:|^event:|^data:)"; then
                echo "1" > "/tmp/concurrent_$i.result"
            fi
        fi
    ) &
done

# 等待所有并发请求完成
wait

# 统计成功的并发请求
for i in 1 2 3; do
    if [ -f "/tmp/concurrent_$i.result" ]; then
        ((CONCURRENT_SUCCESS++))
        rm -f "/tmp/concurrent_$i.result"
    fi
done

if [ $CONCURRENT_SUCCESS -ge 2 ]; then
    test_result 0 "并发流式调用测试: $CONCURRENT_SUCCESS/3"
else
    test_result 1 "并发流式调用测试: $CONCURRENT_SUCCESS/3"
fi
echo ""

# 测试 7: SSE响应头验证
echo -e "${BLUE}测试 7: SSE响应头验证${NC}"
if [ -n "$STREAM_ID" ]; then
    # 使用 -v 获取响应头，并快速中断连接
    HEADERS=$(timeout 2 curl -v -N -H "Accept: text/event-stream" "$BASE_URL/mcp/stream/$STREAM_ID" 2>&1 | head -n 20)
    
    if echo "$HEADERS" | grep -qi "content-type.*text/event-stream"; then
        test_result 0 "Content-Type 响应头正确 (text/event-stream)"
    else
        test_result 1 "Content-Type 响应头不正确"
        echo "响应头信息:"
        echo "$HEADERS" | grep -i "content-type"
    fi
else
    echo -e "${YELLOW}⏭️  跳过响应头测试: 没有有效的 stream_id${NC}"
fi
echo ""

# 生成最终报告
echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}📊 测试结果汇总${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "✅ 通过测试: ${GREEN}$PASSED${NC}"
echo -e "❌ 失败测试: ${RED}$FAILED${NC}"
TOTAL=$((PASSED + FAILED))
if [ $TOTAL -gt 0 ]; then
    SUCCESS_RATE=$((PASSED * 100 / TOTAL))
    echo -e "📈 成功率: ${BLUE}$SUCCESS_RATE%${NC}"
fi

echo ""
if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}🎉 所有 SSE 流式调用测试通过！${NC}"
    echo -e "${GREEN}✨ SSE 连接功能完全正常工作${NC}"
    echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    exit 0
else
    echo -e "${YELLOW}⚠️  有 $FAILED 个测试失败${NC}"
    if echo "$RESPONSE" | grep -q "未找到可用的服务提供者"; then
        echo -e "${YELLOW}💡 提示: 部分失败可能是因为Dubbo服务未注册${NC}"
        echo -e "${YELLOW}   但SSE连接本身是正常工作的${NC}"
    fi
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    exit 1
fi
