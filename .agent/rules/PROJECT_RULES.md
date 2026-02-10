# zk-mcp-parent 项目开发规范与约定 (Project Rules)

## 0. 交互语言 (Language Preference)
- **强制要求**: 所有回复、注释、文档说明必须使用**中文**。

## 1. 核心技术栈 (Technology Stack)
- **Java 版本**: 17
- **基础框架**: Spring Boot 3.4.0
- **微服务架构**: 
  - **Dubbo**: 3.x (服务发现和泛化调用)
  - **注册中心**: Nacos 3.x (主), ZooKeeper (辅助监听)
- **持久层**: MyBatis (XML Mapper) + MySQL
- **协议转换**: Dubbo Generic Invocation → MCP Tools

## 2. 项目定位与职责 (Project Purpose)

### 2.1 核心定位
**zk-mcp-parent** 是 **Dubbo 生态与 MCP 生态的桥接器（Adapter）**，负责将 Dubbo 服务自动转换为 MCP 工具，供 AI 应用调用。

### 2.2 zkInfo 模块职责
zkInfo 是核心模块，承担以下关键职责：

#### 2.2.1 服务发现与监听
- **ZooKeeper 监听**: `zkWatcherSchedulerService` 实时监听 ZooKeeper 中的 Dubbo 服务变化
- **自动发现**: 发现新注册的 Dubbo 服务提供者
- **变更检测**: 监听服务上下线、节点变化

#### 2.2.2 MCP 服务注册
- **自动注册**: `DubboToMcpAutoRegistrationService` 将 Dubbo 接口自动注册为 MCP 服务
- **Nacos 集成**: 
  - 标准路径: 使用 `AiMaintainerService` (Nacos 3.x AI 标准)
  - 降级路径: 使用 `ConfigService` (传统配置中心方式)
- **元数据管理**: 上报完整的服务元数据到 Nacos
- **虚拟节点**: 为集群中的所有 zkInfo 节点创建虚拟实例

#### 2.2.3 协议转换
- **MCP to Dubbo**: `McpExecutorService` 将 MCP JSON-RPC 调用转换为 Dubbo 泛化调用
- **参数映射**: JSON参数 → Dubbo 方法参数
- **结果转换**: Dubbo 返回值 → JSON 响应

#### 2.2.4 元数据持久化
- **数据库存储**: 持久化 Dubbo 接口、方法、参数等元数据
- **Schema 生成**: 从持久化数据生成 MCP InputSchema
- **版本管理**: 跟踪接口版本变化

### 2.3 demo-provider 模块
- **定位**: 测试用例和示例服务
- **职责**: 提供简单的 Dubbo 接口用于验证 zkInfo 的发现和注册逻辑

### 2.4 mcp-ai-client 模块
- **定位**: AI 应用客户端示例
- **职责**: 演示如何通过 MCP 协议调用转换后的 Dubbo 服务

## 3. 编码标准 (Coding Standards)

### 3.1 通用规范
- **Lombok**: 强制使用 `@Data`, `@Slf4j`, `@Builder`, `@RequiredArgsConstructor`
- **日志规范**:
  - 统一使用 `log.info/warn/error/debug`
  - 异常捕获必须记录完整堆栈：`log.error("错误信息...", e)`
  - 关键流程使用表情符号增强可读性：
    - ✅ `log.info("✅ 成功...")`
    - ❌ `log.error("❌ 失败...")`
    - ⚠️ `log.warn("⚠️ 警告...")`
    - 🚀 `log.info("🚀 启动...")`
    - 📦 `log.info("📦 注册...")`

### 3.2 架构分层

#### Controller 层
- 统一返回 `ResponseEntity<T>` 或 MCP 标准响应格式
- **必须**包含全局异常处理逻辑（Try-Catch）
- SSE 端点使用 `SseEmitter` 或 `Flux<ServerSentEvent>`

#### Service 层
- **Nacos 服务**: 
  - `NacosMcpRegistrationService`: 负责服务注册到 Nacos
  - 必须实现优雅降级（AiMaintainerService → ConfigService）
  - 注意异步处理和超时设置
  
