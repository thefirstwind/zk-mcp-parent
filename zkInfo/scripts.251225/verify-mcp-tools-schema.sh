#!/bin/bash

# 验证 MCP Tools Schema 生成是否正确
# 检查注册到 Nacos 的 mcp-tools.json 中的 inputSchema

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Nacos 配置
NACOS_URL="${NACOS_URL:-http://localhost:8848}"
TOOLS_GROUP="mcp-tools"

echo "=========================================="
echo "验证 MCP Tools Schema 生成"
echo "=========================================="
echo ""

# 1. 查找所有 mcp-tools.json 配置
echo "📋 步骤 1: 查找所有 mcp-tools.json 配置..."
TOOLS_CONFIGS=$(curl -s "${NACOS_URL}/nacos/v1/cs/configs?dataId=&group=${TOOLS_GROUP}&pageNo=1&pageSize=100" | jq -r '.pageItems[]? | select(.dataId | contains("mcp-tools.json")) | .dataId' 2>/dev/null || echo "")

if [ -z "$TOOLS_CONFIGS" ]; then
    echo -e "${YELLOW}⚠️  未找到任何 mcp-tools.json 配置${NC}"
    echo "请先注册虚拟项目或 Dubbo 服务"
    exit 1
fi

echo -e "${GREEN}✅ 找到以下 mcp-tools.json 配置:${NC}"
echo "$TOOLS_CONFIGS" | while read -r dataId; do
    echo "  - $dataId"
done
echo ""

# 2. 检查每个配置中的工具 schema
echo "📋 步骤 2: 检查工具 schema..."
echo ""

ERROR_COUNT=0
SUCCESS_COUNT=0

echo "$TOOLS_CONFIGS" | while read -r dataId; do
    echo "----------------------------------------"
    echo "检查配置: $dataId"
    echo "----------------------------------------"
    
    # 获取配置内容
    CONFIG_CONTENT=$(curl -s "${NACOS_URL}/nacos/v1/cs/configs?dataId=${dataId}&group=${TOOLS_GROUP}")
    
    if [ -z "$CONFIG_CONTENT" ] || [ "$CONFIG_CONTENT" == "null" ]; then
        echo -e "${RED}❌ 无法获取配置内容${NC}"
        ERROR_COUNT=$((ERROR_COUNT + 1))
        continue
    fi
    
    # 解析 JSON 并检查工具
    TOOLS_COUNT=$(echo "$CONFIG_CONTENT" | jq '.tools | length' 2>/dev/null || echo "0")
    
    if [ "$TOOLS_COUNT" -eq 0 ]; then
        echo -e "${YELLOW}⚠️  配置中没有工具${NC}"
        continue
    fi
    
    echo -e "${GREEN}✅ 找到 $TOOLS_COUNT 个工具${NC}"
    echo ""
    
    # 检查每个工具的 inputSchema
    echo "$CONFIG_CONTENT" | jq -r '.tools[]? | "\(.name)|\(.inputSchema | tostring)"' 2>/dev/null | while IFS='|' read -r toolName inputSchemaJson; do
        if [ -z "$toolName" ]; then
            continue
        fi
        
        echo "  工具: $toolName"
        
        # 解析 inputSchema
        HAS_ARGS=$(echo "$inputSchemaJson" | jq '.properties.args // empty' 2>/dev/null || echo "")
        HAS_TIMEOUT=$(echo "$inputSchemaJson" | jq '.properties.timeout // empty' 2>/dev/null || echo "")
        REQUIRED=$(echo "$inputSchemaJson" | jq '.required // []' 2>/dev/null || echo "[]")
        PROPERTIES=$(echo "$inputSchemaJson" | jq '.properties // {}' 2>/dev/null || echo "{}")
        
        # 检查是否有固定 args 和 timeout
        if [ -n "$HAS_ARGS" ] && [ -n "$HAS_TIMEOUT" ]; then
            # 检查 required 中是否包含 args
            REQUIRES_ARGS=$(echo "$REQUIRED" | jq 'contains(["args"])' 2>/dev/null || echo "false")
            
            if [ "$REQUIRES_ARGS" == "true" ]; then
                echo -e "    ${RED}❌ 问题: 固定需要 args 和 timeout${NC}"
                echo "    inputSchema:"
                echo "$inputSchemaJson" | jq '.' 2>/dev/null | sed 's/^/      /' || echo "      (无法解析)"
                ERROR_COUNT=$((ERROR_COUNT + 1))
            else
                echo -e "    ${GREEN}✅ 有 args 和 timeout，但 args 不是必需的${NC}"
            fi
        else
            # 检查是否有方法参数
            PARAM_COUNT=$(echo "$PROPERTIES" | jq 'keys | length' 2>/dev/null || echo "0")
            
            if [ "$PARAM_COUNT" -gt 0 ]; then
                echo -e "    ${GREEN}✅ 使用实际方法参数（不是固定 args）${NC}"
                echo "    参数:"
                echo "$PROPERTIES" | jq 'keys[]' 2>/dev/null | sed 's/^/      - /' || echo "      (无法解析)"
                SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
            else
                echo -e "    ${YELLOW}⚠️  没有参数（可能是无参数方法）${NC}"
                SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
            fi
        fi
        echo ""
    done
