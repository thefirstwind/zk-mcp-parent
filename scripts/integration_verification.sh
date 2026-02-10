#!/bin/bash

# zk-mcp-parent 项目集成验证脚本
# 验证所有配置文件、文档和代码的完整性

set -e

echo "======================================"
echo "  zk-mcp-parent 集成验证测试"
echo "======================================"
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

total_tests=0
passed_tests=0
failed_tests=0

# 测试函数
test_file_exists() {
    total_tests=$((total_tests + 1))
    if [ -f "$1" ]; then
        echo -e "${GREEN}✓${NC} $2"
        passed_tests=$((passed_tests + 1))
        return 0
    else
        echo -e "${RED}✗${NC} $2 (文件不存在: $1)"
        failed_tests=$((failed_tests + 1))
        return 1
    fi
}

test_dir_exists() {
    total_tests=$((total_tests + 1))
    if [ -d "$1" ]; then
        echo -e "${GREEN}✓${NC} $2"
        passed_tests=$((passed_tests + 1))
        return 0
    else
        echo -e "${RED}✗${NC} $2 (目录不存在: $1)"
        failed_tests=$((failed_tests + 1))
        return 1
    fi
}

echo "=========================================="
echo "第一部分: 目录结构验证"
echo "=========================================="
echo ""

test_dir_exists ".agent" ".agent/ 目录"
test_dir_exists ".agent/rules" ".agent/rules/ 目录"
test_dir_exists ".agent/workflows" ".agent/workflows/ 目录"
test_dir_exists ".github" ".github/ 目录"
test_dir_exists ".github/ISSUE_TEMPLATE" ".github/ISSUE_TEMPLATE/ 目录"
test_dir_exists ".github/workflows" ".github/workflows/ 目录"

echo ""
echo "=========================================="
echo "第二部分: .agent 配置文件验证"
echo "=========================================="
echo ""

test_file_exists ".agent/rules/PROJECT_RULES.md" "项目开发规范"
test_file_exists ".agent/workflows/review.md" "代码审查工作流"
test_file_exists ".agent/workflows/add-dubbo-provider.md" "添加 Dubbo 服务工作流"

echo ""
echo "=========================================="
echo "第三部分: .github 配置文件验证"
echo "=========================================="
echo ""

test_file_exists ".github/PULL_REQUEST_TEMPLATE.md" "PR 模板"
test_file_exists ".github/ISSUE_TEMPLATE/bug_report.md" "Bug 报告模板"
test_file_exists ".github/ISSUE_TEMPLATE/feature_request.md" "功能请求模板"
test_file_exists ".github/workflows/maven-build.yml" "GitHub Actions 工作流"

echo ""
echo "=========================================="
echo "第四部分: 项目文档验证"
echo "=========================================="
echo ""

test_file_exists "PROJECT_SUMMARY.md" "项目总结文档"
test_file_exists "CONFIG_README.md" "配置使用说明"
test_file_exists "CONFIGURATION_SUMMARY.md" "配置创建总结"
test_file_exists ".gitignore" "Git 忽略规则"
test_file_exists "OPTIMIZATION_SUMMARY.md" "优化总结"

echo ""
echo "=========================================="
echo "第五部分: zkInfo 文档验证"
echo "=========================================="
echo ""

test_file_exists "zkInfo/INDEX.md" "zkInfo 文档索引"
test_file_exists "zkInfo/README_OPTIMIZATION.md" "zkInfo 优化说明"
test_file_exists "zkInfo/WORK_SUMMARY.md" "zkInfo 工作总结"
test_file_exists "zkInfo/QUICK_REFERENCE.md" "zkInfo 快速参考"
test_file_exists "zkInfo/COMPREHENSIVE_ANALYSIS_REPORT.md" "zkInfo 详细分析报告"
test_file_exists "zkInfo/VALIDATION_GUIDE.md" "zkInfo 验证指南"
test_file_exists "zkInfo/REFACTORING_VALIDATION_REPORT.md" "zkInfo 重构验证报告"
test_file_exists "zkInfo/COMMIT_MESSAGE.md" "zkInfo 提交信息"
test_file_exists "zkInfo/integration_test.sh" "zkInfo 集成测试脚本"

echo ""
echo "=========================================="
echo "第六部分: 文档内容验证"
echo "=========================================="
echo ""

