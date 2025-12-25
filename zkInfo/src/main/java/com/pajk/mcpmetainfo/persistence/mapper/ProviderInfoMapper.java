package com.pajk.mcpmetainfo.persistence.mapper;

import com.pajk.mcpmetainfo.persistence.entity.ProviderInfoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProviderInfoMapper {
    
    void insert(ProviderInfoEntity providerInfo);
    
    /**
     * 批量插入Provider信息
     * 
     * @param providers Provider列表
     * @return 插入的记录数
     */
    int batchInsert(@Param("providers") List<ProviderInfoEntity> providers);
    
    void update(ProviderInfoEntity providerInfo);
    
    ProviderInfoEntity findById(@Param("id") Long id);
    
    /**
     * 根据 service_id 和 node_id 查找 Provider
     */
    ProviderInfoEntity findByServiceIdAndNodeId(@Param("serviceId") Long serviceId, 
                                                @Param("nodeId") Long nodeId);
    
    ProviderInfoEntity findByZkPath(@Param("interfaceName") String interfaceName, 
                                   @Param("address") String address, 
                                   @Param("protocol") String protocol, 
                                   @Param("version") String version);
    
    /**
     * 根据ZooKeeper路径和审批状态查找Provider
     * 审批状态从关联的 zk_dubbo_service 获取
     */
    ProviderInfoEntity findByZkPathAndApprovalStatus(@Param("interfaceName") String interfaceName, 
                                                     @Param("address") String address, 
                                                     @Param("protocol") String protocol, 
                                                     @Param("version") String version,
                                                     @Param("approvalStatus") String approvalStatus);
    
    List<ProviderInfoEntity> findApprovedProviders();
    
    List<ProviderInfoEntity> findByApprovalStatus(@Param("approvalStatus") String approvalStatus);
    
    List<ProviderInfoEntity> findAll();
    
    /**
     * 标记 Provider 为离线
     * 注意：zk_path 字段已移除，使用 interface_name + address + protocol + version 来定位
     */
    int markProviderOffline(@Param("interfaceName") String interfaceName, 
                           @Param("address") String address, 
                           @Param("protocol") String protocol, 
                           @Param("version") String version, 
                           @Param("offlineTime") LocalDateTime offlineTime);
    
    /**
     * 更新最后心跳时间
     * 注意：zk_path 字段已移除，使用 interface_name + address + protocol + version 来定位
     */
    int updateLastHeartbeat(@Param("interfaceName") String interfaceName, 
                           @Param("address") String address, 
                           @Param("protocol") String protocol, 
                           @Param("version") String version, 
                           @Param("lastHeartbeat") LocalDateTime lastHeartbeat);
    
    /**
     * 更新 Provider 健康状态
     * 注意：zk_path 字段已移除，使用 interface_name + address + protocol + version 来定位
     */
    int updateProviderHealthStatus(@Param("interfaceName") String interfaceName, 
                                   @Param("address") String address, 
                                   @Param("protocol") String protocol, 
                                   @Param("version") String version, 
                                   @Param("healthy") boolean healthy);
    
    /**
     * 查找健康检查超时的 Provider（超过指定分钟数未更新心跳）
     */
    List<ProviderInfoEntity> findProvidersByHealthCheckTimeout(@Param("timeoutMinutes") int timeoutMinutes);
    
    /**
     * 删除指定时间之前的离线 Provider 记录
     */
    int deleteOfflineProvidersBefore(@Param("beforeTime") LocalDateTime beforeTime);
    
    /**
     * 统计在线 Provider 数量
     */
    int countOnlineProviders();
    
    /**
     * 统计健康的 Provider 数量
     */
    int countHealthyProviders();
}

