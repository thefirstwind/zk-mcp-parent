# Dubbo转MCP服务 - 详细开发计划

## 📅 项目时间线

**项目开始日期**: 2025年1月15日  
**核心功能完成日期**: 2025年1月27日（春节前，必须完成）  
**项目完成日期**: 2025年2月28日  
**核心功能工作日**: 13个工作日（1月15日-1月27日）  
**团队规模**: 2人（A和B）

### 重要时间节点
- **核心功能完成**: 2025年1月27日（春节前，必须完成）
- **春节假期**: 2025年1月28日 - 2月3日（7天）
- **优化与完善**: 2025年2月4日 - 2月28日（春节后）
- **项目交付**: 2025年2月28日

---

## 🎯 项目目标

基于现有的 **zk-mcp-parent** 项目，实现 **Dubbo转MCP服务** 核心功能，**不修改或少修改 mcp-router-v3**，通过标准接口和配置实现集成。

### 核心功能（春节前必须完成）
1. **服务准入流程**: 服务申请、审批、接入（不是所有服务都自动接入）
2. **元数据采集**: 从Zookeeper自动发现Dubbo服务（仅已审批的服务）
3. **元数据管理**: MySQL存储和管理服务元数据
4. **MCP服务注册**: 将Dubbo服务注册为MCP服务到Nacos（标准格式，多版本支持）
5. **协议转换**: Dubbo协议 ↔ MCP协议双向转换
6. **MCP协议端点**: 实现标准MCP协议接口，供mcp-router-v3路由
7. **版本适配**: JDK、Dubbo、Zookeeper版本适配和兼容性处理

### 扩展功能（春节后）
8. **元数据维护**: 元数据编辑、版本管理、灰度发布、回滚机制
9. **Nacos多版本管理**: 服务多版本并存、版本路由规则
10. **数据同步与一致性**: ZK→DB→Nacos三端同步、一致性保障、冲突解决
11. **Web管理界面**: 服务申请、审批、元数据维护、同步监控
12. **鉴权与安全**: 用户和应用级别鉴权
13. **性能优化**: 缓存、连接池优化

---

## 🔗 与 mcp-router-v3 的集成方案（零修改原则）

### 核心原则：不修改 mcp-router-v3，通过标准接口集成

**集成策略**：
1. **zk-mcp-parent 作为标准MCP服务**：实现标准MCP协议接口
2. **注册到Nacos**：使用与mcp-router-v3兼容的服务格式
3. **mcp-router-v3自动发现和路由**：无需任何修改

### 集成架构

```
Zookeeper (Dubbo) 
    ↓ 监听
zk-mcp-parent (实现标准MCP协议)
    ↓ 注册（标准格式）
Nacos (服务注册中心)
    ↓ 自动发现（无需修改）
mcp-router-v3 (路由层，零修改)
    ↓ 路由
MCP客户端
```

### 关键集成点

#### 1. **服务注册格式兼容** ✅
- **要求**: zk-mcp-parent 注册到Nacos的服务格式必须与mcp-router-v3兼容
- **实现**: 使用标准的Nacos服务注册格式，服务组设置为 `mcp-server`
- **元数据**: 包含必要的MCP服务元数据（version, sseEndpoint等）

#### 2. **MCP协议标准实现** ✅
- **要求**: 实现标准MCP协议端点（initialize, tools/list, tools/call等）
- **实现**: 完全按照MCP 2024-11-05规范实现
- **端点**: `/mcp/initialize`, `/mcp/tools/list`, `/mcp/tools/call` 等

#### 3. **健康检查端点** ✅
- **要求**: 提供标准的健康检查端点
- **实现**: Spring Boot Actuator `/actuator/health` 或自定义健康检查端点
- **格式**: 返回标准JSON格式，mcp-router-v3会自动检查

#### 4. **服务发现兼容** ✅
- **要求**: 服务名称、服务组、元数据格式与mcp-router-v3兼容
- **实现**: 使用相同的服务组（mcp-server），元数据格式一致

