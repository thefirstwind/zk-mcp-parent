#!/bin/bash

# 验证并修复 inputSchema 生成问题
# 检查实际注册到 Nacos 的格式，并验证修复是否生效

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Nacos 配置
NACOS_URL="${NACOS_URL:-http://localhost:8848}"
TOOLS_GROUP="mcp-tools"

echo "=========================================="
echo "验证并修复 MCP Tools Schema"
echo "=========================================="
echo ""

# 1. 检查 Nacos 连接
echo "📋 步骤 1: 检查 Nacos 连接..."
if ! curl -s "${NACOS_URL}/nacos/v1/ns/operator/metrics" > /dev/null 2>&1; then
    echo -e "${RED}❌ 无法连接到 Nacos: ${NACOS_URL}${NC}"
    echo "请确保 Nacos 正在运行"
    exit 1
fi
echo -e "${GREEN}✅ Nacos 连接正常${NC}"
echo ""

# 2. 查找 mcp-data-analysis 或 data-analysis 相关的配置
echo "📋 步骤 2: 查找虚拟项目配置..."
echo ""

# 查找包含 data-analysis 的配置
DATA_ANALYSIS_CONFIGS=$(curl -s "${NACOS_URL}/nacos/v1/cs/configs?dataId=&group=${TOOLS_GROUP}&pageNo=1&pageSize=100" 2>/dev/null | \
    jq -r '.pageItems[]? | select(.dataId | contains("data-analysis") or contains("mcp-data-analysis")) | .dataId' 2>/dev/null || echo "")

if [ -z "$DATA_ANALYSIS_CONFIGS" ]; then
    echo -e "${YELLOW}⚠️  未找到 data-analysis 相关的配置${NC}"
    echo "查找所有 mcp-tools.json 配置..."
    
    ALL_CONFIGS=$(curl -s "${NACOS_URL}/nacos/v1/cs/configs?dataId=&group=${TOOLS_GROUP}&pageNo=1&pageSize=100" 2>/dev/null | \
        jq -r '.pageItems[]? | select(.dataId | contains("mcp-tools.json")) | .dataId' 2>/dev/null || echo "")
    
    if [ -z "$ALL_CONFIGS" ]; then
        echo -e "${RED}❌ 未找到任何 mcp-tools.json 配置${NC}"
        echo "请先注册虚拟项目或 Dubbo 服务"
        exit 1
    fi
    
    echo -e "${GREEN}找到以下配置:${NC}"
    echo "$ALL_CONFIGS" | while read -r dataId; do
        echo "  - $dataId"
    done
    
    # 使用第一个配置
    FIRST_CONFIG=$(echo "$ALL_CONFIGS" | head -1)
    DATA_ANALYSIS_CONFIGS="$FIRST_CONFIG"
    echo ""
    echo "使用配置: $FIRST_CONFIG"
else
    echo -e "${GREEN}✅ 找到 data-analysis 相关配置:${NC}"
    echo "$DATA_ANALYSIS_CONFIGS" | while read -r dataId; do
        echo "  - $dataId"
    done
fi
echo ""

# 3. 检查配置中的工具 schema
echo "📋 步骤 3: 检查工具 schema..."
echo ""

FIRST_CONFIG=$(echo "$DATA_ANALYSIS_CONFIGS" | head -1)
CONFIG_CONTENT=$(curl -s "${NACOS_URL}/nacos/v1/cs/configs?dataId=${FIRST_CONFIG}&group=${TOOLS_GROUP}")

if [ -z "$CONFIG_CONTENT" ] || [ "$CONFIG_CONTENT" == "null" ]; then
    echo -e "${RED}❌ 无法获取配置内容${NC}"
    exit 1
fi

# 检查 UserService 的工具
echo "检查 UserService 相关工具:"
echo ""

# 获取所有工具
TOOLS=$(echo "$CONFIG_CONTENT" | jq -r '.tools[]? | @json' 2>/dev/null || echo "")

if [ -z "$TOOLS" ]; then
    echo -e "${YELLOW}⚠️  配置中没有工具${NC}"
    exit 1
fi

# 检查每个工具
ERRORS=0
SUCCESS=0

