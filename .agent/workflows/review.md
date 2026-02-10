---
description: 代码审查工作流
---
# 代码审查工作流 (Code Review Workflow)

本工作流将基于 `PROJECT_RULES.md` 中的规范，对当前打开的文件或指定文件进行全面的代码审查。

## 执行步骤

1. **上下文分析**:
   - 读取 `.agent/rules/PROJECT_RULES.md`，理解 zk-mcp-parent 项目的编码规范
   - 读取目标代码文件，理解当前的实现逻辑
   - 识别文件在项目中的角色（Service、Controller、Mapper等）

2. **合规性检查**:
   - **代码风格**: 
     - 是否使用了 Lombok 注解（@Data, @Slf4j, @Builder等）？
     - 日志是否使用表情符号增强可读性？
     - 是否有完整的 Javadoc 注释？
   
   - **架构规范**:
     - 分层是否正确（Controller、Service、Repository）？
     - Service 层是否实现了降级机制（如 Nacos 注册）？
     - 是否遵循适配器模式（Dubbo ↔ MCP 转换）？
   
   - **错误处理**:
     - Controller 层是否有完整的 try-catch 异常捕获？
     - 异常日志是否记录了完整堆栈？
     - 是否避免暴露敏感信息？
   
   - **Nacos 集成规范**:
     - MD5 是否本地计算（而非网络读取）？
     - 元数据是否包含所有必需字段？
     - 元数据大小是否 < 1024 字节？
   
   - **性能考虑**:
     - 是否注意了异步处理和超时设置？
     - JSON 序列化是否优化？
     - 数据库查询是否高效？

3. **问题识别**:
   - 列出所有违反"项目规范"的地方
   - 识别潜在的 Bug 或逻辑错误：
     - 空指针风险
     - 资源未关闭（如数据库连接、ZK 连接）
     - 并发安全问题
     - MD5 计算不准确问题
     - 元数据格式不匹配问题

4. **提供反馈**:
   - 输出一份 Markdown 格式的审查报告
   - 将问题按严重程度分类：
     - **严重 (Critical)**: 潜在 Bug，会导致功能故障，必须修复
     - **主要 (Major)**: 违反核心架构或规范，强烈建议修复
     - **次要 (Minor)**: 风格或文档问题，建议优化
   - 针对每个问题，必须提供具体的**代码修复示例**

## 审查报告模板

```markdown
# 代码审查报告

## 文件信息
- **文件路径**: {path}
- **文件类型**: {Controller/Service/Mapper/Entity}
- **代码行数**: {lines}
- **审查时间**: {timestamp}

## 总体评分
- **代码质量**: {A/B/C/D}
- **规范符合度**: {%}
- **建议修复**: {Critical: X, Major: Y, Minor: Z}

## 问题清单

### 🔴 Critical 问题

#### 问题 1: {标题}
- **位置**: 第 X 行
- **问题描述**: {详细描述}
- **风险**: {可能导致的后果}
- **修复建议**: 
```java
// 修复前
{原代码}

// 修复后
{修复代码}
```

### 🟡 Major 问题

#### 问题 2: {标题}
...

### 🟢 Minor 问题

#### 问题 3: {标题}
...

## 优点总结
- {列举代码中做得好的地方}

## 改进建议
1. {总体改进建议}
2. ...

## 参考资料
- [项目规范](../.agent/rules/PROJECT_RULES.md)
- [zkInfo 优化文档](../zkInfo/README_OPTIMIZATION.md)
```

## 特别关注点（zk-mcp-parent 特有）

### 1. Nacos 注册逻辑审查
- 检查是否优先使用 AiMaintainerService
- 检查是否有 ConfigService 降级
- 检查 MD5 是否本地计算
- 检查元数据字段完整性

### 2. MCP 协议转换审查
- 检查 JSON 参数到 Dubbo 参数的映射逻辑
- 检查错误处理是否符合 MCP 规范
- 检查 InputSchema 生成是否正确

### 3. ZooKeeper 监听审查
- 检查连接异常处理
- 检查节点监听回调逻辑
- 检查定时任务配置是否合理

### 4. 虚拟节点创建审查
- 检查节点发现逻辑
- 检查多节点注册错误处理
- 检查降级机制（至少注册当前节点）

## 使用示例

```bash
# 审查当前打开的文件
/review

# 审查指定文件
/review zkInfo/src/main/java/com/pajk/mcpmetainfo/core/service/NacosMcpRegistrationService.java

# 审查整个 service 目录
/review zkInfo/src/main/java/com/pajk/mcpmetainfo/core/service/
```

---

**工作流版本**: 1.0  
**最后更新**: 2026-02-09  
**适用项目**: zk-mcp-parent