---

## 📋 详细开发计划

### 第一阶段：核心功能开发（1月15日 - 1月27日，13个工作日）

#### Week 1: 架构设计与元数据采集（1月15日 - 1月19日）

**员工A - 架构设计与版本适配**
- **Day 1 (1月15日)**: 
  - 整体架构设计文档
  - 技术方案选型（Spring Boot 3.2, MyBatis, MySQL）
  - **数据库表结构设计**（包含服务审批、元数据版本、Nacos注册、同步日志等表）
  - **服务准入流程设计**（申请、审批、接入）
  - **版本适配方案设计**（JDK、Dubbo、ZK版本兼容性）
  
- **Day 2 (1月16日)**:
  - 创建项目基础结构
  - 配置Spring Boot + MyBatis + MySQL
  - **版本适配测试**（不同JDK版本、Dubbo版本、ZK版本）
  - 编写数据库初始化脚本
  
- **Day 3 (1月17日)**:
  - 技术验证：Zookeeper连接测试（不同版本）
  - 技术验证：Dubbo泛化调用测试（不同版本）
  - 技术验证：Nacos服务注册测试
  - 版本兼容性测试报告
  
- **Day 4 (1月18日)**:
  - 代码审查和架构评审
  - 完善技术文档
  - 版本适配文档编写
  
- **Day 5 (1月19日)**:
  - 环境迁移准备（公司内部环境）
  - 依赖版本锁定
  - 准备第二阶段开发环境

**员工B - 服务准入与元数据采集模块开发**
- **Day 1 (1月15日)**:
  - 研究Zookeeper Dubbo节点结构（不同版本差异）
  - **设计服务准入流程**（申请、审批、接入）
  - 设计元数据采集流程（仅已审批服务）
  - 编写Zookeeper监听器框架
  
- **Day 2 (1月16日)**:
  - **实现服务申请功能**（API和基础界面）
  - 实现Zookeeper节点监听（仅监听已审批服务）
  - 实现Dubbo元数据解析器（兼容不同版本）
  - 实现数据清洗逻辑
  
- **Day 3 (1月17日)**:
  - **实现服务审批功能**（API和基础界面）
  - 版本兼容性处理（不同ZK版本的节点结构差异）
  - 异常处理和重连机制
  - 单元测试
  
- **Day 4 (1月18日)**:
  - **实现服务接入流程**（审批通过后自动接入）
  - 集成测试
  - 多版本兼容性测试
  - Bug修复
  
- **Day 5 (1月19日)**:
  - 代码审查
  - 性能测试
  - 文档完善

#### Week 2: 元数据管理与MCP服务注册（1月20日 - 1月24日）

**员工A - 元数据管理与MCP服务注册（多版本支持）**
- **Day 1 (1月20日)**:
  - 实现MySQL数据库操作层（MyBatis Mapper）
  - 实现元数据存储Service
  - 实现元数据查询Service
  - **实现元数据版本管理基础功能**
  
- **Day 2 (1月21日)**:
  - 实现元数据缓存机制（Redis，可选）
  - **实现基础同步逻辑**（ZK→DB）
  - 研究mcp-router-v3的Nacos注册格式（**不修改代码，只研究格式**）
  
- **Day 3 (1月22日)**:
  - 设计Dubbo服务到MCP服务的映射规则
  - 实现MCP服务信息构建器（兼容mcp-router-v3格式）
  - **实现Nacos服务注册（多版本支持）**（使用Nacos SDK，不依赖mcp-router-v3代码）
  
- **Day 4 (1月23日)**:
  - 实现心跳机制
  - **实现DB→Nacos同步逻辑**（多版本同步）
  - 与mcp-router-v3集成测试（验证服务发现）
  
- **Day 5 (1月24日)**:
  - 单元测试
  - 集成测试
  - 代码审查

**员工B - 协议转换模块**
- **Day 1 (1月20日)**:
  - 研究MCP协议规范（2024-11-05）
  - 设计Dubbo协议到MCP协议的转换规则
  - 实现参数类型映射
  
