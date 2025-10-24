#!/bin/bash

# 🧪 ZK MCP Parent 全功能测试脚本
# 测试所有 API 接口、MCP 协议功能和 Dubbo 服务调用

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 配置
BASE_URL="http://localhost:9091"
ZKINFO_URL="$BASE_URL"
DEMO_PROVIDER_URL="http://localhost:8083"

# 测试结果统计
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 日志函数
log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
    ((PASSED_TESTS++))
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
    ((FAILED_TESTS++))
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_section() {
    echo -e "\n${PURPLE}🔸 $1${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# HTTP 请求函数
make_request() {
    local method=$1
    local url=$2
    local data=$3
    local expected_status=${4:-200}
    
    ((TOTAL_TESTS++))
    
    if [ -n "$data" ]; then
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X "$method" "$url" \
            -H "Content-Type: application/json" \
            -H "Accept: application/json" \
            -d "$data" 2>/dev/null || echo "HTTPSTATUS:000")
    else
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X "$method" "$url" \
            -H "Accept: application/json" 2>/dev/null || echo "HTTPSTATUS:000")
    fi
    
    http_code=$(echo "$response" | grep -o "HTTPSTATUS:[0-9]*" | cut -d: -f2)
    body=$(echo "$response" | sed 's/HTTPSTATUS:[0-9]*$//')
    
    if [ "$http_code" = "$expected_status" ]; then
        log_success "$method $url - Status: $http_code"
        if [ -n "$body" ] && [ "$body" != "null" ] && [ "$body" != "" ]; then
            echo "   Response: $(echo "$body" | jq -c . 2>/dev/null || echo "$body")"
        fi
        return 0
    else
        log_error "$method $url - Expected: $expected_status, Got: $http_code"
        if [ -n "$body" ]; then
            echo "   Response: $body"
        fi
        return 1
    fi
}

# 检查服务状态
check_service_status() {
    log_section "检查服务状态"
    
    log_info "检查 zkInfo 服务状态..."
    if curl -s "$ZKINFO_URL/actuator/health" > /dev/null; then
        log_success "zkInfo 服务运行正常 ($ZKINFO_URL)"
    else
        log_error "zkInfo 服务未运行，请先启动服务"
        exit 1
    fi
    
    log_info "检查 demo-provider 服务状态..."
    if curl -s "$DEMO_PROVIDER_URL/actuator/health" > /dev/null 2>&1; then
        log_success "demo-provider 服务运行正常 ($DEMO_PROVIDER_URL)"
    else
        log_warning "demo-provider 服务未运行，某些测试可能失败"
    fi
}

# 测试基础 API 接口
test_basic_apis() {
    log_section "测试基础 API 接口"
    
    # 应用管理 API
    log_info "测试应用管理 API..."
    make_request "GET" "$BASE_URL/api/applications"
    make_request "GET" "$BASE_URL/api/applications/demo-provider"
    make_request "GET" "$BASE_URL/api/applications/demo-provider/mcp"
    make_request "GET" "$BASE_URL/api/applications/nonexistent" "" 404
    
    # 服务接口 API
    log_info "测试服务接口 API..."
    make_request "GET" "$BASE_URL/api/interfaces"
    make_request "GET" "$BASE_URL/api/interfaces/com.zkinfo.demo.service.UserService/providers"
    
    # 提供者管理 API
    log_info "测试提供者管理 API..."
    make_request "GET" "$BASE_URL/api/providers"
    make_request "GET" "$BASE_URL/api/providers/search?keyword=user"
    
    # MCP 转换 API
    log_info "测试 MCP 转换 API..."
    make_request "GET" "$BASE_URL/api/mcp"
    
    # 系统统计 API
    log_info "测试系统统计 API..."
    make_request "GET" "$BASE_URL/api/stats"
}

# 测试系统监控接口
test_monitoring_apis() {
    log_section "测试系统监控接口"
    
    # 健康检查
    log_info "测试健康检查..."
    make_request "GET" "$BASE_URL/actuator/health"
    make_request "GET" "$BASE_URL/actuator/info"
    make_request "GET" "$BASE_URL/mcp/health"
    make_request "GET" "$BASE_URL/mcp/info"
    make_request "GET" "$BASE_URL/mcp/sessions/count"
}

