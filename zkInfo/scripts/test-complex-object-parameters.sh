#!/bin/bash

# 复杂对象参数测试脚本
# 测试 MCP tools/call 时复杂对象参数的处理

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

BASE_URL="${BASE_URL:-http://mcp-bridge.test:9091}"
MCP_ROUTER_URL="${MCP_ROUTER_URL:-http://mcp-bridge.test}"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}复杂对象参数测试${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

PASSED=0
FAILED=0

# 测试用例 1: 创建 User（简单 POJO）
echo -e "${YELLOW}测试用例 1: 创建 User（简单 POJO）${NC}"
response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-complex-obj-1" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-1",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.UserService.createUser",
      "arguments": {
        "user": {
          "username": "testuser",
          "email": "test@example.com",
          "phone": "13800138000",
          "realName": "Test User",
          "age": 25,
          "gender": "M",
          "status": "ACTIVE"
        }
      }
    }
  }')

if echo "$response" | jq -e '.result' >/dev/null 2>&1; then
    echo -e "${GREEN}✅ 测试用例 1 通过${NC}"
    echo "$response" | jq '.'
    ((PASSED++))
else
    echo -e "${RED}❌ 测试用例 1 失败${NC}"
    echo "$response"
    ((FAILED++))
fi
echo ""

# 测试用例 2: 创建 Order（嵌套对象）
echo -e "${YELLOW}测试用例 2: 创建 Order（嵌套对象，包含 orderItems）${NC}"
response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-complex-obj-2" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-2",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.OrderService.createOrder",
      "arguments": {
        "order": {
          "userId": 1001,
          "status": "PENDING",
          "totalAmount": 199.99,
          "shippingAddress": "123 Main St",
          "receiverName": "John Doe",
          "receiverPhone": "13800138000",
          "orderItems": [
            {
              "productId": 1,
              "productName": "Product A",
              "price": 99.99,
              "quantity": 2,
              "subtotal": 199.98
            },
            {
              "productId": 2,
              "productName": "Product B",
              "price": 50.00,
              "quantity": 1,
              "subtotal": 50.00
            }
          ]
        }
      }
    }
  }')

if echo "$response" | jq -e '.result' >/dev/null 2>&1; then
    echo -e "${GREEN}✅ 测试用例 2 通过${NC}"
    echo "$response" | jq '.'
    ((PASSED++))
else
    echo -e "${RED}❌ 测试用例 2 失败${NC}"
    echo "$response"
    ((FAILED++))
fi
echo ""

# 测试用例 3: 创建 Product（简单 POJO）
echo -e "${YELLOW}测试用例 3: 创建 Product（简单 POJO）${NC}"
response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-complex-obj-3" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-3",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.ProductService.createProduct",
      "arguments": {
        "product": {
          "name": "Test Product",
          "description": "A test product",
          "category": "Electronics",
          "price": 99.99,
          "stock": 100,
          "brand": "Test Brand",
          "status": "ACTIVE"
        }
      }
    }
  }')

if echo "$response" | jq -e '.result' >/dev/null 2>&1; then
    echo -e "${GREEN}✅ 测试用例 3 通过${NC}"
    echo "$response" | jq '.'
    ((PASSED++))
else
    echo -e "${RED}❌ 测试用例 3 失败${NC}"
    echo "$response"
    ((FAILED++))
fi
echo ""

# 测试用例 4: 更新 Order（嵌套对象）
echo -e "${YELLOW}测试用例 4: 更新 Order（嵌套对象）${NC}"
response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-complex-obj-4" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-4",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.OrderService.updateOrder",
      "arguments": {
        "order": {
          "id": "order-123",
          "userId": 1001,
          "status": "PAID",
          "totalAmount": 249.99,
          "orderItems": [
            {
              "productId": 1,
              "productName": "Product A",
              "price": 99.99,
              "quantity": 3,
              "subtotal": 299.97
            }
          ]
        }
      }
    }
  }')

if echo "$response" | jq -e '.result' >/dev/null 2>&1; then
    echo -e "${GREEN}✅ 测试用例 4 通过${NC}"
    echo "$response" | jq '.'
    ((PASSED++))
else
    echo -e "${RED}❌ 测试用例 4 失败${NC}"
    echo "$response"
    ((FAILED++))
fi
echo ""

# 测试用例 5: 批量创建 Users（List<User>）
echo -e "${YELLOW}测试用例 5: 批量创建 Users（List<User>）${NC}"
response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-complex-obj-5" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-5",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.UserService.batchCreateUsers",
      "arguments": {
        "users": [
          {
            "username": "user1",
            "email": "user1@example.com",
            "phone": "13800138001",
            "realName": "User 1",
            "age": 20
          },
          {
            "username": "user2",
            "email": "user2@example.com",
            "phone": "13800138002",
            "realName": "User 2",
            "age": 25
          }
        ]
      }
    }
  }')

if echo "$response" | jq -e '.result' >/dev/null 2>&1; then
    echo -e "${GREEN}✅ 测试用例 5 通过${NC}"
    echo "$response" | jq '.'
    ((PASSED++))
else
    echo -e "${RED}❌ 测试用例 5 失败${NC}"
    echo "$response"
    ((FAILED++))
fi
echo ""

# 总结
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}测试总结${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "通过: ${GREEN}${PASSED}${NC}"
echo -e "失败: ${RED}${FAILED}${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✅ 所有测试用例通过！${NC}"
    exit 0
else
    echo -e "${RED}❌ 部分测试用例失败${NC}"
    exit 1
fi

