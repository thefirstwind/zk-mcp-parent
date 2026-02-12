package com.pajk.mcpmetainfo.core.util;

import com.pajk.mcpmetainfo.core.service.DubboServiceDbService;
import com.pajk.mcpmetainfo.core.service.DubboServiceMethodService;
import com.pajk.mcpmetainfo.persistence.entity.DubboMethodParameterEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceMethodEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * 增强版 MCP 工具生成器
 * 使用 zk_dubbo_method_parameter 表的详细信息生成更精准的 tools
 * 确保大语言模型能更明确地组合参数调用 tools/call
 */
@Slf4j
@Component
public class EnhancedMcpToolGenerator {
    
    @Autowired(required = false)
    private DubboServiceDbService dubboServiceDbService;
    
    @Autowired(required = false)
    private DubboServiceMethodService dubboServiceMethodService;
    
    @Autowired(required = false)
    private McpToolSchemaGenerator mcpToolSchemaGenerator;
    
    /**
     * 生成增强版的工具定义
     * 
     * @param interfaceName 接口全限定名
     * @param methodName 方法名
     * @return 工具定义 Map，包含 name, description, inputSchema
     */
    public Map<String, Object> generateEnhancedTool(String interfaceName, String methodName) {
        Map<String, Object> tool = new HashMap<>();
        
        // 1. 工具名称
        String toolName = interfaceName + "." + methodName;
        tool.put("name", toolName);
        
        // 2. 生成增强版的描述（包含返回类型和方法功能说明）
        String description = generateEnhancedDescription(interfaceName, methodName);
        tool.put("description", description);
        
        // 3. 生成增强版的 inputSchema（使用数据库中的详细信息）
        Map<String, Object> inputSchema = generateEnhancedInputSchema(interfaceName, methodName);
        tool.put("inputSchema", inputSchema);
        
        return tool;
    }
    
    /**
     * 生成增强版的工具描述
     * 包含：方法功能说明、参数说明、返回类型
     */
    private String generateEnhancedDescription(String interfaceName, String methodName) {
        StringBuilder desc = new StringBuilder();
        
        // 1. 方法功能说明（基于方法名推断）
        String methodPurpose = inferMethodPurpose(methodName);
        desc.append(methodPurpose);
        
        // 2. 获取返回类型（从数据库）
        String returnType = getReturnTypeFromDatabase(interfaceName, methodName);
        if (returnType != null && !returnType.isEmpty()) {
            String simpleReturnType = getSimpleTypeName(returnType);
            desc.append(" 返回类型: ").append(simpleReturnType);
        }
        
        // 3. 获取参数信息（从数据库）
        List<DubboMethodParameterEntity> parameters = getParametersFromDatabase(interfaceName, methodName);
        if (parameters != null && !parameters.isEmpty()) {
            desc.append(" 参数: ");
            List<String> paramDescs = new ArrayList<>();
            for (DubboMethodParameterEntity param : parameters) {
                String paramName = param.getParameterName() != null ? param.getParameterName() : "param" + param.getParameterOrder();
                String paramType = getSimpleTypeName(param.getParameterType());
                String paramDesc = paramName + "(" + paramType + ")";
                if (param.getParameterDescription() != null && !param.getParameterDescription().isEmpty()) {
                    paramDesc += " - " + param.getParameterDescription();
                }
                paramDescs.add(paramDesc);
            }
            desc.append(String.join(", ", paramDescs));
        } else {
            desc.append(" 无参数");
        }
        
        return desc.toString();
    }
    
