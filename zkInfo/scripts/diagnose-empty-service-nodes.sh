#!/bin/bash

# ============================================================================
# zk_dubbo_service_node 表为空问题排查脚本
# ============================================================================
# 
# 用途: 排查生产环境 zk_dubbo_service_node 表为空的原因
# 
# 排查要点:
# 1. ZooKeeper 连接状态
# 2. ZooKeeper 中是否有 Provider 数据
# 3. 白名单配置是否过滤了数据
# 4. 数据库连接和表结构
# 5. 服务审批状态
# 6. 应用启动日志
# 7. 数据同步日志
# ============================================================================

set -euo pipefail

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

# 配置
ZKINFO_URL="${ZKINFO_URL:-http://localhost:9091}"
ZK_CONNECT="${ZK_CONNECT:-localhost:2181}"
ZK_BASE_PATH="${ZK_BASE_PATH:-/dubbo}"
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DATABASE="${MYSQL_DATABASE:-mcp_bridge}"
MYSQL_USERNAME="${MYSQL_USERNAME:-mcp_user}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-mcp_user}"

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
# 1. 检查 zkInfo 服务状态
# ============================================================================
check_zkinfo_service() {
    log_section "1. 检查 zkInfo 服务状态"
    
    log_info "检查服务健康状态..."
    HEALTH=$(curl -s "${ZKINFO_URL}/actuator/health" 2>/dev/null || echo "")
    if [ -z "$HEALTH" ]; then
        log_error "无法连接到 zkInfo 服务: ${ZKINFO_URL}"
        return 1
    fi
    
    STATUS=$(echo "$HEALTH" | grep -o '"status"[^,}]*"[^"]*"' | sed 's/"status"[^"]*"\([^"]*\)"/\1/' || echo "")
    if [ "$STATUS" = "UP" ]; then
        log_success "zkInfo 服务状态: UP"
    else
        log_error "zkInfo 服务状态异常: $STATUS"
    fi
    
    log_info "检查服务统计信息..."
    STATS=$(curl -s "${ZKINFO_URL}/api/stats" 2>/dev/null || echo "")
    echo "$STATS"
    echo ""
}

# ============================================================================
# 2. 检查 ZooKeeper 连接和数据
# ============================================================================
check_zookeeper() {
    log_section "2. 检查 ZooKeeper 连接和数据"
    
    log_info "检查 ZooKeeper 连接..."
    ZK_TREE=$(curl -s "${ZKINFO_URL}/api/debug/zk-tree" 2>/dev/null || echo "")
    if [ -z "$ZK_TREE" ]; then
        log_error "无法获取 ZooKeeper 树结构"
        return 1
    fi
    
    log_success "ZooKeeper 连接正常"
    
    log_info "检查 ZooKeeper 中的服务数量..."
    # 统计服务路径数量
    SERVICE_COUNT=$(echo "$ZK_TREE" | grep -o '/dubbo/[^/]*/providers' | wc -l | tr -d ' ')
    log_info "ZooKeeper 中发现 $SERVICE_COUNT 个服务的 providers 路径"
    
    log_info "检查 ZooKeeper 中的 Provider 节点数量..."
    # 统计 provider 节点（简单统计）
    PROVIDER_NODES=$(echo "$ZK_TREE" | grep -o 'providers/[^"]*' | wc -l | tr -d ' ')
    log_info "ZooKeeper 中发现约 $PROVIDER_NODES 个 Provider 节点"
    
    if [ "$PROVIDER_NODES" -eq 0 ]; then
        log_warning "⚠️ ZooKeeper 中没有 Provider 节点，这是问题的根源！"
        log_info "请检查："
        echo "  1. Dubbo 服务是否已启动并注册到 ZooKeeper"
        echo "  2. ZooKeeper 路径配置是否正确: ${ZK_BASE_PATH}"
        echo "  3. 服务是否在白名单中"
    fi
    
    echo ""
}

