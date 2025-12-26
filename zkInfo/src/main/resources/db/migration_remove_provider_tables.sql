-- ============================================
-- 废弃 zk_provider_info、zk_provider_method、zk_provider_parameter 表
-- 将功能迁移到 zk_dubbo_service_node 表
-- 创建日期: 2025-12-25
-- ============================================

-- 1. 扩展 zk_dubbo_service_node 表，添加心跳和状态字段
ALTER TABLE `zk_dubbo_service_node` 
    ADD COLUMN IF NOT EXISTS `registration_time` DATETIME COMMENT '注册时间' AFTER `address`,
    ADD COLUMN IF NOT EXISTS `last_heartbeat_time` DATETIME COMMENT '最后心跳时间' AFTER `registration_time`,
    ADD COLUMN IF NOT EXISTS `is_online` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否在线：1-在线，0-离线' AFTER `last_heartbeat_time`,
    ADD COLUMN IF NOT EXISTS `is_healthy` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否健康：1-健康，0-不健康' AFTER `is_online`;

-- 2. 添加索引以优化查询
ALTER TABLE `zk_dubbo_service_node`
    ADD INDEX IF NOT EXISTS `idx_is_online` (`is_online`),
    ADD INDEX IF NOT EXISTS `idx_is_healthy` (`is_healthy`),
    ADD INDEX IF NOT EXISTS `idx_last_heartbeat` (`last_heartbeat_time`);

-- 3. 数据迁移：从 zk_provider_info 迁移数据到 zk_dubbo_service_node
-- 注意：如果 zk_provider_info 表存在数据，需要迁移
UPDATE `zk_dubbo_service_node` dsn
INNER JOIN `zk_provider_info` pi ON dsn.service_id = pi.service_id AND dsn.id = pi.node_id
SET 
    dsn.registration_time = pi.registration_time,
    dsn.last_heartbeat_time = pi.last_heartbeat_time,
    dsn.is_online = pi.is_online,
    dsn.is_healthy = pi.is_healthy;

-- 4. 对于没有对应 zk_provider_info 记录的节点，设置默认值
UPDATE `zk_dubbo_service_node`
SET 
    registration_time = COALESCE(registration_time, gmt_created),
    last_heartbeat_time = COALESCE(last_heartbeat_time, gmt_created),
    is_online = COALESCE(is_online, 1),
    is_healthy = COALESCE(is_healthy, 1)
WHERE registration_time IS NULL;

-- 5. 废弃表（可选，建议先备份数据后再执行）
-- DROP TABLE IF EXISTS `zk_provider_parameter`;
-- DROP TABLE IF EXISTS `zk_provider_method`;
-- DROP TABLE IF EXISTS `zk_provider_info`;

-- 注意：建议先注释掉 DROP TABLE 语句，等确认数据迁移成功后再执行