    /**
     * 生成增强版的 inputSchema
     * 优先级：反射获取原接口定义 > 数据库 > mcpToolSchemaGenerator
     */
    private Map<String, Object> generateEnhancedInputSchema(String interfaceName, String methodName) {
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        
        try {
            // 1. 优先通过反射获取原接口的参数定义
            List<ReflectionParameterInfo> reflectionParams = getParametersFromReflection(interfaceName, methodName);
            
            if (reflectionParams != null && !reflectionParams.isEmpty()) {
                log.info("✅ Found {} parameters via reflection for {}.{}", 
                        reflectionParams.size(), interfaceName, methodName);
                
                // 使用反射获取的参数信息
                for (ReflectionParameterInfo param : reflectionParams) {
                    String paramName = param.getName();
                    String paramType = param.getType();
                    
                    log.debug("    Parameter[{}]: {} ({})", param.getOrder(), paramName, paramType);
                    
                    // 尝试从数据库获取描述信息（如果有）
                    String paramDescription = getParameterDescriptionFromDatabase(interfaceName, methodName, param.getOrder());
                    
                    Map<String, Object> paramProperty = createEnhancedParameterProperty(
                            paramName, paramType, paramDescription, param.getOrder());
                    
                    properties.put(paramName, paramProperty);
                    required.add(paramName);
                }
            } else {
                // 2. 如果反射失败，从数据库获取参数信息
                List<DubboMethodParameterEntity> parameters = getParametersFromDatabase(interfaceName, methodName);
                
                if (parameters != null && !parameters.isEmpty()) {
                    // 按 parameterOrder 排序
                    parameters.sort(Comparator.comparing(DubboMethodParameterEntity::getParameterOrder));
                    
                    log.info("✅ Found {} parameters in database for {}.{}", 
                            parameters.size(), interfaceName, methodName);
                    
                    // 为每个参数创建详细的属性定义
                    for (DubboMethodParameterEntity param : parameters) {
                        String paramName = param.getParameterName();
                        if (paramName == null || paramName.isEmpty()) {
                            paramName = "param" + param.getParameterOrder();
                        }
                        
                        String paramType = param.getParameterType();
                        String paramDescription = param.getParameterDescription();
                        
                        log.debug("    Parameter[{}]: {} ({})", param.getParameterOrder(), paramName, paramType);
                        
                        Map<String, Object> paramProperty = createEnhancedParameterProperty(
                                paramName, paramType, paramDescription, param.getParameterOrder());
                        
                        properties.put(paramName, paramProperty);
                        required.add(paramName);
                    }
                } else {
                    // 3. 如果数据库也没有，回退到使用 mcpToolSchemaGenerator
                    log.debug("⚠️ No parameters found via reflection or database for {}.{}, falling back to mcpToolSchemaGenerator", 
                            interfaceName, methodName);
                    if (mcpToolSchemaGenerator != null) {
                        return mcpToolSchemaGenerator.createInputSchemaFromMethod(interfaceName, methodName);
                    }
                    // 无参数方法
                    inputSchema.put("properties", properties);
                    return inputSchema;
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Error generating enhanced inputSchema for {}.{}: {}, falling back to mcpToolSchemaGenerator", 
                    interfaceName, methodName, e.getMessage(), e);
            // 发生错误时，回退到使用 mcpToolSchemaGenerator
            if (mcpToolSchemaGenerator != null) {
                return mcpToolSchemaGenerator.createInputSchemaFromMethod(interfaceName, methodName);
            }
        }
        
        inputSchema.put("properties", properties);
        if (!required.isEmpty()) {
            inputSchema.put("required", required);
        }
        
        // 添加 additionalProperties: false（符合参考标准）
        inputSchema.put("additionalProperties", false);
        
        return inputSchema;
    }
    
    /**
     * 创建增强版的参数属性定义
     * 包含：类型、格式、描述、示例值、约束等
     */
    private Map<String, Object> createEnhancedParameterProperty(
            String paramName, String paramType, String paramDescription, Integer paramOrder) {
        Map<String, Object> property = new HashMap<>();
        
        // 1. 类型转换（Java 类型 -> JSON Schema 类型）
        String jsonType = convertJavaTypeToJsonType(paramType);
        property.put("type", jsonType);
        
        // 2. 添加 format 字段（符合参考标准：int64, int32等）
        String format = getJsonFormatFromJavaType(paramType);
        if (format != null) {
            property.put("format", format);
        }
        
        // 3. 描述（优先使用数据库中的描述，否则生成）
        String description = generateParameterDescription(paramName, paramType, paramDescription);
        property.put("description", description);
        
        // 4. 示例值（根据类型生成）
        Object example = generateExampleValue(paramType, paramName);
        if (example != null) {
            property.put("examples", Collections.singletonList(example));
        }
        
        // 4. 数组/集合类型的 items 定义
        if ("array".equals(jsonType)) {
            Map<String, Object> items = createArrayItemsDefinition(paramType);
            property.put("items", items);
        }
        
        // 5. 对象类型的 properties 定义
        // 通过反射获取原类的字段定义，遵循原接口的字段结构
        if ("object".equals(jsonType) && isComplexObjectType(paramType)) {
            Map<String, Object> objectProperties = createObjectPropertiesDefinition(paramType);
            if (objectProperties != null && !objectProperties.isEmpty()) {
                property.put("properties", objectProperties);
                property.put("additionalProperties", false);
                log.debug("   ✅ Added {} properties for complex object type {} via reflection", 
                        objectProperties.size(), paramType);
            }
        }
        
        return property;
    }
    
    /**
     * 生成参数描述
     * 优先使用数据库中的 parameter_description，如果没有则生成清晰的描述
     * 格式参考：Person's ID, Person's first name 等
     */
    /**
     * 生成参数描述
     * 优先使用数据库中的 parameter_description，如果没有则生成清晰的描述
     * 格式参考：Person's ID, Person's first name 等
     */
    private String generateParameterDescription(String paramName, String paramType, String dbDescription) {
        String description;
        if (dbDescription != null && !dbDescription.trim().isEmpty()) {
            // 优先使用数据库中的描述
            description = dbDescription;
        } else {
            // 如果数据库中没有描述，生成清晰的描述
            // 将参数名转换为更友好的描述格式
            description = formatParameterNameToDescription(paramName);
            String simpleType = getSimpleTypeName(paramType);
            
            // 如果参数名已经是友好的格式，直接使用；否则添加类型信息
            if (description.equals(paramName)) {
                description = description + " (" + simpleType + ")";
            }
        }
        
        // 添加 (类型: <typeName>) 格式，以便 McpProtocolService 可以提取它
        // 注意：paramType 应该是全限定名
        return description + " (类型: " + paramType + ")";
    }
    
    /**
     * 将参数名转换为更友好的描述格式
     * 例如：userId -> User's ID, firstName -> First name
     */
    private String formatParameterNameToDescription(String paramName) {
        if (paramName == null || paramName.isEmpty()) {
            return "parameter";
        }
        
        // 处理驼峰命名：userId -> User's ID
        if (paramName.matches("^[a-z]+[A-Z].*")) {
            // 找到第一个大写字母的位置
            int firstUpperIndex = -1;
            for (int i = 0; i < paramName.length(); i++) {
                if (Character.isUpperCase(paramName.charAt(i))) {
                    firstUpperIndex = i;
                    break;
                }
            }
            
            if (firstUpperIndex > 0) {
                String firstPart = paramName.substring(0, firstUpperIndex);
                String secondPart = paramName.substring(firstUpperIndex);
                
                // 转换为友好的描述
                String entityName = capitalizeFirstLetter(firstPart);
                String fieldName = capitalizeFirstLetter(secondPart);
                
                // 如果以 Id 结尾，转换为 "Entity's ID"
                if (fieldName.equals("Id")) {
                    return entityName + "'s ID";
                }
                // 如果以 Name 结尾，转换为 "Entity's Name"
                else if (fieldName.equals("Name")) {
                    return entityName + "'s " + fieldName;
                }
                // 其他情况：Entity's FieldName
                else {
                    return entityName + "'s " + fieldName;
                }
            }
        }
        
        // 处理下划线命名：first_name -> First name
        if (paramName.contains("_")) {
            String[] parts = paramName.split("_");
            StringBuilder desc = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    desc.append(" ");
                }
                desc.append(capitalizeFirstLetter(parts[i]));
            }
            return desc.toString();
        }
        