# 测试 MCP 协议 JSON-RPC 接口
test_mcp_jsonrpc() {
    log_section "测试 MCP 协议 JSON-RPC 接口"
    
    # 初始化 MCP 会话
    log_info "测试 MCP 初始化..."
    init_data='{
        "jsonrpc": "2.0",
        "id": "test-init",
        "method": "initialize",
        "params": {
            "protocolVersion": "2024-11-05",
            "capabilities": {
                "tools": {},
                "resources": {},
                "prompts": {},
                "logging": {}
            },
            "clientInfo": {
                "name": "test-client",
                "version": "1.0.0"
            }
        }
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$init_data"
    
    # 列出所有工具
    log_info "测试列出工具..."
    list_tools_data='{
        "jsonrpc": "2.0",
        "id": "test-list-tools",
        "method": "tools/list",
        "params": {}
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$list_tools_data"
    
    # 调用工具 - 获取用户信息
    log_info "测试调用工具 - 获取用户信息..."
    call_tool_data='{
        "jsonrpc": "2.0",
        "id": "test-call-tool",
        "method": "tools/call",
        "params": {
            "name": "com.zkinfo.demo.service.UserService.getUserById",
            "arguments": {
                "userId": 1
            }
        }
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$call_tool_data"
    
    # Ping 测试
    log_info "测试 Ping..."
    ping_data='{
        "jsonrpc": "2.0",
        "id": "test-ping",
        "method": "ping",
        "params": {}
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$ping_data"
    
    # 测试不存在的方法
    log_info "测试不存在的方法..."
    invalid_method_data='{
        "jsonrpc": "2.0",
        "id": "test-invalid",
        "method": "invalid/method",
        "params": {}
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$invalid_method_data"
}

# 测试 MCP Resources 功能
test_mcp_resources() {
    log_section "测试 MCP Resources 功能"
    
    # 列出所有资源
    log_info "测试列出资源..."
    list_resources_data='{
        "jsonrpc": "2.0",
        "id": "test-list-resources",
        "method": "resources/list",
        "params": {}
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$list_resources_data"
    
    # 读取资源
    log_info "测试读取资源..."
    read_resource_data='{
        "jsonrpc": "2.0",
        "id": "test-read-resource",
        "method": "resources/read",
        "params": {
            "uri": "providers://all"
        }
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$read_resource_data"
    
    # 订阅资源
    log_info "测试订阅资源..."
    subscribe_data='{
        "jsonrpc": "2.0",
        "id": "test-subscribe",
        "method": "resources/subscribe",
        "params": {
            "uri": "providers://all"
        }
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$subscribe_data"
    
    # REST API 方式测试资源
    log_info "测试 REST API 资源接口..."
    make_request "GET" "$BASE_URL/mcp/resources"
    make_request "GET" "$BASE_URL/mcp/resources/providers%3A%2F%2Fall"
}

# 测试 MCP Prompts 功能
test_mcp_prompts() {
    log_section "测试 MCP Prompts 功能"
    
    # 列出所有提示
    log_info "测试列出提示..."
    list_prompts_data='{
        "jsonrpc": "2.0",
        "id": "test-list-prompts",
        "method": "prompts/list",
        "params": {}
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$list_prompts_data"
    
    # 获取提示
    log_info "测试获取提示..."
    get_prompt_data='{
        "jsonrpc": "2.0",
        "id": "test-get-prompt",
        "method": "prompts/get",
        "params": {
            "name": "analyze-service-health",
            "arguments": {
                "serviceName": "com.zkinfo.demo.service.UserService"
            }
        }
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$get_prompt_data"
    
    # REST API 方式测试提示
    log_info "测试 REST API 提示接口..."
    make_request "GET" "$BASE_URL/mcp/prompts"
    
    add_prompt_data='{
        "name": "test-prompt",
        "description": "测试提示",
        "template": "这是一个测试提示模板"
    }'
    make_request "POST" "$BASE_URL/mcp/prompts/add" "$add_prompt_data"
}

