package com.pajk.mcpmetainfo.persistence.mapper;

import com.pajk.mcpmetainfo.persistence.entity.ProviderMethodEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Provider方法Mapper（已废弃）
 * 
 * @deprecated 已废弃，功能已迁移到 DubboServiceMethodMapper。请使用 DubboServiceMethodMapper。
 */
@Deprecated
@Mapper
public interface ProviderMethodMapper {
    
    void insert(ProviderMethodEntity method);
    
    int batchInsert(@Param("methods") List<ProviderMethodEntity> methods);
    
    void deleteByProviderId(@Param("providerId") Long providerId);
    
    List<ProviderMethodEntity> findByProviderId(@Param("providerId") Long providerId);
}


