# mcp-server-v6 注册机制分析与实现

## 📋 概述

本文档分析 **mcp-server-v6** 如何生成 MCP Server 并注册到 Nacos，以及在 **zkInfo** 中实现相同能力的方案。

---

## 🔍 mcp-server-v6 注册机制分析

### 1. 核心原理

**mcp-server-v6** 使用 **Spring AI Alibaba** 框架的自动配置机制，在应用启动时自动：

1. **生成MCP Server配置**：从 `@Tool` 注解的方法生成工具列表
2. **发布配置到Nacos配置中心**：创建3个配置项
3. **注册服务实例到Nacos服务列表**：注册为可发现的服务

### 2. 关键组件

#### 2.1 Spring AI Alibaba 自动配置

**依赖**：
```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-nacos-mcp-server</artifactId>
    <version>${spring-ai-alibaba.version}</version>
</dependency>
```

**配置**：
```yaml
spring:
  ai:
    mcp:
      server:
        name: mcp-server-v6
        version: 1.0.1
        type: ASYNC
        sse-message-endpoint: /mcp/message
        sse-endpoint: /sse
        capabilities:
          tool: true
          resource: true
          prompt: true
    alibaba:
      mcp:
        nacos:
          server-addr: 127.0.0.1:8848
          namespace: public
          username: nacos
          password: nacos
          registry:
            enabled: true
            service-group: mcp-server
            service-name: ${spring.application.name}
```

#### 2.2 工具定义（@Tool注解）

```java
@Tool("Get a person by their ID")
public Person getPersonById(@P("Person's ID") Long id) {
    // 实现逻辑
}
```

Spring AI 会自动扫描 `@Tool` 注解的方法，生成 MCP 工具定义。

#### 2.3 自动注册流程

**Spring AI Alibaba** 的 `NacosMcpRegister` 类在应用启动时：

1. **监听 `WebServerInitializedEvent` 事件**
2. **生成工具列表**：从 `ToolCallbackProvider` 获取所有工具
3. **发布配置到Nacos**：
   - `{serverName}-mcp-tools.json` → `mcp-tools` 组
   - `{serverName}-mcp-versions.json` → `mcp-server-versions` 组
   - `{serverName}-mcp-server.json` → `mcp-server` 组
4. **注册服务实例**：注册到 Nacos 服务列表

---

## 📊 Nacos配置格式

### 1. mcp-tools.json（工具配置）

**DataId**: `{serviceId}-{version}-mcp-tools.json`  
**Group**: `mcp-tools`

**格式**：
```json
{
  "tools": [
    {
      "name": "getPersonById",
      "description": "Get a person by their ID",
      "inputSchema": {
        "type": "object",
        "properties": {
          "id": {
            "type": "integer",
            "format": "int64",
            "description": "Person's ID"
          }
        },
        "required": ["id"],
        "additionalProperties": false
      }
    }
  ],
  "toolsMeta": {}
}
```

### 2. mcp-versions.json（版本配置）

**DataId**: `{serviceId}-mcp-versions.json`  
**Group**: `mcp-server-versions`

**格式**：
```json
{
  "id": "02bdea21-6b44-4432-9e8e-16514ebd8cb8",
  "name": "mcp-server-v6",
  "protocol": "mcp-sse",
  "frontProtocol": "mcp-sse",
  "description": "mcp-server-v6",
  "enabled": true,
  "capabilities": ["TOOL"],
  "latestPublishedVersion": "1.0.1",
  "versionDetails": [
    {
      "version": "1.0.1",
      "release_date": "2025-08-06T07:50:31Z"
    }
  ]
}
```

### 3. mcp-server.json（服务器配置）

**DataId**: `{serviceId}-{version}-mcp-server.json`  
**Group**: `mcp-server`

**格式**：
```json
{
  "id": "02bdea21-6b44-4432-9e8e-16514ebd8cb8",
  "name": "mcp-server-v6",
  "protocol": "mcp-sse",
  "frontProtocol": "mcp-sse",
  "description": "mcp-server-v6",
  "versionDetail": {
    "version": "1.0.1",
    "release_date": "2025-08-06T07:50:31Z"
  },
  "remoteServerConfig": {
    "serviceRef": {
      "namespaceId": "public",
      "groupName": "mcp-server",
      "serviceName": "mcp-server-v6"
    },
    "exportPath": "/sse"
  },
  "enabled": true,
  "capabilities": ["TOOL"],
  "toolsDescriptionRef": "02bdea21-6b44-4432-9e8e-16514ebd8cb8-1.0.1-mcp-tools.json"
}
```

### 4. 服务实例注册

**服务名**: `mcp-server-v6`  
**服务组**: `mcp-server`

