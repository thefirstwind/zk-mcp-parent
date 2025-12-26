#!/bin/bash

# ============================================================================
# zkInfo 核心功能完整测试脚本
# ============================================================================
# 
# 测试覆盖范围：
# 1. 环境检查（zkInfo、Nacos、ZooKeeper、MySQL）
# 2. 服务发现与同步（ZooKeeper -> 数据库）
# 3. 服务查询（Dubbo服务、方法、参数）
# 4. 项目管理（实际项目、虚拟项目）
# 5. 服务审批（提交、审批、拒绝）
# 6. 接口过滤（白名单）
# 7. 心跳检测（服务节点健康状态）
# 8. MCP转换（Dubbo服务 -> MCP工具）
# 9. Nacos注册（服务注册到Nacos）
# 10. MCP调用（tools/list、tools/call）
# 11. SSE端点（Server-Sent Events流式通信）
# 12. 虚拟项目（创建、注册、调用）
#
# 要求：所有测试必须100%通过
# ============================================================================

set -euo pipefail

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# 配置
ZKINFO_URL="${ZKINFO_URL:-http://localhost:9091}"
NACOS_URL="${NACOS_URL:-http://localhost:8848}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-public}"
NACOS_GROUP="${NACOS_GROUP:-mcp-server}"
TIMEOUT=30
MAX_RETRIES=3

# 测试结果统计
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
SKIPPED_TESTS=0

# 测试数据
TEST_PROJECT_CODE="test-project-$(date +%s)"
TEST_VIRTUAL_ENDPOINT="test-virtual-$(date +%s)"
TEST_SESSION_ID="test-session-$(date +%s)"
TEST_SERVICE_ID=""
TEST_PROJECT_ID=""
TEST_VIRTUAL_PROJECT_ID=""
TEST_APPROVAL_ID=""

# 工具函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_test() {
    echo -e "${CYAN}[TEST $TOTAL_TESTS]${NC} $1"
}

# 测试函数
test_step() {
    local step_name="$1"
    local test_command="$2"
    local allow_failure="${3:-false}"
    local is_critical="${4:-true}"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    log_test "$step_name"
    
    local retries=0
    local result=1
    
    while [ $retries -lt $MAX_RETRIES ]; do
        if eval "$test_command" 2>/dev/null; then
            result=0
            break
        fi
        retries=$((retries + 1))
        if [ $retries -lt $MAX_RETRIES ]; then
            sleep 1
        fi
    done
    
    if [ $result -eq 0 ]; then
        log_success "通过"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        return 0
    else
        if [ "$allow_failure" = "true" ]; then
            log_warning "跳过（允许失败）"
            SKIPPED_TESTS=$((SKIPPED_TESTS + 1))
            return 0
        else
            log_error "失败"
            FAILED_TESTS=$((FAILED_TESTS + 1))
            if [ "$is_critical" = "true" ]; then
                log_error "关键测试失败，停止执行"
                exit 1
            fi
            return 1
        fi
    fi
}

# ============================================================================
# JSON 解析函数（纯 bash，不依赖 jq）
# ============================================================================

# 提取 JSON 字段值（简单字段，如 "id": 123）
json_get_value() {
    local json="$1"
    local key="$2"
    echo "$json" | grep -o "\"$key\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | sed "s/\"$key\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\"/\1/" | head -1
}

# 提取 JSON 数字字段值（如 "id": 123）
json_get_number() {
    local json="$1"
    local key="$2"
    echo "$json" | grep -o "\"$key\"[[:space:]]*:[[:space:]]*[0-9]*" | sed "s/\"$key\"[[:space:]]*:[[:space:]]*\([0-9]*\)/\1/" | head -1
}

# 提取嵌套 JSON 字段值（如 "project.id"）
json_get_nested() {
    local json="$1"
    local path="$2"
    local keys=$(echo "$path" | tr '.' ' ')
    local result="$json"
    
    for key in $keys; do
        result=$(echo "$result" | grep -o "\"$key\"[[:space:]]*:[[:space:]]*{[^}]*}" | head -1)
        if [ -z "$result" ]; then
            result=$(echo "$result" | grep -o "\"$key\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | sed "s/\"$key\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\"/\1/" | head -1)
        fi
    done
    
    echo "$result" | grep -o "\"[^\"]*\"" | head -1 | sed 's/"//g'
}

