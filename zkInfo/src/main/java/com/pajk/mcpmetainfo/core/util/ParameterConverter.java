package com.pajk.mcpmetainfo.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 参数转换器
 * 
 * 负责将 MCP tools/call 的 JSON 参数转换为 Java 对象，支持：
 * - 基础类型转换
 * - POJO 对象转换（User、Order、Product 等）
 * - 集合类型转换（List、Set 等）
 * - 嵌套对象转换
 * - Dubbo2/Dubbo3 兼容处理
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-12-17
 */
@Slf4j
@Component
public class ParameterConverter {
    
    private final ObjectMapper objectMapper;
    
    // 常见 POJO 类型映射（用于快速识别）
    private static final Map<String, String> POJO_TYPE_MAPPING = new HashMap<>();
    
    static {
        // 注册常见的 POJO 类型
        POJO_TYPE_MAPPING.put("com.pajk.mcpmetainfo.core.demo.model.User", "com.pajk.mcpmetainfo.core.demo.model.User");
        POJO_TYPE_MAPPING.put("com.pajk.mcpmetainfo.core.demo.model.Order", "com.pajk.mcpmetainfo.core.demo.model.Order");
        POJO_TYPE_MAPPING.put("com.pajk.mcpmetainfo.core.demo.model.Product", "com.pajk.mcpmetainfo.core.demo.model.Product");
        POJO_TYPE_MAPPING.put("User", "com.pajk.mcpmetainfo.core.demo.model.User");
        POJO_TYPE_MAPPING.put("Order", "com.pajk.mcpmetainfo.core.demo.model.Order");
        POJO_TYPE_MAPPING.put("Product", "com.pajk.mcpmetainfo.core.demo.model.Product");
    }
    
    public ParameterConverter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * 将值转换为目标 Java 类型
     * 
     * @param value 原始值（可能是 Map、List、基础类型等）
     * @param targetType 目标类型（Java 类型全限定名，如 "com.pajk.mcpmetainfo.core.demo.model.User"）
     * @param dubboVersion Dubbo 版本（"2.x" 或 "3.x"）
     * @return 转换后的 Java 对象
     */
    public Object convertToJavaObject(Object value, String targetType, String dubboVersion) {
        if (value == null) {
            return null;
        }
        
        if (targetType == null || targetType.isEmpty()) {
            log.warn("⚠️ Target type is null or empty, returning original value");
            return value;
        }
        
        // 规范化类型名称
        String normalizedType = normalizeTypeName(targetType);
        
        log.debug("🔄 Converting value to type: {} (original: {})", normalizedType, targetType);
        
        try {
            // 1. 基础类型
            if (isPrimitiveType(normalizedType)) {
                return convertPrimitive(value, normalizedType);
            }
            
            // 2. 集合类型
            if (isCollectionType(normalizedType)) {
                return convertCollection(value, normalizedType, dubboVersion);
            }
            
            // 3. Map 类型
            if (isMapType(normalizedType)) {
                return value; // Map 类型直接返回
            }
            
            // 4. POJO 对象类型
            if (isPOJOType(normalizedType)) {
                return convertPOJO(value, normalizedType);
            }
            
            // 5. 数组类型
            if (normalizedType.endsWith("[]")) {
                return convertArray(value, normalizedType, dubboVersion);
            }
            
            // 6. 其他类型：尝试直接转换
            log.debug("⚠️ Unknown type: {}, trying direct conversion", normalizedType);
            return convertPOJO(value, normalizedType);
            
        } catch (Exception e) {
            log.error("❌ Failed to convert value to type {}: {}", normalizedType, e.getMessage(), e);
            // 转换失败时，返回原始值（Dubbo 可能会处理）
            return value;
        }
    }
    
    /**
     * 规范化类型名称
     */
    private String normalizeTypeName(String typeName) {
        if (typeName == null) {
            return typeName;
        }
        
        // 移除泛型参数（如 List<User> -> List）
        String normalized = typeName.split("<")[0].trim();
        
        // 处理简写类型名
        if (POJO_TYPE_MAPPING.containsKey(normalized)) {
            normalized = POJO_TYPE_MAPPING.get(normalized);
        }
        
        return normalized;
    }
    
    /**
     * 判断是否是基础类型
     */
    private boolean isPrimitiveType(String typeName) {
        return typeName.equals("int") || typeName.equals("java.lang.Integer") ||
               typeName.equals("long") || typeName.equals("java.lang.Long") ||
               typeName.equals("short") || typeName.equals("java.lang.Short") ||
               typeName.equals("byte") || typeName.equals("java.lang.Byte") ||
               typeName.equals("float") || typeName.equals("java.lang.Float") ||
               typeName.equals("double") || typeName.equals("java.lang.Double") ||
               typeName.equals("boolean") || typeName.equals("java.lang.Boolean") ||
               typeName.equals("char") || typeName.equals("java.lang.Character") ||
               typeName.equals("java.lang.String");
    }
    
