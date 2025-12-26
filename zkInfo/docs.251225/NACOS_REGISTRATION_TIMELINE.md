# zkInfo 项目 Nacos 注册时机排查文档

## 📋 注册时机总览

zkInfo 项目中有 **3 个主要时机**会将服务注册到 Nacos：

1. **应用启动时** - 批量注册所有已发现的 Dubbo 服务
2. **ZooKeeper 监听事件** - 实时监听并自动注册新服务
3. **虚拟项目创建时** - 手动创建虚拟项目时注册

---

## 1️⃣ 应用启动时注册（批量注册）

### 触发时机
- **事件**: `ApplicationReadyEvent`（Spring Boot 应用完全启动后）
- **位置**: `DubboToMcpRegistrationService.registerAllServicesOnStartup()`
- **注解**: `@EventListener(ApplicationReadyEvent.class)`

### 代码位置
```java
// 文件: DubboToMcpRegistrationService.java
@EventListener(ApplicationReadyEvent.class)
public void registerAllServicesOnStartup() {
    if (!registryEnabled) {
        log.info("Nacos registry is disabled, skip auto registration");
        return;
    }

    log.info("🚀 Starting to register all Dubbo services as MCP services...");
    
    // 获取所有应用
    List<ApplicationInfo> applications = providerService.getAllApplications();
    
    // 按服务接口分组
    Map<String, List<ProviderInfo>> servicesByInterface = new HashMap<>();
    for (ApplicationInfo app : applications) {
        for (ProviderInfo provider : app.getProviders()) {
            String key = provider.getInterfaceName() + ":" + 
                         (provider.getVersion() != null ? provider.getVersion() : "default");
            servicesByInterface.computeIfAbsent(key, k -> new ArrayList<>()).add(provider);
        }
    }

    // 批量注册
    for (Map.Entry<String, List<ProviderInfo>> entry : servicesByInterface.entrySet()) {
        String[] parts = entry.getKey().split(":");
        String serviceInterface = parts[0];
        String version = parts.length > 1 ? parts[1] : "default";
        
        try {
            registerDubboServiceAsMcp(serviceInterface, version, entry.getValue());
        } catch (Exception e) {
            log.error("Failed to register service: {}:{}", serviceInterface, version, e);
        }
    }

    log.info("✅ Completed registering {} services to Nacos", servicesByInterface.size());
}
```

### 执行流程
1. Spring Boot 应用完全启动后触发 `ApplicationReadyEvent`
2. `DubboToMcpRegistrationService` 监听该事件
3. 从 `ProviderService` 获取所有已发现的 Dubbo 服务
4. 按接口名和版本分组
5. 批量调用 `registerDubboServiceAsMcp()` 注册到 Nacos

### 日志标识
```
🚀 Starting to register all Dubbo services as MCP services...
✅ Completed registering X services to Nacos
```

---

## 2️⃣ ZooKeeper 监听事件（实时注册）

### 触发时机
- **事件**: ZooKeeper 中 Provider 节点变化（新增、更新、删除）
- **位置**: `ZooKeeperService.handleProviderAdded()` → `DubboToMcpAutoRegistrationService.handleProviderAdded()`
- **监听**: `CuratorCache` 监听 ZooKeeper 节点变化

### 代码位置

#### 2.1 ZooKeeper 监听
```java
// 文件: ZooKeeperService.java
private void handleProviderAdded(ChildData data, String serviceName) {
    try {
        String providerUrl = URLDecoder.decode(
                data.getPath().substring(data.getPath().lastIndexOf('/') + 1),
                StandardCharsets.UTF_8
        );
        
        log.info("Provider添加: {} -> {}", serviceName, providerUrl);
        
        ProviderInfo providerInfo = parseProviderUrl(providerUrl, serviceName);
        if (providerInfo != null) {
            providerInfo.setZkPath(data.getPath());
            providerService.addProvider(providerInfo);
            
            // 自动注册到Nacos（如果启用）
            if (autoRegistrationService != null) {
                autoRegistrationService.handleProviderAdded(providerInfo);
            }
        }
    } catch (Exception e) {
        log.error("处理Provider添加事件失败", e);
    }
}
```

