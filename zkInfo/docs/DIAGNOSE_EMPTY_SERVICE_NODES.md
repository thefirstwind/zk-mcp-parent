# zk_dubbo_service_node 表为空问题排查手册

**问题描述**: 生产环境 `zk_dubbo_service_node` 表应该有条数据，但实际为空。

**影响**: 无法查询服务节点信息，虚拟项目无法聚合 Provider，MCP 工具调用可能失败。

---

## 🔍 快速排查步骤

### 步骤 1: 检查服务状态

```bash
# 检查 zkInfo 服务是否正常运行
curl -s "${ZKINFO_URL}/actuator/health"

# 检查服务统计信息
curl -s "${ZKINFO_URL}/api/stats"
```

### 步骤 2: 检查 ZooKeeper 数据

```bash
# 检查 ZooKeeper 连接
curl -s "${ZKINFO_URL}/api/debug/zk-tree"

# 检查 ZooKeeper 中是否有 Provider（需要 zkCli）
zkCli.sh -server localhost:2181 ls /dubbo
zkCli.sh -server localhost:2181 ls /dubbo/com.example.Service/providers
```

### 步骤 3: 检查数据库表

```bash
# 检查表记录数（需要 mysql 客户端）
mysql -h${MYSQL_HOST} -u${MYSQL_USERNAME} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} \
  -e "SELECT COUNT(*) FROM zk_dubbo_service_node;"

# 检查服务表记录数
mysql -h${MYSQL_HOST} -u${MYSQL_USERNAME} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} \
  -e "SELECT COUNT(*) FROM zk_dubbo_service;"

# 检查表结构
mysql -h${MYSQL_HOST} -u${MYSQL_USERNAME} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} \
  -e "DESC zk_dubbo_service_node;"
```

### 步骤 4: 检查白名单配置

```bash
# 查询启用的过滤器
curl -s "${ZKINFO_URL}/api/filters/enabled"

# 检查配置文件（需要访问服务器）
cat application.yml | grep -A 5 "whitelist"
```

### 步骤 5: 检查服务审批状态

```bash
# 查询所有服务
curl -s "${ZKINFO_URL}/api/dubbo-services?page=1&size=10"

# 查询待审批服务
curl -s "${ZKINFO_URL}/api/dubbo-services/pending"

# 查询已审批服务
curl -s "${ZKINFO_URL}/api/dubbo-services/approved"
```

---

## 📋 关键日志检查点

### 1. 应用启动日志

查找以下关键词：

```bash
# 检查启动时的数据初始化（最近20条）
grep -i "zookeeper.*数据初始化\|批量拉取.*zookeeper\|bootstrap.*zookeeper" logs/zkinfo.log | tail -20

# 检查启动是否成功
grep -i "zookeeper.*初始化.*完成\|bootstrap.*complete\|数据初始化完成" logs/zkinfo.log | tail -10

# 检查启动时的 Provider 数量
grep -i "拉取到.*provider\|发现.*provider\|parallel.*拉取完成" logs/zkinfo.log | tail -10

# 检查启动时的白名单过滤
grep -i "白名单\|whitelist" logs/zkinfo.log | grep -i "初始化\|bootstrap\|过滤" | tail -10
```

**关键日志示例**:
```
🚀 开始批量拉取 ZooKeeper 数据并入库...
发现 X 个服务接口，开始并行拉取 Provider 信息
应用白名单过滤，白名单前缀: [com.example]
白名单过滤后，剩余 X 个服务接口（原始: Y）
✅ 并行拉取完成: X 个服务接口，共 Y 个 Provider，耗时: XXXms
从 ZooKeeper 拉取到 Y 个 Provider
✅ ZooKeeper 数据初始化完成，总耗时: XXXms
```

**如果日志中没有这些信息，说明启动时的批量拉取可能失败或未执行。**

---

### 2. Provider 保存日志

```bash
# 检查 Provider 保存成功日志（最近100条）
grep -i "保存.*provider.*数据库\|save.*provider.*database\|persist.*provider" logs/zkinfo.log | grep -i "成功\|success\|persisted" | tail -100

# 统计保存成功的数量
grep -i "保存.*provider.*数据库\|persist.*provider" logs/zkinfo.log | grep -i "成功\|success\|persisted" | wc -l

# 检查 Provider 保存失败日志（最近50条）
grep -i "保存.*provider.*数据库.*失败\|save.*provider.*fail\|persist.*provider.*fail" logs/zkinfo.log | tail -50

# 检查数据库操作异常
grep -i "保存.*provider\|persist.*provider" logs/zkinfo.log | grep -i "exception\|error" | tail -50
```

