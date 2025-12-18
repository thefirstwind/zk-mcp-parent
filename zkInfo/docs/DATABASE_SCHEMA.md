# zkInfo 数据库结构设计文档

**创建日期**: 2025-12-17  
**数据库**: MySQL 5.7+  
**字符集**: utf8mb4  
**表名前缀**: zk_

---

## 📋 概述

本文档描述了 zkInfo 项目的完整数据库结构设计，所有表名统一使用 `zk_` 前缀。

---

## 🗄️ 数据库表结构

### 1. zk_project - 项目表

存储项目信息（实际项目 + 虚拟项目）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键ID |
| project_code | VARCHAR(100) | 项目代码（唯一标识） |
| project_name | VARCHAR(200) | 项目名称 |
| project_type | VARCHAR(20) | 项目类型：REAL（实际项目）, VIRTUAL（虚拟项目） |
| description | TEXT | 项目描述 |
| owner_id | BIGINT | 项目负责人ID |
| owner_name | VARCHAR(100) | 项目负责人姓名 |
| status | VARCHAR(20) | 状态：ACTIVE, INACTIVE, DELETED |
| gmt_created | DATETIME | 创建时间 |
| gmt_modified | DATETIME | 更新时间 |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_project_code` (`project_code`)
- KEY `idx_project_type` (`project_type`)
- KEY `idx_status` (`status`)

---

### 2. zk_project_service - 项目服务关联表

存储项目与服务的关系

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 关联ID |
| project_id | BIGINT | 项目ID |
| service_interface | VARCHAR(500) | 服务接口（完整路径） |
| service_version | VARCHAR(50) | 服务版本 |
| service_group | VARCHAR(100) | 服务分组 |
| priority | INT | 优先级（虚拟项目中用于排序） |
| enabled | TINYINT(1) | 是否启用 |
| added_at | DATETIME | 添加时间 |
| added_by | BIGINT | 添加人ID |

**索引**:
- PRIMARY KEY (`id`)
- KEY `idx_project_id` (`project_id`)
- KEY `idx_service_interface` (`service_interface`)
- KEY `idx_service_key` (`service_interface`, `service_version`, `service_group`)

---

### 3. zk_virtual_project_endpoint - 虚拟项目端点表

存储虚拟项目的端点映射信息

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 映射ID |
| virtual_project_id | BIGINT | 虚拟项目ID |
| endpoint_name | VARCHAR(200) | Endpoint名称（对应mcp-router-v3的serviceName） |
| endpoint_path | VARCHAR(500) | Endpoint路径（如：/sse/{endpointName}） |
| mcp_service_name | VARCHAR(200) | MCP服务名称（注册到Nacos的名称） |
| description | TEXT | Endpoint描述 |
| status | VARCHAR(20) | 状态：ACTIVE, INACTIVE |
| gmt_created | DATETIME | 创建时间 |
| gmt_modified | DATETIME | 更新时间 |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_endpoint_name` (`endpoint_name`)
- KEY `idx_virtual_project_id` (`virtual_project_id`)
- KEY `idx_status` (`status`)

---

### 4. zk_dubbo_service - Dubbo服务表

按服务维度存储服务基本信息

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键ID |
| interface_name | VARCHAR(500) | 服务接口名 |
| protocol | VARCHAR(50) | 协议类型 |
| version | VARCHAR(50) | 服务版本 |
| group | VARCHAR(100) | 服务分组 |
| application | VARCHAR(200) | 应用名称 |
| approval_status | VARCHAR(20) | 审批状态: INIT, PENDING, APPROVED, REJECTED |
| approver | VARCHAR(100) | 审批人 |
| approval_time | DATETIME | 审批时间 |
| approval_comment | TEXT | 审批意见 |
| provider_count | INT | 该服务下的Provider数量 |
| online_provider_count | INT | 该服务下在线的Provider数量 |
| gmt_created | DATETIME | 创建时间 |
| gmt_modified | DATETIME | 更新时间 |

**索引**:
- PRIMARY KEY (`id`)
- KEY `idx_interface_name` (`interface_name`)
- KEY `idx_approval_status` (`approval_status`)
- KEY `idx_service_key` (`interface_name`, `protocol`, `version`, `group`, `application`)

