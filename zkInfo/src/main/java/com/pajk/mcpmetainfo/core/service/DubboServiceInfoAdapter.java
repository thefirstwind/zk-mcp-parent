package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceNodeEntity;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import com.pajk.mcpmetainfo.persistence.entity.ProviderInfoEntity; // @deprecated 已废弃
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Dubbo服务信息适配器
 * 
 * 负责将ProviderInfo转换为新的DubboServiceEntity和DubboServiceNodeEntity实体，
 * 以适配新的数据库表结构。
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Component
public class DubboServiceInfoAdapter {
    
    /**
     * 将ProviderInfo转换为DubboServiceEntity
     * 
     * @param providerInfo Provider信息
     * @return Dubbo服务实体
     */
    public DubboServiceEntity convertToServiceEntity(ProviderInfo providerInfo) {
        DubboServiceEntity serviceEntity = new DubboServiceEntity();
        serviceEntity.setInterfaceName(providerInfo.getInterfaceName());
        serviceEntity.setProtocol(providerInfo.getProtocol());
        serviceEntity.setVersion(providerInfo.getVersion());
        // 如果 group 为空，使用 "default" 作为固定值
        String groupValue = providerInfo.getGroup();
        serviceEntity.setGroup((groupValue == null || groupValue.trim().isEmpty()) ? "default" : groupValue);
        serviceEntity.setApplication(providerInfo.getApplication());
        serviceEntity.setApprovalStatus(DubboServiceEntity.ApprovalStatus.INIT); // 默认为初始化状态
        serviceEntity.setCreatedAt(LocalDateTime.now());
        serviceEntity.setUpdatedAt(LocalDateTime.now());
        return serviceEntity;
    }
    
    /**
     * 将ProviderInfo转换为DubboServiceNodeEntity（已废弃，请使用带 version 参数的方法）
     * 
     * @param providerInfo Provider信息
     * @param serviceId 关联的服务ID
     * @return Dubbo服务节点实体
     * @deprecated 请使用 convertToNodeEntity(ProviderInfo, Long, String) 方法
     */
    @Deprecated
    public DubboServiceNodeEntity convertToNodeEntity(ProviderInfo providerInfo, Long serviceId) {
        return convertToNodeEntity(providerInfo, serviceId, null);
    }
    
    /**
     * 将ProviderInfo转换为DubboServiceNodeEntity
     * 
     * 注意：现在包含心跳和状态信息（已从 zk_provider_info 迁移到 zk_dubbo_service_node）
     * 
     * @param providerInfo Provider信息
     * @param serviceId 关联的服务ID
     * @param version 服务版本（从 zk_dubbo_service 获取）
     * @return Dubbo服务节点实体
     */
    public DubboServiceNodeEntity convertToNodeEntity(ProviderInfo providerInfo, Long serviceId, String version) {
        DubboServiceNodeEntity nodeEntity = new DubboServiceNodeEntity();
        nodeEntity.setServiceId(serviceId);
        nodeEntity.setInterfaceName(providerInfo.getInterfaceName());
        nodeEntity.setVersion(version);
        nodeEntity.setAddress(providerInfo.getAddress());
        
        // 设置心跳和状态信息（已从 zk_provider_info 迁移）
        nodeEntity.setRegistrationTime(providerInfo.getRegistrationTime() != null ? 
            providerInfo.getRegistrationTime() : 
            (providerInfo.getRegisterTime() != null ? providerInfo.getRegisterTime() : LocalDateTime.now()));
        nodeEntity.setLastHeartbeatTime(providerInfo.getLastHeartbeat());
        nodeEntity.setIsOnline(providerInfo.getOnline() != null ? providerInfo.getOnline() : true);
        nodeEntity.setIsHealthy(providerInfo.getHealthy() != null ? providerInfo.getHealthy() : true);
        
        nodeEntity.setLastSyncTime(LocalDateTime.now());
        nodeEntity.setCreatedAt(LocalDateTime.now());
        nodeEntity.setUpdatedAt(LocalDateTime.now());
        return nodeEntity;
    }
    
    /**
     * 将ProviderInfoEntity转换为DubboServiceEntity
     * 
     * @param providerInfoEntity Provider信息实体
     * @return Dubbo服务实体
     * @deprecated ProviderInfoEntity 已废弃，请使用 convertToServiceEntity(ProviderInfo) 方法
     */
    @Deprecated
    public DubboServiceEntity convertToServiceEntity(ProviderInfoEntity providerInfoEntity) {
        DubboServiceEntity serviceEntity = new DubboServiceEntity();
        serviceEntity.setInterfaceName(providerInfoEntity.getInterfaceName());
        serviceEntity.setProtocol(providerInfoEntity.getProtocol());
        serviceEntity.setVersion(providerInfoEntity.getVersion());
        // 如果 group 为空，使用 "default" 作为固定值
        String groupValue = providerInfoEntity.getGroup();
        serviceEntity.setGroup((groupValue == null || groupValue.trim().isEmpty()) ? "default" : groupValue);
        serviceEntity.setApplication(providerInfoEntity.getApplication());
        
        // 注意：ProviderInfoEntity 的审批字段已移除，审批状态现在通过 service_id 关联 zk_dubbo_service 获取
        // 如果需要获取审批状态，需要通过 service_id 查询对应的 DubboServiceEntity
        // 这里保持默认值
        serviceEntity.setApprovalStatus(DubboServiceEntity.ApprovalStatus.INIT);
        
        serviceEntity.setCreatedAt(providerInfoEntity.getCreatedAt());
        serviceEntity.setUpdatedAt(providerInfoEntity.getUpdatedAt());
        return serviceEntity;
    }
    
    /**
     * 将ProviderInfoEntity转换为DubboServiceNodeEntity（已废弃，请使用带 version 参数的方法）
     * 
     * @param providerInfoEntity Provider信息实体
     * @param serviceId 关联的服务ID
     * @return Dubbo服务节点实体
     * @deprecated ProviderInfoEntity 已废弃，请使用 convertToNodeEntity(ProviderInfo, Long, String) 方法
     */
    @Deprecated
    public DubboServiceNodeEntity convertToNodeEntity(ProviderInfoEntity providerInfoEntity, Long serviceId) {
        return convertToNodeEntity(providerInfoEntity, serviceId, null);
    }
    
    /**
     * 将ProviderInfoEntity转换为DubboServiceNodeEntity
     * 
     * @param providerInfoEntity Provider信息实体
     * @param serviceId 关联的服务ID
     * @param version 服务版本（从 zk_dubbo_service 获取）
     * @return Dubbo服务节点实体
     * @deprecated ProviderInfoEntity 已废弃，请使用 convertToNodeEntity(ProviderInfo, Long, String) 方法
     */
    @Deprecated
    public DubboServiceNodeEntity convertToNodeEntity(ProviderInfoEntity providerInfoEntity, Long serviceId, String version) {
        DubboServiceNodeEntity nodeEntity = new DubboServiceNodeEntity();
        nodeEntity.setServiceId(serviceId);
        nodeEntity.setInterfaceName(providerInfoEntity.getInterfaceName());
        nodeEntity.setVersion(version);
        nodeEntity.setAddress(providerInfoEntity.getAddress());
        nodeEntity.setLastSyncTime(LocalDateTime.now());
        nodeEntity.setCreatedAt(providerInfoEntity.getCreatedAt());
        nodeEntity.setUpdatedAt(providerInfoEntity.getUpdatedAt());
        return nodeEntity;
    }
}