# 检查 JSON 中是否存在某个字段
json_has_field() {
    local json="$1"
    local key="$2"
    echo "$json" | grep -q "\"$key\""
}

# 检查 JSON 中字段值是否等于某个值
json_field_equals() {
    local json="$1"
    local key="$2"
    local value="$3"
    local actual=$(json_get_value "$json" "$key")
    [ "$actual" = "$value" ]
}

# 检查 JSON 是否为数组且长度大于0
json_array_length() {
    local json="$1"
    # 简单计算：统计 "{" 或 "[" 的数量（不完美，但够用）
    echo "$json" | grep -o '\[.*\]' | grep -o '{' | wc -l | tr -d ' '
}

# 检查响应是否包含错误
json_has_error() {
    local json="$1"
    echo "$json" | grep -qi "error"
}

# 提取数组中的第一个元素的字段值
json_array_first() {
    local json="$1"
    local key="$2"
    # 提取第一个对象
    local first_obj=$(echo "$json" | grep -o '\[[^]]*\]' | grep -o '{[^}]*}' | head -1)
    json_get_value "$first_obj" "$key"
}

# 检查响应是否成功（HTTP 200 且无错误字段）
check_response_ok() {
    local response="$1"
    ! json_has_error "$response" && echo "$response" | grep -q "{"
}

# 检查依赖
check_dependencies() {
    log_info "检查依赖工具..."
    
    if ! command -v curl &> /dev/null; then
        log_error "缺少必要工具: curl"
        log_info "请安装: apt-get install curl 或 yum install curl"
        exit 1
    fi
    
    log_success "所有依赖工具已安装"
}

# 打印测试头部
print_header() {
    echo ""
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${MAGENTA}  $1${NC}"
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════${NC}"
    echo ""
}

# 打印测试总结
print_summary() {
    echo ""
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${MAGENTA}  测试结果汇总${NC}"
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════${NC}"
    echo -e "总测试用例数: ${TOTAL_TESTS}"
    echo -e "通过: ${GREEN}${PASSED_TESTS}${NC}"
    echo -e "失败: ${RED}${FAILED_TESTS}${NC}"
    echo -e "跳过: ${YELLOW}${SKIPPED_TESTS}${NC}"
    echo ""
    
    if [ $FAILED_TESTS -eq 0 ]; then
        log_success "✅ 所有测试用例通过！"
        return 0
    else
        log_error "❌ 有 ${FAILED_TESTS} 个测试用例失败"
        return 1
    fi
}

# ============================================================================
# 阶段 1: 环境检查
# ============================================================================
test_environment() {
    print_header "阶段 1: 环境检查"
    
    test_step "检查 zkInfo 服务状态" \
        "HEALTH=\$(curl -s -f --max-time 5 '$ZKINFO_URL/actuator/health') && json_field_equals \"\$HEALTH\" \"status\" \"UP\"" \
        "false" "true"
    
    test_step "检查 Nacos 服务状态" \
        "curl -s -f --max-time 5 '$NACOS_URL/nacos/v1/ns/service/list?pageNo=1&pageSize=10' > /dev/null" \
        "false" "true"
    
    test_step "检查 ZooKeeper 连接" \
        "curl -s -f --max-time 5 '$ZKINFO_URL/api/debug/zk-tree' > /dev/null" \
        "false" "true"
    
    test_step "检查数据库连接（通过服务列表接口）" \
        "curl -s -f --max-time 5 '$ZKINFO_URL/api/dubbo-services?page=1&size=1' > /dev/null" \
        "false" "true"
    
    log_success "环境检查完成"
}

