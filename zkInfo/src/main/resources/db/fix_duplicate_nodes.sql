use mcp_bridge;

-- ============================================
-- 修复 zk_dubbo_service_node 表中的重复数据
-- ============================================

-- 1. 检查是否存在重复数据（service_id + address 组合重复）
SELECT 
    service_id, 
    address, 
    COUNT(*) as duplicate_count,
    GROUP_CONCAT(id ORDER BY gmt_created DESC) as node_ids
FROM zk_dubbo_service_node
GROUP BY service_id, address
HAVING COUNT(*) > 1;

-- 2. 删除重复数据，只保留最新的一条记录（根据 gmt_created 排序）
-- 注意：执行前请先备份数据！
DELETE n1 FROM zk_dubbo_service_node n1
INNER JOIN zk_dubbo_service_node n2
WHERE n1.service_id = n2.service_id 
  AND n1.address = n2.address
  AND n1.id < n2.id;

-- 3. 验证唯一索引是否存在
SHOW INDEX FROM zk_dubbo_service_node WHERE Key_name = 'uk_service_address';

-- 4. 如果唯一索引不存在，创建唯一索引
-- ALTER TABLE zk_dubbo_service_node 
--     ADD UNIQUE INDEX `uk_service_address` (`service_id`, `address`);

-- 5. 再次检查是否还有重复数据
SELECT 
    service_id, 
    address, 
    COUNT(*) as duplicate_count
FROM zk_dubbo_service_node
GROUP BY service_id, address
HAVING COUNT(*) > 1;





