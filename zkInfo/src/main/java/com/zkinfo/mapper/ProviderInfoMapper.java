package com.zkinfo.mapper;

import com.zkinfo.model.ProviderInfoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProviderInfoMapper {
    
    void insert(ProviderInfoEntity providerInfo);
    
    void update(ProviderInfoEntity providerInfo);
    
    ProviderInfoEntity findById(@Param("id") Long id);
    
    ProviderInfoEntity findByZkPath(@Param("interfaceName") String interfaceName, 
                                   @Param("address") String address, 
                                   @Param("protocol") String protocol, 
                                   @Param("version") String version);
    
    List<ProviderInfoEntity> findApprovedProviders();
    
    List<ProviderInfoEntity> findByApprovalStatus(@Param("approvalStatus") String approvalStatus);
    
    List<ProviderInfoEntity> findAll();
}