done

echo ""
echo "=========================================="
echo "验证完成"
echo "=========================================="

# 3. 特别检查 UserService 的工具
echo ""
echo "📋 步骤 3: 特别检查 UserService 工具..."
echo ""

USER_SERVICE_TOOLS=$(echo "$TOOLS_CONFIGS" | head -1 | xargs -I {} sh -c "curl -s '${NACOS_URL}/nacos/v1/cs/configs?dataId={}&group=${TOOLS_GROUP}' | jq -r '.tools[]? | select(.name | contains(\"UserService\")) | .name'" 2>/dev/null || echo "")

if [ -n "$USER_SERVICE_TOOLS" ]; then
    echo "$USER_SERVICE_TOOLS" | while read -r toolName; do
        echo "检查工具: $toolName"
        
        # 从第一个配置中获取工具详情
        FIRST_CONFIG=$(echo "$TOOLS_CONFIGS" | head -1)
        TOOL_DETAIL=$(curl -s "${NACOS_URL}/nacos/v1/cs/configs?dataId=${FIRST_CONFIG}&group=${TOOLS_GROUP}" | jq -r ".tools[]? | select(.name == \"$toolName\")" 2>/dev/null)
        
        if [ -n "$TOOL_DETAIL" ]; then
            INPUT_SCHEMA=$(echo "$TOOL_DETAIL" | jq '.inputSchema' 2>/dev/null)
            
            # 检查 getAllUsers（应该无参数）
            if [[ "$toolName" == *"getAllUsers"* ]]; then
                HAS_ARGS=$(echo "$INPUT_SCHEMA" | jq '.properties.args // empty' 2>/dev/null || echo "")
                REQUIRED=$(echo "$INPUT_SCHEMA" | jq '.required // []' 2>/dev/null || echo "[]")
                REQUIRES_ARGS=$(echo "$REQUIRED" | jq 'contains(["args"])' 2>/dev/null || echo "false")
                
                if [ "$REQUIRES_ARGS" == "true" ] || [ -n "$HAS_ARGS" ]; then
                    echo -e "  ${RED}❌ getAllUsers 不应该需要 args 参数${NC}"
                    echo "  inputSchema:"
                    echo "$INPUT_SCHEMA" | jq '.' 2>/dev/null | sed 's/^/    /' || echo "    (无法解析)"
                else
                    echo -e "  ${GREEN}✅ getAllUsers 正确：无参数方法${NC}"
                    echo "  inputSchema:"
                    echo "$INPUT_SCHEMA" | jq '.' 2>/dev/null | sed 's/^/    /' || echo "    (无法解析)"
                fi
            fi
            
            # 检查 getUserById（应该有一个 userId 参数）
            if [[ "$toolName" == *"getUserById"* ]]; then
                HAS_USER_ID=$(echo "$INPUT_SCHEMA" | jq '.properties.userId // empty' 2>/dev/null || echo "")
                HAS_ARGS=$(echo "$INPUT_SCHEMA" | jq '.properties.args // empty' 2>/dev/null || echo "")
                REQUIRED=$(echo "$INPUT_SCHEMA" | jq '.required // []' 2>/dev/null || echo "[]")
                REQUIRES_ARGS=$(echo "$REQUIRED" | jq 'contains(["args"])' 2>/dev/null || echo "false")
                
                if [ -n "$HAS_USER_ID" ]; then
                    echo -e "  ${GREEN}✅ getUserById 正确：有 userId 参数${NC}"
                    echo "  inputSchema:"
                    echo "$INPUT_SCHEMA" | jq '.' 2>/dev/null | sed 's/^/    /' || echo "    (无法解析)"
                elif [ "$REQUIRES_ARGS" == "true" ] || [ -n "$HAS_ARGS" ]; then
                    echo -e "  ${RED}❌ getUserById 应该使用 userId 参数，而不是 args${NC}"
                    echo "  inputSchema:"
                    echo "$INPUT_SCHEMA" | jq '.' 2>/dev/null | sed 's/^/    /' || echo "    (无法解析)"
                else
                    echo -e "  ${YELLOW}⚠️  getUserById 参数格式未知${NC}"
                    echo "  inputSchema:"
                    echo "$INPUT_SCHEMA" | jq '.' 2>/dev/null | sed 's/^/    /' || echo "    (无法解析)"
                fi
            fi
        fi
        echo ""
    done
else
    echo -e "${YELLOW}⚠️  未找到 UserService 工具${NC}"
fi

echo ""
echo "=========================================="
echo "验证脚本完成"
echo "=========================================="

