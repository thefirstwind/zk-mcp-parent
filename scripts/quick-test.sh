#!/bin/bash

# 快速功能测试脚本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

BASE_URL="http://localhost:9091"
PASSED=0
FAILED=0

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  快速功能测试${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 测试函数
test_endpoint() {
    local name=$1
    local url=$2
    
    echo -e "${YELLOW}测试: $name${NC}"
    response=$(curl -s -w "\n%{http_code}" "$url" 2>&1)
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" == "200" ]; then
        echo -e "${GREEN}  ✅ 通过${NC}"
        ((PASSED++))
        return 0
    else
        echo -e "${RED}  ❌ 失败 (HTTP $http_code)${NC}"
        ((FAILED++))
        return 1
    fi
}

# 1. 健康检查
test_endpoint "健康检查" "$BASE_URL/actuator/health"

# 2. 统计信息
test_endpoint "统计信息" "$BASE_URL/api/stats"

# 3. Provider列表
test_endpoint "Provider列表" "$BASE_URL/api/providers"

# 4. 已注册服务
test_endpoint "已注册服务" "$BASE_URL/api/registered-services"

# 5. 应用列表
test_endpoint "应用列表" "$BASE_URL/api/applications"

# 6. 接口列表
test_endpoint "接口列表" "$BASE_URL/api/interfaces"

# 7. API文档
test_endpoint "API文档" "$BASE_URL/v3/api-docs"

# 统计信息
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  测试结果${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

PROVIDER_COUNT=$(curl -s "$BASE_URL/api/providers" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
REGISTERED_COUNT=$(curl -s "$BASE_URL/api/registered-services" | python3 -c "import sys, json; print(json.load(sys.stdin).get('count', 0))" 2>/dev/null || echo "0")
ZK_CONNECTED=$(curl -s "$BASE_URL/api/stats" | python3 -c "import sys, json; print('✅' if json.load(sys.stdin).get('zkConnected') else '❌')" 2>/dev/null || echo "❌")

echo -e "通过: ${GREEN}$PASSED${NC}"
echo -e "失败: ${RED}$FAILED${NC}"
echo ""
echo -e "Provider数量: ${BLUE}$PROVIDER_COUNT${NC}"
echo -e "已注册服务: ${BLUE}$REGISTERED_COUNT${NC}"
echo -e "ZooKeeper连接: $ZK_CONNECTED"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✅ 所有测试通过！${NC}"
    exit 0
else
    echo -e "${RED}❌ 部分测试失败${NC}"
    exit 1
fi

