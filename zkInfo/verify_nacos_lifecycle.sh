#!/bin/bash

# zkInfo Nacos 全生命周期验证脚本
# 使用 curl 直接与 Nacos API 交互，验证注册数据的完整性

# ================= 配置区域 =================
NACOS_HOST="${NACOS_HOST:-localhost:8848}"
NAMESPACE="${NAMESPACE:-public}"
SERVICE_NAME="${SERVICE_NAME:-com.example.DemoService:1.0.0}" # 默认服务名，运行时可指定
GROUP_NAME="${GROUP_NAME:-mcp-server}"
# ===========================================

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 帮助函数
usage() {
    echo "用法: $0 [选项]"
    echo "选项:"
    echo "  -h <host>       Nacos 地址 (默认: localhost:8848)"
    echo "  -s <service>    服务名称 (默认: com.example.DemoService:1.0.0)"
    echo "  -n <namespace>  命名空间 (默认: public)"
    echo "  -m              监控模式 (持续刷新状态)"
    echo "  -c              清理模式 (删除相关服务和配置)"
    exit 1
}

# 解析参数
MONITOR_MODE=false
CLEAN_MODE=false

while getopts "h:s:n:mc" opt; do
  case $opt in
    h) NACOS_HOST=$OPTARG ;;
    s) SERVICE_NAME=$OPTARG ;;
    n) NAMESPACE=$OPTARG ;;
    m) MONITOR_MODE=true ;;
    c) CLEAN_MODE=true ;;
    *) usage ;;
  esac
done

echo -e "${BLUE}=============================================${NC}"
echo -e "${BLUE}   zkInfo Nacos 生命周期验证脚本 (Curl版)   ${NC}"
echo -e "${BLUE}=============================================${NC}"
echo "Nacos 地址: $NACOS_HOST"
echo "目标服务名: $SERVICE_NAME"
echo "命名空间:   $NAMESPACE"
echo "---------------------------------------------"
echo ""

# 1. Nacos 健康检查
check_health() {
    echo -n "Step 1: 检查 Nacos 健康状态... "
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://${NACOS_HOST}/nacos/v1/console/health/liveness")
    if [ "$HTTP_CODE" == "200" ]; then
        echo -e "${GREEN}✅ 在线${NC}"
    else
        echo -e "${RED}❌ 离线 (HTTP $HTTP_CODE)${NC}"
        echo "请确保 Nacos Server 已启动"
        exit 1
    fi
}

# 2. 验证服务实例及元数据
check_service() {
    echo -e "\nStep 2: 验证服务实例 (Service Registration)..."
    
    # 获取实例列表
    RESPONSE=$(curl -s "http://${NACOS_HOST}/nacos/v1/ns/instance/list?serviceName=${SERVICE_NAME}&groupName=${GROUP_NAME}&namespaceId=${NAMESPACE}")
    
    # 简单检查是否包含 IP（代表有实例）
    if echo "$RESPONSE" | grep -q "\"ip\""; then
        INSTANCE_COUNT=$(echo "$RESPONSE" | grep -o "\"ip\"" | wc -l | tr -d ' ')
        echo -e "  ✅ 服务存在，发现 ${GREEN}${INSTANCE_COUNT}${NC} 个实例"
        
        # 检查元数据 Protocol
        if echo "$RESPONSE" | grep -q "mcp-sse"; then
            echo -e "  ✅ 元数据 Protocol: ${GREEN}mcp-sse${NC} (正确)"
        else
            echo -e "  ${RED}❌ 元数据 Protocol 缺失或错误${NC}"
            echo "  原始内容: $RESPONSE"
        fi

        # 检查 MD5
        if echo "$RESPONSE" | grep -q "server.md5"; then
            MD5_VAL=$(echo "$RESPONSE" | grep -o '"server.md5":"[^"]*"' | cut -d'"' -f4)
            echo -e "  ✅ 元数据 MD5: ${GREEN}${MD5_VAL}${NC}"
        else
            echo -e "  ${YELLOW}⚠️  元数据 server.md5 缺失${NC}"
        fi
        
        # 检查 endpoint
        if echo "$RESPONSE" | grep -q "sseEndpoint"; then
            EP_VAL=$(echo "$RESPONSE" | grep -o '"sseEndpoint":"[^"]*"' | cut -d'"' -f4)
            echo -e "  ✅ SSE Endpoint: ${GREEN}${EP_VAL}${NC}"
        else
            echo -e "  ${RED}❌ sseEndpoint 缺失${NC}"
        fi

    else
        echo -e "  ${YELLOW}⚠️  未找到实例 (服务可能未注册或已下线)${NC}"
        # 如果是必须存在的验证，可以在这里 exit 1，但为了生命周期观察，我们继续
    fi
}

