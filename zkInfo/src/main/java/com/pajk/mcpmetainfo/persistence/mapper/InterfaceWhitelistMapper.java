package com.pajk.mcpmetainfo.persistence.mapper;

import com.pajk.mcpmetainfo.persistence.entity.InterfaceWhitelistEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 接口白名单 Mapper
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-12-25
 */
@Mapper
public interface InterfaceWhitelistMapper {
    
    /**
     * 插入白名单记录
     * 
     * @param entity 白名单实体
     */
    void insert(InterfaceWhitelistEntity entity);
    
    /**
     * 更新白名单记录
     * 
     * @param entity 白名单实体
     */
    void update(InterfaceWhitelistEntity entity);
    
    /**
     * 根据ID删除白名单记录
     * 
     * @param id 主键ID
     */
    void deleteById(@Param("id") Long id);
    
    /**
     * 根据ID查找白名单记录
     * 
     * @param id 主键ID
     * @return 白名单实体
     */
    InterfaceWhitelistEntity findById(@Param("id") Long id);
    
    /**
     * 查找所有启用的白名单记录
     * 
     * @return 白名单列表
     */
    List<InterfaceWhitelistEntity> findAllEnabled();
    
    /**
     * 查找所有白名单记录（包括禁用的）
     * 
     * @return 白名单列表
     */
    List<InterfaceWhitelistEntity> findAll();
    
    /**
     * 根据前缀查找白名单记录
     * 
     * @param prefix 前缀
     * @return 白名单实体
     */
    InterfaceWhitelistEntity findByPrefix(@Param("prefix") String prefix);
}



