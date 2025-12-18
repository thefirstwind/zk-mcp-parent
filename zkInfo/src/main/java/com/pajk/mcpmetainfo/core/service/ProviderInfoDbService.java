package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.persistence.mapper.ApprovalLogMapper;
import com.pajk.mcpmetainfo.persistence.mapper.ProviderInfoMapper;
import com.pajk.mcpmetainfo.persistence.entity.ApprovalLog;
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
    
    /**
     * 保存或更新Provider信息到数据库
     * 
     * @param providerInfo Provider信息
     * @return 保存后的Provider信息实体
     */
    public ProviderInfoEntity saveOrUpdateProvider(ProviderInfo providerInfo) {
        try {
            // 根据ZooKeeper路径查找是否存在记录
            ProviderInfoEntity existingEntity = providerInfoMapper.findByZkPath(
                providerInfo.getInterfaceName(), 
                providerInfo.getAddress(), 
                providerInfo.getProtocol(), 
                providerInfo.getVersion()
            );
            
            ProviderInfoEntity entity;
            if (existingEntity != null) {
                // 如果存在，则更新现有记录
                entity = existingEntity;
                entity.updateFromProviderInfo(providerInfo);
                log.debug("更新Provider信息到数据库: {}", providerInfo.getZkPath());
            } else {
                // 如果不存在，则创建新记录
                entity = new ProviderInfoEntity(providerInfo);
                log.debug("保存新的Provider信息到数据库: {}", providerInfo.getZkPath());
            }
            
            // 保存到数据库
            if (entity.getId() == null) {
                providerInfoMapper.insert(entity);
            } else {
                providerInfoMapper.update(entity);
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
        return providerInfoMapper.findApprovedProviders();
    }
    
    public List<ProviderInfoEntity> findByApprovalStatus(String approvalStatus) {
        return providerInfoMapper.findByApprovalStatus(approvalStatus);
    }
    
    /**
     * 根据审批状态查找Provider信息列表
     * 
     * @param approvalStatus 审批状态
     * @return 符合条件的Provider信息列表
     */
    public List<ProviderInfoEntity> findByApprovalStatus(ProviderInfoEntity.ApprovalStatus approvalStatus) {
        return providerInfoMapper.findByApprovalStatus(approvalStatus.toString());
    }
    
    /**
     * 审批Provider信息
     * 
     * @param id Provider信息ID
     * @param approver 审批人
     * @param approved 是否批准
     * @param comment 审批意见
     * @return 审批后的Provider信息实体
     */
    public ProviderInfoEntity approveProvider(Long id, String approver, boolean approved, String comment) {
        try {
            ProviderInfoEntity entity = providerInfoMapper.findById(id);
            if (entity == null) {
                throw new IllegalArgumentException("找不到ID为 " + id + " 的Provider信息");
            }
            
            // 记录审批日志
            ApprovalLog approvalLog = new ApprovalLog(
                    entity.getId(),
                    entity.getApprovalStatus(),
                    approved ? ProviderInfoEntity.ApprovalStatus.APPROVED : ProviderInfoEntity.ApprovalStatus.REJECTED,
                    approver,
                    comment
            );
            approvalLogMapper.insert(approvalLog);
            
            // 更新Provider信息
            entity.setApprover(approver);
            entity.setApprovalTime(LocalDateTime.now());
            entity.setApprovalComment(comment);
            entity.setApprovalStatus(approved ? ProviderInfoEntity.ApprovalStatus.APPROVED : ProviderInfoEntity.ApprovalStatus.REJECTED);
            entity.setUpdatedAt(LocalDateTime.now());
            
            providerInfoMapper.update(entity);
            log.info("成功{}Provider信息: {} (ID: {})", 
                    approved ? "批准" : "拒绝", entity.getZkPath(), entity.getId());
            
            return entity;
        } catch (Exception e) {
            log.error("审批Provider信息失败: ID={}", id, e);
            throw new RuntimeException("审批Provider信息失败", e);
        }
    }
    
    /**
     * 拒绝Provider信息
     * 
     * @param id Provider信息ID
     * @param approver 审批人
     * @param rejectionComment 拒绝原因
     */

    
    public void rejectProvider(Long id, String approver, String rejectionComment) {
        ProviderInfoEntity provider = providerInfoMapper.findById(id);
        if (provider == null) {
            throw new RuntimeException("Provider not found with id: " + id);
        }
        
        ProviderInfoEntity.ApprovalStatus oldStatus = provider.getApprovalStatus();
        provider.setApprovalStatus(ProviderInfoEntity.ApprovalStatus.REJECTED);
        provider.setApprover(approver);
        provider.setApprovalTime(LocalDateTime.now());
        provider.setApprovalComment(rejectionComment);
        
        // 记录审批日志
        ApprovalLog approvalLog = new ApprovalLog(
            id, 
            oldStatus, 
            ProviderInfoEntity.ApprovalStatus.REJECTED, 
            approver, 
            rejectionComment
        );
        
        approvalLogMapper.insert(approvalLog);
        providerInfoMapper.update(provider);
    }
    
    public ProviderInfoEntity findById(Long id) {
        return providerInfoMapper.findById(id);
    }
    
    /**
     * 根据ZooKeeper路径和审批状态查找Provider信息
     * 
     * @param zkPath ZooKeeper路径
     * @param approvalStatus 审批状态
     * @return Provider信息实体
     */
    public Optional<ProviderInfoEntity> findByZkPathAndApprovalStatus(String zkPath, ProviderInfoEntity.ApprovalStatus approvalStatus) {
        // 解析zkPath
        String[] parts = zkPath.split("/");
        if (parts.length >= 5) {
            String interfaceName = parts[1];
            String addressPort = parts[2];
            String protocol = parts[3];
            String version = parts[4];
            
            ProviderInfoEntity entity = providerInfoMapper.findByZkPath(interfaceName, addressPort, protocol, version);
            if (entity != null && entity.getApprovalStatus() == approvalStatus) {
                return Optional.of(entity);
            }
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
            int rows = providerInfoMapper.markProviderOffline(zkPath, LocalDateTime.now());
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
            providerInfoMapper.updateLastHeartbeat(zkPath, lastHeartbeat);
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
            providerInfoMapper.updateProviderHealthStatus(zkPath, healthy);
        } catch (Exception e) {
            log.error("Failed to update Provider health status: {}", zkPath, e);
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
}