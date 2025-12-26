#!/bin/bash

# ============================================================================
# zk_dubbo_service_node 表数据修复脚本
# ============================================================================
# 
# 用途: 修复生产环境 zk_dubbo_service_node 表为空的问题
# 
# 修复策略:
# 1. 从 ZooKeeper 重新拉取所有 Provider 数据
# 2. 批量同步所有服务的节点数据
# 3. 验证数据同步结果
# ============================================================================

set -euo pipefail

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# 配置
ZKINFO_URL="${ZKINFO_URL:-http://localhost:9091}"

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

# JSON 解析函数（纯 bash）
json_get_number() {
    local json="$1"
    local key="$2"
    echo "$json" | grep -o "\"$key\"[[:space:]]*:[[:space:]]*[0-9]*" | sed "s/\"$key\"[[:space:]]*:[[:space:]]*\([0-9]*\)/\1/" | head -1
}

extract_service_ids() {
    local json="$1"
    # 提取所有服务ID
    echo "$json" | grep -o '"id"[[:space:]]*:[[:space:]]*[0-9]*' | sed 's/"id"[[:space:]]*:[[:space:]]*\([0-9]*\)/\1/'
}

# ============================================================================
# 主函数
# ============================================================================
main() {
    log_section "zk_dubbo_service_node 表数据修复"
    log_info "开始时间: $(date '+%Y-%m-%d %H:%M:%S')"
    log_info "zkInfo URL: $ZKINFO_URL"
    echo ""
    
    # 1. 检查服务状态
    log_info "检查 zkInfo 服务状态..."
    HEALTH=$(curl -s "${ZKINFO_URL}/actuator/health" 2>/dev/null || echo "")
    if [ -z "$HEALTH" ]; then
        log_error "无法连接到 zkInfo 服务"
        exit 1
    fi
    log_success "服务连接正常"
    
    # 2. 获取所有服务列表
    log_section "获取服务列表"
    log_info "查询所有服务..."
    
    PAGE=1
    SIZE=100
    ALL_SERVICE_IDS=()
    
    while true; do
        SERVICES_RESPONSE=$(curl -s "${ZKINFO_URL}/api/dubbo-services?page=${PAGE}&size=${SIZE}" 2>/dev/null || echo "")
        
        if [ -z "$SERVICES_RESPONSE" ]; then
            log_error "无法获取服务列表"
            break
        fi
        
        # 提取服务ID
        PAGE_SERVICE_IDS=$(extract_service_ids "$SERVICES_RESPONSE")
        
        if [ -z "$PAGE_SERVICE_IDS" ]; then
            break
        fi
        
        # 添加到总列表
        while read -r service_id; do
            if [ ! -z "$service_id" ]; then
                ALL_SERVICE_IDS+=($service_id)
            fi
        done <<< "$PAGE_SERVICE_IDS"
        
        # 检查是否还有下一页
        TOTAL=$(json_get_number "$SERVICES_RESPONSE" "total" || echo "0")
        CURRENT_COUNT=$(($PAGE * $SIZE))
        
        if [ "$CURRENT_COUNT" -ge "$TOTAL" ] 2>/dev/null; then
            break
        fi
        
        PAGE=$((PAGE + 1))
    done
    
    SERVICE_COUNT=${#ALL_SERVICE_IDS[@]}
    log_success "找到 $SERVICE_COUNT 个服务"
    
    if [ "$SERVICE_COUNT" -eq 0 ]; then
        log_warning "没有可用的服务进行同步"
        exit 0
    fi
    
    # 3. 批量同步节点数据
    log_section "批量同步节点数据"
    
    SUCCESS_COUNT=0
    FAILED_COUNT=0
    
    for service_id in "${ALL_SERVICE_IDS[@]}"; do
        log_info "同步服务节点 (Service ID: $service_id)..."
        
        SYNC_RESPONSE=$(curl -s -X POST "${ZKINFO_URL}/api/dubbo-services/${service_id}/sync-nodes" 2>/dev/null || echo "")
        
        if echo "$SYNC_RESPONSE" | grep -qi "成功\|success\|synced"; then
            log_success "服务 $service_id 同步成功"
            SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        else
            log_warning "服务 $service_id 同步可能失败"
            FAILED_COUNT=$((FAILED_COUNT + 1))
        fi
        
        # 避免请求过快
        sleep 0.1
    done
    
    # 4. 验证同步结果
    log_section "验证同步结果"
    
    log_info "检查节点数据..."
    TOTAL_NODES=0
    
    # 检查前10个服务的节点数量
    CHECK_COUNT=$((SERVICE_COUNT > 10 ? 10 : SERVICE_COUNT))
    for i in $(seq 0 $((CHECK_COUNT - 1))); do
        service_id=${ALL_SERVICE_IDS[$i]}
        NODES=$(curl -s "${ZKINFO_URL}/api/dubbo-services/${service_id}/nodes" 2>/dev/null || echo "[]")
        NODE_COUNT=$(echo "$NODES" | grep -o '{' | wc -l | tr -d ' ')
        TOTAL_NODES=$((TOTAL_NODES + NODE_COUNT))
        
        if [ "$NODE_COUNT" -gt 0 ]; then
            log_success "服务 $service_id 有 $NODE_COUNT 个节点"
        else
            log_warning "服务 $service_id 没有节点数据"
        fi
    done
    
    # 5. 总结
    log_section "修复总结"
    echo "总服务数: $SERVICE_COUNT"
    echo "同步成功: ${GREEN}$SUCCESS_COUNT${NC}"
    echo "同步失败: ${RED}$FAILED_COUNT${NC}"
    echo "检查的节点总数: $TOTAL_NODES"
    echo ""
    
    if [ "$TOTAL_NODES" -gt 0 ]; then
        log_success "✅ 节点数据已同步，表中有数据了！"
    else
        log_warning "⚠️ 节点数据仍为空，请检查："
        echo "  1. ZooKeeper 中是否有 Provider 数据"
        echo "  2. 白名单配置是否正确"
        echo "  3. 服务是否已审批"
        echo "  4. 查看应用日志获取详细错误信息"
    fi
    
    log_info "结束时间: $(date '+%Y-%m-%d %H:%M:%S')"
}

# 执行主函数
main "$@"

