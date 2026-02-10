# zkInfo 验证工具箱使用说明

本文档汇集了 zkInfo 项目的所有验证脚本和指南，帮助您快速进行手动和自动化验证。

## 📂 工具清单

| 文件名 | 类型 | 用途 |
|--------|------|------|
| `verify_nacos_lifecycle.sh` | **Curl 脚本** | **推荐**。无依赖全生命周期验证（健康检查、元数据、配置、清理）。 |
| `interactive_validation.sh` | 交互脚本 | 问答式引导验证，适合首次验证或演示。 |
| `integration_test.sh` | 测试脚本 | 基于 Maven 的自动化集成测试（需要完整环境）。 |
| `MANUAL_VALIDATION_CHECKLIST.md` | 检查清单 | 详细的手工验证打钩表。 |
| `MANUAL_VALIDATION_GUIDE.md` | 操作手册 | 详尽的分步验证操作指南。 |

---

## 🚀 快速使用指南

### 1. 轻量级验证 (推荐)
无需 Maven，仅依赖 `curl`，适合在服务器或容器中快速检查。

```bash
# 赋予执行权限
chmod +x verify_nacos_lifecycle.sh

# 1. 基础检查 (默认检查 com.example.DemoService:1.0.0)
./verify_nacos_lifecycle.sh

# 2. 指定服务和 Nacos 地址
./verify_nacos_lifecycle.sh -h 192.168.1.100:8848 -s "com.pajk.McpService:1.0.0"

# 3. 监控模式 (每 5 秒刷新一次状态)
./verify_nacos_lifecycle.sh -m

# 4. 清理模式 (下线服务用于测试注销逻辑)
./verify_nacos_lifecycle.sh -c
```

### 2. 交互式引导验证
适合不熟悉验证流程的开发人员。

```bash
chmod +x interactive_validation.sh
./interactive_validation.sh
```

### 3. 深度集成测试
需要 Maven 环境，运行完整的 JUnit 测试逻辑。

```bash
chmod +x integration_test.sh
./integration_test.sh
```

---

## 📊 验证场景对照表

| 场景 | 推荐工具 | 关注点 |
|------|----------|--------|
| **部署后冒烟测试** | `verify_nacos_lifecycle.sh` | 服务是否存在，端口是否通 |
| **元数据准确性检查** | `verify_nacos_lifecycle.sh` | mcp-sse 协议, md5, endpoints |
| **开发阶段自测** | `interactive_validation.sh` | 环境检查, 启动参数 |
| **CI/CD 流水线** | `integration_test.sh` | 单元测试通过率, 编译状态 |
| **手工详细验收** | `MANUAL_VALIDATION_CHECKLIST.md` | 逐项确认, 留存记录 |

---

## 📝 常见问题 (FAQ)

**Q: 脚本提示 `curl: (7) Failed to connect`?**
A: 检查 Nacos 地址是否正确，或网络防火墙设置。使用 `-h` 参数指定正确地址。

**Q: 元数据中没有 `server.md5`?**
A: 检查 zkInfo 日志，确认是否降级到了不计算 MD5 的模式，或者服务注册逻辑尚未触发 Config 发布。

**Q: 找不到配置文件?**
A: 脚本默认进行模糊匹配。如果使用了特殊的 naming 策略，请手动检查 Nacos 控制台的 `Config Management`。
