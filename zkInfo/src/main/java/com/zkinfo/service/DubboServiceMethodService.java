package com.zkinfo.service;

import com.zkinfo.mapper.DubboServiceMethodMapper;
import com.zkinfo.mapper.DubboMethodParameterMapper;
import com.zkinfo.model.DubboServiceMethodEntity;
import com.zkinfo.model.DubboMethodParameterEntity;
import com.zkinfo.model.ProviderInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

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
            // 删除现有的方法信息
            deleteMethodsByServiceId(serviceId);
            
            // 解析并保存方法信息
            if (providerInfo.getMethods() != null && !providerInfo.getMethods().isEmpty()) {
                String[] methods = providerInfo.getMethods().split(",");
                for (int i = 0; i < methods.length; i++) {
                    String methodName = methods[i].trim();
                    if (!methodName.isEmpty()) {
                        // 创建方法实体（暂时使用Object作为返回类型，实际应该解析URL参数获取详细信息）
                        DubboServiceMethodEntity methodEntity = new DubboServiceMethodEntity(
                            serviceId, methodName, "java.lang.Object");
                        
                        // 保存方法信息
                        dubboServiceMethodMapper.insert(methodEntity);
                        log.debug("保存Dubbo服务方法信息: {} (ID: {})", methodName, methodEntity.getId());
                        
                        // 保存参数信息（暂时为空，实际应该解析URL参数获取详细信息）
                        saveMethodParameters(methodEntity.getId(), new ArrayList<>());
                    }
                }
            }
            
            log.info("成功保存Dubbo服务方法信息: serviceId={}", serviceId);
        } catch (Exception e) {
            log.error("保存Dubbo服务方法信息到数据库失败: serviceId={}", serviceId, e);
            throw new RuntimeException("保存Dubbo服务方法信息失败", e);
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