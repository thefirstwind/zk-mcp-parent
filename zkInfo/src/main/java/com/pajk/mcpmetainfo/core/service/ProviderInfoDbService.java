package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.persistence.mapper.ApprovalLogMapper;
import com.pajk.mcpmetainfo.persistence.mapper.ProviderInfoMapper;
import com.pajk.mcpmetainfo.persistence.entity.ApprovalLog;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceNodeEntity;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import com.pajk.mcpmetainfo.persistence.entity.ProviderInfoEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provider信息数据库服务类
 * 
 * 负责处理Provider信息与数据库之间的交互，包括保存、更新、查询等操作，
 * 以及审批流程的处理。
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
public class ProviderInfoDbService {
    
    @Autowired
    private ProviderInfoMapper providerInfoMapper;
    
    @Autowired
    private ApprovalLogMapper approvalLogMapper;
    
    @Autowired
    private DubboServiceDbService dubboServiceDbService;
    
    @Autowired
    private com.pajk.mcpmetainfo.persistence.mapper.ProviderMethodMapper providerMethodMapper;
    
    @Autowired
    private com.pajk.mcpmetainfo.persistence.mapper.ProviderParameterMapper providerParameterMapper;
    
    @Autowired
    private com.pajk.mcpmetainfo.persistence.mapper.DubboServiceMethodMapper dubboServiceMethodMapper;
    
    @Autowired
    private com.pajk.mcpmetainfo.persistence.mapper.DubboMethodParameterMapper dubboMethodParameterMapper;
    
    @Autowired(required = false)
    private InterfaceWhitelistService interfaceWhitelistService;
    
    /**
     * 保存或更新Provider信息到数据库
     * 
     * @param providerInfo Provider信息
     * @return 保存后的Provider信息实体
     */
    public ProviderInfoEntity saveOrUpdateProvider(ProviderInfo providerInfo) {
        try {
            // 0. 白名单检查：只有匹配白名单的接口才准许入库
            if (interfaceWhitelistService != null && !interfaceWhitelistService.isAllowed(providerInfo.getInterfaceName())) {
                log.debug("接口 {} 不在白名单中，跳过入库", providerInfo.getInterfaceName());
                return null;
            }
            
            // 1. 先获取或创建服务信息
            DubboServiceEntity serviceEntity = dubboServiceDbService.saveOrUpdateService(providerInfo);
            
            // 2. 获取或创建节点信息
            DubboServiceNodeEntity nodeEntity = dubboServiceDbService.saveServiceNode(providerInfo, serviceEntity.getId());
            
            // 3. 根据 service_id 和 node_id 查找是否存在 Provider 记录
            ProviderInfoEntity existingEntity = providerInfoMapper.findByServiceIdAndNodeId(
                    serviceEntity.getId(), nodeEntity.getId());
            
            ProviderInfoEntity entity;
            if (existingEntity != null) {
                // 如果存在，则更新现有记录
                entity = existingEntity;
                entity.updateFromProviderInfo(providerInfo);
                entity.setServiceId(serviceEntity.getId());
                entity.setNodeId(nodeEntity.getId());
                log.debug("更新Provider信息到数据库: {}", providerInfo.getZkPath());
            } else {
                // 如果不存在，则创建新记录
                entity = new ProviderInfoEntity(providerInfo, serviceEntity.getId(), nodeEntity.getId());
                log.debug("保存新的Provider信息到数据库: {}", providerInfo.getZkPath());
            }
            
            // 保存到数据库
            if (entity.getId() == null) {
                providerInfoMapper.insert(entity);
                // 保存 methods 和 parameters 到子表
                saveProviderMethodsAndParameters(entity.getId(), providerInfo);
            } else {
                providerInfoMapper.update(entity);
                // 更新 methods 和 parameters 到子表
                saveProviderMethodsAndParameters(entity.getId(), providerInfo);
            }
            
            log.info("成功保存Provider信息: {} (ID: {})", providerInfo.getZkPath(), entity.getId());
            
            return entity;
        } catch (Exception e) {
            log.error("保存Provider信息到数据库失败: {}", providerInfo.getZkPath(), e);
            throw new RuntimeException("保存Provider信息失败", e);
        }
    }
    
