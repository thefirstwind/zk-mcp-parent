# zkInfo 纯 Curl 手工验证指南

本文档提供了一组直接可用的 `curl` 命令，用于快速验证 zkInfo 在 Nacos 中的注册状态和配置完整性。

**配置变量 (请根据实际情况修改)**:
- Nacos 地址: `localhost:8848`
- 命名空间 ID: `public` (默认)
- 服务名称: `com.pajk.McpService:1.0.0` (示例，请替换为您实际的服务名)
- 分组: `mcp-server`

---

## 🚀 1. 基础健康检查

验证 Nacos Server 是否在线。

```bash
# 检查 Nacos 控制台健康状态
curl -X GET "http://localhost:8848/nacos/v1/console/health/liveness"
# 预期返回: OK
```

---

## 🔍 2. 验证服务实例及元数据

查看服务实例列表，重点检查元数据中的 `protocol`, `md5`, `sseEndpoint`。

```bash
# 获取服务实例列表 (请替换 serviceName)
curl -X GET "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=mcp-server-v6:1.0.3&groupName=mcp-server&namespaceId=public"
```

**✅ 验证点**:
- `hosts` 数组不为空 (有实例)
- `metadata.protocol` == `mcp-sse`
- `metadata.server.md5` 存在且为 32 位字符串
- `metadata.sseEndpoint` 存在 (例如 `/sse/xxx`)
- `metadata.tools.count` 为数字

---

## 📝 3. 验证配置中心 (Config Service)

zkInfo 会发布三个配置文件：`tools`, `versions`, `server`。这里假设 DataId 遵循标准命名规则。

### 3.1 查找配置列表 (模糊搜索)

```bash
# 模糊搜索包含服务名的配置
curl -X GET "http://localhost:8848/nacos/v1/cs/configs?dataId=com.pajk.McpService&group=&pageNo=1&pageSize=10&search=blur&namespaceId=public"
```

### 3.2 获取具体配置内容

请将下方 `dataId` 替换为上一步查到的实际 ID。

**查看 `mcp-tools.json`**:
```bash
# group=mcp-tools
curl -X GET "http://localhost:8848/nacos/v1/cs/configs?dataId=6faa3f6f-2139-344e-8545-3ce60bfd1f5b-1.0.0-mcp-tools.json&group=mcp-tools&namespaceId=public"
```

**查看 `mcp-versions.json`**:
```bash
# group=mcp-versions
curl -X GET "http://localhost:8848/nacos/v1/cs/configs?dataId=com.pajk.McpService-mcp-versions.json&group=mcp-versions&namespaceId=public"
```

**查看 `mcp-server.json`**:
```bash
# group=mcp-server
curl -X GET "http://localhost:8848/nacos/v1/cs/configs?dataId=com.pajk.McpService:1.0.0-mcp-server.json&group=mcp-server&namespaceId=public"
```

**✅ 验证点**:
- 配置内容不为空
- 内容是合法的 JSON 格式

---

## 🧪 4. 模拟虚拟节点注册 (验证集群能力)

在不启动第二个 zkInfo 实例的情况下，您可以使用此命令向 Nacos 注册一个“虚拟节点”，验证 Nacos 是否能正确展示多实例集群。

```bash
# 注册一个虚拟 IP (192.168.1.200) 到同一服务
# 注意：请将 serviceName 和 serverId 替换为您实际查到的值
curl -X POST "http://localhost:8848/nacos/v1/ns/instance" \
-d "serviceName=com.pajk.McpService:1.0.0" \
-d "groupName=mcp-server" \
-d "ip=192.168.1.200" \
-d "port=20880" \
-d "namespaceId=public" \
-d "ephemeral=true" \
-d 'metadata={"protocol":"mcp-sse","serverId":"6faa3f6f-2139-344e-8545-3ce60bfd1f5b","version":"1.0.0","sseEndpoint":"/sse/virtual","application":"demo-provider","server.md5":"e10adc3949ba59abbe56e057f20f883e","tools.count":"1"}'
```

**验证方法**:
1. 执行上述命令。
2. 刷新 Nacos 控制台的服务详情页。
3. 您应该能看到 **2 个实例**：一个是真实的本地实例，一个是 IP 为 `192.168.1.200` 的虚拟实例。

---

## 🛠️ 5. 虚拟节点全生命周期管理 (zkInfo REST API)

本节使用 **zkInfo 项目自身提供的 RESTful 接口** 来管理虚拟节点。所有操作均通过更直观的 `endpointName` 进行标识。

**接口基础地址**: `http://localhost:9091/api/virtual-projects`

### 5.1 创建虚拟项目 (Create Virtual Project)

创建一个名为 `demo-virtual-project` 的虚拟项目，Endpoint 命名为 `demo-virtual-project`。

```bash
# 创建虚拟项目

curl -X DELETE "http://localhost:9091/api/virtual-projects/virtual-demo-virtual-project"
curl -X DELETE "http://localhost:9091/api/virtual-projects/demo-virtual-project"
curl -X DELETE "http://localhost:9091/api/virtual-projects/virtual-test-virtual-endpoint2512301"



curl -X POST "http://localhost:9091/api/virtual-projects" \
-H "Content-Type: application/json" \
-d '{
    "projectName": "demo-virtual-project2",
    "description": "这是通过 Curl 创建的测试虚拟项目",
    "endpointName": "demo-virtual-project2",
    "services": [
        {
            "serviceInterface": "com.pajk.provider1.service.UserService",
            "version": "1.0.0",
            "serviceGroup": "demo",
            "methods": ["getUserById", "getAllUsers"]
        }
    ]
}'
```




