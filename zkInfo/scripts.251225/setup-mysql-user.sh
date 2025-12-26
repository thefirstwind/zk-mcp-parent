#!/bin/bash

# MySQL 用户设置脚本
# 使用方法: ./setup-mysql-user.sh [mysql_host] [mysql_port] [root_password]

MYSQL_HOST=${1:-192.168.0.101}
MYSQL_PORT=${2:-3306}
ROOT_PASSWORD=${3:-}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="$SCRIPT_DIR/setup-mysql-user.sql"

echo "=========================================="
echo "MySQL 用户设置脚本"
echo "=========================================="
echo "MySQL Host: $MYSQL_HOST"
echo "MySQL Port: $MYSQL_PORT"
echo ""

if [ -z "$ROOT_PASSWORD" ]; then
    echo "请输入 MySQL root 密码:"
    read -s ROOT_PASSWORD
fi

echo "正在执行 SQL 脚本..."

if mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u root -p"$ROOT_PASSWORD" < "$SQL_FILE"; then
    echo ""
    echo "=========================================="
    echo "✓ 用户设置成功！"
    echo "=========================================="
    echo ""
    echo "测试连接:"
    echo "mysql -h $MYSQL_HOST -P $MYSQL_PORT -u mcp_user -p mcp_bridge"
    echo "密码: mcp_user"
else
    echo ""
    echo "=========================================="
    echo "✗ 用户设置失败！"
    echo "=========================================="
    echo ""
    echo "请检查："
    echo "1. MySQL root 密码是否正确"
    echo "2. MySQL 服务是否运行"
    echo "3. 网络连接是否正常"
    exit 1
fi
