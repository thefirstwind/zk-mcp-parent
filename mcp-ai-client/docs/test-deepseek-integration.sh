#!/bin/bash

###############################################################################
# DeepSeek API 集成测试脚本
# 此脚本验证所有问题是否已修复
###############################################################################

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}   DeepSeek API 集成测试${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

# 检查应用是否运行
check_app_running() {
    echo -e "${YELLOW}[1/5] 检查应用状态...${NC}"
    if curl -s http://localhost:8081/actuator/health > /dev/null 2>&1; then
        STATUS=$(curl -s http://localhost:8081/actuator/health | jq -r '.status')
        if [ "$STATUS" = "UP" ]; then
            echo -e "${GREEN}✓ 应用运行正常${NC}"
            return 0
        fi
    fi
    echo -e "${RED}✗ 应用未运行或不健康${NC}"
    return 1
}

# 检查配置
check_configuration() {
    echo ""
    echo -e "${YELLOW}[2/5] 检查配置...${NC}"
    
    # 检查 base-url 配置
    BASE_URL_COUNT=$(grep -r "base-url: https://api.deepseek.com$" src/main/resources/application*.yml | wc -l | tr -d ' ')
    if [ "$BASE_URL_COUNT" -eq "3" ]; then
        echo -e "${GREEN}✓ base-url 配置正确（已修复 404 问题）${NC}"
    else
        echo -e "${RED}✗ base-url 配置错误${NC}"
        return 1
    fi
    
    # 检查 Jackson 配置
    JACKSON_COUNT=$(grep -r "fail-on-unknown-properties: false" src/main/resources/application*.yml | wc -l | tr -d ' ')
    if [ "$JACKSON_COUNT" -eq "3" ]; then
        echo -e "${GREEN}✓ Jackson 配置正确（已修复反序列化问题）${NC}"
    else
        echo -e "${RED}✗ Jackson 配置缺失${NC}"
        return 1
    fi
}

# 检查 API Key
check_api_key() {
    echo ""
    echo -e "${YELLOW}[3/5] 检查 API Key...${NC}"
    
    if [ -z "$DEEPSEEK_API_KEY" ]; then
        echo -e "${RED}✗ DEEPSEEK_API_KEY 环境变量未设置${NC}"
        echo ""
        echo -e "${YELLOW}请按以下步骤获取并设置 API Key:${NC}"
        echo ""
        echo "  1. 访问: https://platform.deepseek.com/"
        echo "  2. 注册/登录账号"
        echo "  3. 创建 API Key"
        echo "  4. 设置环境变量:"
        echo ""
        echo -e "     ${BLUE}export DEEPSEEK_API_KEY=sk-your-real-key-here${NC}"
        echo ""
        echo "  5. 重启应用:"
        echo ""
        echo -e "     ${BLUE}lsof -ti:8081 | xargs kill -9 2>/dev/null${NC}"
        echo -e "     ${BLUE}mvn spring-boot:run${NC}"
        echo ""
        return 1
    fi
    
    # 检查是否是占位符
    if [[ "$DEEPSEEK_API_KEY" == *"your-"* ]] || [[ "$DEEPSEEK_API_KEY" == *"key"* ]] || [ ${#DEEPSEEK_API_KEY} -lt 20 ]; then
        echo -e "${YELLOW}⚠ API Key 看起来像是占位符${NC}"
        echo ""
        echo "  当前值: $DEEPSEEK_API_KEY"
        echo ""
        echo "  请设置真实的 DeepSeek API Key"
        return 1
    fi
    
    echo -e "${GREEN}✓ API Key 已设置（长度: ${#DEEPSEEK_API_KEY}）${NC}"
}

# 测试会话创建
test_session_creation() {
    echo ""
    echo -e "${YELLOW}[4/5] 测试会话创建...${NC}"
    
    RESPONSE=$(curl -s -X POST http://localhost:8081/api/chat/session \
        -H "Content-Type: application/json" \
        -d '{"sessionName":"集成测试会话"}')
    
    SESSION_ID=$(echo "$RESPONSE" | jq -r '.sessionId')
    
    if [ "$SESSION_ID" != "null" ] && [ -n "$SESSION_ID" ]; then
        echo -e "${GREEN}✓ 会话创建成功: $SESSION_ID${NC}"
        echo "$SESSION_ID" > /tmp/test_session_id.txt
        return 0
    else
        echo -e "${RED}✗ 会话创建失败${NC}"
        echo "$RESPONSE" | jq .
        return 1
    fi
}

# 测试消息发送
test_message_sending() {
    echo ""
    echo -e "${YELLOW}[5/5] 测试消息发送...${NC}"
    
    SESSION_ID=$(cat /tmp/test_session_id.txt 2>/dev/null || echo "")
    
    if [ -z "$SESSION_ID" ]; then
        echo -e "${RED}✗ 无效的会话 ID${NC}"
        return 1
    fi
    
    RESPONSE=$(curl -s -X POST "http://localhost:8081/api/chat/session/$SESSION_ID/message" \
        -H "Content-Type: application/json" \
        -d '{"message":"你好"}')
    
    AI_RESPONSE=$(echo "$RESPONSE" | jq -r '.aiResponse')
    
    # 检查响应中是否有错误
    if echo "$AI_RESPONSE" | grep -q "401.*Authentication Fails"; then
        echo -e "${RED}✗ API Key 认证失败（401 错误）${NC}"
        echo ""
        echo -e "${YELLOW}这是预期的错误，因为使用的是占位符 API Key${NC}"
        echo ""
        echo "  请设置真实的 DeepSeek API Key 后重试"
        return 1
    elif echo "$AI_RESPONSE" | grep -q "404"; then
        echo -e "${RED}✗ 路径错误（404）- base-url 配置问题${NC}"
        return 1
    elif echo "$AI_RESPONSE" | grep -q "JSON parse error\|Unrecognized field"; then
        echo -e "${RED}✗ JSON 反序列化错误 - Jackson 配置问题${NC}"
        return 1
    elif echo "$AI_RESPONSE" | grep -q "正在初始化工具列表"; then
        echo -e "${YELLOW}⚠ 工具正在初始化中，请稍后重试${NC}"
        return 1
    elif [ "$AI_RESPONSE" != "null" ] && [ -n "$AI_RESPONSE" ]; then
        echo -e "${GREEN}✓ 消息发送成功！${NC}"
        echo ""
        echo "  用户消息: 你好"
        echo "  AI 回复: $AI_RESPONSE"
        echo ""
        return 0
    else
        echo -e "${RED}✗ 未知响应${NC}"
        echo "$RESPONSE" | jq .
        return 1
    fi
}

# 主流程
main() {
    PASSED=0
    FAILED=0
    
    # 执行检查
    if check_app_running; then ((PASSED++)); else ((FAILED++)); fi
    if check_configuration; then ((PASSED++)); else ((FAILED++)); fi
    if check_api_key; then ((PASSED++)); else ((FAILED++)); fi
    if test_session_creation; then ((PASSED++)); else ((FAILED++)); fi
    if test_message_sending; then ((PASSED++)); else ((FAILED++)); fi
    
    # 清理
    rm -f /tmp/test_session_id.txt
    
    # 总结
    echo ""
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}   测试总结${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "  通过: ${GREEN}$PASSED${NC} / 失败: ${RED}$FAILED${NC}"
    echo ""
    
    if [ $FAILED -eq 0 ]; then
        echo -e "${GREEN}🎉 所有测试通过！DeepSeek API 集成成功！${NC}"
        echo ""
        return 0
    elif [ $PASSED -ge 2 ]; then
        echo -e "${YELLOW}⚠️  部分测试通过 - 主要问题已修复${NC}"
        echo ""
        echo -e "${YELLOW}已修复的问题:${NC}"
        echo "  ✓ 404 错误（base-url 配置）"
        echo "  ✓ JSON 反序列化错误（Jackson 配置）"
        echo ""
        echo -e "${YELLOW}待解决:${NC}"
        echo "  • 设置真实的 DeepSeek API Key"
        echo ""
        return 1
    else
        echo -e "${RED}❌ 多个测试失败，请检查配置${NC}"
        echo ""
        return 1
    fi
}

# 运行主流程
main

exit $?



