# mcp-router-v3 集成方案（零修改原则）

## 📋 概述

本文档说明如何将 **zk-mcp-parent** 与 **mcp-router-v3** 集成，**核心原则：不修改 mcp-router-v3 代码**，通过标准接口和配置实现集成。

---

## 🎯 集成目标

1. **零修改 mcp-router-v3**：不修改任何 mcp-router-v3 代码
2. **标准格式注册**：zk-mcp-parent 注册到 Nacos 的服务格式与 mcp-router-v3 兼容
3. **自动发现和路由**：mcp-router-v3 自动发现并路由到 zk-mcp-parent 服务

---

## 🏗️ 集成架构

```
Zookeeper (Dubbo注册中心)
    ↓ 监听
zk-mcp-parent (实现标准MCP协议)
    ↓ 注册（标准格式，兼容mcp-router-v3）
Nacos (服务注册中心)
    ↓ 自动发现（mcp-router-v3零修改）
mcp-router-v3 (路由层)
    ↓ 路由
MCP客户端
```

---

## 🔧 集成实现方案

### 1. 服务注册格式兼容

#### 1.1 服务组设置
```yaml
# zk-mcp-parent/zkInfo/src/main/resources/application.yml
nacos:
  registry:
    service-group: mcp-server  # 与mcp-router-v3使用相同的服务组
```

#### 1.2 服务元数据格式
```java
// zk-mcp-parent 注册服务时的元数据格式（与mcp-router-v3兼容）
Map<String, String> metadata = new HashMap<>();
metadata.put("version", "1.0.0");
metadata.put("sseEndpoint", "/sse");
metadata.put("type", "mcp-server");
metadata.put("capabilities", "tools,resources,prompts");
```

#### 1.3 Nacos服务注册实现
```java
package com.zkinfo.service;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class McpRegistrationService {
    
    @Autowired
    private NamingService namingService;
    
    /**
     * 注册MCP服务到Nacos（格式兼容mcp-router-v3）
     */
    public void registerMcpService(String serviceName, String ip, int port) {
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        instance.setHealthy(true);
        instance.setEnabled(true);
        instance.setEphemeral(true);
        
        // 设置元数据（与mcp-router-v3兼容）
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "1.0.0");
        metadata.put("sseEndpoint", "/sse");
        metadata.put("type", "mcp-server");
        
        instance.setMetadata(metadata);
        
        // 注册到Nacos（服务组：mcp-server）
        try {
            namingService.registerInstance(serviceName, "mcp-server", instance);
        } catch (Exception e) {
            log.error("Failed to register MCP service", e);
        }
    }
}
```

---

### 2. MCP协议标准实现

#### 2.1 实现标准MCP端点

```java
package com.zkinfo.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mcp")
public class McpProtocolController {
    
    /**
     * MCP initialize 端点（标准格式）
     */
    @PostMapping("/initialize")
    public Map<String, Object> initialize(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");
        result.put("capabilities", Map.of(
            "tools", Map.of("listChanged", false),
            "resources", Map.of("subscribe", false, "listChanged", false),
            "prompts", Map.of("listChanged", false)
        ));
        result.put("serverInfo", Map.of(
            "name", "zk-mcp-parent",
            "version", "1.0.0"
        ));
        return result;
    }
    
    /**
     * MCP tools/list 端点（标准格式）
     */
    @PostMapping("/tools/list")
    public Map<String, Object> listTools() {
        // 返回所有可用的工具（从Dubbo服务转换而来）
        List<Map<String, Object>> tools = mcpConverterService.getAllTools();
        return Map.of("tools", tools);
    }
    
    /**
     * MCP tools/call 端点（标准格式）
     */
    @PostMapping("/tools/call")
    public Map<String, Object> callTool(@RequestBody Map<String, Object> request) {
        String toolName = (String) request.get("name");
        Map<String, Object> arguments = (Map<String, Object>) request.get("arguments");
        
        // 执行Dubbo泛化调用
        Object result = mcpExecutorService.executeToolCall(toolName, arguments);
        
        return Map.of(
            "content", List.of(Map.of(
                "type", "text",
                "text", result.toString()
            ))
        );
    }
}
```

#### 2.2 端点路径配置

```yaml
# zk-mcp-parent/zkInfo/src/main/resources/application.yml
server:
  port: 9091

# MCP协议端点路径（标准格式）
mcp:
  endpoints:
    initialize: /mcp/initialize
    tools-list: /mcp/tools/list
    tools-call: /mcp/tools/call
```

---

### 3. 健康检查端点

#### 3.1 实现健康检查端点

