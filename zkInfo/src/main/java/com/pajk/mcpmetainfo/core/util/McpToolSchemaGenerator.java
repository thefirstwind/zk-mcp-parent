package com.pajk.mcpmetainfo.core.util;

import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import com.pajk.mcpmetainfo.core.service.ProviderService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MCP 工具 Schema 生成器
 * 根据实际方法参数生成 inputSchema，而不是固定需要 args 和 timeout
 * 通过 ZooKeeper metadata 或方法名模式推断获取方法签名信息
 */
@Slf4j
@Component
public class McpToolSchemaGenerator {
    
    @Autowired(required = false)
    private ProviderService providerService;
    
    @Autowired(required = false)
    private MethodSignatureResolver methodSignatureResolver;
    
    @Autowired(required = false)
    private ParameterConverter parameterConverter;
    
    /**
     * 方法签名信息
     */
    @Data
    private static class MethodSignatureInfo {
        private int parameterCount;
        private List<MethodParameter> parameters;
        
        public MethodSignatureInfo() {
            this.parameters = new ArrayList<>();
        }
    }
    
    /**
     * 方法参数信息
     */
    @Data
    private static class MethodParameter {
        private String name;
        private String type;
    }
    
    /**
     * 根据方法签名创建 inputSchema
     * 通过 ZooKeeper metadata 或方法名模式推断获取方法的实际参数信息
     * 
     * @param interfaceName 接口全限定名
     * @param methodName 方法名
     * @return inputSchema Map
     */
    public Map<String, Object> createInputSchemaFromMethod(String interfaceName, String methodName) {
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        
        try {
            // 从 ProviderService 获取方法签名信息（从 ZooKeeper metadata 或推断）
            MethodSignatureInfo methodInfo = getMethodSignatureFromMetadata(interfaceName, methodName);
            
            if (methodInfo != null && methodInfo.getParameterCount() >= 0) {
                log.debug("✅ Found method {}.{} with {} parameters", 
                        interfaceName, methodName, methodInfo.getParameterCount());
                
                if (methodInfo.getParameterCount() == 0) {
                    // 无参数方法（如 getAllUsers），不需要 args，也不需要 timeout
                    log.debug("  → No parameters, creating schema without 'args' and 'timeout'");
                    // 无参数方法，properties 为空
                } else {
                    log.debug("  → Has {} parameters, creating schema with method parameters", 
                            methodInfo.getParameterCount());
                    // 有参数方法（如 getUserById(Long userId)）
                    // 为每个参数创建属性
                    List<MethodParameter> params = methodInfo.getParameters();
                    for (int i = 0; i < params.size(); i++) {
                        MethodParameter param = params.get(i);
                        String paramName = param.getName();
                        String paramType = param.getType();
                        
                        log.debug("    Parameter[{}]: {} ({})", i, paramName, paramType);
                        
                        Map<String, Object> paramProperty = new HashMap<>();
                        paramProperty.put("description", getParameterDescriptionFromType(paramType, paramName));
                        
                        // 根据参数类型设置 type
                        String jsonType = getJsonTypeFromJavaTypeName(paramType);
                        paramProperty.put("type", jsonType);
                        
                        // 如果是数组或集合类型，设置 items
                        if (paramType.endsWith("[]") || paramType.contains("List") || 
                            paramType.contains("Set") || paramType.contains("Collection")) {
                            Map<String, Object> items = new HashMap<>();
                            items.put("type", "any");
                            paramProperty.put("items", items);
                        }
                        
                        properties.put(paramName, paramProperty);
                        required.add(paramName);
                    }
                    // 有参数方法，不需要 timeout
                }
            } else {
                // 如果找不到方法签名信息，使用通用 schema（向后兼容）
                log.warn("⚠️ Method signature not found for {}.{} in metadata, using generic schema", 
                        interfaceName, methodName);
                Map<String, Object> argsProperty = new HashMap<>();
                argsProperty.put("type", "array");
                argsProperty.put("description", "方法参数列表");
                argsProperty.put("items", Map.of("type", "any"));
                properties.put("args", argsProperty);
                required.add("args");
            }
        } catch (Exception e) {
            log.error("❌ Error creating inputSchema for {}.{}: {}", 
                    interfaceName, methodName, e.getMessage(), e);
            // 发生错误时，使用通用 schema
            Map<String, Object> argsProperty = new HashMap<>();
            argsProperty.put("type", "array");
            argsProperty.put("description", "方法参数列表");
            argsProperty.put("items", Map.of("type", "any"));
            properties.put("args", argsProperty);
            required.add("args");
        }
        
        inputSchema.put("properties", properties);
        if (!required.isEmpty()) {
            inputSchema.put("required", required);
        }
        
        return inputSchema;
    }
    
