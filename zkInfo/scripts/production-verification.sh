#!/bin/bash

# ============================================================================
# zkInfo 生产环境核心功能验证脚本
# ============================================================================
# 
# 用途: 在生产环境逐步验证核心功能
# 使用方法: ./production-verification.sh [功能模块]
#
# 功能模块:
#   all          - 验证所有功能（默认）
#   health       - 健康检查
#   services     - 服务发现与管理
#   projects     - 项目管理
#   virtual      - 虚拟项目管理
#   approval     - 服务审批
#   mcp          - MCP协议调用
#   sse          - SSE端点
#   nacos        - Nacos注册
# ============================================================================

set -euo pipefail

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# 配置（从环境变量读取，或使用默认值）
ZKINFO_URL="${ZKINFO_URL:-http://localhost:9091}"
NACOS_URL="${NACOS_URL:-http://localhost:8848}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-public}"
NACOS_GROUP="${NACOS_GROUP:-mcp-server}"

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

log_section() {
    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}  $1${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
    echo ""
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
    # 简化处理：直接搜索路径中的最后一个key
    local last_key=$(echo "$path" | awk -F'.' '{print $NF}')
    echo "$json" | grep -o "\"$last_key\"[[:space:]]*:[[:space:]]*[0-9]*" | sed "s/\"$last_key\"[[:space:]]*:[[:space:]]*\([0-9]*\)/\1/" | head -1
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

# 检查响应是否包含错误
json_has_error() {
    local json="$1"
    echo "$json" | grep -qi "error"
}

# 检查依赖
check_dependencies() {
    if ! command -v curl &> /dev/null; then
        log_error "curl 未安装，请先安装 curl"
        exit 1
    fi
    
    log_success "依赖检查通过（使用纯 bash JSON 解析）"
}

# 1. 健康检查
verify_health() {
    log_section "1. 健康检查"
    
    log_info "检查 zkInfo 服务状态..."
    HEALTH_RESPONSE=$(curl -s -f "${ZKINFO_URL}/actuator/health" 2>/dev/null)
    if [ $? -eq 0 ] && [ ! -z "$HEALTH_RESPONSE" ]; then
        STATUS=$(json_get_value "$HEALTH_RESPONSE" "status" || echo "UP")
        log_success "zkInfo 服务状态: $STATUS"
    else
        log_error "zkInfo 服务不可用"
        return 1
    fi
    
    log_info "检查服务统计信息..."
    curl -s "${ZKINFO_URL}/api/stats"
    echo ""
}

# 2. 服务发现与管理
verify_services() {
    log_section "2. 服务发现与管理"
    
    log_info "查询服务列表..."
    SERVICES_RESPONSE=$(curl -s "${ZKINFO_URL}/api/dubbo-services?page=1&size=5")
    SERVICE_COUNT=$(json_get_number "$SERVICES_RESPONSE" "total" || echo "0")
    log_success "找到 $SERVICE_COUNT 个服务"
    
    if [ "$SERVICE_COUNT" -gt 0 ]; then
        # 提取第一个服务的ID（从data数组中）
        FIRST_SERVICE_ID=$(echo "$SERVICES_RESPONSE" | grep -o '"id"[[:space:]]*:[[:space:]]*[0-9]*' | head -1 | sed 's/"id"[[:space:]]*:[[:space:]]*\([0-9]*\)/\1/')
        if [ ! -z "$FIRST_SERVICE_ID" ] && [ "$FIRST_SERVICE_ID" != "null" ]; then
            log_info "查询服务详情 (ID: $FIRST_SERVICE_ID)..."
            curl -s "${ZKINFO_URL}/api/dubbo-services/$FIRST_SERVICE_ID"
            echo ""
            
            log_info "查询服务节点..."
            curl -s "${ZKINFO_URL}/api/dubbo-services/$FIRST_SERVICE_ID/nodes"
            echo ""
        fi
    fi
    
    log_info "查询待审批服务..."
    PENDING_RESPONSE=$(curl -s "${ZKINFO_URL}/api/dubbo-services/pending?page=1&size=5")
    PENDING_TOTAL=$(json_get_number "$PENDING_RESPONSE" "total" || echo "0")
    echo "$PENDING_TOTAL"
    echo ""
}

# 3. 项目管理
verify_projects() {
    log_section "3. 项目管理"
    
    log_info "查询所有项目..."
    PROJECTS=$(curl -s "${ZKINFO_URL}/api/projects")
    # 简单计算项目数量（统计对象数量）
    PROJECT_COUNT=$(echo "$PROJECTS" | grep -o '{' | wc -l | tr -d ' ')
    log_success "找到 $PROJECT_COUNT 个项目"
    
    if [ "$PROJECT_COUNT" -gt 0 ]; then
        # 提取第一个项目的ID
        FIRST_PROJECT_ID=$(echo "$PROJECTS" | grep -o '"id"[[:space:]]*:[[:space:]]*[0-9]*' | head -1 | sed 's/"id"[[:space:]]*:[[:space:]]*\([0-9]*\)/\1/')
        if [ ! -z "$FIRST_PROJECT_ID" ] && [ "$FIRST_PROJECT_ID" != "null" ]; then
            log_info "查询项目详情 (ID: $FIRST_PROJECT_ID)..."
            curl -s "${ZKINFO_URL}/api/projects/$FIRST_PROJECT_ID"
            echo ""
        fi
    fi
}

# 4. 虚拟项目管理
verify_virtual_projects() {
    log_section "4. 虚拟项目管理"
    
    log_info "查询所有虚拟项目..."
    VIRTUAL_PROJECTS=$(curl -s "${ZKINFO_URL}/api/virtual-projects")
    # 简单计算虚拟项目数量
    VIRTUAL_COUNT=$(echo "$VIRTUAL_PROJECTS" | grep -o '{' | wc -l | tr -d ' ')
    log_success "找到 $VIRTUAL_COUNT 个虚拟项目"
    
    if [ "$VIRTUAL_COUNT" -gt 0 ]; then
        # 提取第一个虚拟项目的ID和端点名称
        FIRST_VIRTUAL_ID=$(echo "$VIRTUAL_PROJECTS" | grep -o '"project"[^}]*"id"[^,}]*' | grep -o '[0-9]\+' | head -1)
        ENDPOINT_NAME=$(echo "$VIRTUAL_PROJECTS" | grep -o '"endpointName"[^,}]*"[^"]*"' | head -1 | sed 's/"endpointName"[^"]*"\([^"]*\)"/\1/' | sed 's/.*"\([^"]*\)".*/\1/')
        
        if [ ! -z "$FIRST_VIRTUAL_ID" ] && [ "$FIRST_VIRTUAL_ID" != "null" ]; then
            log_info "查询虚拟项目详情 (ID: $FIRST_VIRTUAL_ID)..."
            curl -s "${ZKINFO_URL}/api/virtual-projects/$FIRST_VIRTUAL_ID"
            echo ""
            
            if [ ! -z "$ENDPOINT_NAME" ] && [ "$ENDPOINT_NAME" != "null" ]; then
                log_info "查询端点信息 (Endpoint: $ENDPOINT_NAME)..."
                curl -s "${ZKINFO_URL}/api/virtual-projects/$FIRST_VIRTUAL_ID/endpoint"
                echo ""
            fi
        fi
    fi
}

# 5. 服务审批
verify_approval() {
    log_section "5. 服务审批"
    
    log_info "查询待审批记录..."
    PENDING=$(curl -s "${ZKINFO_URL}/api/approvals/pending")
    PENDING_COUNT=$(echo "$PENDING" | grep -o '{' | wc -l | tr -d ' ')
    log_success "待审批记录数: $PENDING_COUNT"
    
    log_info "查询所有审批记录..."
    ALL_APPROVALS=$(curl -s "${ZKINFO_URL}/api/approvals?page=1&size=5")
    TOTAL_COUNT=$(json_get_number "$ALL_APPROVALS" "total" || echo "0")
    log_success "总审批记录数: $TOTAL_COUNT"
}

# 6. MCP协议调用
verify_mcp() {
    log_section "6. MCP协议调用"
    
    # 获取一个虚拟项目端点
    VIRTUAL_PROJECTS=$(curl -s "${ZKINFO_URL}/api/virtual-projects")
    ENDPOINT_NAME=$(echo "$VIRTUAL_PROJECTS" | grep -o '"endpointName"[^,}]*"[^"]*"' | head -1 | sed 's/"endpointName"[^"]*"\([^"]*\)"/\1/' | sed 's/.*"\([^"]*\)".*/\1/')
    
    if [ -z "$ENDPOINT_NAME" ] || [ "$ENDPOINT_NAME" = "null" ]; then
        log_warning "没有可用的虚拟项目端点，跳过MCP测试"
        return 0
    fi
    
    log_info "使用端点: $ENDPOINT_NAME"
    SESSION_ID="verify-$(date +%s)"
    
    log_info "MCP Initialize..."
    INIT_RESPONSE=$(curl -s -X POST "${ZKINFO_URL}/mcp/message?sessionId=${SESSION_ID}&endpoint=${ENDPOINT_NAME}" \
        -H "Content-Type: application/json" \
        -d '{
            "jsonrpc": "2.0",
            "id": "1",
            "method": "initialize",
            "params": {
                "protocolVersion": "2024-11-05",
                "capabilities": {},
                "clientInfo": {
                    "name": "verification-client",
                    "version": "1.0.0"
                }
            }
        }')
    
    if json_has_field "$INIT_RESPONSE" "result"; then
        log_success "MCP Initialize 成功"
    else
        log_warning "MCP Initialize 可能失败"
    fi
    
    sleep 1
    
    log_info "MCP Tools/List..."
    TOOLS_RESPONSE=$(curl -s -X POST "${ZKINFO_URL}/mcp/message?sessionId=${SESSION_ID}&endpoint=${ENDPOINT_NAME}" \
        -H "Content-Type: application/json" \
        -d '{
            "jsonrpc": "2.0",
            "id": "2",
            "method": "tools/list",
            "params": {}
        }')
    
    # 计算工具数量（统计 "name" 字段）
    TOOLS_COUNT=$(echo "$TOOLS_RESPONSE" | grep -o '"name"[^,}]*' | wc -l | tr -d ' ')
    log_success "找到 $TOOLS_COUNT 个工具"
}

# 7. SSE端点
verify_sse() {
    log_section "7. SSE端点验证"
    
    # 获取一个虚拟项目端点
    VIRTUAL_PROJECTS=$(curl -s "${ZKINFO_URL}/api/virtual-projects")
    ENDPOINT_NAME=$(echo "$VIRTUAL_PROJECTS" | grep -o '"endpointName"[^,}]*"[^"]*"' | head -1 | sed 's/"endpointName"[^"]*"\([^"]*\)"/\1/' | sed 's/.*"\([^"]*\)".*/\1/')
    
    if [ -z "$ENDPOINT_NAME" ] || [ "$ENDPOINT_NAME" = "null" ]; then
        log_warning "没有可用的虚拟项目端点，跳过SSE测试"
        return 0
    fi
    
    log_info "测试SSE连接 (端点: $ENDPOINT_NAME)..."
    if timeout 3 curl -s -f "${ZKINFO_URL}/sse/${ENDPOINT_NAME}" -H "Accept: text/event-stream" > /dev/null 2>&1; then
        log_success "SSE连接成功"
    else
        EXIT_CODE=$?
        if [ $EXIT_CODE -eq 124 ]; then
            log_success "SSE连接建立成功（超时退出）"
        else
            log_warning "SSE连接可能失败"
        fi
    fi
}

# 8. Nacos注册
verify_nacos() {
    log_section "8. Nacos注册验证"
    
    log_info "查询Nacos服务列表..."
    NACOS_SERVICES=$(curl -s "${NACOS_URL}/nacos/v1/ns/service/list?pageNo=1&pageSize=10")
    SERVICE_COUNT=$(json_get_number "$NACOS_SERVICES" "count" || echo "0")
    log_success "Nacos中的服务数: $SERVICE_COUNT"
    
    # 检查虚拟项目是否注册
    VIRTUAL_PROJECTS=$(curl -s "${ZKINFO_URL}/api/virtual-projects")
    ENDPOINT_NAME=$(echo "$VIRTUAL_PROJECTS" | grep -o '"endpointName"[^,}]*"[^"]*"' | head -1 | sed 's/"endpointName"[^"]*"\([^"]*\)"/\1/' | sed 's/.*"\([^"]*\)".*/\1/')
    
    if [ ! -z "$ENDPOINT_NAME" ] && [ "$ENDPOINT_NAME" != "null" ]; then
        log_info "检查虚拟项目在Nacos中的注册状态..."
        INSTANCES=$(curl -s "${NACOS_URL}/nacos/v3/client/ns/instance/list?namespaceId=${NACOS_NAMESPACE}&groupName=${NACOS_GROUP}&serviceName=virtual-${ENDPOINT_NAME}" \
            -H "Content-Type: application/json" \
            -H "User-Agent: Nacos-Bash-Client" 2>/dev/null)
        # 计算实例数量
        INSTANCE_COUNT=$(echo "$INSTANCES" | grep -o '{' | wc -l | tr -d ' ')
        
        if [ "$INSTANCE_COUNT" -gt 0 ]; then
            log_success "虚拟项目已注册到Nacos (实例数: $INSTANCE_COUNT)"
        else
            log_warning "虚拟项目未在Nacos中找到"
        fi
    fi
}

# 主函数
main() {
    local module="${1:-all}"
    
    log_section "zkInfo 生产环境核心功能验证"
    log_info "zkInfo URL: $ZKINFO_URL"
    log_info "Nacos URL: $NACOS_URL"
    echo ""
    
    check_dependencies
    
    case "$module" in
        health)
            verify_health
            ;;
        services)
            verify_services
            ;;
        projects)
            verify_projects
            ;;
        virtual)
            verify_virtual_projects
            ;;
        approval)
            verify_approval
            ;;
        mcp)
            verify_mcp
            ;;
        sse)
            verify_sse
            ;;
        nacos)
            verify_nacos
            ;;
        all|*)
            verify_health
            verify_services
            verify_projects
            verify_virtual_projects
            verify_approval
            verify_mcp
            verify_sse
            verify_nacos
            ;;
    esac
    
    log_section "验证完成"
    log_success "所有验证步骤已执行"
}

# 显示帮助信息
show_help() {
    cat << EOF
zkInfo 生产环境核心功能验证脚本

使用方法:
    ./production-verification.sh [功能模块]

功能模块:
    all         验证所有功能（默认）
    health      健康检查
    services    服务发现与管理
    projects    项目管理
    virtual     虚拟项目管理
    approval    服务审批
    mcp         MCP协议调用
    sse         SSE端点
    nacos       Nacos注册

环境变量:
    ZKINFO_URL          zkInfo服务地址 (默认: http://localhost:9091)
    NACOS_URL           Nacos服务地址 (默认: http://localhost:8848)
    NACOS_NAMESPACE     Nacos命名空间 (默认: public)
    NACOS_GROUP         Nacos服务组 (默认: mcp-server)

示例:
    # 验证所有功能
    ./production-verification.sh

    # 只验证健康检查
    ./production-verification.sh health

    # 使用自定义地址
    ZKINFO_URL=http://prod.example.com:9091 ./production-verification.sh
EOF
}

# 处理命令行参数
if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
    show_help
    exit 0
fi

# 执行主函数
main "$@"

