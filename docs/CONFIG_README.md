# zk-mcp-parent 配置文件说明

本项目包含了标准化的开发规范、工作流和 GitHub 配置，帮助团队高效协作。

## 📂 目录结构

```
zk-mcp-parent/
├── .agent/                          # AI Agent 配置
│   ├── rules/                       # 项目规范
│   │   └── PROJECT_RULES.md         # 项目开发规范（必读）
│   └── workflows/                   # 标准化工作流
│       ├── review.md                # 代码审查工作流
│       └── add-dubbo-provider.md    # 添加 Dubbo 服务工作流
│
├── .github/                         # GitHub 配置
│   ├── ISSUE_TEMPLATE/              # Issue 模板
│   │   ├── bug_report.md            # Bug 报告模板
│   │   └── feature_request.md       # 功能请求模板
│   ├── workflows/                   # GitHub Actions
│   │   └── maven-build.yml          # 自动化构建和测试
│   └── PULL_REQUEST_TEMPLATE.md     # PR 模板
│
└── zkInfo/                          # 核心模块
    └── docs/                        # 项目文档
```

## 📖 快速开始

### 1. 阅读项目规范
**必读**: [.agent/rules/PROJECT_RULES.md](.agent/rules/PROJECT_RULES.md)

这份文档定义了：
- 技术栈和依赖版本
- 项目架构和职责划分
- 编码标准和最佳实践
- 目录结构规范
- Nacos 集成规范
- 测试和性能要求

### 2. 使用工作流

#### 代码审查
```bash
# 触发代码审查工作流
/review <文件路径>
```
参考：[.agent/workflows/review.md](.agent/workflows/review.md)

#### 添加 Dubbo 服务
参考：[.agent/workflows/add-dubbo-provider.md](.agent/workflows/add-dubbo-provider.md)

完整的步骤指导，从需求分析到上线部署。

### 3. 提交 Issue 或 PR

#### Bug Report
使用模板：[.github/ISSUE_TEMPLATE/bug_report.md](.github/ISSUE_TEMPLATE/bug_report.md)

#### Feature Request  
使用模板：[.github/ISSUE_TEMPLATE/feature_request.md](.github/ISSUE_TEMPLATE/feature_request.md)

#### Pull Request
使用模板：[.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md)

## 🎯 核心规范要点

### 编码规范
- ✅ 使用 Lombok：`@Data`, `@Slf4j`, `@Builder`
- ✅ 日志使用表情符号：`log.info("✅ 成功...")`
- ✅ 完整的异常处理和日志记录
- ✅ 详细的 Javadoc 注释

### Nacos 集成规范
- ✅ 优先使用 `AiMaintainerService`
- ✅ 实现 `ConfigService` 降级机制
- ✅ MD5 **必须本地计算**（不要网络读取）
- ✅ 元数据包含所有必需字段
- ✅ 元数据大小 < 1024 字节

### Git 提交规范
```
<type>(<scope>): <subject>

<body>

<footer>
```

类型（type）:
- `feat`: 新功能
- `fix`: Bug 修复
- `refactor`: 重构
- `docs`: 文档更新
- `test`: 测试相关
- `chore`: 构建/工具配置

示例：
```
feat(nacos): 集成 AiMaintainerService 实现标准化注册

- 升级 nacos-client 到 3.0.1
- 新增 AiMaintainerService 集成
- 实现优雅降级机制

Closes #123
```

## 🔧 GitHub Actions

### 自动化构建
每次 Push 或 PR 到 main/develop 分支时自动触发：

1. **编译验证**: 编译 zkInfo 和 demo-provider
2. **单元测试**: 运行核心单元测试
3. **集成测试**: 使用 MySQL + Nacos 容器运行集成测试
4. **生成报告**: 发布测试结果报告

手动触发：
- 访问 GitHub Actions 页面
- 选择 "Maven Build and Test"
- 点击 "Run workflow"

## 📚 相关文档

### 项目文档
- [zkInfo 优化总结](zkInfo/README_OPTIMIZATION.md)
- [综合分析报告](zkInfo/COMPREHENSIVE_ANALYSIS_REPORT.md)
- [快速参考卡](zkInfo/QUICK_REFERENCE.md)
- [验证指南](zkInfo/VALIDATION_GUIDE.md)

### 外部参考
- [Dubbo 官方文档](https://dubbo.apache.org/)
- [Nacos 官方文档](https://nacos.io/)
- [Spring AI Alibaba](https://github.com/alibaba/spring-ai-alibaba)

## 🤝 贡献指南

1. Fork 项目
2. 创建特性分支：`git checkout -b feature/amazing-feature`
3. 提交更改：`git commit -m 'feat: add amazing feature'`
4. 推送到分支：`git push origin feature/amazing-feature`
5. 提交 Pull Request（使用 PR 模板）

## ❓ 常见问题

### Q: 如何触发代码审查？
A: 在 AI Agent 中使用 `/review` 命令，参考 [review.md](.agent/workflows/review.md)

### Q: 如何添加新的 Dubbo 服务？
A: 参考完整工作流：[add-dubbo-provider.md](.agent/workflows/add-dubbo-provider.md)

### Q: Nacos 注册失败怎么办？
A: 
1. 检查 Nacos Server 是否运行
2. 查看 zkInfo 日志中的错误信息
3. 验证配置文件中的 Nacos 地址
4. 参考 [快速参考卡](zkInfo/QUICK_REFERENCE.md) 的故障排查部分

### Q: 如何运行集成测试？
A: 
```bash
cd zkInfo
./integration_test.sh  # 自动化测试脚本
```

## 📞 获取帮助

- **技术问题**: 提交 [Bug Report](.github/ISSUE_TEMPLATE/bug_report.md)
- **功能建议**: 提交 [Feature Request](.github/ISSUE_TEMPLATE/feature_request.md)
- **文档问题**: 参考 [项目规范](.agent/rules/PROJECT_RULES.md)

---

**最后更新**: 2026-02-09  
**维护者**: zk-mcp-parent 开发团队  
**版本**: 1.0
