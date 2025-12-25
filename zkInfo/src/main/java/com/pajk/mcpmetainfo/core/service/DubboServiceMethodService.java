package com.pajk.mcpmetainfo.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpmetainfo.persistence.mapper.DubboServiceMethodMapper;
import com.pajk.mcpmetainfo.persistence.mapper.DubboMethodParameterMapper;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceMethodEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboMethodParameterEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dubbo服务方法信息服务类
 * 
 * 负责处理Dubbo服务方法信息与数据库之间的交互，包括保存、更新、删除等操作。
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
public class DubboServiceMethodService {
    
    @Autowired
    private DubboServiceMethodMapper dubboServiceMethodMapper;
    
    @Autowired
    private DubboMethodParameterMapper dubboMethodParameterMapper;
    
    @Autowired
    private com.pajk.mcpmetainfo.persistence.mapper.DubboServiceMapper dubboServiceMapper;
    
    @Autowired(required = false)
    @Lazy
    private ZooKeeperService zooKeeperService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 保存或更新Dubbo服务方法信息到数据库
     * 
     * @param providerInfo Provider信息
     * @param serviceId 关联的服务ID
     */
    @Transactional
    public void saveOrUpdateServiceMethods(ProviderInfo providerInfo, Long serviceId) {
        try {
            // 获取当前服务下已有的方法信息
            List<DubboServiceMethodEntity> existingMethods = dubboServiceMethodMapper.findByServiceId(serviceId);
            
            // 创建现有方法名到方法实体的映射，便于快速查找
            Map<String, DubboServiceMethodEntity> existingMethodMap = new HashMap<>();
            for (DubboServiceMethodEntity method : existingMethods) {
                existingMethodMap.put(method.getMethodName(), method);
            }
            
            // 解析并处理方法信息
            Set<String> currentMethodNames = new HashSet<>();
            if (providerInfo.getMethods() != null && !providerInfo.getMethods().isEmpty()) {
                String[] methods = providerInfo.getMethods().split(",");
                for (int i = 0; i < methods.length; i++) {
                    String methodName = methods[i].trim();
                    if (!methodName.isEmpty()) {
                        currentMethodNames.add(methodName);
                        
                        // 从 zk_dubbo_service 获取 version
                        DubboServiceEntity service = dubboServiceMapper.findById(serviceId);
                        String version = service != null ? service.getVersion() : null;
                        
                        // 检查方法是否已存在
                        DubboServiceMethodEntity methodEntity;
                        if (existingMethodMap.containsKey(methodName)) {
                            // 方法已存在，更新 interface_name、version 和时间戳
                            methodEntity = existingMethodMap.get(methodName);
                            methodEntity.setInterfaceName(providerInfo.getInterfaceName());
                            methodEntity.setVersion(version);
                            methodEntity.setUpdatedAt(LocalDateTime.now());
                            dubboServiceMethodMapper.update(methodEntity);
                            log.debug("更新已存在的Dubbo服务方法信息: {} (ID: {})", methodName, methodEntity.getId());
                        } else {
                            // 新方法，创建并保存（使用 ON DUPLICATE KEY UPDATE 避免并发问题）
                            methodEntity = new DubboServiceMethodEntity(
                                serviceId, providerInfo.getInterfaceName(), version, methodName, "java.lang.Object");
                            
                            // 保存方法信息（如果已存在则更新，否则插入）
                            try {
                                dubboServiceMethodMapper.insert(methodEntity);
                                // 如果插入后 id 仍为 null，说明是更新操作，需要查询获取 id
                                if (methodEntity.getId() == null) {
                                    DubboServiceMethodEntity found = dubboServiceMethodMapper.findByServiceIdAndMethodName(serviceId, methodName);
                                    if (found != null) {
                                        methodEntity.setId(found.getId());
                                    }
                                }
                                log.debug("保存新的Dubbo服务方法信息: {} (ID: {})", methodName, methodEntity.getId());
                            } catch (Exception e) {
                                // 如果插入失败（可能是并发插入），尝试查询已存在的记录
                                log.warn("插入方法失败，尝试查询已存在的记录: methodName={}, error={}", methodName, e.getMessage());
                                DubboServiceMethodEntity found = dubboServiceMethodMapper.findByServiceIdAndMethodName(serviceId, methodName);
                                if (found != null) {
                                    methodEntity = found;
                                    methodEntity.setInterfaceName(providerInfo.getInterfaceName());
                                    methodEntity.setVersion(version);
                                    methodEntity.setUpdatedAt(LocalDateTime.now());
                                    dubboServiceMethodMapper.update(methodEntity);
                                    log.debug("更新已存在的Dubbo服务方法信息（从异常恢复）: {} (ID: {})", methodName, methodEntity.getId());
                                } else {
                                    throw e; // 如果查询不到，重新抛出异常
                                }
                            }
                        }
                        
                        // 尝试从 ZooKeeper 元数据中获取方法参数信息（仅对新插入的方法或需要更新参数的方法）
                        if (methodEntity.getId() != null) {
                            log.info("开始解析方法参数: methodId={}, methodName={}, interface={}", 
                                    methodEntity.getId(), methodName, providerInfo.getInterfaceName());
                            
                            List<DubboMethodParameterEntity> methodParameters = parseMethodParametersFromMetadata(
                                methodEntity.getId(), providerInfo.getInterfaceName(), version, methodName, providerInfo);
                            
                            // 保存参数信息（如果有参数则保存，否则跳过）
                            if (!methodParameters.isEmpty()) {
                                saveMethodParameters(methodEntity.getId(), providerInfo.getInterfaceName(), version, methodParameters);
                                log.info("✅ 成功保存方法 {} 的 {} 个参数到数据库", methodName, methodParameters.size());
                            } else {
                                log.warn("⚠️ 方法 {} 没有参数信息，跳过参数保存。请检查：1) Provider是否配置了metadata-report 2) ZooKeeper中是否存在元数据路径", methodName);
                            }
                        } else {
                            log.warn("⚠️ 方法ID为null，无法保存参数信息: methodName={}", methodName);
                        }
                    }
                }
            }
            
            // 可选：删除已不存在的方法（为了保护用户数据，这里暂不删除，仅记录）
            for (DubboServiceMethodEntity existingMethod : existingMethods) {
                if (!currentMethodNames.contains(existingMethod.getMethodName())) {
                    log.info("检测到已不存在的方法: {} (ID: {})，为保护用户数据暂不删除", 
                             existingMethod.getMethodName(), existingMethod.getId());
                    // 如果需要真正删除已不存在的方法，取消下面的注释
                    // dubboMethodParameterMapper.deleteByMethodId(existingMethod.getId());
                    // dubboServiceMethodMapper.deleteById(existingMethod.getId());
                }
            }
            
            log.info("成功保存或更新Dubbo服务方法信息: serviceId={}", serviceId);
        } catch (Exception e) {
            log.error("保存或更新Dubbo服务方法信息到数据库失败: serviceId={}", serviceId, e);
            throw new RuntimeException("保存或更新Dubbo服务方法信息失败", e);
        }
    }
    
