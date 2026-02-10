#!/bin/bash

# zkInfo 实际环境手工验证交互式脚本
# 用于指导用户进行完整的功能验证

set -e

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}  zkInfo 实际环境手工验证${NC}"
echo -e "${BLUE}================================${NC}"
echo ""

# 函数：等待用户确认
wait_for_user() {
    echo -e "${YELLOW}按 Enter 继续...${NC}"
    read
}

# 函数：询问用户
ask_user() {
    local question=$1
    echo -e "${YELLOW}$question (y/n):${NC} "
    read answer
    if [ "$answer" != "y" ]; then
        return 1
    fi
    return 0
}

echo "本脚本将指导您完成 zkInfo 的实际环境验证"
echo "验证将分为以下几个部分："
echo "  1. 环境检查"
echo "  2. 配置验证"
echo "  3. 启动 zkInfo"
echo "  4. 功能验证"
echo "  5. Nacos 控制台验证"
echo ""
wait_for_user

# ============================================
# 第一部分：环境检查
# ============================================
echo -e "${BLUE}=== 第一部分：环境检查 ===${NC}"
echo ""

echo "检查 Java 版本..."
java -version 2>&1 | head -3
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Java 已安装${NC}"
else
    echo -e "${RED}❌ Java 未安装或不在 PATH 中${NC}"
    exit 1
fi
echo ""

echo "检查 Maven 版本..."
mvn -version | head -1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Maven 已安装${NC}"
else
    echo -e "${RED}❌ Maven 未安装或不在 PATH 中${NC}"
    exit 1
fi
echo ""

if ask_user "是否已启动 Nacos Server？"; then
    echo -e "${GREEN}✅ Nacos Server 已启动${NC}"
    
    echo ""
    echo "请提供 Nacos Server 地址信息："
    read -p "Nacos 地址 (默认: localhost:8848): " NACOS_ADDR
    NACOS_ADDR=${NACOS_ADDR:-localhost:8848}
    
    read -p "Nacos 命名空间 (默认: public): " NACOS_NS
    NACOS_NS=${NACOS_NS:-public}
    
    read -p "Nacos 用户名 (默认: nacos): " NACOS_USER
    NACOS_USER=${NACOS_USER:-nacos}
    
    read -p "Nacos 密码 (默认: nacos): " NACOS_PWD
    NACOS_PWD=${NACOS_PWD:-nacos}
    
    echo ""
    echo "测试 Nacos 连接..."
    curl -s "http://${NACOS_ADDR}/nacos/v1/console/health/liveness" > /dev/null
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Nacos Server 可访问${NC}"
    else
        echo -e "${YELLOW}⚠️  无法访问 Nacos Server，请检查地址是否正确${NC}"
        if ! ask_user "是否继续验证？"; then
            exit 1
        fi
    fi
else
    echo -e "${YELLOW}⚠️  请先启动 Nacos Server${NC}"
    echo ""
    echo "快速启动 Nacos（使用 Docker）："
    echo "  docker run -d --name nacos-standalone \\"
    echo "    -e MODE=standalone \\"
    echo "    -p 8848:8848 \\"
    echo "    nacos/nacos-server:v3.1.1"
    echo ""
    if ! ask_user "是否已启动 Nacos？"; then
        exit 1
    fi
    NACOS_ADDR="localhost:8848"
    NACOS_NS="public"
    NACOS_USER="nacos"
    NACOS_PWD="nacos"
fi

echo ""
wait_for_user

# ============================================
# 第二部分：配置验证
# ============================================
echo -e "${BLUE}=== 第二部分：配置验证 ===${NC}"
echo ""

echo "检查当前配置文件..."
if [ -f "src/main/resources/application.yml" ]; then
    echo -e "${GREEN}✅ 找到 application.yml${NC}"
    echo ""
    echo "当前 Nacos 配置:"
    grep -A 10 "nacos:" src/main/resources/application.yml | head -15
else
    echo -e "${RED}❌ 未找到 application.yml${NC}"
    exit 1
fi

echo ""
if ask_user "是否需要创建本地配置文件 (application-local.yml)？"; then
    echo ""
    echo "创建 application-local.yml..."
    cat > src/main/resources/application-local.yml << EOF
# 本地验证配置
spring:
  application:
    name: zkInfo-validation

nacos:
  server-addr: ${NACOS_ADDR}
  namespace: ${NACOS_NS}
  username: ${NACOS_USER}
  password: ${NACOS_PWD}
  registry:
    enabled: true
    service-group: mcp-server

# 启用 Nacos v3 API
nacos-v3-api:
  enabled: true

# 注册配置
registry:
  enabled: true

server:
  port: 9091
  servlet:
    context-path: /

# 日志级别
logging:
  level:
    com.pajk.mcpmetainfo: DEBUG
EOF
    echo -e "${GREEN}✅ 已创建 application-local.yml${NC}"
    echo ""
    echo "配置内容:"
    cat src/main/resources/application-local.yml
fi

echo ""
wait_for_user

# ============================================
# 第三部分：启动 zkInfo
# ============================================
echo -e "${BLUE}=== 第三部分：启动 zkInfo ===${NC}"
echo ""

if ask_user "是否现在启动 zkInfo？"; then
    echo ""
    echo "启动 zkInfo..."
    echo "命令: mvn spring-boot:run -Dspring-boot.run.profiles=local"
    echo ""
    echo -e "${YELLOW}注意：应用将在前台运行，按 Ctrl+C 可停止${NC}"
    echo ""
    echo -e "${YELLOW}请在新终端窗口中观察以下关键日志：${NC}"
    echo "  ✅ AiMaintainerService initialized successfully"
    echo "  🚀 Registering Dubbo service as MCP"
    echo "  📦 Registered MCP service"
    echo "  ✅ Successfully registered instance"
    echo ""
    echo -e "${YELLOW}如果看到错误日志：${NC}"
    echo "  ❌ Failed to initialize AiMaintainerService"
    echo "  ⚠️  Falling back to ConfigService"
    echo "  → 这是正常的降级行为"
    echo ""
    
    if ask_user "准备好启动了吗？"; then
        echo ""
        echo "启动中..."
        mvn spring-boot:run -Dspring-boot.run.profiles=local
    fi
else
    echo ""
    echo "跳过启动，您可以稍后手动启动："
    echo "  cd zkInfo"
    echo "  mvn spring-boot:run -Dspring-boot.run.profiles=local"
fi

echo ""
echo -e "${GREEN}验证脚本执行完成！${NC}"
echo ""
echo "后续验证步骤："
echo "1. 访问 Nacos 控制台: http://${NACOS_ADDR}/nacos"
echo "2. 登录用户名/密码: ${NACOS_USER}/${NACOS_PWD}"
echo "3. 进入「服务管理」→「服务列表」"
echo "4. 查找以 'zk-mcp-' 开头的服务"
echo "5. 检查服务的元数据是否包含："
echo "   - protocol: mcp-sse"
echo "   - serverName: xxx"
echo "   - serverId: UUID"
echo "   - server.md5: MD5值"
echo ""
echo "详细验证指南请查看:"
echo "  - MANUAL_VALIDATION_GUIDE.md"
echo "  - CODE_LOGIC_VALIDATION_REPORT.md"
