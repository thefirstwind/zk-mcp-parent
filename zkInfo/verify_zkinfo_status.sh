#!/bin/bash

# =================================================================
# zkInfo 节点状态与动态 IP 清理验证脚本
# =================================================================

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'
YELLOW='\033[1;33m'

NACOS_URL="http://127.0.0.1:8848"
ZKINFO_SERVICE_NAME="zkinfo" # 默认应用名
NAMESPACE="public"

echo -e "${YELLOW}=== 验证 zkInfo 节点自注册与动态 IP 管理 ===${NC}"

# 1. 登录 Nacos
TOKEN=$(curl -s -X POST "${NACOS_URL}/nacos/v1/auth/login" -d "username=nacos&password=nacos" | jq -r '.accessToken')

# 2. 查询当前活跃的 zkInfo 节点
echo -e "\n${YELLOW}1. 正在从 Nacos 查询活跃的 zkInfo 节点...${NC}"
ZKINFO_INSTANCES=$(curl -s -G "${NACOS_URL}/v3/client/ns/instance/list" \
  --data-urlencode "namespaceId=${NAMESPACE}" \
  --data-urlencode "serviceName=${ZKINFO_SERVICE_NAME}" \
  --data-urlencode "groupName=mcp-server" \
  --data-urlencode "accessToken=${TOKEN}")

echo "$ZKINFO_INSTANCES" | jq -r '.data[] | "节点: \(.ip):\(.port) \t健康: \(.healthy) \t元数据: \(.metadata | to_entries | map("\(.key)=\(.value)") | join(", "))"'

# 3. 检查元数据是否正确上报 (如 version, protocol)
if [[ $ZKINFO_INSTANCES == *"protocol=mcp-sse"* ]]; then
    echo -e "${GREEN}✅ zkInfo 节点元数据包含 protocol=mcp-sse${NC}"
else
    echo -e "${RED}❌ zkInfo 节点元数据缺失关键信息${NC}"
fi

# 4. 验证清理逻辑 (模拟检查)
# 注意：实际清理逻辑在服务启动或注册时触发。
# 我们可以通过查看 zkInfo 的日志来确认清理动作。
echo -e "\n${YELLOW}2. 检查 zkInfo 日志中的清理记录...${NC}"
LOG_FILE="/Users/shine/projects.mcp-router-sse-parent/zk-mcp-parent/zkInfo/logs/zkinfo.log"

if [ -f "$LOG_FILE" ]; then
    CLEANUP_LOGS=$(grep "Found stale instance in Nacos" "$LOG_FILE" | tail -n 5)
    if [ ! -z "$CLEANUP_LOGS" ]; then
        echo -e "${GREEN}✅ 发现清理过时节点的记录:${NC}"
        echo "$CLEANUP_LOGS"
    else
        echo -e "${NC}目前日志中未发现清理动作（可能是环境干净，没有死节点）${NC}"
    fi
else
    echo -e "${NC}日志文件未找到: $LOG_FILE${NC}"
fi

echo -e "\n${GREEN}=== 验证结束 ===${NC}"
