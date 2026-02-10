# 服务准入与元数据管理设计方案

## 📋 概述

针对公司内部Zookeeper有上万个服务的场景，设计服务准入流程、元数据维护机制、Nacos多版本管理和数据一致性方案。

---

## 🎯 核心需求

1. **服务准入流程**：申请 → 审批 → 接入（不是所有服务都自动接入）
2. **Web管理界面**：申请、审批、元数据维护
3. **元数据维护**：避免修复失败导致服务不可用
4. **Nacos多版本管理**：支持服务多版本并存
5. **数据一致性**：元数据同步和一致性保障

---

## 🏗️ 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Zookeeper (上万个服务)                     │
└────────────────────┬────────────────────────────────────────┘
                     │ 监听（仅监听已审批的服务）
┌────────────────────▼────────────────────────────────────────┐
│          zk-mcp-parent (服务准入与元数据管理)                │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ 1. 服务准入管理                                        │  │
│  │    - 服务申请                                          │  │
│  │    - 审批流程                                          │  │
│  │    - 服务接入                                          │  │
│  │ 2. 元数据管理                                          │  │
│  │    - 元数据采集（仅已审批服务）                        │  │
│  │    - 元数据维护                                        │  │
│  │    - 版本管理                                          │  │
│  │ 3. 同步与一致性                                        │  │
│  │    - Nacos多版本同步                                   │  │
│  │    - 元数据一致性保障                                  │  │
│  │    - 回滚机制                                          │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────┬────────────────────────────────────────┘
                     │ 注册（多版本管理）