echo "$TOOLS" | while read -r toolJson; do
    TOOL_NAME=$(echo "$toolJson" | jq -r '.name // ""' 2>/dev/null || echo "")
    
    if [[ "$TOOL_NAME" != *"UserService"* ]]; then
        continue
    fi
    
    echo "----------------------------------------"
    echo "工具: $TOOL_NAME"
    echo "----------------------------------------"
    
    INPUT_SCHEMA=$(echo "$toolJson" | jq '.inputSchema' 2>/dev/null || echo "{}")
    
    # 检查是否有 args
    HAS_ARGS=$(echo "$INPUT_SCHEMA" | jq '.properties.args // empty' 2>/dev/null || echo "")
    HAS_TIMEOUT=$(echo "$INPUT_SCHEMA" | jq '.properties.timeout // empty' 2>/dev/null || echo "")
    REQUIRED=$(echo "$INPUT_SCHEMA" | jq '.required // []' 2>/dev/null || echo "[]")
    REQUIRES_ARGS=$(echo "$REQUIRED" | jq 'contains(["args"])' 2>/dev/null || echo "false")
    
    # 获取所有属性
    PROPERTIES=$(echo "$INPUT_SCHEMA" | jq '.properties // {}' 2>/dev/null || echo "{}")
    PROPERTY_KEYS=$(echo "$PROPERTIES" | jq 'keys[]' 2>/dev/null || echo "")
    
    echo "inputSchema:"
    echo "$INPUT_SCHEMA" | jq '.' 2>/dev/null | sed 's/^/  /' || echo "  (无法解析)"
    echo ""
    
    # 检查 getAllUsers
    if [[ "$TOOL_NAME" == *"getAllUsers"* ]]; then
        echo -e "${BLUE}检查 getAllUsers (应该无参数):${NC}"
        
        if [ "$REQUIRES_ARGS" == "true" ]; then
            echo -e "  ${RED}❌ 错误: required 中包含 args${NC}"
            ERRORS=$((ERRORS + 1))
        elif [ -n "$HAS_ARGS" ]; then
            echo -e "  ${YELLOW}⚠️  警告: 有 args 属性，但不是必需的${NC}"
        else
            echo -e "  ${GREEN}✅ 正确: 无 args 参数${NC}"
            SUCCESS=$((SUCCESS + 1))
        fi
        
        # 检查是否有其他参数（除了 timeout）
        OTHER_PARAMS=$(echo "$PROPERTY_KEYS" | grep -v "timeout" | grep -v "^$" || echo "")
        if [ -n "$OTHER_PARAMS" ]; then
            echo -e "  ${YELLOW}⚠️  警告: 有其他参数: $OTHER_PARAMS${NC}"
        fi
    fi
    
    # 检查 getUserById
    if [[ "$TOOL_NAME" == *"getUserById"* ]]; then
        echo -e "${BLUE}检查 getUserById (应该有 userId 参数):${NC}"
        
        HAS_USER_ID=$(echo "$INPUT_SCHEMA" | jq '.properties.userId // empty' 2>/dev/null || echo "")
        
        if [ -n "$HAS_USER_ID" ]; then
            echo -e "  ${GREEN}✅ 正确: 有 userId 参数${NC}"
            SUCCESS=$((SUCCESS + 1))
        elif [ "$REQUIRES_ARGS" == "true" ] || [ -n "$HAS_ARGS" ]; then
            echo -e "  ${RED}❌ 错误: 使用 args 而不是 userId${NC}"
            ERRORS=$((ERRORS + 1))
        else
            echo -e "  ${YELLOW}⚠️  警告: 参数格式未知${NC}"
        fi
    fi
    
    echo ""
done

echo ""
echo "=========================================="
echo "验证结果"
echo "=========================================="
echo ""

if [ $ERRORS -gt 0 ]; then
    echo -e "${RED}❌ 发现 $ERRORS 个问题${NC}"
    echo ""
    echo "问题分析:"
    echo "1. 可能的原因: 运行时找不到接口类 (ClassNotFoundException)"
    echo "2. 解决方案: 确保 demo-provider 的类在 classpath 中"
    echo "3. 或者: 检查日志中是否有 'Interface class not found' 警告"
    echo ""
    echo "建议:"
    echo "- 检查 zkInfo 启动日志"
    echo "- 确认 demo-provider 已编译并可用"
    echo "- 重新注册虚拟项目"
else
    echo -e "${GREEN}✅ 所有检查通过 ($SUCCESS 个工具正确)${NC}"
fi

echo ""
echo "=========================================="
echo "验证脚本完成"
echo "=========================================="

