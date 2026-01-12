#!/bin/bash

echo "=========================================="
echo "初始化 zkInfo 数据库"
echo "=========================================="

MYSQL_HOST=${MYSQL_HOST:-127.0.0.1}
MYSQL_PORT=${MYSQL_PORT:-3306}
MYSQL_USERNAME=${MYSQL_USERNAME:-mcp_user}
MYSQL_PASSWORD=${MYSQL_PASSWORD:-mcp_user}
MYSQL_DATABASE=${MYSQL_DATABASE:-mcp_bridge}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SCHEMA_FILE="${PROJECT_ROOT}/zkInfo/src/main/resources/db/schema.sql"

if [ ! -f "${SCHEMA_FILE}" ]; then
    echo "❌ 找不到数据库初始化脚本: ${SCHEMA_FILE}"
    exit 1
fi

echo "执行数据库初始化脚本: ${SCHEMA_FILE}"
mysql -h${MYSQL_HOST} -P${MYSQL_PORT} -u${MYSQL_USERNAME} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} < "${SCHEMA_FILE}"

if [ $? -eq 0 ]; then
    echo "✅ 数据库初始化成功"
else
    echo "❌ 数据库初始化失败"
    exit 1
fi

echo "=========================================="