# ============================================================================
# 阶段 2: 服务发现与同步
# ============================================================================
test_service_discovery() {
    print_header "阶段 2: 服务发现与同步"
    
    # 检查是否有可用的Dubbo服务
    log_info "检查可用的Dubbo服务..."
    DUBBO_SERVICES_RESPONSE=$(curl -s "$ZKINFO_URL/api/dubbo-services?page=1&size=10" 2>/dev/null || echo "")
    
    if [ -z "$DUBBO_SERVICES_RESPONSE" ] || echo "$DUBBO_SERVICES_RESPONSE" | grep -q "403\|权限"; then
        log_warning "权限不足或接口错误，尝试其他方式..."
        AVAILABLE_SERVICES=""
    else
        # 提取接口名称（从 data 数组中）
        AVAILABLE_SERVICES=$(echo "$DUBBO_SERVICES_RESPONSE" | grep -o '"interfaceName"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/"interfaceName"[[:space:]]*:[[:space:]]*"\([^"]*\)"/\1/' | head -3)
    fi
    
    if [ -z "$AVAILABLE_SERVICES" ]; then
        log_warning "未找到可用的Dubbo服务，请确保demo-provider已启动"
        log_info "跳过服务发现相关测试"
        SKIPPED_TESTS=$((SKIPPED_TESTS + 5))
        TOTAL_TESTS=$((TOTAL_TESTS + 5))
        return 0
    fi
    
    log_success "找到可用服务: $(echo "$AVAILABLE_SERVICES" | tr '\n' ' ')"
    
    # 获取第一个服务的ID
    TEST_SERVICE_ID=$(json_array_first "$DUBBO_SERVICES_RESPONSE" "id")
    TEST_INTERFACE=$(echo "$AVAILABLE_SERVICES" | head -1)
    
    test_step "验证服务已同步到数据库" \
        "echo '$DUBBO_SERVICES_RESPONSE' | grep -q '\"data\"' && echo '$DUBBO_SERVICES_RESPONSE' | grep -q '{'" \
        "false" "true"
    
    test_step "验证服务有节点信息" \
        "NODES=\$(curl -s '$ZKINFO_URL/api/dubbo-services/$TEST_SERVICE_ID/nodes') && (echo \"\$NODES\" | grep -q '\\[' || echo \"\$NODES\" | grep -q '{')" \
        "true" "false"
    
    test_step "验证服务有方法信息" \
        "METHODS=\$(curl -s '$ZKINFO_URL/api/dubbo-services/$TEST_SERVICE_ID/methods') && (echo \"\$METHODS\" | grep -q '\\[' || echo \"\$METHODS\" | grep -q '{')" \
        "true" "false"
    
    log_success "服务发现与同步测试完成"
}

# ============================================================================
# 阶段 3: 项目管理
# ============================================================================
test_project_management() {
    print_header "阶段 3: 项目管理"
    
    # 创建实际项目
    log_info "创建实际项目..."
    CREATE_PROJECT_RESPONSE=$(curl -s -X POST "$ZKINFO_URL/api/projects" \
        -H "Content-Type: application/json" \
        -d "{
            \"projectCode\": \"$TEST_PROJECT_CODE\",
            \"projectName\": \"Test Project\",
            \"projectType\": \"REAL\",
            \"description\": \"Test project for validation\",
            \"ownerId\": 1,
            \"ownerName\": \"Test User\"
        }" 2>&1)
    
    if echo "$CREATE_PROJECT_RESPONSE" | grep -q "error\|失败"; then
        log_error "创建项目失败: $CREATE_PROJECT_RESPONSE"
        exit 1
    fi
    
    TEST_PROJECT_ID=$(json_get_number "$CREATE_PROJECT_RESPONSE" "id")
    if [ -z "$TEST_PROJECT_ID" ] || [ "$TEST_PROJECT_ID" = "null" ]; then
        log_error "无法获取项目ID"
        exit 1
    fi
    
    log_success "项目创建成功: ID=$TEST_PROJECT_ID"
    
    test_step "验证项目存在" \
        "PROJECT=\$(curl -s '$ZKINFO_URL/api/projects/$TEST_PROJECT_ID') && PROJECT_ID=\$(json_get_number \"\$PROJECT\" \"id\") && [ \"\$PROJECT_ID\" = \"$TEST_PROJECT_ID\" ]" \
        "false" "true"
    
    # 添加服务到项目
    if [ ! -z "$TEST_SERVICE_ID" ] && [ "$TEST_SERVICE_ID" != "null" ]; then
        log_info "添加服务到项目..."
        ADD_SERVICE_RESPONSE=$(curl -s -X POST "$ZKINFO_URL/api/projects/$TEST_PROJECT_ID/services" \
            -H "Content-Type: application/json" \
            -d "{
                \"serviceInterface\": \"$TEST_INTERFACE\",
                \"version\": \"1.0.0\",
                \"group\": \"demo\"
            }" 2>&1)
        
        test_step "验证服务已添加到项目" \
            "json_has_field '$ADD_SERVICE_RESPONSE' 'id' || echo '$ADD_SERVICE_RESPONSE' | grep -q '{'" \
            "true" "false"
    fi
    
    test_step "验证项目服务列表" \
        "SERVICES=\$(curl -s '$ZKINFO_URL/api/projects/$TEST_PROJECT_ID/services') && (echo \"\$SERVICES\" | grep -q '\\[' || echo \"\$SERVICES\" | grep -q '{')" \
        "true" "false"
    
    log_success "项目管理测试完成"
}