# ============================================================================
# 3. 检查数据库表状态
# ============================================================================
check_database() {
    log_section "3. 检查数据库表状态"
    
    log_info "检查数据库连接..."
    if ! command -v mysql &> /dev/null; then
        log_warning "mysql 客户端未安装，跳过直接数据库检查"
        log_info "使用 API 接口检查数据库状态..."
        
        # 通过 API 检查
        SERVICES=$(curl -s "${ZKINFO_URL}/api/dubbo-services?page=1&size=1" 2>/dev/null || echo "")
        SERVICE_TOTAL=$(echo "$SERVICES" | grep -o '"total"[^,}]*[0-9]*' | grep -o '[0-9]*' | head -1 || echo "0")
        log_info "zk_dubbo_service 表中有 $SERVICE_TOTAL 个服务"
        
        # 检查节点数据
        if [ "$SERVICE_TOTAL" -gt 0 ]; then
            FIRST_SERVICE_ID=$(echo "$SERVICES" | grep -o '"id"[^,}]*[0-9]*' | grep -o '[0-9]*' | head -1)
            if [ ! -z "$FIRST_SERVICE_ID" ]; then
                NODES=$(curl -s "${ZKINFO_URL}/api/dubbo-services/${FIRST_SERVICE_ID}/nodes" 2>/dev/null || echo "[]")
                NODE_COUNT=$(echo "$NODES" | grep -o '{' | wc -l | tr -d ' ')
                log_info "第一个服务的节点数量: $NODE_COUNT"
                
                if [ "$NODE_COUNT" -eq 0 ]; then
                    log_error "⚠️ zk_dubbo_service_node 表为空或该服务没有节点数据"
                fi
            fi
        else
            log_warning "⚠️ zk_dubbo_service 表也为空"
        fi
        
        return 0
    fi
    
    log_info "使用 mysql 客户端检查数据库..."
    
    # 检查表是否存在
    TABLE_EXISTS=$(mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USERNAME}" -p"${MYSQL_PASSWORD}" \
        "${MYSQL_DATABASE}" -e "SHOW TABLES LIKE 'zk_dubbo_service_node';" 2>/dev/null | grep -c "zk_dubbo_service_node" || echo "0")
    
    if [ "$TABLE_EXISTS" -eq 0 ]; then
        log_error "⚠️ 表 zk_dubbo_service_node 不存在！"
        return 1
    fi
    
    log_success "表 zk_dubbo_service_node 存在"
    
    # 检查表记录数
    NODE_COUNT=$(mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USERNAME}" -p"${MYSQL_PASSWORD}" \
        "${MYSQL_DATABASE}" -e "SELECT COUNT(*) as count FROM zk_dubbo_service_node;" 2>/dev/null | tail -1 || echo "0")
    
    log_info "zk_dubbo_service_node 表记录数: $NODE_COUNT"
    
    if [ "$NODE_COUNT" -eq 0 ]; then
        log_error "⚠️ 表 zk_dubbo_service_node 为空！"
        
        # 检查服务表是否有数据
        SERVICE_COUNT=$(mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USERNAME}" -p"${MYSQL_PASSWORD}" \
            "${MYSQL_DATABASE}" -e "SELECT COUNT(*) as count FROM zk_dubbo_service;" 2>/dev/null | tail -1 || echo "0")
        
        log_info "zk_dubbo_service 表记录数: $SERVICE_COUNT"
        
        if [ "$SERVICE_COUNT" -gt 0 ]; then
            log_warning "服务表有数据但节点表为空，可能是同步失败"
        fi
    else
        log_success "表中有 $NODE_COUNT 条记录"
        
        # 显示前几条记录
        log_info "前5条节点记录:"
        mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USERNAME}" -p"${MYSQL_PASSWORD}" \
            "${MYSQL_DATABASE}" -e "SELECT id, service_id, interface_name, address, is_online, is_healthy FROM zk_dubbo_service_node LIMIT 5;" 2>/dev/null || true
    fi
    
    echo ""
}

