#!/bin/bash

# 🌊 SSE 流式调用完整测试脚本
# 测试 ZK MCP Parent 项目的 Server-Sent Events 功能

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
TEST_TIMEOUT=30

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
    local timeout=${4:-10}
    
    if [ "$method" = "GET" ]; then
        curl -s -m $timeout -X GET "$BASE_URL$url" \
             -H "Accept: application/json" \
             -w "\n%{http_code}"
    else
        curl -s -m $timeout -X $method "$BASE_URL$url" \
             -H "Content-Type: application/json" \
             -H "Accept: application/json" \
             -d "$data" \
             -w "\n%{http_code}"
    fi
}

# SSE 连接测试函数
test_sse_connection() {
    local stream_id=$1
    local timeout=${2:-10}
    
    log_info "测试 SSE 连接: $stream_id"
    
    # 使用 curl 测试 SSE 连接
    timeout $timeout curl -s -N -H "Accept: text/event-stream" \
        "$BASE_URL/mcp/stream/$stream_id" | head -n 5 > /tmp/sse_test_output.txt
    
    if [ $? -eq 0 ] && [ -s /tmp/sse_test_output.txt ]; then
        log_success "SSE 连接测试通过: $stream_id"
        return 0
    else
        log_error "SSE 连接测试失败: $stream_id"
        return 1
    fi
}

# 检查服务状态
check_service_status() {
    log_section "检查服务状态"
    ((TOTAL_TESTS++))
    
    local response=$(make_request "GET" "/mcp/health")
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | head -n1)
    
    if [ "$http_code" = "200" ]; then
        log_success "服务健康检查通过"
        log_info "响应: $body"
    else
        log_error "服务健康检查失败 (HTTP $http_code)"
        exit 1
    fi
}

# 测试创建流式调用
test_create_stream() {
    log_section "测试创建流式调用"
    
    # 测试 1: 基础流式调用创建
    ((TOTAL_TESTS++))
    log_info "测试基础流式调用创建..."
    
    local request_data='{
        "jsonrpc": "2.0",
        "id": "test-stream-1",
        "method": "tools/call",
        "params": {
            "name": "com.zkinfo.demo.service.UserService.getUserById",
            "arguments": {"args": [1]},
            "stream": true
        }
    }'
    
    local response=$(make_request "POST" "/mcp/stream" "$request_data")
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" = "200" ]; then
        local stream_id=$(echo "$body" | jq -r '.streamId // empty')
        if [ -n "$stream_id" ] && [ "$stream_id" != "null" ]; then
            log_success "流式调用创建成功: $stream_id"
            echo "$stream_id" > /tmp/test_stream_id.txt
        else
            log_error "流式调用创建失败: 未返回 streamId"
        fi
    else
        log_error "流式调用创建失败 (HTTP $http_code)"
        log_info "响应: $body"
    fi
    
    # 测试 2: 不同工具的流式调用
    ((TOTAL_TESTS++))
    log_info "测试不同工具的流式调用..."
    
    local tools=(
        "com.zkinfo.demo.service.UserService.getAllUsers"
        "com.zkinfo.demo.service.OrderService.getOrderById"
        "com.zkinfo.demo.service.ProductService.getProductById"
    )
    
    for tool in "${tools[@]}"; do
        local tool_request='{
            "jsonrpc": "2.0",
            "id": "test-tool-'$(date +%s)'",
            "method": "tools/call",
            "params": {
                "name": "'$tool'",
                "arguments": {"args": [1]},
                "stream": true
            }
        }'
        
        local tool_response=$(make_request "POST" "/mcp/stream" "$tool_request")
        local tool_http_code=$(echo "$tool_response" | tail -n1)
        
        if [ "$tool_http_code" = "200" ]; then
            log_success "工具 $tool 流式调用创建成功"
        else
            log_error "工具 $tool 流式调用创建失败"
        fi
    done
}

