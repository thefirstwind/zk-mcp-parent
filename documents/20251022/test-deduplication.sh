#!/bin/bash

echo "=============================================="
echo "🧪 测试MCP工具列表去重功能"
echo "=============================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 测试1: 获取工具列表并检查去重
echo -e "${BLUE}📋 测试1: 获取工具列表${NC}"
echo "发送 tools/list 请求..."
echo ""

RESPONSE=$(curl -s -X POST http://localhost:9091/mcp/jsonrpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-dedup",
    "method": "tools/list",
    "params": {}
  }')

# 使用Python解析JSON并检查去重
RESULT=$(echo "$RESPONSE" | python3 << 'EOF'
import sys
import json

data = json.load(sys.stdin)
tools = data.get('result', {}).get('tools', [])

tool_names = [t['name'] for t in tools]
total_count = len(tool_names)
unique_count = len(set(tool_names))

print(f"工具总数: {total_count}")
print(f"唯一工具: {unique_count}")

if total_count == unique_count:
    print("✅ 去重成功: 没有重复的工具")
else:
    print(f"❌ 去重失败: 发现 {total_count - unique_count} 个重复")
    
    # 找出重复的工具
    from collections import Counter
    counter = Counter(tool_names)
    duplicates = {name: count for name, count in counter.items() if count > 1}
    
    if duplicates:
        print("\n重复的工具:")
        for name, count in duplicates.items():
            print(f"  - {name} (出现 {count} 次)")

print("\n工具列表:")
for i, name in enumerate(tool_names, 1):
    print(f"{i:2d}. {name}")
EOF
)

echo "$RESULT"
echo ""

# 测试2: 验证工具分类
echo -e "${BLUE}📊 测试2: 工具分类统计${NC}"
echo ""

STATS=$(echo "$RESPONSE" | python3 << 'EOF'
import sys
import json
from collections import defaultdict

data = json.load(sys.stdin)
tools = data.get('result', {}).get('tools', [])

# 按服务分类
services = defaultdict(list)
for tool in tools:
    # 提取服务名（去掉方法名）
    parts = tool['name'].rsplit('.', 1)
    if len(parts) == 2:
        service_name = parts[0]
        method_name = parts[1]
        services[service_name].append(method_name)

print("按服务分类:")
for service, methods in sorted(services.items()):
    service_short = service.split('.')[-1]
    print(f"\n{service_short} ({len(methods)} 个方法):")
    for method in sorted(methods):
        print(f"  - {method}")
EOF
)

echo "$STATS"
echo ""

# 测试3: 验证provider信息
echo -e "${BLUE}🌐 测试3: Provider信息${NC}"
echo ""

PROVIDERS=$(echo "$RESPONSE" | python3 << 'EOF'
import sys
import json
from collections import Counter

data = json.load(sys.stdin)
tools = data.get('result', {}).get('tools', [])

# 统计provider
providers = [t.get('provider', 'N/A') for t in tools]
online_count = sum(1 for t in tools if t.get('online', False))

print(f"在线工具: {online_count}/{len(tools)}")
print(f"\nProvider统计:")
for provider, count in Counter(providers).items():
    print(f"  {provider}: {count} 个工具")
EOF
)

echo "$PROVIDERS"
echo ""

echo "=============================================="
echo -e "${GREEN}✅ 测试完成！${NC}"
echo "=============================================="


