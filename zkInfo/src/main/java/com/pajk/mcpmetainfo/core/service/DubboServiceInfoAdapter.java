package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceNodeEntity;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import com.pajk.mcpmetainfo.persistence.entity.ProviderInfoEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
        serviceEntity.setGroup(providerInfo.getGroup());
        serviceEntity.setApplication(providerInfo.getApplication());
        serviceEntity.setApprovalStatus(DubboServiceEntity.ApprovalStatus.INIT); // 默认为初始化状态
        serviceEntity.setCreatedAt(LocalDateTime.now());
        serviceEntity.setUpdatedAt(LocalDateTime.now());
        return serviceEntity;
    }
    
    /**
     * 将ProviderInfo转换为DubboServiceNodeEntity
     * 
     * @param providerInfo Provider信息
     * @param serviceId 关联的服务ID
     * @return Dubbo服务节点实体
     */
    public DubboServiceNodeEntity convertToNodeEntity(ProviderInfo providerInfo, Long serviceId) {
        DubboServiceNodeEntity nodeEntity = new DubboServiceNodeEntity();
        nodeEntity.setServiceId(serviceId);
        nodeEntity.setAddress(providerInfo.getAddress());
        nodeEntity.setRegisterTime(providerInfo.getRegisterTime());
        nodeEntity.setLastHeartbeat(providerInfo.getLastHeartbeat());
        nodeEntity.setZkPath(providerInfo.getZkPath());
        nodeEntity.setCreatedAt(LocalDateTime.now());
        nodeEntity.setUpdatedAt(LocalDateTime.now());
        return nodeEntity;
    }
    
    /**
     * 将ProviderInfoEntity转换为DubboServiceEntity
     * 
     * @param providerInfoEntity Provider信息实体
     * @return Dubbo服务实体
     */
    public DubboServiceEntity convertToServiceEntity(ProviderInfoEntity providerInfoEntity) {
        DubboServiceEntity serviceEntity = new DubboServiceEntity();
        serviceEntity.setInterfaceName(providerInfoEntity.getInterfaceName());
        serviceEntity.setProtocol(providerInfoEntity.getProtocol());
        serviceEntity.setVersion(providerInfoEntity.getVersion());
        serviceEntity.setGroup(providerInfoEntity.getGroup());
        serviceEntity.setApplication(providerInfoEntity.getApplication());
        
        // 转换审批状态
        switch (providerInfoEntity.getApprovalStatus()) {
            case APPROVED:
                serviceEntity.setApprovalStatus(DubboServiceEntity.ApprovalStatus.APPROVED);
                break;
            case REJECTED:
                serviceEntity.setApprovalStatus(DubboServiceEntity.ApprovalStatus.REJECTED);
                break;
            case PENDING:
                serviceEntity.setApprovalStatus(DubboServiceEntity.ApprovalStatus.PENDING);
                break;
            case INIT:
            default:
                serviceEntity.setApprovalStatus(DubboServiceEntity.ApprovalStatus.INIT);
                break;
        }
        
        serviceEntity.setApprover(providerInfoEntity.getApprover());
        serviceEntity.setApprovalTime(providerInfoEntity.getApprovalTime());
        serviceEntity.setCreatedAt(providerInfoEntity.getCreatedAt());
        serviceEntity.setUpdatedAt(providerInfoEntity.getUpdatedAt());
        return serviceEntity;
    }
    
    /**
     * 将ProviderInfoEntity转换为DubboServiceNodeEntity
     * 
     * @param providerInfoEntity Provider信息实体
     * @param serviceId 关联的服务ID
     * @return Dubbo服务节点实体
     */
    public DubboServiceNodeEntity convertToNodeEntity(ProviderInfoEntity providerInfoEntity, Long serviceId) {
        DubboServiceNodeEntity nodeEntity = new DubboServiceNodeEntity();
        nodeEntity.setServiceId(serviceId);
        nodeEntity.setAddress(providerInfoEntity.getAddress());
        nodeEntity.setRegisterTime(providerInfoEntity.getRegisterTime());
        nodeEntity.setLastHeartbeat(providerInfoEntity.getLastHeartbeat());
        nodeEntity.setZkPath(providerInfoEntity.getZkPath());
        nodeEntity.setCreatedAt(providerInfoEntity.getCreatedAt());
        nodeEntity.setUpdatedAt(providerInfoEntity.getUpdatedAt());
        return nodeEntity;
    }
}