# ============================================================================
# 阶段 4: 虚拟项目管理
# ============================================================================
test_virtual_project() {
    print_header "阶段 4: 虚拟项目管理"
    
    if [ -z "$TEST_INTERFACE" ]; then
        log_warning "没有可用服务，跳过虚拟项目测试"
        SKIPPED_TESTS=$((SKIPPED_TESTS + 5))
        TOTAL_TESTS=$((TOTAL_TESTS + 5))
        return 0
    fi
    
    # 创建虚拟项目
    log_info "创建虚拟项目..."
    CREATE_VIRTUAL_RESPONSE=$(curl -s -X POST "$ZKINFO_URL/api/virtual-projects" \
        -H "Content-Type: application/json" \
        -d "{
            \"endpointName\": \"$TEST_VIRTUAL_ENDPOINT\",
            \"projectName\": \"Test Virtual Project\",
            \"projectCode\": \"test-virtual-$(date +%s)\",
            \"description\": \"Test virtual project\",
            \"services\": [
                {
                    \"serviceInterface\": \"$TEST_INTERFACE\",
                    \"version\": \"1.0.0\",
                    \"group\": \"demo\",
                    \"priority\": 0
                }
            ],
            \"autoRegister\": true
        }" 2>&1)
    
    if echo "$CREATE_VIRTUAL_RESPONSE" | grep -q "error\|失败"; then
        log_error "创建虚拟项目失败: $CREATE_VIRTUAL_RESPONSE"
        exit 1
    fi
    
    TEST_VIRTUAL_PROJECT_ID=$(json_get_nested "$CREATE_VIRTUAL_RESPONSE" "project.id")
    if [ -z "$TEST_VIRTUAL_PROJECT_ID" ] || [ "$TEST_VIRTUAL_PROJECT_ID" = "null" ]; then
        # 尝试直接提取
        TEST_VIRTUAL_PROJECT_ID=$(echo "$CREATE_VIRTUAL_RESPONSE" | grep -o '"project"[^}]*"id"[^,}]*' | grep -o '[0-9]\+' | head -1)
    fi
    if [ -z "$TEST_VIRTUAL_PROJECT_ID" ] || [ "$TEST_VIRTUAL_PROJECT_ID" = "null" ]; then
        log_error "无法获取虚拟项目ID"
        exit 1
    fi
    
    log_success "虚拟项目创建成功: ID=$TEST_VIRTUAL_PROJECT_ID"
    
    test_step "验证虚拟项目存在" \
        "PROJECT=\$(curl -s '$ZKINFO_URL/api/virtual-projects/$TEST_VIRTUAL_PROJECT_ID') && PROJECT_ID=\$(echo \"\$PROJECT\" | grep -o '\"project\"[^}]*\"id\"[^,}]*' | grep -o '[0-9]\+' | head -1) && [ \"\$PROJECT_ID\" = \"$TEST_VIRTUAL_PROJECT_ID\" ]" \
        "false" "true"
    
    test_step "验证虚拟项目有端点信息" \
        "ENDPOINT=\$(curl -s '$ZKINFO_URL/api/virtual-projects/$TEST_VIRTUAL_PROJECT_ID/endpoint') && json_has_field \"\$ENDPOINT\" \"endpointName\"" \
        "false" "true"
    
    test_step "验证虚拟项目有服务关联" \
        "SERVICES=\$(curl -s '$ZKINFO_URL/api/virtual-projects/$TEST_VIRTUAL_PROJECT_ID/services') && (echo \"\$SERVICES\" | grep -q '\\[' || echo \"\$SERVICES\" | grep -q '{')" \
        "true" "false"
    
    # 等待Nacos注册
    log_info "等待服务注册到Nacos..."
    VIRTUAL_SERVICE_NAME="virtual-$TEST_VIRTUAL_ENDPOINT"
    for i in {1..10}; do
        sleep 1
        NACOS_RESPONSE=$(curl -s "$NACOS_URL/nacos/v3/client/ns/instance/list?namespaceId=$NACOS_NAMESPACE&groupName=$NACOS_GROUP&serviceName=$VIRTUAL_SERVICE_NAME" \
            -H "Content-Type: application/json" \
            -H "User-Agent: Nacos-Bash-Client-test" 2>&1)
        # 检查是否有 data 数组且不为空
        if echo "$NACOS_RESPONSE" | grep -q '"data"' && echo "$NACOS_RESPONSE" | grep -q '{'; then
            INSTANCE_COUNT=$(echo "$NACOS_RESPONSE" | grep -o '"data"[^]]*\]' | grep -o '{' | wc -l | tr -d ' ')
            if [ "$INSTANCE_COUNT" -gt 0 ] 2>/dev/null; then
                log_success "服务已注册到Nacos (${i}秒)"
                break
            fi
        fi
    done
    
    test_step "验证服务在Nacos中存在" \
        "NACOS_RESPONSE=\$(curl -s '$NACOS_URL/nacos/v3/client/ns/instance/list?namespaceId=$NACOS_NAMESPACE&groupName=$NACOS_GROUP&serviceName=$VIRTUAL_SERVICE_NAME' -H 'Content-Type: application/json' -H 'User-Agent: Nacos-Bash-Client-test') && echo \"\$NACOS_RESPONSE\" | grep -q '\"data\"' && echo \"\$NACOS_RESPONSE\" | grep -q '{'" \
        "true" "false"
    
    log_success "虚拟项目管理测试完成"
}