    /**
     * 从 metadata 或方法名模式推断方法签名
     */
    private MethodSignatureInfo getMethodSignatureFromMetadata(String interfaceName, String methodName) {
        // 1. 优先从 MethodSignatureResolver 获取（从数据库）
        if (methodSignatureResolver != null) {
            MethodSignatureResolver.MethodSignature signature = 
                    methodSignatureResolver.getMethodSignature(interfaceName, methodName);
            if (signature != null && signature.getParameters() != null) {
                MethodSignatureInfo info = new MethodSignatureInfo();
                info.setParameterCount(signature.getParameters().size());
                for (MethodSignatureResolver.ParameterInfo param : signature.getParameters()) {
                    MethodParameter methodParam = new MethodParameter();
                    methodParam.setName(param.getName());
                    methodParam.setType(param.getType());
                    info.getParameters().add(methodParam);
                }
                log.debug("✅ Got method signature from MethodSignatureResolver: {}.{} with {} parameters", 
                        interfaceName, methodName, info.getParameterCount());
                return info;
            }
        }
        
        // 2. 尝试从 ProviderService 获取 ProviderInfo 并解析 metadata
        if (providerService != null) {
            List<ProviderInfo> providers = providerService.getAllProviders().stream()
                    .filter(p -> interfaceName.equals(p.getInterfaceName()))
                    .filter(ProviderInfo::isOnline)
                    .toList();
            
            if (!providers.isEmpty()) {
                // 可以从 ProviderInfo 的 parameters 中获取更多信息
                // 但目前 ProviderInfo 只存储了 methods 字符串，没有详细的参数信息
                // 所以我们需要基于方法名模式推断
            }
        }
        
        // 3. 基于方法名模式推断参数（fallback）
        return inferMethodSignatureFromName(methodName);
    }
    
    /**
     * 基于方法名模式推断方法签名
     * 这是临时方案，理想情况下应该从 ZooKeeper metadata 读取
     */
    private MethodSignatureInfo inferMethodSignatureFromName(String methodName) {
        MethodSignatureInfo info = new MethodSignatureInfo();
        
        // 常见模式：
        // getAll* / list* / queryAll* -> 无参数
        if (methodName.startsWith("getAll") || methodName.startsWith("list") || 
            methodName.startsWith("queryAll") || methodName.equals("getAllUsers")) {
            info.setParameterCount(0);
            return info;
        }
        
        // get*ById / get*By* -> 通常有一个 Long 或 String 参数
        if (methodName.matches("get.*ById") || methodName.matches("get.*By.*")) {
            info.setParameterCount(1);
            MethodParameter param = new MethodParameter();
            // 提取实体名：getUserById -> userId, getOrderById -> orderId
            String entityName = methodName.replaceAll("^get", "").replaceAll("ById$", "");
            if (entityName.isEmpty()) {
                entityName = "id";
            } else {
                entityName = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1) + "Id";
            }
            param.setName(entityName);
            param.setType("java.lang.Long");
            info.getParameters().add(param);
            return info;
        }
        
