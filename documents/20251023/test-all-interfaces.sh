#!/bin/bash

# 测试所有demo-provider接口的综合脚本
# 通过mcp-ai-client调用

set -e

BASE_URL="http://localhost:8081"
BLUE='\033[0;34m'
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}   Demo Provider 接口全面测试 (通过 MCP AI Client)${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

# 检查服务
echo -e "${YELLOW}检查服务状态...${NC}"
if ! curl -s "$BASE_URL/health" > /dev/null 2>&1; then
    echo -e "${RED}❌ AI客户端未运行！${NC}"
    exit 1
fi
echo -e "${GREEN}✅ 所有服务正常${NC}"
echo ""

# 创建新会话
echo -e "${YELLOW}创建测试会话...${NC}"
SESSION_RESPONSE=$(curl -s -X POST "$BASE_URL/api/chat/session")
SESSION_ID=$(echo "$SESSION_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('sessionId', ''))" 2>/dev/null)

if [ -z "$SESSION_ID" ]; then
    echo -e "${RED}❌ 创建会话失败${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 会话ID: $SESSION_ID${NC}"
echo ""
sleep 2

# 辅助函数：发送消息并显示结果
send_test_message() {
    local test_name="$1"
    local message="$2"
    local expected_keyword="$3"
    
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}测试: $test_name${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "问题: ${YELLOW}$message${NC}"
    
    RESPONSE=$(curl -s -X POST "$BASE_URL/api/chat/session/$SESSION_ID/message" \
        -H "Content-Type: application/json" \
        -d "{\"message\": \"$message\"}")
    
    AI_RESPONSE=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('aiResponse', ''))" 2>/dev/null)
    
    if [ -n "$expected_keyword" ]; then
        if [[ "$AI_RESPONSE" == *"$expected_keyword"* ]]; then
            echo -e "${GREEN}✅ 测试通过${NC}"
        else
            echo -e "${RED}❌ 测试失败（未找到关键词: $expected_keyword）${NC}"
        fi
    else
        if [[ "$AI_RESPONSE" == *"执行工具"* ]] || [[ "$AI_RESPONSE" == *"结果"* ]]; then
            echo -e "${GREEN}✅ 测试通过${NC}"
        else
            echo -e "${RED}❌ 测试失败${NC}"
        fi
    fi
    
    # 显示响应的前300个字符
    echo -e "响应: ${AI_RESPONSE:0:300}"
    if [ ${#AI_RESPONSE} -gt 300 ]; then
        echo -e "... (响应太长，已截断)"
    fi
    echo ""
    sleep 1
}

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}   UserService 接口测试${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

send_test_message "1.1 getUserById - 查询单个用户" \
    "查询用户ID为1的信息" \
    "Alice"

send_test_message "1.2 getUserById - 查询不存在的用户" \
    "查询用户ID为999的信息" \
    ""

send_test_message "1.3 getAllUsers - 获取所有用户" \
    "列出所有用户" \
    "["

send_test_message "1.4 getAllUsers - 自然语言查询" \
    "有多少个用户？" \
    ""

send_test_message "1.5 deleteUser - 删除用户" \
    "删除用户ID为3的用户" \
    ""

send_test_message "1.6 deleteUser - 验证删除" \
    "再次查询用户3的信息" \
    ""

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}   ProductService 接口测试${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

send_test_message "2.1 getProductById - 查询单个产品" \
    "查询产品ID为1的信息" \
    ""

send_test_message "2.2 getProductsByCategory - 按分类查询" \
    "查询电子产品类别的所有产品" \
    ""

send_test_message "2.3 searchProducts - 搜索产品" \
    "搜索包含'Phone'关键词的产品" \
    ""

send_test_message "2.4 getPopularProducts - 获取热门产品" \
    "获取前5个热门产品" \
    ""

send_test_message "2.5 updateStock - 更新库存" \
    "将产品1的库存更新为100" \
    ""

send_test_message "2.6 getProductPrice - 获取价格" \
    "查询产品1的价格" \
    ""

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}   OrderService 接口测试${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

send_test_message "3.1 getOrderById - 查询单个订单" \
    "查询订单号为ORD001的订单信息" \
    ""

send_test_message "3.2 getOrdersByUserId - 按用户查询订单" \
    "查询用户1的所有订单" \
    ""

send_test_message "3.3 updateOrderStatus - 更新订单状态" \
    "将订单ORD001的状态更新为已发货" \
    ""

send_test_message "3.4 calculateOrderTotal - 计算订单总额" \
    "计算订单ORD001的总金额" \
    ""

send_test_message "3.5 cancelOrder - 取消订单" \
    "取消订单ORD002" \
    ""

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}   组合查询测试${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

send_test_message "4.1 复杂查询 - 用户和订单关联" \
    "查询用户Alice的所有订单信息" \
    ""

send_test_message "4.2 复杂查询 - 产品库存和价格" \
    "告诉我产品2的价格和库存情况" \
    ""

send_test_message "4.3 自然语言理解" \
    "Bob买了什么东西？" \
    ""

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}   边界条件测试${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

send_test_message "5.1 空参数测试" \
    "获取前0个热门产品" \
    ""

send_test_message "5.2 负数参数测试" \
    "查询用户ID为-1的信息" \
    ""

send_test_message "5.3 超大数字测试" \
    "查询产品ID为99999999的信息" \
    ""

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}   测试总结${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

# 查看会话历史统计
HISTORY=$(curl -s "$BASE_URL/api/chat/session/$SESSION_ID/history")
MESSAGE_COUNT=$(echo "$HISTORY" | python3 -c "import sys, json; print(len(json.load(sys.stdin).get('history', [])))" 2>/dev/null)

echo -e "${GREEN}✅ 测试完成！${NC}"
echo -e "- 会话ID: ${YELLOW}$SESSION_ID${NC}"
echo -e "- 总消息数: ${YELLOW}$MESSAGE_COUNT${NC}"
echo ""

echo -e "${CYAN}可用的服务接口总结:${NC}"
echo -e "  ${YELLOW}UserService (5个方法):${NC}"
echo -e "    - getUserById(Long)"
echo -e "    - getAllUsers()"
echo -e "    - createUser(User)      ${RED}[需要复杂对象，暂不支持]${NC}"
echo -e "    - updateUser(User)      ${RED}[需要复杂对象，暂不支持]${NC}"
echo -e "    - deleteUser(Long)"
echo ""
echo -e "  ${YELLOW}ProductService (6个方法):${NC}"
echo -e "    - getProductById(Long)"
echo -e "    - getProductsByCategory(String)"
echo -e "    - searchProducts(String)"
echo -e "    - getPopularProducts(int)"
echo -e "    - updateStock(Long, int)"
echo -e "    - getProductPrice(Long)"
echo ""
echo -e "  ${YELLOW}OrderService (6个方法):${NC}"
echo -e "    - getOrderById(String)"
echo -e "    - getOrdersByUserId(Long)"
echo -e "    - createOrder(Order)    ${RED}[需要复杂对象，暂不支持]${NC}"
echo -e "    - updateOrderStatus(String, String)"
echo -e "    - cancelOrder(String)"
echo -e "    - calculateOrderTotal(String)"
echo ""

echo -e "${CYAN}测试方式:${NC}"
echo -e "  curl -X POST \"$BASE_URL/api/chat/session/$SESSION_ID/message\" \\"
echo -e "    -H \"Content-Type: application/json\" \\"
echo -e "    -d '{\"message\": \"你的问题\"}'"
echo ""

echo -e "${CYAN}查看完整会话历史:${NC}"
echo -e "  curl \"$BASE_URL/api/chat/session/$SESSION_ID/history\""
echo ""