# 测试 SSE 流式数据传输
test_sse_streaming() {
    log_section "测试 SSE 流式数据传输"
    
    # 测试 1: 基础 SSE 数据接收
    ((TOTAL_TESTS++))
    log_info "测试基础 SSE 数据接收..."
    
    if [ -f /tmp/test_stream_id.txt ]; then
        local stream_id=$(cat /tmp/test_stream_id.txt)
        if test_sse_connection "$stream_id" 15; then
            log_success "SSE 数据接收测试通过"
            
            # 检查接收到的数据格式
            if grep -q "data:" /tmp/sse_test_output.txt; then
                log_success "SSE 数据格式正确"
            else
                log_warning "SSE 数据格式可能有问题"
            fi
        else
            log_error "SSE 数据接收测试失败"
        fi
    else
        log_warning "跳过 SSE 测试: 没有可用的 stream_id"
    fi
    
    # 测试 2: 多个并发 SSE 连接
    ((TOTAL_TESTS++))
    log_info "测试并发 SSE 连接..."
    
    local concurrent_streams=()
    for i in {1..3}; do
        local concurrent_request='{
            "jsonrpc": "2.0",
            "id": "concurrent-'$i'-'$(date +%s)'",
            "method": "tools/call",
            "params": {
                "name": "com.zkinfo.demo.service.UserService.getUserById",
                "arguments": {"args": ['$i']},
                "stream": true
            }
        }'
        
        local concurrent_response=$(make_request "POST" "/mcp/stream" "$concurrent_request")
        local concurrent_http_code=$(echo "$concurrent_response" | tail -n1)
        local concurrent_body=$(echo "$concurrent_response" | head -n -1)
        
        if [ "$concurrent_http_code" = "200" ]; then
            local concurrent_stream_id=$(echo "$concurrent_body" | jq -r '.streamId // empty')
            if [ -n "$concurrent_stream_id" ] && [ "$concurrent_stream_id" != "null" ]; then
                concurrent_streams+=("$concurrent_stream_id")
            fi
        fi
    done
    
    if [ ${#concurrent_streams[@]} -eq 3 ]; then
        log_success "并发流式调用创建成功: ${#concurrent_streams[@]} 个"
        
        # 测试并发 SSE 连接
        local concurrent_success=0
        for stream_id in "${concurrent_streams[@]}"; do
            if test_sse_connection "$stream_id" 10; then
                ((concurrent_success++))
            fi
        done
        
        if [ $concurrent_success -eq 3 ]; then
            log_success "并发 SSE 连接测试全部通过"
        else
            log_warning "并发 SSE 连接部分成功: $concurrent_success/3"
        fi
    else
        log_error "并发流式调用创建失败"
    fi
}

# 测试错误处理
test_error_handling() {
    log_section "测试错误处理"
    
    # 测试 1: 无效的 stream_id
    ((TOTAL_TESTS++))
    log_info "测试无效的 stream_id..."
    
    local invalid_stream_id="invalid_stream_12345"
    timeout 5 curl -s -N -H "Accept: text/event-stream" \
        "$BASE_URL/mcp/stream/$invalid_stream_id" > /tmp/invalid_sse_test.txt 2>&1
    
    if [ $? -eq 124 ] || [ ! -s /tmp/invalid_sse_test.txt ]; then
        log_success "无效 stream_id 错误处理正确"
    else
        log_warning "无效 stream_id 处理可能有问题"
    fi
    
    # 测试 2: 无效的工具名称
    ((TOTAL_TESTS++))
    log_info "测试无效的工具名称..."
    
    local invalid_tool_request='{
        "jsonrpc": "2.0",
        "id": "invalid-tool-test",
        "method": "tools/call",
        "params": {
            "name": "com.invalid.Service.invalidMethod",
            "arguments": {},
            "stream": true
        }
    }'
    
    local invalid_response=$(make_request "POST" "/mcp/stream" "$invalid_tool_request")
    local invalid_http_code=$(echo "$invalid_response" | tail -n1)
    local invalid_body=$(echo "$invalid_response" | head -n -1)
    
    if [ "$invalid_http_code" = "200" ]; then
        # 检查是否返回了错误信息
        if echo "$invalid_body" | jq -e '.error' > /dev/null 2>&1; then
            log_success "无效工具名称错误处理正确"
        else
            log_warning "无效工具名称可能被接受了"
        fi
    else
        log_success "无效工具名称被正确拒绝 (HTTP $invalid_http_code)"
    fi
    
    # 测试 3: 格式错误的请求
    ((TOTAL_TESTS++))
    log_info "测试格式错误的请求..."
    
    local malformed_request='{"invalid": "json", "missing": "required_fields"}'
    local malformed_response=$(make_request "POST" "/mcp/stream" "$malformed_request")
    local malformed_http_code=$(echo "$malformed_response" | tail -n1)
    
    if [ "$malformed_http_code" != "200" ]; then
        log_success "格式错误请求被正确拒绝 (HTTP $malformed_http_code)"
    else
        log_warning "格式错误请求可能被错误接受"
    fi
}

