use mcp_bridge;

-- ============================================
-- 紧急修复：为 zk_provider_info 表添加 service_id 和 node_id 字段
-- 注意：如果表中已有数据，先添加为允许 NULL，填充数据后再改为 NOT NULL
-- ============================================

-- 1. 添加 service_id 字段（允许 NULL，后续填充数据后再改为 NOT NULL）
ALTER TABLE `zk_provider_info` 
    ADD COLUMN `service_id` BIGINT COMMENT '关联的Dubbo服务ID（引用 zk_dubbo_service.id）' AFTER `id`;

-- 2. 添加 node_id 字段（允许 NULL，后续填充数据后再改为 NOT NULL）
ALTER TABLE `zk_provider_info` 
    ADD COLUMN `node_id` BIGINT COMMENT '关联的服务节点ID（引用 zk_dubbo_service_node.id）' AFTER `service_id`;

-- 3. 添加普通索引（在填充数据前先添加索引）
ALTER TABLE `zk_provider_info` 
    ADD INDEX `idx_service_id` (`service_id`),
    ADD INDEX `idx_node_id` (`node_id`);

-- 4. 注意：填充 service_id 和 node_id 的数据（需要应用层代码执行）
-- 示例 SQL（仅供参考，实际需要根据业务逻辑填充）：
-- UPDATE zk_provider_info pi
-- INNER JOIN zk_dubbo_service ds ON pi.interface_name = ds.interface_name 
--     AND pi.protocol = ds.protocol AND pi.version = ds.version
-- INNER JOIN zk_dubbo_service_node dsn ON ds.id = dsn.service_id 
--     AND pi.address = dsn.address
-- SET pi.service_id = ds.id, pi.node_id = dsn.id;

-- 5. 填充数据后，将字段改为 NOT NULL
-- ALTER TABLE `zk_provider_info` 
--     MODIFY COLUMN `service_id` BIGINT NOT NULL COMMENT '关联的Dubbo服务ID（引用 zk_dubbo_service.id）',
--     MODIFY COLUMN `node_id` BIGINT NOT NULL COMMENT '关联的服务节点ID（引用 zk_dubbo_service_node.id）';

-- 6. 添加唯一索引（在字段改为 NOT NULL 后添加）
-- ALTER TABLE `zk_provider_info` 
--     ADD UNIQUE INDEX `uk_service_node` (`service_id`, `node_id`);

