#!/bin/bash

# 复杂对象参数测试脚本
# 测试 MCP tools/call 时复杂对象参数的处理
# 参考 test-parameter-types.sh 的结构和最佳实践

# 注意：不使用 set -e，因为 curl 可能返回非零退出码但请求成功

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
BASE_URL="${BASE_URL:-http://mcp-bridge.test:9091}"
MCP_ROUTER_URL="${MCP_ROUTER_URL:-http://mcp-bridge.test}"

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
    echo -e "${YELLOW}   例如：BASE_URL=http://localhost:8080 ./test-complex-object-parameters.sh${NC}"
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
echo -e "${BLUE}复杂对象参数测试${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}服务地址: ${BASE_URL}${NC}"
echo ""

# 测试用例 1: 创建 User（包含所有字段，能传值的都传实际值）
# User 类所有字段（10个）：
# - id: Long - 由服务端自动生成，传入 null
# - username: String - 必需字段，传入实际值
# - email: String - 必需字段，传入实际值
# - phone: String - 可选字段，传入实际值
# - realName: String - 可选字段，传入实际值
# - age: Integer - 可选字段，传入实际值
# - gender: String (M/F) - 可选字段，传入实际值
# - status: String - 可选字段，传入实际值（虽然服务端会自动设置，但也可以传）
# - createTime: LocalDateTime - 由服务端自动生成，传入 null
# - updateTime: LocalDateTime - 由服务端自动生成，传入 null
echo -e "${YELLOW}测试用例 1: 创建 User（包含所有字段，能传值的都传实际值）${NC}"
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

check_response "1" "$response"
echo ""

# 测试用例 1.1: 创建 User（包含所有字段，组合不同的参数值）
echo -e "${YELLOW}测试用例 1.1: 创建 User（包含所有字段，组合不同的参数值）${NC}"
response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-complex-obj-1-1" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-1-1",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.UserService.createUser",
      "arguments": {
        "user": {
          "id": null,
          "username": "alice",
          "email": "alice@example.com",
          "phone": "13900139001",
          "realName": "Alice Wang",
          "age": 28,
          "gender": "F",
          "status": "ACTIVE",
          "createTime": null,
          "updateTime": null
        }
      }
    }
  }')

check_response "1.1" "$response"
echo ""

# 测试用例 2: 创建 Order（包含所有字段，能传值的都传实际值）
# Order 类所有字段（11个）：
# - id: String - 由服务端自动生成，传入 null
# - userId: Long - 必需字段，传入实际值
# - status: String - 可选字段，传入实际值（虽然服务端会自动设置，但也可以传）
# - totalAmount: Double - 可选字段，传入实际值（虽然服务端会自动计算，但也可以传）
# - shippingAddress: String - 可选字段，传入实际值
# - receiverName: String - 可选字段，传入实际值
# - receiverPhone: String - 可选字段，传入实际值
# - remark: String - 可选字段，传入实际值
# - orderItems: List<OrderItem> - 可选字段，传入实际值（包含所有OrderItem字段）
# - createTime: LocalDateTime - 由服务端自动生成，传入 null
# - updateTime: LocalDateTime - 由服务端自动生成，传入 null
# OrderItem 所有字段（5个）：
# - productId: Long - 必需字段，传入实际值
# - productName: String - 可选字段，传入实际值
# - price: Double - 必需字段，传入实际值
# - quantity: Integer - 必需字段，传入实际值
# - subtotal: Double - 可选字段，传入实际值（可以计算，但也可以传）
echo -e "${YELLOW}测试用例 2: 创建 Order（包含所有字段，能传值的都传实际值）${NC}"
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
          "id": null,
          "userId": 1001,
          "status": "PENDING",
          "totalAmount": 249.99,
          "shippingAddress": "123 Main St, Beijing, China",
          "receiverName": "John Doe",
          "receiverPhone": "13800138000",
          "remark": "Please handle with care",
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
          ],
          "createTime": null,
          "updateTime": null
        }
      }
    }
  }')

check_response "2" "$response"
echo ""