┌────────────────────▼────────────────────────────────────────┐
│                    Nacos (MCP服务注册中心)                    │
│  - 服务多版本并存                                            │
│  - 版本路由规则                                              │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│          mcp-router-v3 (路由层，零修改)                     │
└─────────────────────────────────────────────────────────────┘
```

---

## 📊 数据库设计

### 1. service_approval（服务审批表）

```sql
CREATE TABLE service_approval (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_name VARCHAR(255) NOT NULL COMMENT '服务名称',
    service_interface VARCHAR(500) NOT NULL COMMENT '服务接口（完整路径）',
    applicant_id BIGINT NOT NULL COMMENT '申请人ID',
    applicant_name VARCHAR(100) NOT NULL COMMENT '申请人姓名',
    applicant_department VARCHAR(200) COMMENT '申请人部门',
    application_reason TEXT COMMENT '申请原因',
    application_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    approver_id BIGINT COMMENT '审批人ID',
    approver_name VARCHAR(100) COMMENT '审批人姓名',
    approval_status VARCHAR(20) DEFAULT 'PENDING' COMMENT '审批状态：PENDING, APPROVED, REJECTED',
    approval_time TIMESTAMP COMMENT '审批时间',
    approval_comment TEXT COMMENT '审批意见',
    version VARCHAR(50) COMMENT '服务版本',
    group_name VARCHAR(100) COMMENT '服务分组',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE, INACTIVE, DELETED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_service_name (service_name),
    INDEX idx_approval_status (approval_status),
    INDEX idx_applicant_id (applicant_id)
) COMMENT '服务审批表';
```

### 2. service_metadata（服务元数据表）

```sql
CREATE TABLE service_metadata (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    approval_id BIGINT NOT NULL COMMENT '关联审批ID',
    service_name VARCHAR(255) NOT NULL COMMENT '服务名称',
    service_interface VARCHAR(500) NOT NULL COMMENT '服务接口',
    method_name VARCHAR(255) NOT NULL COMMENT '方法名',
    method_signature TEXT COMMENT '方法签名',
    parameter_types TEXT COMMENT '参数类型列表（JSON）',
    return_type VARCHAR(255) COMMENT '返回类型',
    metadata_json TEXT COMMENT '完整元数据JSON',
    zk_path VARCHAR(500) COMMENT 'Zookeeper路径',
    provider_ip VARCHAR(50) COMMENT '提供者IP',
    provider_port INT COMMENT '提供者端口',
    version VARCHAR(50) COMMENT '版本',
    group_name VARCHAR(100) COMMENT '分组',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE, INACTIVE, DEPRECATED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_approval_id (approval_id),
    INDEX idx_service_name (service_name),
    INDEX idx_status (status)
) COMMENT '服务元数据表';
```

### 3. metadata_version（元数据版本表）

```sql
CREATE TABLE metadata_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_metadata_id BIGINT NOT NULL COMMENT '关联元数据ID',
    version_number VARCHAR(50) NOT NULL COMMENT '版本号',
    metadata_snapshot TEXT COMMENT '元数据快照（JSON）',
    change_type VARCHAR(20) COMMENT '变更类型：CREATE, UPDATE, DELETE',
    change_description TEXT COMMENT '变更描述',
    operator_id BIGINT COMMENT '操作人ID',
    operator_name VARCHAR(100) COMMENT '操作人姓名',
    operation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    is_current BOOLEAN DEFAULT TRUE COMMENT '是否当前版本',
    rollback_enabled BOOLEAN DEFAULT TRUE COMMENT '是否可回滚',
    INDEX idx_service_metadata_id (service_metadata_id),
    INDEX idx_version_number (version_number),
    INDEX idx_is_current (is_current)
) COMMENT '元数据版本表';
```

### 4. nacos_service_registry（Nacos服务注册表）

```sql
CREATE TABLE nacos_service_registry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_metadata_id BIGINT NOT NULL COMMENT '关联元数据ID',
    mcp_service_name VARCHAR(255) NOT NULL COMMENT 'MCP服务名称',
    nacos_service_name VARCHAR(255) NOT NULL COMMENT 'Nacos服务名称',
    service_group VARCHAR(100) DEFAULT 'mcp-server' COMMENT '服务组',
    version VARCHAR(50) NOT NULL COMMENT '版本',
    nacos_instance_id VARCHAR(255) COMMENT 'Nacos实例ID',
    ip VARCHAR(50) NOT NULL COMMENT 'IP地址',
    port INT NOT NULL COMMENT '端口',
    metadata_json TEXT COMMENT 'Nacos元数据（JSON）',
    registration_status VARCHAR(20) DEFAULT 'REGISTERED' COMMENT '注册状态：REGISTERED, UNREGISTERED, FAILED',
    registered_at TIMESTAMP COMMENT '注册时间',
    last_heartbeat TIMESTAMP COMMENT '最后心跳时间',
    sync_status VARCHAR(20) DEFAULT 'SYNCED' COMMENT '同步状态：SYNCED, PENDING, FAILED',
    sync_time TIMESTAMP COMMENT '同步时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_service_version (mcp_service_name, version, service_group),
    INDEX idx_service_metadata_id (service_metadata_id),
    INDEX idx_sync_status (sync_status)
) COMMENT 'Nacos服务注册表';
```

### 5. metadata_sync_log（元数据同步日志表）

```sql
CREATE TABLE metadata_sync_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_metadata_id BIGINT NOT NULL COMMENT '关联元数据ID',
    sync_type VARCHAR(20) NOT NULL COMMENT '同步类型：ZK_TO_DB, DB_TO_NACOS, NACOS_TO_DB',
    sync_direction VARCHAR(20) COMMENT '同步方向：FORWARD, BACKWARD',
    source_data TEXT COMMENT '源数据（JSON）',
    target_data TEXT COMMENT '目标数据（JSON）',
    sync_status VARCHAR(20) DEFAULT 'SUCCESS' COMMENT '同步状态：SUCCESS, FAILED, PENDING',
    error_message TEXT COMMENT '错误信息',
    operator_id BIGINT COMMENT '操作人ID',
    sync_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '同步时间',
    INDEX idx_service_metadata_id (service_metadata_id),
    INDEX idx_sync_status (sync_status),
    INDEX idx_sync_time (sync_time)
) COMMENT '元数据同步日志表';
```

---

## 🔄 服务准入流程

### 流程设计

```
1. 用户申请
   ↓