**实例元数据**：
```java
metadata.put("version", "1.0.1");
metadata.put("sseEndpoint", "/sse");
metadata.put("sseMessageEndpoint", "/mcp/message");
metadata.put("protocol", "mcp-sse");
metadata.put("serverName", "mcp-server-v6");
metadata.put("tools.names", "getPersonById,getAllPersons,...");
metadata.put("server.md5", "{server配置的MD5值}");
```

---

## 🔧 zkInfo 实现方案

### 1. 实现架构

```
Zookeeper (Dubbo服务)
    ↓ 监听服务变化
ZooKeeperService
    ↓ 发现新服务
DubboToMcpAutoRegistrationService
    ↓ 自动注册
NacosMcpRegistrationService
    ↓ 发布配置 + 注册实例
Nacos (配置中心 + 服务注册中心)
```

### 2. 核心实现类

#### 2.1 NacosMcpRegistrationService

**功能**：模拟 mcp-server-v6 的注册机制

**关键方法**：
- `registerDubboServiceAsMcp()` - 注册Dubbo服务为MCP服务
- `publishConfigsToNacos()` - 发布3个配置到Nacos
- `registerInstanceToNacos()` - 注册服务实例

**实现要点**：
1. **服务ID生成**：使用UUID v3（基于名称），确保可重现
2. **MCP服务名称**：格式 `zk-mcp-{interfaceName}-{version}`
3. **工具生成**：从Dubbo方法转换为MCP工具定义
4. **配置格式**：完全按照mcp-server-v6的格式

#### 2.2 DubboToMcpAutoRegistrationService

**功能**：自动注册服务

**关键方法**：
- `handleProviderAdded()` - 处理服务添加事件
- `handleProviderRemoved()` - 处理服务移除事件
- `handleProviderUpdated()` - 处理服务更新事件

**实现要点**：
1. **防重复注册**：使用缓存避免重复注册
2. **防抖机制**：延迟注册，避免频繁注册
3. **异步处理**：使用 `@Async` 异步注册，不阻塞主流程

#### 2.3 ZooKeeperService 集成

**修改点**：
- 在 `handleProviderAdded()` 中调用自动注册
- 在 `handleProviderRemoved()` 中调用自动注销
- 在 `handleProviderUpdated()` 中调用自动更新

---

## 📝 实现细节

### 1. 服务ID生成

```java
private String generateServiceId(String serviceInterface, String version) {
    String key = serviceInterface + ":" + version;
    // 使用UUID v3（基于名称的UUID），确保相同服务总是生成相同的ID
    return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
}
```

**说明**：
- 使用UUID v3确保相同服务总是生成相同的ID
- 与mcp-server-v6的ID生成方式一致

### 2. MCP服务名称构建

```java
private String buildMcpServiceName(String serviceInterface, String version) {
    String normalizedName = serviceInterface
            .replace(".", "-")
            .replace("/", "-")
            .toLowerCase();
    return "zk-mcp-" + normalizedName + "-" + (version != null ? version : "default");
}
```

**说明**：
- 格式：`zk-mcp-{interfaceName}-{version}`
- 示例：`zk-mcp-com-example-userservice-1-0-0`

### 3. 工具生成（从Dubbo方法）

```java
private List<Map<String, Object>> generateMcpTools(List<ProviderInfo> providers) {
    List<Map<String, Object>> tools = new ArrayList<>();
    
    for (ProviderInfo provider : providers) {
        if (provider.getMethods() != null && !provider.getMethods().isEmpty()) {
            String[] methods = provider.getMethods().split(",");
            for (String method : methods) {
                Map<String, Object> tool = new HashMap<>();
                
                // 工具名称：接口名.方法名
                String toolName = provider.getInterfaceName() + "." + method.trim();
                tool.put("name", toolName);
                
                // 工具描述
                tool.put("description", String.format("调用 %s 服务的 %s 方法", 
                        provider.getInterfaceName(), method.trim()));
                
                // 输入参数schema
                Map<String, Object> inputSchema = buildInputSchema();
                tool.put("inputSchema", inputSchema);
                
                tools.add(tool);
            }
        }
    }
    
    return tools;
}
```

**说明**：
- 从ProviderInfo中提取方法列表
- 每个方法转换为一个MCP工具
- 工具名称格式：`接口名.方法名`

### 4. 配置发布

```java
private void publishConfigsToNacos(String serviceId, String mcpServiceName, 
                                   String version, List<Map<String, Object>> tools) 
        throws NacosException {
    
    // 1. 发布 mcp-tools.json
    String toolsDataId = serviceId + "-" + version + "-mcp-tools.json";
    String toolsContent = createToolsConfig(tools);
    configService.publishConfig(toolsDataId, TOOLS_GROUP, toolsContent);
    
    // 2. 发布 mcp-versions.json
    String versionsDataId = serviceId + "-mcp-versions.json";
    String versionsContent = createVersionsConfig(serviceId, mcpServiceName, version);
    configService.publishConfig(versionsDataId, VERSIONS_GROUP, versionsContent);
    
    // 3. 发布 mcp-server.json
    String serverDataId = serviceId + "-" + version + "-mcp-server.json";
    String serverContent = createServerConfig(serviceId, mcpServiceName, version, toolsDataId);
    configService.publishConfig(serverDataId, SERVER_GROUP, serverContent);
}
```

