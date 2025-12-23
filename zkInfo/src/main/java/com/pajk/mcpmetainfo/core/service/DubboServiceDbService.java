package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.persistence.mapper.DubboServiceMapper;
import com.pajk.mcpmetainfo.persistence.mapper.DubboServiceNodeMapper;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceMethodEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceNodeEntity;
import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import com.pajk.mcpmetainfo.core.model.PageResult;
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
            // 使用 serviceId + address 查询（使用索引优化，避免使用过长的 zkPath）
            DubboServiceNodeEntity existingEntity = dubboServiceNodeMapper.findByServiceIdAndAddress(
                serviceId, providerInfo.getAddress());
            
            DubboServiceNodeEntity entity;
            if (existingEntity != null) {
                // 如果存在，则更新现有记录
                entity = existingEntity;
                entity.updateFromProviderInfo(providerInfo);
                log.debug("更新Dubbo服务节点信息到数据库: serviceId={}, address={}", serviceId, providerInfo.getAddress());
            } else {
                // 如果不存在，则创建新记录
                entity = new DubboServiceNodeEntity(providerInfo, serviceId);
                log.debug("保存新的Dubbo服务节点信息到数据库: serviceId={}, address={}", serviceId, providerInfo.getAddress());
            }
            
            // 保存到数据库
            if (entity.getId() == null) {
                dubboServiceNodeMapper.insert(entity);
            } else {
                dubboServiceNodeMapper.update(entity);
            }
            
            log.info("成功保存Dubbo服务节点信息: serviceId={}, address={} (ID: {})", serviceId, providerInfo.getAddress(), entity.getId());
            
            return entity;
        } catch (Exception e) {
            log.error("保存Dubbo服务节点信息到数据库失败: serviceId={}, address={}", serviceId, providerInfo.getAddress(), e);
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
     * 查找已审批的Dubbo服务信息列表（限制最多1000条，避免内存溢出）
     * 
     * @return 已审批的Dubbo服务信息列表
     */
    public List<DubboServiceEntity> findApprovedServices() {
        return dubboServiceMapper.findByApprovalStatus(DubboServiceEntity.ApprovalStatus.APPROVED.toString());
    }
    
    /**
     * 根据审批状态查找Dubbo服务信息列表（限制最多1000条，避免内存溢出）
     * 
     * @param approvalStatus 审批状态
     * @return 符合条件的Dubbo服务信息列表
     */
    public List<DubboServiceEntity> findByApprovalStatus(DubboServiceEntity.ApprovalStatus approvalStatus) {
        return dubboServiceMapper.findByApprovalStatus(approvalStatus.toString());
    }
    
    /**
     * 根据审批状态分页查找Dubbo服务信息列表
     * 
     * @param approvalStatus 审批状态
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 分页结果
     */
    public PageResult<DubboServiceEntity> findByApprovalStatusWithPagination(
            DubboServiceEntity.ApprovalStatus approvalStatus, int page, int size) {
        try {
            // 参数校验
            if (page < 1) page = 1;
            if (size < 1) size = 10;
            if (size > 100) size = 100; // 限制最大页面大小
            
            // 计算偏移量
            int offset = (page - 1) * size;
            
            // 查询总数
            long total = dubboServiceMapper.countByApprovalStatus(approvalStatus.toString());
            
            // 分页查询数据
            List<DubboServiceEntity> data = dubboServiceMapper.findByApprovalStatusWithPagination(
                    approvalStatus.toString(), offset, size);
            
            return new PageResult<>(data, total, page, size);
        } catch (Exception e) {
            log.error("分页查询Dubbo服务信息失败: approvalStatus={}, page={}, size={}", approvalStatus, page, size, e);
            throw new RuntimeException("分页查询Dubbo服务信息失败", e);
        }
    }
    
    /**
     * 根据接口名查找Dubbo服务（返回第一个匹配的服务）
     * 
     * @param interfaceName 接口全限定名
     * @return Dubbo服务信息实体，如果未找到则返回 null
     */
    public DubboServiceEntity findByInterfaceName(String interfaceName) {
        List<DubboServiceEntity> services = dubboServiceMapper.findByInterfaceName(interfaceName);
        return services != null && !services.isEmpty() ? services.get(0) : null;
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
            // 从 zkPath 解析出 address 和 serviceId
            // zkPath 格式: /interfaceName/address:port/protocol/version/...
            String address = parseAddressFromZkPath(zkPath);
            if (address == null) {
                log.warn("无法从 zkPath 解析出 address: {}", zkPath);
                return;
            }
            
            // 解析出服务信息以查找 serviceId
            ProviderInfo providerInfo = parseProviderInfoFromZkPath(zkPath);
            if (providerInfo == null) {
                log.warn("无法从 zkPath 解析出服务信息: {}", zkPath);
                return;
            }
            
            // 查找服务ID
            Optional<DubboServiceEntity> serviceEntityOpt = findByServiceKey(providerInfo);
            if (!serviceEntityOpt.isPresent()) {
                log.warn("找不到对应的服务: {}", zkPath);
                return;
            }
            
            Long serviceId = serviceEntityOpt.get().getId();
            
            // 使用 serviceId + address 查询节点（使用索引优化）
            DubboServiceNodeEntity nodeEntity = dubboServiceNodeMapper.findByServiceIdAndAddress(serviceId, address);
            if (nodeEntity != null) {
                // 删除节点
                dubboServiceNodeMapper.deleteById(nodeEntity.getId());
                log.debug("成功删除Dubbo服务节点: serviceId={}, address={}", serviceId, address);
                
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
        try {
            // 从 zkPath 解析出 address 和 serviceId
            String address = parseAddressFromZkPath(zkPath);
            if (address == null) {
                log.warn("无法从 zkPath 解析出 address: {}", zkPath);
                return Optional.empty();
            }
            
            // 解析出服务信息以查找 serviceId
            ProviderInfo providerInfo = parseProviderInfoFromZkPath(zkPath);
            if (providerInfo == null) {
                log.warn("无法从 zkPath 解析出服务信息: {}", zkPath);
                return Optional.empty();
            }
            
            // 查找服务ID
            Optional<DubboServiceEntity> serviceEntityOpt = findByServiceKey(providerInfo);
            if (!serviceEntityOpt.isPresent()) {
                log.debug("找不到对应的服务: {}", zkPath);
                return Optional.empty();
            }
            
            Long serviceId = serviceEntityOpt.get().getId();
            
            // 使用 serviceId + address 查询节点（使用索引优化）
            DubboServiceNodeEntity entity = dubboServiceNodeMapper.findByServiceIdAndAddress(serviceId, address);
            return Optional.ofNullable(entity);
        } catch (Exception e) {
            log.error("根据ZooKeeper路径查找Dubbo服务节点失败: {}", zkPath, e);
            return Optional.empty();
        }
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
     * 分页查询Dubbo服务信息列表
     * 
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 分页结果
     */
    public com.pajk.mcpmetainfo.core.model.PageResult<DubboServiceEntity> findWithPagination(int page, int size) {
        try {
            // 参数校验
            if (page < 1) page = 1;
            if (size < 1) size = 10;
            if (size > 100) size = 100; // 限制最大页面大小
            
            // 计算偏移量
            int offset = (page - 1) * size;
            
            // 查询总数
            long total = dubboServiceMapper.countAll();
            
            // 分页查询数据
            List<DubboServiceEntity> data = dubboServiceMapper.findWithPagination(offset, size);
            
            return new com.pajk.mcpmetainfo.core.model.PageResult<>(data, total, page, size);
        } catch (Exception e) {
            log.error("分页查询Dubbo服务信息失败: page={}, size={}", page, size, e);
            throw new RuntimeException("分页查询Dubbo服务信息失败", e);
        }
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
    
    /**
     * 从 zkPath 解析出 address
     * zkPath 格式: /interfaceName/address:port/protocol/version/...
     * 
     * @param zkPath ZooKeeper路径
     * @return address (IP:Port) 或 null
     */
    private String parseAddressFromZkPath(String zkPath) {
        if (zkPath == null || zkPath.isEmpty()) {
            return null;
        }
        
        try {
            String[] parts = zkPath.split("/");
            if (parts.length >= 3) {
                // parts[0] 是空字符串（因为路径以 / 开头）
                // parts[1] 是 interfaceName
                // parts[2] 是 address:port
                return parts[2];
            }
        } catch (Exception e) {
            log.warn("解析 zkPath 中的 address 失败: {}", zkPath, e);
        }
        
        return null;
    }
    
    /**
     * 从 zkPath 解析出 ProviderInfo
     * zkPath 格式: /interfaceName/address:port/protocol/version/...
     * 
     * @param zkPath ZooKeeper路径
     * @return ProviderInfo 或 null
     */
    private ProviderInfo parseProviderInfoFromZkPath(String zkPath) {
        if (zkPath == null || zkPath.isEmpty()) {
            return null;
        }
        
        try {
            String[] parts = zkPath.split("/");
            if (parts.length >= 5) {
                // parts[0] 是空字符串（因为路径以 / 开头）
                // parts[1] 是 interfaceName
                // parts[2] 是 address:port
                // parts[3] 是 protocol
                // parts[4] 是 version
                
                ProviderInfo providerInfo = new ProviderInfo();
                providerInfo.setInterfaceName(parts[1]);
                providerInfo.setAddress(parts[2]);
                providerInfo.setProtocol(parts[3]);
                providerInfo.setVersion(parts[4]);
                
                // 如果路径中有更多部分，可能是 group 或其他参数
                // 这里简化处理，如果需要更完整的解析，可以进一步处理
                
                return providerInfo;
            }
        } catch (Exception e) {
            log.warn("解析 zkPath 为 ProviderInfo 失败: {}", zkPath, e);
        }
        
        return null;
    }
}