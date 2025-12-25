use mcp_bridge;

-- ============================================
-- 为 zk_provider_info 表添加 interface_name 和 address 字段
-- 注意：这些字段保留，便于快速查询和定位问题
-- ============================================

-- 1. 添加 interface_name 字段（如果不存在）
ALTER TABLE `zk_provider_info` 
    ADD COLUMN `interface_name` VARCHAR(500) NOT NULL COMMENT '服务接口名（便于快速查询和定位问题）' AFTER `node_id`;

-- 2. 添加 address 字段（如果不存在）
ALTER TABLE `zk_provider_info` 
    ADD COLUMN `address` VARCHAR(200) NOT NULL COMMENT '提供者地址 (IP:Port)（便于快速查询和定位问题）' AFTER `interface_name`;

-- 3. 添加索引（如果不存在）
ALTER TABLE `zk_provider_info` 
    ADD INDEX `idx_interface_name` (`interface_name`),
    ADD INDEX `idx_address` (`address`);

-- ============================================
-- 注意：
-- 1. 如果表中已有数据，需要先填充 interface_name 和 address 的值
-- 2. 可以通过关联查询填充：
--    UPDATE zk_provider_info pi
--    INNER JOIN zk_dubbo_service ds ON pi.service_id = ds.id
--    INNER JOIN zk_dubbo_service_node dsn ON pi.node_id = dsn.id
--    SET pi.interface_name = ds.interface_name,
--        pi.address = dsn.address;
-- ============================================

