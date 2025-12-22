package com.pajk.mcpmetainfo.persistence.mapper;

import com.pajk.mcpmetainfo.persistence.entity.DubboServiceNodeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DubboServiceNodeMapper {
    
    void insert(DubboServiceNodeEntity node);
    
    void update(DubboServiceNodeEntity node);
    
    void deleteById(@Param("id") Long id);
    
    DubboServiceNodeEntity findById(@Param("id") Long id);
    
    DubboServiceNodeEntity findByZkPath(@Param("zkPath") String zkPath);
    
    DubboServiceNodeEntity findByServiceIdAndAddress(@Param("serviceId") Long serviceId, @Param("address") String address);
    
    List<DubboServiceNodeEntity> findByServiceId(@Param("serviceId") Long serviceId);
    
    List<DubboServiceNodeEntity> findAll();
}