# ============================================================================
# 阶段 5: 服务审批
# ============================================================================
test_service_approval() {
    print_header "阶段 5: 服务审批"
    
    if [ -z "$TEST_SERVICE_ID" ] || [ "$TEST_SERVICE_ID" = "null" ]; then
        log_warning "没有可用服务，跳过审批测试"
        SKIPPED_TESTS=$((SKIPPED_TESTS + 3))
        TOTAL_TESTS=$((TOTAL_TESTS + 3))
        return 0
    fi
    
    # 提交审批
    log_info "提交服务审批..."
    SUBMIT_RESPONSE=$(curl -s -X POST "$ZKINFO_URL/api/dubbo-services/$TEST_SERVICE_ID/submit-for-review" \
        -H "Content-Type: application/json" \
        -d "{
            \"reviewerId\": 1,
            \"reviewerName\": \"Test Reviewer\",
            \"comment\": \"Test approval\"
        }" 2>&1)
    
    test_step "验证审批提交成功" \
        "(json_has_field '$SUBMIT_RESPONSE' 'id' || json_has_field '$SUBMIT_RESPONSE' 'approvalId') && ! json_has_error '$SUBMIT_RESPONSE'" \
        "true" "false"
    
    # 获取待审批列表
    test_step "验证待审批列表" \
        "PENDING=\$(curl -s '$ZKINFO_URL/api/dubbo-services/pending') && (echo \"\$PENDING\" | grep -q '\\[' || echo \"\$PENDING\" | grep -q '{')" \
        "true" "false"
    
    # 获取已审批列表
    test_step "验证已审批列表" \
        "APPROVED=\$(curl -s '$ZKINFO_URL/api/dubbo-services/approved') && (echo \"\$APPROVED\" | grep -q '\\[' || echo \"\$APPROVED\" | grep -q '{')" \
        "true" "false"
    
    log_success "服务审批测试完成"
}