2. 提交审批（待审批状态）
   ↓
3. 领导审批
   ├─ 通过 → 4. 服务接入
   └─ 拒绝 → 结束
   ↓
4. 服务接入
   ├─ 元数据采集
   ├─ 元数据存储
   └─ 注册到Nacos
   ↓
5. 服务可用
```

### 1. 服务申请

**Web界面功能**：
- 服务搜索（从Zookeeper搜索可用服务）
- 服务申请表单
- 申请原因填写
- 服务版本选择

**API设计**：
```java
@PostMapping("/api/service/apply")
public ApiResponse<ServiceApproval> applyService(@RequestBody ServiceApplicationRequest request) {
    // 1. 验证服务是否存在
    // 2. 检查是否已申请
    // 3. 创建申请记录
    // 4. 通知审批人
}
```

### 2. 审批流程

**Web界面功能**：
- 待审批列表
- 审批详情查看
- 审批操作（通过/拒绝）
- 审批意见填写

**API设计**：
```java
@PostMapping("/api/service/approve")
public ApiResponse<Void> approveService(@RequestBody ApprovalRequest request) {
    // 1. 验证审批权限
    // 2. 更新审批状态
    // 3. 如果通过，触发服务接入流程
    // 4. 通知申请人
}
```

### 3. 服务接入

**自动流程**：
```java
@Service
public class ServiceOnboardingService {
    
    @Transactional
    public void onboardService(Long approvalId) {
        // 1. 获取审批信息
        ServiceApproval approval = getApproval(approvalId);
        
        // 2. 从Zookeeper采集元数据（仅已审批的服务）
        List<ServiceMetadata> metadataList = collectMetadata(approval);
        
        // 3. 存储元数据
        saveMetadata(metadataList);
        
        // 4. 注册到Nacos（多版本支持）
        registerToNacos(metadataList);
        
        // 5. 创建版本记录
        createVersionRecord(metadataList);
        
        // 6. 更新审批状态
        updateApprovalStatus(approvalId, "ONBOARDED");
    }
}
```

---

## 🔧 元数据维护机制

### 1. 元数据编辑流程（避免服务不可用）

#### 流程设计

```
1. 用户编辑元数据
   ↓
2. 保存为草稿（不生效）
   ↓
3. 预览验证
   ├─ 验证通过 → 4. 提交审核
   └─ 验证失败 → 返回编辑
   ↓
4. 提交审核（可选）
   ↓
5. 审核通过 → 6. 灰度发布
   ↓
6. 灰度发布
   ├─ 小流量验证
   ├─ 验证通过 → 7. 全量发布
   └─ 验证失败 → 8. 回滚
   ↓
7. 全量发布
   ↓
8. 回滚（如有问题）
```

#### 实现方案

**1. 草稿机制**
```java
@Entity
public class MetadataDraft {
    private Long id;
    private Long serviceMetadataId;
    private String draftData; // 草稿数据（JSON）
    private String changeDescription; // 变更描述
    private Long creatorId;
    private Timestamp createTime;
    private String status; // DRAFT, PENDING_REVIEW, APPROVED, REJECTED
}
```

**2. 预览验证**
```java
@Service
public class MetadataValidationService {
    
    public ValidationResult validateMetadata(MetadataDraft draft) {
        // 1. 格式验证
        // 2. 类型验证
        // 3. 兼容性验证
        // 4. 依赖验证
        return validationResult;
    }
}
```

**3. 灰度发布**
```java
@Service
public class MetadataReleaseService {
    
    @Transactional
    public void releaseMetadata(Long draftId, ReleaseStrategy strategy) {
        // 1. 创建版本快照
        MetadataVersion version = createVersionSnapshot(draftId);
        
        // 2. 灰度发布（小流量）
        if (strategy == ReleaseStrategy.GRAY) {
            releaseToGray(version);
        } else {
            // 3. 全量发布
            releaseToProduction(version);
        }
        
        // 4. 同步到Nacos（多版本）
        syncToNacos(version);
    }
}
```

**4. 回滚机制**
```java
@Service
public class MetadataRollbackService {
    