    /**
     * 根据服务ID删除方法信息
     * 
     * @param serviceId 服务ID
     */
    @Transactional
    public void deleteMethodsByServiceId(Long serviceId) {
        try {
            // 查找所有关联的方法
            List<DubboServiceMethodEntity> methods = dubboServiceMethodMapper.findByServiceId(serviceId);
            
            // 删除所有关联的参数和方法
            for (DubboServiceMethodEntity method : methods) {
                dubboMethodParameterMapper.deleteByMethodId(method.getId());
                dubboServiceMethodMapper.deleteById(method.getId());
            }
            
            log.debug("成功删除Dubbo服务方法信息: serviceId={}", serviceId);
        } catch (Exception e) {
            log.error("删除Dubbo服务方法信息失败: serviceId={}", serviceId, e);
            throw new RuntimeException("删除Dubbo服务方法信息失败", e);
        }
    }
    
    /**
     * 根据服务ID查找所有方法
     * 
     * @param serviceId 服务ID
     * @return Dubbo服务方法信息列表
     */
    public List<DubboServiceMethodEntity> findMethodsByServiceId(Long serviceId) {
        return dubboServiceMethodMapper.findByServiceId(serviceId);
    }
    
    /**
     * 根据服务ID和方法名查找方法
     * 
     * @param serviceId 服务ID
     * @param methodName 方法名
     * @return Dubbo服务方法信息，如果未找到则返回 null
     */
    public DubboServiceMethodEntity findByServiceIdAndMethodName(Long serviceId, String methodName) {
        return dubboServiceMethodMapper.findByServiceIdAndMethodName(serviceId, methodName);
    }
    
