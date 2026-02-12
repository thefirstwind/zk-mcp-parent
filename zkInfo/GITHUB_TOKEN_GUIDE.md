# GitHub 访问令牌 (Personal Access Token) 获取指南

为了让虚拟项目向导能够访问您的私有仓库或提高 API 调用限额，您需要提供一个 GitHub 访问令牌。推荐使用 **Fine-grained tokens**，因为它允许您仅授权访问特定的仓库。

## 🚀 快速步骤

### 1. 进入生成页面
点击链接直接跳转：[GitHub Token 生成页面](https://github.com/settings/tokens?type=beta)
*(或者：Settings -> Developer settings -> Personal access tokens -> Fine-grained tokens -> Generate new token)*

### 2. 配置令牌信息
*   **Token name**: 输入名称（如 `MCP-Wizard`）。
*   **Expiration**: 选择过期时间（如 `30 days`）。
*   **Resource owner**: 保持默认（通常是您的用户名）。

### 3. 设置仓库访问范围 (关键 安全设置)
在 **Repository access** 区域：
*   ✅ 选择 **Only select repositories**。
*   🔽点击下拉菜单，**勾选您需要导入的具体项目**。

### 4. 设置权限 (Permissions)
点击 **Repository permissions** 展开权限列表，设置以下权限：

| 权限项 (Permissions) | 级别 (Access) | 说明 |
| :--- | :--- | :--- |
| **Contents** | **Read-only** | **必须**。允许读取代码、提交记录和元数据。 |
| **Metadata** | **Read-only** | **必须**。读取仓库基础信息（通常随 Contents 自动开启）。 |

*(其他权限保持 `No access` 即可，除非您有特殊需求)*

### 5. 生成并复制
*   点击页面底部的 **Generate token**。
*   📋 **复制** 生成的令牌（格式通常为 `github_pat_xxxx`）。
*   ⚠️ **注意**：请立即保存，刷新页面后将无法再次查看！

## ❓ 常见问题

**Q: 我可以用 Classic Token 吗？**
A: 可以。在 Personal access tokens 菜单下选择 **Tokens (classic)**。但是 Classic Token 默认权限较大（通常是所有仓库），不如 Fine-grained tokens 安全。如果使用 Classic Token，只需勾选 `repo` 权限即可。

**Q: 只有公开项目需要 Token 吗？**
A: 不是必须的，公开项目可以直接克隆。但使用 Token 可以：
1. 避免 GitHub API 的速率限制（Rate Limiting）。
2. 访问您账号下的**私有**项目。
