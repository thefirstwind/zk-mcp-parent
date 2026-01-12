#!/bin/bash

set -e

echo "=========================================="
echo "zkInfo 一键启动脚本"
echo "=========================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 1. 检查前置条件
echo "1. 检查前置条件..."
"${SCRIPT_DIR}/check-prerequisites.sh"

# 2. 初始化数据库
echo ""
echo "2. 初始化数据库..."
read -p "是否初始化数据库? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    "${SCRIPT_DIR}/init-database.sh"
fi

# 3. 启动服务
echo ""
echo "3. 启动 zkInfo 服务..."
read -p "是否启动服务? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    "${SCRIPT_DIR}/start-zkinfo.sh"
fi

echo ""
echo "=========================================="