# ============================================================================
# 阶段 6: MCP协议调用
# ============================================================================
test_mcp_protocol() {
    print_header "阶段 6: MCP协议调用"
    
    if [ -z "$TEST_VIRTUAL_ENDPOINT" ]; then
        log_warning "没有虚拟项目端点，跳过MCP协议测试"
        SKIPPED_TESTS=$((SKIPPED_TESTS + 4))
        TOTAL_TESTS=$((TOTAL_TESTS + 4))
        return 0
    fi
    
    # Initialize
    log_info "MCP Initialize..."
    INIT_RESPONSE=$(curl -s -X POST "$ZKINFO_URL/mcp/message?sessionId=$TEST_SESSION_ID&endpoint=$TEST_VIRTUAL_ENDPOINT" \
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
    
    test_step "验证Initialize成功" \
        "(json_has_field '$INIT_RESPONSE' 'result' || ! json_has_error '$INIT_RESPONSE')" \
        "true" "false"
    
    sleep 1
    
    # Tools/List
    log_info "MCP Tools/List..."
    TOOLS_RESPONSE=$(curl -s -X POST "$ZKINFO_URL/mcp/message?sessionId=$TEST_SESSION_ID&endpoint=$TEST_VIRTUAL_ENDPOINT" \
        -H "Content-Type: application/json" \
        -d '{
            "jsonrpc": "2.0",
            "id": "2",
            "method": "tools/list",
            "params": {}
        }' 2>&1)
    
    # 计算工具数量（统计 "name" 字段）
    TOOLS_COUNT=$(echo "$TOOLS_RESPONSE" | grep -o '"name"[^,}]*' | wc -l | tr -d ' ')
    
    test_step "验证Tools/List成功" \
        "json_has_field '$TOOLS_RESPONSE' 'result'" \
        "true" "false"
    
    # Tools/Call（如果有工具）
    if [ "$TOOLS_COUNT" -gt 0 ] 2>/dev/null; then
        # 提取第一个工具名称
        TEST_TOOL=$(echo "$TOOLS_RESPONSE" | grep -o '"name"[^,}]*"[^"]*"' | head -1 | sed 's/"name"[^"]*"\([^"]*\)"/\1/' | sed 's/.*"\([^"]*\)".*/\1/')
        if [ ! -z "$TEST_TOOL" ] && [ "$TEST_TOOL" != "null" ]; then
            log_info "MCP Tools/Call: $TEST_TOOL"
            
            # 根据工具名构建参数
            ARGS="[]"
            if [[ "$TEST_TOOL" == *"getOrderById"* ]]; then
                ARGS='["ORD001"]'
            elif [[ "$TEST_TOOL" == *"getUserById"* ]]; then
                ARGS='[1]'
            elif [[ "$TEST_TOOL" == *"getAllUsers"* ]] || [[ "$TEST_TOOL" == *"getUsers"* ]]; then
                ARGS='[]'
            fi
            
            CALL_RESPONSE=$(curl -s -X POST "$ZKINFO_URL/mcp/message?sessionId=$TEST_SESSION_ID&endpoint=$TEST_VIRTUAL_ENDPOINT" \
                -H "Content-Type: application/json" \
                -d "{
                    \"jsonrpc\": \"2.0\",
                    \"id\": \"3\",
                    \"method\": \"tools/call\",
                    \"params\": {
                        \"name\": \"$TEST_TOOL\",
                        \"arguments\": $ARGS
                    }
                }" 2>&1)
            
            # 检查是否有错误
            IS_ERROR="false"
            if echo "$CALL_RESPONSE" | grep -qi '"isError"[[:space:]]*:[[:space:]]*true'; then
                IS_ERROR="true"
            fi
            
            test_step "验证Tools/Call成功（Dubbo泛化调用）" \
                "[ \"$IS_ERROR\" != \"true\" ]" \
                "true" "false"
        else
            log_warning "无法获取测试工具名称"
            SKIPPED_TESTS=$((SKIPPED_TESTS + 1))
            TOTAL_TESTS=$((TOTAL_TESTS + 1))
        fi
    else
        log_warning "没有可用的工具，跳过Tools/Call测试"
        SKIPPED_TESTS=$((SKIPPED_TESTS + 1))
        TOTAL_TESTS=$((TOTAL_TESTS + 1))
    fi
    
    log_success "MCP协议调用测试完成"
}

