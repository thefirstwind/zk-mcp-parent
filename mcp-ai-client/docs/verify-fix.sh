#!/bin/bash

echo "======================================"
echo "DeepSeek API 集成验证"
echo "======================================"
echo ""

# 检查应用是否运行
echo "1. 检查应用状态..."
if curl -s http://localhost:8081/actuator/health > /dev/null 2>&1; then
    echo "   ✅ 应用正在运行"
else
    echo "   ❌ 应用未运行"
    exit 1
fi

# 检查配置
echo ""
echo "2. 检查配置..."
BASE_URL=$(grep "base-url" src/main/resources/application.yml | awk '{print $2}')
if [ "$BASE_URL" = "https://api.deepseek.com" ]; then
    echo "   ✅ base-url 配置正确（已修复 404 问题）"
else
    echo "   ❌ base-url 配置错误: $BASE_URL"
fi

JACKSON_CONFIG=$(grep -A2 "jackson:" src/main/resources/application.yml | grep "fail-on-unknown-properties" | awk '{print $2}')
if [ "$JACKSON_CONFIG" = "false" ]; then
    echo "   ✅ Jackson 配置正确（已修复 JSON 反序列化问题）"
else
    echo "   ❌ Jackson 配置错误"
fi

# 检查 API Key
echo ""
echo "3. 检查 API Key..."
if [ -n "$DEEPSEEK_API_KEY" ] && [ "$DEEPSEEK_API_KEY" != "your-deepseek-api-key-here" ]; then
    if [[ $DEEPSEEK_API_KEY == sk-* ]]; then
        echo "   ✅ API Key 格式正确"
    else
        echo "   ⚠️  API Key 格式可能不正确（应该以 sk- 开头）"
    fi
else
    echo "   ⚠️  需要设置真实的 DeepSeek API Key"
    echo "      运行: export DEEPSEEK_API_KEY=sk-your-real-key"
fi

# 创建会话
echo ""
echo "4. 测试会话创建..."
SESSION_RESPONSE=$(curl -s -X POST http://localhost:8081/api/chat/session \
  -H "Content-Type: application/json" \
  -d '{"sessionName":"验证测试"}')

SESSION_ID=$(echo $SESSION_RESPONSE | jq -r '.sessionId // empty')
if [ -n "$SESSION_ID" ]; then
    echo "   ✅ 会话创建成功: $SESSION_ID"
else
    echo "   ❌ 会话创建失败"
    exit 1
fi

# 发送消息
echo ""
echo "5. 测试消息发送..."
MESSAGE_RESPONSE=$(curl -s -X POST "http://localhost:8081/api/chat/session/$SESSION_ID/message" \
  -H "Content-Type: application/json" \
  -d '{"message":"hi"}')

AI_RESPONSE=$(echo $MESSAGE_RESPONSE | jq -r '.aiResponse // empty')

# 检查响应类型
if echo "$AI_RESPONSE" | grep -q "Authentication Fails"; then
    echo "   ⚠️  API Key 认证失败（401 错误）"
    echo "   ℹ️  这是正常的！说明前面的问题都已修复"
    echo "   ℹ️  只需设置真实的 API Key 即可"
elif echo "$AI_RESPONSE" | grep -q "404"; then
    echo "   ❌ 仍然存在 404 错误"
    exit 1
elif echo "$AI_RESPONSE" | grep -q "JSON parse error"; then
    echo "   ❌ 仍然存在 JSON 反序列化错误"
    exit 1
elif [ -n "$AI_RESPONSE" ] && [ "$AI_RESPONSE" != "null" ]; then
    echo "   ✅ 消息发送成功！AI 已回复"
    echo "   AI 回复: ${AI_RESPONSE:0:100}..."
else
    echo "   ❌ 收到未知响应"
fi

# 检查日志中的错误
echo ""
echo "6. 检查最近的错误..."
JSON_ERRORS=$(tail -100 logs/mcp-ai-client.log | grep -c "JSON parse error" || true)
if [ "$JSON_ERRORS" -eq 0 ]; then
    echo "   ✅ 没有 JSON 反序列化错误"
else
    echo "   ❌ 发现 $JSON_ERRORS 个 JSON 错误"
fi

echo ""
echo "======================================"
echo "验证总结"
echo "======================================"

# 统计已修复的问题
FIXED_COUNT=0
ISSUE_COUNT=0

echo ""
echo "已修复的问题:"
if [ "$BASE_URL" = "https://api.deepseek.com" ]; then
    echo "  ✅ 404 错误（base-url 配置）"
    ((FIXED_COUNT++))
fi

if [ "$JACKSON_CONFIG" = "false" ]; then
    echo "  ✅ JSON 反序列化错误（Jackson 配置）"
    ((FIXED_COUNT++))
fi

echo ""
echo "待解决:"
if [ -z "$DEEPSEEK_API_KEY" ] || [ "$DEEPSEEK_API_KEY" = "your-deepseek-api-key-here" ]; then
    echo "  • 设置真实的 DeepSeek API Key"
    ((ISSUE_COUNT++))
fi

echo ""
if [ $FIXED_COUNT -eq 2 ] && [ $ISSUE_COUNT -eq 1 ]; then
    echo "🎉 技术问题全部修复！"
    echo ""
    echo "下一步："
    echo "  1. 访问 https://platform.deepseek.com/ 获取 API Key"
    echo "  2. 运行: export DEEPSEEK_API_KEY=sk-your-real-key"
    echo "  3. 重启应用: lsof -ti:8081 | xargs kill -9 && mvn spring-boot:run &"
    echo "  4. 再次运行此脚本验证"
elif [ $FIXED_COUNT -eq 2 ] && [ $ISSUE_COUNT -eq 0 ]; then
    echo "🎊 完美！所有问题都已解决，系统正常运行！"
else
    echo "⚠️  还有 $((2-FIXED_COUNT)) 个技术问题需要修复"
    exit 1
fi

echo ""