# 测试性能和稳定性
test_performance() {
    log_section "测试性能和稳定性"
    
    # 测试 1: 快速连续请求
    ((TOTAL_TESTS++))
    log_info "测试快速连续请求..."
    
    local rapid_success=0
    for i in {1..5}; do
        local rapid_request='{
            "jsonrpc": "2.0",
            "id": "rapid-'$i'-'$(date +%s)'",
            "method": "tools/call",
            "params": {
                "name": "com.zkinfo.demo.service.UserService.getUserById",
                "arguments": {"args": ['$i']},
                "stream": true
            }
        }'
        
        local rapid_response=$(make_request "POST" "/mcp/stream" "$rapid_request" 5)
        local rapid_http_code=$(echo "$rapid_response" | tail -n1)
        
        if [ "$rapid_http_code" = "200" ]; then
            ((rapid_success++))
        fi
        
        sleep 0.1  # 短暂间隔
    done
    
    if [ $rapid_success -ge 4 ]; then
        log_success "快速连续请求测试通过: $rapid_success/5"
    else
        log_warning "快速连续请求部分失败: $rapid_success/5"
    fi
    
    # 测试 2: 长时间连接
    ((TOTAL_TESTS++))
    log_info "测试长时间 SSE 连接..."
    
    local long_request='{
        "jsonrpc": "2.0",
        "id": "long-connection-test",
        "method": "tools/call",
        "params": {
            "name": "com.zkinfo.demo.service.UserService.getAllUsers",
            "arguments": {},
            "stream": true
        }
    }'
    
    local long_response=$(make_request "POST" "/mcp/stream" "$long_request")
    local long_http_code=$(echo "$long_response" | tail -n1)
    local long_body=$(echo "$long_response" | head -n -1)
    
    if [ "$long_http_code" = "200" ]; then
        local long_stream_id=$(echo "$long_body" | jq -r '.streamId // empty')
        if [ -n "$long_stream_id" ] && [ "$long_stream_id" != "null" ]; then
            # 测试 20 秒的长连接
            timeout 20 curl -s -N -H "Accept: text/event-stream" \
                "$BASE_URL/mcp/stream/$long_stream_id" > /tmp/long_sse_test.txt 2>&1
            
            if [ -s /tmp/long_sse_test.txt ]; then
                log_success "长时间 SSE 连接测试通过"
            else
                log_warning "长时间 SSE 连接可能有问题"
            fi
        else
            log_error "长时间连接测试失败: 无效的 stream_id"
        fi
    else
        log_error "长时间连接创建失败 (HTTP $long_http_code)"
    fi
}

