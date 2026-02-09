# zkInfo Nacos Registration Refactoring - Quick Reference

## 验证清单

### ✅ 1. 编译验证
```bash
cd zkInfo
mvn clean compile -DskipTests
# 预期结果: BUILD SUCCESS
```

### ✅ 2. 核心单元测试
```bash
mvn test -Dtest=DubboToMcpAutoRegistrationServiceTest
# 预期结果: Tests run: 2, Failures: 0, Errors: 0
```

### ⏳ 3. 集成测试（需要环境配置）

#### 环境要求
- Nacos Server 3.1.1 运行在 localhost:8848
- MySQL 数据库已配置
- ZooKeeper 运行（如果需要）

#### 启动应用
```bash
# 确保 application.yml 中配置了正确的 Nacos 连接信息
mvn spring-boot:run
```

#### 验证点
1. **日志检查** - 查找 AiMaintainerService 初始化日志
   ```
   ✅ Successfully initialized AiMaintainerService with Nacos server: ...
   或
   ⚠️ Failed to initialize AiMaintainerService, will use ConfigService fallback
   ```

2. **注册日志** - 查找服务注册日志
   ```
   ✅ Successfully registered MCP service: xxx to Nacos (via AiMaintainerService)
   或
   ✅ Successfully registered MCP service: xxx to Nacos (via ConfigService)
   ```

3. **Nacos 控制台验证**
   - 打开 http://localhost:8848/nacos
   - 进入"服务管理" → "服务列表"
   - 查找注册的 MCP 服务（格式：mcp-{interfaceName}-{version}）
   - 检查服务元数据是否包含：
     - `protocol`: mcp-sse
     - `serverName`: ...
     - `serverId`: ...
     - `version`: ...
     - `sseEndpoint`: ...
     - `sseMessageEndpoint`: ...

### ⏳ 4. 与 mcp-router-v3 集成测试

#### 验证 mcp-router-v3 能否发现服务
```bash
# 在 mcp-router-v3 中查看日志
# 应该能看到从 Nacos 发现的 MCP 服务
```

#### 调用 MCP 工具
```bash
# 通过 mcp-router-v3 调用注册的工具
curl -X POST http://localhost:8080/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "name": "工具名称",
    "arguments": {...}
  }'
```

## 降级逻辑验证

### 测试 AiMaintainerService 降级

1. **模拟初始化失败**
   - 方式1: 临时注释掉 Nacos 配置
   - 方式2: 修改 Nacos server-addr 为无效地址

2. **预期行为**
   ```
   [WARN] Failed to initialize AiMaintainerService: ...
   [INFO] Using ConfigService fallback for MCP registration
   [INFO] ✅ Successfully registered MCP service: xxx to Nacos (via ConfigService)
   ```

3. **验证服务仍然正常注册**
   - 检查 Nacos 配置中心是否有以下配置：
     - `{serviceId}-{version}-mcp-server.json`
     - `{serviceId}-{version}-mcp-tools.json`
     - `{serviceId}-{version}-mcp-versions.json`

## 关键代码路径

### 注册流程
```
DubboToMcpAutoRegistrationService.checkAndUpdateService()
  ↓
NacosMcpRegistrationService.registerDubboServiceAsMcp()
  ↓
  ├─→ publishMcpServerToNacosUsingMaintainerService() [优先]
  │     ↓
  │     AiMaintainerService.createMcpServer()
  │
  └─→ publishConfigsToNacos() [降级]
        ↓
        ConfigService.publishConfig()
  ↓
registerInstanceToNacos()
  ↓
NamingService.registerInstance()
```

### 工具转换
```
generateMcpTools() → List<Map<String, Object>>
  ↓
createMcpToolList() → List<McpTool>
  ↓
McpToolSpecification.setTools()
```

## 配置说明

### application.yml 必需配置
```yaml
spring:
  cloud:
    nacos:
      server-addr: localhost:8848
      username: nacos
      password: nacos
      config:
        namespace: public
      discovery:
        namespace: public
        group: DEFAULT_GROUP
```

### 可选配置
```yaml
nacos:
  registry:
    enabled: true  # 是否启用 Nacos 注册
  v3:
    api:
      enabled: true  # 是否使用 Nacos v3 API
```

## 故障排查

### 问题1: AiMaintainerService 初始化失败
**症状**: 日志显示 "Failed to initialize AiMaintainerService"
**排查**:
1. 检查 Nacos Server 是否运行
2. 检查 server-addr 配置是否正确
3. 检查用户名/密码是否正确
4. 检查网络连接

**解决**: 修复配置后重启应用，或依赖降级机制使用 ConfigService

### 问题2: 类型转换错误
**症状**: `ClassCastException: Object cannot be cast to Map`
**排查**: 检查 Nacos Client 版本是否为 3.0.1
**解决**: 确保 pom.xml 中 nacos-client 和 nacos-maintainer-client 都是 3.0.1

### 问题3: 服务注册到 Nacos 但 mcp-router 无法发现
**症状**: Nacos 中有服务，但 mcp-router-v3 找不到
**排查**:
1. 检查服务元数据是否完整（serverName, serverId, sseEndpoint 等）
2. 检查 mcp-router-v3 的 Nacos 配置（namespace, group）
3. 检查服务名称格式是否正确

**解决**: 
- 查看 registerInstanceToNacos() 方法的 metadata 构建逻辑
- 确保与 mcp-router-v3 期望的格式一致

## 性能考虑

### AiMaintainerService vs ConfigService

| 特性 | AiMaintainerService | ConfigService |
|------|-------------------|---------------|
| 标准化 | ✅ 遵循 Nacos AI 标准 | ❌ 自定义实现 |
| 元数据管理 | ✅ 统一管理 | ❌ 分散在多个配置 |
| 工具发现 | ✅ 原生支持 | ⚠️ 需要额外解析 |
| 性能 | 🔸 单次调用 | 🔸 多次配置发布 |
| 兼容性 | ✅ Nacos 3.x | ✅ Nacos 2.x/3.x |

### 建议
- 生产环境推荐使用 Nacos Server 3.1.1+ 以获得最佳体验
- 如果使用较旧的 Nacos Server，降级机制会自动启用

## 相关文档
- [REFACTORING_VALIDATION_REPORT.md](REFACTORING_VALIDATION_REPORT.md) - 详细验证报告
- [COMMIT_MESSAGE.md](COMMIT_MESSAGE.md) - 提交信息
- [spring-ai-alibaba Nacos MCP 文档](https://github.com/alibaba/spring-ai-alibaba)
