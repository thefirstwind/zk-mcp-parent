package com.zkinfo.service;

import com.zkinfo.mapper.DubboServiceMapper;
import com.zkinfo.mapper.DubboServiceNodeMapper;
import com.zkinfo.model.DubboServiceEntity;
import com.zkinfo.model.DubboServiceMethodEntity;
import com.zkinfo.model.DubboServiceNodeEntity;
import com.zkinfo.model.ProviderInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Dubbo服务信息数据库服务类
 * 
 * 负责处理Dubbo服务信息与数据库之间的交互，包括保存、更新、查询等操作，
 * 以及服务节点的管理。
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
public class DubboServiceDbService {
    
    @Autowired
    private DubboServiceMapper dubboServiceMapper;
    
    @Autowired
    private DubboServiceNodeMapper dubboServiceNodeMapper;
    
    @Autowired
    private DubboServiceMethodService dubboServiceMethodService;
    
    /**
     * 保存或更新Dubbo服务信息到数据库
     * 
     * @param providerInfo Provider信息
     * @return 保存后的Dubbo服务信息实体
     */
    public DubboServiceEntity saveOrUpdateService(ProviderInfo providerInfo) {
        try {
            // 根据服务唯一标识查找是否存在记录
            DubboServiceEntity existingEntity = dubboServiceMapper.findByServiceKey(
                providerInfo.getInterfaceName(), 
                providerInfo.getProtocol(), 
                providerInfo.getVersion(), 
                providerInfo.getGroup(),
                providerInfo.getApplication()
            );
            
            DubboServiceEntity entity;
            if (existingEntity != null) {
                // 如果存在，则更新现有记录
                entity = existingEntity;
                log.debug("更新Dubbo服务信息到数据库: {}", providerInfo.getInterfaceName());
            } else {
                // 如果不存在，则创建新记录
                entity = new DubboServiceEntity(providerInfo);
                log.debug("保存新的Dubbo服务信息到数据库: {}", providerInfo.getInterfaceName());
            }
            
            // 保存到数据库
            if (entity.getId() == null) {
                dubboServiceMapper.insert(entity);
            } else {
                dubboServiceMapper.update(entity);
            }
            
            log.info("成功保存Dubbo服务信息: {} (ID: {})", providerInfo.getInterfaceName(), entity.getId());
            
            return entity;
        } catch (Exception e) {
            log.error("保存Dubbo服务信息到数据库失败: {}", providerInfo.getInterfaceName(), e);
            throw new RuntimeException("保存Dubbo服务信息失败", e);
        }
    }
    
    /**
     * 保存Dubbo服务节点信息到数据库
     * 
     * @param providerInfo Provider信息
     * @param serviceId 关联的服务ID
     * @return 保存后的Dubbo服务节点信息实体
     */
    public DubboServiceNodeEntity saveServiceNode(ProviderInfo providerInfo, Long serviceId) {
        try {
            // 根据ZooKeeper路径查找是否存在记录
            DubboServiceNodeEntity existingEntity = dubboServiceNodeMapper.findByZkPath(providerInfo.getZkPath());
            
            DubboServiceNodeEntity entity;
            if (existingEntity != null) {
                // 如果存在，则更新现有记录
                entity = existingEntity;
                entity.updateFromProviderInfo(providerInfo);
                log.debug("更新Dubbo服务节点信息到数据库: {}", providerInfo.getZkPath());
            } else {
                // 如果不存在，则创建新记录
                entity = new DubboServiceNodeEntity(providerInfo, serviceId);
                log.debug("保存新的Dubbo服务节点信息到数据库: {}", providerInfo.getZkPath());
            }
            
            // 保存到数据库
            if (entity.getId() == null) {
                dubboServiceNodeMapper.insert(entity);
            } else {
                dubboServiceNodeMapper.update(entity);
            }
            
            log.info("成功保存Dubbo服务节点信息: {} (ID: {})", providerInfo.getZkPath(), entity.getId());
            
            return entity;
        } catch (Exception e) {
            log.error("保存Dubbo服务节点信息到数据库失败: {}", providerInfo.getZkPath(), e);
            throw new RuntimeException("保存Dubbo服务节点信息失败", e);
        }
    }
    
