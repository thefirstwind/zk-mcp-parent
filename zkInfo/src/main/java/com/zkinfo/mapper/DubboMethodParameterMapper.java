package com.zkinfo.mapper;

import com.zkinfo.model.DubboMethodParameterEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface DubboMethodParameterMapper {
    
    void insert(DubboMethodParameterEntity parameter);
    
    void update(DubboMethodParameterEntity parameter);
    
    void deleteById(@Param("id") Long id);
    
    void deleteByMethodId(@Param("methodId") Long methodId);
    
    DubboMethodParameterEntity findById(@Param("id") Long id);
    
    List<DubboMethodParameterEntity> findByMethodId(@Param("methodId") Long methodId);
    
    List<DubboMethodParameterEntity> findAll();
}