# ============================================================================
# 4. 检查白名单配置
# ============================================================================
check_whitelist() {
    log_section "4. 检查白名单配置"
    
    log_info "查询启用的过滤器..."
    FILTERS=$(curl -s "${ZKINFO_URL}/api/filters/enabled" 2>/dev/null || echo "[]")
    
    FILTER_COUNT=$(echo "$FILTERS" | grep -o '{' | wc -l | tr -d ' ')
    log_info "启用的过滤器数量: $FILTER_COUNT"
    
    if [ "$FILTER_COUNT" -eq 0 ]; then
        log_warning "⚠️ 没有启用的过滤器，所有服务都应该被允许"
    else
        log_info "过滤器规则:"
        echo "$FILTERS" | head -20
    fi
    
    log_info "检查配置文件中的白名单..."
    log_info "请检查 application.yml 中的配置:"
    echo "  zk.whitelist.interfaces"
    echo "  zk.whitelist.database.enabled"
    echo ""
}

# ============================================================================
# 5. 检查服务审批状态
# ============================================================================
check_approval_status() {
    log_section "5. 检查服务审批状态"
    
    log_info "查询所有服务..."
    ALL_SERVICES=$(curl -s "${ZKINFO_URL}/api/dubbo-services?page=1&size=100" 2>/dev/null || echo "")
    TOTAL=$(echo "$ALL_SERVICES" | grep -o '"total"[^,}]*[0-9]*' | grep -o '[0-9]*' | head -1 || echo "0")
    
    log_info "总服务数: $TOTAL"
    
    log_info "查询待审批服务..."
    PENDING=$(curl -s "${ZKINFO_URL}/api/dubbo-services/pending?page=1&size=10" 2>/dev/null || echo "")
    PENDING_TOTAL=$(echo "$PENDING" | grep -o '"total"[^,}]*[0-9]*' | grep -o '[0-9]*' | head -1 || echo "0")
    
    log_info "待审批服务数: $PENDING_TOTAL"
    
    log_info "查询已审批服务..."
    APPROVED=$(curl -s "${ZKINFO_URL}/api/dubbo-services/approved?page=1&size=10" 2>/dev/null || echo "")
    APPROVED_TOTAL=$(echo "$APPROVED" | grep -o '"total"[^,}]*[0-9]*' | grep -o '[0-9]*' | head -1 || echo "0")
    
    log_info "已审批服务数: $APPROVED_TOTAL"
    
    if [ "$APPROVED_TOTAL" -eq 0 ] && [ "$PENDING_TOTAL" -gt 0 ]; then
        log_warning "⚠️ 有服务待审批但未审批，可能影响数据同步"
    fi
    
    echo ""
}

# ============================================================================
# 6. 检查关键日志
# ============================================================================
check_logs() {
    log_section "6. 检查关键日志"
    
    LOG_FILE="${LOG_FILE:-logs/zkinfo.log}"
    
    if [ ! -f "$LOG_FILE" ]; then
        log_warning "日志文件不存在: $LOG_FILE"
        log_info "请手动检查日志文件，查找以下关键词:"
        echo "  - 'ZooKeeper 数据初始化'"
        echo "  - '批量拉取 ZooKeeper 数据'"
        echo "  - '保存Provider到数据库'"
        echo "  - '保存Provider到数据库失败'"
        echo "  - '白名单'"
        echo "  - '不在白名单中'"
        return 0
    fi
    
    log_info "检查启动时的数据初始化日志..."
    echo "--- 查找 'ZooKeeper 数据初始化' ---"
    grep -i "zookeeper.*数据初始化\|批量拉取.*zookeeper" "$LOG_FILE" | tail -5 || echo "未找到相关日志"
    echo ""
    
    log_info "检查 Provider 保存日志..."
    echo "--- 查找 '保存Provider到数据库' ---"
    grep -i "保存.*provider.*数据库\|save.*provider.*database" "$LOG_FILE" | tail -10 || echo "未找到相关日志"
    echo ""
    
    log_info "检查保存失败日志..."
    echo "--- 查找 '保存Provider到数据库失败' ---"
    grep -i "保存.*provider.*数据库.*失败\|save.*provider.*fail" "$LOG_FILE" | tail -10 || echo "未找到失败日志"
    echo ""
    
    log_info "检查白名单过滤日志..."
    echo "--- 查找 '不在白名单中' ---"
    grep -i "不在白名单\|whitelist\|not.*allowed" "$LOG_FILE" | tail -10 || echo "未找到白名单日志"
    echo ""
    
    log_info "检查 ZooKeeper 连接错误..."
    echo "--- 查找 ZooKeeper 连接错误 ---"
    grep -i "zookeeper.*error\|zookeeper.*exception\|zookeeper.*fail" "$LOG_FILE" | tail -10 || echo "未找到连接错误"
    echo ""
}

