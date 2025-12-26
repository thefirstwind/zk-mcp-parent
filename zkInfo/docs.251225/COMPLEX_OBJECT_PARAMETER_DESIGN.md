# 复杂对象参数处理设计方案

**设计日期**: 2025-12-17  
**目标**: 在 MCP tools/call 时正确处理非 Java 基础类型的参数（如 Order、User、Product）

---

## 📋 问题分析

### 当前问题

1. **类型推断不准确**: 仅通过 Map 的键来推断对象类型，容易误判
2. **缺少方法签名信息**: 无法获取准确的参数类型（如 `com.zkinfo.demo.model.User`）
3. **不支持嵌套对象**: 无法处理嵌套对象、List、Map 等复杂结构
4. **Dubbo2/Dubbo3 差异**: 未区分 Dubbo2 和 Dubbo3 的不同处理方式

### 示例场景

```java
// 接口方法签名
public Order createOrder(Order order);
public List<User> getUsersByCondition(User condition);
public Product updateProduct(Long id, Product product);
```

**MCP tools/call 请求**:
```json
{
  "method": "tools/call",
  "params": {
    "name": "com.zkinfo.demo.service.OrderService.createOrder",
    "arguments": {
      "order": {
        "userId": 1001,
        "status": "PENDING",
        "totalAmount": 199.99,
        "orderItems": [
          {
            "productId": 1,
            "productName": "Product A",
            "price": 99.99,
            "quantity": 2
          }
        ]
      }
    }
  }
}
```

---

## 🎯 设计方案

### 1. 架构设计

```
MCP tools/call Request (JSON)
    ↓
McpMessageController.handleToolCall()
    ↓
McpToolSchemaGenerator.extractMethodParameters()
    ↓ (获取方法签名)
DubboServiceMethodService.getMethodSignature()
    ↓ (根据参数类型转换)
ParameterConverter.convertToJavaObject()
    ↓
McpExecutorService.executeToolCallSync()
    ↓
Dubbo GenericService.$invoke()
```

### 2. 核心组件

#### 2.1 ParameterConverter（参数转换器）

**职责**:
- 根据参数类型将 JSON Map 转换为 Java 对象
- 支持嵌套对象、List、Map 等复杂结构
- 区分 Dubbo2 和 Dubbo3 的处理方式

**关键方法**:
```java
public Object convertToJavaObject(Object value, String targetType, String dubboVersion)
```

#### 2.2 MethodSignatureResolver（方法签名解析器）

**职责**:
- 从数据库获取方法签名信息（DubboServiceMethodEntity）
- 缓存方法签名信息，提高性能
- 支持方法签名推断（fallback）

**关键方法**:
```java
public MethodSignature getMethodSignature(String interfaceName, String methodName)
```

#### 2.3 DubboVersionDetector（Dubbo 版本检测器）

**职责**:
- 检测 Dubbo 版本（2.x 或 3.x）
- 根据版本选择不同的参数处理策略

---

## 🔧 实现方案

### 方案 1: 基于数据库方法签名（推荐）

**优点**:
- 准确：从数据库获取真实的参数类型信息
- 可靠：不依赖推断，避免误判

**实现步骤**:
1. 从 `DubboServiceMethodEntity` 和 `DubboMethodParameterEntity` 获取方法签名
2. 根据参数类型将 JSON Map 转换为对应的 Java 对象
3. 使用 Jackson ObjectMapper 进行转换

### 方案 2: 基于 ZooKeeper Metadata（备选）

**优点**:
- 实时：直接从注册中心获取
- 无需数据库查询

**缺点**:
- 需要解析 metadata 格式
- 可能缺少详细的参数类型信息

### 方案 3: 基于方法名模式推断（当前方案，需改进）

**优点**:
- 简单：不需要额外数据源

**缺点**:
- 不准确：容易误判
- 无法处理复杂场景

---

## 📝 详细设计

### 1. 参数类型转换策略

#### 1.1 基础类型
```java
String -> String
Integer -> int / Integer
Long -> long / Long
Double -> double / Double
Boolean -> boolean / Boolean
```

#### 1.2 复杂对象类型
```java
Map -> POJO (使用 Jackson 转换)
  - User: com.zkinfo.demo.model.User
  - Order: com.zkinfo.demo.model.Order
  - Product: com.zkinfo.demo.model.Product
```

