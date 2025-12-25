package com.pajk.mcpmetainfo.persistence.mapper;

import com.pajk.mcpmetainfo.persistence.entity.ProviderParameterEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProviderParameterMapper {
    
    void insert(ProviderParameterEntity parameter);
    
    int batchInsert(@Param("parameters") List<ProviderParameterEntity> parameters);
    
    void deleteByProviderId(@Param("providerId") Long providerId);
    
    List<ProviderParameterEntity> findByProviderId(@Param("providerId") Long providerId);
}