- **Day 2 (1月21日)**:
  - 实现MCP工具定义生成（从Dubbo方法）
  - 实现参数序列化/反序列化
  - 实现返回值转换
  
- **Day 3 (1月22日)**:
  - 实现MCP协议端点（initialize, tools/list, tools/call）
  - 实现标准MCP响应格式
  - 版本兼容性处理（不同Dubbo版本的参数类型差异）
  
- **Day 4 (1月23日)**:
  - 单元测试
  - 协议兼容性测试
  - 与mcp-router-v3路由测试
  
- **Day 5 (1月24日)**:
  - Bug修复
  - 性能优化
  - 代码审查

#### Week 3: 泛化调用与集成测试（1月27日）

**员工A + 员工B - 联合开发与测试**
- **Day 1 (1月27日)**:
  - 完善Dubbo泛化调用实现（复用zk-mcp-parent现有代码）
  - 实现连接池管理
  - 实现超时和重试机制
  - **端到端集成测试**（Zookeeper → zk-mcp-parent → Nacos → mcp-router-v3 → MCP客户端）
  - **版本适配验证**（不同环境下的兼容性）
  - **核心功能验收**

**🎉 第一阶段里程碑（春节前必须完成）**: 
- ✅ 服务准入流程完成（申请、审批、接入）
- ✅ 元数据采集完成（仅已审批服务）
- ✅ 元数据存储完成
- ✅ MCP服务注册到Nacos（格式兼容mcp-router-v3，多版本支持）
- ✅ mcp-router-v3可以路由到zk-mcp-parent服务（零修改）
- ✅ 基础协议转换完成
- ✅ 版本适配完成
- ✅ 基础同步逻辑完成（ZK→DB→Nacos）

---

### 第二阶段：优化与完善（2月4日 - 2月28日，春节后）

#### Week 4: Web管理界面与元数据维护（2月4日 - 2月7日）

**员工A - 后端API开发**
- **Day 1-2 (2月4-5日)**:
  - 设计RESTful API接口
  - **实现服务申请API**
  - **实现服务审批API**
  - 实现元数据查询API
  - 实现服务管理API
  
- **Day 3-4 (2月6-7日)**:
  - **实现元数据维护API**（编辑、版本管理、回滚）
  - **实现同步监控API**
  - 实现服务状态监控API
  - 实现统计信息API
  - API文档编写（Swagger）

**员工B - 前端界面开发**
- **Day 1-2 (2月4-5日)**:
  - 搭建前端项目（React + Ant Design）
  - 实现基础布局和路由
  - **实现服务申请页面**
  - **实现服务审批页面**
  - 实现元数据列表页面
  
- **Day 3-4 (2月6-7日)**:
  - **实现元数据维护页面**（编辑、版本管理、回滚）
  - **实现同步监控页面**
  - 实现服务状态监控页面
  - 前端测试和UI优化

#### Week 5: 高级功能（2月10日 - 2月14日）

**员工A - 元数据维护与一致性保障**
- **Day 1-2 (2月10-11日)**:
  - **实现元数据版本管理**（版本快照、版本对比）
  - **实现灰度发布机制**（草稿、预览、灰度、全量）
  - **实现回滚机制**
  
- **Day 3-4 (2月12-13日)**:
  - **实现完整同步逻辑**（ZK→DB→Nacos三端同步）
  - **实现一致性保障**（事务、最终一致性、冲突解决）
  - **实现Nacos多版本管理**（版本并存、版本路由）
  
- **Day 5 (2月14日)**:
  - 代码审查
  - 文档完善

**员工B - 鉴权与性能优化**
- **Day 1-2 (2月10-11日)**:
  - 实现用户级别鉴权（JWT）
  - 实现应用级别鉴权（API Key）
  - 实现权限管理
  
- **Day 3-4 (2月12-13日)**:
  - 实现连接池优化
  - 实现缓存优化
  - 性能测试和调优
  
