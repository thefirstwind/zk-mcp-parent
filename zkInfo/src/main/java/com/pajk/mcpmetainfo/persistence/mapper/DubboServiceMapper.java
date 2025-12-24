package com.pajk.mcpmetainfo.persistence.mapper;

import com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DubboServiceMapper {
    
    void insert(DubboServiceEntity dubboService);
    
    /**
     * 批量插入Dubbo服务信息
     * 
     * @param services 服务列表
     * @return 插入的记录数
     */
    int batchInsert(@Param("services") List<DubboServiceEntity> services);
    
    void update(DubboServiceEntity dubboService);
    
    void deleteById(@Param("id") Long id);
    
    DubboServiceEntity findById(@Param("id") Long id);
    
    DubboServiceEntity findByServiceKey(@Param("interfaceName") String interfaceName, 
                                      @Param("protocol") String protocol, 
                                      @Param("version") String version, 
                                      @Param("group") String group,
                                      @Param("application") String application);
    
    List<DubboServiceEntity> findByApprovalStatus(@Param("approvalStatus") String approvalStatus);
    
    /**
     * 根据审批状态分页查找Dubbo服务
     * 
     * @param approvalStatus 审批状态
     * @param offset 偏移量
     * @param limit 每页大小
     * @return Dubbo服务列表
     */
    List<DubboServiceEntity> findByApprovalStatusWithPagination(@Param("approvalStatus") String approvalStatus, 
                                                                 @Param("offset") int offset, 
                                                                 @Param("limit") int limit);
    
    /**
     * 根据审批状态统计数量
     * 
     * @param approvalStatus 审批状态
     * @return 总记录数
     */
    long countByApprovalStatus(@Param("approvalStatus") String approvalStatus);
    
    /**
     * 根据接口名查找Dubbo服务列表
     * 
     * @param interfaceName 接口全限定名
     * @return Dubbo服务列表
     */
    List<DubboServiceEntity> findByInterfaceName(@Param("interfaceName") String interfaceName);
    
    /**
     * 根据接口名查找最大版本的Dubbo服务
     * 
     * @param interfaceName 接口全限定名
     * @return 最大版本的Dubbo服务，如果未找到则返回 null
     */
    DubboServiceEntity findLatestVersionByInterfaceName(@Param("interfaceName") String interfaceName);
    
    
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