#### 2.2 自动注册服务处理
```java
// 文件: DubboToMcpAutoRegistrationService.java
@Async
public void handleProviderAdded(ProviderInfo providerInfo) {
    if (!autoRegisterEnabled) {
        log.debug("Auto registration is disabled, skip");
        return;
    }
    
    try {
        String serviceKey = buildServiceKey(providerInfo);
        
        // 检查是否已注册
        if (registeredServices.contains(serviceKey)) {
            log.debug("Service already registered: {}", serviceKey);
            return;
        }
        
        // 防抖：延迟注册，避免频繁注册（默认 5 秒）
        long currentTime = System.currentTimeMillis();
        Long lastPendingTime = pendingRegistrations.get(serviceKey);
        
        if (lastPendingTime != null && (currentTime - lastPendingTime) < autoRegisterDelay) {
            log.debug("Service registration pending, skip: {}", serviceKey);
            return;
        }
        
        pendingRegistrations.put(serviceKey, currentTime);
        
        // 延迟注册（默认 5 秒）
        Thread.sleep(autoRegisterDelay);
        
        // 再次检查是否已注册（可能在延迟期间已注册）
        if (registeredServices.contains(serviceKey)) {
            pendingRegistrations.remove(serviceKey);
            return;
        }
        
        // 应用三层过滤机制：检查服务是否应该被采集
        if (!filterService.shouldCollect(
                providerInfo.getInterfaceName(),
                providerInfo.getVersion(),
                providerInfo.getGroup())) {
            log.debug("Service {}/{} filtered out by filter service, skip registration", 
                    providerInfo.getInterfaceName(), providerInfo.getVersion());
            pendingRegistrations.remove(serviceKey);
            return;
        }
        
        // 获取该服务的所有Provider（相同接口、版本、分组）
        List<ProviderInfo> sameServiceProviders = getSameServiceProviders(providerInfo);
        
        // 去重：避免重复的方法
        sameServiceProviders = deduplicateProviders(sameServiceProviders);
        
        if (sameServiceProviders.isEmpty()) {
            log.warn("No providers found for service: {}", serviceKey);
            pendingRegistrations.remove(serviceKey);
            return;
        }
        
        // 注册到Nacos
        String version = providerInfo.getVersion() != null ? providerInfo.getVersion() : "1.0.0";
        nacosMcpRegistrationService.registerDubboServiceAsMcp(
                providerInfo.getInterfaceName(),
                version,
                sameServiceProviders
        );
        
        // 标记为已注册
        registeredServices.add(serviceKey);
        pendingRegistrations.remove(serviceKey);
        
        log.info("✅ Auto registered service to Nacos: {}:{}", 
                providerInfo.getInterfaceName(), version);
        
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Registration interrupted: {}", providerInfo.getInterfaceName());
    } catch (Exception e) {
        log.error("Failed to auto register service: {}", providerInfo.getInterfaceName(), e);
        pendingRegistrations.remove(buildServiceKey(providerInfo));
    }
}
```

### 执行流程
1. ZooKeeper 中 Provider 节点发生变化（新增、更新、删除）
2. `CuratorCache` 监听器触发事件
3. `ZooKeeperService.handleProviderAdded()` 解析 Provider URL
4. 调用 `DubboToMcpAutoRegistrationService.handleProviderAdded()`
5. **防抖机制**：延迟 5 秒（可配置）后注册，避免频繁注册
6. **过滤检查**：应用三层过滤机制（项目级、服务级、审批级）
7. 获取相同服务的所有 Provider，去重后注册到 Nacos

### 配置参数
```yaml
nacos:
  registry:
    auto-register: true  # 启用自动注册
    auto-register-delay: 5000  # 延迟注册时间（毫秒），默认 5 秒
```

### 日志标识
```
Provider添加: {serviceName} -> {providerUrl}
✅ Auto registered service to Nacos: {interfaceName}:{version}
```

---

## 3️⃣ 虚拟项目创建时注册（手动注册）

### 触发时机
- **事件**: 通过 API 创建虚拟项目
- **位置**: `VirtualProjectController.createVirtualProject()` → `VirtualProjectRegistrationService.registerVirtualProjectToNacos()`
- **API**: `POST /api/virtual-projects`

### 代码位置
```java
// 文件: VirtualProjectRegistrationService.java
public void registerVirtualProjectToNacos(Project virtualProject, VirtualProjectEndpoint endpoint) {
    try {
        log.info("🚀 Registering virtual project as MCP service: {} -> {}", 
                virtualProject.getProjectName(), endpoint.getEndpointName());
        
        // 1. 获取虚拟项目包含的所有服务
        List<ProjectService> projectServices = projectManagementService.getProjectServices(virtualProject.getId());
        
        if (projectServices.isEmpty()) {
            log.warn("⚠️ Virtual project {} has no services, skip registration", 
                    virtualProject.getProjectName());
            return;
        }
        
        // 2. 聚合所有服务的Provider和工具
        List<ProviderInfo> aggregatedProviders = aggregateProviders(projectServices);
        
        if (aggregatedProviders.isEmpty()) {
            log.warn("⚠️ Virtual project {} has no available providers, skip registration", 
                    virtualProject.getProjectName());
            return;
        }
        
        // 3. 使用NacosMcpRegistrationService注册虚拟项目
        nacosMcpRegistrationService.registerVirtualProjectAsMcp(
                endpoint.getMcpServiceName(),
                "1.0.0", // 虚拟项目统一使用1.0.0版本
                aggregatedProviders
        );
        
        log.info("✅ Successfully registered virtual project to Nacos: {} ({} services, {} providers)", 
                endpoint.getEndpointName(), projectServices.size(), aggregatedProviders.size());
        
    } catch (Exception e) {
        log.error("❌ Failed to register virtual project to Nacos: {}", 
                virtualProject.getProjectName(), e);
        throw new RuntimeException("Failed to register virtual project to Nacos", e);
    }
}
```