- **MCP 服务**:
  - `McpExecutorService`: MCP 调用执行器
  - `McpConverterService`: 协议转换服务
  - 注意 JSON 序列化性能和错误处理
  
- **ZK 服务**:
  - `zkWatcherSchedulerService`: ZooKeeper 监听服务
  - 使用定时任务 `@Scheduled` 进行轮询
  - 注意连接异常处理

#### Repository 层
- **Mapper**: MyBatis 接口，必须与 XML 映射文件对应
- **Entity**: 对应数据库表，使用 `@Data` 注解
- **严禁**直接通过 Controller 返回 Entity

### 3.3 数据模型

#### DTO (Data Transfer Object)
- 使用 `@JsonProperty` 确保与 MCP 协议兼容
- 使用 `@Schema` 提供 OpenAPI 文档支持
- 示例：
```java
@Data
public class McpToolRequest {
    @JsonProperty("name")
    @Schema(description = "工具名称")
    private String name;
    
    @JsonProperty("arguments")
    @Schema(description = "工具参数")
    private Map<String, Object> arguments;
}
```

#### Entity (Database Entity)
- 对应数据库表结构
- 使用 `@Table`, `@Column` 注解
- 严禁直接暴露给外部接口

#### Domain Model
- 业务领域对象，如 `ProviderInfo`, `McpTool`
- 用于内部业务逻辑处理

### 3.4 Nacos 集成规范

#### 服务注册
```java
// 优先使用 AiMaintainerService（推荐）
if (aiMaintainerService != null) {
    result = aiMaintainerService.createMcpServer(...);
}

// 降级使用 ConfigService
if (!result) {
    configService.publishConfig(...);
}
```

#### 元数据要求
必须包含以下字段：
- `protocol`: 固定为 "mcp-sse"
- `serverName`: MCP 服务名称
- `serverId`: UUID 唯一标识
- `version`: 服务版本
- `sseEndpoint`: SSE 连接端点（格式：`/sse/{name}`）
- `sseMessageEndpoint`: 消息端点
- `server.md5`: 配置内容 MD5（用于检测变更）
- `application`: 应用分组名称
- `tools.count`: 工具数量

#### MD5 计算规范
- **必须本地计算**: 使用发布配置时返回的内容计算 MD5
- **严禁网络读取**: 不要发布后再从 Nacos 读取内容计算 MD5
- **目的**: 消除 Nacos 最终一致性导致的不准确问题

## 4. 目录结构规范

```
zkInfo/
├── src/main/java/com/pajk/mcpmetainfo/
│   ├── core/                          # 核心业务逻辑
│   │   ├── config/                    # 配置类
│   │   │   ├── ZookeeperConfig.java
│   │   │   └── NacosConfig.java
│   │   ├── controller/                # MCP 端点
│   │   │   └── McpSseController.java
│   │   ├── service/                   # 核心服务
│   │   │   ├── NacosMcpRegistrationService.java
│   │   │   ├── McpExecutorService.java
│   │   │   ├── zkWatcherSchedulerService.java
│   │   │   ├── DubboToMcpAutoRegistrationService.java
│   │   │   └── ZkInfoNodeDiscoveryService.java
│   │   ├── model/                     # 数据模型
│   │   │   ├── ProviderInfo.java
│   │   │   └── McpRequest.java
│   │   └── util/                      # 工具类
│   │       ├── McpToolSchemaGenerator.java
│   │       └── ParameterConverter.java
│   └── persistence/                   # 持久层
│       ├── entity/                    # 数据库实体
│       │   ├── DubboServiceEntity.java
│       │   ├── DubboServiceMethodEntity.java
│       │   └── DubboMethodParameterEntity.java
│       └── mapper/                    # MyBatis Mapper
│           └── *.java
└── src/main/resources/
    ├── mapper/                        # MyBatis XML
    │   └── *.xml
    └── application.yml                # 配置文件
```

## 5. 关键设计模式

