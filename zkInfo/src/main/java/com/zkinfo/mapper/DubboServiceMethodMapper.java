package com.zkinfo.mapper;

import com.zkinfo.model.DubboServiceMethodEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

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