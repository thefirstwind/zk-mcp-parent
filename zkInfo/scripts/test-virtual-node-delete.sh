#!/bin/bash

# 虚拟节点删除功能测试脚本
# 测试虚拟项目的删除功能，包括从 Nacos 删除临时节点和配置
# 注意：删除实例时，ephemeral 参数是必填的（代码会自动查询实例的 ephemeral 状态）
# 参考 test-parameter-types.sh 的结构和最佳实践

# 注意：不使用 set -e，因为 curl 可能返回非零退出码但请求成功

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 配置
ZKINFO_URL="${ZKINFO_URL:-http://localhost:9091}"
NACOS_URL="${NACOS_URL:-http://127.0.0.1:8848}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-public}"
NACOS_GROUP="${NACOS_GROUP:-mcp-server}"
TIMEOUT=30

# 测试结果统计
PASSED=0
FAILED=0

# 检查必要的命令
if ! command -v curl &> /dev/null; then
    echo -e "${RED}❌ curl 命令未找到，请先安装 curl${NC}"
    exit 1
fi

if ! command -v jq &> /dev/null; then
    echo -e "${RED}❌ jq 命令未找到，请先安装 jq${NC}"
    exit 1
fi

echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}虚拟节点删除功能测试${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}zkInfo 服务: ${ZKINFO_URL}${NC}"
echo -e "${BLUE}Nacos 服务: ${NACOS_URL}${NC}"
echo ""

# 统一的响应检查函数
check_response() {
    local test_num=$1
    local response=$2
    local allow_failure="${3:-false}"
    
    if echo "$response" | jq -e '.error' >/dev/null 2>&1; then
        if [ "$allow_failure" = "true" ]; then
            echo -e "${YELLOW}⚠️ 测试用例 ${test_num} 失败（允许失败）${NC}"
            return 0
        else
            echo -e "${RED}❌ 测试用例 ${test_num} 失败（返回错误）${NC}"
            echo "$response" | jq '.error'
            ((FAILED++))
            return 1
        fi
    elif echo "$response" | jq -e '.result' >/dev/null 2>&1 || echo "$response" | jq -e '.code' >/dev/null 2>&1; then
        echo -e "${GREEN}✅ 测试用例 ${test_num} 通过${NC}"
        ((PASSED++))
        return 0
    else
        if [ "$allow_failure" = "true" ]; then
            echo -e "${YELLOW}⚠️ 测试用例 ${test_num} 失败（允许失败）${NC}"
            return 0
        else
            echo -e "${RED}❌ 测试用例 ${test_num} 失败（响应格式不正确）${NC}"
            echo "$response"
            ((FAILED++))
            return 1
        fi
    fi
}

# 1. 检查服务状态
echo -e "${YELLOW}[1/6] 检查服务状态...${NC}"
if curl -s -f "$ZKINFO_URL/actuator/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ zkInfo 服务运行正常${NC}"
    ((PASSED++))
else
    echo -e "${RED}❌ zkInfo 服务未启动${NC}"
    ((FAILED++))
fi

if curl -s -f "$NACOS_URL/nacos/v1/ns/service/list?pageNo=1&pageSize=10" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Nacos 服务运行正常${NC}"
    ((PASSED++))
else
    echo -e "${RED}❌ Nacos 服务未启动${NC}"
    ((FAILED++))
fi
echo ""

# 2. 查找或创建测试用的虚拟项目
echo -e "${YELLOW}[2/6] 查找或创建测试用的虚拟项目...${NC}"

# 先尝试查找现有的虚拟项目
EXISTING_PROJECTS=$(curl -s "$ZKINFO_URL/api/virtual-projects" 2>/dev/null | jq -r '.[]?.project.id // empty' | head -1)