- **Day 5 (2月14日)**:
  - 代码审查
  - 文档完善

#### Week 6: 集成测试与交付（2月17日 - 2月28日）

**员工A + 员工B - 联合测试与交付**
- **Day 1-2 (2月17-18日)**:
  - 编写全链路测试用例
  - 执行集成测试
  - 修复测试问题
  
- **Day 3-4 (2月19-20日)**:
  - 性能测试
  - 安全测试
  - 兼容性测试
  
- **Day 5-6 (2月21-22日)**:
  - 编写用户手册
  - 编写部署文档
  - 编写API文档
  
- **Day 7-8 (2月24-25日)**:
  - 最终测试
  - 项目总结
  - 交付准备
  
- **Day 9 (2月28日)**:
  - 项目交付
  - 技术分享

---

## 🔧 版本适配方案

### 当前版本
- **JDK**: 17
- **Spring Boot**: 3.2.0
- **Dubbo**: 3.2.8
- **Zookeeper**: 3.8+（通过Curator 5.5.0连接）
- **Nacos**: 2.3+（MCP服务注册）

### 公司内部环境可能遇到的版本差异

#### 1. JDK版本适配
**可能版本**: JDK 8, 11, 17, 21

**适配策略**:
- **目标**: 保持JDK 17，但兼容JDK 11+
- **处理**: 
  - 避免使用JDK 17特有特性（如sealed classes）
  - 使用兼容的API
  - 如果必须使用JDK 8，考虑降级Spring Boot到2.7.x

**代码示例**:
```java
// 避免使用JDK 17特有特性
// 使用兼容的集合API
List<String> list = new ArrayList<>(); // 兼容所有版本
```

#### 2. Dubbo版本适配
**可能版本**: Dubbo 2.7.x, 3.0.x, 3.2.x

**适配策略**:
- **目标**: 支持Dubbo 3.0+，兼容2.7.x（如果必须）
- **处理**:
  - 使用Dubbo泛化调用（GenericService），版本无关
  - 不同版本的元数据格式可能不同，需要适配
  - Zookeeper节点结构可能不同

**代码示例**:
```java
// 使用泛化调用，版本无关
ReferenceConfig<GenericService> reference = new ReferenceConfig<>();
reference.setInterface(serviceName);
reference.setGeneric(true); // 泛化调用
```

#### 3. Zookeeper版本适配
**可能版本**: ZK 3.4.x, 3.5.x, 3.6.x, 3.7.x, 3.8.x

**适配策略**:
- **目标**: 支持ZK 3.6+，兼容3.4+（如果必须）
- **处理**:
  - 使用Curator框架，自动处理版本差异
  - 不同版本的节点路径格式可能不同
  - 需要适配Dubbo在不同ZK版本下的注册格式

**代码示例**:
```java
// 使用Curator，自动处理版本差异
CuratorFramework client = CuratorFrameworkFactory.builder()
    .connectString(connectString)
    .retryPolicy(new ExponentialBackoffRetry(1000, 3))
    .build();
```

#### 4. Spring Boot版本适配
**可能版本**: Spring Boot 2.7.x, 3.0.x, 3.2.x

**适配策略**:
- **目标**: 保持Spring Boot 3.2.0
- **处理**:
  - 如果必须使用Spring Boot 2.7.x，需要大量代码调整（Jakarta EE → Java EE）
  - 优先保持3.2.0，通过配置兼容

### 版本适配检查清单

- [ ] JDK版本兼容性测试（11, 17, 21）
- [ ] Dubbo版本兼容性测试（2.7.x, 3.0.x, 3.2.x）
- [ ] Zookeeper版本兼容性测试（3.4.x, 3.6.x, 3.8.x）
- [ ] Spring Boot版本兼容性测试（2.7.x, 3.0.x, 3.2.x）
- [ ] 不同版本组合的集成测试
- [ ] 版本适配文档编写

---

## 📊 数据库设计

### 核心表结构

详细数据库设计请参考：`SERVICE_APPROVAL_DESIGN.md`

