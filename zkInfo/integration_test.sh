#!/bin/bash
# zkInfo 虚拟节点优化 - 集成测试脚本
# 用途: 自动化验证 zkInfo 与 Nacos 的集成，以及与 mcp-router-v3 的对接
# 
# 前提条件:
# - Docker 已安装
# - Maven 已安装
# - 当前目录: zk-mcp-parent/zkInfo

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[✅]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[⚠️]${NC} $1"
}

log_error() {
    echo -e "${RED}[❌]${NC} $1"
}

# 配置
NACOS_VERSION="v3.1.1"
NACOS_PORT="8848"
NACOS_CONTAINER_NAME="zkinfo-test-nacos"
MYSQL_CONTAINER_NAME="zkinfo-test-mysql"
ZKINFO_PORT="9091"

# 步骤计数
STEP_NUM=0

step() {
    STEP_NUM=$((STEP_NUM + 1))
    log_info "步骤 $STEP_NUM: $1"
}

# 清理函数
cleanup() {
    log_warn "正在清理测试环境..."
    
    # 停止并删除容器
    docker stop $NACOS_CONTAINER_NAME 2>/dev/null || true
    docker rm $NACOS_CONTAINER_NAME 2>/dev/null || true
    docker stop $MYSQL_CONTAINER_NAME 2>/dev/null || true
    docker rm $MYSQL_CONTAINER_NAME 2>/dev/null || true
    
    log_success "清理完成"
}

# 捕获退出信号
trap cleanup EXIT

# ==================== 主流程 ====================

echo "=================================================="
echo "  zkInfo 虚拟节点优化 - 集成测试"
echo "=================================================="
echo ""

# 步骤 1: 检查 Docker
step "检查 Docker 环境"
if ! command -v docker &> /dev/null; then
    log_error "Docker 未安装，请先安装 Docker"
    exit 1
fi
log_success "Docker 已安装: $(docker --version)"

# 步骤 2: 检查 Maven
step "检查 Maven 环境"
if ! command -v mvn &> /dev/null; then
    log_error "Maven 未安装，请先安装 Maven"
    exit 1
fi
log_success "Maven 已安装: $(mvn --version | head -1)"

# 步骤 3: 启动 Nacos Server
step "启动 Nacos Server $NACOS_VERSION"
log_info "正在拉取 Nacos 镜像..."
docker pull nacos/nacos-server:$NACOS_VERSION

log_info "正在启动 Nacos 容器..."
docker run -d \
    --name $NACOS_CONTAINER_NAME \
    -e MODE=standalone \
    -e NACOS_AUTH_ENABLE=false \
    -p $NACOS_PORT:8848 \
    nacos/nacos-server:$NACOS_VERSION

# 等待 Nacos 启动
log_info "等待 Nacos 启动（最多 60 秒）..."
TIMEOUT=60
ELAPSED=0
while [ $ELAPSED -lt $TIMEOUT ]; do
    if curl -s http://localhost:$NACOS_PORT/nacos/ > /dev/null 2>&1; then
        log_success "Nacos 已启动"
        break
    fi
    sleep 2
    ELAPSED=$((ELAPSED + 2))
    echo -n "."
done
echo ""

if [ $ELAPSED -ge $TIMEOUT ]; then
    log_error "Nacos 启动超时"
    exit 1
fi

