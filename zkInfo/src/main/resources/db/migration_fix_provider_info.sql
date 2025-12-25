use mcp_bridge;

-- ============================================
-- 修复 zk_provider_info 表结构
-- 执行顺序：先添加新字段，再移除旧字段
-- ============================================

-- 1. 添加 service_id 字段（允许 NULL，如果表中有数据需要先填充）
ALTER TABLE `zk_provider_info` 
    ADD COLUMN `service_id` BIGINT COMMENT '关联的Dubbo服务ID（引用 zk_dubbo_service.id）' AFTER `id`;

-- 2. 添加 node_id 字段（允许 NULL，如果表中有数据需要先填充）
ALTER TABLE `zk_provider_info` 
    ADD COLUMN `node_id` BIGINT COMMENT '关联的服务节点ID（引用 zk_dubbo_service_node.id）' AFTER `service_id`;

-- 3. 添加 interface_name 和 address 字段（如果不存在）
-- 注意：这些字段保留，便于快速查询和定位问题
ALTER TABLE `zk_provider_info` 
    ADD COLUMN `interface_name` VARCHAR(500) NOT NULL COMMENT '服务接口名（便于快速查询和定位问题）' AFTER `node_id`,
    ADD COLUMN `address` VARCHAR(200) NOT NULL COMMENT '提供者地址 (IP:Port)（便于快速查询和定位问题）' AFTER `interface_name`;

-- 4. 移除其他旧字段（如果表中有数据，需要先填充 service_id 和 node_id）
-- 注意：如果表为空，可以直接执行；如果有数据，需要先填充 service_id 和 node_id
ALTER TABLE `zk_provider_info` 
    DROP COLUMN `protocol`,
    DROP COLUMN `version`,
    DROP COLUMN `group`,
    DROP COLUMN `application`,
    DROP COLUMN `approval_status`,
    DROP COLUMN `approver`,
    DROP COLUMN `approval_time`,
    DROP COLUMN `approval_comment`,
    DROP COLUMN `path_root`,
    DROP COLUMN `path_interface`,
    DROP COLUMN `path_address`,
    DROP COLUMN `path_protocol`,
    DROP COLUMN `path_group`,
    DROP COLUMN `path_application`;

-- 5. 将 service_id 和 node_id 改为 NOT NULL（确保数据已填充）
ALTER TABLE `zk_provider_info` 
    MODIFY COLUMN `service_id` BIGINT NOT NULL COMMENT '关联的Dubbo服务ID（引用 zk_dubbo_service.id）',
    MODIFY COLUMN `node_id` BIGINT NOT NULL COMMENT '关联的服务节点ID（引用 zk_dubbo_service_node.id）';

-- 6. 添加索引
ALTER TABLE `zk_provider_info` 
    ADD INDEX `idx_service_id` (`service_id`),
    ADD INDEX `idx_node_id` (`node_id`),
    ADD INDEX `idx_interface_name` (`interface_name`),
    ADD INDEX `idx_address` (`address`),
    ADD UNIQUE INDEX `uk_service_node` (`service_id`, `node_id`);