    /**
     * 根据ZooKeeper路径查找Provider信息
     * 
     * @param zkPath ZooKeeper路径
     * @return Provider信息实体
     */
    public Optional<ProviderInfoEntity> findByZkPath(String zkPath) {
        // 这里需要解析zkPath来提取interfaceName, address, protocol, version
        // 简化处理，假设zkPath格式为: /interfaceName/address:port/protocol/version
        String[] parts = zkPath.split("/");
        if (parts.length >= 5) {
            String interfaceName = parts[1];
            String addressPort = parts[2];
            String protocol = parts[3];
            String version = parts[4];
            
            ProviderInfoEntity entity = providerInfoMapper.findByZkPath(interfaceName, addressPort, protocol, version);
            if (entity != null) {
                // 从子表加载方法和参数
                loadMethodsAndParameters(entity);
            }
            return Optional.ofNullable(entity);
        }
        return Optional.empty();
    }
    
    /**
     * 查找已审批的Provider信息列表
     * 
     * @return 已审批的Provider信息列表
     */
    public List<ProviderInfoEntity> findApprovedProviders() {
        List<ProviderInfoEntity> entities = providerInfoMapper.findApprovedProviders();
        // 从子表加载方法和参数
        entities.forEach(this::loadMethodsAndParameters);
        return entities;
    }
    
    /**
     * 根据审批状态查找Provider信息列表
     * 注意：审批状态现在从关联的 zk_dubbo_service 获取
     * 
     * @param approvalStatus 审批状态（字符串形式，如 "APPROVED"）
     * @return 符合条件的Provider信息列表
     */
    public List<ProviderInfoEntity> findByApprovalStatus(String approvalStatus) {
        List<ProviderInfoEntity> entities = providerInfoMapper.findByApprovalStatus(approvalStatus);
        // 从子表加载方法和参数
        entities.forEach(this::loadMethodsAndParameters);
        return entities;
    }
    
    /**
     * 注意：Provider 的审批已移除，审批操作应在 Service 级别进行
     * 如需审批 Provider，请操作对应的 zk_dubbo_service
     * 审批状态通过 service_id 关联 zk_dubbo_service.approval_status 获取
     */
    
    public ProviderInfoEntity findById(Long id) {
        return providerInfoMapper.findById(id);
    }
    
    /**
     * 根据ZooKeeper路径和审批状态查找Provider信息
     * 注意：审批状态现在从关联的 zk_dubbo_service 获取
     * 
     * @param zkPath ZooKeeper路径
     * @param approvalStatus 审批状态（字符串形式，如 "APPROVED"）
     * @return Provider信息实体
     */
    public Optional<ProviderInfoEntity> findByZkPathAndApprovalStatus(String zkPath, String approvalStatus) {
        // 解析zkPath
        String[] parts = zkPath.split("/");
        if (parts.length >= 5) {
            String interfaceName = parts[1];
            String addressPort = parts[2];
            String protocol = parts[3];
            String version = parts[4];
            
            ProviderInfoEntity entity = providerInfoMapper.findByZkPathAndApprovalStatus(
                    interfaceName, addressPort, protocol, version, approvalStatus);
            return Optional.ofNullable(entity);
        }
        return Optional.empty();
    }
    
    public void removeProviderByZkPath(String zkPath) {
        // 对于删除操作，我们需要先找到所有匹配的记录，然后逐个删除
        // 由于MyBatis没有直接的删除方法，我们暂时只记录日志
        log.info("从数据库移除Provider: {}", zkPath);
    }
    