**关键日志示例**:
```
成功保存Provider到数据库: /dubbo/com.example.Service/providers/...
✅ Provider persisted to database: com.example.Service (192.168.1.100:20880) - online=true, healthy=true
保存Provider到数据库失败: /dubbo/com.example.Service/providers/... - 异常信息
❌ Failed to persist Provider registration: com.example.Service - SQLException: ...
```

**如果日志中只有失败记录或没有保存记录，说明数据保存流程有问题。**

---

### 3. 白名单过滤日志

```bash
# 检查白名单过滤日志（最近100条）
grep -i "不在白名单\|whitelist\|not.*allowed\|跳过入库" logs/zkinfo.log | tail -100

# 统计被过滤的接口数量
grep -i "不在白名单\|跳过入库" logs/zkinfo.log | wc -l

# 检查白名单配置加载日志
grep -i "白名单.*配置\|whitelist.*config\|加载.*白名单" logs/zkinfo.log | tail -20

# 检查白名单过滤规则
grep -i "白名单前缀\|whitelist.*prefix\|匹配白名单" logs/zkinfo.log | tail -20
```

**关键日志示例**:
```
应用白名单过滤，白名单前缀: [com.example, com.test]
✅ 服务 com.example.Service 匹配白名单前缀: com.example
❌ 服务 com.other.Service 不匹配白名单，跳过
接口 com.other.Service 不在白名单中，跳过入库
白名单过滤后，剩余 X 个服务接口（原始: Y）
```

**如果大量服务被白名单过滤，会导致节点表为空。**

---

### 4. ZooKeeper 连接错误日志

```bash
# 检查 ZooKeeper 连接错误（最近50条）
grep -i "zookeeper.*error\|zookeeper.*exception\|zookeeper.*fail\|connection.*timeout\|curator.*error" logs/zkinfo.log | tail -50

# 检查 ZooKeeper 连接状态
grep -i "zookeeper.*connect\|zookeeper.*connected\|zookeeper.*disconnect" logs/zkinfo.log | tail -20

# 检查 ZooKeeper 路径访问错误
grep -i "zookeeper.*path\|zkpath.*error\|providers.*path" logs/zkinfo.log | grep -i "error\|exception\|fail" | tail -30
```

**关键日志示例**:
```
ZooKeeper 连接失败: Connection timeout
ZooKeeper 会话过期: Session expired
无法访问 ZooKeeper 路径: /dubbo/com.example.Service/providers
```

---

### 5. 数据库操作错误日志

```bash
# 检查数据库操作错误（最近50条）
grep -i "database.*error\|sql.*error\|transaction.*rollback\|mysql.*error\|jdbc.*error" logs/zkinfo.log | tail -50

# 检查数据库连接错误
grep -i "database.*connection\|datasource.*error\|hikari.*error" logs/zkinfo.log | tail -20

# 检查 SQL 执行错误
grep -i "sql.*exception\|preparedstatement\|insert.*error\|update.*error" logs/zkinfo.log | tail -30

# 检查表不存在错误
grep -i "table.*not.*exist\|doesn't exist\|unknown table" logs/zkinfo.log
```

**关键日志示例**:
```
SQLException: Table 'mcp_bridge.zk_dubbo_service_node' doesn't exist
Transaction rolled back: Could not insert node
Database connection failed: Connection refused
```

---

### 6. 服务节点同步日志

```bash
# 检查手动同步节点日志
grep -i "同步.*节点\|sync.*node\|同步.*provider" logs/zkinfo.log | tail -50

# 检查同步成功/失败统计
grep -i "同步.*节点\|sync.*node" logs/zkinfo.log | grep -i "成功\|失败\|success\|fail" | tail -30

# 检查从 ZooKeeper 读取 Provider 节点日志
grep -i "从zookeeper.*读取\|读取到.*provider.*节点\|getchildren.*provider" logs/zkinfo.log | tail -30
```

**关键日志示例**:
```
从ZooKeeper读取到 X 个Provider节点: com.example.Service (ID: 1)
成功同步Provider节点: /dubbo/com.example.Service/providers/...
节点同步成功，同步了 X 个节点
```

---

## 🔧 常见原因和解决方案

### 原因 1: ZooKeeper 中没有 Provider 数据

