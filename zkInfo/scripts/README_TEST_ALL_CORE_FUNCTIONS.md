# zkInfo 核心功能完整测试脚本使用说明

## 📋 概述

`test-all-core-functions.sh` 是一个全面的测试脚本，用于验证 zkInfo 项目的所有核心功能。该脚本设计为**必须100%通过**，确保所有核心功能正常工作。

## 🎯 测试覆盖范围

### 1. 环境检查 ✅
- zkInfo 服务健康状态
- Nacos 服务连接
- ZooKeeper 连接
- 数据库连接

### 2. 服务发现与同步 ✅
- 从 ZooKeeper 同步服务到数据库
- 验证服务节点信息
- 验证服务方法信息

### 3. 项目管理 ✅
- 创建实际项目
- 添加服务到项目
- 查询项目服务列表

### 4. 虚拟项目管理 ✅
- 创建虚拟项目
- 验证端点信息
- 验证服务关联
- 验证 Nacos 注册

### 5. 服务审批 ✅
- 提交服务审批
- 查询待审批列表
- 查询已审批列表

### 6. MCP协议调用 ✅
- Initialize 初始化
- Tools/List 获取工具列表
- Tools/Call 执行 Dubbo 泛化调用

### 7. SSE端点 ✅
- 测试 SSE 流式连接（endpointName）
- 测试 SSE 流式连接（virtual-endpointName）

### 8. 接口过滤（白名单）✅
- 查询过滤器列表
- 查询启用的过滤器

### 9. 心跳检测 ✅
- 验证服务节点信息
- 验证节点状态字段（isOnline, isHealthy）

### 10. API端点验证 ✅
- 应用列表接口
- 服务统计接口
- 已注册服务接口
- 项目列表接口
- 虚拟项目列表接口

## 🚀 使用方法

### 基本用法

```bash
cd /path/to/zk-mcp-parent/zkInfo/scripts
./test-all-core-functions.sh
```

### 自定义配置

通过环境变量自定义配置：

```bash
# 设置 zkInfo 服务地址
export ZKINFO_URL=http://localhost:9091

# 设置 Nacos 服务地址
export NACOS_URL=http://localhost:8848

# 设置 Nacos 命名空间
export NACOS_NAMESPACE=public

# 设置 Nacos 服务组
export NACOS_GROUP=mcp-server

# 执行测试
./test-all-core-functions.sh
```

### 一键测试（带环境变量）

```bash
ZKINFO_URL=http://localhost:9091 \
NACOS_URL=http://localhost:8848 \
./test-all-core-functions.sh
```

## 📊 测试结果

### 成功示例

```
═══════════════════════════════════════════════════════════
  测试结果汇总
═══════════════════════════════════════════════════════════
总测试用例数: 45
通过: 45
失败: 0
跳过: 0

[SUCCESS] ✅ 所有测试用例通过！
```

### 失败处理

如果测试失败，脚本会：
1. 显示失败的测试用例
2. 对于关键测试，立即停止执行
3. 对于非关键测试，继续执行并记录失败
4. 在最后输出详细的失败统计

## 🔧 前置条件

### 1. 必需的服务

- **zkInfo 服务**: 必须运行在 `http://localhost:9091`（或自定义地址）
- **Nacos**: 必须运行在 `http://localhost:8848`（或自定义地址）
- **ZooKeeper**: 必须运行在 `localhost:2181`（或自定义地址）
- **MySQL**: 数据库必须可访问

### 2. 必需的工具

- `curl`: HTTP 请求工具
- `jq`: JSON 解析工具

安装方法：

```bash
# Ubuntu/Debian
sudo apt-get install curl jq

# macOS
brew install curl jq

# CentOS/RHEL
sudo yum install curl jq
```

### 3. 测试数据准备

- **Dubbo 服务**: 建议启动 `demo-provider` 项目，提供测试服务
- **数据库**: 确保数据库已初始化，包含必要的表结构

## 📝 测试流程

1. **环境检查**: 验证所有必需服务是否可用
2. **服务发现**: 检查是否有可用的 Dubbo 服务
3. **项目管理**: 创建测试项目和虚拟项目
4. **功能验证**: 依次验证各个核心功能
5. **清理**: 自动清理测试数据

## ⚠️ 注意事项

1. **测试数据**: 脚本会自动创建测试数据，并在结束时自动清理
2. **并发测试**: 如果同时运行多个测试实例，可能会产生冲突
3. **服务状态**: 确保所有服务正常运行，否则测试可能失败
4. **网络延迟**: 某些测试可能需要等待服务注册，脚本已包含重试机制

## 🐛 故障排查

### 问题1: 连接失败

```
[ERROR] 检查 zkInfo 服务状态失败
```

**解决方案**:
- 检查 zkInfo 服务是否运行: `curl http://localhost:9091/actuator/health`
- 检查防火墙设置
- 确认服务地址配置正确

### 问题2: 没有可用服务

```
[WARNING] 未找到可用的Dubbo服务
```

**解决方案**:
- 启动 `demo-provider` 项目
- 确保服务已注册到 ZooKeeper
- 检查白名单配置

### 问题3: 权限错误

```
[WARNING] 权限不足或接口错误
```

**解决方案**:
- 检查 `PermissionChecker` 配置
- 确认 API 接口权限设置
- 某些测试可能会跳过，不影响核心功能

### 问题4: Nacos 注册失败

```
[WARNING] 服务未在10秒内注册到Nacos
```

**解决方案**:
- 检查 Nacos 服务是否正常运行
- 检查网络连接
- 增加等待时间（修改脚本中的等待逻辑）

## 📈 性能指标

- **总测试时间**: 约 1-2 分钟（取决于服务响应速度）
- **重试机制**: 每个测试最多重试 3 次
- **超时设置**: 默认 30 秒超时

## 🔄 持续集成

可以在 CI/CD 流程中使用此脚本：

```yaml
# GitHub Actions 示例
- name: Run zkInfo Core Function Tests
  run: |
    cd zk-mcp-parent/zkInfo/scripts
    ./test-all-core-functions.sh
```

## 📚 相关文档

- [zkInfo 项目文档](../../docs/readme.md)
- [虚拟项目测试脚本](./test-virtual-node-complete.sh)
- [复杂对象参数测试脚本](./test-complex-object-parameters.sh)

## 🤝 贡献

如果发现测试脚本的问题或需要添加新的测试用例，请：

1. 检查现有测试是否已覆盖该功能
2. 添加新的测试函数
3. 更新测试统计
4. 提交 Pull Request

## 📄 许可证

与 zkInfo 项目保持一致。

