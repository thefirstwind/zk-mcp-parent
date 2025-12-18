package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import com.pajk.mcpmetainfo.core.util.MethodSignatureResolver;
import com.pajk.mcpmetainfo.core.util.ParameterConverter;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.service.GenericService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP 调用执行器服务
 * 
 * 负责执行通过 MCP 格式定义的 Dubbo 服务调用。该服务作为 MCP 协议和
 * Dubbo RPC 调用之间的桥梁，将标准化的 MCP 工具调用转换为实际的
 * Dubbo 服务调用。
 * 
 * <p>核心功能：</p>
 * <ul>
 *   <li>MCP 工具调用执行：接收 MCP 格式的调用请求并执行</li>
 *   <li>Dubbo 泛化调用：使用 Dubbo 泛化接口调用服务</li>
 *   <li>连接池管理：维护 Dubbo 服务引用的连接池</li>
 *   <li>异步调用支持：支持同步和异步调用模式</li>
 *   <li>异常处理：完善的异常处理和重试机制</li>
 * </ul>
 * 
 * <p>调用流程：</p>
 * <ol>
 *   <li>接收 MCP 工具调用请求</li>
 *   <li>解析服务接口和方法信息</li>
 *   <li>获取或创建 Dubbo 服务引用</li>
 *   <li>执行泛化调用</li>
 *   <li>返回调用结果或异常信息</li>
 * </ol>
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
public class McpExecutorService {
    
    @Autowired
    private ProviderService providerService;
    
    @Autowired(required = false)
    private ParameterConverter parameterConverter;
    
    @Autowired(required = false)
    private MethodSignatureResolver methodSignatureResolver;
    
    // Dubbo 配置
    private ApplicationConfig applicationConfig;
    private RegistryConfig registryConfig;
    
    // 服务引用缓存
    private final Map<String, ReferenceConfig<GenericService>> referenceCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        // 初始化 Dubbo 配置
        applicationConfig = new ApplicationConfig();
        applicationConfig.setName("zkinfo-mcp-client");
        
        registryConfig = new RegistryConfig();
        registryConfig.setAddress("zookeeper://localhost:2181");
        
