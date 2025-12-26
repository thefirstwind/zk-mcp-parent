package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.persistence.mapper.DubboServiceMapper;
import com.pajk.mcpmetainfo.persistence.mapper.DubboServiceNodeMapper;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceMethodEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceNodeEntity;
import com.pajk.mcpmetainfo.persistence.mapper.DubboMethodParameterMapper;
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
    
    @Autowired(required = false)
    private com.pajk.mcpmetainfo.persistence.mapper.DubboMethodParameterMapper dubboMethodParameterMapper;
    
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
            // 从 zk_dubbo_service 获取 version
            DubboServiceEntity service = dubboServiceMapper.findById(serviceId);
            String version = service != null ? service.getVersion() : null;
            
            // 使用 serviceId + address 查询（使用索引优化，避免使用过长的 zkPath）
            DubboServiceNodeEntity existingEntity = null;
            try {
                existingEntity = dubboServiceNodeMapper.findByServiceIdAndAddress(
                    serviceId, providerInfo.getAddress());
            } catch (Exception e) {
                // 如果查询失败（可能是存在重复数据），记录警告并尝试获取第一条
                log.warn("查询节点时出现异常，可能存在重复数据: serviceId={}, address={}, error={}", 
                    serviceId, providerInfo.getAddress(), e.getMessage());
                // 尝试查询所有匹配的记录，取第一条
                List<DubboServiceNodeEntity> nodes = dubboServiceNodeMapper.findByServiceId(serviceId);
                existingEntity = nodes.stream()
                    .filter(n -> providerInfo.getAddress().equals(n.getAddress()))
                    .findFirst()
                    .orElse(null);
                if (nodes.stream().filter(n -> providerInfo.getAddress().equals(n.getAddress())).count() > 1) {
                    log.error("发现重复的节点记录: serviceId={}, address={}, 数量={}", 
                        serviceId, providerInfo.getAddress(), 
                        nodes.stream().filter(n -> providerInfo.getAddress().equals(n.getAddress())).count());
                }
            }
            
            DubboServiceNodeEntity entity;
            if (existingEntity != null) {
                // 如果存在，则更新现有记录
                entity = existingEntity;
                entity.updateFromProviderInfo(providerInfo, version);
                log.debug("更新Dubbo服务节点信息到数据库: serviceId={}, address={}, version={}", serviceId, providerInfo.getAddress(), version);
            } else {
                // 如果不存在，则创建新记录
                entity = new DubboServiceNodeEntity(providerInfo, serviceId, version);
                log.debug("保存新的Dubbo服务节点信息到数据库: serviceId={}, address={}, version={}", serviceId, providerInfo.getAddress(), version);
            }
            
            // 保存到数据库
            if (entity.getId() == null) {
                dubboServiceNodeMapper.insert(entity);
            } else {
                dubboServiceNodeMapper.update(entity);
            }
            
            log.info("成功保存Dubbo服务节点信息: serviceId={}, address={}, version={} (ID: {})", 
                    serviceId, providerInfo.getAddress(), version, entity.getId());
            
            return entity;
        } catch (Exception e) {
            log.error("保存Dubbo服务节点信息到数据库失败: serviceId={}, address={}", serviceId, providerInfo.getAddress(), e);
            throw new RuntimeException("保存Dubbo服务节点信息失败", e);
        }
    }
    
    /**
     * 更新节点最后心跳时间
     * 
     * @param serviceId 服务ID
     * @param address 节点地址
     * @param lastHeartbeatTime 最后心跳时间
     */
    public void updateLastHeartbeat(Long serviceId, String address, LocalDateTime lastHeartbeatTime) {
        try {
            dubboServiceNodeMapper.updateLastHeartbeat(serviceId, address, lastHeartbeatTime);
            log.debug("更新节点心跳时间: serviceId={}, address={}, time={}", serviceId, address, lastHeartbeatTime);
        } catch (Exception e) {
            log.error("更新节点心跳时间失败: serviceId={}, address={}", serviceId, address, e);
        }
    }
    
    /**
     * 更新节点在线状态
     * 
     * @param serviceId 服务ID
     * @param address 节点地址
     * @param isOnline 是否在线
     */
    public void updateOnlineStatus(Long serviceId, String address, Boolean isOnline) {
        try {
            dubboServiceNodeMapper.updateOnlineStatus(serviceId, address, isOnline);
            log.debug("更新节点在线状态: serviceId={}, address={}, isOnline={}", serviceId, address, isOnline);
        } catch (Exception e) {
            log.error("更新节点在线状态失败: serviceId={}, address={}", serviceId, address, e);
        }
    }
    
    /**
     * 更新节点健康状态
     * 
     * @param serviceId 服务ID
     * @param address 节点地址
     * @param isHealthy 是否健康
     */
    public void updateHealthStatus(Long serviceId, String address, Boolean isHealthy) {
        try {
            dubboServiceNodeMapper.updateHealthStatus(serviceId, address, isHealthy);
            log.debug("更新节点健康状态: serviceId={}, address={}, isHealthy={}", serviceId, address, isHealthy);
        } catch (Exception e) {
            log.error("更新节点健康状态失败: serviceId={}, address={}", serviceId, address, e);
        }
    }
    
    /**
     * 标记节点为离线
     * 
     * @param serviceId 服务ID
     * @param address 节点地址
     */
    public void markNodeOffline(Long serviceId, String address) {
        try {
            dubboServiceNodeMapper.markOffline(serviceId, address);
            log.info("标记节点为离线: serviceId={}, address={}", serviceId, address);
        } catch (Exception e) {
            log.error("标记节点为离线失败: serviceId={}, address={}", serviceId, address, e);
        }
    }
    
    /**
     * 查找在线节点
     * 
     * @return 在线节点列表
     */
    public List<DubboServiceNodeEntity> findOnlineNodes() {
        try {
            return dubboServiceNodeMapper.findOnlineNodes();
        } catch (Exception e) {
            log.error("查找在线节点失败", e);
            return java.util.Collections.emptyList();
        }
    }
    
    /**
     * 查找健康检查超时的节点
     * 
     * @param timeoutMinutes 超时分钟数
     * @return 超时节点列表
     */
    public List<DubboServiceNodeEntity> findNodesByHealthCheckTimeout(int timeoutMinutes) {
        try {
            return dubboServiceNodeMapper.findNodesByHealthCheckTimeout(timeoutMinutes);
        } catch (Exception e) {
            log.error("查找健康检查超时的节点失败: timeoutMinutes={}", timeoutMinutes, e);
            return java.util.Collections.emptyList();
        }
    }
    
    /**
     * 统计在线节点数量
     * 
     * @return 在线节点数量
     */
    public int countOnlineNodes() {
        try {
            return dubboServiceNodeMapper.countOnlineNodes();
        } catch (Exception e) {
            log.error("统计在线节点数量失败", e);
            return 0;
        }
    }
    
    /**
     * 统计健康节点数量
     * 
     * @return 健康节点数量
     */
    public int countHealthyNodes() {
        try {
            return dubboServiceNodeMapper.countHealthyNodes();
        } catch (Exception e) {
            log.error("统计健康节点数量失败", e);
            return 0;
        }
    }
    
    /**
     * 删除指定时间之前的离线节点
     * 
     * @param beforeTime 时间阈值
     * @return 删除的记录数
     */
    public int deleteOfflineNodesBefore(LocalDateTime beforeTime) {
        try {
            return dubboServiceNodeMapper.deleteOfflineNodesBefore(beforeTime);
        } catch (Exception e) {
            log.error("删除离线节点失败: beforeTime={}", beforeTime, e);
            return 0;
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
     * 根据接口名查找最大版本的Dubbo服务
     * 
     * @param interfaceName 接口全限定名
     * @return 最大版本的Dubbo服务信息实体，如果未找到则返回 null
     */
    public DubboServiceEntity findLatestVersionByInterfaceName(String interfaceName) {
        return dubboServiceMapper.findLatestVersionByInterfaceName(interfaceName);
    }
    
    /**
     * 根据接口名查找最大版本的服务ID
     * 
     * @param interfaceName 接口全限定名
     * @return 最大版本的服务ID，如果未找到则返回 null
     */
    public Long findLatestVersionServiceId(String interfaceName) {
        DubboServiceEntity service = findLatestVersionByInterfaceName(interfaceName);
        return service != null ? service.getId() : null;
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
            DubboServiceNodeEntity nodeEntity = null;
            try {
                nodeEntity = dubboServiceNodeMapper.findByServiceIdAndAddress(serviceId, address);
            } catch (Exception e) {
                log.warn("查询节点时出现异常，可能存在重复数据: serviceId={}, address={}, error={}", 
                    serviceId, address, e.getMessage());
                // 尝试查询所有匹配的记录，删除所有重复的记录
                List<DubboServiceNodeEntity> nodes = dubboServiceNodeMapper.findByServiceId(serviceId);
                List<DubboServiceNodeEntity> matchedNodes = nodes.stream()
                    .filter(n -> address.equals(n.getAddress()))
                    .collect(java.util.stream.Collectors.toList());
                if (!matchedNodes.isEmpty()) {
                    nodeEntity = matchedNodes.get(0);
                    // 删除所有重复的记录
                    for (DubboServiceNodeEntity node : matchedNodes) {
                        dubboServiceNodeMapper.deleteById(node.getId());
                    }
                    log.warn("删除重复的节点记录: serviceId={}, address={}, 数量={}", 
                        serviceId, address, matchedNodes.size());
                    // 删除后直接返回，不需要继续处理
                    return;
                }
            }
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
            DubboServiceNodeEntity entity = null;
            try {
                entity = dubboServiceNodeMapper.findByServiceIdAndAddress(serviceId, address);
            } catch (Exception e) {
                log.warn("查询节点时出现异常，可能存在重复数据: serviceId={}, address={}, error={}", 
                    serviceId, address, e.getMessage());
                // 尝试查询所有匹配的记录，取第一条
                List<DubboServiceNodeEntity> nodes = dubboServiceNodeMapper.findByServiceId(serviceId);
                entity = nodes.stream()
                    .filter(n -> address.equals(n.getAddress()))
                    .findFirst()
                    .orElse(null);
                if (nodes.stream().filter(n -> address.equals(n.getAddress())).count() > 1) {
                    log.error("发现重复的节点记录: serviceId={}, address={}, 数量={}", 
                        serviceId, address, 
                        nodes.stream().filter(n -> address.equals(n.getAddress())).count());
                }
            }
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
    
    /**
     * 根据 service_id 查询 Provider 信息（用于虚拟节点创建，优化版本）
     * 
     * @param serviceId 服务ID
     * @return Provider 信息列表
     */
    public List<ProviderInfo> getProvidersByServiceId(Long serviceId) {
        try {
            List<ProviderInfo> providers = new java.util.ArrayList<>();
            
            // 1. 查询服务信息
            DubboServiceEntity service = dubboServiceMapper.findById(serviceId);
            if (service == null) {
                log.warn("Service not found: serviceId={}", serviceId);
                return providers;
            }
            
            // 2. 查询该服务的所有节点
            List<DubboServiceNodeEntity> nodes = dubboServiceNodeMapper.findByServiceId(serviceId);
            if (nodes == null || nodes.isEmpty()) {
                log.debug("No nodes found for service: serviceId={}", serviceId);
                return providers;
            }
            
            // 3. 对每个节点，查询对应的 Provider 信息
            for (DubboServiceNodeEntity node : nodes) {
                ProviderInfo providerInfo = convertToProviderInfo(service, node);
                if (providerInfo != null && providerInfo.isOnline()) {
                    providers.add(providerInfo);
                }
            }
            
            log.debug("从 zk_dubbo_* 表查询到 {} 个在线 Provider (serviceId={})", providers.size(), serviceId);
            return providers;
            
        } catch (Exception e) {
            log.error("根据 service_id 查询 Provider 信息失败: serviceId={}", serviceId, e);
            return java.util.Collections.emptyList();
        }
    }
    
    /**
     * 从 zk_dubbo_* 表查询所有 Provider 信息（用于虚拟节点创建）
     * 
     * 注意：此方法使用新的 zk_dubbo_* 表结构，而不是老的 zk_provider* 表
     * 
     * @return Provider 信息列表
     */
    public List<ProviderInfo> getAllProvidersFromDubboTables() {
        try {
            List<ProviderInfo> providers = new java.util.ArrayList<>();
            
            // 1. 查询所有服务（使用分页查询，每次查询1000条）
            List<DubboServiceEntity> services = new java.util.ArrayList<>();
            int pageSize = 1000;
            int offset = 0;
            while (true) {
                List<DubboServiceEntity> pageServices = dubboServiceMapper.findWithPagination(offset, pageSize);
                if (pageServices == null || pageServices.isEmpty()) {
                    break;
                }
                services.addAll(pageServices);
                if (pageServices.size() < pageSize) {
                    break; // 最后一页
                }
                offset += pageSize;
            }
            
            if (services.isEmpty()) {
                return providers;
            }
            
            // 2. 对每个服务，查询其所有节点
            for (DubboServiceEntity service : services) {
                List<DubboServiceNodeEntity> nodes = dubboServiceNodeMapper.findByServiceId(service.getId());
                if (nodes == null || nodes.isEmpty()) {
                    continue;
                }
                
                // 3. 对每个节点，查询对应的 Provider 信息（在线状态、心跳等）
                for (DubboServiceNodeEntity node : nodes) {
                    ProviderInfo providerInfo = convertToProviderInfo(service, node);
                    if (providerInfo != null) {
                        providers.add(providerInfo);
                    }
                }
            }
            
            log.debug("从 zk_dubbo_* 表查询到 {} 个 Provider", providers.size());
            return providers;
            
        } catch (Exception e) {
            log.error("从 zk_dubbo_* 表查询 Provider 信息失败", e);
            return java.util.Collections.emptyList();
        }
    }
    
    /**
     * 根据 zkPath 查找 Provider 信息
     * 
     * @param zkPath ZooKeeper 路径
     * @return ProviderInfo 或 null
     */
    public ProviderInfo findProviderByZkPath(String zkPath) {
        try {
            ProviderInfo providerInfo = parseProviderInfoFromZkPath(zkPath);
            if (providerInfo == null) {
                return null;
            }
            
            // 查找服务
            DubboServiceEntity service = dubboServiceMapper.findByServiceKey(
                providerInfo.getInterfaceName(),
                providerInfo.getProtocol(),
                providerInfo.getVersion(),
                providerInfo.getGroup(),
                providerInfo.getApplication()
            );
            
            if (service == null) {
                return null;
            }
            
            // 查找节点
            DubboServiceNodeEntity node = dubboServiceNodeMapper.findByServiceIdAndAddress(
                service.getId(), providerInfo.getAddress());
            
            if (node == null) {
                return null;
            }
            
            // 转换为 ProviderInfo
            return convertToProviderInfo(service, node);
            
        } catch (Exception e) {
            log.error("根据 zkPath 查找 Provider 信息失败: zkPath={}", zkPath, e);
            return null;
        }
    }
    
    /**
     * 将 DubboServiceEntity 和 DubboServiceNodeEntity 转换为 ProviderInfo
     * 
     * 注意：已废弃 zk_provider_info、zk_provider_method、zk_provider_parameter 表
     * 现在直接从 zk_dubbo_service_node 获取心跳和状态信息，从 zk_dubbo_service_method 查询方法信息
     * 
     * @param service 服务实体
     * @param node 节点实体
     * @return ProviderInfo
     */
    public ProviderInfo convertToProviderInfo(DubboServiceEntity service, DubboServiceNodeEntity node) {
        try {
            ProviderInfo providerInfo = new ProviderInfo();
            
            // 从服务实体获取基本信息
            providerInfo.setInterfaceName(service.getInterfaceName());
            providerInfo.setProtocol(service.getProtocol());
            providerInfo.setVersion(service.getVersion());
            providerInfo.setGroup(service.getGroup());
            providerInfo.setApplication(service.getApplication());
            
            // 从节点实体获取地址和状态信息（已迁移到 zk_dubbo_service_node）
            providerInfo.setAddress(node.getAddress());
            providerInfo.setRegisterTime(node.getRegistrationTime() != null ? node.getRegistrationTime() : node.getCreatedAt());
            providerInfo.setRegistrationTime(node.getRegistrationTime());
            providerInfo.setLastHeartbeat(node.getLastHeartbeatTime());
            providerInfo.setOnline(node.getIsOnline() != null ? node.getIsOnline() : true);
            providerInfo.setHealthy(node.getIsHealthy() != null ? node.getIsHealthy() : true);
            
            // 从 zk_dubbo_service_method 查询方法信息
            try {
                List<DubboServiceMethodEntity> methods = dubboServiceMethodService.findMethodsByServiceId(service.getId());
                if (methods != null && !methods.isEmpty()) {
                    String methodsStr = methods.stream()
                        .map(DubboServiceMethodEntity::getMethodName)
                        .collect(java.util.stream.Collectors.joining(","));
                    providerInfo.setMethods(methodsStr);
                    
                    // 构建参数映射（从 zk_dubbo_method_parameter 查询）
                    java.util.Map<String, String> paramsMap = new java.util.HashMap<>();
                    for (DubboServiceMethodEntity method : methods) {
                        if (method.getReturnType() != null && !method.getReturnType().isEmpty()) {
                            paramsMap.put(method.getMethodName() + ".return", method.getReturnType());
                        }
                        
                        // 查询方法的参数
                        List<com.pajk.mcpmetainfo.persistence.entity.DubboMethodParameterEntity> methodParams = 
                            dubboMethodParameterMapper.findByMethodId(method.getId());
                        if (methodParams != null && !methodParams.isEmpty()) {
                            for (com.pajk.mcpmetainfo.persistence.entity.DubboMethodParameterEntity param : methodParams) {
                                String paramKey = method.getMethodName() + ".param." + 
                                    (param.getParameterName() != null && !param.getParameterName().isEmpty() 
                                        ? param.getParameterName() 
                                        : String.valueOf(param.getParameterOrder()));
                                paramsMap.put(paramKey, param.getParameterType());
                            }
                        }
                    }
                    providerInfo.setParameters(paramsMap);
                }
            } catch (Exception e) {
                log.warn("查询方法信息失败: serviceId={}", service.getId(), e);
            }
            
            // 构建 zkPath（用于兼容性）
            if (providerInfo.getZkPath() == null) {
                String zkPath = buildZkPath(providerInfo);
                providerInfo.setZkPath(zkPath);
            }
            
            return providerInfo;
            
        } catch (Exception e) {
            log.error("转换 DubboServiceEntity 和 DubboServiceNodeEntity 为 ProviderInfo 失败: serviceId={}, nodeId={}", 
                service.getId(), node.getId(), e);
            return null;
        }
    }
    
    /**
     * 构建 zkPath（用于兼容性）
     */
    private String buildZkPath(ProviderInfo providerInfo) {
        StringBuilder path = new StringBuilder("/");
        path.append(providerInfo.getInterfaceName());
        if (providerInfo.getAddress() != null) {
            path.append("/").append(providerInfo.getAddress());
        }
        if (providerInfo.getProtocol() != null) {
            path.append("/").append(providerInfo.getProtocol());
        }
        if (providerInfo.getVersion() != null) {
            path.append("/").append(providerInfo.getVersion());
        }
        if (providerInfo.getGroup() != null && !providerInfo.getGroup().isEmpty()) {
            path.append("/").append(providerInfo.getGroup());
        }
        return path.toString();
    }
    
}