#### 1. service_approval（服务审批表）
```sql
CREATE TABLE service_approval (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_name VARCHAR(255) NOT NULL COMMENT '服务名称',
    service_interface VARCHAR(500) NOT NULL COMMENT '服务接口（完整路径）',
    applicant_id BIGINT NOT NULL COMMENT '申请人ID',
    applicant_name VARCHAR(100) NOT NULL COMMENT '申请人姓名',
    approval_status VARCHAR(20) DEFAULT 'PENDING' COMMENT '审批状态：PENDING, APPROVED, REJECTED',
    approval_time TIMESTAMP COMMENT '审批时间',
    version VARCHAR(50) COMMENT '服务版本',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE, INACTIVE, DELETED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_approval_status (approval_status)
) COMMENT '服务审批表';
```

#### 2. dubbo_service_metadata（Dubbo服务元数据表）
```sql
CREATE TABLE dubbo_service_metadata (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_name VARCHAR(255) NOT NULL COMMENT '服务接口名',
    method_name VARCHAR(255) NOT NULL COMMENT '方法名',
    application_name VARCHAR(255) COMMENT '应用名',
    provider_ip VARCHAR(50) COMMENT '提供者IP',
    provider_port INT COMMENT '提供者端口',
    version VARCHAR(50) COMMENT '版本',
    group_name VARCHAR(100) COMMENT '分组',
    metadata_json TEXT COMMENT '完整元数据JSON',
    zk_path VARCHAR(500) COMMENT 'Zookeeper路径',
    zk_version VARCHAR(20) COMMENT 'Zookeeper版本（用于兼容性）',
    dubbo_version VARCHAR(20) COMMENT 'Dubbo版本（用于兼容性）',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_service_method (service_name, method_name, provider_ip, provider_port)
) COMMENT 'Dubbo服务元数据表';
```

#### 3. metadata_version（元数据版本表）
```sql
CREATE TABLE metadata_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_metadata_id BIGINT NOT NULL COMMENT '关联元数据ID',
    version_number VARCHAR(50) NOT NULL COMMENT '版本号',
    metadata_snapshot TEXT COMMENT '元数据快照（JSON）',
    change_type VARCHAR(20) COMMENT '变更类型：CREATE, UPDATE, DELETE',
    is_current BOOLEAN DEFAULT TRUE COMMENT '是否当前版本',
    rollback_enabled BOOLEAN DEFAULT TRUE COMMENT '是否可回滚',
    operation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_service_metadata_id (service_metadata_id),
    INDEX idx_is_current (is_current)
) COMMENT '元数据版本表';
```

#### 4. nacos_service_registry（Nacos服务注册表，支持多版本）
```sql
CREATE TABLE nacos_service_registry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_metadata_id BIGINT NOT NULL COMMENT '关联元数据ID',
    mcp_service_name VARCHAR(255) NOT NULL COMMENT 'MCP服务名称',
    service_group VARCHAR(100) DEFAULT 'mcp-server' COMMENT '服务组（与mcp-router-v3兼容）',
    version VARCHAR(50) NOT NULL COMMENT '版本（支持多版本）',
    nacos_instance_id VARCHAR(255) COMMENT 'Nacos实例ID',
    registration_status VARCHAR(20) DEFAULT 'REGISTERED' COMMENT '注册状态',
    sync_status VARCHAR(20) DEFAULT 'SYNCED' COMMENT '同步状态：SYNCED, PENDING, FAILED',
    registered_at TIMESTAMP COMMENT '注册时间',
    last_heartbeat TIMESTAMP COMMENT '最后心跳时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_service_version (mcp_service_name, version, service_group),
    INDEX idx_sync_status (sync_status)
) COMMENT 'Nacos服务注册表（多版本支持）';
```