    @Transactional
    public void rollback(Long serviceMetadataId, String targetVersion) {
        // 1. 获取目标版本
        MetadataVersion version = getVersion(serviceMetadataId, targetVersion);
        
        // 2. 恢复元数据
        restoreMetadata(version);
        
        // 3. 同步到Nacos
        syncToNacos(version);
        
        // 4. 记录回滚日志
        logRollback(serviceMetadataId, targetVersion);
    }
}
```

### 2. 元数据修复流程

**安全修复流程**：
```
1. 发现问题
   ↓
2. 创建修复草稿
   ↓
3. 验证修复方案
   ├─ 验证通过 → 4. 灰度修复
   └─ 验证失败 → 返回修复
   ↓
4. 灰度修复（小流量）
   ├─ 验证通过 → 5. 全量修复
   └─ 验证失败 → 6. 回滚
   ↓
5. 全量修复
   ↓
6. 回滚（如有问题）
```

---

## 📦 Nacos多版本管理

### 1. 版本策略

**版本命名规则**：
- 主版本：`v1.0.0`, `v2.0.0`
- 次版本：`v1.1.0`, `v1.2.0`
- 修订版本：`v1.0.1`, `v1.0.2`

**版本并存规则**：
- 同一服务可以注册多个版本到Nacos
- 通过版本号区分
- mcp-router-v3可以根据版本路由

### 2. 版本注册实现

```java
@Service
public class NacosVersionService {
    
    public void registerServiceWithVersion(ServiceMetadata metadata, String version) {
        // 1. 构建MCP服务名称（包含版本）
        String mcpServiceName = buildMcpServiceName(metadata, version);
        
        // 2. 注册到Nacos（版本作为元数据）
        Instance instance = new Instance();
        instance.setIp(metadata.getProviderIp());
        instance.setPort(metadata.getProviderPort());
        
        Map<String, String> nacosMetadata = new HashMap<>();
        nacosMetadata.put("version", version);
        nacosMetadata.put("serviceVersion", metadata.getVersion());
        nacosMetadata.put("mcpServiceName", mcpServiceName);
        
        instance.setMetadata(nacosMetadata);
        
        // 3. 注册（服务组：mcp-server）
        namingService.registerInstance(mcpServiceName, "mcp-server", instance);
        
        // 4. 记录注册信息
        saveNacosRegistry(metadata, mcpServiceName, version);
    }
}
```

### 3. 版本路由规则

**mcp-router-v3路由规则**（无需修改，通过元数据路由）：
- 默认路由到最新版本
- 可以通过版本号指定路由
- 支持灰度路由（按比例）

---

## 🔄 元数据同步与一致性

### 1. 同步策略

**三端同步**：
```
Zookeeper (源数据)
    ↓ 采集（仅已审批服务）
MySQL (元数据存储)
    ↓ 同步
Nacos (服务注册)
```

### 2. 同步方向

#### 2.1 Zookeeper → MySQL（采集同步）

```java
@Service
public class ZkToDbSyncService {
    
    @Scheduled(fixedDelay = 30000) // 每30秒同步一次
    public void syncFromZookeeper() {
        // 1. 获取已审批的服务列表
        List<ServiceApproval> approvals = getApprovedServices();
        
        // 2. 从Zookeeper采集元数据
        for (ServiceApproval approval : approvals) {
            List<ServiceMetadata> zkMetadata = collectFromZk(approval);
            
            // 3. 与数据库对比
            List<ServiceMetadata> dbMetadata = getFromDb(approval.getServiceName());
            
            // 4. 差异检测
            MetadataDiff diff = compareMetadata(zkMetadata, dbMetadata);
            
            // 5. 同步差异
            if (diff.hasChanges()) {
                syncDifferences(diff);
                logSync(approval.getServiceName(), "ZK_TO_DB", diff);
            }
        }
    }
}
```

#### 2.2 MySQL → Nacos（注册同步）

```java
@Service
public class DbToNacosSyncService {
    