### 执行流程
1. 用户通过 API 创建虚拟项目
2. `VirtualProjectController.createVirtualProject()` 处理请求
3. `VirtualProjectService.createVirtualProject()` 创建虚拟项目
4. `VirtualProjectRegistrationService.registerVirtualProjectToNacos()` 注册到 Nacos
5. 聚合虚拟项目包含的所有服务的 Provider
6. 调用 `NacosMcpRegistrationService.registerVirtualProjectAsMcp()` 注册

### 日志标识
```
🚀 Registering virtual project as MCP service: {projectName} -> {endpointName}
✅ Successfully registered virtual project to Nacos: {endpointName} ({services} services, {providers} providers)
```

---

## 🔍 注册流程总结

### 注册方法调用链

#### 普通 Dubbo 服务注册
```
ApplicationReadyEvent / ZooKeeper Event
  ↓
DubboToMcpRegistrationService.registerAllServicesOnStartup()
  OR
DubboToMcpAutoRegistrationService.handleProviderAdded()
  ↓
NacosMcpRegistrationService.registerDubboServiceAsMcp()
  ↓
NacosMcpRegistrationService.registerInstanceToNacos()
  ↓
namingService.registerInstance()  // 实际注册到 Nacos
```

#### 虚拟项目注册
```
POST /api/virtual-projects
  ↓
VirtualProjectController.createVirtualProject()
  ↓
VirtualProjectService.createVirtualProject()
  ↓
VirtualProjectRegistrationService.registerVirtualProjectToNacos()
  ↓
NacosMcpRegistrationService.registerVirtualProjectAsMcp()
  ↓
NacosMcpRegistrationService.registerInstanceToNacos()
  ↓
namingService.registerInstance()  // 实际注册到 Nacos
```

---

## ⚙️ 配置控制

### 启用/禁用注册
```yaml
nacos:
  registry:
    enabled: true  # 总开关：是否启用 Nacos 注册
    auto-register: true  # 自动注册开关：是否自动注册新发现的服务
    auto-register-delay: 5000  # 延迟注册时间（毫秒）
```

### 过滤机制
注册前会应用三层过滤机制（`ServiceCollectionFilterService.shouldCollect()`）：
1. **项目级过滤**：服务是否在已定义的项目中
2. **服务级过滤**：服务是否匹配过滤规则
3. **审批级过滤**：服务是否已通过审批

---

## 📝 日志排查指南

### 查看启动时注册
```bash
grep "Starting to register all Dubbo services" zkinfo.log
grep "Completed registering.*services to Nacos" zkinfo.log
```

### 查看实时注册
```bash
grep "Provider添加" zkinfo.log
grep "Auto registered service to Nacos" zkinfo.log
```

### 查看虚拟项目注册
```bash
grep "Registering virtual project as MCP service" zkinfo.log
grep "Successfully registered virtual project to Nacos" zkinfo.log
```

### 查看注册失败
```bash
grep "Failed to register" zkinfo.log
grep "filtered out by filter service" zkinfo.log
```

---

## 🐛 常见问题排查

### 1. 服务未注册到 Nacos
- **检查配置**：确认 `nacos.registry.enabled=true` 和 `nacos.registry.auto-register=true`
- **检查日志**：查看是否有注册相关的日志
- **检查过滤**：确认服务是否被过滤规则排除
- **检查延迟**：如果是实时注册，等待 5 秒延迟时间

### 2. 服务重复注册
- **检查缓存**：`registeredServices` 缓存是否正常工作
- **检查并发**：是否有多个线程同时注册同一服务

### 3. 虚拟项目未注册
- **检查服务列表**：确认虚拟项目是否包含服务
- **检查 Provider**：确认虚拟项目包含的服务是否有可用的 Provider
- **检查日志**：查看是否有 "no available providers" 警告

---

## 📅 注册时机时间线

```
应用启动
  ↓
ZooKeeper 连接建立
  ↓
监听已存在的 Provider（加载历史数据）
  ↓
ApplicationReadyEvent 触发
  ↓
批量注册所有已发现的 Dubbo 服务（时机 1）
  ↓
ZooKeeper 监听器激活
  ↓
新 Provider 出现 → 延迟 5 秒 → 注册（时机 2）
  ↓
虚拟项目创建 → 立即注册（时机 3）
```

---

## 📌 关键文件位置

| 文件 | 说明 |
|------|------|
| `DubboToMcpRegistrationService.java` | 启动时批量注册 |
| `DubboToMcpAutoRegistrationService.java` | 实时自动注册 |
| `VirtualProjectRegistrationService.java` | 虚拟项目注册 |
| `NacosMcpRegistrationService.java` | 实际注册逻辑 |
| `ZooKeeperService.java` | ZooKeeper 监听和事件触发 |