# ============================================================================
# 7. 尝试手动同步
# ============================================================================
try_manual_sync() {
    log_section "7. 尝试手动同步"
    
    log_info "查询第一个服务..."
    SERVICES=$(curl -s "${ZKINFO_URL}/api/dubbo-services?page=1&size=1" 2>/dev/null || echo "")
    FIRST_SERVICE_ID=$(echo "$SERVICES" | grep -o '"id"[^,}]*[0-9]*' | grep -o '[0-9]*' | head -1)
    
    if [ -z "$FIRST_SERVICE_ID" ]; then
        log_warning "没有可用的服务进行同步测试"
        return 0
    fi
    
    log_info "尝试手动同步服务节点 (Service ID: $FIRST_SERVICE_ID)..."
    SYNC_RESPONSE=$(curl -s -X POST "${ZKINFO_URL}/api/dubbo-services/${FIRST_SERVICE_ID}/sync-nodes" 2>/dev/null || echo "")
    
    if echo "$SYNC_RESPONSE" | grep -q "成功\|success\|synced"; then
        log_success "手动同步成功"
        echo "$SYNC_RESPONSE"
    else
        log_warning "手动同步可能失败"
        echo "$SYNC_RESPONSE"
    fi
    
    echo ""
}

# ============================================================================
# 8. 生成排查报告
# ============================================================================
generate_report() {
    log_section "8. 排查报告和建议"
    
    echo "基于以上检查，可能的原因和建议："
    echo ""
    echo "1. ZooKeeper 中没有 Provider 数据"
    echo "   建议: 检查 Dubbo 服务是否已启动并注册到 ZooKeeper"
    echo "   命令: zkCli.sh -server ${ZK_CONNECT} ls ${ZK_BASE_PATH}"
    echo ""
    echo "2. 白名单过滤导致数据被过滤"
    echo "   建议: 检查白名单配置，确保服务接口在白名单中"
    echo "   命令: curl -s '${ZKINFO_URL}/api/filters/enabled'"
    echo ""
    echo "3. 服务未审批，数据未保存"
    echo "   建议: 审批服务或检查审批流程配置"
    echo "   命令: curl -s '${ZKINFO_URL}/api/dubbo-services/pending'"
    echo ""
    echo "4. 数据库保存失败"
    echo "   建议: 检查应用日志中的异常信息"
    echo "   命令: grep -i '保存.*provider.*失败\|exception\|error' logs/zkinfo.log"
    echo ""
    echo "5. 启动时的批量拉取失败"
    echo "   建议: 检查应用启动日志"
    echo "   命令: grep -i 'zookeeper.*初始化\|bootstrap' logs/zkinfo.log | head -20"
    echo ""
    echo "6. 手动触发同步"
    echo "   建议: 使用 API 手动同步服务节点"
    echo "   命令: curl -X POST '${ZKINFO_URL}/api/dubbo-services/{serviceId}/sync-nodes'"
    echo ""
}

# ============================================================================
# 主函数
# ============================================================================
main() {
    log_section "zk_dubbo_service_node 表为空问题排查"
    log_info "开始时间: $(date '+%Y-%m-%d %H:%M:%S')"
    log_info "zkInfo URL: $ZKINFO_URL"
    log_info "ZooKeeper: $ZK_CONNECT"
    echo ""
    
    check_zkinfo_service
    check_zookeeper
    check_database
    check_whitelist
    check_approval_status
    check_logs
    try_manual_sync
    generate_report
    
    log_section "排查完成"
    log_info "结束时间: $(date '+%Y-%m-%d %H:%M:%S')"
}

# 执行主函数
main "$@"



