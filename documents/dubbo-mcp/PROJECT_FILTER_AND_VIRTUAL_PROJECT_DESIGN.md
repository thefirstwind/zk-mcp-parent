# 项目过滤与虚拟项目设计方案

## 📋 概述

针对公司内部上万个Dubbo接口的性能问题和服务编排需求，设计项目级过滤机制和虚拟项目/服务编排方案。

---

## 🎯 核心需求

1. **性能优化**：上万个接口一次性灌入影响性能，需要过滤
2. **项目级管理**：以项目为大颗粒单位，service + version 为最小粒度
3. **虚拟项目**：实现服务编排，根据不同场景组合不同服务
4. **Endpoint集成**：虚拟项目对应 mcp-router-v3 的 endpoint 节点

---

## 🏗️ 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Zookeeper (上万个服务)                     │
└────────────────────┬────────────────────────────────────────┘
                     │ 过滤（仅采集项目包含的服务）
┌────────────────────▼────────────────────────────────────────┐
│          zk-mcp-parent (项目过滤与服务编排)                 │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ 1. 项目管理                                          │  │
│  │    - 项目定义（大颗粒）                              │  │
│  │    - 服务关联（service + version）                   │  │
│  │ 2. 虚拟项目管理                                      │  │
│  │    - 虚拟项目定义                                    │  │
│  │    - 服务编排（跨项目组合）                          │  │
│  │ 3. 过滤机制                                          │  │
│  │    - 项目级过滤                                      │  │
│  │    - 服务级过滤                                      │  │
│  │ 4. Endpoint映射                                      │  │
│  │    - 虚拟项目 → MCP Endpoint                         │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────┬────────────────────────────────────────┘
                     │ 注册（按虚拟项目/Endpoint）
┌────────────────────▼────────────────────────────────────────┐
│                    Nacos (MCP服务注册中心)                    │
│  - 虚拟项目作为独立的MCP服务注册                            │
│  - Endpoint名称对应虚拟项目名称                             │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│          mcp-router-v3 (路由层)                            │
│  - /sse/{virtualProjectName} → 路由到虚拟项目              │
└─────────────────────────────────────────────────────────────┘
```

---

## 📊 数据模型设计

### 1. project（项目表）

```sql
CREATE TABLE project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_code VARCHAR(100) NOT NULL UNIQUE COMMENT '项目代码（唯一标识）',
    project_name VARCHAR(200) NOT NULL COMMENT '项目名称',
    project_type VARCHAR(20) DEFAULT 'REAL' COMMENT '项目类型：REAL（实际项目）, VIRTUAL（虚拟项目）',
    description TEXT COMMENT '项目描述',
    owner_id BIGINT COMMENT '项目负责人ID',
    owner_name VARCHAR(100) COMMENT '项目负责人姓名',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE, INACTIVE, DELETED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project_code (project_code),
    INDEX idx_project_type (project_type),
    INDEX idx_status (status)
) COMMENT '项目表（实际项目+虚拟项目）';
```

### 2. project_service（项目服务关联表）

```sql
CREATE TABLE project_service (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL COMMENT '项目ID',
    service_interface VARCHAR(500) NOT NULL COMMENT '服务接口（完整路径）',
    service_version VARCHAR(50) NOT NULL COMMENT '服务版本',
    service_group VARCHAR(100) COMMENT '服务分组',
    priority INT DEFAULT 0 COMMENT '优先级（虚拟项目中用于排序）',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    added_by BIGINT COMMENT '添加人ID',
    UNIQUE KEY uk_project_service_version (project_id, service_interface, service_version),
    INDEX idx_project_id (project_id),
    INDEX idx_service_interface (service_interface),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) COMMENT '项目服务关联表（service + version为最小粒度）';
