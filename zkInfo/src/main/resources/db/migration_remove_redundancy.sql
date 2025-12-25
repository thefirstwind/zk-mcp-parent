use mcp_bridge;

-- ============================================
-- 移除冗余字段和合并审批逻辑的迁移脚本
-- ============================================

-- 1. 移除 zk_provider_info 表的审批字段（审批状态通过 service_id 关联 zk_dubbo_service 获取）
ALTER TABLE `zk_provider_info` 
    DROP COLUMN `approval_status`,
    DROP COLUMN `approver`,
    DROP COLUMN `approval_time`,
    DROP COLUMN `approval_comment`;

-- 2. 移除 zk_provider_info 表的 path_* 字段（路径信息通过 service_id 和 node_id 关联获取）
ALTER TABLE `zk_provider_info` 
    DROP COLUMN `path_root`,
    DROP COLUMN `path_interface`,
    DROP COLUMN `path_address`,
    DROP COLUMN `path_protocol`,
    DROP COLUMN `path_group`,
    DROP COLUMN `path_application`;

-- 3. 先添加 service_id 和 node_id 字段（允许 NULL，后续填充数据后再改为 NOT NULL）
-- 注意：如果表中已有数据，需要先填充 service_id 和 node_id，然后再执行后续的 DROP COLUMN
ALTER TABLE `zk_provider_info` 
    ADD COLUMN `service_id` BIGINT COMMENT '关联的Dubbo服务ID（引用 zk_dubbo_service.id）' AFTER `id`,
    ADD COLUMN `node_id` BIGINT COMMENT '关联的服务节点ID（引用 zk_dubbo_service_node.id）' AFTER `service_id`;

-- 4. 添加 interface_name 和 address 字段（如果不存在）
-- 注意：这些字段保留，便于快速查询和定位问题
ALTER TABLE `zk_provider_info` 
    ADD COLUMN `interface_name` VARCHAR(500) NOT NULL COMMENT '服务接口名（便于快速查询和定位问题）' AFTER `node_id`,
    ADD COLUMN `address` VARCHAR(200) NOT NULL COMMENT '提供者地址 (IP:Port)（便于快速查询和定位问题）' AFTER `interface_name`;

-- 4.1 移除 zk_provider_info 表的 protocol, version, group, application 字段
-- （这些字段通过 service_id 和 node_id 关联获取）
-- 注意：执行前确保 service_id 和 node_id 已填充数据
ALTER TABLE `zk_provider_info` 
    DROP COLUMN `protocol`,
    DROP COLUMN `version`,
    DROP COLUMN `group`,
    DROP COLUMN `application`;

-- 5. 将 service_id 和 node_id 改为 NOT NULL（确保数据已填充）
ALTER TABLE `zk_provider_info` 
    MODIFY COLUMN `service_id` BIGINT NOT NULL COMMENT '关联的Dubbo服务ID（引用 zk_dubbo_service.id）',
    MODIFY COLUMN `node_id` BIGINT NOT NULL COMMENT '关联的服务节点ID（引用 zk_dubbo_service_node.id）';

-- 6. 添加唯一索引
ALTER TABLE `zk_provider_info` 
    ADD UNIQUE INDEX `uk_service_node` (`service_id`, `node_id`);

-- 6. 修改 zk_service_approval 表，改为引用 service_id
-- 6.1 添加 service_id 字段
ALTER TABLE `zk_service_approval` 
    ADD COLUMN `service_id` BIGINT NOT NULL COMMENT '关联的Dubbo服务ID（引用 zk_dubbo_service.id）' AFTER `id`;

-- 6.2 移除冗余的服务信息字段
ALTER TABLE `zk_service_approval` 
    DROP COLUMN `service_interface`,
    DROP COLUMN `service_version`,
    DROP COLUMN `service_group`;

-- 6.3 添加唯一索引
ALTER TABLE `zk_service_approval` 
    ADD UNIQUE INDEX `uk_service_approval` (`service_id`);

-- 8. 修改 zk_approval_log 表，改为主要记录 service_id 的审批历史
-- 7.1 移除 provider_id 字段（因为审批现在在服务级别）
ALTER TABLE `zk_approval_log` 
    DROP COLUMN `provider_id`;

-- 7.2 确保 service_id 字段存在且非空
ALTER TABLE `zk_approval_log` 
    MODIFY COLUMN `service_id` BIGINT NOT NULL COMMENT '关联的服务ID（引用 zk_dubbo_service.id）';

-- 7.3 添加 approval_id 字段（关联 zk_service_approval.id）
ALTER TABLE `zk_approval_log` 
    ADD COLUMN `approval_id` BIGINT COMMENT '关联的审批申请ID（引用 zk_service_approval.id，可选）' AFTER `service_id`;

-- 7.4 添加索引
ALTER TABLE `zk_approval_log` 
    ADD INDEX `idx_approval_id` (`approval_id`);

-- ============================================
-- 注意：
-- 1. 执行此脚本前，请确保已有数据已正确迁移 service_id 和 node_id
-- 2. 如果 zk_provider_info 表中已有数据，需要先通过关联查询填充 service_id 和 node_id
-- 3. 审批相关的业务逻辑需要更新为在服务级别进行审批
-- ============================================