**症状**:
- ZooKeeper 树结构中没有 `/dubbo/{service}/providers` 路径
- 或 providers 路径下没有节点

**排查命令**:
```bash
# 检查 ZooKeeper 中的服务
zkCli.sh -server localhost:2181 ls /dubbo

# 检查特定服务的 providers
zkCli.sh -server localhost:2181 ls /dubbo/com.example.Service/providers

# 通过 API 检查
curl -s "${ZKINFO_URL}/api/debug/zk-tree" | grep -o '/dubbo/[^/]*/providers' | head -10
```

**解决方案**:
1. 确保 Dubbo 服务已启动
2. 检查 Dubbo 服务的 ZooKeeper 注册配置
3. 验证服务是否成功注册到 ZooKeeper

---

### 原因 2: 白名单过滤导致数据被过滤

**症状**:
- ZooKeeper 中有 Provider 数据
- 但日志中显示 "不在白名单中，跳过入库"
- `zk_dubbo_service` 表也为空或只有部分服务

**排查命令**:
```bash
# 查询白名单配置
curl -s "${ZKINFO_URL}/api/filters/enabled"

# 检查配置文件
cat application.yml | grep -A 10 "zk.whitelist"

# 检查日志中被过滤的服务
grep -i "不在白名单\|跳过入库" logs/zkinfo.log | tail -20
```

**解决方案**:
1. 将服务接口添加到白名单
2. 或临时禁用白名单（不推荐生产环境）
3. 更新白名单配置后重启服务

**添加白名单示例**:
```bash
curl -X POST "${ZKINFO_URL}/api/filters" \
  -H "Content-Type: application/json" \
  -d '{
    "filterName": "Production Whitelist",
    "filterType": "WHITELIST",
    "enabled": true,
    "rules": [
      {
        "ruleType": "INTERFACE_PREFIX",
        "ruleValue": "com.example",
        "action": "INCLUDE",
        "priority": 1
      }
    ]
  }'
```

---

### 原因 3: 服务未审批，数据未保存

**症状**:
- `zk_dubbo_service` 表有数据
- 但 `zk_dubbo_service_node` 表为空
- 日志中可能有 "未审批服务" 相关提示

**排查命令**:
```bash
# 查询待审批服务
curl -s "${ZKINFO_URL}/api/dubbo-services/pending?page=1&size=10"

# 查询服务审批状态
SERVICE_ID=1
curl -s "${ZKINFO_URL}/api/dubbo-services/${SERVICE_ID}"
```

**解决方案**:
1. 审批服务：
```bash
curl -X POST "${ZKINFO_URL}/api/dubbo-services/{serviceId}/approve" \
  -H "Content-Type: application/json" \
  -d '{
    "reviewerId": 1,
    "reviewerName": "Admin",
    "comment": "Approved"
  }'
```

2. 审批后手动同步节点：
```bash
curl -X POST "${ZKINFO_URL}/api/dubbo-services/{serviceId}/sync-nodes"
```

---

### 原因 4: 数据库保存失败（异常被吞掉）

**症状**:
- 日志中有 "保存Provider到数据库失败" 错误
- 数据库连接正常，但数据未保存

**排查命令**:
```bash
# 检查数据库连接
mysql -h${MYSQL_HOST} -u${MYSQL_USERNAME} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} -e "SELECT 1;"

# 检查表结构是否正确
mysql -h${MYSQL_HOST} -u${MYSQL_USERNAME} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} \
  -e "DESC zk_dubbo_service_node;"

# 检查数据库错误日志
grep -i "sql.*error\|database.*exception\|transaction.*rollback" logs/zkinfo.log | tail -30

# 检查表是否存在
mysql -h${MYSQL_HOST} -u${MYSQL_USERNAME} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} \
  -e "SHOW TABLES LIKE 'zk_dubbo_service_node';"
```

**解决方案**:
1. 检查数据库表结构是否正确
2. 检查数据库权限
3. 检查事务是否回滚
4. 查看完整的异常堆栈信息

---

### 原因 5: 启动时的批量拉取失败

**症状**:
- 应用启动日志中没有 "ZooKeeper 数据初始化完成"
- 或初始化过程中出现异常