**说明**：
- 完全按照mcp-server-v6的格式创建配置
- 使用相同的DataId命名规则和Group

### 5. 服务实例注册

```java
private void registerInstanceToNacos(String mcpServiceName, String serviceId, 
                                    String version, List<Map<String, Object>> tools) 
        throws NacosException {
    
    Instance instance = new Instance();
    instance.setIp(getLocalIp());
    instance.setPort(serverPort);
    instance.setHealthy(true);
    instance.setEnabled(true);
    instance.setEphemeral(true);
    
    // 设置元数据（与mcp-server-v6一致）
    Map<String, String> metadata = new HashMap<>();
    metadata.put("version", version);
    metadata.put("sseEndpoint", "/sse");
    metadata.put("sseMessageEndpoint", "/mcp/message");
    metadata.put("protocol", "mcp-sse");
    metadata.put("serverName", mcpServiceName);
    metadata.put("serverId", serviceId);
    metadata.put("tools.names", toolNames);
    metadata.put("server.md5", serverConfigMd5);
    
    instance.setMetadata(metadata);
    
    // 注册实例
    namingService.registerInstance(mcpServiceName, serviceGroup, instance);
}
```

**说明**：
- 元数据格式与mcp-server-v6完全一致
- 包含必要的端点信息和工具列表

---

## 🔄 自动注册流程

### 1. 服务发现流程

```
1. ZooKeeperService 监听 Zookeeper
   ↓
2. 发现新的Provider节点
   ↓
3. 解析Provider信息
   ↓
4. 添加到ProviderService
   ↓
5. 触发自动注册
```

### 2. 自动注册流程

```
1. DubboToMcpAutoRegistrationService.handleProviderAdded()
   ↓
2. 防抖检查（延迟注册）
   ↓
3. 获取相同服务的所有Provider
   ↓
4. NacosMcpRegistrationService.registerDubboServiceAsMcp()
   ↓
5. 生成服务ID和MCP服务名称
   ↓
6. 生成工具列表（从Dubbo方法）
   ↓
7. 发布配置到Nacos（3个配置）
   ↓
8. 注册服务实例到Nacos
   ↓
9. 标记为已注册
```

### 3. 服务注销流程

```
1. ZooKeeperService 发现Provider移除
   ↓
2. 从ProviderService移除
   ↓
3. DubboToMcpAutoRegistrationService.handleProviderRemoved()
   ↓
4. 检查是否还有其他Provider
   ↓
5. 如果没有，注销Nacos服务
```

---

## ✅ 实现检查清单

### 配置检查
- [x] Nacos依赖已添加（nacos-client）
- [x] NacosConfig配置类已创建
- [x] application.yml中Nacos配置已添加

### 服务实现检查
- [x] NacosMcpRegistrationService已实现
- [x] 配置格式与mcp-server-v6一致
- [x] 服务实例注册格式一致
- [x] DubboToMcpAutoRegistrationService已实现
- [x] 自动注册机制已集成到ZooKeeperService

### 功能检查
- [x] 自动注册新服务
- [x] 自动注销已移除服务
- [x] 防重复注册机制
- [x] 防抖机制
- [x] 异步处理

---

## 🧪 测试验证

### 1. 验证配置发布

```bash
# 检查Nacos配置中心
curl "http://localhost:8848/nacos/v1/cs/configs?dataId={serviceId}-1.0.0-mcp-tools.json&group=mcp-tools"
curl "http://localhost:8848/nacos/v1/cs/configs?dataId={serviceId}-mcp-versions.json&group=mcp-server-versions"
curl "http://localhost:8848/nacos/v1/cs/configs?dataId={serviceId}-1.0.0-mcp-server.json&group=mcp-server"
```

### 2. 验证服务注册

```bash
# 检查Nacos服务列表
curl "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=zk-mcp-com-example-userservice-1-0-0&groupName=mcp-server"
```

### 3. 验证mcp-router-v3发现

```bash
# 通过mcp-router-v3查询服务
curl "http://localhost:8050/mcp/router/servers"
```

---

## 📚 关键代码文件

1. **NacosMcpRegistrationService.java** - MCP服务注册实现
2. **DubboToMcpAutoRegistrationService.java** - 自动注册服务
3. **ZooKeeperService.java** - Zookeeper监听和事件处理
4. **NacosConfig.java** - Nacos配置类
5. **application.yml** - 配置文件

---

**文档版本**: v1.0.0  
**创建日期**: 2025-01-15  
**最后更新**: 2025-01-15

