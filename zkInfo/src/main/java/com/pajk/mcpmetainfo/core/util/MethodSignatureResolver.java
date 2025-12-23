package com.pajk.mcpmetainfo.core.util;

import com.pajk.mcpmetainfo.persistence.entity.DubboMethodParameterEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceMethodEntity;
import com.pajk.mcpmetainfo.core.service.DubboServiceDbService;
import com.pajk.mcpmetainfo.core.service.DubboServiceMethodService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 方法签名解析器
 * 
 * 负责从数据库获取方法签名信息，并缓存以提高性能。
 * 支持从 DubboServiceMethodEntity 和 DubboMethodParameterEntity 获取详细的参数类型信息。
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-12-17
 */
@Slf4j
@Component
public class MethodSignatureResolver {
    
    @Autowired(required = false)
    private DubboServiceMethodService dubboServiceMethodService;
    
    @Autowired(required = false)
    private DubboServiceDbService dubboServiceDbService;
    
    // 方法签名缓存：interfaceName.methodName -> MethodSignature
    private final Map<String, MethodSignature> signatureCache = new ConcurrentHashMap<>();
    
    /**
     * 方法签名信息
     */
    @Data
    public static class MethodSignature {
        private String interfaceName;
        private String methodName;
        private String returnType;
        private List<ParameterInfo> parameters;
        
        public MethodSignature() {
            this.parameters = new ArrayList<>();
        }
        
        public MethodSignature(String interfaceName, String methodName, String returnType) {
            this.interfaceName = interfaceName;
            this.methodName = methodName;
            this.returnType = returnType;
            this.parameters = new ArrayList<>();
        }
    }
    
    /**
     * 参数信息
     */
    @Data
    public static class ParameterInfo {
        private String name;
        private String type;
        private int order;
        private String description;
        
        public ParameterInfo(String name, String type, int order) {
            this.name = name;
            this.type = type;
            this.order = order;
        }
    }
    
    /**
     * 获取方法签名
     * 
     * @param interfaceName 接口全限定名
     * @param methodName 方法名
     * @return 方法签名信息，如果未找到则返回 null
     */
    public MethodSignature getMethodSignature(String interfaceName, String methodName) {
        String cacheKey = interfaceName + "." + methodName;
        
        // 先从缓存获取
        MethodSignature cached = signatureCache.get(cacheKey);
        if (cached != null) {
            log.debug("✅ Found method signature in cache: {}.{}", interfaceName, methodName);
            return cached;
        }
        
        // 从数据库获取
        MethodSignature signature = loadMethodSignatureFromDatabase(interfaceName, methodName);
        
        if (signature != null) {
            // 缓存结果
            signatureCache.put(cacheKey, signature);
            log.debug("✅ Loaded method signature from database: {}.{} with {} parameters", 
                    interfaceName, methodName, signature.getParameters().size());
        } else {
            log.debug("⚠️ Method signature not found in database: {}.{}", interfaceName, methodName);
        }
        
        return signature;
    }
    
    /**
     * 从数据库加载方法签名
     */
    private MethodSignature loadMethodSignatureFromDatabase(String interfaceName, String methodName) {
        if (dubboServiceMethodService == null || dubboServiceDbService == null) {
            log.debug("⚠️ DubboServiceMethodService or DubboServiceDbService is not available");
            return null;
        }
        
        try {
            // 1. 根据 interfaceName 直接查询服务（优化：避免加载所有数据）
            DubboServiceEntity matchedService = dubboServiceDbService.findByInterfaceName(interfaceName);
            
            if (matchedService == null) {
                log.debug("⚠️ Service not found in database: {}", interfaceName);
                return null;
            }
            
            Long serviceId = matchedService.getId();
            log.debug("✅ Found service in database: {} (ID: {})", interfaceName, serviceId);
            
            // 2. 根据 serviceId 和 methodName 查找方法
            DubboServiceMethodEntity method = dubboServiceMethodService.findByServiceIdAndMethodName(serviceId, methodName);
            
            if (method == null) {
                log.debug("⚠️ Method not found in database: {}.{}", interfaceName, methodName);
                return null;
            }
            
            log.debug("✅ Found method in database: {}.{} (ID: {})", interfaceName, methodName, method.getId());
            
            // 3. 根据 methodId 查找参数列表
            List<DubboMethodParameterEntity> parameters = dubboServiceMethodService.findParametersByMethodId(method.getId());
            
            // 4. 构建 MethodSignature
            MethodSignature signature = new MethodSignature(interfaceName, methodName, method.getReturnType());
            
            if (parameters != null && !parameters.isEmpty()) {
                // 按 parameterOrder 排序
                parameters.sort(Comparator.comparing(DubboMethodParameterEntity::getParameterOrder));
                
                for (DubboMethodParameterEntity param : parameters) {
                    ParameterInfo paramInfo = new ParameterInfo(
                            param.getParameterName(),
                            param.getParameterType(),
                            param.getParameterOrder()
                    );
                    paramInfo.setDescription(param.getParameterDescription());
                    signature.getParameters().add(paramInfo);
                }
            }
            
            log.debug("✅ Loaded method signature from database: {}.{} with {} parameters", 
                    interfaceName, methodName, signature.getParameters().size());
            
            return signature;
            
        } catch (Exception e) {
            log.error("❌ Failed to load method signature from database: {}.{}", interfaceName, methodName, e);
            return null;
        }
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        signatureCache.clear();
        log.debug("✅ Method signature cache cleared");
    }
    
    /**
     * 清除指定方法的缓存
     */
    public void clearCache(String interfaceName, String methodName) {
        String cacheKey = interfaceName + "." + methodName;
        signatureCache.remove(cacheKey);
        log.debug("✅ Cleared cache for: {}.{}", interfaceName, methodName);
    }
}

