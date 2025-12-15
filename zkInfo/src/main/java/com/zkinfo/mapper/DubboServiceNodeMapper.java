package com.zkinfo.mapper;

import com.zkinfo.model.DubboServiceNodeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface DubboServiceNodeMapper {
    
    void insert(DubboServiceNodeEntity node);
    
    void update(DubboServiceNodeEntity node);
    
    void deleteById(@Param("id") Long id);
    
    DubboServiceNodeEntity findById(@Param("id") Long id);
    
    DubboServiceNodeEntity findByZkPath(@Param("zkPath") String zkPath);
    
    List<DubboServiceNodeEntity> findByServiceId(@Param("serviceId") Long serviceId);
    
    List<DubboServiceNodeEntity> findAll();
}