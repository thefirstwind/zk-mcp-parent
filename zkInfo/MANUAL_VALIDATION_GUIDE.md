# zkInfo 手工验证手册

## 📋 验证目标

验证 zkInfo 的以下核心功能：
1. ✅ Nacos 3.x 集成（AiMaintainerService + ConfigService 降级）
2. ✅ 虚拟节点创建逻辑
3. ✅ MD5 本地计算
4. ✅ Dubbo 服务发现和注册
5. ✅ 元数据完整性

---

## 🔧 环境准备

### 必需环境
- ✅ Java 17+
- ✅ Maven 3.8+
- ✅ Nacos Server 3.x（推荐 3.1.1+）
- ✅ MySQL 8.0+
- ❓ ZooKeeper（可选，如果测试 Dubbo 服务发现）

### 可选环境
- ❓ Dubbo 服务提供者（demo-provider）
- ❓ mcp-router-v3（端到端测试）

---

## 📝 验证步骤

### 第一步：检查代码和配置

#### 1.1 查看核心代码改动
```bash
cd zkInfo

# 查看 NacosMcpRegistrationService.java 的关键方法
echo "=== 查看核心服务类 ==="
ls -lh src/main/java/com/pajk/mcpmetainfo/core/service/NacosMcpRegistrationService.java
```

#### 1.2 检查依赖配置
```bash
# 查看 pom.xml 中的 Nacos 依赖
echo "=== 检查 Nacos 依赖版本 ==="
grep -A 2 "nacos-client\|nacos-maintainer" pom.xml
```

**预期输出**:
```xml
<dependency>
    <groupId>com.alibaba.nacos</groupId>
    <artifactId>nacos-client</artifactId>
    <version>3.0.1</version>
</dependency>
```

#### 1.3 检查配置文件
```bash
# 查看 application.yml 配置
echo "=== 检查 Nacos 配置 ==="
grep -A 10 "nacos:" src/main/resources/application.yml
```

**检查点**:
- [ ] server-addr 配置正确
- [ ] namespace 配置（如有）
- [ ] username/password 配置（如有）

---

### 第二步：编译项目

#### 2.1 清理编译
```bash
echo "=== 开始编译 zkInfo ==="
mvn clean compile -DskipTests
```

**预期输出**:
```
[INFO] BUILD SUCCESS
[INFO] Total time: XX.XXX s
```

**验证点**:
- [ ] 编译成功，无错误
- [ ] 可能有少量警告（@Deprecated 等），可忽略

#### 2.2 检查编译产物
```bash
ls -lh target/classes/com/pajk/mcpmetainfo/core/service/ | grep NacosMcp
```

**预期输出**:
```
NacosMcpRegistrationService.class
```

---

### 第三步：运行单元测试

#### 3.1 运行核心测试
```bash
echo "=== 运行核心单元测试 ==="
mvn test -Dtest=DubboToMcpAutoRegistrationServiceTest
```

**预期输出**:
```
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**验证点**:
- [ ] 测试通过: 2/2
- [ ] 日志显示 "✅ Updated registered service"
- [ ] 日志显示 "⚠️ All providers removed"

#### 3.2 检查测试日志
```bash
# 查看完整的测试输出
mvn test -Dtest=DubboToMcpAutoRegistrationServiceTest 2>&1 | grep -E "✅|❌|⚠️|🚀|📦"
```

**关键日志**:
```
✅ Updated registered service: com.example.DemoService:1.0.0
⚠️ All providers removed for registered service
```

---

### 第四步：检查关键代码逻辑

#### 4.1 验证 AiMaintainerService 初始化
```bash
echo "=== 检查 AiMaintainerService 初始化逻辑 ==="
grep -A 30 "@PostConstruct" src/main/java/com/pajk/mcpmetainfo/core/service/NacosMcpRegistrationService.java | head -35
```

**关键代码**:
```java
@PostConstruct
public void init() {
    if (!registryEnabled) {
        return;
    }
    try {
        // ... 初始化 AiMaintainerService
        this.aiMaintainerService = AiMaintainerFactory.createAiMaintainerService(properties);
        log.info("✅ AiMaintainerService initialized successfully");
    } catch (Exception e) {
        log.error("❌ Failed to initialize AiMaintainerService", e);
        // 不抛出异常，允许降级
    }
}
```

**验证点**:
- [ ] 有 AiMaintainerService 初始化逻辑
- [ ] 有异常捕获（不阻塞启动）
- [ ] 有成功和失败日志

#### 4.2 验证双路径注册策略
```bash
echo "=== 检查双路径注册逻辑 ==="
grep -A 15 "if (aiMaintainerService != null)" src/main/java/com/pajk/mcpmetainfo/core/service/NacosMcpRegistrationService.java | head -20
```

**关键代码**:
```java
// 优先使用 AiMaintainerService
if (aiMaintainerService != null) {
    useMaintainer = publishMcpServerToNacosUsingMaintainerService(...);
}

