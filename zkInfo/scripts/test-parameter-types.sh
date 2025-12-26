#!/bin/bash

# 测试脚本：验证参数类型获取功能
# 此脚本用于测试各种场景下的参数类型获取，确保从 ZooKeeper metadata 正确获取参数类型
# 测试场景包括：
# - 无参数方法
# - 单个参数方法（Long, String 等基础类型）
# - 多个参数方法
# - POJO 对象参数

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
BASE_URL="${BASE_URL:-http://mcp-bridge.test:9091}"

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

# 检查服务是否可用
echo -e "${BLUE}检查服务连接...${NC}"
if ! curl -s -f "${BASE_URL}/health" > /dev/null 2>&1 && ! curl -s -f "${BASE_URL}/" > /dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  无法连接到服务 ${BASE_URL}，但继续测试...${NC}"
    echo -e "${YELLOW}   提示：可以通过环境变量 BASE_URL 指定服务地址${NC}"
    echo -e "${YELLOW}   例如：BASE_URL=http://localhost:8080 ./test-parameter-types.sh${NC}"
    echo ""
fi

# 统一的响应检查函数
check_response() {
    local test_num=$1
    local response=$2
    
    if echo "$response" | jq -e '.error' >/dev/null 2>&1; then
        echo -e "${RED}❌ 测试用例 ${test_num} 失败（返回错误）${NC}"
        echo "$response" | jq '.error'
        ((FAILED++))
        return 1
    elif echo "$response" | jq -e '.result' >/dev/null 2>&1; then
        echo -e "${GREEN}✅ 测试用例 ${test_num} 通过${NC}"
        # 只显示简要结果，不显示完整响应（避免输出过长）
        if echo "$response" | jq -e '.result.content[0].text' >/dev/null 2>&1; then
            local result_text=$(echo "$response" | jq -r '.result.content[0].text' | head -c 200)
            echo "   结果: ${result_text}..."
        fi
        ((PASSED++))
        return 0
    else
        echo -e "${RED}❌ 测试用例 ${test_num} 失败（响应格式不正确）${NC}"
        echo "$response"
        ((FAILED++))
        return 1
    fi
}

echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}测试脚本：验证参数类型获取功能${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}服务地址: ${BASE_URL}${NC}"
echo ""

# 测试用例 1: getProductById - Long 类型参数
echo -e "${YELLOW}测试用例 1: getProductById（Long 类型参数）${NC}"
echo "预期：参数类型 = java.lang.Long, 参数数量 = 1"
echo "方法签名: Product getProductById(Long productId)"
echo ""

response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-1" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-1",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.ProductService.getProductById",
      "arguments": {
        "productId": 1
      }
    }
  }')

check_response "1" "$response"
echo ""

# 测试用例 2: getAllUsers - 无参数方法
echo -e "${YELLOW}测试用例 2: getAllUsers（无参数方法）${NC}"
echo "预期：参数类型 = 无参数, 参数数量 = 0"
echo "方法签名: List<User> getAllUsers()"
echo ""

response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-2" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-2",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.UserService.getAllUsers",
      "arguments": {}
    }
  }')

check_response "2" "$response"
echo ""

# 测试用例 3: getOrderById - String 类型参数
echo -e "${YELLOW}测试用例 3: getOrderById（String 类型参数）${NC}"
echo "预期：参数类型 = java.lang.String, 参数数量 = 1"
echo "方法签名: Order getOrderById(String orderId)"
echo ""

response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-3" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-3",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.OrderService.getOrderById",
      "arguments": {
        "orderId": "ORD001"
      }
    }
  }')

check_response "3" "$response"
echo ""

# 测试用例 4: getProductsByCategory - String 类型参数
echo -e "${YELLOW}测试用例 4: getProductsByCategory（String 类型参数）${NC}"
echo "预期：参数类型 = java.lang.String, 参数数量 = 1"
echo "方法签名: List<Product> getProductsByCategory(String category)"
echo ""

response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-4" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-4",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.ProductService.getProductsByCategory",
      "arguments": {
        "category": "Electronics"
      }
    }
  }')

check_response "4" "$response"
echo ""

# 测试用例 5: updateOrderStatus - 两个 String 类型参数
echo -e "${YELLOW}测试用例 5: updateOrderStatus（两个 String 类型参数）${NC}"
echo "预期：参数类型 = java.lang.String, java.lang.String, 参数数量 = 2"
echo "方法签名: Order updateOrderStatus(String orderId, String status)"
echo ""

response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-5" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-5",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.OrderService.updateOrderStatus",
      "arguments": {
        "orderId": "ORD001",
        "status": "COMPLETED"
      }
    }
  }')

check_response "5" "$response"
echo ""

# 测试用例 6: updateStock - Long 和 int 类型参数
echo -e "${YELLOW}测试用例 6: updateStock（Long 和 int 类型参数）${NC}"
echo "预期：参数类型 = java.lang.Long, int, 参数数量 = 2"
echo "方法签名: boolean updateStock(Long productId, int stock)"
echo ""

response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-6" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-6",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.ProductService.updateStock",
      "arguments": {
        "productId": 1,
        "stock": 100
      }
    }
  }')

check_response "6" "$response"
echo ""

# 测试用例 7: getOrdersByUserId - Long 类型参数
echo -e "${YELLOW}测试用例 7: getOrdersByUserId（Long 类型参数）${NC}"
echo "预期：参数类型 = java.lang.Long, 参数数量 = 1"
echo "方法签名: List<Order> getOrdersByUserId(Long userId)"
echo ""

response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-7" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-7",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.OrderService.getOrdersByUserId",
      "arguments": {
        "userId": 1001
      }
    }
  }')

check_response "7" "$response"
echo ""

# 测试用例 8: createUser - POJO 对象参数
echo -e "${YELLOW}测试用例 8: createUser（POJO 对象参数）${NC}"
echo "预期：参数类型 = com.zkinfo.demo.model.User, 参数数量 = 1"
echo "方法签名: User createUser(User user)"
echo ""

response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-8" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-8",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.UserService.createUser",
      "arguments": {
        "user": {
          "id": null,
          "username": "testuser",
          "email": "test@example.com",
          "phone": "13800138000",
          "realName": "Test User",
          "age": 25,
          "gender": "M",
          "status": "ACTIVE",
          "createTime": null,
          "updateTime": null
        }
      }
    }
  }')

check_response "8" "$response"
echo ""

# 测试用例 9: getProductPrice - Long 类型参数
echo -e "${YELLOW}测试用例 9: getProductPrice（Long 类型参数）${NC}"
echo "预期：参数类型 = java.lang.Long, 参数数量 = 1"
echo "方法签名: Double getProductPrice(Long productId)"
echo ""

response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-9" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-9",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.ProductService.getProductPrice",
      "arguments": {
        "productId": 1
      }
    }
  }')

check_response "9" "$response"
echo ""

# 输出测试结果
echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
echo -e "${YELLOW}测试结果汇总${NC}"
echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
echo -e "总测试用例数: $((PASSED + FAILED))"
echo -e "通过: ${GREEN}${PASSED}${NC}"
echo -e "失败: ${RED}${FAILED}${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✅ 所有测试用例通过！${NC}"
    exit 0
else
    echo -e "${RED}❌ 有 ${FAILED} 个测试用例失败${NC}"
    exit 1
fi