    /**
     * 根据ID查找Dubbo服务
     * 
     * @param id 服务ID
     * @return Dubbo服务信息实体
     */
    public DubboServiceEntity findById(Long id) {
        return dubboServiceMapper.findById(id);
    }
    
    /**
     * 根据服务唯一标识查找Dubbo服务
     * 
     * @param providerInfo Provider信息
     * @return Dubbo服务信息实体
     */
    public Optional<DubboServiceEntity> findByServiceKey(ProviderInfo providerInfo) {
        DubboServiceEntity entity = dubboServiceMapper.findByServiceKey(
            providerInfo.getInterfaceName(), 
            providerInfo.getProtocol(), 
            providerInfo.getVersion(), 
            providerInfo.getGroup(),
            providerInfo.getApplication()
        );
        return Optional.ofNullable(entity);
    }
    
    /**
     * 查找已审批的Dubbo服务信息列表
     * 
     * @return 已审批的Dubbo服务信息列表
     */
    public List<DubboServiceEntity> findApprovedServices() {
        return dubboServiceMapper.findByApprovalStatus(DubboServiceEntity.ApprovalStatus.APPROVED.toString());
    }
    
    /**
     * 根据审批状态查找Dubbo服务信息列表
     * 
     * @param approvalStatus 审批状态
     * @return 符合条件的Dubbo服务信息列表
     */
    public List<DubboServiceEntity> findByApprovalStatus(DubboServiceEntity.ApprovalStatus approvalStatus) {
        return dubboServiceMapper.findByApprovalStatus(approvalStatus.toString());
    }
    
    /**
     * 审批Dubbo服务信息
     * 
     * @param id 服务信息ID
     * @param approver 审批人
     * @param approved 是否批准
     * @param comment 审批意见
     * @return 审批后的Dubbo服务信息实体
     */
    public DubboServiceEntity approveService(Long id, String approver, boolean approved, String comment) {
        try {
            DubboServiceEntity entity = dubboServiceMapper.findById(id);
            if (entity == null) {
                throw new IllegalArgumentException("找不到ID为 " + id + " 的Dubbo服务信息");
            }
            
            // 更新服务信息
            entity.setApprover(approver);
            entity.setApprovalTime(LocalDateTime.now());
            entity.setApprovalComment(comment);
            entity.setApprovalStatus(approved ? DubboServiceEntity.ApprovalStatus.APPROVED : DubboServiceEntity.ApprovalStatus.REJECTED);
            entity.setUpdatedAt(LocalDateTime.now());
            
            dubboServiceMapper.update(entity);
            
            log.info("成功审批Dubbo服务: {} (ID: {})", entity.getInterfaceName(), entity.getId());
            
            return entity;
        } catch (Exception e) {
            log.error("审批Dubbo服务信息失败: ID={}", id, e);
            throw new RuntimeException("审批Dubbo服务信息失败", e);
        }
    }
    
    /**
     * 保存或更新Dubbo服务及节点信息到数据库
     * 
     * @param providerInfo Provider信息
     */
    public void saveOrUpdateServiceWithNode(ProviderInfo providerInfo) {
        try {
            // 保存或更新服务信息
            DubboServiceEntity serviceEntity = saveOrUpdateService(providerInfo);
            
            // 保存或更新节点信息
            saveServiceNode(providerInfo, serviceEntity.getId());
            
            // 保存或更新服务方法信息
            dubboServiceMethodService.saveOrUpdateServiceMethods(providerInfo, serviceEntity.getId());
            
            log.debug("成功保存或更新Dubbo服务及节点信息: {}", providerInfo.getZkPath());
        } catch (Exception e) {
            log.error("保存或更新Dubbo服务及节点信息失败: {}", providerInfo.getZkPath(), e);
            throw new RuntimeException("保存或更新Dubbo服务及节点信息失败", e);
        }
    }
    
