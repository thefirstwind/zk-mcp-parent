---
description: 添加新 Dubbo 服务提供者的标准化流程
---
# 添加新 Dubbo 服务提供者工作流

本工作流用于在 `zk-mcp-parent` 项目中添加新的 Dubbo 服务提供者，以便自动转换为 MCP 工具。

## 前置条件

- 项目已正常运行
- zkInfo 已连接 ZooKeeper 和 Nacos
- 了解 Dubbo 服务开发基础

## 工作流步骤

### 阶段 1: 需求分析 (Requirements Analysis)

#### 1.1 明确服务功能
- **输入**: 用户描述的功能需求
- **输出**: 清晰的 Dubbo 接口定义
- **检查点**:
  - [ ] 服务名称已定义（如 `com.example.UserService`）
  - [ ] 主要方法已列举（至少 2 个方法）
  - [ ] 方法参数和返回值已明确

#### 1.2 参考现有实现
```bash
# 查看现有 Dubbo 服务示例
ls -la demo-provider*/
cat demo-provider/src/main/java/com/example/demo/api/DemoService.java
```
- **检查点**:
  - [ ] 已查看至少 1 个现有服务
  - [ ] 理解 Dubbo 服务接口规范

---

### 阶段 2: 接口设计 (Interface Design)

#### 2.1 创建服务接口
**包结构**:
```
com.example.{domain}.api
├── {Service}Interface.java
└── model/
    ├── {Request}.java
    └── {Response}.java
```

**接口规范**:
```java
package com.example.{domain}.api;

/**
 * {服务描述}
 */
public interface {Service}Interface {
    
    /**
     * {方法描述}
     * 
     * @param param 参数描述
     * @return 返回值描述
     */
    Response methodName(Request param);
}
```

- **检查点**:
  - [ ] 接口有完整的 Javadoc 注释
  - [ ] 方法签名符合 Dubbo 规范
  - [ ] 参数和返回值是可序列化的

---

### 阶段 3: 实现服务 (Implementation)

#### 3.1 创建服务提供者模块
```bash
# 复制现有模块作为模板
cp -r demo-provider demo-provider-{name}
```

#### 3.2 修改 pom.xml
```xml
<artifactId>demo-provider-{name}</artifactId>
<name>Demo Provider - {Name}</name>
<description>{服务描述}</description>

<dependencies>
    <!-- Dubbo -->
    <dependency>
        <groupId>org.apache.dubbo</groupId>
        <artifactId>dubbo-spring-boot-starter</artifactId>
    </dependency>
    
    <!-- Nacos Registry -->
    <dependency>
        <groupId>com.alibaba.nacos</groupId>
        <artifactId>nacos-client</artifactId>
    </dependency>
</dependencies>
```

#### 3.3 实现服务类
**规范要点**:
1. 使用 `@DubboService` 注解
2. 使用 Lombok `@Slf4j` 进行日志记录
3. 完整的异常处理

**代码模板**:
```java
package com.example.{domain}.provider;

import com.example.{domain}.api.{Service}Interface;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * {服务实现描述}
 */
@Slf4j
@DubboService(
    version = "1.0.0",
    group = "default",
    timeout = 5000
)
public class {Service}Impl implements {Service}Interface {
    
    @Override
    public Response methodName(Request request) {
        log.info("🚀 调用 methodName, 参数: {}", request);
        
        try {
            // 业务逻辑实现
            Response response = new Response();
            // ... 处理逻辑
            
            log.info("✅ methodName 执行成功");
            return response;
            
        } catch (Exception e) {
            log.error("❌ methodName 执行失败", e);
            throw new RuntimeException("执行失败: " + e.getMessage());
        }
    }
}
```

#### 3.4 配置 application.yml
```yaml
server:
  port: 20XXX  # 选择未使用的端口

spring:
  application:
    name: {service-name}-provider

dubbo:
  application:
    name: ${spring.application.name}
    qos-enable: false
  
  protocol:
    name: dubbo
    port: 20XXX
  
  registry:
    address: zookeeper://localhost:2181
    # 或使用 Nacos
    # address: nacos://localhost:8848
```

- **检查点**:
  - [ ] 端口号不冲突
  - [ ] 注册中心配置正确
  - [ ] Dubbo 版本号已配置

---

### 阶段 4: 注册到 zkInfo (Registration)

#### 4.1 启动服务提供者
// turbo
```bash
cd demo-provider-{name}
mvn spring-boot:run
```

- **检查点**:
  - [ ] 应用成功启动
  - [ ] 日志显示 Dubbo 服务已注册到 ZooKeeper
  - [ ] 端口正常监听

#### 4.2 验证 ZooKeeper 注册
```bash
# 连接 ZooKeeper 查看注册节点
zkCli.sh -server localhost:2181
ls /dubbo/com.example.{domain}.api.{Service}Interface/providers
```

