# 完成 AiMaintainerService 移除的剩余步骤

## 需要完成的修改

### 1. 修复虚拟项目注册方法（NacosMcpRegistrationService.java 第186-204行）

将：
```java
// 3. 发布配置到Nacos
String serverContent = null;
boolean useMaintainer = false;

// 优先尝试使用 AiMaintainerService
if (aiMaintainer Service != null) {
    useMaintainer = publishMcpServerToNacosUsingMaintainerService(serviceId, mcpServiceName, version, tools);
}

// 如果 AiMaintainerService 不可用或失败，回退到 ConfigService
if (!useMaintainer) {
    serverContent = publishConfigsToNacos(serviceId, mcpServiceName, version, tools, virtualProjectName);
}

// 4. 注册服务实例到Nacos服务列表（使用虚拟项目名称作为 application）
registerInstancesToNacosForAllNodes(mcpServiceName, serviceId, version, tools, providers, virtualProjectName, false, serverContent);

log.info("✅ Successfully registered virtual project MCP service: {} to Nacos (via {})", 
        mcpServiceName, useMaintainer ? "AiMaintainerService" : "ConfigService");
```

替换为：
```java
// 3. 发布到 Nacos MCP 管理（使用 HTTP API）
McpServerBasicInfo serverBasic Info = buildMcpServerBasicInfo(mcpServiceName, version);
McpToolSpecification toolSpec = buildMcpToolSpecification(tools);
McpEndpointSpec endpointSpec = buildMcpEndpointSpec(mcpServiceName);

boolean publishMcpSuccess = nacosMcpHttpApiService.createMcpServer(mcpServiceName, serverBasicInfo, toolSpec, endpointSpec);

// 同时也发布配置到 ConfigService（向后兼容）
String serverContent = publishConfigsToNacos(serviceId, mcpServiceName, version, tools, virtualProjectName);

// 4. 注册服务实例到Nacos服务列表（使用虚拟项目名称作为 application）
registerInstancesToNacosForAllNodes(mcpServiceName, serviceId, version, tools, providers, virtualProjectName, false, serverContent);

log.info("✅ Successfully registered virtual project MCP service: {} to Nacos (HTTP API: {}, ConfigService: OK)", 
        mcpServiceName, publishMcpSuccess ? "SUCCESS" : "FAILED");
```

### 2. 修复第152行的日志语句

将：
```java
log.info("✅ Successfully registered Dubbo MCP service: {} to Nacos (HTTP API: {}, ConfigService: OK)", 
        mcpServiceName, useMaintainer ? "AiMaintainer Service" : "ConfigService");
```

替换为：
```java
log.info("✅ Successfully registered Dubbo MCP service: {} to Nacos (HTTP API: {}, ConfigService: OK)", 
        mcpServiceName, publishMcpSuccess ? "SUCCESS" : "FAILED");
```

### 3. 添加辅助方法（在 `createMcpToolList` 方法之后添加）

在大约第720行（`createMcpToolList` 方法结束后）添加以下三个方法：

```java
/**
 * 构建 MCP Server 基本信息
 */
private McpServerBasicInfo buildMcpServerBasicInfo(String mcpServiceName, String version) {
    McpServerBasicInfo serverBasicInfo = new McpServerBasicInfo();
    serverBasicInfo.setName(mcpServiceName);
    serverBasicInfo.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
    serverBasicInfo.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
    serverBasicInfo.setDescription("Dubbo service converted to MCP: " + mcpServiceName);
    
    // 设置版本详情
    ServerVersionDetail versionDetail = new ServerVersionDetail();
    versionDetail.setVersion(version);
    serverBasicInfo.setVersionDetail(versionDetail);
    
    // 设置远程服务配置
    McpServerRemoteServiceConfig remoteServerConfig = new McpServerRemoteServiceConfig();
    remoteServerConfig.setExportPath("/sse");
    
    McpServiceRef serviceRef = new McpServiceRef();
    serviceRef.setNamespaceId(nacosNamespace != null ? nacosNamespace : "public");
    serviceRef.setGroupName(serviceGroup);
    serviceRef.setServiceName(mcpServiceName);
    remoteServerConfig.setServiceRef(serviceRef);
    
    serverBasicInfo.setRemoteServerConfig(remoteServerConfig);
    
    return serverBasicInfo;
}

/**
 * 构建 MCP 工具规格
 */
private McpToolSpecification buildMcpToolSpecification(List<Map<String, Object>> tools) {
    McpToolSpecification toolSpec = new McpToolSpecification();
    List<McpTool> mcpTools = createMcpToolList(tools);
    toolSpec.setTools(mcpTools);
    return toolSpec;
}

/**
 * 构建 MCP 端点规格
 */
private McpEndpointSpec buildMcpEndpointSpec(String mcpServiceName) {
    McpEndpointSpec endpointSpec = new McpEndpointSpec();
    endpointSpec.setType(AiConstants.Mcp.CMP_ENDPOINT_TYPE_REF);
    
    Map<String, String> endpointData = new HashMap<>();
    endpointData.put("namespaceId", nacosNamespace != null ? nacosNamespace : "public");
    endpointData.put("groupName", serviceGroup);
    endpointData.put("serviceName", mcpServiceName);
    endpointSpec.setData(endpointData);
    
    return endpointSpec;
}
```

## 完成后测试

1. 编译项目：`mvn clean compile -DskipTests`
2. 运行项目：`mvn spring-boot:run`
3. 创建虚拟项目测试MCP注册
4. 检查 Nacos 控制台的"MCP管理"页面是否显示注册的服务

## 预期结果

- ✅ 编译成功
- ✅ 服务启动成功
- ✅ 创建虚拟项目时日志显示 "HTTP API: SUCCESS"
- ✅ Nacos "MCP管理" 页面显示注册的 MCP 服务
- ✅ Nacos "配置管理" 页面显示相关配置（向后兼容）
- ✅ Nacos "服务管理" 页面显示服务实例
