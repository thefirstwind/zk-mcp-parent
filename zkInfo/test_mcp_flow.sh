#!/bin/bash

echo "=== 测试 Nacos MCP HTTP API 完整流程 ==="
echo

# 1. 创建虚拟项目/MCP服务
echo "1. 创建虚拟项目和 MCP 服务..."
CREATE_RESULT=$(curl -s -X POST "http://localhost:9091/api/virtual-projects" \
  -H "Content-Type: application/json" \
  -d '{
    "projectName": "test-nacos-mcp-api",
    "endpointName": "test-nacos-mcp-api",
    "description": "Testing Nacos MCP HTTP API",
    "services": [
        {
            "serviceInterface": "com.pajk.provider1.service.ProductService",
            "version": "1.0.0",
            "serviceGroup": "demo",
            "methods": ["getProductById"]
        }
    ]
  }')

echo "$CREATE_RESULT" | jq .
MCP_NAME=$(echo "$CREATE_RESULT" | jq -r '.endpoint.mcpServiceName')
ENDPOINT_NAME=$(echo "$CREATE_RESULT" | jq -r '.endpoint.endpointName')

echo
echo "✅ 创建成功: MCP服务名称 = $MCP_NAME"
echo

# 等待注册完成
sleep 2

# 2. 使用 Nacos HTTP API 查询 MCP 服务
echo "2. 通过 Nacos API 查询 MCP 服务..."

# 获取 access token
TOKEN=$(curl -s -X POST "http://127.0.0.1:8848/nacos/v1/auth/login" \
  -d "username=nacos&password=nacos" | jq -r '.accessToken')

echo "Access Token: ${TOKEN:0:20}..."

# 列出所有 MCP 服务（调试用）
echo "列出所有 MCP 服务(前5个):"
curl -s -G "http://127.0.0.1:8848/v3/console/ai/mcp/list" \
  --data-urlencode "accessToken=$TOKEN" \
  --data-urlencode "namespaceId=public" \
  --data-urlencode "pageNo=1" \
  --data-urlencode "pageSize=5" | jq .

# 查询 MCP 服务详情
echo "查询 MCP 服务详情: $MCP_NAME"
MCP_DETAIL=$(curl -s -G "http://127.0.0.1:8848/v3/console/ai/mcp" \
  --data-urlencode "accessToken=$TOKEN" \
  --data-urlencode "namespaceId=public" \
  --data-urlencode "mcpName=$MCP_NAME")

echo
echo "MCP 服务详情:"
echo "$MCP_DETAIL" | jq .
echo

# 3. 删除虚拟项目
echo "3. 删除虚拟项目..."
DELETE_RESULT=$(curl -s -X DELETE "http://localhost:9091/api/virtual-projects/$ENDPOINT_NAME")
echo "$DELETE_RESULT" | jq .
echo

# 等待删除完成
sleep 2

# 4. 验证删除（应该返回 404 或空）
echo "4. 验证删除（应该查不到服务）..."
VERIFY=$(curl -s -X GET "http://127.0.0.1:8848/v3/console/ai/mcp?namespaceId=public&mcpName=$MCP_NAME&accessToken=$TOKEN")
echo "$VERIFY" | jq .

echo
echo "=== 测试完成 ==="