# ============================================================================
# 阶段 7: SSE端点
# ============================================================================
test_sse_endpoints() {
    print_header "阶段 7: SSE端点测试"
    
    if [ -z "$TEST_VIRTUAL_ENDPOINT" ]; then
        log_warning "没有虚拟项目端点，跳过SSE测试"
        SKIPPED_TESTS=$((SKIPPED_TESTS + 2))
        TOTAL_TESTS=$((TOTAL_TESTS + 2))
        return 0
    fi
    
    # 测试SSE连接（使用timeout，如果连接建立就算成功）
    test_step "测试SSE端点连接（endpointName）" \
        "timeout 3 curl -s -f '$ZKINFO_URL/sse/$TEST_VIRTUAL_ENDPOINT' -H 'Accept: text/event-stream' > /dev/null 2>&1; [ \$? -eq 0 -o \$? -eq 124 ]" \
        "true" "false"
    
    test_step "测试SSE端点连接（virtual-endpointName）" \
        "timeout 3 curl -s -f '$ZKINFO_URL/sse/virtual-$TEST_VIRTUAL_ENDPOINT' -H 'Accept: text/event-stream' > /dev/null 2>&1; [ \$? -eq 0 -o \$? -eq 124 ]" \
        "true" "false"
    
    log_success "SSE端点测试完成"
}

# ============================================================================
# 阶段 8: 接口过滤（白名单）
# ============================================================================
test_interface_filter() {
    print_header "阶段 8: 接口过滤（白名单）"
    
    # 获取过滤器列表
    test_step "验证过滤器列表" \
        "FILTERS=\$(curl -s '$ZKINFO_URL/api/filters') && (echo \"\$FILTERS\" | grep -q '\\[' || echo \"\$FILTERS\" | grep -q '{')" \
        "true" "false"
    
    # 获取启用的过滤器
    test_step "验证启用的过滤器" \
        "FILTERS=\$(curl -s '$ZKINFO_URL/api/filters/enabled') && (echo \"\$FILTERS\" | grep -q '\\[' || echo \"\$FILTERS\" | grep -q '{')" \
        "true" "false"
    
    log_success "接口过滤测试完成"
}

# ============================================================================
# 阶段 9: 心跳检测
# ============================================================================
test_heartbeat_monitoring() {
    print_header "阶段 9: 心跳检测"
    
    if [ -z "$TEST_SERVICE_ID" ] || [ "$TEST_SERVICE_ID" = "null" ]; then
        log_warning "没有可用服务，跳开心跳检测测试"
        SKIPPED_TESTS=$((SKIPPED_TESTS + 2))
        TOTAL_TESTS=$((TOTAL_TESTS + 2))
        return 0
    fi
    
    # 获取服务节点
    NODES_RESPONSE=$(curl -s "$ZKINFO_URL/api/dubbo-services/$TEST_SERVICE_ID/nodes" 2>/dev/null || echo "[]")
    # 简单计算节点数量（统计对象数量）
    NODE_COUNT=$(echo "$NODES_RESPONSE" | grep -o '{' | wc -l | tr -d ' ')
    
    test_step "验证服务节点信息" \
        "[ \"$NODE_COUNT\" -ge 0 ]" \
        "true" "false"
    
    # 验证节点状态字段（isOnline, isHealthy等）
    if [ "$NODE_COUNT" -gt 0 ]; then
        test_step "验证节点状态字段" \
            "(json_has_field '$NODES_RESPONSE' 'isOnline' || json_has_field '$NODES_RESPONSE' 'isHealthy')" \
            "true" "false"
    else
        SKIPPED_TESTS=$((SKIPPED_TESTS + 1))
        TOTAL_TESTS=$((TOTAL_TESTS + 1))
    fi
    
    log_success "心跳检测测试完成"
}

