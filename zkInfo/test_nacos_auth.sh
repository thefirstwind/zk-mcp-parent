#!/bin/bash

# Nacos 认证测试脚本
# 此脚本用于测试 Nacos 服务器的认证配置

NACOS_ADDR="127.0.0.1:8848"
USERNAME="nacos"
PASSWORD="nacos"

echo "=== Nacos 认证测试 ==="
echo

# 1. 测试登录获取 token
echo "1. 测试登录 (POST /nacos/v1/auth/login)"
LOGIN_RESPONSE=$(curl -s -X POST "http://${NACOS_ADDR}/nacos/v1/auth/login" \
  -d "username=${USERNAME}&password=${PASSWORD}")

echo "Response: $LOGIN_RESPONSE"
echo

# 检查是否成功获取 token
if echo "$LOGIN_RESPONSE" | grep -q "accessToken"; then
    ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"accessToken":"[^"]*"' | sed 's/"accessToken":"//;s/"//')
    echo "✅ Login successful! Access Token: ${ACCESS_TOKEN:0:20}..."
    echo
    
    # 2. 使用 token 查询服务列表
    echo "2. 测试查询服务列表 (GET /nacos/v1/ns/service/list)"
    curl -s "http://${NACOS_ADDR}/nacos/v1/ns/service/list?pageNo=1&pageSize=10&accessToken=${ACCESS_TOKEN}"
    echo
    echo
    
else
    echo "❌ Login failed!"
    echo "可能的原因："
    echo "  1. 用户名或密码错误"
    echo "  2. Nacos 认证未启用"
    echo "  3. Nacos Server 版本不兼容"
    echo
    
    # 3. 尝试无认证模式
    echo "3. 尝试无认证模式查询服务列表"
    ANON_RESPONSE=$(curl -s "http://${NACOS_ADDR}/nacos/v1/ns/service/list?pageNo=1&pageSize=10")
    echo "Response: $ANON_RESPONSE"
    echo
    
    if echo "$ANON_RESPONSE" | grep -q "count"; then
        echo "✅ 无认证模式可用（Nacos 认证可能未启用）"
    else
        echo "❌ 无认证模式也失败"
    fi
fi

echo
echo "=== 测试完成 ==="