# 测试用例 3: 获取产品信息（ProductService 没有 createProduct 方法，改用 getProductById）
# Product 类所有字段：
# - id: Long - 必需字段（用于查询）
# - name: String - 产品名称
# - description: String - 产品描述
# - category: String - 产品分类
# - price: Double - 产品价格
# - stock: Integer - 库存数量
# - imageUrl: String - 产品图片URL
# - brand: String - 品牌
# - status: String - 产品状态
# - salesCount: Integer - 销量
# - rating: Double - 评分
# - createTime: LocalDateTime - 创建时间
# - updateTime: LocalDateTime - 更新时间
echo -e "${YELLOW}测试用例 3: 获取产品信息（getProductById，返回包含所有字段的 Product）${NC}"
response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-complex-obj-3" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-3",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.ProductService.getProductById",
      "arguments": {
        "productId": 1
      }
    }
  }')

check_response "3" "$response"
echo ""

# 测试用例 4: 获取订单信息（OrderService 没有 updateOrder 方法，改用 getOrderById）
# 返回的 Order 对象包含所有字段
echo -e "${YELLOW}测试用例 4: 获取订单信息（getOrderById，返回包含所有字段的 Order）${NC}"
response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-complex-obj-4" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-4",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.OrderService.getOrderById",
      "arguments": {
        "orderId": "ORD001"
      }
    }
  }')

check_response "4" "$response"
echo ""

# 测试用例 2.1: 创建 Order（不同的参数组合，包含所有字段）
echo -e "${YELLOW}测试用例 2.1: 创建 Order（不同的参数组合，包含所有字段）${NC}"
response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-complex-obj-2-1" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-2-1",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.OrderService.createOrder",
      "arguments": {
        "order": {
          "id": null,
          "userId": 2002,
          "status": "PENDING",
          "totalAmount": 5999.00,
          "shippingAddress": "456 Oak Avenue, Shanghai, China",
          "receiverName": "Jane Smith",
          "receiverPhone": "13900139002",
          "remark": "Gift wrapping requested",
          "orderItems": [
            {
              "productId": 3,
              "productName": "Product C",
              "price": 2999.00,
              "quantity": 1,
              "subtotal": 2999.00
            },
            {
              "productId": 4,
              "productName": "Product D",
              "price": 1500.00,
              "quantity": 2,
              "subtotal": 3000.00
            }
          ],
          "createTime": null,
          "updateTime": null
        }
      }
    }
  }')

check_response "2.1" "$response"
echo ""

# 测试用例 5: 获取所有用户列表（无参数方法，返回 List<User>）
echo -e "${YELLOW}测试用例 5: 获取所有用户列表（无参数方法，返回 List<User>）${NC}"
response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-complex-obj-5" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-5",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.UserService.getAllUsers",
      "arguments": {}
    }
  }')

check_response "5" "$response"
echo ""

# 测试用例 6: 更新 User（包含所有字段，能传值的都传实际值）
# 注意：需要先有一个用户ID，这里假设使用测试用例1创建的用户ID
echo -e "${YELLOW}测试用例 6: 更新 User（包含所有字段，能传值的都传实际值）${NC}"
response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-complex-obj-6" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-6",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.UserService.updateUser",
      "arguments": {
        "user": {
          "id": 1,
          "username": "updateduser",
          "email": "updated@example.com",
          "phone": "13900139003",
          "realName": "Updated User",
          "age": 30,
          "gender": "F",
          "status": "ACTIVE",
          "createTime": null,
          "updateTime": null
        }
      }
    }
  }')

check_response "6" "$response"
echo ""

# 测试用例 7: 根据ID获取用户（返回包含所有字段的 User）
echo -e "${YELLOW}测试用例 7: 根据ID获取用户（getUserById，返回包含所有字段的 User）${NC}"
response=$(curl -s -X POST "${BASE_URL}/mcp/message?sessionId=test-complex-obj-7" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-7",
    "method": "tools/call",
    "params": {
      "name": "com.zkinfo.demo.service.UserService.getUserById",
      "arguments": {
        "userId": 1
      }
    }
  }')

check_response "7" "$response"
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

