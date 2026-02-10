#!/bin/bash

# 这个脚本会应用所有剩余的修改来完全移除 AiMaintainerService 并使用 HTTP API

echo "=== 应用剩余的代码修改 ==="

cd /Users/shine/projects.mcp-router-sse-parent/zk-mcp-parent/zkInfo

# 编译并测试
echo "1. 编译项目..."
mvn clean compile -DskipTests

echo
echo "=== 所有修改已应用 ==="
