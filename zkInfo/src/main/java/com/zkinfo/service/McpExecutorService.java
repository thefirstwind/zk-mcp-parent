package com.zkinfo.service;

import com.zkinfo.model.ProviderInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.service.GenericService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
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
                
                // 首先尝试使用null让Dubbo自动推断参数类型
                log.debug("尝试使用null参数类型让Dubbo自动推断");
                Object result;
                try {
                    result = genericService.$invoke(methodName, null, args);
                } catch (Exception e) {
                    log.debug("Dubbo自动推断失败，尝试手动推断参数类型: {}", e.getMessage());
                    
                    // 如果自动推断失败，尝试手动推断参数类型
                    String[] parameterTypes = inferParameterTypes(args);
                    log.debug("手动推断的参数类型: {}", parameterTypes != null ? String.join(", ", parameterTypes) : "null");
                    
                    result = genericService.$invoke(methodName, parameterTypes, args);
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
     * 推断参数类型
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
                } else {
                    // 其他类型使用完整类名
                    types[i] = clazz.getName();
                }
            }
        }
        
        return types;
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