// 降级使用 ConfigService
if (!useMaintainer) {
    serverContent = publishConfigsToNacos(...);
}
```

**验证点**:
- [ ] 有优先使用 AiMaintainerService 的逻辑
- [ ] 有降级到 ConfigService 的逻辑
- [ ] 两种方式都能正常工作

#### 4.3 验证 MD5 本地计算
```bash
echo "=== 检查 MD5 计算逻辑 ==="
grep -B 5 -A 10 "publishConfigsToNacos" src/main/java/com/pajk/mcpmetainfo/core/service/NacosMcpRegistrationService.java | grep -A 15 "private String publishConfigs"
```

**关键代码**:
```java
private String publishConfigsToNacos(...) {
    // ... 发布配置
    String serverContent = createServerConfig(...);
    configService.publishConfig(..., serverContent);
    
    return serverContent; // ✅ 返回内容用于本地 MD5 计算
}
```

**验证点**:
- [ ] publishConfigsToNacos 方法返回 String（配置内容）
- [ ] 不是从 Nacos 读取配置计算 MD5
- [ ] MD5 使用本地内容计算

#### 4.4 验证虚拟节点逻辑
```bash
echo "=== 检查虚拟节点创建逻辑 ==="
grep -A 20 "registerInstancesToNacosForAllNodes" src/main/java/com/pajk/mcpmetainfo/core/service/NacosMcpRegistrationService.java | head -25
```

**关键代码**:
```java
// 发现所有活跃节点
List<ZkInfoNode> activeNodes = zkInfoNodeDiscoveryService.getAllActiveZkInfoNodes();

// 为每个节点创建虚拟实例
for (ZkInfoNode node : activeNodes) {
    registerInstanceToNacosForNode(..., node.getIp(), node.getPort(), ...);
}
```

**验证点**:
- [ ] 有节点发现逻辑
- [ ] 有循环为每个节点创建实例
- [ ] 有错误处理（部分失败不影响整体）

---

### 第五步：启动 zkInfo（可选，需要环境）

#### 5.1 配置 Nacos 连接
```bash
echo "=== 配置 Nacos 连接信息 ==="
cat << 'EOF' > src/main/resources/application-local.yml
spring:
  cloud:
    nacos:
      server-addr: localhost:8848
      discovery:
        namespace: public
        group: mcp-server
      config:
        namespace: public
        
registry:
  enabled: true
  
server:
  port: 9091
EOF

echo "✅ 配置文件已创建"
```

#### 5.2 启动应用
```bash
echo "=== 启动 zkInfo ==="
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**预期日志**（关键部分）:
```
🚀 zkInfo application starting...
✅ AiMaintainerService initialized successfully
📦 Registered MCP service: xxx to Nacos
✅ Successfully registered instance
```

**验证点**:
- [ ] 应用成功启动
- [ ] 日志显示 AiMaintainerService 初始化成功（或降级）
- [ ] 日志显示服务注册成功

#### 5.3 检查 Nacos 注册（需要 Nacos 控制台）
```bash
echo "=== 访问 Nacos 控制台验证 ==="
echo "URL: http://localhost:8848/nacos"
echo "用户名: nacos"
echo "密码: nacos"
echo ""
echo "检查点："
echo "1. 进入「服务管理」→「服务列表」"
echo "2. 查找以 'zk-mcp-' 开头的服务"
echo "3. 点击详情，检查元数据"
```

**元数据验证点**:
- [ ] protocol: "mcp-sse"
- [ ] serverName: xxx
- [ ] serverId: UUID
- [ ] version: xxx
- [ ] sseEndpoint: "/sse/xxx"
- [ ] server.md5: xxxxx（32位MD5值）
- [ ] application: xxx
- [ ] tools.count: 数字

---

### 第六步：验证降级机制（可选）

#### 6.1 模拟 AiMaintainerService 失败
```bash
echo "=== 测试降级机制 ==="
# 方法1: 使用错误的 Nacos 地址
export NACOS_SERVER_ADDR=invalid-host:8848
mvn spring-boot:run

# 方法2: 暂时关闭 Nacos Server
# 然后启动 zkInfo
```

**预期日志**:
```
❌ Failed to initialize AiMaintainerService
⚠️ Falling back to ConfigService
✅ Successfully registered using ConfigService
```

**验证点**:
- [ ] AiMaintainerService 初始化失败时不阻塞启动
- [ ] 自动降级到 ConfigService
- [ ] 服务仍然能正常注册

