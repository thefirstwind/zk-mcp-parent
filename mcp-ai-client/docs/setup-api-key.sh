#!/bin/bash

echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║         MCP AI Client - DeepSeek API Key 配置助手                 ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"
echo ""

# 检查是否已设置环境变量
if [ -n "$DEEPSEEK_API_KEY" ]; then
    echo "✓ 检测到已设置的 DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY:0:10}***"
    echo ""
    read -p "是否要使用此 Key？(y/n) " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "✓ 使用现有 API Key"
        exit 0
    fi
fi

# 提示用户输入 API Key
echo "请输入你的 DeepSeek API Key:"
echo "(格式: sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx)"
echo ""
read -p "API Key: " api_key

# 验证格式
if [[ ! $api_key =~ ^sk-[a-zA-Z0-9]{32,}$ ]]; then
    echo ""
    echo "❌ 错误: API Key 格式不正确"
    echo "   正确格式: sk- 开头，后跟至少32位字符"
    echo ""
    echo "📍 如何获取 DeepSeek API Key:"
    echo "   1. 访问 https://platform.deepseek.com/"
    echo "   2. 注册/登录账号"
    echo "   3. 进入 'API Keys' 管理页面"
    echo "   4. 创建新 Key 或复制现有 Key"
    exit 1
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "选择配置方式："
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "1) 仅设置环境变量 (推荐，不会修改文件)"
echo "2) 写入配置文件 (application.yml)"
echo "3) 两者都配置"
echo ""
read -p "请选择 (1-3): " -n 1 -r choice
echo ""
echo ""

case $choice in
    1)
        export DEEPSEEK_API_KEY="$api_key"
        echo "✓ 已设置环境变量"
        echo ""
        echo "⚠️  注意: 环境变量仅在当前终端会话有效"
        echo "   要永久生效，请添加到 ~/.bashrc 或 ~/.zshrc:"
        echo ""
        echo "   echo 'export DEEPSEEK_API_KEY=$api_key' >> ~/.zshrc"
        echo "   source ~/.zshrc"
        ;;
    2)
        # 更新 application.yml
        sed -i.bak "s|api-key:.*|api-key: $api_key|" src/main/resources/application.yml
        echo "✓ 已更新 application.yml"
        echo ""
        echo "⚠️  注意: API Key 已写入配置文件"
        echo "   请勿将此文件提交到 Git 仓库！"
        ;;
    3)
        export DEEPSEEK_API_KEY="$api_key"
        sed -i.bak "s|api-key:.*|api-key: $api_key|" src/main/resources/application.yml
        echo "✓ 已设置环境变量"
        echo "✓ 已更新 application.yml"
        echo ""
        echo "⚠️  注意: 请勿将配置文件提交到 Git 仓库！"
        ;;
    *)
        echo "❌ 无效选择"
        exit 1
        ;;
esac

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ 配置完成！"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "下一步操作："
echo ""
echo "1. 确保 zkInfo MCP Server 正在运行:"
echo "   cd ../zkInfo && mvn spring-boot:run"
echo ""
echo "2. 启动 MCP AI Client:"
echo "   ./start.sh"
echo ""
echo "3. 访问 Web 界面:"
echo "   http://localhost:8081"
echo ""

