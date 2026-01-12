# zkInfo 快速开始指南

## 📋 概述

本文档提供 zkInfo 项目的快速开始指南，包括环境准备、服务启动、功能验证等步骤。

## 🚀 快速开始

### 1. 前置条件

运行前置条件检查脚本：

```bash
cd zk-mcp-parent/zkInfo
./scripts/check-prerequisites.sh
```

**必需组件**:
- Java 8/11/17
- MySQL 5.7+
- ZooKeeper 3.4+
- Nacos 2.0+
- Redis 5.0+

### 2. 初始化数据库

```bash
./scripts/init-database.sh
```

### 3. 启动服务

```bash
./scripts/start-zkinfo.sh
```

### 4. 验证服务

```bash
# 验证服务注册
./scripts/verify-service-registration.sh

# 验证虚拟项目
./scripts/verify-virtual-project.sh test-endpoint001

# 完整流程演示
./scripts/demo-complete-flow.sh
```

## 📖 详细文档

- [系统架构图与数据流图](./ZKINFO_ARCHITECTURE_DIAGRAMS.md)
- [完整使用演示文档](./ZKINFO_DEMO_GUIDE.md)

## 🔗 相关链接

- 项目根目录: `/Users/shine/projects.mcp-router-sse-parent/zk-mcp-parent/zkInfo`
- 脚本目录: `scripts/`
- 文档目录: `docs/`