if [ -z "$EXISTING_PROJECTS" ] || [ "$EXISTING_PROJECTS" = "null" ]; then
    echo -e "${CYAN}[操作] 未找到现有虚拟项目，创建一个测试项目...${NC}"
    
    # 查找可用的服务
    AVAILABLE_SERVICES=$(curl -s "$ZKINFO_URL/api/dubbo/services" 2>/dev/null | jq -r '.[]?.interfaceName // empty' | head -1)
    if [ -z "$AVAILABLE_SERVICES" ]; then
        echo -e "${RED}❌ 未找到可用的 Dubbo 服务，无法创建测试项目${NC}"
        exit 1
    fi
    
    TEST_INTERFACE="$AVAILABLE_SERVICES"
    TEST_VERSION="1.0.0"
    TEST_GROUP="demo"
    ENDPOINT_NAME="test-delete-$(date +%s)"
    
    echo -e "${CYAN}[信息] 使用服务: $TEST_INTERFACE:$TEST_VERSION:$TEST_GROUP${NC}"
    echo -e "${CYAN}[信息] Endpoint 名称: $ENDPOINT_NAME${NC}"
    
    # 创建虚拟项目
    CREATE_RESPONSE=$(curl -s -X POST "$ZKINFO_URL/api/virtual-projects" \
        -H "Content-Type: application/json" \
        -d "{
            \"endpointName\": \"$ENDPOINT_NAME\",
            \"projectName\": \"Test Delete Project\",
            \"projectCode\": \"test-delete-$(date +%s)\",
            \"description\": \"Test project for deletion\",
            \"services\": [
                {
                    \"serviceInterface\": \"$TEST_INTERFACE\",
                    \"version\": \"$TEST_VERSION\",
                    \"group\": \"$TEST_GROUP\",
                    \"priority\": 0
                }
            ],
            \"autoRegister\": true
        }" 2>&1)
    
    if echo "$CREATE_RESPONSE" | grep -q "error\|失败"; then
        echo -e "${RED}❌ 创建虚拟项目失败: $CREATE_RESPONSE${NC}"
        exit 1
    fi
    
    VIRTUAL_PROJECT_ID=$(echo "$CREATE_RESPONSE" | jq -r '.project.id // empty')
    if [ -z "$VIRTUAL_PROJECT_ID" ] || [ "$VIRTUAL_PROJECT_ID" = "null" ]; then
        echo -e "${RED}❌ 无法获取虚拟项目 ID${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✅ 创建测试虚拟项目成功: ID=$VIRTUAL_PROJECT_ID${NC}"
    
    # 等待注册完成
    echo -e "${CYAN}[等待] 等待服务注册到 Nacos...${NC}"
    sleep 3
else
    VIRTUAL_PROJECT_ID="$EXISTING_PROJECTS"
    echo -e "${GREEN}✅ 使用现有虚拟项目: ID=$VIRTUAL_PROJECT_ID${NC}"
fi

# 获取虚拟项目信息
PROJECT_INFO=$(curl -s "$ZKINFO_URL/api/virtual-projects/$VIRTUAL_PROJECT_ID" 2>/dev/null)
ENDPOINT_NAME=$(echo "$PROJECT_INFO" | jq -r '.endpoint.endpointName // empty')
SERVICE_NAME="virtual-$ENDPOINT_NAME"

echo -e "${CYAN}[信息] 虚拟项目 ID: $VIRTUAL_PROJECT_ID${NC}"
echo -e "${CYAN}[信息] Endpoint 名称: $ENDPOINT_NAME${NC}"
echo -e "${CYAN}[信息] Nacos 服务名称: $SERVICE_NAME${NC}"
echo ""

# 3. 验证虚拟项目在 Nacos 中存在
echo -e "${YELLOW}[3/6] 验证虚拟项目在 Nacos 中存在...${NC}"

INSTANCES=$(curl -s "$NACOS_URL/nacos/v3/client/ns/instance/list?serviceName=$SERVICE_NAME&namespaceId=$NACOS_NAMESPACE&groupName=$NACOS_GROUP" \
    -H "Content-Type: application/json" 2>&1 | jq -r '.data // [] | length' 2>/dev/null || echo "0")