#### 5. metadata_sync_log（元数据同步日志表）
```sql
CREATE TABLE metadata_sync_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_metadata_id BIGINT NOT NULL COMMENT '关联元数据ID',
    sync_type VARCHAR(20) NOT NULL COMMENT '同步类型：ZK_TO_DB, DB_TO_NACOS, NACOS_TO_DB',
    sync_status VARCHAR(20) DEFAULT 'SUCCESS' COMMENT '同步状态：SUCCESS, FAILED, PENDING',
    error_message TEXT COMMENT '错误信息',
    sync_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_service_metadata_id (service_metadata_id),
    INDEX idx_sync_status (sync_status)
) COMMENT '元数据同步日志表';
```

---

## 🚀 部署方案

### 部署架构

```
┌─────────────────┐
│   Zookeeper     │ (Dubbo注册中心)
└────────┬────────┘
         │
┌────────▼────────────────────────┐
│   zk-mcp-parent                 │
│   - 元数据采集                  │
│   - 协议转换                    │
│   - MCP服务注册                 │
└────────┬────────────────────────┘
         │ 注册到Nacos
┌────────▼────────┐
│     Nacos       │ (MCP服务注册中心)
└────────┬────────┘
         │
┌────────▼────────────────────────┐
│   mcp-router-v3                 │
│   - 路由（零修改）              │
│   - 负载均衡（零修改）           │
│   - 健康检查（零修改）           │
└────────┬────────────────────────┘
         │
┌────────▼────────┐
│   MCP客户端     │
└─────────────────┘
```

### 部署步骤

1. **部署基础服务**
   - 启动Zookeeper（Dubbo注册中心）
   - 启动Nacos（MCP服务注册中心）
   - 启动MySQL（元数据存储）
   - 启动Redis（缓存，可选）

2. **部署zk-mcp-parent**
   - 配置Zookeeper连接
   - 配置Nacos连接
   - 配置MySQL连接
   - 启动服务，自动采集元数据并注册到Nacos

3. **部署mcp-router-v3**（无需修改）
   - 配置Nacos连接
   - 启动服务，自动发现zk-mcp-parent注册的服务

4. **验证**
   - 检查Nacos服务列表
   - 测试MCP协议调用
   - 验证路由功能

---

## ✅ 验收标准

### 核心功能验收（春节前）
- [ ] 服务准入流程完成（申请、审批、接入）
- [ ] 能够从Zookeeper发现Dubbo服务（仅已审批的服务）
- [ ] 元数据正确存储到MySQL
- [ ] Dubbo服务成功注册为MCP服务到Nacos（格式兼容mcp-router-v3，多版本支持）
- [ ] mcp-router-v3能够路由到zk-mcp-parent服务（零修改）
- [ ] MCP协议调用能够正确转换为Dubbo调用
- [ ] 版本适配完成（JDK、Dubbo、ZK）
- [ ] 基础同步逻辑完成（ZK→DB→Nacos）

### 扩展功能验收（春节后）
- [ ] 元数据维护功能完成（编辑、版本管理、回滚）
- [ ] 灰度发布机制完成
- [ ] Nacos多版本管理完成
- [ ] 完整同步与一致性保障完成
- [ ] Web管理界面完成（申请、审批、维护、监控）

### 性能验收
- [ ] 元数据采集延迟 < 5秒
- [ ] MCP调用响应时间 < 500ms（P95）
- [ ] 支持并发调用 > 1000 QPS

### 质量验收
- [ ] 代码覆盖率 > 80%
- [ ] 无严重Bug（P0/P1）
- [ ] 文档完整
- [ ] 通过安全测试

---

## 🐛 风险与应对

### 风险1: 版本适配复杂度高
- **应对**: 提前进行版本兼容性测试，设计适配层

### 风险2: 春节前时间紧张
- **应对**: 聚焦核心功能，扩展功能春节后完成

### 风险3: mcp-router-v3集成问题
- **应对**: 严格遵循标准格式，不修改mcp-router-v3，通过配置和接口适配

### 风险4: 公司内部环境差异
- **应对**: 提前了解公司环境，准备多版本适配方案

---

**文档版本**: v2.0.0  
**创建日期**: 2025-01-15  
**最后更新**: 2025-01-15