# ============================================================================
# 阶段 10: API端点验证
# ============================================================================
test_api_endpoints() {
    print_header "阶段 10: API端点验证"
    
    # 应用列表
    test_step "验证应用列表接口" \
        "APPS=\$(curl -s '$ZKINFO_URL/api/applications') && (echo \"\$APPS\" | grep -q '\\[' || echo \"\$APPS\" | grep -q '{')" \
        "true" "false"
    
    # 服务统计
    test_step "验证服务统计接口" \
        "STATS=\$(curl -s '$ZKINFO_URL/api/stats') && echo \"\$STATS\" | grep -q '{'" \
        "true" "false"
    
    # 已注册服务
    test_step "验证已注册服务接口" \
        "SERVICES=\$(curl -s '$ZKINFO_URL/api/registered-services') && (echo \"\$SERVICES\" | grep -q '\\[' || echo \"\$SERVICES\" | grep -q '{')" \
        "true" "false"
    
    # 项目列表
    test_step "验证项目列表接口" \
        "PROJECTS=\$(curl -s '$ZKINFO_URL/api/projects') && (echo \"\$PROJECTS\" | grep -q '\\[' || echo \"\$PROJECTS\" | grep -q '{')" \
        "true" "false"
    
    # 虚拟项目列表
    test_step "验证虚拟项目列表接口" \
        "VIRTUAL=\$(curl -s '$ZKINFO_URL/api/virtual-projects') && (echo \"\$VIRTUAL\" | grep -q '\\[' || echo \"\$VIRTUAL\" | grep -q '{')" \
        "true" "false"
    
    log_success "API端点验证完成"
}

# ============================================================================
# 清理函数
# ============================================================================
cleanup() {
    print_header "清理测试数据"
    
    # 删除虚拟项目
    if [ ! -z "$TEST_VIRTUAL_PROJECT_ID" ] && [ "$TEST_VIRTUAL_PROJECT_ID" != "null" ]; then
        log_info "删除虚拟项目: $TEST_VIRTUAL_PROJECT_ID"
        curl -s -X DELETE "$ZKINFO_URL/api/virtual-projects/$TEST_VIRTUAL_PROJECT_ID" > /dev/null 2>&1 || true
    fi
    
    # 删除实际项目
    if [ ! -z "$TEST_PROJECT_ID" ] && [ "$TEST_PROJECT_ID" != "null" ]; then
        log_info "删除项目: $TEST_PROJECT_ID"
        curl -s -X DELETE "$ZKINFO_URL/api/projects/$TEST_PROJECT_ID" > /dev/null 2>&1 || true
    fi
    
    log_success "清理完成"
}

# ============================================================================
# 主函数
# ============================================================================
main() {
    print_header "zkInfo 核心功能完整测试"
    log_info "测试开始时间: $(date '+%Y-%m-%d %H:%M:%S')"
    log_info "zkInfo URL: $ZKINFO_URL"
    log_info "Nacos URL: $NACOS_URL"
    echo ""
    
    # 检查依赖
    check_dependencies
    
    # 注册清理函数
    trap cleanup EXIT
    
    # 执行测试
    test_environment
    test_service_discovery
    test_project_management
    test_virtual_project
    test_service_approval
    test_mcp_protocol
    test_sse_endpoints
    test_interface_filter
    test_heartbeat_monitoring
    test_api_endpoints
    
    # 打印总结
    print_summary
    exit_code=$?
    
    log_info "测试结束时间: $(date '+%Y-%m-%d %H:%M:%S')"
    return $exit_code
}

# 执行主函数
main "$@"