        // create* / add* -> 通常有一个对象参数
        if (methodName.startsWith("create") || methodName.startsWith("add")) {
            info.setParameterCount(1);
            MethodParameter param = new MethodParameter();
            // createUser -> user, addOrder -> order
            String entityName = methodName.replaceAll("^create", "").replaceAll("^add", "");
            String paramType = "java.lang.Object"; // 默认类型
            
            if (entityName.isEmpty()) {
                entityName = "entity";
            } else {
                entityName = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1);
                
                // 推断具体的 POJO 类型
                String inferredType = inferPOJOTypeFromMethodName(methodName, entityName);
                if (inferredType != null) {
                    paramType = inferredType;
                }
            }
            param.setName(entityName);
            param.setType(paramType);
            info.getParameters().add(param);
            return info;
        }
        
        // update* -> 通常有一个对象参数
        if (methodName.startsWith("update")) {
            info.setParameterCount(1);
            MethodParameter param = new MethodParameter();
            String entityName = methodName.replaceAll("^update", "");
            String paramType = "java.lang.Object"; // 默认类型
            
            if (entityName.isEmpty()) {
                entityName = "entity";
            } else {
                entityName = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1);
                
                // 推断具体的 POJO 类型
                String inferredType = inferPOJOTypeFromMethodName(methodName, entityName);
                if (inferredType != null) {
                    paramType = inferredType;
                }
            }
            param.setName(entityName);
            param.setType(paramType);
            info.getParameters().add(param);
            return info;
        }
        
        // delete* / remove* -> 通常有一个 Long 参数
        if (methodName.startsWith("delete") || methodName.startsWith("remove")) {
            info.setParameterCount(1);
            MethodParameter param = new MethodParameter();
            String entityName = methodName.replaceAll("^delete", "").replaceAll("^remove", "");
            if (entityName.isEmpty()) {
                param.setName("id");
            } else {
                param.setName(Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1) + "Id");
            }
            param.setType("java.lang.Long");
            info.getParameters().add(param);
            return info;
        }
        
        // 默认：无参数
        info.setParameterCount(0);
        return info;
    }
    
    /**
     * 从方法名推断 POJO 类型
     * 例如: createUser -> com.pajk.mcpmetainfo.core.demo.model.User
     *      createOrder -> com.pajk.mcpmetainfo.core.demo.model.Order
     */
    private String inferPOJOTypeFromMethodName(String methodName, String entityName) {
        // 常见实体类型映射
        Map<String, String> entityTypeMap = new HashMap<>();
        entityTypeMap.put("user", "com.pajk.mcpmetainfo.core.demo.model.User");
        entityTypeMap.put("order", "com.pajk.mcpmetainfo.core.demo.model.Order");
        entityTypeMap.put("product", "com.pajk.mcpmetainfo.core.demo.model.Product");
        
        // 从方法名提取实体名（首字母大写）
        String entityType = null;
        if (methodName.startsWith("create")) {
            String extracted = methodName.substring(6); // 跳过 "create"
            if (!extracted.isEmpty()) {
                entityType = extracted.toLowerCase();
            }
        } else if (methodName.startsWith("add")) {
            String extracted = methodName.substring(3); // 跳过 "add"
            if (!extracted.isEmpty()) {
                entityType = extracted.toLowerCase();
            }
        } else if (methodName.startsWith("update")) {
            String extracted = methodName.substring(6); // 跳过 "update"
            if (!extracted.isEmpty()) {
                entityType = extracted.toLowerCase();
            }
        }
        
        // 从 entityName 推断
        if (entityType == null && entityName != null) {
            entityType = entityName.toLowerCase();
        }
        
        // 查找映射
        if (entityType != null && entityTypeMap.containsKey(entityType)) {
            return entityTypeMap.get(entityType);
        }
        
        // 尝试构建完整类名
        if (entityType != null) {
            // 首字母大写
            String capitalized = Character.toUpperCase(entityType.charAt(0)) + entityType.substring(1);
            return "com.pajk.mcpmetainfo.core.demo.model." + capitalized;
        }
        
        return null;
    }
    
    /**
     * 获取参数描述（基于类型名）
     */
    private String getParameterDescriptionFromType(String typeName, String paramName) {
        String simpleType = typeName.contains(".") ? 
                typeName.substring(typeName.lastIndexOf(".") + 1) : typeName;
        return String.format("%s 类型的参数 %s", simpleType, paramName);
    }
    
    /**
     * 将 Java 类型名转换为 JSON Schema 类型
     */
    private String getJsonTypeFromJavaTypeName(String javaTypeName) {
        if (javaTypeName == null || javaTypeName.isEmpty()) {
            return "any";
        }
        
        // 基本类型
        if (javaTypeName.equals("boolean") || javaTypeName.equals("java.lang.Boolean")) {
            return "boolean";
        } else if (javaTypeName.equals("int") || javaTypeName.equals("java.lang.Integer") ||
                   javaTypeName.equals("long") || javaTypeName.equals("java.lang.Long") ||
                   javaTypeName.equals("short") || javaTypeName.equals("java.lang.Short") ||
                   javaTypeName.equals("byte") || javaTypeName.equals("java.lang.Byte")) {
            return "integer";
        } else if (javaTypeName.equals("float") || javaTypeName.equals("java.lang.Float") ||
                   javaTypeName.equals("double") || javaTypeName.equals("java.lang.Double")) {
            return "number";
        } else if (javaTypeName.equals("java.lang.String") || javaTypeName.equals("String") ||
                   javaTypeName.equals("char") || javaTypeName.equals("java.lang.Character")) {
            return "string";
        } else if (javaTypeName.endsWith("[]") || javaTypeName.contains("List") || 
                   javaTypeName.contains("Set") || javaTypeName.contains("Collection")) {
            return "array";
        } else if (javaTypeName.contains("Map")) {
            return "object";
        } else {
            // 其他对象类型
            return "object";
        }
    }
    
    /**
     * 从工具输入参数中提取方法参数数组
     * 根据方法签名从 params Map 中提取对应的参数值，并转换为正确的 Java 类型
     * 
     * @param interfaceName 接口全限定名
     * @param methodName 方法名
     * @param params 工具输入参数 Map
     * @return 方法参数数组（按方法签名顺序，已转换为正确的 Java 类型）
     */
    public Object[] extractMethodParameters(String interfaceName, String methodName, Map<String, Object> params) {
        try {
            // 从 metadata 获取方法签名
            MethodSignatureInfo methodInfo = getMethodSignatureFromMetadata(interfaceName, methodName);
            
            if (methodInfo != null && methodInfo.getParameterCount() > 0) {
                List<MethodParameter> parameters = methodInfo.getParameters();
                Object[] args = new Object[parameters.size()];
                
                // 检测 Dubbo 版本（简化处理，默认使用 2.x）
                String dubboVersion = "2.x"; // TODO: 从 ProviderInfo 获取实际版本
                
                for (int i = 0; i < parameters.size(); i++) {
                    MethodParameter param = parameters.get(i);
                    Object rawValue = params.get(param.getName());
                    
                    // 使用 ParameterConverter 转换参数类型
                    if (parameterConverter != null && rawValue != null && param.getType() != null) {
                        args[i] = parameterConverter.convertToJavaObject(rawValue, param.getType(), dubboVersion);
                        log.debug("✅ Converted parameter[{}] {}: {} -> {}", 
                                i, param.getName(), rawValue.getClass().getSimpleName(), param.getType());
                    } else {
                        args[i] = rawValue;
                    }
                }
                return args;
            } else if (methodInfo != null && methodInfo.getParameterCount() == 0) {
                // 无参数方法
                return new Object[0];
            } else {
                // 如果找不到方法签名，尝试向后兼容：从 args 字段获取
                log.warn("⚠️ Method signature not found for {}.{}, trying backward compatibility with 'args' field", 
                        interfaceName, methodName);
                if (params.containsKey("args") && params.get("args") instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> argsList = (List<Object>) params.get("args");
                    return argsList.toArray();
                }
                return new Object[0];
            }
        } catch (Exception e) {
            log.error("❌ Error extracting method parameters for {}.{}: {}", 
                    interfaceName, methodName, e.getMessage(), e);
            // 发生错误时，尝试向后兼容
            if (params.containsKey("args") && params.get("args") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> argsList = (List<Object>) params.get("args");
                return argsList.toArray();
            }
            return new Object[0];
        }
    }
}