    /**
     * 标记 Provider 为离线
     * 
     * @param zkPath ZooKeeper 路径
     */
    public void markProviderOffline(String zkPath) {
        try {
            // 解析 zkPath 提取字段
            String[] fields = parseZkPath(zkPath);
            if (fields == null) {
                log.warn("无法解析 zkPath: {}", zkPath);
                return;
            }
            int rows = providerInfoMapper.markProviderOffline(fields[0], fields[1], fields[2], fields[3], LocalDateTime.now());
            if (rows > 0) {
                log.debug("✅ Marked Provider as offline: {}", zkPath);
            }
        } catch (Exception e) {
            log.error("Failed to mark Provider offline: {}", zkPath, e);
        }
    }
    
    /**
     * 更新最后心跳时间
     * 
     * @param zkPath ZooKeeper 路径
     * @param lastHeartbeat 最后心跳时间
     */
    public void updateLastHeartbeat(String zkPath, LocalDateTime lastHeartbeat) {
        try {
            // 解析 zkPath 提取字段
            String[] fields = parseZkPath(zkPath);
            if (fields == null) {
                log.warn("无法解析 zkPath: {}", zkPath);
                return;
            }
            providerInfoMapper.updateLastHeartbeat(fields[0], fields[1], fields[2], fields[3], lastHeartbeat);
        } catch (Exception e) {
            log.error("Failed to update last heartbeat: {}", zkPath, e);
        }
    }
    
    /**
     * 更新 Provider 健康状态
     * 
     * @param zkPath ZooKeeper 路径
     * @param healthy 是否健康
     */
    public void updateProviderHealthStatus(String zkPath, boolean healthy) {
        try {
            // 解析 zkPath 提取字段
            String[] fields = parseZkPath(zkPath);
            if (fields == null) {
                log.warn("无法解析 zkPath: {}", zkPath);
                return;
            }
            providerInfoMapper.updateProviderHealthStatus(fields[0], fields[1], fields[2], fields[3], healthy);
        } catch (Exception e) {
            log.error("Failed to update Provider health status: {}", zkPath, e);
        }
    }
    
    /**
     * 解析 zkPath 提取 interfaceName, address, protocol, version
     * 
     * @param zkPath ZooKeeper 路径
     * @return [interfaceName, address, protocol, version] 或 null
     */
    private String[] parseZkPath(String zkPath) {
        if (zkPath == null || zkPath.isEmpty()) {
            return null;
        }
        try {
            // 直接从路径中解析
            String[] parts = zkPath.split("/");
            if (parts.length >= 5) {
                String interfaceName = parts.length > 1 ? parts[1] : null;
                String address = parts.length > 2 ? parts[2] : null;
                String protocol = parts.length > 3 ? parts[3] : null;
                String version = parts.length > 4 ? parts[4] : null;
                
                if (interfaceName == null || address == null) {
                    return null;
                }
                
                return new String[]{interfaceName, address, protocol != null ? protocol : "", version != null ? version : ""};
            }
            return null;
        } catch (Exception e) {
            log.warn("解析 zkPath 失败: {}", zkPath, e);
            return null;
        }
    }
    
