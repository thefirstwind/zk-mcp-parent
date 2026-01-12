#!/bin/bash

echo "=========================================="
echo "启动 zkInfo 服务"
echo "=========================================="

# 设置环境变量
export MYSQL_HOST=${MYSQL_HOST:-127.0.0.1}
export MYSQL_PORT=${MYSQL_PORT:-3306}
export MYSQL_USERNAME=${MYSQL_USERNAME:-mcp_user}
export MYSQL_PASSWORD=${MYSQL_PASSWORD:-mcp_user}
export MYSQL_DATABASE=${MYSQL_DATABASE:-mcp_bridge}

export ZK_CONNECT_STRING=${ZK_CONNECT_STRING:-127.0.0.1:2181}
export NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR:-127.0.0.1:8848}
export NACOS_USERNAME=${NACOS_USERNAME:-nacos}
export NACOS_PASSWORD=${NACOS_PASSWORD:-nacos}

export REDIS_HOST=${REDIS_HOST:-127.0.0.1}
export REDIS_PORT=${REDIS_PORT:-6379}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ZKINFO_DIR="${PROJECT_ROOT}/zk-mcp-parent/zkInfo"

if [ ! -d "${ZKINFO_DIR}" ]; then
    echo "❌ 找不到 zkInfo 项目目录: ${ZKINFO_DIR}"
    exit 1
fi

cd "${ZKINFO_DIR}"

echo "工作目录: $(pwd)"
echo "启动 zkInfo 服务..."
echo ""

mvn spring-boot:run

echo ""
echo "=========================================="


