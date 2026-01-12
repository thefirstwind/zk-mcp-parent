#!/bin/bash

echo "=========================================="
echo "zkInfo 前置条件检查"
echo "=========================================="

# 检查 Java 版本
echo "1. 检查 Java 版本..."
if java -version 2>&1 | grep -q "1.8\|11\|17"; then
    echo "✅ Java 版本正确"
    java -version 2>&1 | head -1
else
    echo "❌ Java 版本不正确，需要 Java 8/11/17"
fi

# 检查 MySQL 连接
echo ""
echo "2. 检查 MySQL 连接..."
MYSQL_HOST=${MYSQL_HOST:-127.0.0.1}
MYSQL_PORT=${MYSQL_PORT:-3306}
MYSQL_USERNAME=${MYSQL_USERNAME:-mcp_user}
MYSQL_PASSWORD=${MYSQL_PASSWORD:-mcp_user}
MYSQL_DATABASE=${MYSQL_DATABASE:-mcp_bridge}

if mysql -h${MYSQL_HOST} -P${MYSQL_PORT} -u${MYSQL_USERNAME} -p${MYSQL_PASSWORD} -e "SELECT 1" ${MYSQL_DATABASE} > /dev/null 2>&1; then
    echo "✅ MySQL 连接正常 (${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE})"
else
    echo "❌ MySQL 连接失败 (${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE})"
    echo "   请检查: MYSQL_HOST, MYSQL_PORT, MYSQL_USERNAME, MYSQL_PASSWORD, MYSQL_DATABASE"
fi

# 检查 ZooKeeper 连接
echo ""
echo "3. 检查 ZooKeeper 连接..."
ZK_CONNECT_STRING=${ZK_CONNECT_STRING:-127.0.0.1:2181}
if echo "stat" | nc -w 2 $(echo ${ZK_CONNECT_STRING} | cut -d: -f1) $(echo ${ZK_CONNECT_STRING} | cut -d: -f2) > /dev/null 2>&1; then
    echo "✅ ZooKeeper 连接正常 (${ZK_CONNECT_STRING})"
else
    echo "❌ ZooKeeper 连接失败 (${ZK_CONNECT_STRING})"
    echo "   请检查: ZK_CONNECT_STRING"
fi

# 检查 Nacos 连接
echo ""
echo "4. 检查 Nacos 连接..."
NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR:-127.0.0.1:8848}
if curl -s -m 2 "http://${NACOS_SERVER_ADDR}/nacos/v1/console/health" > /dev/null 2>&1; then
    echo "✅ Nacos 连接正常 (${NACOS_SERVER_ADDR})"
else
    echo "❌ Nacos 连接失败 (${NACOS_SERVER_ADDR})"
    echo "   请检查: NACOS_SERVER_ADDR"
fi

# 检查 Redis 连接
echo ""
echo "5. 检查 Redis 连接..."
REDIS_HOST=${REDIS_HOST:-127.0.0.1}
REDIS_PORT=${REDIS_PORT:-6379}
if redis-cli -h ${REDIS_HOST} -p ${REDIS_PORT} ping > /dev/null 2>&1; then
    echo "✅ Redis 连接正常 (${REDIS_HOST}:${REDIS_PORT})"
else
    echo "❌ Redis 连接失败 (${REDIS_HOST}:${REDIS_PORT})"
    echo "   请检查: REDIS_HOST, REDIS_PORT"
fi

echo ""
echo "=========================================="


