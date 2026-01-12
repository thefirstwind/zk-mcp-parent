#!/bin/bash

echo "=========================================="
echo "zkInfo 完整验证"
echo "=========================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 1. 验证服务注册
echo "1. 验证服务注册..."
"${SCRIPT_DIR}/verify-service-registration.sh"

# 2. 验证虚拟项目
echo ""
echo "2. 验证虚拟项目..."
ENDPOINT_NAME=${1:-test-endpoint001}
"${SCRIPT_DIR}/verify-virtual-project.sh" "${ENDPOINT_NAME}"

# 3. 完整流程演示
echo ""
echo "3. 完整流程演示..."
"${SCRIPT_DIR}/demo-complete-flow.sh"

echo ""
echo "=========================================="
echo "验证完成！"
echo "=========================================="


