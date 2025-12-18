package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.persistence.mapper.DubboServiceMethodMapper;
import com.pajk.mcpmetainfo.persistence.mapper.DubboMethodParameterMapper;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceMethodEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboMethodParameterEntity;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
                        
                        // 检查方法是否已存在
                        if (existingMethodMap.containsKey(methodName)) {
                            // 方法已存在，保留原有信息，仅更新时间戳
                            DubboServiceMethodEntity existingMethod = existingMethodMap.get(methodName);
                            existingMethod.setUpdatedAt(LocalDateTime.now());
                            dubboServiceMethodMapper.update(existingMethod);
                            log.debug("更新已存在的Dubbo服务方法信息: {} (ID: {})", methodName, existingMethod.getId());
                        } else {
                            // 新方法，创建并保存
                            DubboServiceMethodEntity methodEntity = new DubboServiceMethodEntity(
                                serviceId, methodName, "java.lang.Object");
                            
                            // 保存方法信息
                            dubboServiceMethodMapper.insert(methodEntity);
                            log.debug("保存新的Dubbo服务方法信息: {} (ID: {})", methodName, methodEntity.getId());
                            
                            // 保存参数信息（暂时为空，实际应该解析URL参数获取详细信息）
                            saveMethodParameters(methodEntity.getId(), new ArrayList<>());
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
     * @param parameters 参数列表
     */
    @Transactional
    public void saveMethodParameters(Long methodId, List<DubboMethodParameterEntity> parameters) {
        try {
            for (DubboMethodParameterEntity parameter : parameters) {
                parameter.setMethodId(methodId);
                dubboMethodParameterMapper.insert(parameter);
                log.debug("保存Dubbo方法参数信息: {} (ID: {})", parameter.getParameterName(), parameter.getId());
            }
        } catch (Exception e) {
            log.error("保存Dubbo方法参数信息失败: methodId={}", methodId, e);
            throw new RuntimeException("保存Dubbo方法参数信息失败", e);
        }
    }
}