        // 默认情况：首字母大写
        return capitalizeFirstLetter(paramName);
    }
    
    /**
     * 首字母大写
     */
    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1).toLowerCase();
    }
    
    
    /**
     * 生成示例值
     * 只提供基础类型的示例，复杂对象不提供具体字段示例
     */
    private Object generateExampleValue(String paramType, String paramName) {
        if (paramType == null || paramType.isEmpty()) {
            return null;
        }
        
        // 只提供基础类型的示例值
        if (paramType.equals("java.lang.String") || paramType.equals("String")) {
            return "string_value";
        } else if (paramType.equals("java.lang.Long") || paramType.equals("long")) {
            return 0L;
        } else if (paramType.equals("java.lang.Integer") || paramType.equals("int")) {
            return 0;
        } else if (paramType.equals("java.lang.Double") || paramType.equals("double")) {
            return 0.0;
        } else if (paramType.equals("java.lang.Float") || paramType.equals("float")) {
            return 0.0f;
        } else if (paramType.equals("java.lang.Boolean") || paramType.equals("boolean")) {
            return false;
        } else if (paramType.contains("List") || paramType.contains("[]")) {
            return Collections.emptyList();
        } else if (paramType.contains("Map")) {
            return Collections.emptyMap();
        }
        // 复杂对象类型不提供示例，避免硬编码字段结构
        
        return null;
    }
    
    /**
     * 创建数组类型的 items 定义
     */
    private Map<String, Object> createArrayItemsDefinition(String paramType) {
        Map<String, Object> items = new HashMap<>();
        
        // 提取数组元素类型
        String elementType = extractElementType(paramType);
        if (elementType != null) {
            String jsonType = convertJavaTypeToJsonType(elementType);
            items.put("type", jsonType);
            
            // 复杂对象的字段结构应该从 metadata 动态获取，不硬编码
        } else {
            items.put("type", "any");
        }
        
        return items;
    }
    
    /**
     * 创建对象类型的 properties 定义
     * 通过反射获取原类的字段定义，遵循原接口的字段结构
     */
    private Map<String, Object> createObjectPropertiesDefinition(String paramType) {
        try {
            // 通过反射获取类的字段定义
            Class<?> clazz = Class.forName(paramType);
            return getClassFieldsFromReflection(clazz);
        } catch (ClassNotFoundException e) {
            log.debug("⚠️ Class not found in classpath: {}, cannot get fields via reflection", paramType);
            // 类不在 classpath 中，无法通过反射获取字段
            return null;
        } catch (Exception e) {
            log.warn("⚠️ Failed to get fields via reflection for class {}: {}", paramType, e.getMessage());
            return null;
        }
    }
    
    /**
     * 通过反射获取类的字段定义
     * 返回字段名 -> 字段类型的映射，用于构建 JSON Schema 的 properties
     */
    private Map<String, Object> getClassFieldsFromReflection(Class<?> clazz) {
        Map<String, Object> properties = new HashMap<>();
        
        try {
            // 获取所有字段（包括父类的字段）
            List<Field> allFields = new ArrayList<>();
            Class<?> currentClass = clazz;
            while (currentClass != null && currentClass != Object.class) {
                Field[] fields = currentClass.getDeclaredFields();
                for (Field field : fields) {
                    // 跳过静态字段和某些系统字段
                    if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        allFields.add(field);
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
            
            if (allFields.isEmpty()) {
                log.debug("   Class {} has no fields", clazz.getName());
                return null;
            }
            
            log.debug("   ✅ Found {} fields in class {} via reflection", allFields.size(), clazz.getName());
            
            // 为每个字段创建属性定义
            for (Field field : allFields) {
                field.setAccessible(true); // 允许访问私有字段
                
                String fieldName = field.getName();
                Class<?> fieldType = field.getType();
                String fieldTypeName = fieldType.getName();
                
                Map<String, Object> fieldProperty = new HashMap<>();
                
                // 转换 Java 类型到 JSON Schema 类型
                String jsonType = convertJavaTypeToJsonType(fieldTypeName);
                fieldProperty.put("type", jsonType);
                
                // 添加 format 字段（符合参考标准）
                String format = getJsonFormatFromJavaType(fieldTypeName);
                if (format != null) {
                    fieldProperty.put("format", format);
                }
                
                // 字段描述：使用字段名和类型（格式：字段名 (类型)）
                fieldProperty.put("description", fieldName + " (" + getSimpleTypeName(fieldTypeName) + ")");
                
                // 如果是数组类型，设置 items
                if ("array".equals(jsonType)) {
                    String elementType = extractElementType(fieldTypeName);
                    if (elementType != null) {
                        Map<String, Object> items = new HashMap<>();
                        String elementJsonType = convertJavaTypeToJsonType(elementType);
                        items.put("type", elementJsonType);
                        
                        // 如果数组元素是复杂对象，递归获取其字段结构
                        if ("object".equals(elementJsonType) && isComplexObjectType(elementType)) {
                            try {
                                Class<?> elementClass = Class.forName(elementType);
                                Map<String, Object> elementProperties = getClassFieldsFromReflection(elementClass);
                                if (elementProperties != null && !elementProperties.isEmpty()) {
                                    items.put("properties", elementProperties);
                                    items.put("additionalProperties", false);
                                }
                            } catch (ClassNotFoundException e) {
                                log.debug("   Element class {} not found in classpath", elementType);
                            }
                        }
                        fieldProperty.put("items", items);
                    }
                }
                
                // 如果是复杂对象类型，递归获取其字段结构
                if ("object".equals(jsonType) && isComplexObjectType(fieldTypeName)) {
                    Map<String, Object> nestedProperties = getClassFieldsFromReflection(fieldType);
                    if (nestedProperties != null && !nestedProperties.isEmpty()) {
                        fieldProperty.put("properties", nestedProperties);
                        fieldProperty.put("additionalProperties", false);
                    }
                }
                
                properties.put(fieldName, fieldProperty);
            }
            
            return properties;
            
        } catch (Exception e) {
            log.warn("⚠️ Error getting fields from class {} via reflection: {}", clazz.getName(), e.getMessage());
            return null;
        }
    }
    
    
    /**
     * 通过反射获取原接口的参数定义
     * 遵循原接口的字段定义，包括参数名、参数类型
     */
    private List<ReflectionParameterInfo> getParametersFromReflection(String interfaceName, String methodName) {
        try {
            Class<?> interfaceClass = Class.forName(interfaceName);
            Method[] methods = interfaceClass.getMethods();
            
            for (Method method : methods) {
                if (method.getName().equals(methodName)) {
                    Parameter[] parameters = method.getParameters();
                    Class<?>[] paramTypes = method.getParameterTypes();
                    
                    List<ReflectionParameterInfo> paramInfos = new ArrayList<>();
                    
                    for (int i = 0; i < parameters.length; i++) {
                        Parameter param = parameters[i];
                        ReflectionParameterInfo paramInfo = new ReflectionParameterInfo();
                        paramInfo.setOrder(i);
                        
                        // 获取参数名（如果编译时保留了参数名信息）
                        if (param.isNamePresent()) {
                            paramInfo.setName(param.getName());
                        } else {
                            // 如果没有参数名，使用默认名称
                            paramInfo.setName("param" + i);
                        }
                        
                        // 获取参数类型（完整类名）
                        paramInfo.setType(paramTypes[i].getName());
                        
                        paramInfos.add(paramInfo);
                    }
                    
                    log.debug("✅ Got {} parameters via reflection for {}.{}", 
                            paramInfos.size(), interfaceName, methodName);
                    return paramInfos;
                }
            }
            
            log.debug("⚠️ Method {} not found in interface {} via reflection", methodName, interfaceName);
            return null;
            
        } catch (ClassNotFoundException e) {
            log.debug("⚠️ Interface class {} not found in classpath, cannot use reflection", interfaceName);
            return null;
        } catch (Exception e) {
            log.warn("⚠️ Failed to get parameters via reflection for {}.{}: {}", 
                    interfaceName, methodName, e.getMessage());
            return null;
        }
    }
    
    /**
     * 从数据库获取指定位置的参数描述
     */
    private String getParameterDescriptionFromDatabase(String interfaceName, String methodName, int paramOrder) {
        try {
            List<DubboMethodParameterEntity> parameters = getParametersFromDatabase(interfaceName, methodName);
            if (parameters != null) {
                for (DubboMethodParameterEntity param : parameters) {
                    if (param.getParameterOrder() != null && param.getParameterOrder() == paramOrder) {
                        return param.getParameterDescription();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("⚠️ Failed to get parameter description from database: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * 反射参数信息
     */
    private static class ReflectionParameterInfo {
        private int order;
        private String name;
        private String type;
        
        public int getOrder() {
            return order;
        }
        
        public void setOrder(int order) {
            this.order = order;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
    }
    
    /**
     * 从数据库获取参数列表
     */
    private List<DubboMethodParameterEntity> getParametersFromDatabase(String interfaceName, String methodName) {
        if (dubboServiceDbService == null || dubboServiceMethodService == null) {
            return null;
        }
        
        try {
            // 1. 查找服务
            DubboServiceEntity service = dubboServiceDbService.findByInterfaceName(interfaceName);
            if (service == null) {
                return null;
            }
            
            // 2. 查找方法
            DubboServiceMethodEntity method = dubboServiceMethodService.findByServiceIdAndMethodName(
                    service.getId(), methodName);
            if (method == null) {
                return null;
            }
            
            // 3. 查找参数
            return dubboServiceMethodService.findParametersByMethodId(method.getId());
        } catch (Exception e) {
            log.warn("⚠️ Failed to get parameters from database for {}.{}: {}", 
                    interfaceName, methodName, e.getMessage());
            return null;
        }
    }
    
    /**
     * 从数据库获取返回类型
     */
    private String getReturnTypeFromDatabase(String interfaceName, String methodName) {
        if (dubboServiceDbService == null || dubboServiceMethodService == null) {
            return null;
        }
        
        try {
            DubboServiceEntity service = dubboServiceDbService.findByInterfaceName(interfaceName);
            if (service == null) {
                return null;
            }
            
            DubboServiceMethodEntity method = dubboServiceMethodService.findByServiceIdAndMethodName(
                    service.getId(), methodName);
            if (method == null) {
                return null;
            }
            
            return method.getReturnType();
        } catch (Exception e) {
            log.debug("⚠️ Failed to get return type from database for {}.{}: {}", 
                    interfaceName, methodName, e.getMessage());
            return null;
        }
    }
    
    /**
     * 生成方法功能说明
     * 不硬编码，只提供通用的方法调用说明
     */
    private String inferMethodPurpose(String methodName) {
        if (methodName == null || methodName.isEmpty()) {
            return "调用方法";
        }
        
        // 不硬编码具体业务含义，只提供通用说明
        return "调用方法: " + methodName;
    }
    
    /**
     * 获取 JSON Schema format 字段（int64, int32等）
     */
    private String getJsonFormatFromJavaType(String javaType) {
        if (javaType == null || javaType.isEmpty()) {
            return null;
        }
        
        // Long -> int64
        if (javaType.equals("long") || javaType.equals("java.lang.Long")) {
            return "int64";
        }
        // Integer -> int32
        else if (javaType.equals("int") || javaType.equals("java.lang.Integer")) {
            return "int32";
        }
        // Float -> float
        else if (javaType.equals("float") || javaType.equals("java.lang.Float")) {
            return "float";
        }
        // Double -> double
        else if (javaType.equals("double") || javaType.equals("java.lang.Double")) {
            return "double";
        }
        
        // 其他类型不设置 format
        return null;
    }
    
    /**
     * 将 Java 类型转换为 JSON Schema 类型
     */
    private String convertJavaTypeToJsonType(String javaType) {
        if (javaType == null || javaType.isEmpty()) {
            return "any";
        }
        
        if (javaType.equals("boolean") || javaType.equals("java.lang.Boolean")) {
            return "boolean";
        } else if (javaType.equals("int") || javaType.equals("java.lang.Integer") ||
                   javaType.equals("long") || javaType.equals("java.lang.Long") ||
                   javaType.equals("short") || javaType.equals("java.lang.Short") ||
                   javaType.equals("byte") || javaType.equals("java.lang.Byte")) {
            return "integer";
        } else if (javaType.equals("float") || javaType.equals("java.lang.Float") ||
                   javaType.equals("double") || javaType.equals("java.lang.Double")) {
            return "number";
        } else if (javaType.equals("java.lang.String") || javaType.equals("String") ||
                   javaType.equals("char") || javaType.equals("java.lang.Character")) {
            return "string";
        } else if (javaType.endsWith("[]") || javaType.contains("List") || 
                   javaType.contains("Set") || javaType.contains("Collection")) {
            return "array";
        } else if (javaType.contains("Map")) {
            return "object";
        } else {
            return "object";
        }
    }
    
    /**
     * 提取数组元素类型
     */
    private String extractElementType(String arrayType) {
        if (arrayType == null || arrayType.isEmpty()) {
            return null;
        }
        
        if (arrayType.endsWith("[]")) {
            return arrayType.substring(0, arrayType.length() - 2);
        } else if (arrayType.contains("List<")) {
            int start = arrayType.indexOf("<") + 1;
            int end = arrayType.indexOf(">");
            if (end > start) {
                return arrayType.substring(start, end).trim();
            }
        } else if (arrayType.contains("Set<")) {
            int start = arrayType.indexOf("<") + 1;
            int end = arrayType.indexOf(">");
            if (end > start) {
                return arrayType.substring(start, end).trim();
            }
        }
        
        return null;
    }
    
    /**
     * 判断是否为复杂对象类型
     */
    private boolean isComplexObjectType(String paramType) {
        if (paramType == null || paramType.isEmpty()) {
            return false;
        }
        
        // 排除基础类型和集合类型
        if (paramType.equals("java.lang.String") || paramType.equals("String") ||
            paramType.equals("java.lang.Integer") || paramType.equals("int") ||
            paramType.equals("java.lang.Long") || paramType.equals("long") ||
            paramType.equals("java.lang.Double") || paramType.equals("double") ||
            paramType.equals("java.lang.Float") || paramType.equals("float") ||
            paramType.equals("java.lang.Boolean") || paramType.equals("boolean") ||
            paramType.contains("List") || paramType.contains("Set") || 
            paramType.contains("Map") || paramType.endsWith("[]")) {
            return false;
        }
        
        // 包含包路径的通常是复杂对象
        return paramType.contains(".");
    }
    
    /**
     * 获取简单类型名（去掉包路径）
     */
    private String getSimpleTypeName(String fullTypeName) {
        if (fullTypeName == null || fullTypeName.isEmpty()) {
            return "Object";
        }
        
        // 处理泛型：List<User> -> User
        if (fullTypeName.contains("<")) {
            int start = fullTypeName.indexOf("<") + 1;
            int end = fullTypeName.indexOf(">");
            if (end > start) {
                fullTypeName = fullTypeName.substring(start, end).trim();
            }
        }
        
        // 处理数组：User[] -> User
        if (fullTypeName.endsWith("[]")) {
            fullTypeName = fullTypeName.substring(0, fullTypeName.length() - 2);
        }
        
        // 提取简单类名
        if (fullTypeName.contains(".")) {
            return fullTypeName.substring(fullTypeName.lastIndexOf(".") + 1);
        }
        
        return fullTypeName;
    }
}