```

### 3. virtual_project_endpoint（虚拟项目Endpoint映射表）

```sql
CREATE TABLE virtual_project_endpoint (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    virtual_project_id BIGINT NOT NULL COMMENT '虚拟项目ID',
    endpoint_name VARCHAR(200) NOT NULL COMMENT 'Endpoint名称（对应mcp-router-v3的serviceName）',
    endpoint_path VARCHAR(500) COMMENT 'Endpoint路径（如：/sse/{endpointName}）',
    mcp_service_name VARCHAR(255) COMMENT 'MCP服务名称（注册到Nacos的名称）',
    description TEXT COMMENT 'Endpoint描述',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE, INACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_endpoint_name (endpoint_name),
    UNIQUE KEY uk_virtual_project_id (virtual_project_id),
    INDEX idx_status (status),
    FOREIGN KEY (virtual_project_id) REFERENCES project(id) ON DELETE CASCADE
) COMMENT '虚拟项目Endpoint映射表';
```

### 4. service_collection_filter（服务采集过滤规则表）

```sql
CREATE TABLE service_collection_filter (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    filter_type VARCHAR(20) NOT NULL COMMENT '过滤类型：PROJECT（项目级）, SERVICE（服务级）, PATTERN（模式匹配）',
    filter_value VARCHAR(500) NOT NULL COMMENT '过滤值',
    filter_operator VARCHAR(20) DEFAULT 'INCLUDE' COMMENT '操作符：INCLUDE（包含）, EXCLUDE（排除）',
    priority INT DEFAULT 0 COMMENT '优先级（数字越大优先级越高）',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    description TEXT COMMENT '过滤规则描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_filter_type (filter_type),
    INDEX idx_enabled (enabled),
    INDEX idx_priority (priority)
) COMMENT '服务采集过滤规则表';
```

### 5. 更新 service_approval（服务审批表）

```sql
-- 添加项目关联字段
ALTER TABLE service_approval 
ADD COLUMN project_id BIGINT COMMENT '关联项目ID',
ADD COLUMN virtual_project_id BIGINT COMMENT '关联虚拟项目ID（如果通过虚拟项目申请）',
ADD INDEX idx_project_id (project_id),
ADD INDEX idx_virtual_project_id (virtual_project_id);
```

---

## 🔧 核心功能设计

### 1. 项目级过滤机制

#### 1.1 过滤策略

**三层过滤机制**：

1. **项目级过滤**（第一层）
   - 只采集已定义项目包含的服务
   - 通过 `project_service` 表关联

2. **服务级过滤**（第二层）
   - 通过 `service_collection_filter` 表配置过滤规则
   - 支持包含/排除模式

3. **审批级过滤**（第三层）
   - 只有审批通过的服务才会被采集
   - 与原有的审批流程结合

#### 1.2 过滤实现

```java
@Service
public class ServiceCollectionFilterService {
    