    @Scheduled(fixedDelay = 60000) // 每60秒同步一次
    public void syncToNacos() {
        // 1. 获取需要同步的元数据
        List<ServiceMetadata> metadataList = getMetadataToSync();
        
        // 2. 同步到Nacos（多版本）
        for (ServiceMetadata metadata : metadataList) {
            List<String> versions = getVersions(metadata);
            
            for (String version : versions) {
                // 3. 检查Nacos中是否已注册
                boolean exists = checkNacosExists(metadata, version);
                
                if (!exists || needsUpdate(metadata, version)) {
                    // 4. 注册或更新
                    registerOrUpdateToNacos(metadata, version);
                    
                    // 5. 更新同步状态
                    updateSyncStatus(metadata, version, "SYNCED");
                }
            }
        }
    }
}
```

#### 2.3 Nacos → MySQL（反向同步）

```java
@Service
public class NacosToDbSyncService {
    
    @Scheduled(fixedDelay = 120000) // 每2分钟同步一次
    public void syncFromNacos() {
        // 1. 从Nacos获取所有注册的服务
        List<Instance> nacosInstances = getAllNacosInstances();
        
        // 2. 与数据库对比
        for (Instance instance : nacosInstances) {
            ServiceMetadata dbMetadata = getFromDb(instance);
            
            // 3. 如果Nacos有但数据库没有，记录异常
            if (dbMetadata == null) {
                logOrphanedService(instance);
            }
            
            // 4. 如果Nacos状态与数据库不一致，更新数据库
            if (needsUpdate(dbMetadata, instance)) {
                updateFromNacos(dbMetadata, instance);
            }
        }
    }
}
```

### 3. 一致性保障

#### 3.1 事务保障

```java
@Service
@Transactional
public class MetadataConsistencyService {
    
    public void updateMetadataWithConsistency(Long metadataId, MetadataUpdate update) {
        // 1. 开启事务
        // 2. 创建版本快照
        MetadataVersion snapshot = createSnapshot(metadataId);
        
        // 3. 更新数据库
        updateDatabase(metadataId, update);
        
        // 4. 同步到Nacos
        syncToNacos(metadataId, update);
        
        // 5. 验证一致性
        if (!verifyConsistency(metadataId)) {
            // 回滚
            rollbackToSnapshot(snapshot);
            throw new ConsistencyException("一致性验证失败");
        }
    }
}
```

#### 3.2 最终一致性

```java
@Service
public class EventualConsistencyService {
    
    @Scheduled(fixedDelay = 30000)
    public void ensureConsistency() {
        // 1. 检测不一致的数据
        List<Inconsistency> inconsistencies = detectInconsistencies();
        
        // 2. 修复不一致
        for (Inconsistency inconsistency : inconsistencies) {
            repairInconsistency(inconsistency);
        }
    }
    
    private void repairInconsistency(Inconsistency inconsistency) {
        // 根据不一致类型修复
        switch (inconsistency.getType()) {
            case DB_MISSING:
                // 数据库缺失，从Zookeeper恢复
                restoreFromZk(inconsistency);
                break;
            case NACOS_MISSING:
                // Nacos缺失，从数据库注册
                registerToNacos(inconsistency);
                break;
            case DATA_MISMATCH:
                // 数据不匹配，以数据库为准
                syncToNacos(inconsistency);
                break;
        }
    }
}
```

### 4. 冲突解决策略

**优先级规则**：
1. **Zookeeper为源**：Zookeeper的数据是最终来源
2. **数据库为主**：数据库是权威存储
3. **Nacos为镜像**：Nacos是服务注册镜像

**冲突解决流程**：
```
检测到冲突
    ↓