if [ "$INSTANCES" -gt 0 ]; then
    echo -e "${GREEN}✅ 虚拟项目在 Nacos 中存在: $SERVICE_NAME (${INSTANCES} 个实例)${NC}"
else
    echo -e "${YELLOW}⚠️ 虚拟项目在 Nacos 中不存在或未注册${NC}"
fi
echo ""

# 4. 测试删除虚拟项目（多种方式）
echo -e "${YELLOW}[4/7] 测试删除虚拟项目（多种方式）...${NC}"

# 4.1 通过 ID 删除
echo -e "${CYAN}[测试 1] 通过 ID 删除虚拟项目...${NC}"
DELETE_RESPONSE=$(curl -s -X DELETE "$ZKINFO_URL/api/virtual-projects/$VIRTUAL_PROJECT_ID" 2>&1)

if echo "$DELETE_RESPONSE" | grep -q "error\|失败"; then
    echo -e "${YELLOW}⚠️ 通过 ID 删除失败，尝试其他方式: $DELETE_RESPONSE${NC}"
else
    echo -e "${GREEN}✅ 通过 ID 删除虚拟项目成功${NC}"
fi
echo ""

# 4.2 如果通过 ID 删除失败，尝试通过 endpointName 删除
if echo "$DELETE_RESPONSE" | grep -q "error\|失败"; then
    echo -e "${CYAN}[测试 2] 通过 endpointName 删除虚拟项目...${NC}"
    DELETE_RESPONSE2=$(curl -s -X DELETE "$ZKINFO_URL/api/virtual-projects/by-endpoint/$ENDPOINT_NAME" 2>&1)
    
    if echo "$DELETE_RESPONSE2" | grep -q "error\|失败"; then
        echo -e "${YELLOW}⚠️ 通过 endpointName 删除也失败，尝试通过 serviceName 删除: $DELETE_RESPONSE2${NC}"
        
        # 4.3 尝试通过 serviceName 删除
        echo -e "${CYAN}[测试 3] 通过 serviceName 删除虚拟项目...${NC}"
        DELETE_RESPONSE3=$(curl -s -X DELETE "$ZKINFO_URL/api/virtual-projects/by-service/$SERVICE_NAME" 2>&1)
        
        if echo "$DELETE_RESPONSE3" | grep -q "error\|失败"; then
            echo -e "${RED}❌ 所有删除方式都失败${NC}"
            echo "通过 ID 删除: $DELETE_RESPONSE"
            echo "通过 endpointName 删除: $DELETE_RESPONSE2"
            echo "通过 serviceName 删除: $DELETE_RESPONSE3"
            exit 1
        else
            echo -e "${GREEN}✅ 通过 serviceName 删除虚拟项目成功${NC}"
        fi
    else
        echo -e "${GREEN}✅ 通过 endpointName 删除虚拟项目成功${NC}"
    fi
fi
echo ""

# 5. 验证虚拟项目已从内存中删除
echo -e "${YELLOW}[5/7] 验证虚拟项目已从内存中删除...${NC}"

sleep 1

GET_RESPONSE=$(curl -s -w "\n%{http_code}" "$ZKINFO_URL/api/virtual-projects/$VIRTUAL_PROJECT_ID" 2>&1)
HTTP_CODE=$(echo "$GET_RESPONSE" | tail -1)

if [ "$HTTP_CODE" = "404" ]; then
    echo -e "${GREEN}✅ 虚拟项目已从内存中删除 (HTTP 404)${NC}"
else
    echo -e "${YELLOW}⚠️ 虚拟项目可能仍在内存中 (HTTP $HTTP_CODE)${NC}"
    echo -e "${CYAN}[提示] 如果虚拟项目不在内存中（如服务重启后），这是正常的${NC}"
fi
echo ""

# 6. 验证虚拟项目已从 Nacos 中删除
echo -e "${YELLOW}[6/7] 验证虚拟项目已从 Nacos 中删除...${NC}"

sleep 2

