#!/bin/bash

# 功能完整性验证脚本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

SERVER_URL="http://localhost:9091"
PASSED=0
FAILED=0

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  功能完整性验证${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 测试函数
test_endpoint() {
    local name=$1
    local url=$2
    local expected_status=${3:-200}
    
    echo -e "${YELLOW}测试: ${name}${NC}"
    response=$(curl -s -w "\n%{http_code}" "$url" 2>&1)
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" == "$expected_status" ]; then
        echo -e "${GREEN}  ✅ 通过 (HTTP $http_code)${NC}"
        if [ ! -z "$body" ] && [ "$body" != "null" ]; then
            echo "  响应: $(echo "$body" | head -c 100)..."
        fi
        ((PASSED++))
        return 0
    else
        echo -e "${RED}  ❌ 失败 (HTTP $http_code, 期望 $expected_status)${NC}"
        ((FAILED++))
        return 1
    fi
}

# 1. 基础健康检查
echo -e "${BLUE}[1/10] 基础健康检查${NC}"
test_endpoint "健康检查" "$SERVER_URL/actuator/health"
echo ""

# 2. 统计信息
echo -e "${BLUE}[2/10] 统计信息端点${NC}"
test_endpoint "统计信息" "$SERVER_URL/api/stats"
echo ""

# 3. 已注册服务
echo -e "${BLUE}[3/10] 已注册服务端点${NC}"
test_endpoint "已注册服务列表" "$SERVER_URL/api/registered-services"
echo ""

# 4. Provider列表
echo -e "${BLUE}[4/10] Provider列表端点${NC}"
test_endpoint "Provider列表" "$SERVER_URL/api/providers"
echo ""

# 5. 应用列表
echo -e "${BLUE}[5/10] 应用列表端点${NC}"
test_endpoint "应用列表" "$SERVER_URL/api/applications"
echo ""

# 6. 接口列表
echo -e "${BLUE}[6/10] 接口列表端点${NC}"
test_endpoint "接口列表" "$SERVER_URL/api/interfaces"
echo ""

# 7. API文档
echo -e "${BLUE}[7/10] API文档端点${NC}"
test_endpoint "OpenAPI文档" "$SERVER_URL/v3/api-docs"
echo ""

# 8. Swagger UI (302重定向是正常的)
echo -e "${BLUE}[8/10] Swagger UI${NC}"
response=$(curl -s -w "\n%{http_code}" "$SERVER_URL/swagger-ui.html" 2>&1)
http_code=$(echo "$response" | tail -n1)
if [ "$http_code" == "200" ] || [ "$http_code" == "302" ]; then
    echo -e "${GREEN}  ✅ 通过 (HTTP $http_code)${NC}"
    ((PASSED++))
else
    echo -e "${RED}  ❌ 失败 (HTTP $http_code)${NC}"
    ((FAILED++))
fi
echo ""

# 9. ZooKeeper连接状态
echo -e "${BLUE}[9/10] ZooKeeper连接状态${NC}"
stats_response=$(curl -s "$SERVER_URL/api/stats")
if echo "$stats_response" | grep -q '"zkConnected":true'; then
    echo -e "${GREEN}  ✅ ZooKeeper连接正常${NC}"
    ((PASSED++))
else
    echo -e "${RED}  ❌ ZooKeeper连接异常${NC}"
    ((FAILED++))
fi
echo ""

# 10. 服务组件检查
echo -e "${BLUE}[10/10] 服务组件检查${NC}"
echo "检查关键服务类是否已加载..."

# 检查日志中是否有服务初始化信息
if [ -f "logs/zkinfo.log" ]; then
    if grep -q "ServiceCollectionFilterService\|ProjectManagementService\|VirtualProjectService" logs/zkinfo.log 2>/dev/null; then
        echo -e "${GREEN}  ✅ 关键服务类已加载${NC}"
        ((PASSED++))
    else
        echo -e "${YELLOW}  ⚠️  无法确认服务类加载状态（检查日志）${NC}"
    fi
    
    # 检查是否有错误
    error_count=$(grep -i "ERROR\|Exception" logs/zkinfo.log 2>/dev/null | grep -v "DubboShutdownHook" | wc -l || echo "0")
    if [ "$error_count" -eq 0 ]; then
        echo -e "${GREEN}  ✅ 无错误日志${NC}"
        ((PASSED++))
    else
        echo -e "${YELLOW}  ⚠️  发现 $error_count 个错误日志（请检查）${NC}"
    fi
else
    echo -e "${YELLOW}  ⚠️  日志文件不存在${NC}"
fi
echo ""

# 总结
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  验证结果总结${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "通过: ${GREEN}$PASSED${NC}"
echo -e "失败: ${RED}$FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✅ 所有功能验证通过！${NC}"
    exit 0
else
    echo -e "${RED}❌ 部分功能验证失败，请检查上述错误${NC}"
    exit 1
fi