    /**
     * 判断是否是集合类型
     */
    private boolean isCollectionType(String typeName) {
        return typeName.startsWith("java.util.List") ||
               typeName.startsWith("java.util.Set") ||
               typeName.startsWith("java.util.Collection") ||
               typeName.startsWith("List") ||
               typeName.startsWith("Set") ||
               typeName.startsWith("Collection");
    }
    
    /**
     * 判断是否是 Map 类型
     */
    private boolean isMapType(String typeName) {
        return typeName.startsWith("java.util.Map") ||
               typeName.startsWith("Map");
    }
    
    /**
     * 判断是否是 POJO 类型
     */
    private boolean isPOJOType(String typeName) {
        // 排除基础类型和集合类型
        if (isPrimitiveType(typeName) || isCollectionType(typeName) || isMapType(typeName)) {
            return false;
        }
        
        // 检查是否是已知的 POJO 类型
        if (POJO_TYPE_MAPPING.containsValue(typeName)) {
            return true;
        }
        
        // 检查是否是 com.pajk.mcpmetainfo.core.demo.model 包下的类型
        if (typeName.startsWith("com.pajk.mcpmetainfo.core.demo.model.")) {
            return true;
        }
        
        // 其他情况：假设是 POJO（由调用方保证）
        return !typeName.contains(".") || typeName.contains("model") || typeName.contains("entity");
    }
    