        log.info("MCP 执行器服务初始化完成");
    }
    
    @PreDestroy
    public void destroy() {
        // 清理资源
        referenceCache.values().forEach(ref -> {
            try {
                ref.destroy();
            } catch (Exception e) {
                log.warn("销毁服务引用失败", e);
            }
        });
        referenceCache.clear();
        log.info("MCP 执行器服务已销毁");
    }
    
    /**
     * 执行 MCP 工具调用
     * 
     * @param toolName 工具名称 (格式: interface.method)
     * @param args 方法参数数组
     * @param timeout 调用超时时间(毫秒)
     * @return 调用结果
     */
    public CompletableFuture<McpCallResult> executeToolCall(String toolName, Object[] args, Integer timeout) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 解析工具名称
                String[] parts = toolName.split("\\.");
                if (parts.length < 2) {
                    throw new IllegalArgumentException("无效的工具名称格式: " + toolName);
                }
                
                String methodName = parts[parts.length - 1];
                String interfaceName = toolName.substring(0, toolName.lastIndexOf("." + methodName));
                
                log.info("执行 MCP 调用: {} -> {}({})", interfaceName, methodName, args != null ? args.length : 0);
                if (args != null) {
                    for (int i = 0; i < args.length; i++) {
                        log.debug("参数[{}]: 类型={}, 值={}", i, args[i] != null ? args[i].getClass().getName() : "null", args[i]);
                    }
                }
                
                // 获取服务提供者信息
                ProviderInfo provider = getAvailableProvider(interfaceName);
                if (provider == null) {
                    throw new RuntimeException("未找到可用的服务提供者: " + interfaceName);
                }
                
                // 获取或创建服务引用
                GenericService genericService = getOrCreateServiceReference(interfaceName, provider);
                
                // 检测 Dubbo 版本
                String dubboVersion = detectDubboVersion(provider);
                log.debug("检测到 Dubbo 版本: {}", dubboVersion);
                
                // 转换参数（根据方法签名和 Dubbo 版本）
                Object[] convertedArgs = convertParameters(args, interfaceName, methodName, dubboVersion);
                
                // 获取参数类型（用于 Dubbo2）
                String[] parameterTypes = getParameterTypes(interfaceName, methodName, convertedArgs, dubboVersion);
                
                // 执行调用
                Object result;
                if ("3.x".equals(dubboVersion)) {
                    // Dubbo3: 支持 POJO 模式，parameterTypes 可以为 null
                    log.debug("使用 Dubbo3 POJO 模式调用");
                    result = genericService.$invoke(methodName, null, convertedArgs);
                } else {
                    // Dubbo2: 需要指定 parameterTypes
                    log.debug("使用 Dubbo2 模式调用，参数类型: {}", 
                            parameterTypes != null ? String.join(", ", parameterTypes) : "null");
                    if (parameterTypes != null && parameterTypes.length > 0) {
                        result = genericService.$invoke(methodName, parameterTypes, convertedArgs);
                    } else {
                        // 如果无法获取参数类型，尝试让 Dubbo 自动推断
                        log.debug("参数类型为空，尝试让 Dubbo 自动推断");
                        result = genericService.$invoke(methodName, null, convertedArgs);
                    }
                }
                
                return McpCallResult.success(result);
                
            } catch (Exception e) {
                log.error("MCP 调用执行失败: {}", toolName, e);
                return McpCallResult.failure(e.getMessage(), e);
            }
        });
    }
    
    /**
     * 同步执行 MCP 工具调用
     */
    public McpCallResult executeToolCallSync(String toolName, Object[] args, Integer timeout) {
        try {
            CompletableFuture<McpCallResult> future = executeToolCall(toolName, args, timeout);
            
            if (timeout != null && timeout > 0) {
                return future.get(timeout, TimeUnit.MILLISECONDS);
            } else {
                return future.get(3000, TimeUnit.MILLISECONDS); // 默认3秒超时
            }
            
        } catch (Exception e) {
            log.error("MCP 同步调用执行失败: {}", toolName, e);
            return McpCallResult.failure("调用超时或执行失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检测 Dubbo 版本
     */
    private String detectDubboVersion(ProviderInfo provider) {
        // 方式1: 从 parameters 获取
        if (provider.getParameters() != null) {
            String dubboVersion = provider.getParameters().get("dubbo");
            if (dubboVersion != null && dubboVersion.startsWith("3")) {
                return "3.x";
            }
        }
        
        // 方式2: 从协议判断（Triple 协议是 Dubbo3）
        if ("tri".equals(provider.getProtocol()) || "triple".equals(provider.getProtocol())) {
            return "3.x";
        }
        
        // 默认: Dubbo2
        return "2.x";
    }
    
    /**
     * 转换参数（根据方法签名和 Dubbo 版本）
     */
    private Object[] convertParameters(Object[] args, String interfaceName, String methodName, String dubboVersion) {
        if (args == null || args.length == 0) {
            return args;
        }
        
        // 如果没有 ParameterConverter，使用原有逻辑
        if (parameterConverter == null) {
            log.debug("ParameterConverter not available, using original conversion logic");
            return args;
        }
        
        // 获取方法签名
        String[] parameterTypes = getParameterTypes(interfaceName, methodName, args, dubboVersion);
        
        if (parameterTypes != null && parameterTypes.length == args.length) {
            // 使用 ParameterConverter 转换参数
            return parameterConverter.convertParameters(args, parameterTypes, dubboVersion);
        } else {
            // 如果无法获取方法签名，使用原有逻辑
            log.debug("Cannot get method signature, using original conversion logic");
            return args;
        }
    }
    
    /**
     * 获取参数类型数组
     */
    private String[] getParameterTypes(String interfaceName, String methodName, Object[] args, String dubboVersion) {
        // 1. 尝试从 MethodSignatureResolver 获取
        if (methodSignatureResolver != null) {
            MethodSignatureResolver.MethodSignature signature = 
                    methodSignatureResolver.getMethodSignature(interfaceName, methodName);
            if (signature != null && signature.getParameters() != null && 
                signature.getParameters().size() == args.length) {
                String[] types = new String[signature.getParameters().size()];
                for (int i = 0; i < signature.getParameters().size(); i++) {
                    types[i] = signature.getParameters().get(i).getType();
                }
                log.debug("✅ Got parameter types from MethodSignatureResolver: {}", String.join(", ", types));
                return types;
            }
        }
        
        // 2. 如果无法从方法签名获取，使用原有推断逻辑
        log.debug("⚠️ Cannot get parameter types from MethodSignatureResolver, using inference");
        return inferParameterTypes(args);
    }
    
    /**
     * 推断参数类型（原有逻辑，作为 fallback）
     * 
     * @param args 参数数组
     * @return 参数类型字符串数组
     */
    private String[] inferParameterTypes(Object[] args) {
        if (args == null || args.length == 0) {
            return new String[0];
        }
        
        String[] types = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                types[i] = "java.lang.Object";
            } else {
                Class<?> clazz = args[i].getClass();
                
                // 处理基本类型的包装类
                if (clazz == Integer.class) {
                    types[i] = "int";
                } else if (clazz == Long.class) {
                    // Long 可能是 int 或 long，先尝试转换为 int
                    Long value = (Long) args[i];
                    if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                        // 转换为 Integer，这样泛化调用会优先匹配 int 参数
                        args[i] = value.intValue();
                        types[i] = "int";
                    } else {
                        types[i] = "long";
                    }
                } else if (clazz == Double.class) {
                    types[i] = "double";
                } else if (clazz == Float.class) {
                    types[i] = "float";
                } else if (clazz == Boolean.class) {
                    types[i] = "boolean";
                } else if (clazz == Short.class) {
                    types[i] = "short";
                } else if (clazz == Byte.class) {
                    types[i] = "byte";
                } else if (clazz == Character.class) {
                    types[i] = "char";
                } else if (clazz == String.class) {
                    types[i] = "java.lang.String";
                } else if (clazz == LinkedHashMap.class || clazz == HashMap.class) {
                    // 处理Map类型，尝试推断为具体的业务对象类型
                    Map<?, ?> map = (Map<?, ?>) args[i];
                    String inferredType = inferObjectTypeFromMap(map);
                    if (inferredType != null) {
                        types[i] = inferredType;
                        // 尝试转换Map为具体对象
                        Object convertedObject = convertMapToObject(map, inferredType);
                        if (convertedObject != null) {
                            args[i] = convertedObject;
                        }
                    } else {
                        types[i] = "java.util.Map";
                    }
                } else {
                    // 其他类型使用完整类名
                    types[i] = clazz.getName();
                }
            }
        }
        
        return types;
    }
    
    /**
     * 从Map的键推断对象类型
     * 改进版：更准确地识别 User、Order、Product 等对象
     */
    private String inferObjectTypeFromMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        
        Set<?> keys = map.keySet();
        
        // 检查是否是User对象
        // User 的特征字段: username, email, phone, realName
        if (keys.contains("username") && keys.contains("email")) {
            return "com.pajk.mcpmetainfo.core.demo.model.User";
        }
        
        // 检查是否是Order对象
        // Order 的特征字段: userId, status, totalAmount, orderItems
        if (keys.contains("userId") && (keys.contains("status") || keys.contains("totalAmount") || keys.contains("orderItems"))) {
            return "com.pajk.mcpmetainfo.core.demo.model.Order";
        }
        
        // 检查是否是Product对象
        // Product 的特征字段: name, price, category, stock
        if (keys.contains("name") && keys.contains("price") && keys.contains("category")) {
            return "com.pajk.mcpmetainfo.core.demo.model.Product";
        }
        
        // 检查是否是OrderItem对象（嵌套在Order中）
        // OrderItem 的特征字段: productId, productName, price, quantity, subtotal
        if (keys.contains("productId") && keys.contains("quantity") && 
            (keys.contains("productName") || keys.contains("subtotal"))) {
            return "com.pajk.mcpmetainfo.core.demo.model.Order$OrderItem";
        }
        
        return null;
    }
    
    /**
     * 将Map转换为具体对象
     * 改进版：支持嵌套对象转换（如 Order.orderItems）
     */
    private Object convertMapToObject(Map<?, ?> map, String targetType) {
        try {
            // 使用Jackson进行转换
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            
            // 处理嵌套对象（如 Order.orderItems）
            Map<String, Object> processedMap = processNestedObjects(map, targetType);
            
            String json = objectMapper.writeValueAsString(processedMap);
            Class<?> targetClass = Class.forName(targetType);
            
            Object result = objectMapper.readValue(json, targetClass);
            log.debug("✅ 成功将Map转换为对象: {} -> {}", map.getClass().getSimpleName(), targetType);
            return result;
            
        } catch (ClassNotFoundException e) {
            log.warn("⚠️ Target class not found: {}, returning Map", targetType);
            return map; // 类不存在时返回 Map
        } catch (Exception e) {
            log.warn("⚠️ Map转换为对象失败: targetType={}, map={}, error={}", targetType, map, e.getMessage());
            return map; // 转换失败时返回 Map（Dubbo 可能会处理）
        }
    }
    
    /**
     * 处理嵌套对象
     * 例如: Order.orderItems -> List<Order.OrderItem>
     */
    private Map<String, Object> processNestedObjects(Map<?, ?> map, String targetType) {
        Map<String, Object> processed = new LinkedHashMap<>();
        
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey().toString();
            Object value = entry.getValue();
            
            // 处理 orderItems 字段（Order 的嵌套对象列表）
            if ("orderItems".equals(key) && value instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> items = (List<Object>) value;
                List<Map<String, Object>> processedItems = new ArrayList<>();
                
                for (Object item : items) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> itemMap = (Map<String, Object>) item;
                        // OrderItem 已经是 Map，直接添加
                        processedItems.add(itemMap);
                    }
                }
                
                processed.put(key, processedItems);
            } else {
                // 其他字段直接复制
                processed.put(key, value);
            }
        }
        
        return processed;
    }
    
    /**
     * 获取可用的服务提供者
     */
    private ProviderInfo getAvailableProvider(String interfaceName) {
        return providerService.getProvidersByInterface(interfaceName)
                .stream()
                .filter(ProviderInfo::isOnline)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 获取或创建服务引用
     */
    private GenericService getOrCreateServiceReference(String interfaceName, ProviderInfo provider) {
        String cacheKey = interfaceName + ":" + provider.getGroup() + ":" + provider.getVersion();
        
        ReferenceConfig<GenericService> reference = referenceCache.computeIfAbsent(cacheKey, key -> {
            ReferenceConfig<GenericService> ref = new ReferenceConfig<>();
            // 注意：setApplication已废弃，使用全局配置
            ref.setRegistry(registryConfig);
            ref.setInterface(interfaceName);
            ref.setGeneric("true");
            
            // 设置版本和分组
            if (provider.getVersion() != null) {
                ref.setVersion(provider.getVersion());
            }
            if (provider.getGroup() != null) {
                ref.setGroup(provider.getGroup());
            }
            
            // 设置超时时间
            ref.setTimeout(3000);
            
            log.info("创建服务引用: {} (group: {}, version: {})", 
                    interfaceName, provider.getGroup(), provider.getVersion());
            
            return ref;
        });
        
        return reference.get();
    }
    
    /**
     * MCP 调用结果
     */
    public static class McpCallResult {
        private boolean success;
        private Object result;
        private String errorMessage;
        private Throwable exception;
        private long executionTime;
        
        private McpCallResult(boolean success, Object result, String errorMessage, Throwable exception) {
            this.success = success;
            this.result = result;
            this.errorMessage = errorMessage;
            this.exception = exception;
            this.executionTime = System.currentTimeMillis();
        }
        
        public static McpCallResult success(Object result) {
            return new McpCallResult(true, result, null, null);
        }
        
        public static McpCallResult failure(String errorMessage, Throwable exception) {
            return new McpCallResult(false, null, errorMessage, exception);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public Object getResult() { return result; }
        public String getErrorMessage() { return errorMessage; }
        public Throwable getException() { return exception; }
        public long getExecutionTime() { return executionTime; }
    }
}