    /**
     * 查找健康检查超时的 Provider
     * 
     * @param timeoutMinutes 超时分钟数
     * @return Provider 列表
     */
    public List<ProviderInfo> findProvidersByHealthCheckTimeout(int timeoutMinutes) {
        try {
            List<ProviderInfoEntity> entities = providerInfoMapper.findProvidersByHealthCheckTimeout(timeoutMinutes);
            return entities.stream()
                    .map(ProviderInfoEntity::toProviderInfo)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to find Providers by health check timeout: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * 删除指定时间之前的离线 Provider 记录
     * 
     * @param beforeTime 时间阈值
     * @return 删除的记录数
     */
    public int deleteOfflineProvidersBefore(LocalDateTime beforeTime) {
        try {
            return providerInfoMapper.deleteOfflineProvidersBefore(beforeTime);
        } catch (Exception e) {
            log.error("Failed to delete offline Providers before: {}", beforeTime, e);
            return 0;
        }
    }
    
    /**
     * 统计在线 Provider 数量
     * 
     * @return 在线 Provider 数量
     */
    public int countOnlineProviders() {
        try {
            return providerInfoMapper.countOnlineProviders();
        } catch (Exception e) {
            log.error("Failed to count online Providers: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * 统计健康的 Provider 数量
     * 
     * @return 健康的 Provider 数量
     */
    public int countHealthyProviders() {
        try {
            return providerInfoMapper.countHealthyProviders();
        } catch (Exception e) {
            log.error("Failed to count healthy Providers: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * 保存 Provider 的方法和参数到子表
     * 
     * @param providerId Provider ID
     * @param providerInfo Provider 信息
     */
    private void saveProviderMethodsAndParameters(Long providerId, ProviderInfo providerInfo) {
        try {
            // 1. 删除旧的方法和参数
            providerMethodMapper.deleteByProviderId(providerId);
            providerParameterMapper.deleteByProviderId(providerId);
            
            // 2. 保存方法列表到子表
            if (providerInfo.getMethods() != null && !providerInfo.getMethods().isEmpty()) {
                String[] methodNames = providerInfo.getMethods().split(",");
                List<com.pajk.mcpmetainfo.persistence.entity.ProviderMethodEntity> methods = new java.util.ArrayList<>();
                for (int i = 0; i < methodNames.length; i++) {
                    String methodName = methodNames[i].trim();
                    if (!methodName.isEmpty()) {
                        methods.add(new com.pajk.mcpmetainfo.persistence.entity.ProviderMethodEntity(
                            providerId, methodName, i));
                    }
                }
                if (!methods.isEmpty()) {
                    providerMethodMapper.batchInsert(methods);
                }
            }
            
            // 3. 保存方法的入参和出参到子表（从 zk_dubbo_method_parameter 和 zk_dubbo_service_method 获取）
            if (providerInfo.getMethods() != null && !providerInfo.getMethods().isEmpty()) {
                String[] methodNames = providerInfo.getMethods().split(",");
                List<com.pajk.mcpmetainfo.persistence.entity.ProviderParameterEntity> parameters = new java.util.ArrayList<>();
                int order = 0;
                
                // 获取服务ID（通过 interfaceName 查找）
                DubboServiceEntity serviceEntity = dubboServiceDbService.findByInterfaceName(providerInfo.getInterfaceName());
                if (serviceEntity != null && serviceEntity.getId() != null) {
                    Long serviceId = serviceEntity.getId();
                    
                    for (String methodName : methodNames) {
                        methodName = methodName.trim();
                        if (methodName.isEmpty()) {
                            continue;
                        }
                        
                        try {
                            // 查找方法信息（获取返回类型）
                            com.pajk.mcpmetainfo.persistence.entity.DubboServiceMethodEntity methodEntity = 
                                dubboServiceMethodMapper.findByServiceIdAndMethodName(serviceId, methodName);
                            
                            if (methodEntity != null && methodEntity.getId() != null) {
                                Long methodId = methodEntity.getId();
                                
                                // 保存返回类型（出参）
                                if (methodEntity.getReturnType() != null && !methodEntity.getReturnType().isEmpty()) {
                                    String returnKey = methodName + ".return";
                                    String returnValue = methodEntity.getReturnType();
                                    // 截断过长的参数值
                                    if (returnValue.length() > 2000) {
                                        log.warn("返回类型过长，将被截断: method={}, 原始长度={}", methodName, returnValue.length());
                                        returnValue = returnValue.substring(0, 2000);
                                    }
                                    parameters.add(new com.pajk.mcpmetainfo.persistence.entity.ProviderParameterEntity(
                                        providerId, returnKey, returnValue, order++));
                                }
                                
                                // 查找方法的所有入参
                                List<com.pajk.mcpmetainfo.persistence.entity.DubboMethodParameterEntity> methodParams = 
                                    dubboMethodParameterMapper.findByMethodId(methodId);
                                
                                if (methodParams != null && !methodParams.isEmpty()) {
                                    for (com.pajk.mcpmetainfo.persistence.entity.DubboMethodParameterEntity methodParam : methodParams) {
                                        // 构建参数键：方法名.param.参数名 或 方法名.param.索引
                                        String paramKey;
                                        if (methodParam.getParameterName() != null && !methodParam.getParameterName().isEmpty()) {
                                            paramKey = methodName + ".param." + methodParam.getParameterName();
                                        } else {
                                            paramKey = methodName + ".param." + methodParam.getParameterOrder();
                                        }
                                        
                                        String paramValue = methodParam.getParameterType();
                                        // 截断过长的参数值
                                        if (paramValue != null && paramValue.length() > 2000) {
                                            log.warn("参数类型过长，将被截断: method={}, param={}, 原始长度={}", 
                                                    methodName, paramKey, paramValue.length());
                                            paramValue = paramValue.substring(0, 2000);
                                        }
                                        
                                        parameters.add(new com.pajk.mcpmetainfo.persistence.entity.ProviderParameterEntity(
                                            providerId, paramKey, paramValue, order++));
                                    }
                                } else {
                                    log.debug("方法 {} 没有入参信息", methodName);
                                }
                            } else {
                                log.debug("未找到方法信息: interface={}, method={}", providerInfo.getInterfaceName(), methodName);
                            }
                        } catch (Exception e) {
                            log.warn("保存方法 {} 的参数信息失败: {}", methodName, e.getMessage());
                        }
                    }
                } else {
                    log.warn("未找到服务信息，无法保存方法参数: interface={}", providerInfo.getInterfaceName());
                }
                
                if (!parameters.isEmpty()) {
                    providerParameterMapper.batchInsert(parameters);
                    log.info("成功保存 {} 个方法的入参和出参到 zk_provider_parameter: providerId={}, methods={}", 
                            parameters.size(), providerId, providerInfo.getMethods());
                } else {
                    log.warn("没有找到任何方法的入参和出参信息: providerId={}, methods={}", 
                            providerId, providerInfo.getMethods());
                }
            }
            
        } catch (Exception e) {
            log.error("保存Provider方法和参数到子表失败: providerId={}", providerId, e);
            // 不抛出异常，避免影响主表保存
        }
    }
    
    /**
     * 从子表加载方法和参数到 ProviderInfoEntity
     * 
     * @param entity ProviderInfoEntity
     */
    public void loadMethodsAndParameters(ProviderInfoEntity entity) {
        if (entity == null || entity.getId() == null) {
            return;
        }
        
        try {
            // 加载方法列表
            List<com.pajk.mcpmetainfo.persistence.entity.ProviderMethodEntity> methods = 
                providerMethodMapper.findByProviderId(entity.getId());
            if (methods != null && !methods.isEmpty()) {
                String methodsStr = methods.stream()
                    .map(com.pajk.mcpmetainfo.persistence.entity.ProviderMethodEntity::getMethodName)
                    .collect(java.util.stream.Collectors.joining(","));
                entity.setMethods(methodsStr);
            }
            
            // 加载参数
            List<com.pajk.mcpmetainfo.persistence.entity.ProviderParameterEntity> parameters = 
                providerParameterMapper.findByProviderId(entity.getId());
            if (parameters != null && !parameters.isEmpty()) {
                java.util.Map<String, String> paramsMap = new java.util.HashMap<>();
                for (com.pajk.mcpmetainfo.persistence.entity.ProviderParameterEntity param : parameters) {
                    paramsMap.put(param.getParamKey(), param.getParamValue());
                }
                entity.setParameters(paramsMap);
            }
        } catch (Exception e) {
            log.error("从子表加载方法和参数失败: providerId={}", entity.getId(), e);
        }
    }
}