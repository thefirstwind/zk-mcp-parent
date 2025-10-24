#!/bin/bash

# MCP AI Client 启动脚本
export DEEPSEEK_API_KEY=sk-c82a3957785d4c48b08a62e0e707ecf2
echo "========================================="
echo "MCP AI Client 启动脚本"
echo "========================================="

# 检查 DEEPSEEK_API_KEY
if [ -z "$DEEPSEEK_API_KEY" ]; then
    echo "错误: 未设置 DEEPSEEK_API_KEY 环境变量"
    echo "请运行: export DEEPSEEK_API_KEY=your-api-key"
    echo "或者运行配置助手: ./setup-api-key.sh"
    exit 1
fi

echo "✓ DEEPSEEK_API_KEY 已设置"

# 检查 Java 版本
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "错误: 需要 Java 17 或更高版本"
    exit 1
fi

echo "✓ Java 版本: $JAVA_VERSION"

# 构建项目
echo ""
echo "开始构建项目..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "错误: 构建失败"
    exit 1
fi

echo "✓ 构建成功"

# 启动应用
echo ""
echo "启动 MCP AI Client..."
echo "访问地址: http://localhost:8081"
echo "Swagger UI: http://localhost:8081/swagger-ui.html"
echo ""

java -jar target/mcp-ai-client-1.0.0.jar \
    --spring.profiles.active=dev

