package com.pajk.mcpmetainfo.persistence.mapper;

import com.pajk.mcpmetainfo.persistence.entity.ProviderParameterEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Provider参数Mapper（已废弃）
 * 
 * @deprecated 已废弃，功能已迁移到 DubboMethodParameterMapper。请使用 DubboMethodParameterMapper。
 */
@Deprecated
@Mapper
public interface ProviderParameterMapper {
    
    void insert(ProviderParameterEntity parameter);
    
    int batchInsert(@Param("parameters") List<ProviderParameterEntity> parameters);
    
    void deleteByProviderId(@Param("providerId") Long providerId);
    
    List<ProviderParameterEntity> findByProviderId(@Param("providerId") Long providerId);
}


