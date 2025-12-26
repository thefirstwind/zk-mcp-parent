#!/bin/bash

# zkInfo 项目代码优化脚本
# 删除未使用的服务和冗余逻辑

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC_DIR="$PROJECT_ROOT/src/main/java/com/zkinfo"

echo "🔍 开始分析 zkInfo 项目的代码..."

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 1. 检查未使用的服务
echo ""
echo "📋 检查未使用的服务..."

# HeartbeatMonitorService - 未被使用
if ! grep -r "HeartbeatMonitorService" "$SRC_DIR/controller" "$SRC_DIR/config" --include="*.java" | grep -v "HeartbeatMonitorService.java" | grep -q "HeartbeatMonitorService"; then
    echo -e "${YELLOW}⚠️  HeartbeatMonitorService 未被使用${NC}"
    echo "   文件: $SRC_DIR/service/HeartbeatMonitorService.java"
    echo "   建议: 如果不需要心跳监控功能，可以删除"
fi

# DubboServiceInfoAdapter - 未被使用
if ! grep -r "DubboServiceInfoAdapter" "$SRC_DIR/controller" "$SRC_DIR/config" "$SRC_DIR/service" --include="*.java" | grep -v "DubboServiceInfoAdapter.java" | grep -q "DubboServiceInfoAdapter"; then
    echo -e "${YELLOW}⚠️  DubboServiceInfoAdapter 未被使用${NC}"
    echo "   文件: $SRC_DIR/service/DubboServiceInfoAdapter.java"
    echo "   建议: 如果 DubboServiceDbService 不再使用，可以删除"
fi

# DubboToMcpRegistrationService - 可能未使用
if ! grep -r "dubboToMcpRegistrationService\." "$SRC_DIR/service" --include="*.java" | grep -q "dubboToMcpRegistrationService"; then
    echo -e "${YELLOW}⚠️  DubboToMcpRegistrationService 可能未使用${NC}"
    echo "   文件: $SRC_DIR/service/DubboToMcpRegistrationService.java"
    echo "   建议: 检查是否被实际调用，如果未使用，可以删除（NacosMcpRegistrationService 已使用 Nacos v3 API）"
fi

# ProviderInfoDbService - 未被使用
if ! grep -r "ProviderInfoDbService" "$SRC_DIR/controller" "$SRC_DIR/config" --include="*.java" | grep -v "ProviderInfoDbService.java" | grep -q "ProviderInfoDbService"; then
    echo -e "${YELLOW}⚠️  ProviderInfoDbService 未被使用${NC}"
    echo "   文件: $SRC_DIR/service/ProviderInfoDbService.java"
    echo "   建议: 如果已完全迁移到新表结构，可以删除"
fi

# 2. 检查冗余的 MCP 协议处理
echo ""
echo "📋 检查冗余的 MCP 协议处理..."

# McpProtocolService + McpController
if [ -f "$SRC_DIR/service/McpProtocolService.java" ] && [ -f "$SRC_DIR/controller/McpController.java" ]; then
    echo -e "${YELLOW}⚠️  发现冗余的 MCP 协议处理实现${NC}"
    echo "   McpProtocolService: $SRC_DIR/service/McpProtocolService.java"
    echo "   McpController: $SRC_DIR/controller/McpController.java"
    echo "   McpMessageController: $SRC_DIR/controller/McpMessageController.java"
    echo "   建议: 检查 McpController 是否被实际使用，如果未使用，可以删除或标记为废弃"
fi

echo ""
echo "✅ 分析完成！"
echo ""
echo "📝 详细分析报告请查看: $PROJECT_ROOT/docs/CODE_OPTIMIZATION_ANALYSIS.md"
echo ""
echo "🚀 下一步操作："
echo "   1. 确认哪些服务确实未被使用"
echo "   2. 备份代码"
echo "   3. 执行删除操作"
echo "   4. 运行测试确保功能正常"

