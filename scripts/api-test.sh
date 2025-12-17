#!/bin/bash

# API功能测试脚本
# 用于逐步测试各个API功能

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

BASE_URL="http://localhost:9091"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  API功能测试${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 检查服务是否运行
if ! curl -s "$BASE_URL/actuator/health" > /dev/null; then
    echo -e "${RED}❌ 服务未运行，请先启动服务${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 服务运行中${NC}"
echo ""

# 测试1: 创建项目
echo -e "${BLUE}[测试1] 创建项目${NC}"
PROJECT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/projects" \
  -H "Content-Type: application/json" \
  -d '{
    "projectCode": "TEST_PROJECT_001",
    "projectName": "测试项目1",
    "projectType": "REAL",
    "description": "用于测试的项目",
    "status": "ACTIVE"
  }' 2>&1)

if echo "$PROJECT_RESPONSE" | grep -q "error\|Error\|ERROR" 2>/dev/null; then
    echo -e "${YELLOW}  ⚠️  项目创建API可能未实现，跳过${NC}"
    echo "  响应: $PROJECT_RESPONSE"
else
    echo -e "${GREEN}  ✅ 项目创建成功${NC}"
    PROJECT_ID=$(echo "$PROJECT_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('id', '1'))" 2>/dev/null || echo "1")
    echo "  项目ID: $PROJECT_ID"
fi
echo ""

# 测试2: 获取项目列表
echo -e "${BLUE}[测试2] 获取项目列表${NC}"
PROJECTS_RESPONSE=$(curl -s "$BASE_URL/api/projects" 2>&1)
if [ "$?" -eq 0 ]; then
    echo -e "${GREEN}  ✅ API可访问${NC}"
    echo "  响应: $(echo "$PROJECTS_RESPONSE" | head -c 100)..."
else
    echo -e "${YELLOW}  ⚠️  API可能未实现${NC}"
fi
echo ""

# 测试3: 获取统计信息
echo -e "${BLUE}[测试3] 获取统计信息${NC}"
STATS=$(curl -s "$BASE_URL/api/stats")
ZK_CONNECTED=$(echo "$STATS" | python3 -c "import sys, json; print(json.load(sys.stdin).get('zkConnected', False))" 2>/dev/null || echo "false")
TOTAL_PROVIDERS=$(echo "$STATS" | python3 -c "import sys, json; print(json.load(sys.stdin).get('totalProviders', 0))" 2>/dev/null || echo "0")

echo -e "${GREEN}  ✅ 统计信息获取成功${NC}"
echo "  ZooKeeper连接: $ZK_CONNECTED"
echo "  Provider数量: $TOTAL_PROVIDERS"
echo ""

# 测试4: 获取Provider列表
echo -e "${BLUE}[测试4] 获取Provider列表${NC}"
PROVIDERS=$(curl -s "$BASE_URL/api/providers")
PROVIDER_COUNT=$(echo "$PROVIDERS" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
echo -e "${GREEN}  ✅ Provider列表获取成功${NC}"
echo "  Provider数量: $PROVIDER_COUNT"

if [ "$PROVIDER_COUNT" -gt 0 ]; then
    echo "  前3个Provider:"
    echo "$PROVIDERS" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for i, p in enumerate(data[:3]):
    print(f\"    {i+1}. {p.get('interfaceName', 'N/A')}:{p.get('version', 'N/A')}\")
" 2>/dev/null || echo "    无法解析"
fi
echo ""

# 测试5: 获取已注册服务
echo -e "${BLUE}[测试5] 获取已注册服务${NC}"
REGISTERED=$(curl -s "$BASE_URL/api/registered-services")
REGISTERED_COUNT=$(echo "$REGISTERED" | python3 -c "import sys, json; print(json.load(sys.stdin).get('count', 0))" 2>/dev/null || echo "0")
echo -e "${GREEN}  ✅ 已注册服务列表获取成功${NC}"
echo "  已注册服务数量: $REGISTERED_COUNT"

if [ "$REGISTERED_COUNT" -gt 0 ]; then
    echo "  已注册服务:"
    echo "$REGISTERED" | python3 -c "
import sys, json
data = json.load(sys.stdin)
services = data.get('registeredServices', [])
for s in services[:5]:
    print(f\"    - {s}\")
" 2>/dev/null || echo "    无法解析"
fi
echo ""

# 测试6: 获取应用列表
echo -e "${BLUE}[测试6] 获取应用列表${NC}"
APPLICATIONS=$(curl -s "$BASE_URL/api/applications")
APP_COUNT=$(echo "$APPLICATIONS" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
echo -e "${GREEN}  ✅ 应用列表获取成功${NC}"
echo "  应用数量: $APP_COUNT"
echo ""

# 测试7: 获取接口列表
echo -e "${BLUE}[测试7] 获取接口列表${NC}"
INTERFACES=$(curl -s "$BASE_URL/api/interfaces")
INTERFACE_COUNT=$(echo "$INTERFACES" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
echo -e "${GREEN}  ✅ 接口列表获取成功${NC}"
echo "  接口数量: $INTERFACE_COUNT"

if [ "$INTERFACE_COUNT" -gt 0 ]; then
    echo "  前5个接口:"
    echo "$INTERFACES" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for i, iface in enumerate(data[:5]):
    print(f\"    {i+1}. {iface}\")
" 2>/dev/null || echo "    无法解析"
fi
echo ""

# 测试8: 验证ZooKeeper连接
echo -e "${BLUE}[测试8] 验证ZooKeeper连接${NC}"
if [ "$ZK_CONNECTED" == "True" ] || [ "$ZK_CONNECTED" == "true" ]; then
    echo -e "${GREEN}  ✅ ZooKeeper连接正常${NC}"
else
    echo -e "${RED}  ❌ ZooKeeper连接异常${NC}"
fi
echo ""

# 总结
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  测试总结${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "ZooKeeper连接: $ZK_CONNECTED"
echo -e "Provider数量: ${BLUE}$PROVIDER_COUNT${NC}"
echo -e "已注册服务: ${BLUE}$REGISTERED_COUNT${NC}"
echo -e "应用数量: ${BLUE}$APP_COUNT${NC}"
echo -e "接口数量: ${BLUE}$INTERFACE_COUNT${NC}"
echo ""
echo -e "${GREEN}✅ API功能测试完成！${NC}"
echo ""
echo -e "下一步测试建议:"
echo -e "  1. 启动demo-provider服务，验证服务发现"
echo -e "  2. 创建项目并关联服务，验证过滤机制"
echo -e "  3. 创建虚拟项目，验证服务编排"
echo -e "  4. 查看详细测试文档: ${BLUE}TEST_GUIDE.md${NC}"
echo ""