    /**
     * 根据方法ID查找方法实体
     * 
     * @param methodId 方法ID
     * @return 方法实体，如果未找到则返回 null
     */
    public DubboServiceMethodEntity findMethodById(Long methodId) {
        return dubboServiceMethodMapper.findById(methodId);
    }
    
    /**
     * 根据方法ID查找参数列表
     * 
     * @param methodId 方法ID
     * @return 参数列表
     */
    public List<DubboMethodParameterEntity> findParametersByMethodId(Long methodId) {
        return dubboMethodParameterMapper.findByMethodId(methodId);
    }
    
    /**
     * 保存方法参数信息
     * 
     * @param methodId 方法ID
     * @param interfaceName 接口名
     * @param version 版本（从 zk_dubbo_service 获取）
     * @param parameters 参数列表
     */
    @Transactional
    public void saveMethodParameters(Long methodId, String interfaceName, String version, List<DubboMethodParameterEntity> parameters) {
        try {
            if (parameters == null || parameters.isEmpty()) {
                log.debug("参数列表为空，跳过保存: methodId={}", methodId);
                return;
            }
            
            LocalDateTime now = LocalDateTime.now();
            for (DubboMethodParameterEntity parameter : parameters) {
                parameter.setMethodId(methodId);
                parameter.setInterfaceName(interfaceName);
                parameter.setVersion(version);
                // 如果时间字段为null，设置为当前时间
                if (parameter.getCreatedAt() == null) {
                    parameter.setCreatedAt(now);
                }
                if (parameter.getUpdatedAt() == null) {
                    parameter.setUpdatedAt(now);
                }
                dubboMethodParameterMapper.insert(parameter);
                log.debug("保存Dubbo方法参数信息: methodId={}, parameterName={}, parameterType={} (ID: {})", 
                        methodId, parameter.getParameterName(), parameter.getParameterType(), parameter.getId());
            }
        } catch (Exception e) {
            log.error("保存Dubbo方法参数信息失败: methodId={}", methodId, e);
            throw new RuntimeException("保存Dubbo方法参数信息失败", e);
        }
    }
    
