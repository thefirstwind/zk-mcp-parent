package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import com.pajk.mcpmetainfo.core.util.MethodSignatureResolver;
import com.pajk.mcpmetainfo.core.util.ParameterConverter;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.service.GenericService;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.curator.framework.CuratorFramework;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
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
    private com.pajk.mcpmetainfo.core.service.DubboServiceDbService dubboServiceDbService;
    
    @Autowired(required = false)
    private ParameterConverter parameterConverter;
    
    @Autowired(required = false)
    private MethodSignatureResolver methodSignatureResolver;
    
    @Autowired(required = false)
    private com.pajk.mcpmetainfo.core.config.ZooKeeperConfig zooKeeperConfig;
    
    @Autowired(required = false)
    private com.pajk.mcpmetainfo.core.service.ZooKeeperService zooKeeperService;
    
    // Dubbo 超时配置（从配置文件读取，默认 30 秒）
    @Value("${dubbo.consumer.timeout:30000}")
    private int dubboTimeout;
    
    // Dubbo QOS 配置（从配置文件读取）
    @Value("${dubbo.application.qos-enable:false}")
    private boolean qosEnable;
    
    @Value("${dubbo.application.qos-port:-1}")
    private int qosPort;
    
    // Dubbo 配置
    private ApplicationConfig applicationConfig;
    private RegistryConfig registryConfig;
    private ProtocolConfig protocolConfig;
    
    // 服务引用缓存
    private final Map<String, ReferenceConfig<GenericService>> referenceCache = new ConcurrentHashMap<>();
    
    // Metadata 缓存：interfaceName -> metadata JSON
    private final Map<String, String> metadataCache = new ConcurrentHashMap<>();
    
    // MetadataReport 缓存（使用 Dubbo SDK）
    // Dubbo 2.5 不支持 MetadataReport，注释掉
    // private MetadataReport metadataReport;
    private final Object metadataReportLock = new Object();
    
    // ObjectMapper 用于解析 JSON
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @PostConstruct
    public void init() {
        // 初始化 Dubbo 配置
        applicationConfig = new ApplicationConfig();
        applicationConfig.setName("zkinfo-mcp-client");
        
        // 禁用 QOS 服务器（从配置文件读取）
        try {
            // 尝试使用反射设置 qos-enable 和 qos-port
            java.lang.reflect.Method setQosEnableMethod = applicationConfig.getClass().getMethod("setQosEnable", boolean.class);
            setQosEnableMethod.invoke(applicationConfig, qosEnable);
            log.info("✅ 设置 QOS enable: {}", qosEnable);
        } catch (Exception e) {
            log.debug("无法通过反射设置 qos-enable，将通过 parameters 设置: {}", e.getMessage());
        }
        
        try {
            java.lang.reflect.Method setQosPortMethod = applicationConfig.getClass().getMethod("setQosPort", int.class);
            setQosPortMethod.invoke(applicationConfig, qosPort);
            log.info("✅ 设置 QOS port: {}", qosPort);
        } catch (Exception e) {
            log.debug("无法通过反射设置 qos-port，将通过 parameters 设置: {}", e.getMessage());
        }
        
        // 设置全局序列化方式为 hessian2（确保与提供者兼容）
        Map<String, String> appParameters = new HashMap<>();
        appParameters.put("serialization", "hessian2");
        // 移除 prefer.serialization 参数，避免使用 fastjson2
        appParameters.put("prefer.serialization", "hessian2");
        // 通过 parameters 设置 qos 相关参数（作为备选方案）
        appParameters.put("qos.enable", String.valueOf(qosEnable));
        appParameters.put("qos.port", String.valueOf(qosPort));
        applicationConfig.setParameters(appParameters);
        
        registryConfig = new RegistryConfig();
        // 从 application.yml 配置中读取 ZooKeeper 地址
        if (zooKeeperConfig == null) {
            throw new IllegalStateException("ZooKeeperConfig 未注入，请检查配置类是否正确配置");
        }
        
        String connectString = zooKeeperConfig.getConnectString();
        if (connectString == null || connectString.trim().isEmpty()) {
            throw new IllegalStateException("ZooKeeper 连接地址未配置，请在 application.yml 中配置 zookeeper.connect-string");
        }
        
        // 构建 Dubbo Registry 地址（格式：zookeeper://host:port）
        String zkAddress;
        if (connectString.startsWith("zookeeper://")) {
            zkAddress = connectString;
        } else {
            zkAddress = "zookeeper://" + connectString;
        }
        
        registryConfig.setAddress(zkAddress);
        // 禁用 Consumer 注册到 ZooKeeper（只订阅，不注册，避免连接冲突）
        registryConfig.setRegister(false);
        // 增加 ZooKeeper 连接超时时间（默认 30 秒可能不够）
        registryConfig.setTimeout(60000); // 60 秒
        
        // 通过 parameters 设置 ZooKeeper 连接参数，提高连接稳定性
        Map<String, String> registryParams = new HashMap<>();
        // 会话超时时间（从配置读取，默认 60 秒）
        registryParams.put("session.timeout", String.valueOf(zooKeeperConfig.getSessionTimeout()));
        // 连接超时时间（从配置读取，默认 30 秒）
        registryParams.put("connect.timeout", String.valueOf(zooKeeperConfig.getConnectionTimeout()));
        // 禁用 Consumer 注册（双重保险）
        registryParams.put("register", "false");
        // 设置客户端类型为 curator（Dubbo 3.x 支持）
        registryParams.put("client", "curator");
        registryConfig.setParameters(registryParams);
        
        log.info("✅ 从 application.yml 读取 ZooKeeper 地址: {}, register=false, timeout=60000ms, sessionTimeout={}ms, connectionTimeout={}ms", 
                zkAddress, zooKeeperConfig.getSessionTimeout(), zooKeeperConfig.getConnectionTimeout());
        
        // 创建 ProtocolConfig，强制使用 hessian2 序列化
        protocolConfig = new ProtocolConfig();
        protocolConfig.setName("dubbo");
        protocolConfig.setSerialization("hessian2");
        
        log.info("MCP 执行器服务初始化完成 (ZooKeeper: {}, Serialization: hessian2)", zkAddress);
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
        return executeToolCall(toolName, args, timeout, null);
    }

    /**
     * 执行 MCP 工具调用（支持显式指定参数类型）
     * 
     * @param toolName 工具名称 (格式: interface.method)
     * @param args 方法参数数组
     * @param timeout 调用超时时间(毫秒)
     * @param explicitParameterTypes 显式指定的参数类型（可选，如果不为空则跳过推断）
     * @return 调用结果
     */
    public CompletableFuture<McpCallResult> executeToolCall(String toolName, Object[] args, Integer timeout, String[] explicitParameterTypes) {
        // 从 toolName 解析接口名和方法名（用于生成友好的错误信息）
        String interfaceName = null;
        String methodName = null;
        try {
            String[] parts = toolName.split("\\.");
            if (parts.length >= 2) {
                methodName = parts[parts.length - 1];
                interfaceName = toolName.substring(0, toolName.lastIndexOf("." + methodName));
            }
        } catch (Exception e) {
            log.debug("无法从 toolName 解析接口名和方法名: {}", toolName);
        }
        
        final String finalInterfaceName = interfaceName;
        final String finalMethodName = methodName;
        
        return CompletableFuture.supplyAsync(() -> {
            // 在 lambda 内部使用 final 变量，如果需要重新解析则使用局部变量
            String localInterfaceName = finalInterfaceName;
            String localMethodName = finalMethodName;
            
            try {
                // 使用已解析的接口名和方法名
                if (localInterfaceName == null || localMethodName == null) {
                    // 如果解析失败，重新解析
                    String[] parts = toolName.split("\\.");
                    if (parts.length < 2) {
                        throw new IllegalArgumentException("无效的工具名称格式: " + toolName);
                    }
                    localMethodName = parts[parts.length - 1];
                    localInterfaceName = toolName.substring(0, toolName.lastIndexOf("." + localMethodName));
                }
                
                log.info("执行 MCP 调用: {} -> {}({})", localInterfaceName, localMethodName, args != null ? args.length : 0);
                if (args != null) {
                    for (int i = 0; i < args.length; i++) {
                        log.debug("参数[{}]: 类型={}, 值={}", i, args[i] != null ? args[i].getClass().getName() : "null", args[i]);
                    }
                }
                
                // 获取服务提供者信息
                ProviderInfo provider = getAvailableProvider(localInterfaceName);
                if (provider == null) {
                    throw new RuntimeException("未找到可用的服务提供者: " + localInterfaceName);
                }
                
                // 获取或创建服务引用
                GenericService genericService = getOrCreateServiceReference(localInterfaceName, provider);
                
                // 检测 Dubbo 版本
                String dubboVersion = detectDubboVersion(provider);
                log.debug("检测到 Dubbo 版本: {}", dubboVersion);
                
                // 记录原始参数
                log.info("📥 原始参数: args.length={}", args != null ? args.length : 0);
                if (args != null && args.length > 0) {
                    for (int i = 0; i < args.length; i++) {
                        log.info("   原始参数[{}]: type={}, value={}", i, 
                                args[i] != null ? args[i].getClass().getName() : "null", args[i]);
                    }
                }
                
                // 先获取参数类型（用于后续的参数转换和调用）
                String[] parameterTypes;
                if (explicitParameterTypes != null && explicitParameterTypes.length > 0) {
                    parameterTypes = explicitParameterTypes;
                    log.info("✅ 使用显式指定的参数类型: {}", String.join(", ", parameterTypes));
                } else {
                    parameterTypes = getParameterTypes(localInterfaceName, localMethodName, args, dubboVersion);
                }
                
                // 确保参数类型和参数值都存在且匹配
                if (parameterTypes != null && parameterTypes.length > 0) {
                    if (explicitParameterTypes == null) {
                        log.info("✅ 获取到参数类型: {}", String.join(", ", parameterTypes));
                    }
                    
                    // 如果参数值为空但参数类型不为空，说明参数在提取阶段丢失了
                    if (args == null || args.length == 0) {
                        log.error("❌ 参数类型已获取但参数值为空！这不应该发生。请检查 extractMethodParameters 方法。");
                        throw new IllegalStateException("参数类型已获取但参数值为空，请检查参数提取逻辑");
                    }
                    
                    // 确保参数类型和参数值数量匹配
                    if (parameterTypes.length != args.length) {
                        log.warn("⚠️ 参数类型数量 ({}) 与参数值数量 ({}) 不匹配", 
                                parameterTypes.length, args.length);
                        // 如果类型数量大于参数数量，截断类型数组
                        if (parameterTypes.length > args.length) {
                            parameterTypes = Arrays.copyOf(parameterTypes, args.length);
                            log.warn("已截断参数类型数组到 {}", args.length);
                        }
                    }
                } else {
                    log.warn("⚠️ 无法获取参数类型，将使用 Dubbo 自动推断");
                }
                
                // 转换参数（根据方法签名和 Dubbo 版本）
                // 对于 Dubbo 2.7 泛化调用：
                // - parameterTypes: 完整的类名，如 ["com.zkinfo.demo.model.User"]
                // - convertedArgs: 可以是 Map 对象，Dubbo 会自动转换为对应的 POJO
                Object[] convertedArgs = convertParameters(args, localInterfaceName, localMethodName, dubboVersion, parameterTypes);
                
                // 验证转换后的参数
                if (convertedArgs == null || convertedArgs.length == 0) {
                    if (parameterTypes != null && parameterTypes.length > 0) {
                        log.error("❌ 参数转换后为空，但参数类型不为空！这不应该发生。");
                        throw new IllegalStateException("参数转换后为空，但参数类型不为空");
                    }
                } else {
                    log.info("✅ 参数转换完成: convertedArgs.length={}", convertedArgs.length);
                    for (int i = 0; i < convertedArgs.length; i++) {
                        String type = parameterTypes != null && i < parameterTypes.length ? parameterTypes[i] : "unknown";
                        log.info("   转换后参数[{}]: type={}, valueType={}, value={}", i, type,
                                convertedArgs[i] != null ? convertedArgs[i].getClass().getName() : "null",
                                convertedArgs[i]);
                    }
                }
                
                // 执行调用
                Object result;
                
                // 验证参数和类型数组长度匹配
                if (parameterTypes != null && convertedArgs != null && 
                    parameterTypes.length != convertedArgs.length) {
                    log.warn("⚠️ 参数类型数组长度 ({}) 与参数数组长度 ({}) 不匹配，尝试修复", 
                            parameterTypes.length, convertedArgs.length);
                    // 如果类型数组长度大于参数数组，截断类型数组
                    if (parameterTypes.length > convertedArgs.length) {
                        parameterTypes = Arrays.copyOf(parameterTypes, convertedArgs.length);
                        log.warn("已截断参数类型数组到 {}", convertedArgs.length);
                    } else {
                        // 如果参数数组长度大于类型数组，使用 null 作为类型数组（让 Dubbo 自动推断）
                        log.warn("参数数组长度大于类型数组，使用 null 类型数组让 Dubbo 自动推断");
                        parameterTypes = null;
                    }
                }
                
                // ========== 泛化调用前输出调用参数 ==========
                log.info("═══════════════════════════════════════════════════════════");
                log.info("🚀 准备执行泛化调用");
                log.info("   接口: {}", localInterfaceName);
                log.info("   方法: {}", localMethodName);
                log.info("   Dubbo版本: {}", dubboVersion);
                log.info("   参数数量: {}", convertedArgs != null ? convertedArgs.length : 0);
                
                if (parameterTypes != null && parameterTypes.length > 0) {
                    log.info("   参数类型: {}", String.join(", ", parameterTypes));
                } else {
                    log.info("   参数类型: null (Dubbo自动推断)");
                }
                
                // 详细输出每个参数
                if (convertedArgs != null && convertedArgs.length > 0) {
                    log.info("   ───────────────────────────────────────────────────────");
                    log.info("   参数详情:");
                    for (int i = 0; i < convertedArgs.length; i++) {
                        Object arg = convertedArgs[i];
                        String type = parameterTypes != null && i < parameterTypes.length ? parameterTypes[i] : "unknown";
                        
                        if (arg == null) {
                            log.info("     参数[{}]: 类型={}, 值=null", i, type);
                        } else if (arg instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> mapArg = (Map<String, Object>) arg;
                            log.info("     参数[{}]: 类型={}, 值=Map ({} 个字段)", i, type, mapArg.size());
                            log.info("         ┌─ Map 内容:");
                            for (Map.Entry<String, Object> entry : mapArg.entrySet()) {
                                String key = entry.getKey();
                                Object value = entry.getValue();
                                String valueStr;
                                
                                if (value == null) {
                                    valueStr = "null";
                                } else if (value instanceof Map) {
                                    valueStr = "Map(" + ((Map<?, ?>) value).size() + " keys)";
                                } else if (value instanceof List) {
                                    valueStr = "List(" + ((List<?>) value).size() + " items)";
                                } else if (value instanceof String) {
                                    // 字符串可能很长，截断显示
                                    String str = (String) value;
                                    valueStr = str.length() > 100 ? "\"" + str.substring(0, 100) + "...\"" : "\"" + str + "\"";
                                } else {
                                    valueStr = value.toString();
                                    // 如果值太长，截断
                                    if (valueStr.length() > 100) {
                                        valueStr = valueStr.substring(0, 100) + "...";
                                    }
                                }
                                
                                log.info("         │  {} = {}", key, valueStr);
                            }
                            log.info("         └─");
                        } else if (arg instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<Object> listArg = (List<Object>) arg;
                            log.info("     参数[{}]: 类型={}, 值=List ({} 个元素)", i, type, listArg.size());
                            for (int j = 0; j < Math.min(listArg.size(), 5); j++) {
                                Object item = listArg.get(j);
                                if (item instanceof Map) {
                                    log.info("         [{}]: Map({} keys)", j, ((Map<?, ?>) item).size());
                                } else {
                                    log.info("         [{}]: {}", j, item);
                                }
                            }
                            if (listArg.size() > 5) {
                                log.info("         ... (还有 {} 个元素)", listArg.size() - 5);
                            }
                        } else {
                            String valueStr = arg.toString();
                            // 如果值太长，截断
                            if (valueStr.length() > 200) {
                                valueStr = valueStr.substring(0, 200) + "...";
                            }
                            log.info("     参数[{}]: 类型={}, 值={}", i, type, valueStr);
                        }
                    }
                    log.info("   ───────────────────────────────────────────────────────");
                } else {
                    log.info("   参数: 无参数");
                }
                log.info("═══════════════════════════════════════════════════════════");
                
                // 根据 Dubbo 2.7 官方文档：
                // $invoke(String method, String[] parameterTypes, Object[] args)
                // - parameterTypes: 参数类型数组，如 ["com.zkinfo.demo.model.User", "java.lang.Long"]
                // - args: 参数值数组，对于 POJO 类型可以是 Map 对象，Dubbo 会自动转换为对应的 POJO
                
                if ("3.x".equals(dubboVersion)) {
                    // Dubbo3: 支持 POJO 模式，parameterTypes 可以为 null
                    // 但如果有明确的参数类型，应该优先使用，以避免自动推断错误（特别是 int/long 混淆）
                    log.info("📞 执行 Dubbo3 泛化调用: {}.{}({} 个参数)", 
                            localInterfaceName, localMethodName, convertedArgs != null ? convertedArgs.length : 0);
                    
                    // 如果 parameterTypes 为空，传 null 让 Dubbo 推断
                    // 如果有 explicitParameterTypes，肯定已经赋给了 parameterTypes，所以优先使用
                    String[] invokeTypes = (parameterTypes != null && parameterTypes.length > 0) ? parameterTypes : null;
                    if (invokeTypes != null) {
                        log.info("   使用指定的参数类型: {}", String.join(", ", invokeTypes));
                    } else {
                        log.info("   使用 Dubbo3 自动推断");
                    }
                    
                    result = genericService.$invoke(localMethodName, invokeTypes, convertedArgs);
                } else {
                    // Dubbo2: 必须指定 parameterTypes
                    // 关键：parameterTypes 必须是完整的类名，args 可以是 Map 对象（对于 POJO 类型）
                    if (parameterTypes != null && parameterTypes.length > 0 && 
                        convertedArgs != null && convertedArgs.length > 0) {
                        // 确保类型数组和参数数组长度一致
                        if (parameterTypes.length == convertedArgs.length) {
                            log.info("📞 执行 Dubbo2 泛化调用: {}.{}({})", 
                                    localInterfaceName, localMethodName, String.join(", ", parameterTypes));
                            log.info("   参数值: {} 个参数", convertedArgs.length);
                            for (int i = 0; i < convertedArgs.length; i++) {
                                log.info("      args[{}]: type={}, valueType={}", i, 
                                        parameterTypes[i],
                                        convertedArgs[i] != null ? convertedArgs[i].getClass().getName() : "null");
                            }
                            // 调用 Dubbo 泛化接口：参数类型和参数值必须一一对应
                            result = genericService.$invoke(localMethodName, parameterTypes, convertedArgs);
                        } else {
                            log.error("❌ 参数类型数组长度 ({}) 与参数数组长度 ({}) 不匹配，无法调用", 
                                    parameterTypes.length, convertedArgs.length);
                            throw new IllegalArgumentException(
                                    String.format("参数类型数组长度 (%d) 与参数数组长度 (%d) 不匹配", 
                                            parameterTypes.length, convertedArgs.length));
                        }
                    } else if (convertedArgs != null && convertedArgs.length > 0) {
                        // 如果无法获取参数类型，但参数值存在，尝试让 Dubbo 自动推断
                        log.warn("⚠️ 无法获取参数类型，但参数值存在，尝试让 Dubbo 自动推断");
                        log.info("📞 执行 Dubbo2 泛化调用: {}.{}() (类型自动推断)", 
                                localInterfaceName, localMethodName);
                        result = genericService.$invoke(localMethodName, null, convertedArgs);
                    } else {
                        // 无参数方法
                        log.info("📞 执行 Dubbo2 泛化调用: {}.{}() (无参数)", 
                                localInterfaceName, localMethodName);
                        result = genericService.$invoke(localMethodName, new String[0], new Object[0]);
                    }
                }
                
                log.info("✅ 泛化调用执行完成: {}.{}", localInterfaceName, localMethodName);
                
                return McpCallResult.success(result);
                
            } catch (ExceptionInInitializerError e) {
                log.error("❌ MCP 调用执行失败 (ExceptionInInitializerError): {}", toolName, e);
                Throwable cause = e.getCause();
                String errorMessage = "Dubbo 框架初始化失败: " + 
                        (cause != null ? cause.getMessage() : e.getMessage());
                return McpCallResult.failure(errorMessage, e);
            } catch (IllegalArgumentException e) {
                // 检查是否是参数类型不匹配错误
                String friendlyMessage = parseArgumentTypeMismatchError(e, finalInterfaceName, finalMethodName);
                if (friendlyMessage != null) {
                    log.error("❌ MCP 调用执行失败 (参数类型不匹配): {} - {}", toolName, friendlyMessage);
                    return McpCallResult.failure(friendlyMessage, e);
                }
                // 其他 IllegalArgumentException，使用原始错误信息
                log.error("❌ MCP 调用执行失败 (IllegalArgumentException): {}", toolName, e);
                return McpCallResult.failure("参数错误: " + e.getMessage(), e);
            } catch (Exception e) {
                // 检查异常链中是否有 IllegalArgumentException（参数类型不匹配）
                String friendlyMessage = findArgumentTypeMismatchInCauseChain(e, finalInterfaceName, finalMethodName);
                if (friendlyMessage != null) {
                    log.error("❌ MCP 调用执行失败 (参数类型不匹配): {} - {}", toolName, friendlyMessage);
                    return McpCallResult.failure(friendlyMessage, e);
                }
                log.error("MCP 调用执行失败: {}", toolName, e);
                return McpCallResult.failure(e.getMessage(), e);
            }
        });
    }
    
    /**
     * 同步执行 MCP 工具调用
     */
    /**
     * 同步执行 MCP 工具调用
     */
    public McpCallResult executeToolCallSync(String toolName, Object[] args, Integer timeout) {
        return executeToolCallSync(toolName, args, timeout, null);
    }

    /**
     * 同步执行 MCP 工具调用（支持显式指定参数类型）
     */
    public McpCallResult executeToolCallSync(String toolName, Object[] args, Integer timeout, String[] explicitParameterTypes) {
        // 从 toolName 解析接口名和方法名（用于生成友好的错误信息）
        String interfaceName = null;
        String methodName = null;
        try {
            String[] parts = toolName.split("\\.");
            if (parts.length >= 2) {
                methodName = parts[parts.length - 1];
                interfaceName = toolName.substring(0, toolName.lastIndexOf("." + methodName));
            }
        } catch (Exception e) {
            log.debug("无法从 toolName 解析接口名和方法名: {}", toolName);
        }
        
        try {
            CompletableFuture<McpCallResult> future = executeToolCall(toolName, args, timeout, explicitParameterTypes);
            
            // 使用传入的超时时间，如果没有则使用配置的 Dubbo 超时时间（默认 30 秒）
            int syncTimeout = (timeout != null && timeout > 0) ? timeout : dubboTimeout;
            // 同步等待的超时时间应该比 Dubbo 调用超时时间稍长，避免提前超时
            syncTimeout = syncTimeout + 5000; // 增加 5 秒缓冲时间
            return future.get(syncTimeout, TimeUnit.MILLISECONDS);
            
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("MCP 同步调用超时: {}", toolName, e);
            return McpCallResult.failure("调用超时: " + e.getMessage(), e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ExceptionInInitializerError) {
                log.error("❌ MCP 同步调用失败 (ExceptionInInitializerError): {}", toolName, cause);
                return McpCallResult.failure("Dubbo 框架初始化失败: " + 
                        (cause.getCause() != null ? cause.getCause().getMessage() : cause.getMessage()), cause);
            }
            // 检查是否是参数类型不匹配错误
            String friendlyMessage = findArgumentTypeMismatchInCauseChain(e, interfaceName, methodName);
            if (friendlyMessage != null) {
                log.error("❌ MCP 同步调用失败 (参数类型不匹配): {} - {}", toolName, friendlyMessage);
                return McpCallResult.failure(friendlyMessage, e);
            }
            log.error("MCP 同步调用执行失败: {}", toolName, e);
            return McpCallResult.failure("调用执行失败: " + 
                    (cause != null ? cause.getMessage() : e.getMessage()), e);
        } catch (ExceptionInInitializerError e) {
            log.error("❌ MCP 同步调用失败 (ExceptionInInitializerError): {}", toolName, e);
            Throwable cause = e.getCause();
            return McpCallResult.failure("Dubbo 框架初始化失败: " + 
                    (cause != null ? cause.getMessage() : e.getMessage()), e);
        } catch (IllegalArgumentException e) {
            // 检查是否是参数类型不匹配错误
            String friendlyMessage = parseArgumentTypeMismatchError(e, interfaceName, methodName);
            if (friendlyMessage != null) {
                log.error("❌ MCP 同步调用失败 (参数类型不匹配): {} - {}", toolName, friendlyMessage);
                return McpCallResult.failure(friendlyMessage, e);
            }
            log.error("❌ MCP 同步调用失败 (IllegalArgumentException): {}", toolName, e);
            return McpCallResult.failure("参数错误: " + e.getMessage(), e);
        } catch (Exception e) {
            // 检查异常链中是否有参数类型不匹配错误
            String friendlyMessage = findArgumentTypeMismatchInCauseChain(e, interfaceName, methodName);
            if (friendlyMessage != null) {
                log.error("❌ MCP 同步调用失败 (参数类型不匹配): {} - {}", toolName, friendlyMessage);
                return McpCallResult.failure(friendlyMessage, e);
            }
            log.error("MCP 同步调用执行失败: {}", toolName, e);
            return McpCallResult.failure("调用超时或执行失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检测 Dubbo 版本
     * 
     * @return 版本字符串，格式："2.6.x", "2.7.x", "3.x" 等，如果无法确定具体版本则返回 "2.x"
     */
    private String detectDubboVersion(ProviderInfo provider) {
        // 方式1: 从 parameters 获取具体版本号
        if (provider.getParameters() != null) {
            // 优先检查 release 参数（Dubbo 2.7+ 会带上 release 版本号）
            String release = provider.getParameters().get("release");
            if (release != null && !release.isEmpty()) {
                if (release.startsWith("3")) return "3.x";
                if (release.startsWith("2.7")) return "2.7.x";
                if (release.startsWith("2.6")) return "2.6.x";
            }
            
            String dubboVersion = provider.getParameters().get("dubbo");
            if (dubboVersion != null && !dubboVersion.isEmpty()) {
                // 如果版本号以 3 开头，返回 "3.x"
                if (dubboVersion.startsWith("3")) {
                    return "3.x";
                }
                // 如果版本号以 2.7 开头，返回 "2.7.x"
                if (dubboVersion.startsWith("2.7")) {
                    return "2.7.x";
                }
                // 如果版本号以 2.6 开头，返回 "2.6.x"
                if (dubboVersion.startsWith("2.6")) {
                    return "2.6.x";
                }
                // 注意：Dubbo 2.0.2 可能是协议版本号而不是框架版本号
                // 如果是 2.0.2 且同时存在 release 参数，已经在上面处理了
                // 如果只有 2.0.2，且 provider 带有 group，则极有可能是 2.6+
                if ("2.0.2".equals(dubboVersion) && provider.getGroup() != null && !provider.getGroup().isEmpty()) {
                    return "2.6.x"; // 至少是 2.6
                }
                // 如果版本号以 2.5 开头，返回 "2.5.x"
                if (dubboVersion.startsWith("2.5")) {
                    return "2.5.x";
                }
                // 如果版本号以 2 开头，返回 "2.x"
                if (dubboVersion.startsWith("2")) {
                    return "2.x";
                }
                // 其他情况，返回原版本号
                return dubboVersion;
            }
        }
        
        // 方式2: 从协议判断（Triple 协议是 Dubbo3）
        if ("tri".equals(provider.getProtocol()) || "triple".equals(provider.getProtocol())) {
            return "3.x";
        }
        
        // 默认: Dubbo2（无法确定具体版本，假设是 2.x）
        return "2.x";
    }
    
    /**
     * 判断 Dubbo 版本是否支持 group（2.6+ 才支持）
     * 
     * @param dubboVersion 版本字符串
     * @return true 如果版本 >= 2.6，false 否则（2.5 及更早版本不支持 group）
     */
    private boolean isGroupSupported(String dubboVersion, ProviderInfo provider) {
        // 如果 Provider 信息中已经明确包含了 Group（且不是默认值），则肯定支持 Group
        if (provider != null && provider.getGroup() != null && !provider.getGroup().isEmpty() && !"default".equals(provider.getGroup())) {
            log.debug("✅ Provider 显式包含 Group {}, 判定为支持 Group", provider.getGroup());
            return true;
        }
        
        if (dubboVersion == null || dubboVersion.isEmpty()) {
            return false; // 未知版本，保守策略：不支持 group
        }
        // 3.x 支持 group
        if (dubboVersion.startsWith("3")) {
            return true;
        }
        // 2.7.x 支持 group
        if (dubboVersion.startsWith("2.7")) {
            return true;
        }
        // 2.6.x 支持 group
        if (dubboVersion.startsWith("2.6")) {
            return true;
        }
        // 2.5.x 及更早版本不支持 group
        if (dubboVersion.startsWith("2.5") || dubboVersion.startsWith("2.4") || 
            dubboVersion.startsWith("2.3") || dubboVersion.startsWith("2.2") ||
            dubboVersion.startsWith("2.1") || dubboVersion.startsWith("2.0")) {
            return false;
        }
        // 对于 "2.x" 这种不确定的版本，保守策略：假设是 2.5，不支持 group
        // 这样可以确保 demo-provider2（Dubbo 2.5）使用直接 URL 方式
        if ("2.x".equals(dubboVersion)) {
            return false;
        }
        // 其他 2.x 版本，默认假设支持（保守策略）
        if (dubboVersion.startsWith("2")) {
            return true;
        }
        // 未知版本，默认不支持（保守策略）
        return false;
    }
    
    /**
     * 判断 Dubbo 版本是否支持 metadata（2.7+ 才支持）
     * 
     * @param dubboVersion 版本字符串
     * @return true 如果版本 >= 2.7，false 否则
     */
    private boolean isMetadataSupported(String dubboVersion) {
        if (dubboVersion == null || dubboVersion.isEmpty()) {
            return false;
        }
        // 3.x 支持 metadata
        if (dubboVersion.startsWith("3")) {
            return true;
        }
        // 2.7.x 支持 metadata
        if (dubboVersion.startsWith("2.7")) {
            return true;
        }
        // 2.6.x 及以下不支持 metadata
        if (dubboVersion.startsWith("2.6") || dubboVersion.startsWith("2.5") || 
            dubboVersion.startsWith("2.4") || dubboVersion.startsWith("2.3") ||
            dubboVersion.startsWith("2.2") || dubboVersion.startsWith("2.1") ||
            dubboVersion.startsWith("2.0")) {
            return false;
        }
        // 其他 2.x 版本，默认假设不支持（保守策略）
        if (dubboVersion.startsWith("2")) {
            return false;
        }
        // 未知版本，默认不支持（保守策略）
        return false;
    }
    
    /**
     * 转换参数（根据方法签名和 Dubbo 版本）
     * 
     * 泛化调用支持两种方式：
     * 1. Map方式：参数类型是POJO类型，参数值是Map（不转换为POJO，直接使用Map）
     * 2. JSON方式：参数类型是POJO类型，参数值是Map（从JSON解析而来，直接使用Map）
     * 
     * 关键：对于泛化调用，如果参数是Map且目标类型是POJO，应该直接使用Map，不要转换为POJO对象。
     * 如果 Map 中的值类型与 POJO 字段类型不匹配，Dubbo 会报错，但错误信息是明确的。
     */
    private Object[] convertParameters(Object[] args, String interfaceName, String methodName, String dubboVersion, String[] parameterTypes) {
        if (args == null || args.length == 0) {
            return args;
        }
        
        // 对于泛化调用，如果参数是Map且目标类型是POJO，直接使用Map，不转换
        // 这是Dubbo泛化调用的标准做法：参数类型指定POJO类型，参数值使用Map
        if (parameterTypes != null && parameterTypes.length == args.length) {
            Object[] convertedArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                String paramType = parameterTypes[i];
                
                // 如果参数是Map且目标类型是POJO，直接使用Map（泛化调用的标准方式）
                if (arg instanceof Map && paramType != null && !paramType.equals("java.util.Map")) {
                    // 检查是否是POJO类型（不是基础类型、集合类型等）
                    if (isPOJOType(paramType)) {
                        log.debug("✅ 泛化调用：参数[{}]是Map，目标类型是POJO {}，直接使用Map（不转换）", i, paramType);
                        convertedArgs[i] = arg; // 直接使用Map，不转换为POJO
                        continue;
                    }
                }
                
                // 处理 JSON String 格式的参数 (例如 MCP Inspector 可能传过来 String 类型的 JSON)
                if (arg instanceof String && paramType != null && !paramType.equals("java.lang.String")) {
                    String strArg = ((String) arg).trim();
                    // 检查是否看起来像 JSON 对象或数组
                    if ((strArg.startsWith("{") && strArg.endsWith("}")) || 
                        (strArg.startsWith("[") && strArg.endsWith("]"))) {
                        try {
                            log.debug("🔄 检测到 JSON String 参数，尝试解析: str={}, targetType={}", strArg, paramType);
                            // 将 JSON String 解析为 Map 或 List
                            Object parsedArgs = objectMapper.readValue(strArg, Object.class);
                            
                            // 如果目标是 POJO，解析出来的是 Map，直接使用
                            if (parsedArgs instanceof Map && isPOJOType(paramType)) {
                                log.info("✅ 参数转换: 将 JSON String 解析为 Map 以匹配 POJO 类型 {}. JSON: {}", paramType, strArg);
                                convertedArgs[i] = parsedArgs;
                                continue;
                            }
                            
                            // 如果目标是 List/Set/Collection，解析出来的是 List
                            if (parsedArgs instanceof List && (
                                paramType.startsWith("java.util.List") || 
                                paramType.startsWith("java.util.Collection") ||
                                paramType.startsWith("java.util.Set") ||
                                paramType.endsWith("[]")
                            )) {
                                log.info("✅ 参数转换: 将 JSON String 解析为 List 以匹配集合类型 {}. JSON: {}", paramType, strArg);
                                convertedArgs[i] = parsedArgs; // Dubbo 泛化调用通常能处理 List -> Array/Set 的转换
                                continue;
                            }
                            
                            // 如果目标是 Map
                            if (parsedArgs instanceof Map && paramType.startsWith("java.util.Map")) {
                                convertedArgs[i] = parsedArgs;
                                continue;
                            }
                            
                            // 其他情况，如果在 isPOJOType 判定为真，尝试使用解析后的对象
                            if (isPOJOType(paramType)) {
                                log.info("✅ 参数转换: 将 JSON String 解析为 Object 以匹配 POJO 类型 {}.", paramType);
                                convertedArgs[i] = parsedArgs;
                                continue;
                            }
                            
                        } catch (Exception e) {
                            log.warn("⚠️ 尝试解析 JSON String 参数失败，保留原值: error={}, json={}", e.getMessage(), strArg);
                        }
                    }
                }
                
                // 其他情况：使用ParameterConverter转换（如果有）
                if (parameterConverter != null) {
                    convertedArgs[i] = parameterConverter.convertToJavaObject(arg, paramType, dubboVersion);
                } else {
                    convertedArgs[i] = arg;
                }
            }
            return convertedArgs;
        } else {
            // 如果无法获取方法签名，使用原有逻辑
            log.debug("Cannot get method signature, using original conversion logic");
            return args;
        }
    }
    
    /**
     * 判断是否是POJO类型（非基础类型、非集合类型、非Map类型）
     */
    private boolean isPOJOType(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return false;
        }
        
        // 基础类型
        if (typeName.equals("int") || typeName.equals("long") || typeName.equals("double") || 
            typeName.equals("float") || typeName.equals("boolean") || typeName.equals("short") ||
            typeName.equals("byte") || typeName.equals("char") ||
            typeName.equals("java.lang.Integer") || typeName.equals("java.lang.Long") ||
            typeName.equals("java.lang.Double") || typeName.equals("java.lang.Float") ||
            typeName.equals("java.lang.Boolean") || typeName.equals("java.lang.Short") ||
            typeName.equals("java.lang.Byte") || typeName.equals("java.lang.Character") ||
            typeName.equals("java.lang.String")) {
            return false;
        }
        
        // 集合类型
        if (typeName.startsWith("java.util.List") || typeName.startsWith("java.util.Set") ||
            typeName.startsWith("java.util.Collection") || typeName.startsWith("java.util.Map") ||
            typeName.startsWith("java.util.ArrayList") || typeName.startsWith("java.util.LinkedList") ||
            typeName.startsWith("java.util.HashSet") || typeName.startsWith("java.util.TreeSet")) {
            return false;
        }
        
        // 数组类型
        if (typeName.endsWith("[]")) {
            return false;
        }
        
        // 其他类型认为是POJO
        return true;
    }
    
    /**
     * 获取参数类型数组
     * 优先从 ZooKeeper metadata 获取，如果成功则直接返回，不再尝试其他方式
     */
    private String[] getParameterTypes(String interfaceName, String methodName, Object[] args, String dubboVersion) {
        long startTime = System.currentTimeMillis();
        log.info("🔍 开始获取参数类型: interface={}, method={}, args.length={}, dubboVersion={}", 
                interfaceName, methodName, args != null ? args.length : 0, dubboVersion);
        
        try {
            // 判断 Dubbo 版本是否支持 metadata（2.7+ 才支持）
            boolean metadataSupported = isMetadataSupported(dubboVersion);
            log.debug("   Dubbo 版本 {} {} metadata 支持", dubboVersion, metadataSupported ? "支持" : "不支持");
            
            String[] result;
            // 根据版本支持情况决定优先级
            if (metadataSupported) {
                // Dubbo 2.7+ 版本：优先从 metadata 获取，失败则从数据库读取
                result = getParameterTypesWithMetadataFirst(interfaceName, methodName, args);
            } else {
                // Dubbo 2.7 以下版本：优先从数据库读取，失败则尝试 metadata（以防万一）
                result = getParameterTypesWithDatabaseFirst(interfaceName, methodName, args);
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            if (result != null && result.length > 0) {
                log.info("✅ 获取参数类型完成 (耗时 {}ms): {}", elapsed, String.join(", ", result));
            } else {
                log.warn("⚠️ 获取参数类型完成 (耗时 {}ms): 未获取到参数类型，将使用推断逻辑", elapsed);
            }
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("❌ 获取参数类型失败 (耗时 {}ms): {}", elapsed, e.getMessage(), e);
            // 发生异常时，返回 null 触发推断逻辑
            return null;
        }
    }
    
    /**
     * 优先从 metadata 获取参数类型（适用于 Dubbo 2.7+）
     */
    private String[] getParameterTypesWithMetadataFirst(String interfaceName, String methodName, Object[] args) {
        log.debug("   使用 metadata 优先策略（适用于 Dubbo 2.7+）");
        
        // 1. 优先从 ZooKeeper metadata 获取（最准确）
        String[] typesFromMetadata = getParameterTypesFromMetadata(interfaceName, methodName, args);
        if (typesFromMetadata != null) {
            if (typesFromMetadata.length > 0) {
                log.info("✅ 从 ZooKeeper metadata 获取到参数类型: {} (类型数量: {})", 
                        String.join(", ", typesFromMetadata), typesFromMetadata.length);
                if (args != null && typesFromMetadata.length != args.length) {
                    log.warn("⚠️ metadata 返回的参数类型数量 ({}) 与实际参数数量 ({}) 不匹配，但仍使用 metadata 中的类型", 
                            typesFromMetadata.length, args.length);
                }
                return typesFromMetadata;
            } else {
                // 无参数方法
                log.info("✅ 从 ZooKeeper metadata 获取到参数类型: 无参数方法");
                return typesFromMetadata;
            }
        }
        
        // 2. metadata 获取失败，回退到数据库
        log.debug("   metadata 获取失败，回退到数据库读取");
        String[] typesFromDatabase = getParameterTypesFromDatabase(interfaceName, methodName, args);
        if (typesFromDatabase != null) {
            return typesFromDatabase;
        }
        
        // 3. 如果都无法获取，使用推断逻辑
        log.warn("⚠️ 无法从 metadata 或数据库获取参数类型，使用推断逻辑");
        String[] inferredTypes = inferParameterTypes(interfaceName, methodName, args);
        if (inferredTypes != null && inferredTypes.length > 0) {
            log.info("✅ 使用推断逻辑获取参数类型: {}", String.join(", ", inferredTypes));
        }
        return inferredTypes;
    }
    
    /**
     * 优先从数据库获取参数类型（适用于 Dubbo 2.7 以下版本）
     */
    private String[] getParameterTypesWithDatabaseFirst(String interfaceName, String methodName, Object[] args) {
        log.debug("   使用数据库优先策略（适用于 Dubbo 2.7 以下版本）");
        
        // 1. 优先从数据库读取（Dubbo 2.7 以下版本通常没有 metadata）
        String[] typesFromDatabase = getParameterTypesFromDatabase(interfaceName, methodName, args);
        if (typesFromDatabase != null) {
            return typesFromDatabase;
        }
        
        // 2. 数据库读取失败，尝试 metadata（以防万一，某些特殊配置可能启用了 metadata）
        log.debug("   数据库读取失败，尝试 metadata（以防万一）");
        String[] typesFromMetadata = getParameterTypesFromMetadata(interfaceName, methodName, args);
        if (typesFromMetadata != null) {
            if (typesFromMetadata.length > 0) {
                log.info("✅ 从 ZooKeeper metadata 获取到参数类型: {} (类型数量: {})", 
                        String.join(", ", typesFromMetadata), typesFromMetadata.length);
                return typesFromMetadata;
            } else {
                log.info("✅ 从 ZooKeeper metadata 获取到参数类型: 无参数方法");
                return typesFromMetadata;
            }
        }
        
        // 3. 如果都无法获取，使用推断逻辑
        log.warn("⚠️ 无法从数据库或 metadata 获取参数类型，使用推断逻辑");
        String[] inferredTypes = inferParameterTypes(interfaceName, methodName, args);
        if (inferredTypes != null && inferredTypes.length > 0) {
            log.info("✅ 使用推断逻辑获取参数类型: {}", String.join(", ", inferredTypes));
        }
        return inferredTypes;
    }
    
    /**
     * 从数据库获取参数类型（通过 MethodSignatureResolver）
     */
    private String[] getParameterTypesFromDatabase(String interfaceName, String methodName, Object[] args) {
        if (methodSignatureResolver == null) {
            log.debug("   MethodSignatureResolver 未注入，无法从数据库获取参数类型");
            return null;
        }
        
        try {
            log.debug("   从数据库读取方法签名: interface={}, method={}", interfaceName, methodName);
            MethodSignatureResolver.MethodSignature signature = 
                    methodSignatureResolver.getMethodSignature(interfaceName, methodName);
            
            if (signature != null && signature.getParameters() != null) {
                int paramCount = signature.getParameters().size();
                
                // 无参数方法
                if ((args == null || args.length == 0) && paramCount == 0) {
                    log.info("✅ 从数据库获取到参数类型: 无参数方法");
                    return new String[0];
                }
                
                // 有参数方法，检查参数数量是否匹配
                if (args != null && paramCount == args.length) {
                    String[] types = new String[paramCount];
                    for (int i = 0; i < paramCount; i++) {
                        types[i] = signature.getParameters().get(i).getType();
                    }
                    log.info("✅ 从数据库获取到参数类型: {} (类型数量: {})", 
                            String.join(", ", types), types.length);
                    return types;
                } else {
                    log.warn("⚠️ 数据库返回的参数数量 ({}) 与实际参数数量 ({}) 不匹配", 
                            paramCount, args != null ? args.length : 0);
                    // 如果数据库返回 0 个参数但实际有参数，返回 null 让调用方使用推断逻辑
                    if (paramCount == 0 && args != null && args.length > 0) {
                        log.warn("⚠️ 数据库显示无参数但实际有 {} 个参数，将使用推断逻辑", args.length);
                        return null; // 返回 null 触发推断逻辑
                    }
                    // 如果数据库有参数但数量不匹配，返回数据库中的类型（让调用方决定如何处理）
                    if (paramCount > 0) {
                        String[] types = new String[paramCount];
                        for (int i = 0; i < paramCount; i++) {
                            types[i] = signature.getParameters().get(i).getType();
                        }
                        return types;
                    }
                }
            } else {
                log.debug("   数据库中没有找到方法签名: {}.{}", interfaceName, methodName);
            }
        } catch (Exception e) {
            log.error("❌ 从数据库获取参数类型失败: interface={}, method={}, error={}", 
                    interfaceName, methodName, e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * 从 ZooKeeper metadata 获取参数类型
     * 优先使用指定路径格式直接读取 ZooKeeper，如果失败则回退到 SDK 方式和其他路径格式
     * 优先路径格式：/dubbo/metadata/{interfaceName}/{version}/{group}/provider/{application}
     * 实际示例：/dubbo/metadata/com.zkinfo.demo.service.UserService/1.0.0/demo/provider/demo-provider
     */
    private String[] getParameterTypesFromMetadata(String interfaceName, String methodName, Object[] args) {
        try {
            // 获取 Provider 信息
            ProviderInfo provider = getAvailableProvider(interfaceName);
            if (provider == null) {
                log.warn("⚠️ 未找到可用的 Provider: {}", interfaceName);
                return null;
            }
            
            String version = provider.getVersion() != null ? provider.getVersion() : "1.0.0";
            String group = provider.getGroup() != null && !provider.getGroup().isEmpty() ? provider.getGroup() : "";
            String application = provider.getApplication() != null ? provider.getApplication() : "";
            
            log.info("🔍 开始从 metadata 获取参数类型: interface={}, method={}, version={}, group={}, application={}", 
                    interfaceName, methodName, version, group, application);
            
            // 方式1: 优先使用指定路径格式直接读取 ZooKeeper
            // 路径格式：/dubbo/metadata/{interfaceName}/{version}/{group}/provider/{application}
            log.debug("   优先尝试直接读取 ZooKeeper 指定路径格式");
            String[] typesFromZK = getParameterTypesFromZooKeeper(interfaceName, methodName, version, group, application, args);
            if (typesFromZK != null) {
                log.info("✅ 通过 ZooKeeper 直接读取获取到参数类型: {}", String.join(", ", typesFromZK));
                return typesFromZK;
            }
            
            // 方式2: 回退到使用 Dubbo SDK 的 MetadataReport
            // Dubbo 2.5 不支持 MetadataReport，跳过此方式
            // log.debug("   ZooKeeper 直接读取失败，尝试使用 Dubbo SDK MetadataReport");
            // String[] typesFromSDK = getParameterTypesFromMetadataReport(interfaceName, methodName, version, group, application, args);
            // if (typesFromSDK != null) {
            //     log.info("✅ 通过 Dubbo SDK MetadataReport 获取到参数类型: {}", String.join(", ", typesFromSDK));
            //     return typesFromSDK;
            // }
            
            log.warn("⚠️ 所有 metadata 获取方式都失败: interface={}, method={}", interfaceName, methodName);
            return null;
            
        } catch (Exception e) {
            log.error("❌ 从 metadata 获取参数类型失败: interface={}, method={}, error={}", 
                    interfaceName, methodName, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 使用 Dubbo SDK 的 MetadataReport 获取参数类型（官方推荐方式）
     * Dubbo 2.5 不支持 MetadataReport，此方法已禁用
     */
    // private String[] getParameterTypesFromMetadataReport(String interfaceName, String methodName, 
    //                                                      String version, String group, String application, 
    //                                                      Object[] args) {
    //     // Dubbo 2.5 不支持 MetadataReport
    //     return null;
    // }
    
    /**
     * 获取或创建 MetadataReport 实例
     * Dubbo 2.5 不支持 MetadataReport，此方法已禁用
     */
    // private MetadataReport getOrCreateMetadataReport() {
    //     // Dubbo 2.5 不支持 MetadataReport
    //     return null;
    // }
    
    /**
     * 直接读取 ZooKeeper 获取参数类型（兼容方式）
     */
    private String[] getParameterTypesFromZooKeeper(String interfaceName, String methodName, 
                                                    String version, String group, String application, 
                                                    Object[] args) {
        if (zooKeeperService == null) {
            log.warn("⚠️ ZooKeeperService 未注入，无法从 ZooKeeper 获取参数类型");
            return null;
        }
        
        CuratorFramework client = zooKeeperService.getClient();
        if (client == null) {
            log.warn("⚠️ ZooKeeper 客户端未初始化，无法从 ZooKeeper 获取参数类型");
            return null;
        }
        
        // 构建 metadata 路径（优先使用用户指定的路径格式）
        List<String> metadataPaths = new ArrayList<>();
        
        // 格式1（最高优先级）: /dubbo/metadata/{interfaceName}/{version}/{group}/provider/{application}
        // 用户指定的实际格式示例：/dubbo/metadata/com.zkinfo.demo.service.UserService/1.0.0/demo/provider/demo-provider
        if (!group.isEmpty() && !application.isEmpty()) {
            String path1 = String.format("/dubbo/metadata/%s/%s/%s/provider/%s", 
                    interfaceName, version, group, application);
            metadataPaths.add(path1);
            log.info("   🎯 优先尝试路径1（用户指定格式）: {}", path1);
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
                log.debug("   检查路径是否存在: {}", metadataPath);
                if (client.checkExists().forPath(metadataPath) != null) {
                    log.info("   ✅ 找到 metadata 路径: {}", metadataPath);
                    
                    // 如果是目录，尝试读取目录下的所有节点
                    if (metadataPath.endsWith("/provider") || metadataPath.endsWith("/provider/")) {
                        List<String> children = client.getChildren().forPath(metadataPath);
                        log.debug("   发现 {} 个子节点", children != null ? children.size() : 0);
                        if (children != null && !children.isEmpty()) {
                            for (String child : children) {
                                String childPath = metadataPath + "/" + child;
                                log.debug("   尝试读取子节点: {}", childPath);
                                String[] types = parseMetadataForMethod(client, childPath, methodName, args);
                                if (types != null) {
                                    log.info("   ✅ 从子节点 {} 成功获取参数类型", childPath);
                                    return types;
                                }
                            }
                        }
                    } else {
                        // 直接读取文件
                        log.debug("   直接读取文件: {}", metadataPath);
                        String[] types = parseMetadataForMethod(client, metadataPath, methodName, args);
                        if (types != null) {
                            log.info("   ✅ 从文件 {} 成功获取参数类型", metadataPath);
                            return types;
                        }
                    }
                } else {
                    log.debug("   ❌ 路径不存在: {}", metadataPath);
                }
            } catch (Exception e) {
                log.warn("   ⚠️ 读取 metadata 路径失败: {}, error: {}", metadataPath, e.getMessage());
            }
        }
        
        log.warn("⚠️ 所有 ZooKeeper metadata 路径都无法获取参数类型: interface={}, method={}", interfaceName, methodName);
        return null;
    }
    
    /**
     * 从 metadata JSON 字符串中解析参数类型
     */
    private String[] parseParameterTypesFromMetadataJson(String metadataJson, String methodName, Object[] args) {
        try {
            JsonNode rootNode = objectMapper.readTree(metadataJson);
            JsonNode methodsNode = rootNode.get("methods");
            
            if (methodsNode != null && methodsNode.isArray()) {
                for (JsonNode methodNode : methodsNode) {
                    JsonNode nameNode = methodNode.get("name");
                    if (nameNode != null && methodName.equals(nameNode.asText())) {
                        // 找到目标方法，深入解析 parameterTypes
                        log.debug("   找到方法 {}，开始解析 parameterTypes", methodName);
                        
                        JsonNode parameterTypesNode = methodNode.get("parameterTypes");
                        if (parameterTypesNode != null && parameterTypesNode.isArray()) {
                            List<String> types = new ArrayList<>();
                            for (JsonNode typeNode : parameterTypesNode) {
                                String type = typeNode.asText();
                                types.add(type);
                                log.debug("     参数类型[{}]: {}", types.size() - 1, type);
                            }
                            
                            log.info("   ✅ 成功解析 parameterTypes，类型数量: {}，实际参数数量: {}", 
                                    types.size(), args != null ? args.length : 0);
                            log.info("   ✅ 参数类型列表: {}", String.join(", ", types));
                            
                            // 无论参数数量是否匹配，都返回从 metadata 解析出的类型
                            if (args != null && types.size() != args.length) {
                                log.warn("   ⚠️ 参数数量不匹配: metadata类型数量={}, 实际参数数量={}，但仍返回metadata中的类型", 
                                        types.size(), args.length);
                            }
                            
                            // 返回解析出的参数类型
                            return types.toArray(new String[0]);
                        } else {
                            log.debug("   方法 {} 没有 parameterTypes 字段或不是数组", methodName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("   解析 metadata JSON 失败: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 解析 metadata JSON，提取指定方法的参数类型
     */
    private String[] parseMetadataForMethod(CuratorFramework client, String metadataPath, 
                                           String methodName, Object[] args) {
        try {
            byte[] data = client.getData().forPath(metadataPath);
            if (data == null || data.length == 0) {
                log.debug("   metadata 数据为空: {}", metadataPath);
                return null;
            }
            
            String metadataJson = new String(data, StandardCharsets.UTF_8);
            log.debug("   读取到 metadata JSON，长度: {} 字节", metadataJson.length());
            
            // 缓存 metadata
            String cacheKey = metadataPath;
            metadataCache.put(cacheKey, metadataJson);
            
            // 解析 JSON
            JsonNode rootNode = objectMapper.readTree(metadataJson);
            JsonNode methodsNode = rootNode.get("methods");
            
            if (methodsNode == null) {
                log.debug("   metadata 中没有 methods 字段");
                return null;
            }
            
            if (methodsNode.isArray()) {
                log.debug("   发现 {} 个方法", methodsNode.size());
                for (JsonNode methodNode : methodsNode) {
                    JsonNode nameNode = methodNode.get("name");
                    if (nameNode != null) {
                        String currentMethodName = nameNode.asText();
                        log.debug("   检查方法: {}", currentMethodName);
                        if (methodName.equals(currentMethodName)) {
                            // 找到目标方法，深入解析 parameterTypes
                            log.info("   ✅ 找到方法 {}，开始解析 parameterTypes", methodName);
                            
                            JsonNode parameterTypesNode = methodNode.get("parameterTypes");
                            if (parameterTypesNode != null && parameterTypesNode.isArray()) {
                                List<String> types = new ArrayList<>();
                                for (JsonNode typeNode : parameterTypesNode) {
                                    String type = typeNode.asText();
                                    types.add(type);
                                    log.debug("     参数类型[{}]: {}", types.size() - 1, type);
                                }
                                
                                log.info("   ✅ 成功解析 parameterTypes，类型数量: {}，实际参数数量: {}", 
                                        types.size(), args != null ? args.length : 0);
                                log.info("   ✅ 参数类型列表: {}", String.join(", ", types));
                                
                                // 无论参数数量是否匹配，都返回从 metadata 解析出的类型
                                // 因为这是从 ZooKeeper metadata 获取的准确信息
                                if (args != null && types.size() != args.length) {
                                    log.warn("   ⚠️ 参数数量不匹配: metadata类型数量={}, 实际参数数量={}，但仍返回metadata中的类型", 
                                            types.size(), args.length);
                                }
                                
                                // 返回解析出的参数类型
                                return types.toArray(new String[0]);
                            } else {
                                log.warn("   ⚠️ 方法 {} 没有 parameterTypes 字段或不是数组", methodName);
                                // 尝试从其他字段获取参数信息
                                JsonNode parametersNode = methodNode.get("parameters");
                                if (parametersNode != null) {
                                    log.debug("   发现 parameters 字段，尝试从中提取类型信息");
                                }
                            }
                        }
                    }
                }
            } else {
                log.debug("   methods 不是数组格式");
            }
            
        } catch (Exception e) {
            log.warn("   ❌ 解析 metadata 失败: path={}, error={}", metadataPath, e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * 推断参数类型（原有逻辑，作为 fallback）
     * 
     * @param interfaceName 接口名（用于推断 POJO 类型）
     * @param methodName 方法名（用于推断 POJO 类型，如 createUser -> User）
     * @param args 参数数组
     * @return 参数类型字符串数组
     */
    private String[] inferParameterTypes(String interfaceName, String methodName, Object[] args) {
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
                    // 处理Map类型：尝试从方法名推断POJO类型
                    // 例如：createUser -> com.pajk.provider2.model.User
                    String pojoType = inferPOJOTypeFromMethodName(interfaceName, methodName, i);
                    if (pojoType != null) {
                        types[i] = pojoType;
                        log.info("✅ 从方法名推断 Map 参数类型: {} -> {}", methodName, pojoType);
                    } else {
                        // 如果无法推断，使用 java.util.Map 作为 fallback
                        types[i] = "java.util.Map";
                        log.warn("⚠️ Map 类型参数，无法从方法名推断 POJO 类型，使用 java.util.Map 作为 fallback");
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
     * 从方法名推断 POJO 类型
     * 例如：createUser -> com.pajk.provider2.model.User
     * 
     * @param interfaceName 接口名
     * @param methodName 方法名
     * @param paramIndex 参数索引
     * @return POJO 类型全限定名，如果无法推断则返回 null
     */
    private String inferPOJOTypeFromMethodName(String interfaceName, String methodName, int paramIndex) {
        if (methodName == null || methodName.isEmpty()) {
            return null;
        }
        
        // 常见的方法名前缀
        String[] prefixes = {"create", "update", "save", "add", "set", "put", "process", "handle", "submit"};
        String entityName = null;
        
        // 尝试从方法名提取实体名
        for (String prefix : prefixes) {
            if (methodName.startsWith(prefix) && methodName.length() > prefix.length()) {
                String suffix = methodName.substring(prefix.length());
                if (!suffix.isEmpty()) {
                    entityName = suffix;
                    break;
                }
            }
        }
        
        // 简单的单复数处理：如果以s结尾且不是ss/us结尾，尝试去掉s
        if (entityName != null && entityName.endsWith("s") && entityName.length() > 3 && 
            !entityName.endsWith("ss") && !entityName.endsWith("us")) {
            entityName = entityName.substring(0, entityName.length() - 1);
        }
        
        // 如果无法从前缀提取，尝试从接口名提取
        if (entityName == null && interfaceName != null && interfaceName.contains(".")) {
            String simpleInterfaceName = interfaceName.substring(interfaceName.lastIndexOf(".") + 1);
            // 移除常见的 Service/Manager/Facade 后缀
            String[] commonSuffixes = {"Service", "Manager", "Facade", "Controller", "Provider"};
            for (String s : commonSuffixes) {
                if (simpleInterfaceName.endsWith(s)) {
                    entityName = simpleInterfaceName.substring(0, simpleInterfaceName.length() - s.length());
                    break;
                }
            }
            if (entityName == null) {
                entityName = simpleInterfaceName;
            }
        }
        
        if (entityName == null || entityName.isEmpty()) {
            return null;
        }
        
        // 构建 POJO 类型全限定名
        // 尝试多种可能的包路径
        String[] possiblePackages = {
            "com.pajk.provider2.model",
            "com.pajk.provider2.entity",
            "com.pajk.provider2.domain",
            "com.pajk.provider2.dto",
            "com.pajk.provider1.model",
            "com.pajk.provider1.entity",
            "com.zkinfo.demo.model",
            "com.zkinfo.demo.entity",
            "com.zkinfo.demo.dto"
        };
        
        // 如果接口名包含包路径，尝试从接口包路径推断
        if (interfaceName.contains(".")) {
            String interfacePackage = interfaceName.substring(0, interfaceName.lastIndexOf("."));
            // 尝试将 service/api/core 替换为 model/entity/domain/dto/vo/pojo/request
            String[] replacements = {"model", "entity", "domain", "dto", "vo", "pojo", "request", "param"};
            String[] sourcePackages = {".service", ".api", ".core", ".facade"};
            
            for (String source : sourcePackages) {
                if (interfacePackage.contains(source)) {
                    for (String replacement : replacements) {
                        String possiblePackage = interfacePackage.replace(source, "." + replacement);
                        if (!possiblePackage.equals(interfacePackage)) {
                            String possibleType = possiblePackage + "." + entityName;
                            log.debug("尝试推断类型: {}", possibleType);
                            // 这里可以链式尝试多个，但在没找到类加载器验证的情况下，返回第一个比较通用的
                            return possibleType;
                        }
                    }
                }
            }
        }
        
        // 尝试常见的包路径
        for (String pkg : possiblePackages) {
            String possibleType = pkg + "." + entityName;
            log.debug("尝试推断类型: {}", possibleType);
            return possibleType;
        }
        
        return null;
    }
    
    /**
     * 从 ZooKeeper metadata 的 types 中获取类型的 properties（字段信息）
     * 用于验证和补充 Map 参数中的字段
     * 
     * @param typeName 类型全限定名（如 com.zkinfo.demo.model.Order）
     * @return 类型的 properties Map，key 为字段名，value 为字段类型
     */
    private Map<String, String> getTypePropertiesFromMetadata(String typeName) {
        if (zooKeeperService == null || typeName == null) {
            return null;
        }
        
        CuratorFramework client = zooKeeperService.getClient();
        if (client == null) {
            return null;
        }
        
        try {
            // 从缓存中查找 metadata
            for (Map.Entry<String, String> entry : metadataCache.entrySet()) {
                String metadataJson = entry.getValue();
                try {
                    JsonNode rootNode = objectMapper.readTree(metadataJson);
                    JsonNode typesNode = rootNode.get("types");
                    
                    if (typesNode != null && typesNode.isArray()) {
                        for (JsonNode typeNode : typesNode) {
                            JsonNode typeNameNode = typeNode.get("type");
                            if (typeNameNode != null && typeName.equals(typeNameNode.asText())) {
                                // 找到目标类型，提取 properties
                                JsonNode propertiesNode = typeNode.get("properties");
                                if (propertiesNode != null && propertiesNode.isObject()) {
                                    Map<String, String> properties = new HashMap<>();
                                    Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
                                    while (fields.hasNext()) {
                                        Map.Entry<String, JsonNode> field = fields.next();
                                        properties.put(field.getKey(), field.getValue().asText());
                                    }
                                    log.debug("✅ 从 metadata 获取类型 {} 的 {} 个字段", typeName, properties.size());
                                    return properties;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("解析 metadata 缓存失败: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.debug("从 metadata 获取类型属性失败: type={}, error={}", typeName, e.getMessage());
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
     * 优先从 zk_dubbo_* 表查找（包括虚拟项目聚合的 Provider），如果找不到再从 ProviderService 查找
     */
    private ProviderInfo getAvailableProvider(String interfaceName) {
        // 1. 优先从 zk_dubbo_* 表查找（包括虚拟项目聚合的 Provider）
        if (dubboServiceDbService != null) {
            try {
                // 查找匹配的 Dubbo 服务（findByInterfaceName 返回第一个匹配的服务）
                com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity service = 
                        dubboServiceDbService.findByInterfaceName(interfaceName);
                if (service != null) {
                    // 从该服务获取 Provider 列表
                    List<ProviderInfo> providers = dubboServiceDbService.getProvidersByServiceId(service.getId());
                    if (providers != null && !providers.isEmpty()) {
                        // 优先返回在线的 Provider
                        ProviderInfo onlineProvider = providers.stream()
                                .filter(ProviderInfo::isOnline)
                                .findFirst()
                                .orElse(null);
                        if (onlineProvider != null) {
                            log.info("✅ Found provider from zk_dubbo_* tables: {}:{}:{} at {}:{} (serviceId: {})", 
                                    onlineProvider.getInterfaceName(), 
                                    onlineProvider.getVersion(), 
                                    onlineProvider.getGroup(),
                                    onlineProvider.getAddress(),
                                    onlineProvider.getPort(),
                                    service.getId());
                            return onlineProvider;
                        }
                        // 如果没有在线的，返回第一个（可能是在线状态未更新）
                        log.warn("⚠️ No online provider found for serviceId {}, using first provider: {}:{}:{}", 
                                service.getId(),
                                providers.get(0).getInterfaceName(),
                                providers.get(0).getVersion(),
                                providers.get(0).getGroup());
                        return providers.get(0);
                    } else {
                        log.debug("No providers found for serviceId: {}", service.getId());
                    }
                } else {
                    log.debug("No service found for interface: {}", interfaceName);
                }
            } catch (Exception e) {
                log.warn("Failed to get provider from zk_dubbo_* tables: {}", e.getMessage());
            }
        }
        
        // 2. 回退到 ProviderService（实际项目的 Provider）
        ProviderInfo provider = providerService.getProvidersByInterface(interfaceName)
                .stream()
                .filter(ProviderInfo::isOnline)
                .findFirst()
                .orElse(null);
        
        if (provider != null) {
            log.info("✅ Found provider from ProviderService: {}:{}:{} at {}:{}", 
                    provider.getInterfaceName(), 
                    provider.getVersion(), 
                    provider.getGroup(),
                    provider.getAddress(),
                    provider.getPort());
        } else {
            log.warn("❌ No provider found for interface: {}", interfaceName);
        }
        
        return provider;
    }
    
    /**
     * 预订阅服务（启动时使用）
     * 创建服务引用并触发订阅，如果超时则只记录警告，不抛出异常
     * 
     * @param interfaceName 接口名称
     * @param provider Provider 信息
     * @return 是否成功创建引用
     */
    public boolean preSubscribeService(String interfaceName, ProviderInfo provider) {
        try {
            // 调用 getOrCreateServiceReference 会创建 ReferenceConfig 并触发订阅
            // 如果超时，getOrCreateServiceReference 会抛出异常，我们捕获它并返回 false
            GenericService service = getOrCreateServiceReference(interfaceName, provider);
            if (service != null) {
                log.info("✅ 预订阅成功: {}", interfaceName);
                return true;
            } else {
                log.warn("⚠️ 预订阅返回 null: {}", interfaceName);
                return false;
            }
        } catch (RuntimeException e) {
            // 如果是超时异常，只记录警告，不抛出异常（避免阻塞启动）
            if (e.getMessage() != null && e.getMessage().contains("超时")) {
                log.warn("⚠️ 预订阅服务超时（将在首次调用时自动订阅）: {}, error: {}", 
                        interfaceName, e.getMessage());
            } else {
                log.warn("⚠️ 预订阅服务失败（将在首次调用时自动订阅）: {}, error: {}", 
                        interfaceName, e.getMessage());
            }
            return false;
        } catch (Exception e) {
            log.warn("⚠️ 预订阅服务异常（将在首次调用时自动订阅）: {}, error: {}", 
                    interfaceName, e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取或创建服务引用
     */
    GenericService getOrCreateServiceReference(String interfaceName, ProviderInfo provider) {
        // 检测 Dubbo 版本，判断是否支持 group（在方法开始处定义，供后续使用）
        // 对于不支持 group 的版本（如 2.5），cacheKey 不应该包含 group
        final String dubboVersion = detectDubboVersion(provider);
        final boolean groupSupported = isGroupSupported(dubboVersion, provider);
        
        // 构建 cacheKey：如果版本不支持 group，则不包含 group
        String cacheKey;
        if (groupSupported && provider.getGroup() != null && !provider.getGroup().isEmpty()) {
            cacheKey = interfaceName + ":" + provider.getGroup() + ":" + provider.getVersion();
        } else {
            cacheKey = interfaceName + ":" + provider.getVersion();
            log.debug("⚠️ Dubbo 版本 {} 不支持 group，cacheKey 不包含 group: {}", dubboVersion, cacheKey);
        }
        
        // 先尝试从缓存获取，如果存在且已初始化，直接使用
        ReferenceConfig<GenericService> existingRef = referenceCache.get(cacheKey);
        if (existingRef != null) {
            try {
                // 尝试获取服务实例，如果成功说明已初始化
                GenericService existingService = existingRef.get();
                if (existingService != null) {
                    log.info("✅ 复用已存在的 ReferenceConfig: {}", cacheKey);
                    return existingService;
                }
            } catch (Exception e) {
                // 如果获取失败，说明引用可能已失效，需要重新创建
                log.warn("⚠️ 缓存的 ReferenceConfig 已失效，将重新创建: {}, error: {}", cacheKey, e.getMessage());
                referenceCache.remove(cacheKey);
                try {
                    existingRef.destroy();
                } catch (Exception destroyEx) {
                    log.warn("销毁失效的 ReferenceConfig 失败: {}", destroyEx.getMessage());
                }
            }
        }
        
        // 创建新的 ReferenceConfig（只有在缓存不存在或已失效时才创建）
        ReferenceConfig<GenericService> reference = referenceCache.computeIfAbsent(cacheKey, key -> {
            try {
                ReferenceConfig<GenericService> ref = new ReferenceConfig<>();
                
                // 确保配置已初始化
                if (applicationConfig == null || registryConfig == null) {
                    log.warn("Dubbo 配置未初始化，重新初始化...");
                    init();
                }
                
                // 设置 ApplicationConfig（虽然已废弃，但某些版本仍需要）
                ref.setApplication(applicationConfig);
                ref.setInterface(interfaceName);
                
                // 对于 Dubbo 2.5 Provider，使用直接 URL 方式连接，完全绕过订阅机制
                if (!groupSupported) {
                    // Dubbo 2.5 使用直接 URL 方式，不通过注册中心订阅，避免创建 routers 路径的问题
                    // 注意：provider.getAddress() 返回的是 "IP:Port" 格式，所以使用 getIp() 和 getPort() 分别获取
                    String ip = provider.getIp();
                    Integer port = provider.getPort();
                    if (ip == null || port == null) {
                        throw new IllegalArgumentException("Provider 地址或端口为空: address=" + provider.getAddress());
                    }
                    String directUrl = String.format("dubbo://%s:%d/%s?version=%s&generic=true&serialization=hessian2&timeout=%d&check=false&retries=0",
                            ip,
                            port,
                            interfaceName,
                            provider.getVersion() != null ? provider.getVersion() : "1.0.0",
                            dubboTimeout);
                    ref.setUrl(directUrl);
                    // 重要：不设置 Registry，避免触发订阅机制
                    log.info("🔧 使用直接 URL 方式连接 Dubbo 2.5 Provider（绕过订阅机制）: {}", directUrl);
                } else {
                    // Dubbo 2.7+ 使用 ZooKeeper 注册中心（通过 SDK 方式连接）
                    ref.setRegistry(registryConfig);
                    log.info("🔧 检测到 Dubbo 2.7+ Provider，使用 ZooKeeper SDK 方式连接");
                }
                
                // 设置 ProtocolConfig，强制使用 hessian2 序列化
                if (protocolConfig != null) {
                    // 直接设置 ProtocolConfig 对象，确保序列化配置生效
                    try {
                        java.lang.reflect.Method setProtocolMethod = ref.getClass().getMethod("setProtocol", org.apache.dubbo.config.ProtocolConfig.class);
                        setProtocolMethod.invoke(ref, protocolConfig);
                        log.debug("✅ 通过 ProtocolConfig 设置 serialization=hessian2");
                    } catch (Exception e) {
                        // 如果方法不存在，则只设置协议名称
                        ref.setProtocol(protocolConfig.getName());
                        log.debug("⚠️ 无法通过 ProtocolConfig 设置，使用协议名称: {}", e.getMessage());
                    }
                }
                
                // 关键：设置泛化调用为 true（必须）
                // 注意：必须在所有其他配置之前设置
                ref.setGeneric("true");
                
                // 通过反射强制设置 generic 字段（如果存在）
                try {
                    java.lang.reflect.Field genericField = ref.getClass().getDeclaredField("generic");
                    genericField.setAccessible(true);
                    genericField.set(ref, "true");
                    log.info("✅ 通过反射强制设置 generic=true");
                } catch (Exception e) {
                    log.debug("⚠️ 无法通过反射设置 generic 字段: {}", e.getMessage());
                }
                
                // 强制设置序列化方式为 hessian2
                // 通过多种方式确保序列化方式正确设置
                Map<String, String> parameters = new HashMap<>();
                // 优先设置序列化方式，确保不被 fastjson2 覆盖
                parameters.put("serialization", "hessian2");
                parameters.put("generic", "true");  // 确保 generic 参数正确设置（双重保险）
                // 设置 prefer.serialization 为 hessian2，避免使用 fastjson2（序列化类型 23）
                parameters.put("prefer.serialization", "hessian2");
                // 禁用 fastjson2 序列化（Dubbo 2.6.7 可能不支持，但加上也无妨）
                parameters.put("serialization.fastjson2", "false");
                // 禁用其他序列化方式
                parameters.put("serialization.before", "false");
                // 强制使用 hessian2，不允许降级
                parameters.put("serialization.check", "false");
                // 确保不使用默认序列化
                parameters.put("default.serialization", "hessian2");
                ref.setParameters(parameters);
                
                // 显式设置序列化方式（通过反射，如果方法存在）
                try {
                    java.lang.reflect.Method setSerializationMethod = ref.getClass().getMethod("setSerialization", String.class);
                    setSerializationMethod.invoke(ref, "hessian2");
                    log.debug("✅ 通过反射设置 serialization=hessian2");
                } catch (Exception e) {
                    log.debug("⚠️ 无法通过反射设置 serialization，使用 parameters: {}", e.getMessage());
                }
                
                // 通过 ConsumerConfig 设置（如果存在）
                try {
                    org.apache.dubbo.config.ConsumerConfig consumerConfig = new org.apache.dubbo.config.ConsumerConfig();
                    consumerConfig.setGeneric("true");
                    ref.setConsumer(consumerConfig);
                    log.debug("✅ 通过 ConsumerConfig 设置 serialization=hessian2");
                } catch (Exception e) {
                    log.debug("⚠️ 无法通过 ConsumerConfig 设置: {}", e.getMessage());
                }
                
                // 设置版本和分组
                if (provider.getVersion() != null) {
                    ref.setVersion(provider.getVersion());
                }
                
                // 使用外部定义的 dubboVersion 和 groupSupported（避免重复定义）
                // Dubbo 2.5 及更早版本不支持 group，设置 group 会导致找不到 Provider
                if (groupSupported && provider.getGroup() != null && !provider.getGroup().isEmpty()) {
                    ref.setGroup(provider.getGroup());
                    log.debug("✅ 设置 group: {} (Dubbo 版本: {})", provider.getGroup(), dubboVersion);
                } else {
                    if (!groupSupported) {
                        log.debug("⚠️ Dubbo 版本 {} 不支持 group，显式清除 group 设置", dubboVersion);
                        // 对于不支持 group 的版本，显式设置为 null 或空字符串，确保不会使用 group
                        try {
                            ref.setGroup(null);
                            // 同时从 parameters 中移除 group（如果存在）
                            Map<String, String> currentParams = ref.getParameters();
                            if (currentParams != null && currentParams.containsKey("group")) {
                                currentParams.remove("group");
                                ref.setParameters(currentParams);
                                log.debug("✅ 从 parameters 中移除 group 参数");
                            }
                        } catch (Exception e) {
                            log.debug("⚠️ 无法清除 group 设置: {}", e.getMessage());
                        }
                    } else {
                        log.debug("⚠️ Provider 的 group 为空，不设置 group");
                    }
                }
                
                // 设置超时时间（从配置文件读取，默认 30 秒）
                ref.setTimeout(dubboTimeout);
                log.info("✅ 设置 Dubbo 调用超时时间: {} ms", dubboTimeout);
                
                // 设置检查服务是否可用（避免启动时检查失败）
                ref.setCheck(false);
                
                // 对于使用注册中心的情况（Dubbo 2.7+），禁用 Consumer 注册到 ZooKeeper
                // 对于直接 URL 方式（Dubbo 2.5），不需要这些配置
                if (groupSupported) {
                    // 通过 parameters 设置 register=false
                    Map<String, String> refParams = ref.getParameters();
                    if (refParams == null) {
                        refParams = new HashMap<>();
                    }
                    refParams.put("register", "false");
                    // 禁用 Dubbo 3.x 的 routers 和 configurators 路径创建（避免订阅时创建路径失败）
                    // 只订阅 providers，不订阅 routers 和 configurators
                    refParams.put("category", "providers");
                    // 禁用动态配置
                    refParams.put("dynamic", "false");
                    ref.setParameters(refParams);
                    log.info("✅ 禁用 Consumer 注册到 ZooKeeper（只订阅 providers，不创建 routers/configurators）");
                } else {
                    log.info("✅ 使用直接 URL 方式，无需注册中心配置");
                }
                
                // 设置重试次数
                ref.setRetries(0);
                
                // 设置负载均衡策略（仅对使用注册中心的情况有效）
                if (groupSupported) {
                    ref.setLoadbalance("roundrobin");
                }
                
                // 记录连接方式
                if (!groupSupported) {
                    log.info("✅ 使用直接 URL 方式连接 Provider（Dubbo 版本: {}，绕过订阅机制）", dubboVersion);
                } else {
                    log.info("✅ 使用 ZooKeeper SDK 方式连接 Provider（Dubbo 版本: {}）", dubboVersion);
                }
                
                log.info("创建服务引用: {} (group: {}, version: {}, dubboVersion: {}, groupSupported: {})", 
                        interfaceName, provider.getGroup(), provider.getVersion(), dubboVersion, groupSupported);
                
                // 不在这里调用 get()，让调用者负责获取服务实例
                // 这样可以避免在配置不正确时提前失败
                return ref;
            } catch (Exception e) {
                log.error("创建 ReferenceConfig 失败: {}", interfaceName, e);
                throw new RuntimeException("创建服务引用失败: " + e.getMessage(), e);
            }
        });
        
        try {
            // 在调用 get() 之前，再次验证并强制设置配置
            String currentGeneric = reference.getGeneric();
            if (!"true".equals(currentGeneric)) {
                log.warn("⚠️ generic 配置不正确: {}，强制设置为 true", currentGeneric);
                reference.setGeneric("true");
                
                // 通过反射强制设置 generic 字段
                try {
                    java.lang.reflect.Field genericField = reference.getClass().getDeclaredField("generic");
                    genericField.setAccessible(true);
                    genericField.set(reference, "true");
                    log.info("✅ 通过反射强制设置 generic=true");
                } catch (Exception e) {
                    log.warn("无法通过反射设置 generic: {}", e.getMessage());
                }
                
                // 再次通过 parameters 设置
                Map<String, String> params = reference.getParameters();
                if (params == null) {
                    params = new HashMap<>();
                }
                params.put("generic", "true");
                reference.setParameters(params);
                log.info("✅ 通过 parameters 强制设置 generic=true");
            }
            
            // 调用 get() 可能会触发 Dubbo 框架的静态初始化
            // 如果发生 ExceptionInInitializerError，说明 Dubbo 框架初始化失败
            log.info("🔍 准备获取 GenericService 实例: interface={}, cacheKey={}", interfaceName, cacheKey);
            long getStartTime = System.currentTimeMillis();
            
            // 使用 CompletableFuture 和超时保护，避免 reference.get() 阻塞
            // 设置超时时间为 30 秒（ZooKeeper 连接不稳定时需要更长时间）
            // 使用自定义线程池，避免阻塞 ForkJoinPool.commonPool
            int getTimeoutSeconds = 30;
            GenericService service;
            CompletableFuture<GenericService> future = null;
            java.util.concurrent.ExecutorService executor = null;
            try {
                // 创建单线程执行器，专门用于执行 reference.get()
                executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "Dubbo-Reference-Get-" + interfaceName);
                    t.setDaemon(true);
                    return t;
                });
                
                future = CompletableFuture.supplyAsync(() -> {
                    try {
                        log.debug("   在独立线程中执行 reference.get(): interface={}", interfaceName);
                        // 检查线程中断状态，如果已中断则清除中断标志并重试
                        if (Thread.currentThread().isInterrupted()) {
                            log.warn("   线程已中断，清除中断标志并继续执行");
                            Thread.interrupted(); // 清除中断标志
                        }
                        return reference.get();
                    } catch (Exception e) {
                        // 检查是否是 InterruptedException 或其包装异常
                        Throwable cause = e;
                        boolean isInterrupted = false;
                        
                        // 检查异常链中是否有 InterruptedException
                        while (cause != null) {
                            if (cause instanceof InterruptedException) {
                                isInterrupted = true;
                                break;
                            }
                            // 检查是否是 IllegalStateException 包装的 InterruptedException
                            if (cause instanceof IllegalStateException && cause.getCause() instanceof InterruptedException) {
                                isInterrupted = true;
                                break;
                            }
                            cause = cause.getCause();
                        }
                        
                        if (isInterrupted) {
                            log.warn("   reference.get() 底层操作被中断，清除中断标志并重试: interface={}", interfaceName);
                            try {
                                // 清除中断标志并重试
                                Thread.interrupted();
                                return reference.get();
                            } catch (Exception retryException) {
                                // 如果重试仍然失败，包装为 RuntimeException
                                throw new RuntimeException("获取 GenericService 失败（重试后）: " + retryException.getMessage(), retryException);
                            }
                        }
                        // 其他异常直接包装为 RuntimeException
                        throw new RuntimeException("获取 GenericService 失败: " + e.getMessage(), e);
                    }
                }, executor);
                
                service = future.get(getTimeoutSeconds, TimeUnit.SECONDS);
                long getElapsed = System.currentTimeMillis() - getStartTime;
                log.info("✅ 成功获取 GenericService 实例 (耗时 {}ms): interface={}", getElapsed, interfaceName);
            } catch (java.util.concurrent.TimeoutException e) {
                long getElapsed = System.currentTimeMillis() - getStartTime;
                log.error("❌ 获取 GenericService 实例超时 (耗时 {}ms，超时时间 {}s): interface={}", 
                        getElapsed, getTimeoutSeconds, interfaceName);
                log.error("   可能原因：1) ZooKeeper 连接不稳定 2) Provider 未正确注册 3) Dubbo 版本不兼容");
                log.error("   建议：检查 ZooKeeper 连接状态和 Provider 注册情况");
                
                // 取消 future，避免继续占用资源
                if (future != null) {
                    future.cancel(true);
                }
                
                // 不要立即移除缓存，保留 ReferenceConfig 以便后台重试
                // Dubbo 有自动重试机制，订阅会在后台继续重试
                log.warn("⚠️ 保留 ReferenceConfig 在缓存中，等待 Dubbo 后台重试订阅");
                
                throw new RuntimeException("获取 GenericService 实例超时（" + getTimeoutSeconds + "秒）: " + 
                        "interface=" + interfaceName + "，可能原因：ZooKeeper 连接不稳定或 Provider 未正确注册。订阅将在后台继续重试。", e);
            } catch (Exception e) {
                long getElapsed = System.currentTimeMillis() - getStartTime;
                log.error("❌ 获取 GenericService 实例失败 (耗时 {}ms): interface={}, error={}", 
                        getElapsed, interfaceName, e.getMessage(), e);
                // 取消 future，避免继续占用资源
                if (future != null) {
                    future.cancel(true);
                }
                throw e;
            } finally {
                // 关闭执行器
                if (executor != null) {
                    executor.shutdown();
                    try {
                        if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                            executor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        executor.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                }
            }
            
            // 验证 URL 中的配置（包括 generic 和 group）
            try {
                java.lang.reflect.Method getUrlMethod = reference.getClass().getMethod("getUrl");
                Object url = getUrlMethod.invoke(reference);
                if (url != null) {
                    String urlStr = url.toString();
                    log.info("✅ ReferenceConfig URL: {}", urlStr);
                    
                    // 检查 URL 中是否包含 generic=true
                    if (urlStr.contains("generic=false")) {
                        log.error("❌ URL 中仍然显示 generic=false，配置可能未生效！");
                    } else if (urlStr.contains("generic=true")) {
                        log.info("✅ URL 中确认 generic=true，配置已生效");
                    }
                    
                    // 检查 URL 中是否包含 group 参数（对于不支持 group 的版本，不应该有 group）
                    // 使用外部定义的 dubboVersion 和 groupSupported（避免重复定义）
                    if (!groupSupported && urlStr.contains("group=")) {
                        log.warn("⚠️ Dubbo 版本 {} 不支持 group，但 URL 中仍然包含 group 参数: {}", dubboVersion, urlStr);
                    } else if (groupSupported && urlStr.contains("group=")) {
                        log.debug("✅ URL 中包含 group 参数（版本支持）");
                    } else if (!groupSupported && !urlStr.contains("group=")) {
                        log.info("✅ URL 中不包含 group 参数（版本不支持，符合预期）");
                    }
                }
            } catch (Exception e) {
                log.debug("无法获取 URL: {}", e.getMessage());
            }
            
            return service;
        } catch (ExceptionInInitializerError e) {
            log.error("❌ Dubbo 框架初始化失败 (ExceptionInInitializerError): {}", e.getMessage(), e);
            // 获取根本原因
            Throwable cause = e.getCause();
            if (cause != null) {
                log.error("根本原因: {}", cause.getMessage(), cause);
            }
            throw new RuntimeException("Dubbo 框架初始化失败，请检查 ZooKeeper 连接和 Dubbo 配置: " + 
                    (cause != null ? cause.getMessage() : e.getMessage()), e);
        } catch (Exception e) {
            log.error("获取服务引用失败: {}", interfaceName, e);
            // 从缓存中移除失败的引用，以便下次重试
            referenceCache.remove(cacheKey);
            throw new RuntimeException("获取服务引用失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解析参数类型不匹配错误，生成友好的错误信息
     * 
     * Dubbo 的错误信息格式通常是：
     * "Failed to set pojo User property phone value 1388883(class java.lang.Integer), cause: argument type mismatch"
     * 
     * @param e 异常对象
     * @param interfaceName 接口名称（可选，用于生成更详细的错误信息）
     * @param methodName 方法名称（可选，用于生成更详细的错误信息）
     * @return 友好的错误信息，如果不是参数类型不匹配错误则返回 null
     */
    private String parseArgumentTypeMismatchError(Throwable e, String interfaceName, String methodName) {
        if (e == null) {
            return null;
        }
        
        String message = e.getMessage();
        if (message == null) {
            return null;
        }
        
        // 检查是否包含参数类型不匹配的关键词
        if (!message.contains("argument type mismatch") && 
            !message.contains("Failed to set pojo") &&
            !message.contains("property") && 
            !message.contains("value")) {
            return null;
        }
        
        // 解析错误信息
        // 格式：Failed to set pojo <ClassName> property <fieldName> value <value>(class <actualType>), cause: argument type mismatch
        try {
            String pojoClassName = null;
            String fieldName = null;
            String actualValue = null;
            String actualType = null;
            
            // 提取 POJO 类名：在 "pojo" 和 "property" 之间
            int pojoIndex = message.indexOf("pojo ");
            int propertyIndex = message.indexOf(" property ");
            if (pojoIndex >= 0 && propertyIndex > pojoIndex) {
                pojoClassName = message.substring(pojoIndex + 5, propertyIndex).trim();
            }
            
            // 提取字段名：在 "property" 和 "value" 之间
            int valueIndex = message.indexOf(" value ");
            if (propertyIndex >= 0 && valueIndex > propertyIndex) {
                fieldName = message.substring(propertyIndex + 10, valueIndex).trim();
            }
            
            // 提取实际值和类型：在 "value" 和 "class" 之间，以及 "class" 之后
            int classIndex = message.indexOf("(class ");
            int causeIndex = message.indexOf("), cause:");
            if (valueIndex >= 0 && classIndex > valueIndex) {
                actualValue = message.substring(valueIndex + 7, classIndex).trim();
            }
            if (classIndex >= 0 && causeIndex > classIndex) {
                actualType = message.substring(classIndex + 7, causeIndex).trim();
            }
            
            // 如果成功解析，生成友好的错误信息
            if (pojoClassName != null && fieldName != null && actualType != null) {
                StringBuilder friendlyMsg = new StringBuilder();
                friendlyMsg.append("参数类型不匹配：");
                
                if (interfaceName != null && methodName != null) {
                    friendlyMsg.append(String.format("调用 %s.%s 时，", interfaceName, methodName));
                }
                
                friendlyMsg.append(String.format("POJO 类 '%s' 的字段 '%s' 期望的类型与传入的类型不匹配。", 
                        pojoClassName, fieldName));
                
                if (actualValue != null && !actualValue.isEmpty()) {
                    friendlyMsg.append(String.format(" 传入的值: %s", actualValue));
                }
                
                friendlyMsg.append(String.format(" 传入的类型: %s", getSimpleTypeName(actualType)));
                
                friendlyMsg.append(" 请检查传入的 Map 中该字段的类型是否正确。");
                
                // 添加常见类型转换建议
                if (actualType.contains("Integer") || actualType.contains("Long") || actualType.contains("Number")) {
                    friendlyMsg.append(" 提示：如果字段期望 String 类型，请将数值转换为字符串（例如：\"123\" 而不是 123）。");
                } else if (actualType.contains("String")) {
                    friendlyMsg.append(" 提示：如果字段期望数值类型，请确保字符串可以转换为对应的数值类型。");
                }
                
                return friendlyMsg.toString();
            }
        } catch (Exception parseEx) {
            log.debug("解析参数类型不匹配错误失败: {}", parseEx.getMessage());
        }
        
        // 如果无法解析，返回包含原始错误信息的友好提示
        return "参数类型不匹配：" + message + "。请检查传入的参数类型是否正确。";
    }
    
    /**
     * 在异常链中查找参数类型不匹配错误
     * 
     * @param e 异常对象
     * @param interfaceName 接口名称（可选）
     * @param methodName 方法名称（可选）
     * @return 友好的错误信息，如果未找到则返回 null
     */
    private String findArgumentTypeMismatchInCauseChain(Throwable e, String interfaceName, String methodName) {
        Throwable current = e;
        int depth = 0;
        while (current != null && depth < 10) { // 最多遍历 10 层异常链
            String friendlyMsg = parseArgumentTypeMismatchError(current, interfaceName, methodName);
            if (friendlyMsg != null) {
                return friendlyMsg;
            }
            current = current.getCause();
            depth++;
        }
        return null;
    }
    
    /**
     * 获取类型的简单名称（去掉包名）
     * 
     * @param fullTypeName 完整类型名，如 "java.lang.Integer"
     * @return 简单类型名，如 "Integer"
     */
    private String getSimpleTypeName(String fullTypeName) {
        if (fullTypeName == null || fullTypeName.isEmpty()) {
            return fullTypeName;
        }
        int lastDot = fullTypeName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < fullTypeName.length() - 1) {
            return fullTypeName.substring(lastDot + 1);
        }
        return fullTypeName;
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
