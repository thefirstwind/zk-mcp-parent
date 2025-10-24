#!/bin/bash

# AI客户端测试脚本
# 用于快速测试MCP AI客户端的功能

set -e

BASE_URL="http://localhost:8081"
BLUE='\033[0;34m'
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   MCP AI Client 测试脚本${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 检查服务是否运行
echo -e "${YELLOW}1. 检查服务状态...${NC}"
if ! curl -s "$BASE_URL/health" > /dev/null 2>&1; then
    echo -e "${RED}❌ AI客户端未运行！请先启动服务${NC}"
    exit 1
fi
echo -e "${GREEN}✅ AI客户端正在运行${NC}"

# 检查MCP服务器
echo -e "${YELLOW}2. 检查MCP服务器连接...${NC}"
MCP_HEALTH=$(curl -s "http://localhost:9091/mcp/health" 2>/dev/null)
if [ -z "$MCP_HEALTH" ]; then
    echo -e "${RED}❌ MCP服务器未运行！${NC}"
    exit 1
fi
echo -e "${GREEN}✅ MCP服务器正常${NC}"
echo ""

# 创建新会话
echo -e "${YELLOW}3. 创建新会话...${NC}"
SESSION_RESPONSE=$(curl -s -X POST "$BASE_URL/api/chat/session")
SESSION_ID=$(echo "$SESSION_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('sessionId', ''))" 2>/dev/null)

if [ -z "$SESSION_ID" ]; then
    echo -e "${RED}❌ 创建会话失败${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 会话已创建: $SESSION_ID${NC}"
echo ""

# 等待工具加载
echo -e "${YELLOW}等待工具列表加载...${NC}"
sleep 2

# 测试1: 查询单个用户
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}测试 1: 查询用户ID为1的信息${NC}"
echo -e "${BLUE}========================================${NC}"

RESPONSE=$(curl -s -X POST "$BASE_URL/api/chat/session/$SESSION_ID/message" \
    -H "Content-Type: application/json" \
    -d '{"message": "查询用户ID为1的信息"}')

AI_RESPONSE=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('aiResponse', ''))" 2>/dev/null)

if [[ "$AI_RESPONSE" == *"Alice"* ]]; then
    echo -e "${GREEN}✅ 测试通过！${NC}"
    echo -e "AI返回: ${AI_RESPONSE:0:200}..."
else
    echo -e "${RED}❌ 测试失败${NC}"
    echo -e "AI返回: $AI_RESPONSE"
fi
echo ""

# 测试2: 查询所有用户
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}测试 2: 查询所有用户${NC}"
echo -e "${BLUE}========================================${NC}"

RESPONSE=$(curl -s -X POST "$BASE_URL/api/chat/session/$SESSION_ID/message" \
    -H "Content-Type: application/json" \
    -d '{"message": "列出所有用户"}')

AI_RESPONSE=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('aiResponse', ''))" 2>/dev/null)

if [[ "$AI_RESPONSE" == *"["* ]] && [[ "$AI_RESPONSE" == *"Alice"* ]]; then
    echo -e "${GREEN}✅ 测试通过！${NC}"
    echo -e "AI返回: 找到多个用户"
else
    echo -e "${RED}❌ 测试失败${NC}"
    echo -e "AI返回: ${AI_RESPONSE:0:200}"
fi
echo ""

# 测试3: 自然语言查询
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}测试 3: 自然语言查询（用户2是谁？）${NC}"
echo -e "${BLUE}========================================${NC}"

RESPONSE=$(curl -s -X POST "$BASE_URL/api/chat/session/$SESSION_ID/message" \
    -H "Content-Type: application/json" \
    -d '{"message": "用户2是谁？"}')

AI_RESPONSE=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('aiResponse', ''))" 2>/dev/null)

if [[ "$AI_RESPONSE" == *"Bob"* ]] || [[ "$AI_RESPONSE" == *"id\":2"* ]]; then
    echo -e "${GREEN}✅ 测试通过！${NC}"
    echo -e "AI返回: ${AI_RESPONSE:0:200}..."
else
    echo -e "${RED}❌ 测试失败${NC}"
    echo -e "AI返回: $AI_RESPONSE"
fi
echo ""

# 查看会话历史
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}查看会话历史${NC}"
echo -e "${BLUE}========================================${NC}"

HISTORY=$(curl -s "$BASE_URL/api/chat/session/$SESSION_ID/history")
MESSAGE_COUNT=$(echo "$HISTORY" | python3 -c "import sys, json; print(len(json.load(sys.stdin).get('history', [])))" 2>/dev/null)

echo -e "${GREEN}会话中共有 $MESSAGE_COUNT 条消息${NC}"
echo ""

# 总结
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}测试完成！${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "你可以使用以下命令继续测试："
echo -e "${YELLOW}curl -X POST \"$BASE_URL/api/chat/session/$SESSION_ID/message\" \\${NC}"
echo -e "${YELLOW}  -H \"Content-Type: application/json\" \\${NC}"
echo -e "${YELLOW}  -d '{\"message\": \"你的问题\"}'${NC}"
echo ""
echo -e "查看Swagger UI: ${YELLOW}http://localhost:8081/swagger-ui.html${NC}"
echo ""