**排查命令**:
```bash
# 检查启动日志（前500行）
head -500 logs/zkinfo.log | grep -i "zookeeper\|bootstrap\|初始化"

# 检查启动异常
grep -i "exception\|error" logs/zkinfo.log | grep -i "bootstrap\|初始化\|zookeeper" | head -20

# 检查启动时的完整日志
grep -i "zookeeper.*初始化\|批量拉取" logs/zkinfo.log | head -50
```

**解决方案**:
1. 检查 ZooKeeper 连接配置
2. 检查网络连接
3. 重启应用并观察启动日志
4. 如果启动拉取失败，可以手动触发同步

---

### 原因 6: ZooKeeper 路径配置错误

**症状**:
- ZooKeeper 连接正常
- 但无法找到服务路径

**排查命令**:
```bash
# 检查配置的 ZooKeeper 路径
curl -s "${ZKINFO_URL}/api/debug/zk-tree" | grep -o '/dubbo'

# 检查实际 ZooKeeper 路径
zkCli.sh -server localhost:2181 ls /

# 检查配置文件
cat application.yml | grep -A 3 "zookeeper.base-path"
```

**解决方案**:
1. 检查 `application.yml` 中的 `zookeeper.base-path` 配置
2. 确保配置的路径与实际 ZooKeeper 路径一致
3. 更新配置后重启服务

---

## 🛠️ 修复步骤

### 步骤 1: 使用排查脚本

```bash
# 运行自动排查脚本
cd zk-mcp-parent/zkInfo/scripts
chmod +x diagnose-empty-service-nodes.sh

# 设置环境变量（根据实际情况调整）
export ZKINFO_URL="http://your-host:9091"
export MYSQL_HOST="your-mysql-host"
export MYSQL_USERNAME="your-username"
export MYSQL_PASSWORD="your-password"
export MYSQL_DATABASE="mcp_bridge"

# 执行排查
./diagnose-empty-service-nodes.sh > diagnose-report.txt 2>&1

# 查看排查报告
cat diagnose-report.txt
```

### 步骤 1.1: 快速检查（使用 curl）

```bash
#!/bin/bash
ZKINFO_URL="${ZKINFO_URL:-http://localhost:9091}"

echo "=== 1. 服务状态 ==="
curl -s "${ZKINFO_URL}/actuator/health"
echo ""

echo "=== 2. 服务统计 ==="
curl -s "${ZKINFO_URL}/api/stats"
echo ""

echo "=== 3. 服务数量 ==="
SERVICES=$(curl -s "${ZKINFO_URL}/api/dubbo-services?page=1&size=1")
TOTAL=$(echo "$SERVICES" | grep -o '"total"[^,}]*[0-9]*' | grep -o '[0-9]*' | head -1)
echo "总服务数: $TOTAL"
echo ""

echo "=== 4. 节点数量（前5个服务）==="
for i in 1 2 3 4 5; do
    PAGE_SERVICES=$(curl -s "${ZKINFO_URL}/api/dubbo-services?page=$i&size=1")
    SERVICE_ID=$(echo "$PAGE_SERVICES" | grep -o '"id"[^,}]*[0-9]*' | grep -o '[0-9]*' | head -1)
    if [ ! -z "$SERVICE_ID" ]; then
        NODES=$(curl -s "${ZKINFO_URL}/api/dubbo-services/${SERVICE_ID}/nodes")
        NODE_COUNT=$(echo "$NODES" | grep -o '{' | wc -l | tr -d ' ')
        echo "服务 $SERVICE_ID: $NODE_COUNT 个节点"
    fi
done
echo ""

echo "=== 5. ZooKeeper 连接 ==="
curl -s "${ZKINFO_URL}/api/debug/zk-tree" | head -20
echo ""

echo "=== 6. 白名单配置 ==="
curl -s "${ZKINFO_URL}/api/filters/enabled"
echo ""
```

### 步骤 2: 根据排查结果修复

#### 如果 ZooKeeper 中没有数据：
```bash
# 1. 启动或重启 Dubbo 服务
# 2. 验证服务注册到 ZooKeeper
zkCli.sh -server localhost:2181 ls /dubbo/com.example.Service/providers
```

#### 如果白名单过滤：
```bash
# 1. 添加服务到白名单
curl -X POST "${ZKINFO_URL}/api/filters" \
  -H "Content-Type: application/json" \
  -d '{
    "filterName": "Production Whitelist",
    "filterType": "WHITELIST",
    "enabled": true,
    "rules": [{"ruleType": "INTERFACE_PREFIX", "ruleValue": "com.example", "action": "INCLUDE", "priority": 1}]
  }'

# 2. 重启服务或等待自动同步
```

