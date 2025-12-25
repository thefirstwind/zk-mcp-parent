package com.pajk.mcpmetainfo.persistence.mapper;

import com.pajk.mcpmetainfo.persistence.entity.ProviderMethodEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProviderMethodMapper {
    
    void insert(ProviderMethodEntity method);
    
    int batchInsert(@Param("methods") List<ProviderMethodEntity> methods);
    
    void deleteByProviderId(@Param("providerId") Long providerId);
    
    List<ProviderMethodEntity> findByProviderId(@Param("providerId") Long providerId);
}