---

### 5. zk_provider_info - Provider信息表

存储服务提供者的详细信息

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键ID |
| interface_name | VARCHAR(500) | 服务接口名 |
| address | VARCHAR(200) | 提供者地址 (IP:Port) |
| protocol | VARCHAR(50) | 协议类型 |
| version | VARCHAR(50) | 服务版本 |
| group | VARCHAR(100) | 服务分组 |
| application | VARCHAR(200) | 应用名称 |
| methods | TEXT | 服务方法列表（JSON格式） |
| parameters | TEXT | 其他参数（JSON格式） |
| zk_path | VARCHAR(1000) | ZooKeeper节点路径 |
| registration_time | DATETIME | 注册时间 |
| last_heartbeat_time | DATETIME | 最后心跳时间 |
| is_online | TINYINT(1) | 是否在线 |
| is_healthy | TINYINT(1) | 是否健康 |
| approval_status | VARCHAR(20) | 审批状态: INIT, PENDING, APPROVED, REJECTED |
| approver | VARCHAR(100) | 审批人 |
| approval_time | DATETIME | 审批时间 |
| approval_comment | TEXT | 审批意见 |
| last_sync_time | DATETIME | 最后同步时间 |
| gmt_created | DATETIME | 创建时间 |
| gmt_modified | DATETIME | 更新时间 |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_zk_path` (`zk_path`)
- KEY `idx_interface_name` (`interface_name`)
- KEY `idx_address` (`address`)
- KEY `idx_application` (`application`)
- KEY `idx_approval_status` (`approval_status`)
- KEY `idx_is_online` (`is_online`)
- KEY `idx_is_healthy` (`is_healthy`)
- KEY `idx_last_heartbeat` (`last_heartbeat_time`)

---

### 6. zk_dubbo_service_node - Dubbo服务节点表

存储服务实例节点信息

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键ID |
| service_id | BIGINT | 关联的服务ID |
| address | VARCHAR(200) | 提供者地址 (IP:Port) |
| zk_path | VARCHAR(1000) | ZooKeeper节点路径 |
| register_time | DATETIME | 注册时间 |
| last_heartbeat | DATETIME | 最后心跳时间 |
| last_sync_time | DATETIME | 最后同步时间 |
| gmt_created | DATETIME | 创建时间 |
| gmt_modified | DATETIME | 更新时间 |

**索引**:
- PRIMARY KEY (`id`)
- KEY `idx_service_id` (`service_id`)
- KEY `idx_zk_path` (`zk_path`)
- KEY `idx_address` (`address`)

---

### 7. zk_dubbo_service_method - Dubbo服务方法表

存储服务接口的方法信息

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键ID |
| service_id | BIGINT | 关联的服务ID |
| method_name | VARCHAR(200) | 方法名 |
| return_type | VARCHAR(500) | 返回值类型 |
| gmt_created | DATETIME | 创建时间 |
| gmt_modified | DATETIME | 更新时间 |

**索引**:
- PRIMARY KEY (`id`)
- KEY `idx_service_id` (`service_id`)
- KEY `idx_method_name` (`method_name`)
- UNIQUE KEY `uk_service_method` (`service_id`, `method_name`)

---

### 8. zk_dubbo_method_parameter - Dubbo方法参数表

存储服务方法的参数信息

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键ID |
| method_id | BIGINT | 关联的方法ID |
| parameter_name | VARCHAR(200) | 参数名 |
| parameter_type | VARCHAR(500) | 参数类型 |
| parameter_order | INT | 参数顺序 |
| parameter_description | TEXT | 参数描述 |
| gmt_created | DATETIME | 创建时间 |
| gmt_modified | DATETIME | 更新时间 |

**索引**:
- PRIMARY KEY (`id`)
- KEY `idx_method_id` (`method_id`)
- KEY `idx_parameter_order` (`method_id`, `parameter_order`)

---

### 9. zk_service_approval - 服务审批表

存储服务审批信息

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 审批ID |
| service_interface | VARCHAR(500) | 服务接口名 |
| service_version | VARCHAR(50) | 服务版本 |
| service_group | VARCHAR(100) | 服务分组 |
| project_id | BIGINT | 项目ID（可选） |
| applicant_id | BIGINT | 申请人ID |
| applicant_name | VARCHAR(100) | 申请人姓名 |
| reason | TEXT | 申请原因 |
| status | VARCHAR(20) | 审批状态：PENDING, APPROVED, REJECTED, CANCELLED |
| approver_id | BIGINT | 审批人ID |
| approver_name | VARCHAR(100) | 审批人姓名 |
| comment | TEXT | 审批意见 |
| approved_at | DATETIME | 审批时间 |
| gmt_created | DATETIME | 创建时间 |
| gmt_modified | DATETIME | 更新时间 |

**索引**:
- PRIMARY KEY (`id`)
- KEY `idx_service_interface` (`service_interface`)
- KEY `idx_project_id` (`project_id`)
- KEY `idx_status` (`status`)
- KEY `idx_applicant_id` (`applicant_id`)

---

### 10. zk_approval_log - 审批日志表

记录审批历史，便于审计和追踪

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键ID |
| provider_id | BIGINT | 关联的服务提供者ID |
| service_id | BIGINT | 关联的服务ID（可选） |
| old_status | VARCHAR(20) | 原审批状态 |
| new_status | VARCHAR(20) | 新审批状态 |
| approver | VARCHAR(100) | 审批人 |
| approval_comment | TEXT | 审批意见 |
| gmt_created | DATETIME | 创建时间 |

**索引**:
- PRIMARY KEY (`id`)
- KEY `idx_provider_id` (`provider_id`)
- KEY `idx_service_id` (`service_id`)
- KEY `idx_created_at` (`gmt_created`)

---

## 📊 表关系图

```
zk_project (项目)
  ├── zk_project_service (项目服务关联)
  └── zk_virtual_project_endpoint (虚拟项目端点)