# 测试 MCP Logging 功能
test_mcp_logging() {
    log_section "测试 MCP Logging 功能"
    
    # 记录日志
    log_info "测试记录日志..."
    log_message_data='{
        "jsonrpc": "2.0",
        "id": "test-log",
        "method": "logging/setLevel",
        "params": {
            "level": "info"
        }
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$log_message_data"
    
    # REST API 方式测试日志
    log_info "测试 REST API 日志接口..."
    log_data='{
        "level": "info",
        "data": {
            "message": "测试日志消息",
            "source": "comprehensive-test",
            "timestamp": "'$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")'"
        }
    }'
    make_request "POST" "$BASE_URL/mcp/logging/log" "$log_data"
    
    make_request "GET" "$BASE_URL/mcp/logging/messages?level=info&limit=10"
    make_request "GET" "$BASE_URL/mcp/logging/statistics"
}

# 测试 Dubbo 服务调用
test_dubbo_services() {
    log_section "测试 Dubbo 服务调用"
    
    # 用户服务测试
    log_info "测试用户服务..."
    
    # 获取用户信息
    get_user_data='{
        "jsonrpc": "2.0",
        "id": "test-get-user",
        "method": "tools/call",
        "params": {
            "name": "com.zkinfo.demo.service.UserService.getUserById",
            "arguments": {
                "userId": 1
            }
        }
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$get_user_data"
    
    # 获取所有用户
    get_all_users_data='{
        "jsonrpc": "2.0",
        "id": "test-get-all-users",
        "method": "tools/call",
        "params": {
            "name": "com.zkinfo.demo.service.UserService.getAllUsers",
            "arguments": {}
        }
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$get_all_users_data"
    
    # 创建用户
    create_user_data='{
        "jsonrpc": "2.0",
        "id": "test-create-user",
        "method": "tools/call",
        "params": {
            "name": "com.zkinfo.demo.service.UserService.createUser",
            "arguments": {
                "user": {
                    "username": "testuser",
                    "email": "testuser@example.com",
                    "phone": "13800138999",
                    "realName": "Test User",
                    "age": 25,
                    "gender": "M"
                }
            }
        }
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$create_user_data"
    
    # 产品服务测试
    log_info "测试产品服务..."
    
    # 获取产品信息
    get_product_data='{
        "jsonrpc": "2.0",
        "id": "test-get-product",
        "method": "tools/call",
        "params": {
            "name": "com.zkinfo.demo.service.ProductService.getProductById",
            "arguments": {
                "productId": 1
            }
        }
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$get_product_data"
    
    # 搜索产品
    search_products_data='{
        "jsonrpc": "2.0",
        "id": "test-search-products",
        "method": "tools/call",
        "params": {
            "name": "com.zkinfo.demo.service.ProductService.searchProducts",
            "arguments": {
                "keyword": "test"
            }
        }
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$search_products_data"
    
    # 订单服务测试
    log_info "测试订单服务..."
    
    # 获取订单信息
    get_order_data='{
        "jsonrpc": "2.0",
        "id": "test-get-order",
        "method": "tools/call",
        "params": {
            "name": "com.zkinfo.demo.service.OrderService.getOrderById",
            "arguments": {
                "orderId": "ORD-001"
            }
        }
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$get_order_data"
    
    # 根据用户ID获取订单
    get_orders_by_user_data='{
        "jsonrpc": "2.0",
        "id": "test-get-orders-by-user",
        "method": "tools/call",
        "params": {
            "name": "com.zkinfo.demo.service.OrderService.getOrdersByUserId",
            "arguments": {
                "userId": 1
            }
        }
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$get_orders_by_user_data"
}

# 测试流式传输功能
test_streaming() {
    log_section "测试流式传输功能"
    
    # 创建流式调用
    log_info "测试创建流式调用..."
    stream_data='{
        "jsonrpc": "2.0",
        "id": "test-stream",
        "method": "tools/call",
        "params": {
            "name": "com.zkinfo.demo.service.ProductService.searchProducts",
            "arguments": {
                "keyword": "laptop"
            }
        }
    }'
    
    # 创建流式调用并获取 streamId
    response=$(curl -s -X POST "$BASE_URL/mcp/stream" \
        -H "Content-Type: application/json" \
        -d "$stream_data")
    
    if echo "$response" | jq -e '.streamId' > /dev/null 2>&1; then
        stream_id=$(echo "$response" | jq -r '.streamId')
        log_success "创建流式调用成功，streamId: $stream_id"
        
        # 测试 SSE 端点（只测试连接，不等待数据）
        log_info "测试 SSE 流式数据接口..."
        if curl -s --max-time 3 "$BASE_URL/mcp/stream/$stream_id" > /dev/null 2>&1; then
            log_success "SSE 流式数据接口连接成功"
        else
            log_warning "SSE 流式数据接口连接超时或失败"
        fi
    else
        log_error "创建流式调用失败: $response"
    fi
}