#### 如果服务未审批：
```bash
# 1. 查询待审批服务
PENDING=$(curl -s "${ZKINFO_URL}/api/dubbo-services/pending?page=1&size=100")

# 2. 提取服务ID并审批（需要解析 JSON）
# 示例：审批第一个服务
SERVICE_ID=1
curl -X POST "${ZKINFO_URL}/api/dubbo-services/${SERVICE_ID}/approve" \
  -H "Content-Type: application/json" \
  -d '{"reviewerId": 1, "reviewerName": "Admin", "comment": "Approved"}'

# 3. 手动同步节点
curl -X POST "${ZKINFO_URL}/api/dubbo-services/${SERVICE_ID}/sync-nodes"
```

#### 如果数据库保存失败：
```bash
# 1. 检查数据库连接
mysql -h${MYSQL_HOST} -u${MYSQL_USERNAME} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} -e "SELECT 1;"

# 2. 检查表结构
mysql -h${MYSQL_HOST} -u${MYSQL_USERNAME} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} \
  -e "DESC zk_dubbo_service_node;"

# 3. 查看详细错误日志
grep -i "保存.*provider.*失败\|sql.*error" logs/zkinfo.log | tail -50

# 4. 修复后重启服务
```

### 步骤 3: 使用修复脚本批量同步

```bash
# 使用自动修复脚本批量同步所有服务节点
cd zk-mcp-parent/zkInfo/scripts
chmod +x fix-empty-service-nodes.sh

# 设置环境变量
export ZKINFO_URL="http://your-host:9091"

# 执行修复
./fix-empty-service-nodes.sh > fix-report.txt 2>&1

# 查看修复报告
cat fix-report.txt
```

### 步骤 3.1: 手动触发同步（单个服务）

```bash
# 获取服务列表
SERVICES=$(curl -s "${ZKINFO_URL}/api/dubbo-services?page=1&size=100")

# 提取服务ID（需要手动解析或使用脚本）
# 示例：同步第一个服务
SERVICE_ID=1
curl -X POST "${ZKINFO_URL}/api/dubbo-services/${SERVICE_ID}/sync-nodes"

# 验证同步结果
curl -s "${ZKINFO_URL}/api/dubbo-services/${SERVICE_ID}/nodes" | grep -o '{' | wc -l
```

### 步骤 3.2: 批量同步脚本（纯 curl）

```bash
#!/bin/bash
ZKINFO_URL="${ZKINFO_URL:-http://localhost:9091}"

# 获取所有服务ID并逐个同步
PAGE=1
while true; do
    RESPONSE=$(curl -s "${ZKINFO_URL}/api/dubbo-services?page=${PAGE}&size=100")
    SERVICE_IDS=$(echo "$RESPONSE" | grep -o '"id"[^,}]*[0-9]*' | sed 's/"id"[^,}]*\([0-9]*\)/\1/')
    
    if [ -z "$SERVICE_IDS" ]; then
        break
    fi
    
    for SERVICE_ID in $SERVICE_IDS; do
        echo "同步服务节点: $SERVICE_ID"
        curl -s -X POST "${ZKINFO_URL}/api/dubbo-services/${SERVICE_ID}/sync-nodes"
        sleep 0.1
    done
    
    PAGE=$((PAGE + 1))
done
```

### 步骤 4: 验证修复结果

```bash
# 4.1 通过 API 检查节点数据
curl -s "${ZKINFO_URL}/api/dubbo-services/{serviceId}/nodes"

# 4.2 检查节点总数（通过 API）
curl -s "${ZKINFO_URL}/api/stats" | grep -o '"onlineProviders"[^,}]*[0-9]*' | grep -o '[0-9]*'

# 4.3 直接查询数据库（如果有 mysql 客户端）
mysql -h${MYSQL_HOST} -u${MYSQL_USERNAME} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} \
  -e "SELECT COUNT(*) as total_nodes FROM zk_dubbo_service_node;"

# 4.4 检查在线节点数量
mysql -h${MYSQL_HOST} -u${MYSQL_USERNAME} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} \
  -e "SELECT COUNT(*) as online_nodes FROM zk_dubbo_service_node WHERE is_online = 1;"

# 4.5 检查健康节点数量
mysql -h${MYSQL_HOST} -u${MYSQL_USERNAME} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} \
  -e "SELECT COUNT(*) as healthy_nodes FROM zk_dubbo_service_node WHERE is_healthy = 1;"

# 4.6 查看前10条节点记录
mysql -h${MYSQL_HOST} -u${MYSQL_USERNAME} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} \
  -e "SELECT id, service_id, interface_name, address, is_online, is_healthy, last_heartbeat_time FROM zk_dubbo_service_node LIMIT 10;"

# 4.7 检查服务与节点的关联
mysql -h${MYSQL_HOST} -u${MYSQL_USERNAME} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} \
  -e "SELECT s.id as service_id, s.interface_name, COUNT(n.id) as node_count FROM zk_dubbo_service s LEFT JOIN zk_dubbo_service_node n ON s.id = n.service_id GROUP BY s.id LIMIT 10;"
```

