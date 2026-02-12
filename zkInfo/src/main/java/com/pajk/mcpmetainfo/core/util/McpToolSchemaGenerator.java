package com.pajk.mcpmetainfo.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import com.pajk.mcpmetainfo.core.service.ProviderService;
import com.pajk.mcpmetainfo.core.service.ZooKeeperService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
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
    
    @Lazy
    @Autowired(required = false)
    private ZooKeeperService zooKeeperService;
    
    // ObjectMapper 用于解析 JSON
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取人工维护的方法描述（来自数据库）。
     *
     * 用于 tools 的 description 展示；如果数据库没有则返回 null。
     */
    public String getMethodDescriptionFromDb(String interfaceName, String methodName) {
        if (methodSignatureResolver == null) return null;
        try {
            MethodSignatureResolver.MethodSignature sig = methodSignatureResolver.getMethodSignature(interfaceName, methodName);
            if (sig == null) return null;
            String desc = sig.getMethodDescription();
            if (desc == null || desc.isBlank()) return null;
            return desc;
        } catch (Exception e) {
            log.debug("⚠️ Failed to get methodDescription from DB: {}.{} error={}", interfaceName, methodName, e.getMessage());
            return null;
        }
    }
    
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
        log.info("🔧 创建 inputSchema: interface={}, method={}", interfaceName, methodName);
        
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        // DB signature (human maintained description/schema) is used as overlay even when ZK metadata exists
        MethodSignatureResolver.MethodSignature dbSignature = null;
        try {
            if (methodSignatureResolver != null) {
                dbSignature = methodSignatureResolver.getMethodSignature(interfaceName, methodName);
            }
        } catch (Exception e) {
            log.debug("⚠️ Failed to load DB signature for overlay: {}.{} error={}", interfaceName, methodName, e.getMessage());
        }
        
        try {
            // 从 ProviderService 获取方法签名信息（从 ZooKeeper metadata 或推断）
            MethodSignatureInfo methodInfo = getMethodSignatureFromMetadata(interfaceName, methodName);
            
            if (methodInfo != null && methodInfo.getParameterCount() >= 0) {
                log.info("✅ 找到方法签名: {}.{} with {} parameters", 
                        interfaceName, methodName, methodInfo.getParameterCount());
                
                if (methodInfo.getParameterCount() == 0) {
                    // 无参数方法（如 getAllUsers），不需要 args，也不需要 timeout
                    log.info("  → 无参数方法，properties 为空");
                    // 无参数方法，properties 为空
                } else {
                    log.info("  → 有 {} 个参数，创建 schema", methodInfo.getParameterCount());
                    // 有参数方法（如 getUserById(Long userId)）
                    // 为每个参数创建属性
                    List<MethodParameter> params = methodInfo.getParameters();
                    for (int i = 0; i < params.size(); i++) {
                        MethodParameter param = params.get(i);
                        String paramName = param.getName();
                        String paramType = param.getType();
                        
                        if (paramName == null || paramName.isEmpty()) {
                            log.warn("    ⚠️ 参数[{}] 名称为空，使用默认名称", i);
                            paramName = "param" + i;
                        }
                        if (paramType == null || paramType.isEmpty()) {
                            log.warn("    ⚠️ 参数[{}] {} 类型为空，使用默认类型", i, paramName);
                            paramType = "java.lang.Object";
                        }
                        
                        log.info("    Parameter[{}]: name={}, type={}", i, paramName, paramType);
                        
                        // Overlay: prefer DB description + structured schema if available
                        MethodSignatureResolver.ParameterInfo dbParam = null;
                        if (dbSignature != null && dbSignature.getParameters() != null && !dbSignature.getParameters().isEmpty()) {
                            // 1) match by name
                            for (MethodSignatureResolver.ParameterInfo p : dbSignature.getParameters()) {
                                if (p != null && p.getName() != null && p.getName().equals(paramName)) {
                                    dbParam = p;
                                    break;
                                }
                            }
                            // 2) fallback by index
                            if (dbParam == null && i < dbSignature.getParameters().size()) {
                                dbParam = dbSignature.getParameters().get(i);
                            }
                        }

                        String dbDesc = dbParam != null ? dbParam.getDescription() : null;
                        String dbSchemaJson = dbParam != null ? dbParam.getSchemaJson() : null;

                        Map<String, Object> paramProperty = null;
                        boolean paramRequired = true;

                        // If structured schema exists, use it to build property schema
                        if (dbSchemaJson != null && !dbSchemaJson.isBlank()) {
                            try {
                                JsonNode root = objectMapper.readTree(dbSchemaJson);
                                JsonNode requiredNode = root.get("required");
                                if (requiredNode != null && requiredNode.isBoolean()) {
                                    paramRequired = requiredNode.asBoolean(true);
                                }
                                JsonNode schemaNode = root.get("jsonSchema");
                                if (schemaNode == null || schemaNode.isMissingNode() || schemaNode.isNull()) {
                                    // backward compatibility: accept "schema" as alias
                                    schemaNode = root.get("schema");
                                }
                                if (schemaNode != null && schemaNode.isObject()) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> schemaMap = objectMapper.convertValue(schemaNode, Map.class);
                                    paramProperty = new HashMap<>(schemaMap);
                                }
                            } catch (Exception ex) {
                                log.debug("⚠️ Failed to parse parameter schemaJson for {}.{} param={} error={}",
                                        interfaceName, methodName, paramName, ex.getMessage());
                            }
                        }

                        // Fallback to type-based schema
                        if (paramProperty == null) {
                            paramProperty = new HashMap<>();
                            // 根据参数类型设置 type
                            String jsonType = getJsonTypeFromJavaTypeName(paramType);
                            paramProperty.put("type", jsonType);

                            // 如果是数组或集合类型，设置 items
                            if (paramType != null && (paramType.endsWith("[]") || paramType.contains("List") ||
                                paramType.contains("Set") || paramType.contains("Collection"))) {
                                Map<String, Object> items = new HashMap<>();
                                // items.put("type", "any"); // type: any is invalid in JSON Schema
                                // Leave items empty to allow any type, or default to string
                                // items.put("type", "string"); 
                                paramProperty.put("items", items);
                            }
                        }

                        // Description: prefer DB, fallback to type-based
                        String finalDesc = (dbDesc != null && !dbDesc.isBlank())
                                ? dbDesc
                                : getParameterDescriptionFromType(paramType, paramName);
                        if (!paramProperty.containsKey("description") || paramProperty.get("description") == null ||
                                String.valueOf(paramProperty.get("description")).isBlank()) {
                            paramProperty.put("description", finalDesc);
                        }
                        
                        properties.put(paramName, paramProperty);
                        if (paramRequired) {
                            required.add(paramName);
                        }
                    }
                    log.info("  ✅ 成功创建 {} 个参数的 properties", properties.size());
                }
            } else {
                // 如果找不到方法签名信息，使用通用 schema（向后兼容）
                log.warn("⚠️ 未找到方法签名信息: {}.{}，使用通用 schema", 
                        interfaceName, methodName);
                Map<String, Object> argsProperty = new HashMap<>();
                argsProperty.put("type", "array");
                argsProperty.put("description", "方法参数列表");
                argsProperty.put("items", new HashMap<>()); // Empty schema matches anything
                properties.put("args", argsProperty);
                required.add("args");
            }
        } catch (Exception e) {
            log.error("❌ 创建 inputSchema 失败: {}.{}, error={}", 
                    interfaceName, methodName, e.getMessage(), e);
            // 发生错误时，使用通用 schema
            Map<String, Object> argsProperty = new HashMap<>();
            argsProperty.put("type", "array");
            argsProperty.put("description", "方法参数列表");
            argsProperty.put("items", new HashMap<>()); // Empty schema matches anything
            properties.put("args", argsProperty);
            required.add("args");
        }
        
        inputSchema.put("properties", properties);
        if (!required.isEmpty()) {
            inputSchema.put("required", required);
        }
        
        log.info("✅ inputSchema 创建完成: {}.{}, properties数量={}, required数量={}", 
                interfaceName, methodName, properties.size(), required.size());
        
        return inputSchema;
    }
    
    /**
     * 从 metadata 或方法名模式推断方法签名
     * 优先级：ZooKeeper metadata > 数据库 > 方法名推断
     */
    private MethodSignatureInfo getMethodSignatureFromMetadata(String interfaceName, String methodName) {
        // 1. 优先从 ZooKeeper metadata 获取（最准确）
        if (zooKeeperService != null && providerService != null) {
            MethodSignatureInfo infoFromZK = getMethodSignatureFromZooKeeper(interfaceName, methodName);
            if (infoFromZK != null && infoFromZK.getParameterCount() >= 0) {
                log.info("✅ 从 ZooKeeper metadata 获取到方法签名: {}.{} with {} parameters", 
                        interfaceName, methodName, infoFromZK.getParameterCount());
                return infoFromZK;
            }
        }
        
        // 2. 从 MethodSignatureResolver 获取（从数据库）
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
        
        // 3. 基于方法名模式推断参数（fallback）
        log.warn("⚠️ 无法从 metadata 或数据库获取方法签名，使用方法名推断: {}.{}", interfaceName, methodName);
        return inferMethodSignatureFromName(methodName, interfaceName);
    }
    
    /**
     * 从 ZooKeeper metadata 获取方法签名
     * 路径格式：/dubbo/metadata/{interfaceName}/{version}/{group}/provider/{application}
     */
    private MethodSignatureInfo getMethodSignatureFromZooKeeper(String interfaceName, String methodName) {
        if (zooKeeperService == null || providerService == null) {
            return null;
        }
        
        CuratorFramework client = zooKeeperService.getClient();
        if (client == null) {
            log.debug("   ZooKeeper 客户端未初始化");
            return null;
        }
        
        try {
            // 获取 Provider 信息
            List<ProviderInfo> providers = providerService.getAllProviders().stream()
                    .filter(p -> interfaceName.equals(p.getInterfaceName()))
                    .filter(ProviderInfo::isOnline)
                    .toList();
            
            if (providers.isEmpty()) {
                log.debug("   未找到可用的 Provider: {}", interfaceName);
                return null;
            }
            
            // 使用第一个可用的 Provider
            ProviderInfo provider = providers.get(0);
            String version = provider.getVersion() != null ? provider.getVersion() : "1.0.0";
            String group = provider.getGroup() != null && !provider.getGroup().isEmpty() ? provider.getGroup() : "";
            String application = provider.getApplication() != null ? provider.getApplication() : "";
            
            log.debug("   从 ZooKeeper metadata 获取方法签名: interface={}, method={}, version={}, group={}, application={}", 
                    interfaceName, methodName, version, group, application);
            
            // 构建 metadata 路径（优先使用用户指定的路径格式）
            List<String> metadataPaths = new ArrayList<>();
            
            // 格式1（优先）: /dubbo/metadata/{interfaceName}/{version}/{group}/provider/{application}
            if (!group.isEmpty() && !application.isEmpty()) {
                String path1 = String.format("/dubbo/metadata/%s/%s/%s/provider/%s", 
                        interfaceName, version, group, application);
                metadataPaths.add(path1);
                log.debug("   尝试路径1: {}", path1);
            }
            
            // 格式2: /dubbo/metadata/{interfaceName}/{version}/provider/{application}
            if (!application.isEmpty()) {
                String path2 = String.format("/dubbo/metadata/%s/%s/provider/%s", 
                        interfaceName, version, application);
                metadataPaths.add(path2);
                log.debug("   尝试路径2: {}", path2);
            }
            
            // 格式3: /dubbo/metadata/{interfaceName}/provider/{application}
            if (!application.isEmpty()) {
                String path3 = String.format("/dubbo/metadata/%s/provider/%s", 
                        interfaceName, application);
                metadataPaths.add(path3);
                log.debug("   尝试路径3: {}", path3);
            }
            
            // 格式4: /dubbo/metadata/{interfaceName}/provider
            String path4 = String.format("/dubbo/metadata/%s/provider", interfaceName);
            metadataPaths.add(path4);
            log.debug("   尝试路径4: {}", path4);
            
            // 尝试读取 metadata
            for (String metadataPath : metadataPaths) {
                try {
                    if (client.checkExists().forPath(metadataPath) != null) {
                        log.debug("   找到 metadata 路径: {}", metadataPath);
                        
                        // 如果是目录，尝试读取目录下的所有节点
                        if (metadataPath.endsWith("/provider") || metadataPath.endsWith("/provider/")) {
                            List<String> children = client.getChildren().forPath(metadataPath);
                            if (children != null && !children.isEmpty()) {
                                for (String child : children) {
                                    String childPath = metadataPath + "/" + child;
                                    MethodSignatureInfo info = parseMethodSignatureFromMetadata(client, childPath, methodName);
                                    if (info != null) {
                                        log.info("   ✅ 从子节点 {} 成功获取方法签名", childPath);
                                        return info;
                                    }
                                }
                            }
                        } else {
                            // 直接读取文件
                            MethodSignatureInfo info = parseMethodSignatureFromMetadata(client, metadataPath, methodName);
                            if (info != null) {
                                log.info("   ✅ 从文件 {} 成功获取方法签名", metadataPath);
                                return info;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("   读取 metadata 路径失败: {}, error: {}", metadataPath, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.warn("   ❌ 从 ZooKeeper metadata 获取方法签名失败: interface={}, method={}, error={}", 
                    interfaceName, methodName, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 从 metadata JSON 中解析方法签名
     */
    private MethodSignatureInfo parseMethodSignatureFromMetadata(CuratorFramework client, String metadataPath, String methodName) {
        try {
            byte[] data = client.getData().forPath(metadataPath);
            if (data == null || data.length == 0) {
                return null;
            }
            
            String metadataJson = new String(data, StandardCharsets.UTF_8);
            JsonNode rootNode = objectMapper.readTree(metadataJson);
            JsonNode methodsNode = rootNode.get("methods");
            
            if (methodsNode == null || !methodsNode.isArray()) {
                return null;
            }
            
            for (JsonNode methodNode : methodsNode) {
                JsonNode nameNode = methodNode.get("name");
                if (nameNode != null && methodName.equals(nameNode.asText())) {
                    // 找到目标方法，解析 parameterTypes
                    MethodSignatureInfo info = new MethodSignatureInfo();
                    
                    JsonNode parameterTypesNode = methodNode.get("parameterTypes");
                    if (parameterTypesNode != null && parameterTypesNode.isArray()) {
                        int paramIndex = 0;
                        for (JsonNode typeNode : parameterTypesNode) {
                            String paramType = typeNode.asText();
                            MethodParameter param = new MethodParameter();
                            
                            // 尝试从 metadata 获取参数名（如果有 parameterNames 字段）
                            JsonNode parameterNamesNode = methodNode.get("parameterNames");
                            String paramName;
                            if (parameterNamesNode != null && parameterNamesNode.isArray() && 
                                paramIndex < parameterNamesNode.size()) {
                                paramName = parameterNamesNode.get(paramIndex).asText();
                            } else {
                                // 如果没有参数名，使用默认名称或从类型推断
                                paramName = inferParameterNameFromType(paramType, paramIndex);
                            }
                            
                            param.setName(paramName);
                            param.setType(paramType);
                            info.getParameters().add(param);
                            paramIndex++;
                        }
                        info.setParameterCount(info.getParameters().size());
                        log.debug("   ✅ 成功解析方法签名: {} 个参数", info.getParameterCount());
                        return info;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("   解析 metadata JSON 失败: path={}, error={}", metadataPath, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 从类型推断参数名
     */
    private String inferParameterNameFromType(String paramType, int index) {
        // 如果是 POJO 类型，提取类名作为参数名
        if (paramType != null && paramType.contains(".")) {
            String simpleName = paramType.substring(paramType.lastIndexOf(".") + 1);
            // 转换为驼峰命名：User -> user, OrderItem -> orderItem
            if (!simpleName.isEmpty()) {
                return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
            }
        }
        
        // 基础类型使用默认名称
        if (paramType != null) {
            if (paramType.contains("Long") || paramType.equals("long")) {
                return "id";
            } else if (paramType.contains("String")) {
                return "name";
            } else if (paramType.contains("Integer") || paramType.equals("int")) {
                return "value";
            }
        }
        
        // 默认使用 param0, param1 等
        return "param" + index;
    }
    
    /**
     * 基于方法名模式推断方法签名
     * 这是临时方案，理想情况下应该从 ZooKeeper metadata 读取
     */
    private MethodSignatureInfo inferMethodSignatureFromName(String methodName, String interfaceName) {
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
                String inferredType = inferPOJOTypeFromMethodName(methodName, entityName, interfaceName);
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
                String inferredType = inferPOJOTypeFromMethodName(methodName, entityName, interfaceName);
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
    /**
     * 从方法名推断 POJO 类型
     * 优先尝试根据 interfaceName 推断所在的包，然后推断 model 包
     */
    private String inferPOJOTypeFromMethodName(String methodName, String entityName, String interfaceName) {
        // 从方法名提取实体名（首字母大写）
        String entityType = null;
        if (methodName.startsWith("create")) {
            String extracted = methodName.substring(6); // 跳过 "create"
            if (!extracted.isEmpty()) {
                entityType = extracted;
            }
        } else if (methodName.startsWith("add")) {
            String extracted = methodName.substring(3); // 跳过 "add"
            if (!extracted.isEmpty()) {
                entityType = extracted;
            }
        } else if (methodName.startsWith("update")) {
            String extracted = methodName.substring(6); // 跳过 "update"
            if (!extracted.isEmpty()) {
                entityType = extracted;
            }
        }
        
        // 从 entityName 推断
        if (entityType == null && entityName != null) {
            entityType = Character.toUpperCase(entityName.charAt(0)) + entityName.substring(1);
        }

        if (entityType == null) {
            return null;
        }

        // 1. 尝试根据 interfaceName 推断包名
        if (interfaceName != null && interfaceName.contains(".")) {
            // 假设目录结构: ...service.UserService -> ...model.User
            String packageName = interfaceName.substring(0, interfaceName.lastIndexOf("."));
            String modelPackage = null;
            
            if (packageName.endsWith(".service")) {
                // ...service -> ...model
                modelPackage = packageName.substring(0, packageName.lastIndexOf(".service")) + ".model";
            } else if (packageName.endsWith(".api")) {
                // ...api -> ...model
                modelPackage = packageName.substring(0, packageName.lastIndexOf(".api")) + ".model";
            } else {
                // 尝试直接 append .model
                modelPackage = packageName + ".model";
            }
            
            if (modelPackage != null) {
                String inferredClass = modelPackage + "." + entityType;
                log.debug("🎯 Inferred POJO type from interface: {} -> {}", interfaceName, inferredClass);
                return inferredClass;
            }
        }
        
        // 2. 常见实体类型映射（作为后备）
        Map<String, String> entityTypeMap = new HashMap<>();
        entityTypeMap.put("user", "com.pajk.mcpmetainfo.core.demo.model.User");
        entityTypeMap.put("order", "com.pajk.mcpmetainfo.core.demo.model.Order");
        entityTypeMap.put("product", "com.pajk.mcpmetainfo.core.demo.model.Product");
        
        String lowerCaseType = entityType.toLowerCase();
        if (entityTypeMap.containsKey(lowerCaseType)) {
            return entityTypeMap.get(lowerCaseType);
        }
        
        // 3. 默认回退到 demo model
        return "com.pajk.mcpmetainfo.core.demo.model." + entityType;
    }
    
    /**
     * 获取参数描述（基于类型名）
     */
    private String getParameterDescriptionFromType(String typeName, String paramName) {
        String simpleType = typeName.contains(".") ? 
                typeName.substring(typeName.lastIndexOf(".") + 1) : typeName;
        // 添加 (类型: <typeName>) 格式，以便 McpProtocolService 可以提取它
        return String.format("%s 类型的参数 %s (类型: %s)", simpleType, paramName, typeName);
    }
    
    /**
     * 将 Java 类型名转换为 JSON Schema 类型
     */
    private String getJsonTypeFromJavaTypeName(String javaTypeName) {
        if (javaTypeName == null || javaTypeName.isEmpty()) {
            return "string"; // Default to string instead of invalid 'any'
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
        log.info("🔍 extractMethodParameters: interface={}, method={}, params={}", 
                interfaceName, methodName, params != null ? params.keySet() : "null");
        
        try {
            // 从 metadata 获取方法签名
            MethodSignatureInfo methodInfo = getMethodSignatureFromMetadata(interfaceName, methodName);
            
            if (methodInfo != null && methodInfo.getParameterCount() > 0) {
                List<MethodParameter> parameters = methodInfo.getParameters();
                Object[] args = new Object[parameters.size()];
                
                log.info("📋 Method signature found: {} parameters", parameters.size());
                for (int i = 0; i < parameters.size(); i++) {
                    MethodParameter param = parameters.get(i);
                    log.debug("   Parameter[{}]: name={}, type={}", i, param.getName(), param.getType());
                }
                
                // 检测 Dubbo 版本（简化处理，默认使用 2.x）
                String dubboVersion = "2.x"; // TODO: 从 ProviderInfo 获取实际版本
                
                boolean hasMissingParams = false;
                for (int i = 0; i < parameters.size(); i++) {
                    MethodParameter param = parameters.get(i);
                    Object rawValue = params.get(param.getName());
                    
                    if (rawValue == null) {
                        log.warn("⚠️ Parameter[{}] '{}' not found in params Map. Available keys: {}", 
                                i, param.getName(), params.keySet());
                        hasMissingParams = true;
                    }
                    
                    // 使用 ParameterConverter 转换参数类型
                    if (parameterConverter != null && rawValue != null && param.getType() != null) {
                        args[i] = parameterConverter.convertToJavaObject(rawValue, param.getType(), dubboVersion);
                        log.debug("✅ Converted parameter[{}] {}: {} -> {}", 
                                i, param.getName(), rawValue.getClass().getSimpleName(), param.getType());
                    } else {
                        args[i] = rawValue;
                        if (rawValue != null) {
                            log.debug("✅ Using parameter[{}] {} as-is: {}", i, param.getName(), rawValue);
                        }
                    }
                }
                
                // 如果参数名不匹配导致参数丢失，尝试从 params Map 中提取所有非系统字段
                if (hasMissingParams && params != null && !params.isEmpty()) {
                    log.warn("⚠️ Some parameters missing by name, attempting to extract from params Map");
                    List<Object> extractedArgs = new ArrayList<>();
                    for (Map.Entry<String, Object> entry : params.entrySet()) {
                        String key = entry.getKey();
                        if (!key.equals("timeout") && !key.equals("args")) {
                            extractedArgs.add(entry.getValue());
                            log.debug("   ✅ Extracted parameter: {} = {}", key, entry.getValue());
                        }
                    }
                    if (!extractedArgs.isEmpty() && extractedArgs.size() == parameters.size()) {
                        log.info("✅ Extracted {} parameters from params Map (matched parameter count)", 
                                extractedArgs.size());
                        return extractedArgs.toArray();
                    } else if (!extractedArgs.isEmpty()) {
                        log.warn("⚠️ Extracted {} parameters but method signature expects {}", 
                                extractedArgs.size(), parameters.size());
                        // 仍然返回提取的参数，让调用方处理
                        return extractedArgs.toArray();
                    }
                }
                
                return args;
            } else if (methodInfo != null && methodInfo.getParameterCount() == 0) {
                // 方法签名显示无参数，但检查 params 中是否有参数值
                // 如果 params 不为空且不包含 "args" 字段，说明可能有参数但方法签名不正确
                if (params != null && !params.isEmpty() && !params.containsKey("args")) {
                    log.warn("⚠️ Method signature shows no parameters for {}.{}, but params Map is not empty: {}. " +
                            "Attempting to extract parameters from params Map.", 
                            interfaceName, methodName, params.keySet());
                    
                    // 尝试从 params Map 中提取参数值（按常见参数名模式）
                    List<Object> extractedArgs = new ArrayList<>();
                    
                    // 常见参数名模式：productId, userId, orderId, id 等
                    String[] commonParamNames = {
                        "productId", "userId", "orderId", "id",
                        "product", "user", "order",
                        "productName", "userName", "orderName"
                    };
                    
                    for (String paramName : commonParamNames) {
                        if (params.containsKey(paramName)) {
                            extractedArgs.add(params.get(paramName));
                            log.debug("   ✅ Extracted parameter from params: {} = {}", paramName, params.get(paramName));
                        }
                    }
                    
                    // 如果找到了参数，返回它们
                    if (!extractedArgs.isEmpty()) {
                        log.info("✅ Extracted {} parameters from params Map despite method signature showing 0 parameters", 
                                extractedArgs.size());
                        return extractedArgs.toArray();
                    }
                    
                    // 如果没找到常见参数名，尝试将所有非系统字段作为参数
                    // 排除系统字段：timeout, args 等
                    for (Map.Entry<String, Object> entry : params.entrySet()) {
                        String key = entry.getKey();
                        if (!key.equals("timeout") && !key.equals("args")) {
                            extractedArgs.add(entry.getValue());
                            log.debug("   ✅ Extracted parameter from params: {} = {}", key, entry.getValue());
                        }
                    }
                    
                    if (!extractedArgs.isEmpty()) {
                        log.info("✅ Extracted {} parameters from params Map (all non-system fields)", 
                                extractedArgs.size());
                        return extractedArgs.toArray();
                    }
                }
                
                // 真正的无参数方法
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
                
                // 如果 params 不为空，尝试提取参数
                if (params != null && !params.isEmpty() && !params.containsKey("args")) {
                    log.warn("⚠️ Attempting to extract parameters from params Map as fallback");
                    List<Object> extractedArgs = new ArrayList<>();
                    for (Map.Entry<String, Object> entry : params.entrySet()) {
                        String key = entry.getKey();
                        if (!key.equals("timeout") && !key.equals("args")) {
                            extractedArgs.add(entry.getValue());
                        }
                    }
                    if (!extractedArgs.isEmpty()) {
                        log.info("✅ Extracted {} parameters from params Map as fallback", extractedArgs.size());
                        return extractedArgs.toArray();
                    }
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
            
            // 如果 params 不为空，尝试提取参数
            if (params != null && !params.isEmpty()) {
                log.warn("⚠️ Attempting to extract parameters from params Map after error");
                List<Object> extractedArgs = new ArrayList<>();
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    String key = entry.getKey();
                    if (!key.equals("timeout") && !key.equals("args")) {
                        extractedArgs.add(entry.getValue());
                    }
                }
                if (!extractedArgs.isEmpty()) {
                    log.info("✅ Extracted {} parameters from params Map after error", extractedArgs.size());
                    return extractedArgs.toArray();
                }
            }
            
            return new Object[0];
        }
    }
    
    /**
     * 获取方法的参数类型列表
     * 用于MCP工具定义中的 parameterTypes 字段
     * 
     * @param interfaceName 接口全限定名
     * @param methodName 方法名
     * @return 参数类型列表，如 ["java.lang.Long", "java.lang.String"]，如果无法获取则返回空列表
     */
    public List<String> getParameterTypes(String interfaceName, String methodName) {
        log.debug("获取参数类型: interface={}, method={}", interfaceName, methodName);
        
        try {
            MethodSignatureInfo methodInfo = getMethodSignatureFromMetadata(interfaceName, methodName);
            
            if (methodInfo != null && methodInfo.getParameters() != null) {
                return methodInfo.getParameters().stream()
                        .map(MethodParameter::getType)
                        .filter(type -> type != null && !type.isEmpty())
                        .toList();
            }
        } catch (Exception e) {
            log.warn("获取参数类型失败: {}.{}, error={}", interfaceName, methodName, e.getMessage());
        }
        
        return Collections.emptyList();
    }
}
