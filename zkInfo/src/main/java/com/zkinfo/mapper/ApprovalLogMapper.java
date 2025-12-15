package com.zkinfo.mapper;

import com.zkinfo.model.ApprovalLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ApprovalLogMapper {
    
    void insert(ApprovalLog approvalLog);
    
    List<ApprovalLog> findByProviderIdOrderByCreatedAtDesc(@Param("providerId") Long providerId);
}