```java
package com.zkinfo.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/actuator")
public class HealthController {
    
    /**
     * 健康检查端点（Spring Boot Actuator标准格式）
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("components", Map.of(
            "zookeeper", checkZookeeper(),
            "nacos", checkNacos(),
            "database", checkDatabase()
        ));
        return health;
    }
    
    private Map<String, Object> checkZookeeper() {
        // 检查Zookeeper连接
        return Map.of("status", "UP");
    }
    
    private Map<String, Object> checkNacos() {
        // 检查Nacos连接
        return Map.of("status", "UP");
    }
    
    private Map<String, Object> checkDatabase() {
        // 检查数据库连接
        return Map.of("status", "UP");
    }
}
```

#### 3.2 健康检查配置

```yaml
# zk-mcp-parent/zkInfo/src/main/resources/application.yml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

---

### 4. 服务发现兼容

#### 4.1 服务名称规范

```java
// zk-mcp-parent 服务名称规范（与mcp-router-v3兼容）
// 格式：zk-mcp-{dubbo-service-name}
String mcpServiceName = "zk-mcp-" + 
    dubboServiceName.replace(".", "-").replace("/", "-");
```

#### 4.2 服务组设置

```java
// 必须使用与mcp-router-v3相同的服务组
String serviceGroup = "mcp-server";
```

---

## ✅ 集成验证

### 验证步骤

1. **启动 zk-mcp-parent**
   ```bash
   cd zk-mcp-parent/zkInfo
   mvn spring-boot:run
   ```

2. **检查 Nacos 服务列表**
   ```bash
   curl "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=zk-mcp-xxx&groupName=mcp-server"
   ```

3. **启动 mcp-router-v3**（无需修改）
   ```bash
   cd mcp-router-v3
   mvn spring-boot:run
   ```

4. **验证服务发现**
   ```bash
   # 通过mcp-router-v3查询服务列表
   curl "http://localhost:8050/mcp/router/servers"
   ```

5. **测试路由功能**
   ```bash
   # 通过mcp-router-v3路由到zk-mcp-parent
   curl -X POST "http://localhost:8050/mcp/router/route/zk-mcp-xxx" \
     -H "Content-Type: application/json" \
     -d '{
       "jsonrpc": "2.0",
       "id": "1",
       "method": "tools/list",
       "params": {}
     }'
   ```

---

## 🔍 集成检查清单

### 服务注册检查
- [ ] zk-mcp-parent 能够连接到 Nacos
- [ ] 服务组设置为 `mcp-server`（与mcp-router-v3一致）
- [ ] 服务元数据格式与 mcp-router-v3 兼容
- [ ] 服务成功注册到 Nacos

### 服务发现检查
- [ ] mcp-router-v3 能够发现 zk-mcp-parent 注册的服务（无需修改）
- [ ] 服务列表正确显示
- [ ] 服务健康状态正确

### 路由检查
- [ ] mcp-router-v3 能够路由请求到 zk-mcp-parent（无需修改）
- [ ] MCP 协议格式正确
- [ ] 请求能够正确转换为 Dubbo 调用
- [ ] 响应能够正确返回

### 健康检查检查
- [ ] zk-mcp-parent 提供健康检查端点（/actuator/health）
- [ ] mcp-router-v3 能够检查服务健康状态（无需修改）
- [ ] 健康状态正确更新

---

## 🐛 常见问题

### Q1: mcp-router-v3 无法发现 zk-mcp-parent 注册的服务

**原因**: 
- 服务组不一致
- Nacos 连接配置错误
- 服务信息格式不正确

**解决方案**:
1. 确保服务组都设置为 `mcp-server`
2. 检查 Nacos 连接配置
3. 验证服务信息格式（version, sseEndpoint等元数据）

### Q2: 路由请求失败

**原因**:
- MCP 协议格式不正确
- 端点路径不匹配
- 服务未正确启动

**解决方案**:
1. 检查 MCP 协议格式是否符合标准（2024-11-05）
2. 验证端点路径是否正确（/mcp/initialize, /mcp/tools/list等）
3. 检查服务健康状态

### Q3: 健康检查失败

**原因**:
- 健康检查端点未实现
- 端点路径不正确
- 服务内部错误

**解决方案**:
1. 实现标准的健康检查端点（/actuator/health）
2. 检查端点路径配置
3. 检查服务内部状态

---

## 📚 参考资料

- [MCP 协议规范](https://spec.modelcontextprotocol.io/)
- [Nacos 文档](https://nacos.io/docs/latest/)
- mcp-router-v3 项目代码（参考格式，不修改）

---

**文档版本**: v2.0.0  
**创建日期**: 2025-01-15  
**最后更新**: 2025-01-15