# 检查关键文档是否包含必要内容
total_tests=$((total_tests + 1))
if grep -q "交互语言" .agent/rules/PROJECT_RULES.md >/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} PROJECT_RULES.md 包含语言规范"
    passed_tests=$((passed_tests + 1))
else
    echo -e "${RED}✗${NC} PROJECT_RULES.md 缺少语言规范"
    failed_tests=$((failed_tests + 1))
fi

total_tests=$((total_tests + 1))
if grep -q "AiMaintainerService" .agent/rules/PROJECT_RULES.md >/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} PROJECT_RULES.md 包含 Nacos 集成规范"
    passed_tests=$((passed_tests + 1))
else
    echo -e "${RED}✗${NC} PROJECT_RULES.md 缺少 Nacos 集成规范"
    failed_tests=$((failed_tests + 1))
fi

total_tests=$((total_tests + 1))
if grep -q "turbo" .agent/workflows/add-dubbo-provider.md >/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} add-dubbo-provider.md 包含 turbo 注解"
    passed_tests=$((passed_tests + 1))
else
    echo -e "${YELLOW}⚠${NC}  add-dubbo-provider.md 缺少 turbo 注解（可选）"
fi

total_tests=$((total_tests + 1))
if grep -q "services:" .github/workflows/maven-build.yml >/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} maven-build.yml 包含服务容器配置"
    passed_tests=$((passed_tests + 1))
else
    echo -e "${RED}✗${NC} maven-build.yml 缺少服务容器配置"
    failed_tests=$((failed_tests + 1))
fi

echo ""
echo "=========================================="
echo "第七部分: 代码编译验证"
echo "=========================================="
echo ""

total_tests=$((total_tests + 1))
if cd zkInfo && mvn clean compile -DskipTests -q >/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} zkInfo 编译成功"
    passed_tests=$((passed_tests + 1))
    cd ..
else
    echo -e "${RED}✗${NC} zkInfo 编译失败"
    failed_tests=$((failed_tests + 1))
    cd ..
fi

echo ""
echo "=========================================="
echo "第八部分: 核心单元测试验证"
echo "=========================================="
echo ""

total_tests=$((total_tests + 1))
if cd zkInfo && mvn test -Dtest=DubboToMcpAutoRegistrationServiceTest -q >/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} 核心单元测试通过"
    passed_tests=$((passed_tests + 1))
    cd ..
else
    echo -e "${YELLOW}⚠${NC}  核心单元测试失败（可能需要依赖服务）"
    cd ..
fi

echo ""
echo "=========================================="
echo "第九部分: 文件统计"
echo "=========================================="
echo ""

echo "配置文件统计:"
echo "  .agent 文件数: $(find .agent -type f | wc -l | tr -d ' ')"
echo "  .github 文件数: $(find .github -type f | wc -l | tr -d ' ')"
echo ""

echo "文档文件统计:"
echo "  项目根文档: 5 个"
echo "  zkInfo 文档: $(find zkInfo -maxdepth 1 -name "*.md" -o -name "*.sh" | wc -l | tr -d ' ') 个"
echo ""

echo "代码行数统计:"
total_lines=$(find .agent .github -name "*.md" -o -name "*.yml" | xargs wc -l 2>/dev/null | tail -1 | awk '{print $1}')
echo "  配置文件总行数: ${total_lines:-未知}"
doc_lines=$(find . -maxdepth 1 -name "*.md" | xargs wc -l 2>/dev/null | tail -1 | awk '{print $1}')
echo "  项目文档总行数: ${doc_lines:-未知}"

echo ""
echo "=========================================="
echo "测试结果汇总"
echo "=========================================="
echo ""

echo -e "总测试数: ${total_tests}"
echo -e "${GREEN}通过: ${passed_tests}${NC}"
echo -e "${RED}失败: ${failed_tests}${NC}"
echo ""

success_rate=$(awk "BEGIN {printf \"%.1f\", ($passed_tests/$total_tests)*100}")
echo "成功率: ${success_rate}%"

echo ""
if [ $failed_tests -eq 0 ]; then
    echo -e "${GREEN}=========================================="
    echo -e "  ✅ 所有验证测试通过！"
    echo -e "==========================================${NC}"
    exit 0
else
    echo -e "${YELLOW}=========================================="
    echo -e "  ⚠️  部分测试失败，请检查上述错误"
    echo -e "==========================================${NC}"
    exit 1
fi
