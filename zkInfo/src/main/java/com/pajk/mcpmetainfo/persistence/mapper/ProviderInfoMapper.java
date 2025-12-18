package com.pajk.mcpmetainfo.persistence.mapper;

import com.pajk.mcpmetainfo.persistence.entity.ProviderInfoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProviderInfoMapper {
    
    void insert(ProviderInfoEntity providerInfo);
    
    void update(ProviderInfoEntity providerInfo);
    
    ProviderInfoEntity findById(@Param("id") Long id);
    
    ProviderInfoEntity findByZkPath(@Param("interfaceName") String interfaceName, 
                                   @Param("address") String address, 
                                   @Param("protocol") String protocol, 
                                   @Param("version") String version);
    
    List<ProviderInfoEntity> findApprovedProviders();
    
    List<ProviderInfoEntity> findByApprovalStatus(@Param("approvalStatus") String approvalStatus);
    
    List<ProviderInfoEntity> findAll();
    
    /**
     * 标记 Provider 为离线
     */
    int markProviderOffline(@Param("zkPath") String zkPath, @Param("offlineTime") LocalDateTime offlineTime);
    
    /**
     * 更新最后心跳时间
     */
    int updateLastHeartbeat(@Param("zkPath") String zkPath, @Param("lastHeartbeat") LocalDateTime lastHeartbeat);
    
    /**
     * 更新 Provider 健康状态
     */
    int updateProviderHealthStatus(@Param("zkPath") String zkPath, @Param("healthy") boolean healthy);
    
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

