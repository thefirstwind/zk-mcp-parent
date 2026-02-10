# 贡献指南 (Contributing Guide)

感谢您对 `mcp-router-sse-parent` 项目的关注！

## 🌟 开发工作流

本项目采用 **GitHub Flow** 工作流，简单高效：

```
main 分支
  ↓
创建功能分支
  ↓
开发和测试
  ↓
提交 Pull Request
  ↓
代码审查
  ↓
合并到 main
  ↓
自动部署
```

## 📋 分支命名规范

创建分支时，请遵循以下命名规范：

| 类型 | 前缀 | 示例 |
|------|------|------|
| 新功能 | `feature/` | `feature/add-gemini-integration` |
| Bug 修复 | `bugfix/` | `bugfix/fix-agent-memory-leak` |
| 热修复 | `hotfix/` | `hotfix/critical-security-fix` |
| 文档 | `docs/` | `docs/update-readme` |
| 重构 | `refactor/` | `refactor/optimize-mcp-client` |
| 测试 | `test/` | `test/add-integration-tests` |
| 配置 | `chore/` | `chore/update-dependencies` |

### 示例

```bash
# 好的分支名 ✅
feature/add-weather-mcp-server
bugfix/fix-null-pointer-in-agent
docs/add-gemini-guide

# 不好的分支名 ❌
my-branch
test
updates
```

## 💻 开发流程

### 1. Fork 并 Clone 项目

```bash
# Fork 项目到您的 GitHub 账号

# Clone 到本地
git clone https://github.com/YOUR_USERNAME/mcp-router-sse-parent.git
cd mcp-router-sse-parent

# 添加上游仓库
git remote add upstream https://github.com/ORIGINAL_OWNER/mcp-router-sse-parent.git
```

### 2. 创建功能分支

```bash
# 确保 main 是最新的
git checkout main
git pull upstream main

# 创建新分支
git checkout -b feature/your-feature-name
```

### 3. 开发

使用标准化工作流：

#### 添加 MCP Server
```bash
# 参考工作流文档
cat .agent/workflows/add-mcp-server.md

# 或者让 AI 帮助
# "请按照 add-mcp-server 工作流添加天气 MCP Server"
```

#### 添加 AI Agent
```bash
# 参考工作流文档
cat .agent/workflows/add-agent-workflow.md

# 或者让 AI 帮助
# "请按照 add-agent-workflow 创建天气分析 Agent"
```

### 4. 提交代码

#### Commit Message 规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Type 类型**:
- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档更新
- `style`: 代码格式（不影响功能）
- `refactor`: 重构
- `perf`: 性能优化
- `test`: 测试相关
- `chore`: 构建/工具链

**示例**:
```bash
# 好的 commit message ✅
git commit -m "feat(mcp-server): add weather query tool"
git commit -m "fix(agent): resolve memory leak in ReactAgent"
git commit -m "docs(readme): add Gemini integration guide"

# 不好的 commit message ❌
git commit -m "update"
git commit -m "fix bug"
git commit -m "changes"
```

### 5. 推送并创建 PR

```bash
# 推送到您的 fork
git push origin feature/your-feature-name

# 在 GitHub 上创建 Pull Request
# 填写 PR 模板中的所有必要信息
```

## ✅ 代码审查清单

在提交 PR 前，请确保：

### 代码质量
- [ ] 代码遵循项目规范
- [ ] 使用 Lombok 减少样板代码
- [ ] 使用 Slf4j 记录日志
- [ ] 每个公共方法都有 Javadoc
- [ ] 没有硬编码的配置值

### 测试
- [ ] 单元测试覆盖率 > 80%
- [ ] 所有测试通过：`mvn test`
- [ ] 集成测试验证（如适用）

### 文档
- [ ] README.md 已更新（如适用）
- [ ] 模块 README 已更新（如适用）
- [ ] API 文档已更新
- [ ] 工作流文档已更新（如果改了流程）

### 构建
- [ ] Maven 构建成功：`mvn clean install`
- [ ] 无编译警告
- [ ] 依赖版本兼容

## 🔧 本地开发环境

### 必需工具
- **Java**: 17+
- **Maven**: 3.6+
- **Git**: 最新版本

### 推荐工具
- **IDE**: IntelliJ IDEA / Eclipse
- **Lombok Plugin**: 安装 IDE 插件
- **Git GUI**: GitKraken / SourceTree (可选)

### 运行项目

```bash
# 构建所有模块
mvn clean install

# 运行特定模块
cd mcp-server-v6
mvn spring-boot:run

# 运行测试
mvn test
```

## 📚 参考文档

### 工作流
- [工作流对比分析](./docs/GITHUB_WORKFLOWS_COMPARISON.md)
- [工作流总结](./docs/WORKFLOWS_SUMMARY.md)
- [添加 MCP Server](./.agent/workflows/add-mcp-server.md)
- [添加 AI Agent](./.agent/workflows/add-agent-workflow.md)
- [代码审查](./.agent/workflows/review.md)

### 技术文档
- [Spring AI Alibaba 集成](./spring-ai-alibaba/README.md)
- [Gemini 整合指南](./docs/GEMINI_INTEGRATION_GUIDE.md)
- [快速开始](./docs/QUICK_START.md)

## 🤝 代码审查流程

1. **自我审查**: 提交前先自己审查一遍代码
2. **CI 检查**: 确保 GitHub Actions 通过
3. **Peer Review**: 至少一位团队成员审查
4. **修改**: 根据反馈修改代码
5. **再次审查**: 确认修改符合要求
6. **合并**: Maintainer 合并到 main

### 审查关注点
- 代码逻辑正确性
- 性能问题
- 安全问题
- 可读性和可维护性
- 测试覆盖度

## 💬 沟通渠道

- **Issues**: 报告 Bug 或提出功能建议
- **Pull Requests**: 提交代码改动
- **Discussions**: 技术讨论和问答

## 🙏 感谢

感谢所有贡献者！

您的贡献让这个项目变得更好。

---

## 常见问题

### Q: 我能直接提交到 main 吗？
**A**: 不能。所有改动都必须通过 Pull Request。

### Q: PR 多久会被审查？
**A**: 通常在 24-48 小时内。

### Q: 如何更新我的 fork？
**A**: 
```bash
git checkout main
git pull upstream main
git push origin main
```

### Q: Commit 写错了怎么办？
**A**:
```bash
# 修改最后一次 commit
git commit --amend

# 交互式 rebase 修改历史
git rebase -i HEAD~3
```

### Q: 如何运行单个模块的测试？
**A**:
```bash
cd mcp-server-v6
mvn test
```

---

**Happy Coding! 🚀**
