#!/bin/bash

# 启动所有服务的脚本
set -e

PROJECT_ROOT="/Users/shine/projects/zk-mcp-parent"

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║  🚀 启动所有服务                                              ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# 步骤 1: 清理端口
echo "📌 步骤 1: 清理端口..."
for port in 8083 20883 8080 9091 8081; do
    lsof -ti:$port | xargs kill -9 2>/dev/null && echo "  ✅ 端口 $port 已释放" || echo "  ⏭  端口 $port 未被占用"
done
echo ""

# 步骤 2: 启动 demo-provider (Dubbo 服务提供者)
echo "📌 步骤 2: 启动 demo-provider (端口 8083, Dubbo 20883)..."
cd "$PROJECT_ROOT/demo-provider"
nohup mvn spring-boot:run > logs/demo-provider.log 2>&1 &
DEMO_PID=$!
echo "  PID: $DEMO_PID"
echo "  ⏳ 等待 demo-provider 启动..."
sleep 15

# 检查是否启动成功
if curl -s http://localhost:8083/actuator/health > /dev/null 2>&1; then
    echo "  ✅ demo-provider 启动成功"
else
    echo "  ❌ demo-provider 启动失败，查看日志: $PROJECT_ROOT/demo-provider/logs/demo-provider.log"
    exit 1
fi
echo ""

# 步骤 3: 启动 zkInfo (MCP Server)
echo "📌 步骤 3: 启动 zkInfo/MCP Server (端口 9091)..."
cd "$PROJECT_ROOT/zkInfo"
nohup mvn spring-boot:run > logs/zkinfo.log 2>&1 &
MCP_PID=$!
echo "  PID: $MCP_PID"
echo "  ⏳ 等待 zkInfo 启动..."
sleep 20

# 检查是否启动成功
if curl -s http://localhost:9091/actuator/health > /dev/null 2>&1; then
    echo "  ✅ zkInfo/MCP Server 启动成功"
else
    echo "  ❌ zkInfo 启动失败，查看日志: $PROJECT_ROOT/zkInfo/logs/zkinfo.log"
    exit 1
fi
echo ""

# 步骤 4: 检查 MCP 工具是否可用
echo "📌 步骤 4: 检查 MCP 工具..."
TOOL_COUNT=$(curl -s -X POST http://localhost:9091/mcp/jsonrpc -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}' | jq '.result.tools | length')
echo "  ✅ 发现 $TOOL_COUNT 个 MCP 工具"
echo ""

# 步骤 5: 启动 mcp-ai-client (AI 客户端)
echo "📌 步骤 5: 启动 mcp-ai-client (端口 8081)..."
cd "$PROJECT_ROOT/mcp-ai-client"

# 检查 API Key
if [ -z "$DEEPSEEK_API_KEY" ] || [ "$DEEPSEEK_API_KEY" = "test-key" ]; then
    echo "  ⚠️  警告: DEEPSEEK_API_KEY 未设置或使用测试值"
    echo "  请设置: export DEEPSEEK_API_KEY=sk-your-api-key"
    read -p "  是否继续? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

nohup mvn spring-boot:run > logs/app.log 2>&1 &
AI_PID=$!
echo "  PID: $AI_PID"
echo "  ⏳ 等待 mcp-ai-client 启动..."
sleep 15

# 检查是否启动成功
if curl -s http://localhost:8081/actuator/health > /dev/null 2>&1; then
    echo "  ✅ mcp-ai-client 启动成功"
else
    echo "  ❌ mcp-ai-client 启动失败，查看日志: $PROJECT_ROOT/mcp-ai-client/logs/app.log"
    exit 1
fi
echo ""

# 完成
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║  ✅ 所有服务启动成功！                                        ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  📊 服务状态:                                                 ║"
echo "║    • demo-provider:   http://localhost:8083 (PID: $DEMO_PID)"
echo "║    • zkInfo (MCP):    http://localhost:9091 (PID: $MCP_PID)"
echo "║    • mcp-ai-client:   http://localhost:8081 (PID: $AI_PID)"
echo "║                                                                ║"
echo "║  🎯 测试端点:                                                 ║"
echo "║    • AI Chat:         http://localhost:8081                    ║"
echo "║    • MCP Health:      http://localhost:9091/mcp/health         ║"
echo "║    • User Service:    http://localhost:8083/user/1             ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""
echo "💡 快速测试:"
echo "   curl -X POST http://localhost:8081/api/chat/session"
echo ""
echo "📜 查看日志:"
echo "   tail -f $PROJECT_ROOT/mcp-ai-client/logs/app.log"
echo ""

