#!/bin/bash

echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║         DeepSeek API 连接测试                                      ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"
echo ""

# 检查环境变量
if [ -z "$DEEPSEEK_API_KEY" ]; then
    echo "❌ 错误: 未设置 DEEPSEEK_API_KEY 环境变量"
    echo ""
    echo "请先设置 API Key:"
    echo "  export DEEPSEEK_API_KEY=sk-your-actual-key"
    echo ""
    echo "或运行配置助手:"
    echo "  ./setup-api-key.sh"
    exit 1
fi

echo "✓ 检测到 DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY:0:10}***"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "测试 DeepSeek API 连接..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 调用 DeepSeek API
response=$(curl -s -w "\n%{http_code}" \
  https://api.deepseek.com/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DEEPSEEK_API_KEY" \
  -d '{
    "model": "deepseek-chat",
    "messages": [
      {"role": "user", "content": "你好，请用一句话介绍你自己"}
    ],
    "max_tokens": 100,
    "temperature": 0.7,
    "stream": false
  }')

# 分离响应体和状态码
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')

echo "HTTP 状态码: $http_code"
echo ""

if [ "$http_code" = "200" ]; then
    echo "✅ API 连接成功！"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "AI 响应:"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    
    # 提取并显示回复内容
    if command -v jq &> /dev/null; then
        echo "$body" | jq -r '.choices[0].message.content'
    else
        echo "$body" | grep -o '"content":"[^"]*"' | sed 's/"content":"\(.*\)"/\1/' | head -1
    fi
    
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "✅ DeepSeek API 配置正确！可以启动应用了。"
    echo ""
    echo "下一步:"
    echo "  1. 确保 zkInfo MCP Server 正在运行 (端口 9091)"
    echo "  2. 运行 ./start.sh 启动 MCP AI Client"
    echo "  3. 访问 http://localhost:8081/swagger-ui.html"
    echo ""
    
elif [ "$http_code" = "401" ]; then
    echo "❌ 认证失败！"
    echo ""
    echo "错误详情:"
    echo "$body" | head -20
    echo ""
    echo "可能的原因:"
    echo "  1. API Key 不正确"
    echo "  2. API Key 已过期"
    echo "  3. API Key 没有访问权限"
    echo ""
    echo "解决方法:"
    echo "  1. 访问 https://platform.deepseek.com/"
    echo "  2. 检查/重新生成 API Key"
    echo "  3. 重新设置环境变量: export DEEPSEEK_API_KEY=新的key"
    echo ""
    exit 1
    
elif [ "$http_code" = "429" ]; then
    echo "❌ 请求频率超限！"
    echo ""
    echo "错误详情:"
    echo "$body" | head -20
    echo ""
    echo "可能的原因:"
    echo "  1. 超出免费额度"
    echo "  2. 请求过于频繁"
    echo ""
    echo "解决方法:"
    echo "  1. 等待一段时间后重试"
    echo "  2. 检查 DeepSeek 账户余额"
    echo "  3. 考虑升级账户或购买额度"
    echo ""
    exit 1
    
else
    echo "❌ API 调用失败！"
    echo ""
    echo "HTTP 状态码: $http_code"
    echo ""
    echo "错误详情:"
    echo "$body" | head -20
    echo ""
    echo "请检查:"
    echo "  1. 网络连接是否正常"
    echo "  2. API Key 是否正确"
    echo "  3. DeepSeek 服务是否正常"
    echo ""
    echo "如需帮助，请访问: https://platform.deepseek.com/docs"
    echo ""
    exit 1
fi