判断冲突类型
    ├─ ZK与DB不一致 → 以ZK为准，更新DB
    ├─ DB与Nacos不一致 → 以DB为准，更新Nacos
    └─ 三端都不一致 → 人工介入
    ↓
记录冲突日志
    ↓
通知管理员
```

---

## 🖥️ Web管理界面设计

### 1. 服务申请页面

**功能**：
- 服务搜索（从Zookeeper搜索）
- 服务申请表单
- 申请历史查看

**界面元素**：
```
┌─────────────────────────────────────┐
│  服务申请                              │
├─────────────────────────────────────┤
│  搜索服务: [____________] [搜索]      │
│                                      │
│  服务列表:                            │
│  ┌────────────────────────────────┐  │
│  │ ☑ com.example.UserService     │  │
│  │   版本: 1.0.0                  │  │
│  │   分组: default                │  │
│  │   [申请]                       │  │
│  └────────────────────────────────┘  │
│                                      │
│  申请原因: [________________]        │
│  [提交申请]                          │
└─────────────────────────────────────┘
```

### 2. 审批管理页面

**功能**：
- 待审批列表
- 审批操作
- 审批历史

**界面元素**：
```
┌─────────────────────────────────────┐
│  审批管理                              │
├─────────────────────────────────────┤
│  待审批 (5)                           │
│  ┌────────────────────────────────┐  │
│  │ 服务: com.example.UserService  │  │
│  │ 申请人: 张三                    │  │
│  │ 申请时间: 2025-01-15 10:00     │  │
│  │ 申请原因: 需要接入MCP服务      │  │
│  │ [通过] [拒绝]                  │  │
│  └────────────────────────────────┘  │
│                                      │
│  已审批 (20)                          │
│  [查看历史]                           │
└─────────────────────────────────────┘
```

### 3. 元数据维护页面

**功能**：
- 元数据列表
- 元数据编辑（草稿机制）
- 版本管理
- 回滚操作

**界面元素**：
```
┌─────────────────────────────────────┐
│  元数据维护                            │
├─────────────────────────────────────┤
│  服务: com.example.UserService       │
│  版本: v1.0.0 [切换版本]              │
│                                      │
│  方法列表:                            │
│  ┌────────────────────────────────┐  │
│  │ getUserById(Long id)           │  │
│  │   参数: id (Long)              │  │
│  │   返回: User                   │  │
│  │   [编辑] [删除]                │  │
│  └────────────────────────────────┘  │
│                                      │
│  [保存草稿] [预览] [提交审核] [发布]  │
│                                      │
│  版本历史:                            │
│  - v1.0.1 (2025-01-15) [回滚]        │
│  - v1.0.0 (2025-01-10) [当前]        │
└─────────────────────────────────────┘
```

### 4. 同步监控页面

**功能**：
- 同步状态监控
- 一致性检查
- 冲突处理

**界面元素**：
```
┌─────────────────────────────────────┐
│  同步监控                              │
├─────────────────────────────────────┤
│  同步状态:                            │
│  ZK → DB: ✅ 正常 (30秒前)           │
│  DB → Nacos: ✅ 正常 (60秒前)        │
│  Nacos → DB: ✅ 正常 (120秒前)       │
│                                      │
│  一致性检查:                          │
│  ✅ 所有服务一致                      │
│                                      │
│  冲突列表:                            │
│  (无冲突)                             │
│                                      │
│  [手动同步] [一致性检查]              │
└─────────────────────────────────────┘
```

---

## ✅ 实施计划

### 第一阶段：核心功能（春节前）
- [ ] 服务审批流程（申请、审批）
- [ ] 元数据采集（仅已审批服务）
- [ ] 基础元数据维护

### 第二阶段：高级功能（春节后）
- [ ] 元数据版本管理
- [ ] 灰度发布机制
- [ ] Nacos多版本管理
- [ ] 同步与一致性保障
- [ ] Web管理界面完善

---

**文档版本**: v1.0.0  
**创建日期**: 2025-01-15  
**最后更新**: 2025-01-15