---

### 第七步：检查代码审查建议

#### 7.1 运行代码审查工作流（使用 AI Agent）
```bash
# 如果有 AI Agent 可用
/review zkInfo/src/main/java/com/pajk/mcpmetainfo/core/service/NacosMcpRegistrationService.java
```

#### 7.2 手动代码审查重点
```bash
echo "=== 手动审查重点 ==="
echo "1. 检查是否使用了 Lombok 注解"
grep -E "@Data|@Slf4j|@Builder" src/main/java/com/pajk/mcpmetainfo/core/service/*.java

echo ""
echo "2. 检查日志是否使用表情符号"
grep -E "log\.(info|error|warn).*[✅❌⚠️🚀📦]" src/main/java/com/pajk/mcpmetainfo/core/service/*.java | head -5

echo ""
echo "3. 检查异常处理"
grep -A 3 "catch (Exception" src/main/java/com/pajk/mcpmetainfo/core/service/*.java | head -10
```

**验证点**:
- [ ] 使用了 @Slf4j 日志注解
- [ ] 日志使用表情符号增强可读性
- [ ] 有完整的异常处理和日志记录

---

## 📊 验证检查表

### 基础验证
- [ ] 依赖版本正确（Nacos Client 3.0.1）
- [ ] 配置文件完整
- [ ] 编译成功
- [ ] 单元测试通过（2/2）

### 代码逻辑验证
- [ ] AiMaintainerService 初始化逻辑正确
- [ ] 双路径注册策略实现
- [ ] MD5 本地计算（不网络读取）
- [ ] 虚拟节点自动发现和创建

### 代码质量验证
- [ ] 使用 Lombok 注解
- [ ] 日志使用表情符号
- [ ] 完整的异常处理
- [ ] 详细的注释

### 功能验证（需要环境）
- [ ] 应用能正常启动
- [ ] 服务注册到 Nacos
- [ ] 元数据包含所有必需字段
- [ ] 降级机制正常工作

---

## 🐛 常见问题排查

### Q1: 编译失败
**症状**: mvn compile 失败
**排查**:
```bash
# 检查 Java 版本
java -version  # 应该是 17+

# 清理并重新编译
mvn clean compile -U
```

### Q2: 单元测试失败
**症状**: 测试运行失败
**排查**:
```bash
# 查看完整的测试输出
mvn test -Dtest=DubboToMcpAutoRegistrationServiceTest -X

# 检查是否有 Mock 配置问题
```

### Q3: 找不到 AiMaintainerService 类
**症状**: 编译时找不到 AiMaintainerService
**排查**:
```bash
# 检查依赖是否正确下载
ls -lh ~/.m2/repository/com/alibaba/nacos/nacos-maintainer-client/3.0.1/

# 强制更新依赖
mvn clean install -U
```

### Q4: 应用启动失败
**症状**: Spring Boot 应用启动异常
**排查**:
```bash
# 查看详细日志
mvn spring-boot:run -X

# 检查端口是否被占用
lsof -i:9091

# 检查 Nacos 连接
telnet localhost 8848
```

---

## 📝 验证报告模板

完成验证后，请填写以下报告：

```markdown
# zkInfo 手工验证报告

## 验证环境
- Java 版本: ___
- Maven 版本: ___
- Nacos Server: ___ (版本 ___)
- MySQL: ___ (版本 ___)

## 验证结果

### 基础验证
- [ ] 编译: ✅ / ❌ (耗时: ___)
- [ ] 单元测试: ✅ / ❌ (通过: __/2)

### 代码验证
- [ ] AiMaintainerService 初始化: ✅ / ❌
- [ ] 双路径注册: ✅ / ❌
- [ ] MD5 本地计算: ✅ / ❌
- [ ] 虚拟节点: ✅ / ❌

### 功能验证（如已启动）
- [ ] 应用启动: ✅ / ❌
- [ ] Nacos 注册: ✅ / ❌
- [ ] 元数据正确: ✅ / ❌

## 发现的问题
1. 
2. 
3. 

## 建议改进
1. 
2. 
3. 

## 总体评价
- 验证结果: ✅ 通过 / ❌ 不通过 / ⚠️ 部分通过
- 建议: 

验证人: ___
验证时间: ___
```

---

## 🎯 下一步

验证通过后：
1. 📖 阅读 [VALIDATION_GUIDE.md](./VALIDATION_GUIDE.md) 了解集成测试
2. 🧪 运行 `./integration_test.sh` 进行自动化集成测试
3. 🚀 部署到测试环境进行端到端测试

---

**手册版本**: v1.0  
**最后更新**: 2026-02-09  
**适用项目**: zk-mcp-parent/zkInfo
