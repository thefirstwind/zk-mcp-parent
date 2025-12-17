package com.zkinfo.util;

import com.zkinfo.model.ProviderInfo;
import com.zkinfo.service.ProviderService;
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
        // 1. 尝试从 ProviderService 获取 ProviderInfo 并解析 metadata
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
        
        // 2. 基于方法名模式推断参数
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
            if (entityName.isEmpty()) {
                entityName = "entity";
            } else {
                entityName = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1);
            }
            param.setName(entityName);
            param.setType("java.lang.Object"); // 对象类型
            info.getParameters().add(param);
            return info;
        }
        
        // update* -> 通常有一个对象参数
        if (methodName.startsWith("update")) {
            info.setParameterCount(1);
            MethodParameter param = new MethodParameter();
            String entityName = methodName.replaceAll("^update", "");
            if (entityName.isEmpty()) {
                entityName = "entity";
            } else {
                entityName = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1);
            }
            param.setName(entityName);
            param.setType("java.lang.Object");
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
     * 根据方法签名从 params Map 中提取对应的参数值
     * 
     * @param interfaceName 接口全限定名
     * @param methodName 方法名
     * @param params 工具输入参数 Map
     * @return 方法参数数组（按方法签名顺序）
     */
    public Object[] extractMethodParameters(String interfaceName, String methodName, Map<String, Object> params) {
        try {
            // 从 metadata 获取方法签名
            MethodSignatureInfo methodInfo = getMethodSignatureFromMetadata(interfaceName, methodName);
            
            if (methodInfo != null && methodInfo.getParameterCount() > 0) {
                List<MethodParameter> parameters = methodInfo.getParameters();
                Object[] args = new Object[parameters.size()];
                for (int i = 0; i < parameters.size(); i++) {
                    MethodParameter param = parameters.get(i);
                    args[i] = params.get(param.getName());
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