    /**
     * 根据ZooKeeper路径移除服务
     * 
     * @param zkPath ZooKeeper路径
     */
    public void removeServiceByZkPath(String zkPath) {
        try {
            // 先查找节点信息
            DubboServiceNodeEntity nodeEntity = dubboServiceNodeMapper.findByZkPath(zkPath);
            if (nodeEntity != null) {
                // 删除节点
                dubboServiceNodeMapper.deleteById(nodeEntity.getId());
                log.debug("成功删除Dubbo服务节点: {}", zkPath);
                
                // 查找关联的服务信息
                // DubboServiceEntity serviceEntity = dubboServiceMapper.findById(nodeEntity.getServiceId());
                // if (serviceEntity != null) {
                //     // 检查是否还有其他节点关联此服务
                //     List<DubboServiceNodeEntity> remainingNodes = dubboServiceNodeMapper.findByServiceId(serviceEntity.getId());
                //     if (remainingNodes.isEmpty()) {
                //         // 如果没有其他节点，也删除服务
                //         dubboServiceMapper.deleteById(serviceEntity.getId());
                //         log.debug("成功删除Dubbo服务: {}", serviceEntity.getInterfaceName());
                //     }
                // }
            }
        } catch (Exception e) {
            log.error("根据ZooKeeper路径移除服务失败: {}", zkPath, e);
            throw new RuntimeException("根据ZooKeeper路径移除服务失败", e);
        }
    }
    
    /**
     * 更新服务的Provider数量统计
     * 
     * @param serviceId 服务ID
     * @param providerCount Provider总数
     * @param onlineProviderCount 在线Provider数量
     */
    public void updateProviderCounts(Long serviceId, Integer providerCount, Integer onlineProviderCount) {
        try {
            dubboServiceMapper.updateProviderCounts(serviceId, providerCount, onlineProviderCount);
            log.debug("更新服务Provider统计信息: serviceId={}, providerCount={}, onlineProviderCount={}", 
                     serviceId, providerCount, onlineProviderCount);
        } catch (Exception e) {
            log.error("更新服务Provider统计信息失败: serviceId={}", serviceId, e);
            throw new RuntimeException("更新服务Provider统计信息失败", e);
        }
    }
    
    /**
     * 根据ZooKeeper路径查找Dubbo服务节点
     * 
     * @param zkPath ZooKeeper路径
     * @return Dubbo服务节点信息实体
     */
    public Optional<DubboServiceNodeEntity> findNodeByZkPath(String zkPath) {
        DubboServiceNodeEntity entity = dubboServiceNodeMapper.findByZkPath(zkPath);
        return Optional.ofNullable(entity);
    }
    
    /**
     * 根据服务ID查找所有节点
     * 
     * @param serviceId 服务ID
     * @return Dubbo服务节点信息列表
     */
    public List<DubboServiceNodeEntity> findNodesByServiceId(Long serviceId) {
        return dubboServiceNodeMapper.findByServiceId(serviceId);
    }
    
    /**
     * 查找所有Dubbo服务信息列表
     * 
     * @return 所有Dubbo服务信息列表
     */
    public List<DubboServiceEntity> findAll() {
        return dubboServiceMapper.findAll();
    }
    
    /**
     * 根据服务ID查找所有方法
     * 
     * @param serviceId 服务ID
     * @return Dubbo服务方法信息列表
     */
    public List<DubboServiceMethodEntity> findMethodsByServiceId(Long serviceId) {
        return dubboServiceMethodService.findMethodsByServiceId(serviceId);
    }
    
    /**
     * 删除Dubbo服务节点
     * 
     * @param nodeId 节点ID
     */
    public void deleteNodeById(Long nodeId) {
        try {
            dubboServiceNodeMapper.deleteById(nodeId);
            log.info("成功删除Dubbo服务节点: ID={}", nodeId);
        } catch (Exception e) {
            log.error("删除Dubbo服务节点失败: ID={}", nodeId, e);
            throw new RuntimeException("删除Dubbo服务节点失败", e);
        }
    }
}