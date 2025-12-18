# 复杂对象参数处理方案总结

**实现日期**: 2025-12-17  
**状态**: ✅ 核心功能已实现

---

## 🎯 问题

在 MCP tools/call 时，如何正确处理非 Java 基础类型的参数（如 Order、User、Product）？

**挑战**:
- MCP 请求参数是 JSON Map 格式
- Dubbo 泛化调用需要 Java 对象或明确的参数类型
- 需要支持嵌套对象（如 Order.orderItems）
- 需要区分 Dubbo2 和 Dubbo3 的不同处理方式

---

## ✅ 解决方案

### 核心组件

1. **ParameterConverter** - 参数转换器
   - 将 JSON Map 转换为 Java POJO 对象
   - 支持嵌套对象、List、Map 等复杂结构
   - 使用 Jackson ObjectMapper 进行转换

2. **MethodSignatureResolver** - 方法签名解析器
   - 从数据库获取方法签名信息
   - 缓存方法签名，提高性能
   - 支持方法签名推断（fallback）

3. **McpExecutorService** - 已更新
   - 集成 ParameterConverter 和 MethodSignatureResolver
   - 检测 Dubbo 版本并选择处理策略
   - 支持复杂对象参数转换

4. **McpToolSchemaGenerator** - 已更新
   - 改进方法签名推断（支持 POJO 类型识别）
   - 在 extractMethodParameters 中使用 ParameterConverter

---

## 🔧 实现细节

### 1. 参数类型识别策略（优先级从高到低）

1. **从数据库获取**（最准确）
   - 从 `DubboServiceMethodEntity` 和 `DubboMethodParameterEntity` 获取
   - 包含完整的参数类型信息

2. **从方法名推断**（较准确）
   - `createUser` → `com.zkinfo.demo.model.User`
   - `createOrder` → `com.zkinfo.demo.model.Order`
   - `createProduct` → `com.zkinfo.demo.model.Product`

3. **从 Map 键推断**（fallback）
   - 包含 `username` + `email` → `User`
   - 包含 `userId` + `status` + `orderItems` → `Order`
   - 包含 `name` + `price` + `category` → `Product`

### 2. 嵌套对象处理

**Order.orderItems 示例**:
```json
{
  "order": {
    "userId": 1001,
    "orderItems": [
      {
        "productId": 1,
        "quantity": 2
      }
    ]
  }
}
```

**处理流程**:
1. 识别 `order` 为 `com.zkinfo.demo.model.Order` 类型
2. 识别 `orderItems` 为 `List<Order.OrderItem>` 类型
3. 转换每个 `orderItems` 元素为 `Order.OrderItem` 对象
4. 使用 Jackson ObjectMapper 转换为 `Order` 对象

### 3. Dubbo2 vs Dubbo3 处理差异

#### Dubbo2
```java
// 需要明确指定参数类型
String[] parameterTypes = {"com.zkinfo.demo.model.Order"};
Object[] args = {convertedOrder};
genericService.$invoke("createOrder", parameterTypes, args);
```

#### Dubbo3
```java
// 支持 POJO 模式，parameterTypes 可以为 null
Object[] args = {convertedOrder};
genericService.$invoke("createOrder", null, args);
```

**检测方式**:
1. 从 `ProviderInfo.parameters` 获取 `dubbo` 版本
2. 从协议判断（`tri`/`triple` → Dubbo3）
3. 默认：Dubbo2

---

## 📝 使用示例

### 示例 1: 创建 User

**MCP Request**:
```json
{
  "method": "tools/call",
  "params": {
    "name": "com.zkinfo.demo.service.UserService.createUser",
    "arguments": {
      "user": {
        "username": "testuser",
        "email": "test@example.com",
        "age": 25
      }
    }
  }
}
```

**处理结果**:
- `user` Map → `com.zkinfo.demo.model.User` 对象
- 调用 `UserService.createUser(User user)`

### 示例 2: 创建 Order（嵌套对象）

**MCP Request**:
```json
{
  "method": "tools/call",
  "params": {
    "name": "com.zkinfo.demo.service.OrderService.createOrder",
    "arguments": {
      "order": {
        "userId": 1001,
        "orderItems": [
          {
            "productId": 1,
            "quantity": 2
          }
        ]
      }
    }
  }
}
```

**处理结果**:
- `order` Map → `com.zkinfo.demo.model.Order` 对象
- `orderItems` List<Map> → `List<Order.OrderItem>`
- 调用 `OrderService.createOrder(Order order)`

---

## 🧪 测试

运行测试脚本：
```bash
cd zk-mcp-parent/zkInfo
./scripts/test-complex-object-parameters.sh
```

**测试用例**:
1. ✅ 创建 User（简单 POJO）
2. ✅ 创建 Order（嵌套对象，包含 orderItems）
3. ✅ 创建 Product（简单 POJO）
4. ✅ 更新 Order（嵌套对象）
5. ✅ 批量创建 Users（List<User>）

---

## 📚 相关文件

### 新增文件
- `src/main/java/com/zkinfo/util/ParameterConverter.java` - 参数转换器
- `src/main/java/com/zkinfo/util/MethodSignatureResolver.java` - 方法签名解析器
- `scripts/test-complex-object-parameters.sh` - 测试脚本
- `docs/COMPLEX_OBJECT_PARAMETER_DESIGN.md` - 设计方案文档
- `docs/COMPLEX_OBJECT_PARAMETER_IMPLEMENTATION.md` - 实现总结文档
- `docs/COMPLEX_OBJECT_PARAMETER_SUMMARY.md` - 方案总结文档

### 修改文件
- `src/main/java/com/zkinfo/service/McpExecutorService.java` - 集成新组件
- `src/main/java/com/zkinfo/util/McpToolSchemaGenerator.java` - 改进方法签名推断

---

## 🎯 关键特性

✅ **已完成**:
- 基础类型转换
- POJO 对象转换（User、Order、Product）
- 嵌套对象转换（Order.orderItems）
- 集合类型转换（List<User>）
- Dubbo2/Dubbo3 兼容处理
- 方法签名推断（从方法名）
- Map 键推断（fallback）

⏭️ **待完善**:
- MethodSignatureResolver 的数据库查询实现（TODO）
- 更多 POJO 类型的识别规则
- 性能优化和缓存策略

---

## 🔍 技术要点

1. **类型转换**: 使用 Jackson ObjectMapper，支持嵌套对象和集合类型
2. **方法签名**: 优先从数据库获取，fallback 到方法名推断
3. **Dubbo 兼容**: 自动检测版本并选择处理策略
4. **错误处理**: 转换失败时返回原始 Map，让 Dubbo 处理

---

## 📊 数据流

```
MCP Request (JSON)
    ↓
McpMessageController.handleToolCall()
    ↓
McpToolSchemaGenerator.extractMethodParameters()
    ├─ MethodSignatureResolver.getMethodSignature()
    ├─ 提取参数值
    └─ ParameterConverter.convertToJavaObject()
    ↓
McpExecutorService.executeToolCallSync()
    ├─ detectDubboVersion()
    ├─ convertParameters()
    └─ getParameterTypes()
    ↓
Dubbo GenericService.$invoke()
    ├─ Dubbo2: parameterTypes + args
    └─ Dubbo3: null + args (POJO 模式)
```

---

## 🚀 下一步

1. ⏭️ 完善 MethodSignatureResolver 的数据库查询逻辑
2. ⏭️ 添加更多 POJO 类型的识别规则
3. ⏭️ 性能优化（缓存优化）
4. ⏭️ 添加单元测试