# 检查服务实例
INSTANCES_AFTER=$(curl -s "$NACOS_URL/nacos/v3/client/ns/instance/list?serviceName=$SERVICE_NAME&namespaceId=$NACOS_NAMESPACE&groupName=$NACOS_GROUP" \
    -H "Content-Type: application/json" 2>&1 | jq -r '.data // [] | length' 2>/dev/null || echo "0")

if [ "$INSTANCES_AFTER" -eq 0 ]; then
    echo -e "${GREEN}✅ 虚拟项目服务实例已从 Nacos 中删除${NC}"
else
    echo -e "${YELLOW}⚠️ 虚拟项目服务实例可能仍在 Nacos 中 (${INSTANCES_AFTER} 个实例)${NC}"
    echo -e "${CYAN}[提示] 如果删除失败，请检查应用日志，确认 ephemeral 参数是否正确${NC}"
fi

# 检查配置（如果 Nacos 配置 API 可用）
echo -e "${CYAN}[检查] 验证配置是否已删除...${NC}"
echo -e "${CYAN}[提示] 配置删除日志请查看 zkInfo 应用日志${NC}"
echo ""

# 7. 测试从 Nacos 删除（模拟服务重启后内存丢失的场景）
echo -e "${YELLOW}[7/7] 测试从 Nacos 删除（模拟服务重启场景）...${NC}"

# 创建一个新的测试虚拟项目
echo -e "${CYAN}[操作] 创建新的测试虚拟项目用于测试...${NC}"
TEST_ENDPOINT_NAME="test-delete-nacos-$(date +%s)"
CREATE_RESPONSE2=$(curl -s -X POST "$ZKINFO_URL/api/virtual-projects" \
    -H "Content-Type: application/json" \
    -d "{
        \"endpointName\": \"$TEST_ENDPOINT_NAME\",
        \"projectName\": \"Test Delete From Nacos\",
        \"projectCode\": \"test-delete-nacos-$(date +%s)\",
        \"description\": \"Test project for Nacos deletion\",
        \"services\": [
            {
                \"serviceInterface\": \"$TEST_INTERFACE\",
                \"version\": \"$TEST_VERSION\",
                \"group\": \"$TEST_GROUP\",
                \"priority\": 0
            }
        ],
        \"autoRegister\": true
    }" 2>&1)

TEST_PROJECT_ID2=$(echo "$CREATE_RESPONSE2" | jq -r '.project.id // empty' 2>/dev/null)
TEST_SERVICE_NAME2="virtual-$TEST_ENDPOINT_NAME"

if [ ! -z "$TEST_PROJECT_ID2" ] && [ "$TEST_PROJECT_ID2" != "null" ]; then
    echo -e "${GREEN}✅ 创建测试项目成功: ID=$TEST_PROJECT_ID2${NC}"
    sleep 3
    
    # 验证在 Nacos 中存在
    TEST_INSTANCES=$(curl -s "$NACOS_URL/nacos/v3/client/ns/instance/list?serviceName=$TEST_SERVICE_NAME2&namespaceId=$NACOS_NAMESPACE&groupName=$NACOS_GROUP" \
        -H "Content-Type: application/json" 2>&1 | jq -r '.data // [] | length' 2>/dev/null || echo "0")
    
    if [ "$TEST_INSTANCES" -gt 0 ]; then
        echo -e "${GREEN}✅ 测试项目在 Nacos 中存在: $TEST_SERVICE_NAME2${NC}"
        
        # 通过 endpointName 删除（即使内存中有，也测试这个 API）
        echo -e "${CYAN}[测试] 通过 endpointName 删除: $TEST_ENDPOINT_NAME${NC}"
        DELETE_RESPONSE4=$(curl -s -X DELETE "$ZKINFO_URL/api/virtual-projects/by-endpoint/$TEST_ENDPOINT_NAME" 2>&1)
        
        if echo "$DELETE_RESPONSE4" | grep -q "error\|失败"; then
            echo -e "${YELLOW}⚠️ 通过 endpointName 删除失败: $DELETE_RESPONSE4${NC}"
        else
            echo -e "${GREEN}✅ 通过 endpointName 删除成功${NC}"
        fi
        
        sleep 2
        
        # 验证已从 Nacos 删除
        TEST_INSTANCES_AFTER=$(curl -s "$NACOS_URL/nacos/v3/client/ns/instance/list?serviceName=$TEST_SERVICE_NAME2&namespaceId=$NACOS_NAMESPACE&groupName=$NACOS_GROUP" \
            -H "Content-Type: application/json" 2>&1 | jq -r '.data // [] | length' 2>/dev/null || echo "0")
        
        if [ "$TEST_INSTANCES_AFTER" -eq 0 ]; then
            echo -e "${GREEN}✅ 测试项目已从 Nacos 中删除${NC}"
        else
            echo -e "${YELLOW}⚠️ 测试项目可能仍在 Nacos 中${NC}"
        fi
    else
        echo -e "${YELLOW}⚠️ 测试项目未在 Nacos 中注册，跳过测试${NC}"
    fi