# 3. 验证配置中心 (ConfigService)
check_configs() {
    echo -e "\nStep 3: 验证配置中心 (Config Publishing)..."
    
    # 解析 serviceId 和 version (假设 ServiceName 格式为 Name:Version)
    # 实际 ID 逻辑可能需要根据 zkInfo 代码调整，这里尝试标准猜测
    # zkInfo 生成 serviceId 通常是 UUID，或者根据 Interface+Version 生成
    # 由于 ID 可能是 UUID，我们主要检查是否存在相关后缀的配置
    
    echo "  正在通过模糊查询查找相关配置..."
    
    # 列出所有配置，搜索包含 SERVICE_NAME 的配置
    # 注意：Nacos Config List API 分页
    SEARCH_KEY=$(echo $SERVICE_NAME | cut -d':' -f1) # 取接口名部分
    CONFIG_LIST=$(curl -s "http://${NACOS_HOST}/nacos/v1/cs/configs?dataId=$SEARCH_KEY&group=&pageNo=1&pageSize=20&search=blur&namespaceId=${NAMESPACE}")

    if echo "$CONFIG_LIST" | grep -q "mcp-server.json"; then
        echo -e "  ✅ 发现关联配置:"
        
        # 尝试提取具体的 DataId
        TOOLS_ID=$(echo "$CONFIG_LIST" | grep -o "[^\"]*-mcp-tools.json" | head -1)
        VERSIONS_ID=$(echo "$CONFIG_LIST" | grep -o "[^\"]*-mcp-versions.json" | head -1)
        SERVER_ID=$(echo "$CONFIG_LIST" | grep -o "[^\"]*-mcp-server.json" | head -1)
        
        check_single_config "$TOOLS_ID" "mcp-tools"
        check_single_config "$VERSIONS_ID" "mcp-versions"
        check_single_config "$SERVER_ID" "mcp-server"
    else
        echo -e "  ${YELLOW}⚠️  未找到匹配 '$SEARCH_KEY' 的 MCP 配置文件${NC}"
        echo "  (如果您使用了自定义 ServiceId，这是正常的，请尝试手动指定 DataId)"
    fi
}

check_single_config() {
    DATA_ID=$1
    GROUP=$2
    if [ -z "$DATA_ID" ]; then
        return
    fi
    
    CONTENT=$(curl -s "http://${NACOS_HOST}/nacos/v1/cs/configs?dataId=${DATA_ID}&group=${GROUP}&namespaceId=${NAMESPACE}")
    
    if [ "$CONTENT" != "config data not exist" ] && [ -n "$CONTENT" ]; then
         # 简单的 JSON 检查
         if echo "$CONTENT" | grep -q "{"; then
             SIZE=$(echo ${#CONTENT})
             echo -e "  ✅ [${GROUP}] $DATA_ID: 获取成功 (${SIZE} bytes)"
         else
             echo -e "  ${RED}❌ [${GROUP}] $DATA_ID: 内容格式可能错误${NC}"
         fi
    else
         echo -e "  ${RED}❌ [${GROUP}] $DATA_ID: 配置不存在${NC}"
    fi
}

# 4. 清理功能 (模拟生命周期结束)
clean_up() {
    echo -e "\nStep 4: 执行清理 (模拟下线)..."
    echo -e "${YELLOW}警告: 将删除服务 $SERVICE_NAME 的实例和配置${NC}"
    read -p "确认清理? (y/n): " CONFIRM
    if [ "$CONFIRM" != "y" ]; then
        echo "取消清理"
        return
    fi
    
    # 下线实例
    echo "  正在下线实例..."
    # 需要 IP 和 Port，这里简化为只探测
    # 实际脚本需要先获取 IP/Port
    LIST_RESP=$(curl -s "http://${NACOS_HOST}/nacos/v1/ns/instance/list?serviceName=${SERVICE_NAME}&groupName=${GROUP_NAME}&namespaceId=${NAMESPACE}")
    IP=$(echo "$LIST_RESP" | grep -o "\"ip\":\"[^\"]*\"" | head -1 | cut -d'"' -f4)
    PORT=$(echo "$LIST_RESP" | grep -o "\"port\":[0-9]*" | head -1 | cut -d':' -f2)
    
    if [ -n "$IP" ]; then
        curl -X DELETE "http://${NACOS_HOST}/nacos/v1/ns/instance?serviceName=${SERVICE_NAME}&groupName=${GROUP_NAME}&ip=${IP}&port=${PORT}&namespaceId=${NAMESPACE}"
        echo -e "  ✅ 实例 ${IP}:${PORT} 下线请求已发送"
    else
        echo "  ⚠️  未找到实例，跳过下线"
    fi
    
    # 删除配置
    # 需要确切的 DataId，这里仅作演示结构
    echo "  (注: 自动删除配置需要确切的 DataId，建议手动清理)"
}

# 主逻辑
check_health

if [ "$CLEAN_MODE" = true ]; then
    clean_up
    exit 0
fi

if [ "$MONITOR_MODE" = true ]; then
    echo -e "\n${BLUE}=== 进入监控模式 (按 Ctrl+C 退出) ===${NC}"
    while true; do
        DATE=$(date '+%H:%M:%S')
        echo -e "\n[$DATE] 刷新状态..."
        check_service
        sleep 5
    done
else
    check_service
    check_configs
    echo -e "\n${BLUE}=== 验证完成 ===${NC}"
fi