#### 1.3 集合类型
```java
List<Map> -> List<POJO>
  - List<User> -> List<com.zkinfo.demo.model.User>
  - List<Order> -> List<com.zkinfo.demo.model.Order>
```

#### 1.4 嵌套对象
```java
Order.orderItems -> List<Order.OrderItem>
User.address -> Address (如果存在)
```

### 2. Dubbo2 vs Dubbo3 处理差异

#### Dubbo2
```java
// 需要明确指定参数类型
String[] parameterTypes = {"com.zkinfo.demo.model.Order"};
Object[] args = {convertedOrder};
genericService.$invoke("createOrder", parameterTypes, args);
```

#### Dubbo3
```java
// 支持 POJO 模式，可以直接传递 Map
// 方式1: 传递 Map（推荐）
Map<String, Object> orderMap = {...};
genericService.$invoke("createOrder", null, new Object[]{orderMap});

// 方式2: 传递 POJO 对象
Order order = convertMapToOrder(orderMap);
genericService.$invoke("createOrder", null, new Object[]{order});
```

**关键差异**:
- **Dubbo2**: 必须指定 `parameterTypes`，参数可以是 Map 或 POJO
- **Dubbo3**: `parameterTypes` 可以为 null，Dubbo 会自动推断，支持 POJO 模式

### 3. 参数转换流程

```
JSON Arguments Map
    ↓
获取方法签名 (MethodSignature)
    ↓
遍历每个参数
    ↓
根据参数类型转换
    ├─ 基础类型 → 直接转换
    ├─ POJO 类型 → Map → POJO (Jackson)
    ├─ List<POJO> → List<Map> → List<POJO>
    └─ 嵌套对象 → 递归转换
    ↓
构建参数数组 (Object[])
    ↓
调用 Dubbo GenericService.$invoke()
```

---

## 🚀 实现步骤

### 阶段 1: 创建 ParameterConverter

1. 创建 `ParameterConverter` 类
2. 实现基础类型转换
3. 实现 POJO 对象转换（使用 Jackson）
4. 实现集合类型转换
5. 实现嵌套对象转换

### 阶段 2: 集成方法签名解析

1. 创建 `MethodSignatureResolver` 类
2. 从数据库获取方法签名
3. 缓存方法签名信息
4. 集成到 `McpToolSchemaGenerator`

### 阶段 3: 区分 Dubbo 版本

1. 创建 `DubboVersionDetector` 类
2. 检测 Dubbo 版本
3. 根据版本选择处理策略
4. 集成到 `McpExecutorService`

### 阶段 4: 测试和优化

1. 编写单元测试
2. 测试复杂对象场景
3. 测试嵌套对象场景
4. 性能优化

---

## 📊 数据流图