---

## 📊 数据同步流程

理解数据同步流程有助于排查问题：

```
ZooKeeper Provider 节点
    ↓
【启动时】ZooKeeperBootstrapService.bootstrapZooKeeperData()
    ↓
loadAllProvidersFromZooKeeper() - 批量拉取所有 Provider
    ↓
persistProvidersByInterface() - 按接口分组持久化
    ↓
【运行时】ZooKeeperService.watchServiceProviders()
    ↓
handleProviderAdded() / saveServiceProvidersToDatabase()
    ↓
白名单检查 (InterfaceWhitelistService.isAllowed())
    ↓
DubboServiceDbService.saveOrUpdateServiceWithNode()
    ↓
  ├─ saveOrUpdateService() → 保存到 zk_dubbo_service 表
  ├─ saveServiceNode() → 保存到 zk_dubbo_service_node 表
  └─ saveOrUpdateServiceMethods() → 保存到 zk_dubbo_service_method 表
```

**关键检查点**:
1. ✅ ZooKeeper 中是否有 Provider 节点
2. ✅ 启动时的批量拉取是否成功
3. ✅ 白名单是否允许该接口
4. ✅ 服务是否已审批（某些场景）
5. ✅ 数据库保存是否成功（检查异常日志）

**对应的日志关键词**:
- `批量拉取 ZooKeeper 数据` - 启动时批量拉取
- `保存Provider到数据库` - 数据保存
- `不在白名单中，跳过入库` - 白名单过滤
- `保存Provider到数据库失败` - 保存失败

---

## 🔄 预防措施

1. **监控告警**: 设置 `zk_dubbo_service_node` 表记录数告警
   ```bash
   # 定期检查节点数量（可加入 cron）
   NODE_COUNT=$(mysql -h${MYSQL_HOST} -u${MYSQL_USERNAME} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} \
     -e "SELECT COUNT(*) FROM zk_dubbo_service_node;" | tail -1)
   if [ "$NODE_COUNT" -eq 0 ]; then
     echo "警告: zk_dubbo_service_node 表为空！" | mail -s "告警" admin@example.com
   fi
   ```

2. **定期检查**: 定期运行排查脚本
   ```bash
   # 添加到 crontab（每天凌晨2点执行）
   0 2 * * * /path/to/diagnose-empty-service-nodes.sh >> /var/log/zkinfo-diagnose.log 2>&1
   ```

3. **日志监控**: 监控关键日志关键词
   ```bash
   # 监控保存失败日志
   tail -f logs/zkinfo.log | grep -i "保存.*provider.*失败\|persist.*fail"
   
   # 监控白名单过滤
   tail -f logs/zkinfo.log | grep -i "不在白名单\|跳过入库"
   ```

---

## 📞 联系支持

如果以上步骤无法解决问题，请提供以下信息：

1. **排查脚本的完整输出**
   ```bash
   ./diagnose-empty-service-nodes.sh > diagnose-report.txt 2>&1
   ```

2. **应用启动日志（最近一次启动）**
   ```bash
   head -500 logs/zkinfo.log > startup.log
   ```

3. **错误日志（最近24小时）**
   ```bash
   grep -i "error\|exception\|fail" logs/zkinfo.log | tail -200 > errors.log
   ```

4. **数据库表结构**
   ```bash
   mysql -h${MYSQL_HOST} -u${MYSQL_USERNAME} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} \
     -e "DESC zk_dubbo_service_node;" > table-structure.txt
   ```

5. **ZooKeeper 路径结构**
   ```bash
   zkCli.sh -server localhost:2181 ls /dubbo > zk-structure.txt
   ```

---

**文档版本**: 1.0.0  
**更新日期**: 2025-12-26

