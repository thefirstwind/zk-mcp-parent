#!/bin/bash

################################################################################
# ZK-MCP 测试脚本
# 
# 功能: 只负责测试，不启动/停止服务
#
# 用法:
#   ./run-and-test.sh [选项]
#
# 选项:
#   test-basic   - 运行基础测试 (默认)
#   test-full    - 运行完整测试
#   help         - 显示帮助信息
#
# 注意: 请先手动启动所有服务
################################################################################

PROJECT_ROOT="/Users/shine/projects/zk-mcp-parent"
BASE_URL="http://localhost:8081"
MCP_URL="http://localhost:9091"
DEMO_URL="http://localhost:8083"

# 颜色定义
BLUE='\033[0;34m'
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# 全局变量
SESSION_ID=""
PASSED_TESTS=0
FAILED_TESTS=0
TOTAL_TESTS=0

# ==================== 工具函数 ====================

log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_section() {
    echo ""
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC} $1"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

# ==================== 测试函数 ====================

# 不再检查服务状态，直接测试

# 发送测试消息
send_test_message() {
    local test_name="$1"
    local message="$2"
    local expected_keyword="$3"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}测试 $TOTAL_TESTS: $test_name${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "问题: ${YELLOW}$message${NC}"
    
    local response=$(curl -s -X POST "$BASE_URL/api/chat/session/$SESSION_ID/message" \
        -H "Content-Type: application/json" \
        -d "{\"message\": \"$message\"}" 2>/dev/null)
    
    local ai_response=$(echo "$response" | python3 -c "import sys, json; print(json.load(sys.stdin).get('aiResponse', ''))" 2>/dev/null || echo "")
    
    # 验证结果
    local test_passed=false
    if [ -n "$expected_keyword" ]; then
        if [[ "$ai_response" == *"$expected_keyword"* ]]; then
            test_passed=true
        fi
    else
        if [ -n "$ai_response" ] && [ "$ai_response" != "null" ]; then
            test_passed=true
        fi
    fi
    
    if [ "$test_passed" = true ]; then
        log_success "测试通过"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        log_error "测试失败"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
    
    # 显示响应
    echo -e "响应: ${ai_response:0:300}"
    if [ ${#ai_response} -gt 300 ]; then
        echo -e "... (响应太长，已截断)"
    fi
    echo ""
    sleep 1
}

# 基础测试
run_basic_tests() {
    log_section "🧪 运行基础功能测试"
    
    # 创建会话
    log_info "创建测试会话..."
    SESSION_RESPONSE=$(curl -s -X POST "$BASE_URL/api/chat/session")
    SESSION_ID=$(echo "$SESSION_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('sessionId', ''))" 2>/dev/null)
    
    if [ -z "$SESSION_ID" ]; then
        log_error "创建会话失败"
        return 1
    fi
    
    log_success "会话已创建: $SESSION_ID"
    sleep 2
    echo ""
    
    # 基础测试用例
    send_test_message "查询单个用户" "查询用户ID为1的信息" "Alice"
    send_test_message "查询所有用户" "列出所有用户" "["
    send_test_message "自然语言查询" "用户2是谁？" "Bob"
    
    # 查看会话历史
    log_info "查看会话历史..."
    HISTORY=$(curl -s "$BASE_URL/api/chat/session/$SESSION_ID/history")
    MESSAGE_COUNT=$(echo "$HISTORY" | python3 -c "import sys, json; print(len(json.load(sys.stdin).get('history', [])))" 2>/dev/null || echo "0")
    log_success "会话中共有 $MESSAGE_COUNT 条消息"
}

# 完整测试
run_full_tests() {
    log_section "🧪 运行完整接口测试"
    
    # 创建会话
    log_info "创建测试会话..."
    SESSION_RESPONSE=$(curl -s -X POST "$BASE_URL/api/chat/session")
    SESSION_ID=$(echo "$SESSION_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('sessionId', ''))" 2>/dev/null)
    
    if [ -z "$SESSION_ID" ]; then
        log_error "创建会话失败"
        return 1
    fi
    
    log_success "会话已创建: $SESSION_ID"
    sleep 2
    echo ""
    
    # UserService 测试
    log_section "UserService 接口测试"
    send_test_message "getUserById - 查询单个用户" "查询用户ID为1的信息" "Alice"
    send_test_message "getUserById - 查询不存在的用户" "查询用户ID为999的信息" ""
    send_test_message "getAllUsers - 获取所有用户" "列出所有用户" "["
    send_test_message "getAllUsers - 自然语言查询" "有多少个用户？" ""
    send_test_message "deleteUser - 删除用户" "删除用户ID为3的用户" ""
    send_test_message "deleteUser - 验证删除" "再次查询用户3的信息" ""
    
    # ProductService 测试
    log_section "ProductService 接口测试"
    send_test_message "getProductById - 查询单个产品" "查询产品ID为1的信息" ""
    send_test_message "getProductsByCategory - 按分类查询" "查询电子产品类别的所有产品" ""
    send_test_message "searchProducts - 搜索产品" "搜索包含'Phone'关键词的产品" ""
    send_test_message "getPopularProducts - 获取热门产品" "获取前5个热门产品" ""
    send_test_message "updateStock - 更新库存" "将产品1的库存更新为100" ""
    send_test_message "getProductPrice - 获取价格" "查询产品1的价格" ""
    
    # OrderService 测试
    log_section "OrderService 接口测试"
    send_test_message "getOrderById - 查询单个订单" "查询订单号为ORD001的订单信息" ""
    send_test_message "getOrdersByUserId - 按用户查询订单" "查询用户1的所有订单" ""
    send_test_message "updateOrderStatus - 更新订单状态" "将订单ORD001的状态更新为已发货" ""
    send_test_message "calculateOrderTotal - 计算订单总额" "计算订单ORD001的总金额" ""
    send_test_message "cancelOrder - 取消订单" "取消订单ORD002" ""
    
    # 组合查询测试
    log_section "组合查询测试"
    send_test_message "用户和订单关联" "查询用户Alice的所有订单信息" ""
    send_test_message "产品库存和价格" "告诉我产品2的价格和库存情况" ""
    send_test_message "自然语言理解" "Bob买了什么东西？" ""
    
    # 边界条件测试
    log_section "边界条件测试"
    send_test_message "特殊参数测试" "获取前5个热门产品" ""
    send_test_message "负数参数测试" "查询用户ID为-1的信息" ""
    send_test_message "超大数字测试" "查询产品ID为99999999的信息" ""
}

# 测试报告
show_test_summary() {
    log_section "📊 测试报告"
    
    local success_rate=0
    if [ $TOTAL_TESTS -gt 0 ]; then
        success_rate=$((PASSED_TESTS * 100 / TOTAL_TESTS))
    fi
    
    echo -e "${CYAN}测试统计:${NC}"
    echo "  • 总测试数:   $TOTAL_TESTS"
    echo -e "  • 通过数:     ${GREEN}$PASSED_TESTS${NC}"
    echo -e "  • 失败数:     ${RED}$FAILED_TESTS${NC}"
    echo -e "  • 成功率:     ${GREEN}${success_rate}%${NC}"
    echo ""
    
    if [ -n "$SESSION_ID" ]; then
        echo -e "${CYAN}会话信息:${NC}"
        echo "  • 会话ID:     $SESSION_ID"
        echo ""
        
        echo -e "${CYAN}继续测试:${NC}"
        echo "  curl -X POST \"$BASE_URL/api/chat/session/$SESSION_ID/message\" \\"
        echo "    -H \"Content-Type: application/json\" \\"
        echo "    -d '{\"message\": \"你的问题\"}'"
        echo ""
    fi
    
    echo -e "${CYAN}可用服务接口:${NC}"
    echo "  UserService: getUserById(Long), getAllUsers(), deleteUser(Long)"
    echo "  ProductService: getProductById(Long), searchProducts(String), updateStock(Long, int)"
    echo "  OrderService: getOrderById(String), getOrdersByUserId(Long), cancelOrder(String)"
    echo ""
    
    if [ $FAILED_TESTS -gt 0 ]; then
        log_warning "存在失败的测试"
        return 1
    else
        log_success "所有测试通过！"
        return 0
    fi
}

# ==================== 主函数 ====================

show_usage() {
    cat << EOF
用法: $0 [选项]

选项:
  test-basic   - 运行基础测试 (默认)
  test-full    - 运行完整测试
  help         - 显示此帮助信息

示例:
  $0                # 运行基础测试
  $0 test-basic     # 运行基础测试
  $0 test-full      # 运行完整测试

注意:
  请先确保所有服务已启动:
    - demo-provider:  http://localhost:8083
    - zkInfo:         http://localhost:9091
    - mcp-ai-client:  http://localhost:8081

EOF
}

main() {
    local command="${1:-test-basic}"
    
    case "$command" in
        test-basic)
            run_basic_tests
            show_test_summary
            ;;
        test-full)
            run_full_tests
            show_test_summary
            ;;
        help|--help|-h)
            show_usage
            ;;
        *)
            log_error "未知选项: $command"
            show_usage
            exit 1
            ;;
    esac
}

# 执行主函数
cd "$PROJECT_ROOT" || {
    echo "❌ 无法切换到项目根目录: $PROJECT_ROOT"
    exit 1
}

main "$@"
