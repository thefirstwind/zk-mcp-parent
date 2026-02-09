#!/bin/bash

# =================================================================
# 虚拟项目全流程验证脚本 (Virtual Project Full Flow Validation)
# =================================================================

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color
YELLOW='\033[1;33m'

ZKINFO_URL="http://localhost:9091"
NACOS_URL="http://127.0.0.1:8848"
NAMESPACE="public"

echo -e "${YELLOW}=== 开始虚拟项目注册与验证全流程 ===${NC}"

# 1. 登录 Nacos 获取 Token
echo -e "\n${YELLOW}1. 正在登录 Nacos...${NC}"
LOGIN_RESPONSE=$(curl -s -X POST "${NACOS_URL}/nacos/v1/auth/login" \
  -d "username=nacos&password=nacos")
TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.accessToken')

if [ "$TOKEN" == "null" ] || [ -z "$TOKEN" ]; then
    echo -e "${RED}❌ Nacos 登录失败，请检查 Nacos 是否启动${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Nacos 登录成功${NC}"

# 2. 创建虚拟项目
PROJECT_NAME="order-analysis-test"
ENDPOINT_NAME="order-analysis-test"

echo -e "\n${YELLOW}2. 正在创建虚拟项目: ${PROJECT_NAME}...${NC}"
CREATE_DATA='{
    "projectName": "'$PROJECT_NAME'",
    "endpointName": "'$ENDPOINT_NAME'",
    "description": "订单数据分析虚拟项目测试",
    "services": [
        {
            "serviceInterface": "com.pajk.provider2.service.OrderService",
            "version": "1.0.0",
            "serviceGroup": "demo",
            "priority": 1
        }
    ],
    "autoRegister": true
}'

CREATE_RESULT=$(curl -s -X POST "${ZKINFO_URL}/api/virtual-projects" \
  -H "Content-Type: application/json" \
  -d "$CREATE_DATA")

echo $CREATE_RESULT | jq .

# 检查是否创建成功
if [[ $CREATE_RESULT == *"projectName"* ]]; then
    echo -e "${GREEN}✅ 虚拟项目创建指令发送成功${NC}"
else
    echo -e "${RED}❌ 虚拟项目创建失败${NC}"
    exit 1
fi

# 3. 等待 Nacos 注册完成
echo -e "\n${YELLOW}3. 等待 Nacos 注册同步 (5s)...${NC}"
sleep 5

# 4. 验证 Nacos 中的服务实例和元数据
SERVICE_NAME="virtual-${ENDPOINT_NAME}"
echo -e "\n${YELLOW}4. 验证 Nacos 服务实例: ${SERVICE_NAME}...${NC}"

# 使用 Nacos v3 API 查询实例
INSTANCE_INFO=$(curl -s -G "${NACOS_URL}/v3/client/ns/instance/list" \
  --data-urlencode "namespaceId=${NAMESPACE}" \
  --data-urlencode "serviceName=${SERVICE_NAME}" \
  --data-urlencode "groupName=mcp-server" \
  --data-urlencode "accessToken=${TOKEN}")

echo $INSTANCE_INFO | jq .

# 检查元数据中是否包含预期字段
if [[ $INSTANCE_INFO == *"sseEndpoint"* ]] && [[ $INSTANCE_INFO == *"protocol"* ]]; then
    echo -e "${GREEN}✅ Nacos 元数据验证成功: 包含 sseEndpoint 和 protocol${NC}"
else
    echo -e "${RED}❌ Nacos 元数据缺失关键字段${NC}"
fi

# 5. 验证工具列表查询
echo -e "\n${YELLOW}5. 验证通过 zkInfo 接口查询工具列表...${NC}"
TOOLS_RESULT=$(curl -s -X GET "${ZKINFO_URL}/api/virtual-projects/tools?endpointName=${ENDPOINT_NAME}")

TOOL_COUNT=$(echo $TOOLS_RESULT | jq '. | length')
echo -e "获取到工具数量: ${GREEN}${TOOL_COUNT}${NC}"
echo $TOOLS_RESULT | jq '.[0] | {name, description}'

if [ "$TOOL_COUNT" -gt 0 ]; then
    echo -e "${GREEN}✅ 工具列表同步成功${NC}"
else
    echo -e "${RED}❌ 工具列表为空，请检查 Dubbo 服务是否在线及元数据上报${NC}"
fi

# 6. (可选) 清理环境 - 删除测试项目
echo -e "\n${YELLOW}6. 是否删除测试项目? (y/n)${NC}"
# 注意：在非交互模式下默认不删除，或者可以根据需要开启
# read -p "" -n 1 -r
# echo
# if [[ $REPLY =~ ^[Yy]$ ]]; then
#    echo -e "${YELLOW}正在清理测试项目...${NC}"
#    curl -s -X DELETE "${ZKINFO_URL}/api/virtual-projects/${ENDPOINT_NAME}" | jq .
#    echo -e "${GREEN}✅ 测试项目已删除${NC}"
# fi

echo -e "\n${GREEN}=== 自动化验证完成 ===${NC}"
