package com.pajk.mcpmetainfo.persistence.mapper;

import com.pajk.mcpmetainfo.persistence.entity.DubboServiceMethodEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DubboServiceMethodMapper {
    
    void insert(DubboServiceMethodEntity method);
    
    void update(DubboServiceMethodEntity method);
    
    void deleteById(@Param("id") Long id);
    
    void deleteByServiceId(@Param("serviceId") Long serviceId);
    
    DubboServiceMethodEntity findById(@Param("id") Long id);
    
    List<DubboServiceMethodEntity> findByServiceId(@Param("serviceId") Long serviceId);
    
    DubboServiceMethodEntity findByServiceIdAndMethodName(@Param("serviceId") Long serviceId, @Param("methodName") String methodName);
    
    List<DubboServiceMethodEntity> findAll();
}