# 测试错误处理
test_error_handling() {
    log_section "测试错误处理"
    
    # 测试无效的工具名
    log_info "测试无效工具名..."
    invalid_tool_data='{
        "jsonrpc": "2.0",
        "id": "test-invalid-tool",
        "method": "tools/call",
        "params": {
            "name": "com.invalid.service.InvalidService.invalidMethod",
            "arguments": {}
        }
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$invalid_tool_data"
    
    # 测试无效参数
    log_info "测试无效参数..."
    invalid_args_data='{
        "jsonrpc": "2.0",
        "id": "test-invalid-args",
        "method": "tools/call",
        "params": {
            "name": "com.zkinfo.demo.service.UserService.getUserById",
            "arguments": {
                "userId": "invalid_id"
            }
        }
    }'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$invalid_args_data"
    
    # 测试无效的 JSON-RPC 格式
    log_info "测试无效 JSON-RPC 格式..."
    invalid_json_data='{"invalid": "json"}'
    make_request "POST" "$BASE_URL/mcp/jsonrpc" "$invalid_json_data"
}

# 性能测试
test_performance() {
    log_section "性能测试"
    
    log_info "执行并发调用测试..."
    
    # 创建临时文件存储结果
    temp_file=$(mktemp)
    
    # 并发调用测试
    for i in {1..5}; do
        (
            start_time=$(date +%s%N)
            response=$(curl -s -X POST "$BASE_URL/mcp/jsonrpc" \
                -H "Content-Type: application/json" \
                -d '{
                    "jsonrpc": "2.0",
                    "id": "perf-test-'$i'",
                    "method": "tools/call",
                    "params": {
                        "name": "com.zkinfo.demo.service.UserService.getUserById",
                        "arguments": {"userId": 1}
                    }
                }')
            end_time=$(date +%s%N)
            duration=$(( (end_time - start_time) / 1000000 ))
            echo "Request $i: ${duration}ms" >> "$temp_file"
        ) &
    done
    
    # 等待所有并发请求完成
    wait
    
    # 显示结果
    if [ -f "$temp_file" ]; then
        log_success "并发调用测试完成："
        cat "$temp_file"
        rm "$temp_file"
    fi
}

# 生成测试报告
generate_report() {
    log_section "测试报告"
    
    echo -e "${CYAN}📊 测试统计${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo -e "总测试数: ${BLUE}$TOTAL_TESTS${NC}"
    echo -e "通过测试: ${GREEN}$PASSED_TESTS${NC}"
    echo -e "失败测试: ${RED}$FAILED_TESTS${NC}"
    
    if [ $FAILED_TESTS -eq 0 ]; then
        echo -e "\n${GREEN}🎉 所有测试通过！${NC}"
        exit 0
    else
        echo -e "\n${RED}❌ 有 $FAILED_TESTS 个测试失败${NC}"
        exit 1
    fi
}

# 主函数
main() {
    echo -e "${PURPLE}🚀 ZK MCP Parent 全功能测试${NC}"
    echo -e "${PURPLE}================================${NC}"
    echo -e "开始时间: $(date)"
    echo -e "测试目标: $BASE_URL\n"
    
    # 检查必要的工具
    if ! command -v curl &> /dev/null; then
        log_error "curl 命令未找到，请安装 curl"
        exit 1
    fi
    
    if ! command -v jq &> /dev/null; then
        log_warning "jq 命令未找到，JSON 格式化将被跳过"
    fi
    
    # 执行测试
    check_service_status
    test_basic_apis
    test_monitoring_apis
    test_mcp_jsonrpc
    test_mcp_resources
    test_mcp_prompts
    test_mcp_logging
    test_dubbo_services
    test_streaming
    test_error_handling
    test_performance
    
    # 生成报告
    generate_report
}

# 运行主函数
main "$@"