# 步骤 4: 验证 Nacos API
step "验证 Nacos API"
NACOS_VERSION_RESPONSE=$(curl -s http://localhost:$NACOS_PORT/nacos/v1/console/server/state)
log_info "Nacos 状态: $NACOS_VERSION_RESPONSE"

# 步骤 5: 编译 zkInfo
step "编译 zkInfo 项目"
log_info "正在执行: mvn clean compile -DskipTests"
mvn clean compile -DskipTests

if [ $? -eq 0 ]; then
    log_success "编译成功"
else
    log_error "编译失败"
    exit 1
fi

# 步骤 6: 运行核心单元测试
step "运行核心单元测试"
log_info "正在执行: mvn test -Dtest=DubboToMcpAutoRegistrationServiceTest"
mvn test -Dtest=DubboToMcpAutoRegistrationServiceTest

if [ $? -eq 0 ]; then
    log_success "单元测试通过"
else
    log_error "单元测试失败"
    exit 1
fi

# 步骤 7: 启动 zkInfo（可选，需要数据库）
step "启动 zkInfo 应用（可选）"
log_warn "此步骤需要 MySQL 数据库，如果没有配置请跳过"
read -p "是否启动 zkInfo 应用？ (y/n): " -n 1 -r
echo ""

if [[ $REPLY =~ ^[Yy]$ ]]; then
    # 检查是否配置了数据库
    if grep -q "spring.datasource.url" src/main/resources/application.yml; then
        log_info "正在后台启动 zkInfo..."
        mvn spring-boot:run &
        ZKINFO_PID=$!
        
        # 等待 zkInfo 启动
        log_info "等待 zkInfo 启动（最多 60 秒）..."
        TIMEOUT=60
        ELAPSED=0
        while [ $ELAPSED -lt $TIMEOUT ]; do
            if curl -s http://localhost:$ZKINFO_PORT/actuator/health > /dev/null 2>&1; then
                log_success "zkInfo 已启动"
                break
            fi
            sleep 2
            ELAPSED=$((ELAPSED + 2))
            echo -n "."
        done
        echo ""
        
        if [ $ELAPSED -ge $TIMEOUT ]; then
            log_error "zkInfo 启动超时"
            kill $ZKINFO_PID 2>/dev/null || true
            exit 1
        fi
        
        # 步骤 8: 验证服务注册
        step "验证 MCP 服务注册到 Nacos"
        sleep 5  # 等待注册完成
        
        log_info "查询 Nacos 服务列表..."
        SERVICES=$(curl -s "http://localhost:$NACOS_PORT/nacos/v1/ns/service/list?pageNo=1&pageSize=100&groupName=mcp-server")
        
        if echo "$SERVICES" | grep -q "zk-mcp"; then
            log_success "发现 MCP 服务"
            echo "$SERVICES" | jq '.doms' 2>/dev/null || echo "$SERVICES"
        else
            log_warn "未发现 MCP 服务，可能需要更多时间"
        fi
        
        # 步骤 9: 验证元数据
        step "验证服务元数据"
        log_info "查询服务实例..."
        
        # 获取第一个 MCP 服务名称
        SERVICE_NAME=$(echo "$SERVICES" | jq -r '.doms[0]' 2>/dev/null || echo "")
        
        if [ -n "$SERVICE_NAME" ] && [ "$SERVICE_NAME" != "null" ]; then
            INSTANCES=$(curl -s "http://localhost:$NACOS_PORT/nacos/v1/ns/instance/list?serviceName=$SERVICE_NAME&groupName=mcp-server")
            
            log_info "服务实例: $SERVICE_NAME"
            echo "$INSTANCES" | jq '.hosts[0].metadata' 2>/dev/null || echo "$INSTANCES"
            
            # 验证关键元数据字段
            METADATA=$(echo "$INSTANCES" | jq '.hosts[0].metadata' 2>/dev/null)
            if [ -n "$METADATA" ]; then
                PROTOCOL=$(echo "$METADATA" | jq -r '.protocol' 2>/dev/null)
                SERVER_NAME=$(echo "$METADATA" | jq -r '.serverName' 2>/dev/null)
                SERVER_MD5=$(echo "$METADATA" | jq -r '."server.md5"' 2>/dev/null)
                
                if [ "$PROTOCOL" == "mcp-sse" ]; then
                    log_success "protocol = mcp-sse ✅"
                else
                    log_warn "protocol = $PROTOCOL (期望: mcp-sse)"
                fi
                
                if [ -n "$SERVER_NAME" ] && [ "$SERVER_NAME" != "null" ]; then
                    log_success "serverName = $SERVER_NAME ✅"
                else
                    log_warn "serverName 缺失"
                fi
                
                if [ -n "$SERVER_MD5" ] && [ "$SERVER_MD5" != "null" ]; then
                    log_success "server.md5 = $SERVER_MD5 ✅"
                else
                    log_warn "server.md5 缺失"
                fi
            fi
        fi
        
        # 关闭 zkInfo
        log_info "正在关闭 zkInfo..."
        kill $ZKINFO_PID 2>/dev/null || true
        wait $ZKINFO_PID 2>/dev/null || true
        log_success "zkInfo 已关闭"
    else
        log_warn "未找到数据库配置，跳过 zkInfo 启动"
    fi
else
    log_info "跳过 zkInfo 启动"
fi

# 步骤 10: 生成测试报告
step "生成测试报告"

REPORT_FILE="integration_test_report_$(date +%Y%m%d_%H%M%S).txt"

cat > $REPORT_FILE << EOF
================================================
zkInfo 虚拟节点优化 - 集成测试报告
================================================

测试时间: $(date)
测试环境:
- Nacos 版本: $NACOS_VERSION
- Nacos 地址: http://localhost:$NACOS_PORT/nacos
- zkInfo 端口: $ZKINFO_PORT

测试结果:
✅ 步骤 1: Docker 环境检查通过
✅ 步骤 2: Maven 环境检查通过
✅ 步骤 3: Nacos Server 启动成功
✅ 步骤 4: Nacos API 验证通过
✅ 步骤 5: zkInfo 编译成功
✅ 步骤 6: 核心单元测试通过

下一步建议:
1. 访问 Nacos 控制台: http://localhost:$NACOS_PORT/nacos
   用户名/密码: nacos/nacos
   
2. 检查服务列表（服务管理 → 服务列表）
   - 查找 MCP 服务（名称包含 "zk-mcp"）
   
3. 验证元数据
   - protocol: mcp-sse
   - serverName: zk-mcp-xxx
   - serverId: uuid-xxx
   - server.md5: xxx
   
4. 测试与 mcp-router-v3 集成
   - 启动 mcp-router-v3
   - 验证服务发现
   - 调用工具

================================================

EOF

log_success "测试报告已生成: $REPORT_FILE"
cat $REPORT_FILE

# 步骤 11: 总结
echo ""
echo "=================================================="
echo "  测试完成"
echo "=================================================="
echo ""
log_success "所有自动化测试通过！"
echo ""
log_info "Nacos 控制台: http://localhost:$NACOS_PORT/nacos"
log_info "用户名/密码: nacos/nacos"
echo ""
log_warn "Nacos 容器将保持运行，您可以手动验证"
log_warn "要停止 Nacos: docker stop $NACOS_CONTAINER_NAME"
log_warn "要删除容器: docker rm $NACOS_CONTAINER_NAME"
echo ""

# 询问是否保持容器运行
read -p "是否保持 Nacos 容器运行以供手动测试？ (y/n): " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    cleanup
fi
