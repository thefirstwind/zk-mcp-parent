# Bug修复报告

## 问题描述

mcp-ai-client无法成功调用MCP工具，总是返回"工具执行失败"错误，但无法看到具体的错误信息。

## 根本原因

发现了两个主要问题：

### 1. 错误信息被隐藏

**位置**: `zkInfo/src/main/java/com/zkinfo/service/McpProtocolService.java:196-200`

**原始代码**:
```java
return executeToolCall(toolName, arguments, timeout)
        .map(result -> createSuccessResponse(request.getId(), result))
        .onErrorReturn(createErrorResponse(request.getId(), 
            McpProtocol.ErrorCodes.TOOL_EXECUTION_ERROR, 
            "工具执行失败"));
```

**问题**: `onErrorReturn`会吞掉真正的异常信息，只返回通用错误消息。

**修复**: 改用`onErrorResume`并记录详细错误日志：
```java
return executeToolCall(toolName, arguments, timeout)
        .map(result -> createSuccessResponse(request.getId(), result))
        .onErrorResume(error -> {
            log.error("工具执行出错: toolName={}, error={}", toolName, error.getMessage(), error);
            return Mono.just(createErrorResponse(request.getId(), 
                McpProtocol.ErrorCodes.TOOL_EXECUTION_ERROR, 
                "工具执行失败: " + error.getMessage()));
        });
```

### 2. 参数类型转换错误

**位置**: `zkInfo/src/main/java/com/zkinfo/service/McpProtocolService.java:448-461`

**根本原因**: 
- mcp-ai-client的简单JSON解析器（`AiConversationService.parseArguments`）将所有值都解析为字符串
- AI返回 `{"args": [1]}` 时，被解析为 `{"args": "[1]"}`（字符串而不是数组）
- `convertArgumentsToArray`方法尝试将字符串强制转换为List，导致ClassCastException

**原始代码**:
```java
private Object[] convertArgumentsToArray(Map<String, Object> arguments) {
    if (arguments == null || arguments.isEmpty()) {
        return new Object[0];
    }
    
    // 如果参数是按位置传递的
    if (arguments.containsKey("args")) {
        List<?> argsList = (List<?>) arguments.get("args");  // ❌ 类型转换失败
        return argsList.toArray();
    }
    
    return arguments.values().toArray();
}
```

**错误信息**:
```
class java.lang.String cannot be cast to class java.util.List
```

**修复**: 增强`convertArgumentsToArray`方法，支持多种参数格式：

```java
private Object[] convertArgumentsToArray(Map<String, Object> arguments) {
    if (arguments == null || arguments.isEmpty()) {
        return new Object[0];
    }
    
    // 如果参数是按位置传递的
    if (arguments.containsKey("args")) {
        Object argsValue = arguments.get("args");
        
        // 如果已经是List，直接转换
        if (argsValue instanceof List) {
            List<?> argsList = (List<?>) argsValue;
            return argsList.toArray();
        }
        
        // ✅ 如果是String，尝试解析为JSON数组
        if (argsValue instanceof String) {
            String argsStr = (String) argsValue;
            try {
                // 简单处理数组格式：[1] 或 [1, 2, 3]
                if (argsStr.startsWith("[") && argsStr.endsWith("]")) {
                    argsStr = argsStr.substring(1, argsStr.length() - 1);
                    if (argsStr.trim().isEmpty()) {
                        return new Object[0];
                    }
                    String[] parts = argsStr.split(",");
                    Object[] result = new Object[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        String part = parts[i].trim();
                        // 尝试解析为数字
                        try {
                            if (part.contains(".")) {
                                result[i] = Double.parseDouble(part);
                            } else {
                                result[i] = Long.parseLong(part);
                            }
                        } catch (NumberFormatException e) {
                            // 保持为字符串，去掉引号
                            result[i] = part.replaceAll("^\"|\"$", "");
                        }
                    }
                    return result;
                }
            } catch (Exception e) {
                log.warn("解析args字符串失败: {}", argsStr, e);
            }
        }
        
        // 其他情况，作为单个参数
        return new Object[]{argsValue};
    }
    
    return arguments.values().toArray();
}
```

## 测试结果

### 成功案例

1. **查询单个用户**:
```bash
curl -X POST "http://localhost:8081/api/chat/session/{sessionId}/message" \
  -H "Content-Type: application/json" \
  -d '{"message": "查询用户ID为1的信息"}'
```

**返回**:
```json
{
  "sessionId": "c4cad98d-9d45-4bc2-bede-3eebc2df043d",
  "userMessage": "查询用户ID为1的信息",
  "aiResponse": "执行工具 com.zkinfo.demo.service.UserService.getUserById 的结果：\n\n{\"realName\":\"Alice Wang\",\"gender\":\"F\",\"createTime\":\"2025-10-24T10:16:20.431606\",\"phone\":\"13800138001\",\"updateTime\":\"2025-10-24T10:16:20.431609\",\"id\":1,\"class\":\"com.zkinfo.demo.model.User\",\"age\":25,\"email\":\"alice@example.com\",\"username\":\"alice\",\"status\":\"ACTIVE\"}",
  "timestamp": 1761275801816
}
```

2. **查询所有用户**:
```bash
curl -X POST "http://localhost:8081/api/chat/session/{sessionId}/message" \
  -H "Content-Type: application/json" \
  -d '{"message": "查询所有用户"}'
```

**成功返回**: 用户列表JSON数据

## 已知限制

1. **复杂对象参数**: 由于mcp-ai-client使用简单的JSON解析器，无法处理复杂对象（如User对象），因此创建用户等需要传递对象的操作会失败。

2. **建议**: 
   - 选项A：改进`AiConversationService.parseArguments`使用Jackson进行完整的JSON解析
   - 选项B：在提示词中指导AI只使用基本类型参数调用方法

## 文件修改清单

1. `zkInfo/src/main/java/com/zkinfo/service/McpProtocolService.java`
   - 修改`handleCallTool`方法中的错误处理逻辑（第196-203行）
   - 增强`convertArgumentsToArray`方法支持字符串解析（第448-505行）

## 验证步骤

1. 确保所有服务正在运行:
   ```bash
   # ZooKeeper (端口2181)
   # demo-provider (端口20883)
   # zkInfo (端口9091)
   # mcp-ai-client (端口8081)
   ```

2. 创建会话:
   ```bash
   curl -X POST http://localhost:8081/api/chat/session
   ```

3. 发送测试消息:
   ```bash
   curl -X POST "http://localhost:8081/api/chat/session/{sessionId}/message" \
     -H "Content-Type: application/json" \
     -d '{"message": "查询用户ID为1的信息"}'
   ```

4. 预期结果: 返回用户详细信息（Alice Wang）

## 总结

通过这两处修复：
1. ✅ 错误信息现在能够正确显示，便于调试
2. ✅ 参数类型转换支持多种格式，兼容简单JSON解析器的输出
3. ✅ AI能够成功调用Dubbo服务并返回结果

系统现在可以正常工作，实现了AI通过MCP协议调用Dubbo服务的完整流程。