# 测试数据完整性
test_data_integrity() {
    log_section "测试数据完整性"
    
    # 测试 1: Unicode 数据处理
    ((TOTAL_TESTS++))
    log_info "测试 Unicode 数据处理..."
    
    local unicode_request='{
        "jsonrpc": "2.0",
        "id": "unicode-test-测试-🚀",
        "method": "tools/call",
        "params": {
            "name": "com.zkinfo.demo.service.UserService.getUserById",
            "arguments": {"args": [1]},
            "stream": true
        }
    }'
    
    local unicode_response=$(make_request "POST" "/mcp/stream" "$unicode_request")
    local unicode_http_code=$(echo "$unicode_response" | tail -n1)
    
    if [ "$unicode_http_code" = "200" ]; then
        log_success "Unicode 数据处理测试通过"
    else
        log_error "Unicode 数据处理测试失败 (HTTP $unicode_http_code)"
    fi
    
    # 测试 2: 大数据量处理
    ((TOTAL_TESTS++))
    log_info "测试大数据量处理..."
    
    local large_args='{"args": ['
    for i in {1..100}; do
        large_args+="$i"
        if [ $i -lt 100 ]; then
            large_args+=","
        fi
    done
    large_args+=']}'
    
    local large_request='{
        "jsonrpc": "2.0",
        "id": "large-data-test",
        "method": "tools/call",
        "params": {
            "name": "com.zkinfo.demo.service.UserService.getAllUsers",
            "arguments": '$large_args',
            "stream": true
        }
    }'
    
    local large_response=$(make_request "POST" "/mcp/stream" "$large_request" 15)
    local large_http_code=$(echo "$large_response" | tail -n1)
    
    if [ "$large_http_code" = "200" ]; then
        log_success "大数据量处理测试通过"
    else
        log_warning "大数据量处理测试失败 (HTTP $large_http_code)"
    fi
}

# 生成测试报告
generate_report() {
    log_section "测试报告"
    
    echo -e "${CYAN}📊 SSE 流式调用测试结果${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo -e "总测试数: ${BLUE}$TOTAL_TESTS${NC}"
    echo -e "通过测试: ${GREEN}$PASSED_TESTS${NC}"
    echo -e "失败测试: ${RED}$FAILED_TESTS${NC}"
    
    local success_rate=0
    if [ $TOTAL_TESTS -gt 0 ]; then
        success_rate=$((PASSED_TESTS * 100 / TOTAL_TESTS))
    fi
    echo -e "成功率: ${CYAN}$success_rate%${NC}"
    
    echo ""
    if [ $FAILED_TESTS -eq 0 ]; then
        echo -e "${GREEN}🎉 所有 SSE 流式调用测试通过！${NC}"
        exit 0
    else
        echo -e "${YELLOW}⚠️  部分测试失败，请检查服务状态和配置${NC}"
        exit 1
    fi
}

# 清理临时文件
cleanup() {
    rm -f /tmp/test_stream_id.txt
    rm -f /tmp/sse_test_output.txt
    rm -f /tmp/invalid_sse_test.txt
    rm -f /tmp/long_sse_test.txt
}

# 主函数
main() {
    echo -e "${PURPLE}🌊 SSE 流式调用完整测试${NC}"
    echo -e "${PURPLE}================================${NC}"
    echo -e "开始时间: $(date)"
    echo -e "测试目标: $BASE_URL"
    echo -e "超时设置: $TEST_TIMEOUT 秒\n"
    
    # 检查必要的工具
    if ! command -v curl &> /dev/null; then
        log_error "curl 命令未找到，请安装 curl"
        exit 1
    fi
    
    if ! command -v jq &> /dev/null; then
        log_warning "jq 命令未找到，JSON 解析功能受限"
    fi
    
    # 设置清理函数
    trap cleanup EXIT
    
    # 执行测试
    check_service_status
    test_create_stream
    test_sse_streaming
    test_error_handling
    test_performance
    test_data_integrity
    
    # 生成报告
    generate_report
}

# 运行主函数
main "$@"