### 5.1 适配器模式 (Adapter Pattern)
- **场景**: Dubbo 泛化调用 ↔ MCP 协议转换
- **实现**: `McpExecutorService` 作为适配器

### 5.2 策略模式 (Strategy Pattern)
- **场景**: 不同的 Nacos 注册策略（AiMaintainerService vs ConfigService）
- **实现**: `NacosMcpRegistrationService` 根据条件选择策略

### 5.3 观察者模式 (Observer Pattern)
- **场景**: ZooKeeper 节点变化监听
- **实现**: `zkWatcherSchedulerService` 定期检查并触发注册

### 5.4 工厂模式 (Factory Pattern)
- **场景**: 创建 MCP Tool Schema
- **实现**: `McpToolSchemaGenerator`

## 6. 测试规范

### 6.1 单元测试
- **覆盖率要求**: 核心服务类覆盖率 > 70%
- **测试框架**: JUnit 5 + Mockito
- **Mock 规范**: 
  - Mock 外部依赖（Nacos, ZooKeeper, Database）
  - 不 Mock 被测试的核心逻辑

### 6.2 集成测试
- **环境要求**: 
  - Nacos Server 3.x
  - MySQL
  - ZooKeeper (可选)
- **测试场景**:
  - 服务发现和注册流程
  - MCP 工具调用流程
  - 虚拟节点创建流程

## 7. 性能要求

### 7.1 响应时间
- MCP 工具调用: < 500ms (P95)
- 服务注册: < 2s
- ZooKeeper 监听周期: 30s (可配置)

### 7.2 并发
- 支持 100+ 并发 MCP 工具调用
- 支持 1000+ Dubbo 服务注册

### 7.3 元数据限制
- Nacos 元数据: < 1024 字节
- 超过限制时按优先级移除可选字段

## 8. 安全规范

### 8.1 输入验证
- 所有外部输入必须验证
- 使用 `@Valid` 和 `@Validated` 进行参数校验

### 8.2 错误处理
- 不暴露敏感信息（如内部路径、数据库结构）
- 统一错误码和错误消息格式

### 8.3 日志安全
- 不记录敏感数据（密码、Token）
- 使用占位符而非字符串拼接

## 9. 配置管理

### 9.1 配置层级
- **默认配置**: `application.yml`
- **环境配置**: `application-{profile}.yml`
- **Nacos 配置**: 动态配置（可选）

### 9.2 必需配置项
```yaml
spring:
  cloud:
    nacos:
      server-addr: localhost:8848
      discovery:
        namespace: public
        group: mcp-server

dubbo:
  registry:
    address: zookeeper://localhost:2181

server:
  port: 9091
```

## 10. 文档规范

### 10.1 代码注释
- **类注释**: Javadoc 格式，说明类的职责和用途
- **方法注释**: 说明参数、返回值、异常
- **关键逻辑**: 行内注释说明复杂逻辑

### 10.2 README
- 每个模块必须有 README.md
- 包含：功能介绍、快速开始、配置说明、API 文档

### 10.3 变更文档
- 重大变更必须更新 CHANGELOG.md
- 提供迁移指南

## 11. Git 提交规范

### 11.1 提交消息格式
```
<type>(<scope>): <subject>

<body>

<footer>
```

### 11.2 Type 类型
- `feat`: 新功能
- `fix`: Bug 修复
- `refactor`: 重构
- `docs`: 文档更新
- `test`: 测试相关
- `chore`: 构建/工具配置

### 11.3 示例
```
feat(nacos): 集成 AiMaintainerService 实现标准化注册

- 升级 nacos-client 到 3.0.1
- 新增 AiMaintainerService 集成
- 实现优雅降级机制

Closes #123
```

## 12. 依赖管理

### 12.1 核心依赖版本
- Spring Boot: 3.4.0
- Nacos Client: 3.0.1
- Dubbo: 3.x
- MyBatis: 最新稳定版

### 12.2 依赖原则
- 优先使用 Spring Boot Starter
- 避免版本冲突
- 定期更新安全补丁

---

**规范版本**: 1.0  
**最后更新**: 2026-02-09  
**维护者**: zkInfo 开发团队
