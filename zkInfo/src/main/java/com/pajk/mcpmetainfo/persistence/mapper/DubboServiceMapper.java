package com.pajk.mcpmetainfo.persistence.mapper;

import com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DubboServiceMapper {
    
    void insert(DubboServiceEntity dubboService);
    
    void update(DubboServiceEntity dubboService);
    
    void deleteById(@Param("id") Long id);
    
    DubboServiceEntity findById(@Param("id") Long id);
    
    DubboServiceEntity findByServiceKey(@Param("interfaceName") String interfaceName, 
                                      @Param("protocol") String protocol, 
                                      @Param("version") String version, 
                                      @Param("group") String group,
                                      @Param("application") String application);
    
    List<DubboServiceEntity> findByApprovalStatus(@Param("approvalStatus") String approvalStatus);
    
    List<DubboServiceEntity> findAll();
    
    void updateProviderCounts(@Param("id") Long id, 
                             @Param("providerCount") Integer providerCount, 
                             @Param("onlineProviderCount") Integer onlineProviderCount);
}