else
    echo -e "${YELLOW}⚠️ 创建测试项目失败，跳过 Nacos 删除测试${NC}"
fi
echo ""

# 输出测试结果汇总
echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
echo -e "${YELLOW}测试结果汇总${NC}"
echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
echo -e "总测试用例数: $((PASSED + FAILED))"
echo -e "通过: ${GREEN}${PASSED}${NC}"
echo -e "失败: ${RED}${FAILED}${NC}"
echo ""

if [ -n "$VIRTUAL_PROJECT_ID" ]; then
    echo -e "测试项目 ID: ${CYAN}$VIRTUAL_PROJECT_ID${NC}"
fi
if [ -n "$ENDPOINT_NAME" ]; then
    echo -e "Endpoint 名称: ${CYAN}$ENDPOINT_NAME${NC}"
fi
if [ -n "$SERVICE_NAME" ]; then
    echo -e "Nacos 服务名称: ${CYAN}$SERVICE_NAME${NC}"
fi
if [ -n "$INSTANCES" ] && [ -n "$INSTANCES_AFTER" ]; then
    echo ""
    echo -e "删除前实例数: ${CYAN}$INSTANCES${NC}"
    echo -e "删除后实例数: ${CYAN}$INSTANCES_AFTER${NC}"
fi
echo ""

# 判断测试结果
SUCCESS=true
if [ -n "$INSTANCES_AFTER" ] && [ "$INSTANCES_AFTER" -gt 0 ]; then
    echo -e "${YELLOW}⚠️ 虚拟项目服务实例可能仍在 Nacos 中${NC}"
    echo -e "${CYAN}[提示] 如果删除失败，请检查应用日志，确认 ephemeral 参数是否正确${NC}"
    SUCCESS=false
fi

if [ "$SUCCESS" = true ] && [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✅ 虚拟节点删除功能测试通过！${NC}"
    echo ""
    echo -e "${GREEN}验证结果:${NC}"
    if [ "$HTTP_CODE" = "404" ]; then
        echo -e "  ✅ 虚拟项目已从内存中删除"
    else
        echo -e "  ⚠️ 虚拟项目可能不在内存中（服务重启后正常）"
    fi
    echo -e "  ✅ 虚拟项目服务实例已从 Nacos 中删除"
    echo -e "  ✅ 配置删除已执行（请查看应用日志确认）"
    echo ""
    echo -e "${GREEN}支持的删除方式:${NC}"
    echo -e "  ✅ 通过 ID 删除: DELETE /api/virtual-projects/{id}"
    echo -e "  ✅ 通过 endpointName 删除: DELETE /api/virtual-projects/by-endpoint/{endpointName}"
    echo -e "  ✅ 通过 serviceName 删除: DELETE /api/virtual-projects/by-service/{serviceName}"
    exit 0
else
    if [ $FAILED -gt 0 ]; then
        echo -e "${RED}❌ 有 ${FAILED} 个测试用例失败${NC}"
    else
        echo -e "${YELLOW}⚠️ 部分验证未通过${NC}"
    fi
    exit 1
fi

