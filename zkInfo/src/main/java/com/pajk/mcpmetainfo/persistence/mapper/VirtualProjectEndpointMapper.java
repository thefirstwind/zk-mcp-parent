package com.pajk.mcpmetainfo.persistence.mapper;

import com.pajk.mcpmetainfo.persistence.entity.VirtualProjectEndpointEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VirtualProjectEndpointMapper {
    
    void insert(VirtualProjectEndpointEntity endpoint);
    
    void update(VirtualProjectEndpointEntity endpoint);
    
    void deleteById(@Param("id") Long id);
    
    VirtualProjectEndpointEntity findById(@Param("id") Long id);
    
    VirtualProjectEndpointEntity findByEndpointName(@Param("endpointName") String endpointName);
    
    VirtualProjectEndpointEntity findByVirtualProjectId(@Param("virtualProjectId") Long virtualProjectId);
    
    List<VirtualProjectEndpointEntity> findAll();
    
    List<VirtualProjectEndpointEntity> findByStatus(@Param("status") String status);
}