    /**
     * 转换基础类型
     */
    private Object convertPrimitive(Object value, String targetType) {
        if (value == null) {
            return null;
        }
        
        try {
            if (targetType.equals("int") || targetType.equals("java.lang.Integer")) {
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                } else if (value instanceof String) {
                    return Integer.parseInt((String) value);
                }
            } else if (targetType.equals("long") || targetType.equals("java.lang.Long")) {
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                } else if (value instanceof String) {
                    return Long.parseLong((String) value);
                }
            } else if (targetType.equals("double") || targetType.equals("java.lang.Double")) {
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                } else if (value instanceof String) {
                    return Double.parseDouble((String) value);
                }
            } else if (targetType.equals("boolean") || targetType.equals("java.lang.Boolean")) {
                if (value instanceof Boolean) {
                    return value;
                } else if (value instanceof String) {
                    return Boolean.parseBoolean((String) value);
                }
            } else if (targetType.equals("java.lang.String")) {
                return value.toString();
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to convert primitive type: {} -> {}", value, targetType, e);
        }
        
        return value;
    }
    
    /**
     * 转换 POJO 对象
     * 支持嵌套对象转换（如 Order.orderItems -> List<Order.OrderItem>）
     */
    private Object convertPOJO(Object value, String targetType) {
        if (value == null) {
            return null;
        }
        
        // 如果已经是目标类型，直接返回
        try {
            Class<?> targetClass = Class.forName(targetType);
            if (targetClass.isInstance(value)) {
                return value;
            }
        } catch (ClassNotFoundException e) {
            log.warn("⚠️ Target class not found: {}", targetType);
            return value;
        }
        
        // Map -> POJO
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            
            try {
                Class<?> targetClass = Class.forName(targetType);
                
                // 处理嵌套对象（如 Order.orderItems）
                Map<String, Object> processedMap = processNestedObjectsForPOJO(map, targetType);
                
                // 使用 Jackson 转换
                Object pojo = objectMapper.convertValue(processedMap, targetClass);
                log.debug("✅ Successfully converted Map to POJO: {} -> {}", map.getClass().getSimpleName(), targetType);
                return pojo;
                
            } catch (ClassNotFoundException e) {
                log.warn("⚠️ Target class not found: {}, returning Map", targetType);
                return map;
            } catch (Exception e) {
                log.warn("⚠️ Failed to convert Map to POJO: {} -> {}, error: {}", 
                        map.getClass().getSimpleName(), targetType, e.getMessage());
                // 转换失败时返回 Map（Dubbo 可能会处理）
                return map;
            }
        }
        
        // 其他类型：尝试使用 Jackson 转换
        try {
            Class<?> targetClass = Class.forName(targetType);
            return objectMapper.convertValue(value, targetClass);
        } catch (Exception e) {
            log.warn("⚠️ Failed to convert value to POJO: {} -> {}", value.getClass().getName(), targetType);
            return value;
        }
    }
    
    /**
     * 处理 POJO 的嵌套对象
     * 例如: Order.orderItems -> List<Order.OrderItem>
     */
    private Map<String, Object> processNestedObjectsForPOJO(Map<String, Object> map, String targetType) {
        Map<String, Object> processed = new LinkedHashMap<>(map);
        
        // 处理 Order.orderItems
        if (targetType.equals("com.pajk.mcpmetainfo.core.demo.model.Order") && map.containsKey("orderItems")) {
            Object orderItemsValue = map.get("orderItems");
            if (orderItemsValue instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> items = (List<Object>) orderItemsValue;
                List<Map<String, Object>> processedItems = new ArrayList<>();
                
                for (Object item : items) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> itemMap = (Map<String, Object>) item;
                        // OrderItem 已经是 Map，直接添加
                        processedItems.add(itemMap);
                    } else {
                        // 如果不是 Map，尝试转换
                        processedItems.add(objectMapper.convertValue(item, Map.class));
                    }
                }
                
                processed.put("orderItems", processedItems);
            }
        }
        
        // 可以在这里添加其他嵌套对象的处理逻辑
        
        return processed;
    }
    
    /**
     * 转换集合类型
     */
    private Object convertCollection(Object value, String targetType, String dubboVersion) {
        if (value == null) {
            return null;
        }
        
        if (!(value instanceof Collection)) {
            log.warn("⚠️ Value is not a Collection: {}", value.getClass().getName());
            return value;
        }
        
        Collection<?> collection = (Collection<?>) value;
        
        // 提取元素类型（如 List<User> -> User）
        String elementType = extractElementType(targetType);
        
        if (elementType == null || elementType.isEmpty()) {
            log.warn("⚠️ Cannot extract element type from: {}", targetType);
            return value;
        }
        
        log.debug("🔄 Converting Collection with element type: {}", elementType);
        
        // 转换每个元素
        List<Object> convertedList = collection.stream()
                .map(item -> convertToJavaObject(item, elementType, dubboVersion))
                .collect(Collectors.toList());
        
        // 根据目标类型返回对应的集合类型
        if (targetType.startsWith("java.util.Set") || targetType.startsWith("Set")) {
            return new LinkedHashSet<>(convertedList);
        } else {
            return convertedList;
        }
    }
    
    /**
     * 转换数组类型
     */
    private Object convertArray(Object value, String targetType, String dubboVersion) {
        if (value == null) {
            return null;
        }
        
        if (!(value instanceof Collection) && !(value.getClass().isArray())) {
            log.warn("⚠️ Value is not a Collection or Array: {}", value.getClass().getName());
            return value;
        }
        
        // 提取元素类型（如 User[] -> User）
        String elementType = targetType.substring(0, targetType.length() - 2);
        
        List<Object> list;
        if (value instanceof Collection) {
            list = new ArrayList<>((Collection<?>) value);
        } else {
            list = Arrays.asList((Object[]) value);
        }
        
        // 转换每个元素
        List<Object> convertedList = list.stream()
                .map(item -> convertToJavaObject(item, elementType, dubboVersion))
                .collect(Collectors.toList());
        
        // 转换为数组
        try {
            Class<?> elementClass = Class.forName(elementType);
            Object[] array = (Object[]) java.lang.reflect.Array.newInstance(elementClass, convertedList.size());
            return convertedList.toArray(array);
        } catch (Exception e) {
            log.warn("⚠️ Failed to convert to array: {}", targetType, e);
            return convertedList.toArray();
        }
    }
    
    /**
     * 从泛型类型中提取元素类型
     * 例如: List<com.pajk.mcpmetainfo.core.demo.model.User> -> com.pajk.mcpmetainfo.core.demo.model.User
     */
    private String extractElementType(String genericType) {
        if (genericType == null || genericType.isEmpty()) {
            return null;
        }
        
        // 查找泛型参数
        int startIndex = genericType.indexOf('<');
        int endIndex = genericType.lastIndexOf('>');
        
        if (startIndex >= 0 && endIndex > startIndex) {
            String elementType = genericType.substring(startIndex + 1, endIndex).trim();
            
            // 移除可能的嵌套泛型（如 List<List<User>> -> List<User>）
            // 这里简化处理，只取第一个泛型参数
            if (elementType.contains(",")) {
                elementType = elementType.split(",")[0].trim();
            }
            
            return elementType;
        }
        
        // 如果没有泛型参数，返回 Object
        return "java.lang.Object";
    }
    
    /**
     * 批量转换参数数组
     * 
     * @param values 参数值数组
     * @param parameterTypes 参数类型数组
     * @param dubboVersion Dubbo 版本
     * @return 转换后的参数数组
     */
    public Object[] convertParameters(Object[] values, String[] parameterTypes, String dubboVersion) {
        if (values == null || parameterTypes == null) {
            return values;
        }
        
        if (values.length != parameterTypes.length) {
            log.warn("⚠️ Parameter count mismatch: values={}, types={}", values.length, parameterTypes.length);
            return values;
        }
        
        Object[] converted = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            converted[i] = convertToJavaObject(values[i], parameterTypes[i], dubboVersion);
        }
        
        return converted;
    }
}

