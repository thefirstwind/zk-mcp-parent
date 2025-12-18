#!/bin/bash

# 网络连通性检查脚本
# 用于检查 MySQL、Nacos、ZooKeeper 的连接状态

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 从环境变量读取配置，如果没有则使用默认值
MYSQL_HOST=${MYSQL_HOST:-192.168.0.101}
MYSQL_PORT=${MYSQL_PORT:-3306}
NACOS_HOST=${NACOS_SERVER_ADDR:-192.168.0.101:8848}
NACOS_HOST_ONLY=$(echo $NACOS_HOST | cut -d: -f1)
NACOS_PORT=$(echo $NACOS_HOST | cut -d: -f2)
ZK_HOST=${ZK_CONNECT_STRING:-192.168.0.101:2181}
ZK_HOST_ONLY=$(echo $ZK_HOST | cut -d: -f1)
ZK_PORT=$(echo $ZK_HOST | cut -d: -f2)

echo "=========================================="
echo "网络连通性检查"
echo "=========================================="
echo ""

# 检查 MySQL
echo -n "检查 MySQL ($MYSQL_HOST:$MYSQL_PORT)... "
if timeout 3 bash -c "cat < /dev/null > /dev/tcp/$MYSQL_HOST/$MYSQL_PORT" 2>/dev/null; then
    echo -e "${GREEN}✓ 连接成功${NC}"
else
    echo -e "${RED}✗ 连接失败${NC}"
    echo "  请检查："
    echo "  1. MySQL 服务是否运行"
    echo "  2. 防火墙是否开放端口 $MYSQL_PORT"
    echo "  3. MySQL 是否允许远程连接"
fi

# 检查 Nacos
echo -n "检查 Nacos ($NACOS_HOST_ONLY:$NACOS_PORT)... "
if timeout 3 bash -c "cat < /dev/null > /dev/tcp/$NACOS_HOST_ONLY/$NACOS_PORT" 2>/dev/null; then
    echo -e "${GREEN}✓ 连接成功${NC}"
    # 尝试访问健康检查接口
    HEALTH_CHECK=$(curl -s -o /dev/null -w "%{http_code}" "http://$NACOS_HOST/nacos/v1/console/health" 2>/dev/null)
    if [ "$HEALTH_CHECK" = "200" ]; then
        echo "  Nacos 健康检查: ${GREEN}正常${NC}"
    else
        echo "  Nacos 健康检查: ${YELLOW}异常 (HTTP $HEALTH_CHECK)${NC}"
    fi
else
    echo -e "${RED}✗ 连接失败${NC}"
    echo "  请检查："
    echo "  1. Nacos 服务是否运行"
    echo "  2. 防火墙是否开放端口 $NACOS_PORT"
    echo "  3. Nacos 是否配置为允许外部访问"
fi

# 检查 ZooKeeper
echo -n "检查 ZooKeeper ($ZK_HOST_ONLY:$ZK_PORT)... "
if timeout 3 bash -c "cat < /dev/null > /dev/tcp/$ZK_HOST_ONLY/$ZK_PORT" 2>/dev/null; then
    echo -e "${GREEN}✓ 连接成功${NC}"
    # 尝试使用 nc 或 telnet 发送 ruok 命令
    if command -v nc &> /dev/null; then
        RESPONSE=$(echo "ruok" | nc -w 2 $ZK_HOST_ONLY $ZK_PORT 2>/dev/null)
        if [ "$RESPONSE" = "imok" ]; then
            echo "  ZooKeeper 状态: ${GREEN}正常${NC}"
        else
            echo "  ZooKeeper 状态: ${YELLOW}未知${NC}"
        fi
    fi
else
    echo -e "${RED}✗ 连接失败${NC}"
    echo "  请检查："
    echo "  1. ZooKeeper 服务是否运行"
    echo "  2. 防火墙是否开放端口 $ZK_PORT"
    echo "  3. ZooKeeper 配置是否正确"
fi

echo ""
echo "=========================================="
echo "检查完成"
echo "=========================================="