- **检查点**:
  - [ ] 服务节点已创建
  - [ ] 提供者 URL 包含完整的方法列表

#### 4.3 验证 zkInfo 自动发现
启动 zkInfo 后，检查日志：
```
✅ Discovered Dubbo service: com.example.{domain}.api.{Service}Interface:1.0.0
🚀 Registering Dubbo service as MCP: zk-mcp-{service}-1.0.0
✅ Successfully registered MCP service: zk-mcp-{service}-1.0.0 to Nacos
```

- **检查点**:
  - [ ] zkInfo 发现了新服务
  - [ ] 服务已注册到 Nacos
  - [ ] 元数据包含所有必需字段

---

### 阶段 5: 元数据管理 (Metadata Management)

#### 5.1 持久化接口元数据到数据库
zkInfo 会自动持久化以下信息：
- 服务接口信息（`dubbo_service`表）
- 方法信息（`dubbo_service_method`表）
- 参数信息（`dubbo_method_parameter`表）

#### 5.2 验证元数据
```sql
-- 查看服务
SELECT * FROM dubbo_service 
WHERE interface_name = 'com.example.{domain}.api.{Service}Interface';

-- 查看方法
SELECT * FROM dubbo_service_method WHERE service_id = ?;

-- 查看参数
SELECT * FROM dubbo_method_parameter WHERE method_id = ?;
```

#### 5.3 补充参数描述（可选）
如果自动生成的描述不够清晰，可以手动更新：
```sql
UPDATE dubbo_method_parameter 
SET parameter_description = '更清晰的描述'
WHERE id = ?;
```

---

### 阶段 6: 测试 MCP 工具调用 (Testing)

#### 6.1 查看 Nacos 服务列表
访问 Nacos 控制台：http://localhost:8848/nacos
- 进入"服务管理" → "服务列表"
- 查找 `zk-mcp-{service}-1.0.0`
- 检查元数据和实例信息

#### 6.2 测试工具调用
通过 mcp-ai-client 或直接调用：
```bash
curl -X POST http://localhost:9091/mcp/tools/call \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "com.example.{domain}.api.{Service}Interface.methodName",
    "arguments": {
      "param": "value"
    }
  }'
```

- **检查点**:
  - [ ] 工具调用成功
  - [ ] 返回结果符合预期
  - [ ] zkInfo 日志显示泛化调用过程

---

### 阶段 7: 文档化 (Documentation)

#### 7.1 更新项目 README
在 `zk-mcp-parent/README.md` 中添加：
```markdown
### 新增服务: {Service}

- **接口**: `com.example.{domain}.api.{Service}Interface`
- **版本**: 1.0.0
- **功能**: {功能描述}
- **方法**:
  - `methodName`: {方法描述}
```

#### 7.2 创建服务文档
在 `demo-provider-{name}/README.md` 中详细说明：
- 服务功能
- 接口定义
- 使用示例
- 配置说明

---

## 输出物清单

完成本工作流后，应产出：

- [x] Dubbo 服务接口定义
- [x] 服务实现代码
- [x] 单元测试（覆盖率 > 60%）
- [x] 服务注册到 ZooKeeper
- [x] zkInfo 自动发现并注册到 Nacos
- [x] 元数据持久化到数据库
- [x] MCP 工具调用测试通过
- [x] 文档更新

---

## 常见问题

### Q1: 如何选择 Dubbo 端口号？
**A**: 查看现有端口分配：
```bash
grep -r "dubbo.protocol.port" demo-provider*/src/main/resources/
```
选择未使用的 20XXX 端口。

### Q2: zkInfo 没有发现新服务怎么办？
**A**: 检查以下几点：
1. 服务是否成功注册到 ZooKeeper（`zkCli.sh` 查看）
2. zkInfo 的 ZooKeeper 连接配置是否正确
3. zkInfo 的定时任务日志是否正常

### Q3: 元数据 InputSchema 不正确怎么办？
**A**: 
1. 检查数据库中的参数信息是否正确
2. 手动更新 `dubbo_method_parameter` 表
3. 重新注册服务（重启 zkInfo 或调用注册接口）

### Q4: 如何添加复杂参数的 Schema？
**A**: 在数据库中更新 `parameter_schema_json` 字段：
```json
{
  "required": true,
  "jsonSchema": {
    "type": "object",
    "properties": {
      "field1": {"type": "string", "description": "字段1"},
      "field2": {"type": "integer", "description": "字段2"}
    },
    "required": ["field1"]
  }
}
```

---

## 参考资源

- Dubbo 官方文档: https://dubbo.apache.org/
- zkInfo 优化文档: `zkInfo/README_OPTIMIZATION.md`
- 项目开发规范: `.agent/rules/PROJECT_RULES.md`

---

**工作流版本**: 1.0  
**最后更新**: 2026-02-09  
**适用项目**: zk-mcp-parent