    /**
     * 判断服务是否应该被采集
     */
    public boolean shouldCollect(String serviceInterface, String version) {
        // 1. 检查是否在已定义的项目中
        if (!isInDefinedProjects(serviceInterface, version)) {
            return false;
        }
        
        // 2. 检查过滤规则
        if (isFilteredOut(serviceInterface, version)) {
            return false;
        }
        
        // 3. 检查审批状态
        if (!isApproved(serviceInterface, version)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查服务是否在已定义的项目中
     */
    private boolean isInDefinedProjects(String serviceInterface, String version) {
        // 查询 project_service 表
        return projectServiceMapper.existsByServiceAndVersion(serviceInterface, version);
    }
    
    /**
     * 检查是否被过滤规则排除
     */
    private boolean isFilteredOut(String serviceInterface, String version) {
        List<ServiceCollectionFilter> filters = filterMapper.findEnabledFilters();
        
        for (ServiceCollectionFilter filter : filters) {
            boolean matches = matchesFilter(serviceInterface, version, filter);
            if (matches) {
                return filter.getFilterOperator().equals("EXCLUDE");
            }
        }
        
        return false;
    }
}
```

#### 1.3 Zookeeper监听优化

```java
@Service
public class OptimizedZooKeeperService {
    
    private final ServiceCollectionFilterService filterService;
    
    /**
     * 优化的服务监听（仅监听项目包含的服务）
     */
    private void watchServiceProviders(String servicePath, String serviceName) {
        // 1. 先检查服务是否在项目中
        if (!isServiceInProjects(serviceName)) {
            log.debug("服务 {} 不在任何项目中，跳过监听", serviceName);
            return;
        }
        
        // 2. 监听服务变化
        CuratorCache cache = CuratorCache.build(client, servicePath);
        
        cache.listenable().addListener((type, oldData, data) -> {
            if (type == Type.NODE_CREATED && data != null) {
                String providerUrl = extractProviderUrl(data);
                ProviderInfo providerInfo = parseProviderUrl(providerUrl, serviceName);
                
                // 3. 应用过滤规则
                if (filterService.shouldCollect(
                        providerInfo.getServiceName(), 
                        providerInfo.getVersion())) {
                    handleProviderAdded(data, serviceName);
                } else {
                    log.debug("服务 {}/{} 被过滤规则排除", 
                            providerInfo.getServiceName(), providerInfo.getVersion());
                }
            }
        });
        
        cache.start();
    }
    
    /**
     * 检查服务是否在任何项目中
     */
    private boolean isServiceInProjects(String serviceName) {
        // 快速检查：查询是否有项目包含此服务（不检查版本）
        return projectServiceMapper.existsByServiceInterface(serviceName);
    }
}
```

### 2. 虚拟项目与服务编排

#### 2.1 虚拟项目概念

**虚拟项目**：
- 不直接对应实际的业务项目
- 可以根据不同场景组合不同实际项目的服务
- 对应 mcp-router-v3 的一个 endpoint
- 实现服务编排功能

**使用场景**：
- **场景A**：数据分析场景，需要组合用户服务、订单服务、商品服务
- **场景B**：报表场景，需要组合订单服务、支付服务、物流服务
- **场景C**：AI对话场景，需要组合所有可用的服务

#### 2.2 虚拟项目创建

```java
@Service
@Transactional
public class VirtualProjectService {
    
    /**
     * 创建虚拟项目
     */
    public VirtualProject createVirtualProject(CreateVirtualProjectRequest request) {
        // 1. 创建项目记录（类型为VIRTUAL）
        Project project = new Project();
        project.setProjectCode("VIRTUAL_" + UUID.randomUUID().toString().substring(0, 8));
        project.setProjectName(request.getName());
        project.setProjectType("VIRTUAL");
        project.setDescription(request.getDescription());
        projectMapper.insert(project);
        
        // 2. 关联服务（从不同实际项目中选择）
        for (ServiceSelection selection : request.getServices()) {
            ProjectService projectService = new ProjectService();
            projectService.setProjectId(project.getId());
            projectService.setServiceInterface(selection.getServiceInterface());
            projectService.setServiceVersion(selection.getVersion());
            projectService.setPriority(selection.getPriority());
            projectServiceMapper.insert(projectService);
        }
        
        // 3. 创建Endpoint映射
        VirtualProjectEndpoint endpoint = new VirtualProjectEndpoint();
        endpoint.setVirtualProjectId(project.getId());
        endpoint.setEndpointName(request.getEndpointName());
        endpoint.setMcpServiceName("mcp-" + request.getEndpointName());
        endpointMapper.insert(endpoint);
        
        // 4. 注册到Nacos（作为独立的MCP服务）
        registerVirtualProjectToNacos(project, endpoint);
        
        return buildVirtualProject(project, endpoint);
    }
}
```

#### 2.3 服务编排

```java
@Service
public class ServiceOrchestrationService {
    
    /**
     * 为虚拟项目编排服务
     */
    public void orchestrateServices(Long virtualProjectId, 
                                    List<ServiceOrchestrationRule> rules) {
        VirtualProject virtualProject = getVirtualProject(virtualProjectId);
        
        // 1. 根据规则选择服务
        List<ProjectService> selectedServices = selectServicesByRules(rules);
        
        // 2. 更新虚拟项目的服务列表
        updateVirtualProjectServices(virtualProjectId, selectedServices);
        
        // 3. 重新注册到Nacos（更新工具列表）
        reregisterVirtualProjectToNacos(virtualProject);
    }
    
    /**
     * 根据规则选择服务
     */
    private List<ProjectService> selectServicesByRules(
            List<ServiceOrchestrationRule> rules) {
        List<ProjectService> services = new ArrayList<>();
        
        for (ServiceOrchestrationRule rule : rules) {
            switch (rule.getType()) {
                case BY_PROJECT:
                    // 选择指定项目的所有服务
                    services.addAll(getServicesByProject(rule.getProjectId()));
                    break;
                case BY_PATTERN:
                    // 按模式匹配选择服务
                    services.addAll(getServicesByPattern(rule.getPattern()));
                    break;
                case BY_TAG:
                    // 按标签选择服务
                    services.addAll(getServicesByTag(rule.getTag()));
                    break;
            }
        }
        
        return services;
    }
}
```

### 3. Endpoint映射与mcp-router-v3集成

#### 3.1 Endpoint命名规则

**Endpoint名称**：
- 对应 mcp-router-v3 的 `serviceName` 参数
- 格式：`/sse/{endpointName}`
- 示例：`/sse/data-analysis`, `/sse/report-generation`

#### 3.2 MCP服务注册

```java
@Service
public class VirtualProjectRegistrationService {
    
    /**
     * 将虚拟项目注册为MCP服务到Nacos
     */
    public void registerVirtualProjectToNacos(Project virtualProject, 
                                               VirtualProjectEndpoint endpoint) {
        // 1. 获取虚拟项目包含的所有服务
        List<ProjectService> projectServices = 
                projectServiceMapper.findByProjectId(virtualProject.getId());
        
        // 2. 聚合所有服务的工具（tools）
        List<McpTool> aggregatedTools = aggregateTools(projectServices);
        
        // 3. 构建MCP服务信息
        McpServerInfo mcpServerInfo = McpServerInfo.builder()
                .name(endpoint.getMcpServiceName())
                .ip(getLocalIp())
                .port(9091)
                .version("1.0.0")
                .serviceGroup("mcp-server")
                .metadata(Map.of(
                    "endpointName", endpoint.getEndpointName(),
                    "virtualProjectId", String.valueOf(virtualProject.getId()),
                    "tools.count", String.valueOf(aggregatedTools.size())
                ))
                .build();
        
        // 4. 注册到Nacos
        nacosNamingService.registerInstance(
                endpoint.getMcpServiceName(),
                "mcp-server",
                buildNacosInstance(mcpServerInfo)
        );
        
        // 5. 记录注册信息
        saveNacosRegistry(virtualProject, endpoint, mcpServerInfo);
    }
    
    /**
     * 聚合所有服务的工具
     */
    private List<McpTool> aggregateTools(List<ProjectService> projectServices) {
        List<McpTool> tools = new ArrayList<>();
        
        for (ProjectService projectService : projectServices) {
            // 从元数据中获取服务的工具列表
            List<McpTool> serviceTools = getToolsFromMetadata(
                    projectService.getServiceInterface(),
                    projectService.getServiceVersion()
            );
            tools.addAll(serviceTools);
        }
        
        return tools;
    }
}
```

#### 3.3 mcp-router-v3路由

**无需修改 mcp-router-v3**，虚拟项目注册后自动可路由：

```
客户端请求: GET /sse/data-analysis
    ↓
mcp-router-v3: 查找 serviceName = "data-analysis"
    ↓
Nacos: 返回对应的MCP服务实例
    ↓
zk-mcp-parent: 接收请求，路由到虚拟项目包含的服务
    ↓
返回聚合的工具列表
```

---

## 🖥️ Web管理界面设计

### 1. 项目管理页面

**功能**：
- 项目列表（实际项目 + 虚拟项目）
- 项目创建
- 项目服务关联管理

**界面元素**：
```
┌─────────────────────────────────────┐
│  项目管理                              │
├─────────────────────────────────────┤
│  [创建项目] [创建虚拟项目]            │
│                                      │
│  项目列表:                            │
│  ┌────────────────────────────────┐  │
│  │ 📦 用户中心项目 (REAL)         │  │
│  │   服务数: 15                   │  │
│  │   [管理服务] [查看详情]        │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ 🎭 数据分析场景 (VIRTUAL)      │  │
│  │   Endpoint: data-analysis      │  │
│  │   服务数: 8                    │  │
│  │   [编排服务] [查看详情]        │  │
│  └────────────────────────────────┘  │
└─────────────────────────────────────┘
```

### 2. 虚拟项目编排页面

**功能**：
- 服务选择（从不同项目选择）
- 服务编排规则配置
- Endpoint配置

**界面元素**：
```
┌─────────────────────────────────────┐
│  虚拟项目编排                          │
├─────────────────────────────────────┤
│  项目名称: [数据分析场景]            │
│  Endpoint: [data-analysis]          │
│                                      │
│  服务编排规则:                        │
│  ┌────────────────────────────────┐  │
│  │ 规则1: 包含"用户中心项目"所有服务│  │
│  │ 规则2: 包含"订单服务"v1.0.0    │  │
│  │ 规则3: 排除"测试服务"          │  │
│  │ [+ 添加规则]                   │  │
│  └────────────────────────────────┘  │
│                                      │
│  已选服务 (8个):                      │
│  - com.example.UserService:v1.0.0   │
│  - com.example.OrderService:v1.0.0  │
│  ...                                 │
│                                      │
│  [预览工具列表] [保存] [注册到Nacos] │
└─────────────────────────────────────┘
```

### 3. 过滤规则管理页面

**功能**：
- 过滤规则配置
- 规则优先级管理
- 规则测试

**界面元素**：
```
┌─────────────────────────────────────┐
│  过滤规则管理                          │
├─────────────────────────────────────┤
│  [添加规则]                          │
│                                      │
│  规则列表:                            │
│  ┌────────────────────────────────┐  │
│  │ 规则1: 项目级 - 包含"用户中心"  │  │
│  │   优先级: 10                   │  │
│  │   状态: ✅ 启用                │  │
│  │   [编辑] [删除]                │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ 规则2: 服务级 - 排除"test.*"   │  │
│  │   优先级: 5                    │  │
│  │   状态: ✅ 启用                │  │
│  │   [编辑] [删除]                │  │
│  └────────────────────────────────┘  │
└─────────────────────────────────────┘
```

---

## 📈 性能优化方案

### 1. 分层过滤

**三层过滤减少采集量**：
1. **项目级过滤**：只监听项目包含的服务（减少90%+的监听）
2. **服务级过滤**：应用过滤规则（进一步减少）
3. **审批级过滤**：只采集审批通过的服务（最终过滤）

### 2. 延迟加载

**按需加载服务元数据**：
- 启动时只加载项目列表
- 服务元数据按需加载（当服务被访问时）
- 使用缓存减少重复查询

### 3. 批量处理

**批量采集和注册**：
- 批量采集服务元数据
- 批量注册到Nacos
- 使用异步处理避免阻塞

---

## ✅ 实施计划

### 第一阶段：项目过滤机制（春节前）
- [ ] 数据库表设计（project, project_service, service_collection_filter）
- [ ] 项目管理功能（创建、关联服务）
- [ ] 过滤机制实现（三层过滤）
- [ ] Zookeeper监听优化（仅监听项目服务）

### 第二阶段：虚拟项目与服务编排（春节后）
- [ ] 虚拟项目功能（创建、管理）
- [ ] 服务编排功能（规则配置、服务选择）
- [ ] Endpoint映射（虚拟项目 → MCP Endpoint）
- [ ] Nacos注册（虚拟项目作为独立MCP服务）
- [ ] Web管理界面（项目管理、编排、过滤规则）

---

## 🔍 可行性评估

### ✅ 方案可行性

1. **项目级过滤**：✅ 完全可行
   - 通过数据库关联实现
   - 性能提升明显（减少90%+的监听）
   - 实现简单，风险低

2. **虚拟项目/服务编排**：✅ 完全可行
   - 符合mcp-router-v3的endpoint概念
   - 可以实现服务编排
   - 灵活组合不同项目的服务

3. **Endpoint集成**：✅ 完全可行
   - mcp-router-v3支持通过serviceName路由
   - 虚拟项目注册为独立的MCP服务
   - 无需修改mcp-router-v3

### ⚠️ 注意事项

1. **数据一致性**：确保项目服务关联与Zookeeper数据一致
2. **性能监控**：监控过滤后的采集性能
3. **虚拟项目管理**：避免虚拟项目过多导致管理复杂

---

**文档版本**: v1.0.0  
**创建日期**: 2025-01-15  
**最后更新**: 2025-01-15