```
┌─────────────────────────────────────────────────────────────┐
│ MCP tools/call Request                                      │
│ {                                                           │
│   "name": "OrderService.createOrder",                      │
│   "arguments": {                                            │
│     "order": {                                              │
│       "userId": 1001,                                       │
│       "orderItems": [...]                                   │
│     }                                                       │
│   }                                                         │
│ }                                                           │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ McpMessageController.handleToolCall()                       │
│ - 解析 toolName → interfaceName + methodName               │
│ - 提取 arguments Map                                        │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ McpToolSchemaGenerator.extractMethodParameters()           │
│ - 调用 MethodSignatureResolver.getMethodSignature()         │
│ - 根据方法签名提取参数值                                     │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ MethodSignatureResolver.getMethodSignature()               │
│ - 查询数据库: DubboServiceMethodEntity                      │
│ - 获取参数列表: DubboMethodParameterEntity                  │
│ - 返回: MethodSignature {                                  │
│     parameters: [                                           │
│       {name: "order", type: "com.zkinfo.demo.model.Order"} │
│     ]                                                       │
│   }                                                         │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ ParameterConverter.convertToJavaObject()                    │
│ - 输入: Map, targetType="com.zkinfo.demo.model.Order"      │
│ - 使用 Jackson ObjectMapper 转换                            │
│ - 处理嵌套对象: orderItems → List<Order.OrderItem>         │
│ - 输出: Order 对象                                          │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ McpExecutorService.executeToolCallSync()                    │
│ - 检测 Dubbo 版本                                           │
│ - Dubbo2: 指定 parameterTypes                              │
│ - Dubbo3: parameterTypes = null                            │
│ - 调用 GenericService.$invoke()                            │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ Dubbo GenericService.$invoke()                              │
│ - 执行实际的 Dubbo 调用                                      │
│ - 返回结果                                                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔍 关键实现细节

### 1. 参数类型转换

```java
public Object convertToJavaObject(Object value, String targetType, String dubboVersion) {
    if (value == null) {
        return null;
    }
    
    // 基础类型
    if (isPrimitiveType(targetType)) {
        return convertPrimitive(value, targetType);
    }
    
    // POJO 对象
    if (isPOJOType(targetType)) {
        return convertPOJO(value, targetType);
    }
    
    // 集合类型
    if (isCollectionType(targetType)) {
        return convertCollection(value, targetType);
    }
    
    // 其他类型
    return value;
}
```

### 2. 嵌套对象处理

```java
private Object convertPOJO(Object value, String targetType) {
    if (value instanceof Map) {
        Map<String, Object> map = (Map<String, Object>) value;
        
        // 使用 Jackson 转换
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        
        try {
            Class<?> targetClass = Class.forName(targetType);
            return mapper.convertValue(map, targetClass);
        } catch (Exception e) {
            log.warn("Failed to convert Map to POJO: {}", targetType, e);
            return map; // Fallback: 返回 Map
        }
    }
    
    return value;
}
```

### 3. 集合类型处理

```java
private Object convertCollection(Object value, String targetType) {
    // 解析泛型类型: List<com.zkinfo.demo.model.User>
    String elementType = extractElementType(targetType);
    
    if (value instanceof List) {
        List<Object> list = (List<Object>) value;
        return list.stream()
            .map(item -> convertToJavaObject(item, elementType, dubboVersion))
            .collect(Collectors.toList());
    }
    
    return value;
}
```

### 4. Dubbo 版本检测

```java
private String detectDubboVersion(ProviderInfo provider) {
    // 方式1: 从 metadata 获取
    String dubboVersion = provider.getMetadata().get("dubbo");
    if (dubboVersion != null && dubboVersion.startsWith("3")) {
        return "3.x";
    }
    
    // 方式2: 从协议判断
    if ("tri".equals(provider.getProtocol())) {
        return "3.x";
    }
    
    // 默认: Dubbo2
    return "2.x";
}
```

---

## 📋 测试用例

### 测试用例 1: 简单 POJO 参数

```json
{
  "method": "tools/call",
  "params": {
    "name": "UserService.createUser",
    "arguments": {
      "user": {
        "username": "test",
        "email": "test@example.com",
        "age": 25
      }
    }
  }
}
```

**期望**: `user` Map 转换为 `com.zkinfo.demo.model.User` 对象

### 测试用例 2: 嵌套对象参数

```json
{
  "method": "tools/call",
  "params": {
    "name": "OrderService.createOrder",
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

**期望**: 
- `order` Map 转换为 `com.zkinfo.demo.model.Order` 对象
- `orderItems` List<Map> 转换为 `List<Order.OrderItem>`

### 测试用例 3: 集合类型参数

```json
{
  "method": "tools/call",
  "params": {
    "name": "UserService.batchCreateUsers",
    "arguments": {
      "users": [
        {"username": "user1", "email": "user1@example.com"},
        {"username": "user2", "email": "user2@example.com"}
      ]
    }
  }
}
```

**期望**: `users` List<Map> 转换为 `List<com.zkinfo.demo.model.User>`

---

## 🎯 实施优先级

1. **P0**: 实现基础 POJO 对象转换（User、Order、Product）
2. **P1**: 实现嵌套对象转换（Order.orderItems）
3. **P2**: 实现集合类型转换（List<User>）
4. **P3**: 区分 Dubbo2/Dubbo3 处理方式
5. **P4**: 性能优化和缓存

---

## 📚 参考资料

- [Dubbo 泛化调用文档](https://dubbo.apache.org/zh-cn/docs/advanced/generic-reference/)
- [Dubbo3 POJO 模式](https://dubbo.apache.org/zh-cn/docs/advanced/pojo-mode/)
- [Jackson ObjectMapper 文档](https://github.com/FasterXML/jackson-docs)

