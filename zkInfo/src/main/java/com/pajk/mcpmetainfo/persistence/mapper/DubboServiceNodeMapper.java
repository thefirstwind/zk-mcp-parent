package com.pajk.mcpmetainfo.persistence.mapper;

import com.pajk.mcpmetainfo.persistence.entity.DubboServiceNodeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DubboServiceNodeMapper {
    
    void insert(DubboServiceNodeEntity node);
    
    /**
     * 批量插入Dubbo服务节点信息
     * 
     * @param nodes 节点列表
     * @return 插入的记录数
     */
    int batchInsert(@Param("nodes") List<DubboServiceNodeEntity> nodes);
    
    void update(DubboServiceNodeEntity node);
    
    void deleteById(@Param("id") Long id);
    
    DubboServiceNodeEntity findById(@Param("id") Long id);
    
    DubboServiceNodeEntity findByServiceIdAndAddress(@Param("serviceId") Long serviceId, @Param("address") String address);
    
    List<DubboServiceNodeEntity> findByServiceId(@Param("serviceId") Long serviceId);
    
    List<DubboServiceNodeEntity> findAll();
    
    /**
     * 查找 version 为空的节点记录
     * 
     * @return version 为空的节点列表
     */
    List<DubboServiceNodeEntity> findNodesWithNullVersion();
    
    /**
     * 更新最后心跳时间
     */
    void updateLastHeartbeat(@Param("serviceId") Long serviceId, @Param("address") String address, @Param("lastHeartbeatTime") java.time.LocalDateTime lastHeartbeatTime);
    
    /**
     * 更新在线状态
     */
    void updateOnlineStatus(@Param("serviceId") Long serviceId, @Param("address") String address, @Param("isOnline") Boolean isOnline);
    
    /**
     * 更新健康状态
     */
    void updateHealthStatus(@Param("serviceId") Long serviceId, @Param("address") String address, @Param("isHealthy") Boolean isHealthy);
    
    /**
     * 标记节点为离线
     */
    void markOffline(@Param("serviceId") Long serviceId, @Param("address") String address);
    
    /**
     * 查找在线节点
     */
    List<DubboServiceNodeEntity> findOnlineNodes();
    
    /**
     * 查找健康检查超时的节点
     */
    List<DubboServiceNodeEntity> findNodesByHealthCheckTimeout(@Param("timeoutMinutes") int timeoutMinutes);
    
    /**
     * 统计在线节点数量
     */
    int countOnlineNodes();
    
    /**
     * 统计健康节点数量
     */
    int countHealthyNodes();
    
    /**
     * 删除指定时间之前的离线节点
     */
    int deleteOfflineNodesBefore(@Param("beforeTime") java.time.LocalDateTime beforeTime);
}

