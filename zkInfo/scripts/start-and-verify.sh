#!/bin/bash

# ZkInfo 服务启动和验证脚本
# 用于启动服务并验证核心功能是否正常

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
ZKINFO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$ZKINFO_DIR/../.." && pwd)"
LOG_FILE="$ZKINFO_DIR/logs/zkinfo.log"
PID_FILE="$ZKINFO_DIR/pids/zkinfo.pid"
SERVER_PORT=9091
HEALTH_CHECK_URL="http://localhost:${SERVER_PORT}/actuator/health"
API_DOCS_URL="http://localhost:${SERVER_PORT}/v3/api-docs"
SWAGGER_UI_URL="http://localhost:${SERVER_PORT}/swagger-ui.html"

# 创建必要的目录
mkdir -p "$ZKINFO_DIR/logs"
mkdir -p "$ZKINFO_DIR/pids"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  ZkInfo 服务启动和验证脚本${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 检查Java环境
echo -e "${YELLOW}[1/6] 检查Java环境...${NC}"
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java未安装，请先安装Java 17+${NC}"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo -e "${RED}❌ Java版本过低，需要Java 17+，当前版本: $JAVA_VERSION${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Java版本: $(java -version 2>&1 | head -n 1)${NC}"

# 检查Maven环境
echo -e "${YELLOW}[2/6] 检查Maven环境...${NC}"
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven未安装，请先安装Maven${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Maven版本: $(mvn -version | head -n 1)${NC}"

# 检查ZooKeeper连接
echo -e "${YELLOW}[3/6] 检查ZooKeeper连接...${NC}"
ZK_CONNECT="${ZK_CONNECT_STRING:-localhost:2181}"
if command -v nc &> /dev/null; then
    ZK_HOST=$(echo $ZK_CONNECT | cut -d':' -f1)
    ZK_PORT=$(echo $ZK_CONNECT | cut -d':' -f2)
    if nc -z "$ZK_HOST" "$ZK_PORT" 2>/dev/null; then
        echo -e "${GREEN}✅ ZooKeeper连接正常: $ZK_CONNECT${NC}"
    else
        echo -e "${YELLOW}⚠️  ZooKeeper连接失败: $ZK_CONNECT (服务可能未启动，但将继续启动)${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  无法检查ZooKeeper连接 (nc命令不可用)${NC}"
fi

# 检查Nacos连接
echo -e "${YELLOW}[4/6] 检查Nacos连接...${NC}"
NACOS_ADDR="${NACOS_SERVER_ADDR:-127.0.0.1:8848}"
NACOS_HOST=$(echo $NACOS_ADDR | cut -d':' -f1)
NACOS_PORT=$(echo $NACOS_ADDR | cut -d':' -f2)
if command -v nc &> /dev/null; then
    if nc -z "$NACOS_HOST" "$NACOS_PORT" 2>/dev/null; then
        echo -e "${GREEN}✅ Nacos连接正常: $NACOS_ADDR${NC}"
    else
        echo -e "${YELLOW}⚠️  Nacos连接失败: $NACOS_ADDR (服务可能未启动，但将继续启动)${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  无法检查Nacos连接 (nc命令不可用)${NC}"
fi

# 编译项目
echo -e "${YELLOW}[5/6] 编译项目...${NC}"
cd "$ZKINFO_DIR"
if mvn clean compile -DskipTests 2>&1 | tee /tmp/zkinfo-build.log; then
    echo -e "${GREEN}✅ 编译成功${NC}"
else
    echo -e "${RED}❌ 编译失败，请查看日志: /tmp/zkinfo-build.log${NC}"
    exit 1
fi

# 停止已运行的服务
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if ps -p "$OLD_PID" > /dev/null 2>&1; then
        echo -e "${YELLOW}停止已运行的服务 (PID: $OLD_PID)...${NC}"
        kill "$OLD_PID" 2>/dev/null || true
        sleep 2
    fi
    rm -f "$PID_FILE"
fi

# 启动服务
echo -e "${YELLOW}[6/6] 启动服务...${NC}"
cd "$ZKINFO_DIR"
nohup mvn spring-boot:run > "$LOG_FILE" 2>&1 &
APP_PID=$!
echo $APP_PID > "$PID_FILE"
echo -e "${GREEN}✅ 服务已启动，PID: $APP_PID${NC}"
echo -e "${BLUE}   日志文件: $LOG_FILE${NC}"

# 等待服务启动
echo -e "${YELLOW}等待服务启动...${NC}"
MAX_WAIT=60
WAIT_COUNT=0
while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
    if curl -s -f "$HEALTH_CHECK_URL" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ 服务启动成功！${NC}"
        break
    fi
    sleep 1
    WAIT_COUNT=$((WAIT_COUNT + 1))
    echo -n "."
done

if [ $WAIT_COUNT -ge $MAX_WAIT ]; then
    echo -e "\n${RED}❌ 服务启动超时，请检查日志: $LOG_FILE${NC}"
    tail -n 50 "$LOG_FILE"
    exit 1
fi

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  功能验证${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 验证健康检查
echo -e "${YELLOW}[1/5] 验证健康检查端点...${NC}"
HEALTH_RESPONSE=$(curl -s "$HEALTH_CHECK_URL")
if echo "$HEALTH_RESPONSE" | grep -q "UP\|status.*UP"; then
    echo -e "${GREEN}✅ 健康检查正常${NC}"
    echo "   响应: $HEALTH_RESPONSE"
else
    echo -e "${RED}❌ 健康检查异常${NC}"
    echo "   响应: $HEALTH_RESPONSE"
fi

# 验证API文档
echo -e "${YELLOW}[2/5] 验证API文档端点...${NC}"
if curl -s -f "$API_DOCS_URL" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ API文档端点正常${NC}"
    echo "   访问: $API_DOCS_URL"
else
    echo -e "${YELLOW}⚠️  API文档端点不可用${NC}"
fi

# 验证Swagger UI
echo -e "${YELLOW}[3/5] 验证Swagger UI...${NC}"
if curl -s -f "$SWAGGER_UI_URL" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Swagger UI正常${NC}"
    echo "   访问: $SWAGGER_UI_URL"
else
    echo -e "${YELLOW}⚠️  Swagger UI不可用${NC}"
fi

# 验证ZooKeeper服务
echo -e "${YELLOW}[4/5] 验证ZooKeeper服务连接...${NC}"
ZK_STATUS_URL="http://localhost:${SERVER_PORT}/api/providers"
ZK_RESPONSE=$(curl -s "$ZK_STATUS_URL" 2>/dev/null || echo "ERROR")
if [ "$ZK_RESPONSE" != "ERROR" ]; then
    echo -e "${GREEN}✅ ZooKeeper服务正常${NC}"
    PROVIDER_COUNT=$(echo "$ZK_RESPONSE" | grep -o '"interfaceName"' | wc -l || echo "0")
    echo "   发现的Provider数量: $PROVIDER_COUNT"
else
    echo -e "${YELLOW}⚠️  ZooKeeper服务端点不可用或未连接${NC}"
fi

# 验证Nacos注册服务
echo -e "${YELLOW}[5/5] 验证Nacos注册服务...${NC}"
NACOS_STATUS_URL="http://localhost:${SERVER_PORT}/api/registered-services"
NACOS_RESPONSE=$(curl -s "$NACOS_STATUS_URL" 2>/dev/null || echo "ERROR")
if [ "$NACOS_RESPONSE" != "ERROR" ]; then
    echo -e "${GREEN}✅ Nacos注册服务正常${NC}"
    REGISTERED_COUNT=$(echo "$NACOS_RESPONSE" | grep -o '","' | wc -l || echo "0")
    echo "   已注册服务数量: $REGISTERED_COUNT"
else
    echo -e "${YELLOW}⚠️  Nacos注册服务端点不可用${NC}"
fi

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}  服务启动和验证完成！${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "服务信息:"
echo -e "  - 访问地址: http://localhost:${SERVER_PORT}"
echo -e "  - 健康检查: $HEALTH_CHECK_URL"
echo -e "  - API文档: $API_DOCS_URL"
echo -e "  - Swagger UI: $SWAGGER_UI_URL"
echo -e "  - 日志文件: $LOG_FILE"
echo -e "  - 进程ID: $APP_PID"
echo ""
echo -e "停止服务: kill $APP_PID 或查看日志: tail -f $LOG_FILE"
echo ""