另外还有一个很大的问题就是所有方法注册到 nacos上的时候 ，参数都是0，这个是不对的，通过对依赖的jar包分析 要能预判出来每个方法的出参和入参，以方便LLM组合参数，同时更新到nacos的元数据中，当然我们无法使用dubbo3，只能通过jar包解析或者 pom依赖 拿到这些数据，同时我也创建了页面功能，你要分析一下现在有的前端代码 讲这个功能做的更丝滑。这都是已经做过的功能，你怎么都弄的不好用了。


或者也可以通过解析git的主分支拿到代码信息，这个要能支持 gitlab和github，想办法来实现他，同时我也创建了页面功能，你要分析一下现在有的前端代码 讲这个功能做的更丝滑。


有几个问题要调整
1 虚拟节点过多的问题，请考虑
2 现在我有这么多的表，
`health_check_records`, `mcp_servers`, `routing_logs`, `routing_logs_archive`, `system_config`, `zk_approval_log`, `zk_dubbo_method_parameter`, `zk_dubbo_service`, `zk_dubbo_service_method`, `zk_dubbo_service_node`, `zk_interface_whitelist`, `zk_project`, `zk_project_service`, `zk_service_approval`, `zk_virtual_project_endpoint`
哪些是有用的，哪些是孤岛，请列出来
3 demo-provider，demo-provider2等应用，项目名称不会改变，但是 随着每次代码更新 发布上线，他的IP都会改变，如何处理该问题，老得ip要从可用列表中删掉，不能在用，同时还要更新新的IP到nacos上，请考虑该问题
4 demo-provider的接口会不断更新，如果已经更新到nacos的元数据中如何更新
5 读取一个pom依赖



帮我设计一个流程

比如项目 demo-provider2 的 pom依赖如下：
<groupId>com.zkinfo</groupId>
<artifactId>demo-provider2</artifactId>
<version>1.0.0</version>

存在接口 com.pajk.provider2.service.OrderService
有方法 getOrderById，
入参是 String orderId, String status
出参是：Order

我现在想要创建一个虚拟项目 demo-project3

1 页面设计先明确 pom依赖，
2 解析 pom依赖，查找私有库或者远程库 该jar包的接口信息
3 同时提供git项目地址 
4 用户选择所需要的接口和方法
5 结合 git获取的信息 ，针对方法 getOrderById 补全方法/入参/出参的描述
6 再连接到当前的UI页面，可以人工修改方法和初入参的定义

然后 1 ～6 可以重复，
7 admin审批虚拟节点上线


**✅ 验证点**: 返回 JSON 中包含 `project`, `endpoint` 及 `serviceCount: 1`。


### 5.2 查询虚拟项目详情 (Get Project Detail)

直接通过 `endpointName` 获取项目详情。

```bash
# 获取 demo-virtual-project 的项目详情
curl -X GET "http://localhost:9091/api/virtual-projects/demo-virtual-project2"
```

### 5.3 获取虚拟项目服务详情 (Get Services)

通过 `endpointName` 查看服务列表。

```bash
# 获取 demo-virtual-project 的服务列表
curl -X GET "http://localhost:9091/api/virtual-projects/demo-virtual-project/services"
```

### 5.4 更新服务列表 (Update Services)

通过 `endpointName` 直接添加或移除服务。

```bash
# 更新 demo-virtual-project 的服务列表


curl -X PUT "http://localhost:9091/api/virtual-projects/demo-virtual-project/services" \
-H "Content-Type: application/json" \
-d '{
    "services": [
        {
            "serviceInterface": "com.pajk.provider1.service.UserService",
            "version": "1.0.0",
            "serviceGroup": "demo",
            "methods": ["getUserById", "getAllUsers"]
        },
        {
            "serviceInterface": "com.pajk.provider1.service.OrderService",
            "version": "2.0.0", 
            "serviceGroup": "demo",
            "methods": ["getOrderById"]
        }
    ]
}'
```

### 5.5 查看工具配置预览 (Get Tools)

查看该虚拟项目生成的 MCP 工具配置。

```bash
# 获取 demo-virtual-project 的工具配置
curl -X GET "http://localhost:9091/api/virtual-projects/demo-virtual-project/tools"
```

### 5.6 重新注册虚拟项目 (Reregister)

强制重新向 Nacos 注册该虚拟项目（刷新元数据）。

```bash
# 重新注册 demo-virtual-project
curl -X POST "http://localhost:9091/api/virtual-projects/demo-virtual-project/reregister"
```

### 5.7 删除虚拟项目 (Delete Project)

通过 `endpointName` 删除整个虚拟项目，并清理 Nacos 中的注册信息。

```bash
# 删除 endpointName 为 demo-virtual-project 的虚拟项目
curl -X DELETE "http://localhost:9091/api/virtual-projects/demo-virtual-project"
```

---

## 🛑 6. 模拟服务下线 (清理验证)

**警告**: 此操作将删除服务实例，仅建议在测试环境执行。

```bash
# 下线实例 (需替换 ip 和 port 为实际值)
curl -X DELETE "http://localhost:8848/nacos/v1/ns/instance?serviceName=com.pajk.McpService:1.0.0&groupName=mcp-server&ip=192.168.1.x&port=20880&namespaceId=public"
# 预期返回: ok
```

---

## 🛠️ 5. 调试技巧

如果遇到问题，可以查看 Nacos 的详细服务信息：

```bash
# 查看服务详情 (包含保护阈值等信息)
curl -X GET "http://localhost:8848/nacos/v1/ns/service?serviceName=com.pajk.McpService:1.0.0&groupName=mcp-server&namespaceId=public"
```
