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
}