    /**
     * 从 ZooKeeper 元数据中解析方法参数信息
     * 
     * 元数据路径格式：/dubbo/metadata/{接口全限定名}/provider/{应用名}
     * 
     * @param methodId 方法ID
     * @param interfaceName 接口名
     * @param version 版本
     * @param methodName 方法名
     * @param providerInfo Provider信息
     * @return 参数列表
     */
    private List<DubboMethodParameterEntity> parseMethodParametersFromMetadata(Long methodId, String interfaceName, 
                                                                                String version, String methodName, 
                                                                                ProviderInfo providerInfo) {
        List<DubboMethodParameterEntity> parameters = new ArrayList<>();
        
        try {
            // 1. 优先从 ZooKeeper 元数据路径读取
            if (zooKeeperService != null) {
                CuratorFramework client = zooKeeperService.getClient();
                if (client != null) {
                    String application = providerInfo.getApplication();
                    
                    log.info("开始从ZooKeeper读取方法参数元数据: interface={}, method={}, application={}", 
                            interfaceName, methodName, application);
                    
                    // 尝试多个元数据路径格式
                    List<String> metadataPaths = new ArrayList<>();
                    
                    // 格式1: /dubbo/metadata/{接口全限定名}/provider/{应用名} (最标准格式)
                    if (application != null && !application.isEmpty()) {
                        metadataPaths.add("/dubbo/metadata/" + interfaceName + "/provider/" + application);
                    }
                    
                    // 格式2: /dubbo/metadata/{接口全限定名}/provider (如果没有应用名，尝试直接读取 provider 目录下的所有节点)
                    metadataPaths.add("/dubbo/metadata/" + interfaceName + "/provider");
                    
                    // 格式3: /dubbo/metadata/{接口全限定名} (尝试读取接口级别的元数据)
                    metadataPaths.add("/dubbo/metadata/" + interfaceName);
                    
                    // 格式4: 尝试读取 /dubbo/metadata 下的所有接口，然后匹配
                    // 这个格式通常用于 Dubbo 3.x 的元数据中心
                    String metadataBasePath = "/dubbo/metadata";
                    try {
                        if (client.checkExists().forPath(metadataBasePath) != null) {
                            List<String> interfaces = client.getChildren().forPath(metadataBasePath);
                            if (interfaces != null) {
                                for (String iface : interfaces) {
                                    // 检查是否是目标接口（可能包含版本号等后缀）
                                    if (iface.startsWith(interfaceName) || interfaceName.equals(iface)) {
                                        String providerPath = metadataBasePath + "/" + iface + "/provider";
                                        if (client.checkExists().forPath(providerPath) != null) {
                                            if (application != null && !application.isEmpty()) {
                                                metadataPaths.add(providerPath + "/" + application);
                                            }
                                            metadataPaths.add(providerPath);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("遍历元数据基础路径失败: {}", e.getMessage());
                    }
                    
                    log.debug("将尝试以下元数据路径: {}", metadataPaths);
                    
                    // 依次尝试每个路径
                    for (String metadataPath : metadataPaths) {
                        try {
                            log.debug("尝试读取元数据路径: {}", metadataPath);
                            
                            // 检查路径是否存在
                            if (client.checkExists().forPath(metadataPath) != null) {
                                // 如果是目录，尝试读取目录下的所有节点
                                if (metadataPath.endsWith("/provider") || metadataPath.endsWith("/provider/")) {
                                    // 这是一个目录，尝试读取目录下的所有子节点
                                    List<String> children = client.getChildren().forPath(metadataPath);
                                    if (children != null && !children.isEmpty()) {
                                        log.debug("发现 {} 个子节点在路径: {}", children.size(), metadataPath);
                                        // 尝试读取每个子节点的数据
                                        for (String child : children) {
                                            String childPath = metadataPath + "/" + child;
                                            try {
                                                byte[] data = client.getData().forPath(childPath);
                                                if (data != null && data.length > 0) {
                                                    String metadataContent = new String(data, StandardCharsets.UTF_8);
                                                    log.info("从元数据路径读取到数据: {}, 长度: {} 字节", childPath, metadataContent.length());
                                                    
                                                    // 检查是否是有效的 JSON
                                                    if (isValidJson(metadataContent)) {
                                                        // 解析 JSON 元数据
                                                        parameters = parseMetadataJson(methodId, interfaceName, version, methodName, metadataContent);
                                                        
                                                        if (!parameters.isEmpty()) {
                                                            log.info("✅ 从元数据中解析到方法 {} 的 {} 个参数: path={}", 
                                                                    methodName, parameters.size(), childPath);
                                                            return parameters;
                                                        } else {
                                                            log.debug("元数据中未找到方法 {} 的参数信息: path={}", methodName, childPath);
                                                        }
                                                    } else {
                                                        log.warn("元数据路径 {} 的内容不是有效的 JSON，前100字符: {}", childPath, 
                                                                metadataContent.length() > 100 ? metadataContent.substring(0, 100) + "..." : metadataContent);
                                                    }
                                                }
                                            } catch (Exception e) {
                                                log.warn("读取元数据子节点失败: path={}, error={}", childPath, e.getMessage());
                                            }
                                        }
                                    } else {
                                        log.debug("元数据路径 {} 下没有子节点", metadataPath);
                                    }
                                } else {
                                    // 这是一个文件，直接读取
                                    byte[] data = client.getData().forPath(metadataPath);
                                    if (data != null && data.length > 0) {
                                        String metadataContent = new String(data, StandardCharsets.UTF_8);
                                        log.info("从元数据路径读取到数据: {}, 长度: {} 字节", metadataPath, metadataContent.length());
                                        
                                        // 检查是否是有效的 JSON
                                        if (isValidJson(metadataContent)) {
                                            // 解析 JSON 元数据
                                            parameters = parseMetadataJson(methodId, interfaceName, version, methodName, metadataContent);
                                            
                                            if (!parameters.isEmpty()) {
                                                log.info("✅ 从元数据中解析到方法 {} 的 {} 个参数: path={}", 
                                                        methodName, parameters.size(), metadataPath);
                                                return parameters;
                                            } else {
                                                log.debug("元数据中未找到方法 {} 的参数信息: path={}", methodName, metadataPath);
                                            }
                                        } else {
                                            log.warn("元数据路径 {} 的内容不是有效的 JSON，前100字符: {}", metadataPath, 
                                                    metadataContent.length() > 100 ? metadataContent.substring(0, 100) + "..." : metadataContent);
                                        }
                                    } else {
                                        log.debug("元数据路径 {} 的数据为空", metadataPath);
                                    }
                                }
                            } else {
                                log.debug("元数据路径不存在: {}", metadataPath);
                            }
                        } catch (Exception e) {
                            log.warn("读取元数据失败: path={}, error={}", metadataPath, e.getMessage());
                            // 继续尝试下一个路径
                        }
                    }
                    
                    if (parameters.isEmpty()) {
                        log.warn("⚠️ 所有元数据路径都无法获取方法 {} 的参数信息，接口: {}, 应用: {}", 
                                methodName, interfaceName, application);
                    }
                } else {
                    log.warn("ZooKeeper 客户端未初始化，无法读取元数据");
                }
            } else {
                log.warn("ZooKeeperService 未注入，无法读取元数据");
            }
            
            // 2. 如果元数据读取失败，尝试通过反射获取（如果接口类在 classpath 中）
            if (parameters.isEmpty()) {
                try {
                    log.debug("尝试通过反射获取方法参数: interface={}, method={}", interfaceName, methodName);
                    Class<?> serviceClass = Class.forName(interfaceName);
                    java.lang.reflect.Method[] methods = serviceClass.getMethods();
                    
                    for (java.lang.reflect.Method method : methods) {
                        if (method.getName().equals(methodName)) {
                            Class<?>[] paramTypes = method.getParameterTypes();
                            java.lang.reflect.Parameter[] reflectParams = method.getParameters();
                            
                            log.debug("通过反射找到方法: {}, 参数数量: {}", methodName, paramTypes.length);
                            
                            for (int i = 0; i < paramTypes.length; i++) {
                                DubboMethodParameterEntity parameter = new DubboMethodParameterEntity();
                                parameter.setMethodId(methodId);
                                parameter.setInterfaceName(interfaceName);
                                parameter.setVersion(version);
                                
                                // 获取参数类型（完整类名）
                                String paramType = paramTypes[i].getName();
                                parameter.setParameterType(paramType);
                                parameter.setParameterOrder(i);
                                
                                // 尝试获取参数名（如果编译时保留了参数名信息）
                                String paramName = null;
                                if (i < reflectParams.length) {
                                    java.lang.reflect.Parameter reflectParam = reflectParams[i];
                                    // 检查参数是否有名称（需要编译时使用 -parameters 选项）
                                    if (reflectParam.isNamePresent()) {
                                        paramName = reflectParam.getName();
                                    } else {
                                        // 如果没有参数名，尝试从类型推断
                                        paramName = inferParameterNameFromType(paramType, i);
                                    }
                                } else {
                                    // 如果反射参数数组长度不匹配，使用类型推断
                                    paramName = inferParameterNameFromType(paramType, i);
                                }
                                
                                parameter.setParameterName(paramName);
                                parameter.setParameterDescription(null);
                                
                                parameters.add(parameter);
                                log.debug("反射获取参数: order={}, name={}, type={}", i, paramName, paramType);
                            }
                            
                            if (!parameters.isEmpty()) {
                                log.info("✅ 通过反射获取到方法 {} 的 {} 个参数", methodName, parameters.size());
                            }
                            break;
                        }
                    }
                    
                    if (parameters.isEmpty()) {
                        log.debug("通过反射未找到方法: interface={}, method={}", interfaceName, methodName);
                    }
                } catch (ClassNotFoundException e) {
                    log.debug("接口类 {} 不在 classpath 中，无法通过反射获取参数信息", interfaceName);
                } catch (Exception e) {
                    log.warn("通过反射获取方法参数失败: interfaceName={}, methodName={}, error={}", 
                            interfaceName, methodName, e.getMessage());
                }
            }
            
            // 3. 如果反射也失败，尝试从数据库中查询已存在的参数信息（可能之前已经保存过）
            if (parameters.isEmpty()) {
                try {
                    // 查找服务ID
                    com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity serviceEntity = 
                        dubboServiceMapper.findByInterfaceName(interfaceName).stream()
                            .filter(s -> version == null || version.equals(s.getVersion()))
                            .findFirst()
                            .orElse(null);
                    
                    if (serviceEntity != null) {
                        // 查找方法
                        com.pajk.mcpmetainfo.persistence.entity.DubboServiceMethodEntity methodEntity = 
                            dubboServiceMethodMapper.findByServiceIdAndMethodName(serviceEntity.getId(), methodName);
                        
                        if (methodEntity != null && methodEntity.getId() != null) {
                            // 查询已存在的参数信息
                            List<DubboMethodParameterEntity> existingParams = 
                                dubboMethodParameterMapper.findByMethodId(methodEntity.getId());
                            
                            if (existingParams != null && !existingParams.isEmpty()) {
                                // 更新 methodId 为当前方法的 ID
                                for (DubboMethodParameterEntity param : existingParams) {
                                    param.setMethodId(methodId);
                                }
                                parameters = existingParams;
                                log.info("✅ 从数据库查询到方法 {} 的 {} 个已保存的参数信息", methodName, parameters.size());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("从数据库查询参数信息失败: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.warn("解析方法参数信息失败: methodName={}, error={}", methodName, e.getMessage());
        }
        
        return parameters;
    }
    
    /**
     * 解析 Dubbo 元数据 JSON，提取方法参数信息
     * 
     * Dubbo 元数据格式示例（格式1 - 标准格式）：
     * {
     *   "application": "demo-provider",
     *   "revision": "1.0.0",
     *   "methods": [
     *     {
     *       "name": "getProductById",
     *       "parameterTypes": ["java.lang.String"],
     *       "parameterNames": ["productId"],
     *       "returnType": "com.example.Product"
     *     }
     *   ]
     * }
     * 
     * Dubbo 元数据格式示例（格式2 - 简化格式）：
     * {
     *   "methods": {
     *     "getProductById": {
     *       "params": ["java.lang.String"],
     *       "return": "com.example.Product"
     *     }
     *   }
     * }
     * 
     * Dubbo 元数据格式示例（格式3 - Dubbo 3.x 格式）：
     * {
     *   "app": "demo-provider",
     *   "revision": "1.0.0",
     *   "methods": [
     *     {
     *       "name": "getProductById",
     *       "parameterTypes": ["java.lang.String"],
     *       "returnType": "com.example.Product"
     *     }
     *   ],
     *   "types": [...]
     * }
     * 
     * @param methodId 方法ID
     * @param interfaceName 接口名
     * @param version 版本
     * @param methodName 方法名
     * @param metadataJson 元数据 JSON 字符串
     * @return 参数列表
     */
    private List<DubboMethodParameterEntity> parseMetadataJson(Long methodId, String interfaceName, 
                                                                String version, String methodName, 
                                                                String metadataJson) {
        List<DubboMethodParameterEntity> parameters = new ArrayList<>();
        
        try {
            log.debug("开始解析元数据 JSON，查找方法: {}", methodName);
            JsonNode rootNode = objectMapper.readTree(metadataJson);
            
            // 格式1: 查找 methods 数组（标准格式）
            JsonNode methodsNode = rootNode.get("methods");
            if (methodsNode != null && methodsNode.isArray()) {
                log.debug("发现 methods 数组格式，包含 {} 个方法", methodsNode.size());
                for (JsonNode methodNode : methodsNode) {
                    JsonNode nameNode = methodNode.get("name");
                    if (nameNode != null && methodName.equals(nameNode.asText())) {
                        // 找到目标方法，解析参数
                        log.debug("找到目标方法: {}", methodName);
                        parameters = parseMethodNode(methodId, interfaceName, version, methodName, methodNode);
                        if (!parameters.isEmpty()) {
                            log.info("✅ 从元数据 JSON（格式1-数组）中解析到方法 {} 的 {} 个参数", methodName, parameters.size());
                            return parameters;
                        }
                    }
                }
            }
            
            // 格式2: 查找 methods 对象（简化格式）
            if (methodsNode != null && methodsNode.isObject()) {
                log.debug("发现 methods 对象格式");
                JsonNode methodNode = methodsNode.get(methodName);
                if (methodNode != null) {
                    log.debug("找到目标方法: {}", methodName);
                    parameters = parseMethodNode(methodId, interfaceName, version, methodName, methodNode);
                    if (!parameters.isEmpty()) {
                        log.info("✅ 从元数据 JSON（格式2-对象）中解析到方法 {} 的 {} 个参数", methodName, parameters.size());
                        return parameters;
                    }
                }
            }
            
            // 格式3: 直接查找方法名作为键
            JsonNode methodNode = rootNode.get(methodName);
            if (methodNode != null) {
                log.debug("发现方法名作为键的格式: {}", methodName);
                parameters = parseMethodNode(methodId, interfaceName, version, methodName, methodNode);
                if (!parameters.isEmpty()) {
                    log.info("✅ 从元数据 JSON（格式3-直接键）中解析到方法 {} 的 {} 个参数", methodName, parameters.size());
                    return parameters;
                }
            }
            
            // 格式4: 尝试查找所有可能的字段
            log.debug("尝试查找所有可能的字段...");
            if (rootNode.isObject()) {
                rootNode.fieldNames().forEachRemaining(fieldName -> {
                    if (fieldName.toLowerCase().contains("method") || fieldName.toLowerCase().contains("param")) {
                        log.debug("发现可能相关字段: {}", fieldName);
                    }
                });
            }
            
            log.warn("⚠️ 在元数据 JSON 中未找到方法 {} 的参数信息。JSON结构预览: {}", methodName, 
                    metadataJson.length() > 200 ? metadataJson.substring(0, 200) + "..." : metadataJson);
            
        } catch (Exception e) {
            log.error("❌ 解析元数据 JSON 失败: methodName={}, error={}", methodName, e.getMessage(), e);
        }
        
        return parameters;
    }
    
    /**
     * 解析方法节点，提取参数信息
     * 
     * @param methodId 方法ID
     * @param interfaceName 接口名
     * @param version 版本
     * @param methodName 方法名
     * @param methodNode 方法节点
     * @return 参数列表
     */
    private List<DubboMethodParameterEntity> parseMethodNode(Long methodId, String interfaceName, 
                                                              String version, String methodName, 
                                                              JsonNode methodNode) {
        List<DubboMethodParameterEntity> parameters = new ArrayList<>();
        
        try {
            // 尝试多种参数类型字段名
            JsonNode parameterTypesNode = null;
            String[] paramTypeFieldNames = {"parameterTypes", "params", "paramTypes", "parameters"};
            for (String fieldName : paramTypeFieldNames) {
                parameterTypesNode = methodNode.get(fieldName);
                if (parameterTypesNode != null && parameterTypesNode.isArray()) {
                    break;
                }
            }
            
            if (parameterTypesNode != null && parameterTypesNode.isArray()) {
                int paramOrder = 0;
                Iterator<JsonNode> paramTypesIterator = parameterTypesNode.elements();
                
                while (paramTypesIterator.hasNext()) {
                    JsonNode paramTypeNode = paramTypesIterator.next();
                    String paramType = paramTypeNode.isTextual() ? paramTypeNode.asText() : paramTypeNode.toString();
                    
                    DubboMethodParameterEntity parameter = new DubboMethodParameterEntity();
                    parameter.setMethodId(methodId);
                    parameter.setInterfaceName(interfaceName);
                    parameter.setVersion(version);
                    parameter.setParameterName("param" + paramOrder); // 默认参数名
                    parameter.setParameterType(paramType);
                    parameter.setParameterOrder(paramOrder++);
                    parameter.setParameterDescription(null);
                    
                    parameters.add(parameter);
                }
                
                // 尝试获取参数名（如果元数据中包含）
                String[] paramNameFieldNames = {"parameterNames", "paramNames", "names"};
                for (String fieldName : paramNameFieldNames) {
                    JsonNode parameterNamesNode = methodNode.get(fieldName);
                    if (parameterNamesNode != null && parameterNamesNode.isArray()) {
                        int i = 0;
                        Iterator<JsonNode> paramNamesIterator = parameterNamesNode.elements();
                        while (paramNamesIterator.hasNext() && i < parameters.size()) {
                            String paramName = paramNamesIterator.next().asText();
                            parameters.get(i).setParameterName(paramName);
                            i++;
                        }
                        break;
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("解析方法节点失败: methodName={}, error={}", methodName, e.getMessage());
        }
        
        return parameters;
    }
    
    /**
     * 检查字符串是否是有效的 JSON
     * 
     * @param content 待检查的内容
     * @return 如果是有效的 JSON 返回 true，否则返回 false
     */
    private boolean isValidJson(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = content.trim();
        // 简单的 JSON 格式检查：以 { 或 [ 开头
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                // 尝试解析 JSON，如果成功则认为是有效的 JSON
                objectMapper.readTree(trimmed);
                return true;
            } catch (Exception e) {
                // 解析失败，不是有效的 JSON
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * 从参数类型推断参数名
     * 
     * @param paramType 参数类型（完整类名）
     * @param index 参数索引
     * @return 推断的参数名
     */
    private String inferParameterNameFromType(String paramType, int index) {
        // 如果类型是基本类型或常见类型，使用常见的参数名
        if (paramType.equals("java.lang.String") || paramType.equals("String")) {
            return "str" + (index > 0 ? index : "");
        } else if (paramType.equals("java.lang.Long") || paramType.equals("Long") || 
                   paramType.equals("long")) {
            return "id" + (index > 0 ? index : "");
        } else if (paramType.equals("java.lang.Integer") || paramType.equals("Integer") || 
                   paramType.equals("int")) {
            return "num" + (index > 0 ? index : "");
        } else if (paramType.equals("java.lang.Boolean") || paramType.equals("Boolean") || 
                   paramType.equals("boolean")) {
            return "flag" + (index > 0 ? index : "");
        } else if (paramType.equals("java.util.List") || paramType.startsWith("java.util.List<")) {
            return "list" + (index > 0 ? index : "");
        } else if (paramType.equals("java.util.Map") || paramType.startsWith("java.util.Map<")) {
            return "map" + (index > 0 ? index : "");
        } else {
            // 对于其他类型，尝试从类名推断
            String simpleName = paramType;
            if (paramType.contains(".")) {
                simpleName = paramType.substring(paramType.lastIndexOf(".") + 1);
            }
            // 将首字母转为小写
            if (!simpleName.isEmpty()) {
                simpleName = Character.toLowerCase(simpleName.charAt(0)) + 
                            (simpleName.length() > 1 ? simpleName.substring(1) : "");
            }
            return simpleName + (index > 0 ? index : "");
        }
    }
}