zk_dubbo_service (Dubbo服务)
  ├── zk_dubbo_service_node (服务节点)
  └── zk_dubbo_service_method (服务方法)
      └── zk_dubbo_method_parameter (方法参数)

zk_provider_info (Provider信息)
  └── zk_approval_log (审批日志)

zk_service_approval (服务审批)
```

---

## 🔧 Entity 类映射

| 表名 | Entity 类 | Mapper 接口 |
|------|-----------|-------------|
| zk_project | ProjectEntity | ProjectMapper |
| zk_project_service | ProjectServiceEntity | ProjectServiceMapper |
| zk_virtual_project_endpoint | VirtualProjectEndpointEntity | VirtualProjectEndpointMapper |
| zk_dubbo_service | DubboServiceEntity | DubboServiceMapper |
| zk_provider_info | ProviderInfoEntity | ProviderInfoMapper |
| zk_dubbo_service_node | DubboServiceNodeEntity | DubboServiceNodeMapper |
| zk_dubbo_service_method | DubboServiceMethodEntity | DubboServiceMethodMapper |
| zk_dubbo_method_parameter | DubboMethodParameterEntity | DubboMethodParameterMapper |
| zk_service_approval | ServiceApprovalEntity | ServiceApprovalMapper |
| zk_approval_log | ApprovalLog | ApprovalLogMapper |

---

## 📝 使用说明

1. **执行数据库脚本**: 运行 `src/main/resources/db/schema.sql` 创建所有表
2. **配置数据源**: 在 `application.yml` 中配置数据库连接信息
3. **MyBatis 配置**: 确保 `mapper-locations` 和 `type-aliases-package` 配置正确

---

## ✅ 完成状态

- ✅ 数据库脚本已创建
- ✅ Entity 类已创建
- ✅ Mapper XML 文件已更新（表名统一为 zk_ 开头）
- ✅ ProviderInfoEntity 字段映射已完善
- ⏳ Mapper 接口需要创建（ProjectMapper, ProjectServiceMapper, VirtualProjectEndpointMapper, ServiceApprovalMapper）

---

## 📌 注意事项

1. 所有表名统一使用 `zk_` 前缀
2. 时间字段统一使用 `gmt_created` 和 `gmt_modified`（部分表使用 `created_at` 和 `updated_at`，需要统一）
3. JSON 字段（methods, parameters）需要使用 TypeHandler 进行序列化/反序列化
4. 外键约束已注释，可根据实际需求决定是否启用

