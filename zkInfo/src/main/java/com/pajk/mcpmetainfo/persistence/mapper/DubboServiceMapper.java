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
    
    /**
     * 分页查询Dubbo服务列表
     * 
     * @param offset 偏移量
     * @param limit 每页大小
     * @return Dubbo服务列表
     */
    List<DubboServiceEntity> findWithPagination(@Param("offset") int offset, @Param("limit") int limit);
    
    /**
     * 查询Dubbo服务总数
     * 
     * @return 总记录数
     */
    long countAll();
    
    void updateProviderCounts(@Param("id") Long id, 
                             @Param("providerCount") Integer providerCount, 
                             @Param("onlineProviderCount") Integer onlineProviderCount);
}

