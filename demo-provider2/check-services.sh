#!/bin/bash

# 检查 demo-provider2 服务是否注册到 ZooKeeper

ZKINFO_URL="${ZKINFO_URL:-http://localhost:9091}"
ZK_SERVER="${ZK_SERVER:-localhost:2181}"

echo "=========================================="
echo "检查 demo-provider2 服务注册状态"
echo "=========================================="
echo ""

# 1. 检查 zkInfo 服务是否运行
echo "【1】检查 zkInfo 服务状态..."
if curl -s -f "${ZKINFO_URL}/actuator/health" > /dev/null 2>&1; then
    echo "✅ zkInfo 服务运行正常"
else
    echo "❌ zkInfo 服务未运行或无法访问"
    echo "   请确保 zkInfo 服务已启动: ${ZKINFO_URL}"
    exit 1
fi
echo ""

# 2. 检查 ZooKeeper 树结构
echo "【2】检查 ZooKeeper 中的服务..."
ZK_TREE=$(curl -s "${ZKINFO_URL}/api/debug/zk-tree" 2>/dev/null)
if [ -z "$ZK_TREE" ]; then
    echo "❌ 无法获取 ZooKeeper 树结构"
    exit 1
fi

# 查找 demo-provider2 相关的服务
PROVIDER2_SERVICES=$(echo "$ZK_TREE" | grep -o "com\.pajk\.provider2\.[^/]*" | sort -u)
if [ -z "$PROVIDER2_SERVICES" ]; then
    echo "❌ 未找到 demo-provider2 的服务"
    echo ""
    echo "ZooKeeper 中所有服务列表："
    echo "$ZK_TREE" | grep -o "/dubbo/[^/]*" | sort -u | sed 's|/dubbo/||' | head -20
else
    echo "✅ 找到 demo-provider2 的服务："
    echo "$PROVIDER2_SERVICES" | while read svc; do
        echo "   - $svc"
    done
fi
echo ""

# 3. 通过 API 检查注册的服务
echo "【3】通过 API 检查注册的服务..."
DUBBO_SERVICES=$(curl -s "${ZKINFO_URL}/api/dubbo-services?page=1&size=100" 2>/dev/null)
if [ -z "$DUBBO_SERVICES" ]; then
    echo "❌ 无法获取服务列表"
else
    # 使用 Python 解析 JSON（如果没有 jq）
    PROVIDER2_IN_DB=$(echo "$DUBBO_SERVICES" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    services = data.get('data', [])
    provider2_services = [s for s in services if 'provider2' in s.get('interfaceName', '').lower()]
    for svc in provider2_services:
        print(f\"{svc.get('interfaceName')} - {svc.get('version')} - {svc.get('group', 'default')}\")
except:
    pass
" 2>/dev/null)
    
    if [ -z "$PROVIDER2_IN_DB" ]; then
        echo "⚠️  数据库中未找到 demo-provider2 的服务"
        echo "   服务可能还未同步到数据库"
    else
        echo "✅ 数据库中找到 demo-provider2 的服务："
        echo "$PROVIDER2_IN_DB" | while read svc; do
            echo "   - $svc"
        done
    fi
fi
echo ""

# 4. 检查服务节点
echo "【4】检查服务节点信息..."
if [ ! -z "$PROVIDER2_SERVICES" ]; then
    echo "$PROVIDER2_SERVICES" | while read svc; do
        echo "检查服务: $svc"
        NODES=$(curl -s "${ZKINFO_URL}/api/dubbo-services?interfaceName=${svc}" 2>/dev/null | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    services = data.get('data', [])
    if services:
        service_id = services[0].get('id')
        if service_id:
            print(service_id)
except:
    pass
" 2>/dev/null)
        
        if [ ! -z "$NODES" ]; then
            echo "   服务ID: $NODES"
            NODE_INFO=$(curl -s "${ZKINFO_URL}/api/dubbo-services/${NODES}/nodes" 2>/dev/null)
            if [ ! -z "$NODE_INFO" ]; then
                NODE_COUNT=$(echo "$NODE_INFO" | python3 -c "import sys, json; data = json.load(sys.stdin); print(len(data) if isinstance(data, list) else 0)" 2>/dev/null)
                echo "   节点数量: $NODE_COUNT"
            fi
        fi
    done
fi
echo ""

# 5. 总结
echo "=========================================="
echo "检查完成"
echo "=========================================="
echo ""
echo "如果服务未注册，请检查："
echo "1. demo-provider2 应用是否正在运行"
echo "2. ZooKeeper 服务是否正常运行 (${ZK_SERVER})"
echo "3. 应用日志中是否有注册错误"
echo "4. 端口 20884 是否被占用"


