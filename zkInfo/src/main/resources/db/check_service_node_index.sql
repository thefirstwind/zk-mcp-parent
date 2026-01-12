-- 检查 zk_dubbo_service_node 表的索引情况
-- 确认唯一索引 uk_service_address 是否存在

-- 1. 查看表结构
SHOW CREATE TABLE zk_dubbo_service_node;

-- 2. 查看所有索引
SHOW INDEX FROM zk_dubbo_service_node;

-- 3. 检查唯一索引是否存在
SELECT 
    INDEX_NAME,
    COLUMN_NAME,
    NON_UNIQUE,
    SEQ_IN_INDEX
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'zk_dubbo_service_node'
  AND INDEX_NAME = 'uk_service_address'
ORDER BY SEQ_IN_INDEX;

-- 4. 测试：同一个 service_id 可以有多个不同的 address
-- 示例数据：
-- service_id=1, address="192.168.1.1:20880" ✓ 允许
-- service_id=1, address="192.168.1.2:20880" ✓ 允许
-- service_id=1, address="192.168.1.1:20880" ✗ 不允许（重复）

-- 5. 检查是否有重复数据（违反唯一索引的数据）
SELECT 
    service_id, 
    address, 
    COUNT(*) as count
FROM zk_dubbo_service_node
GROUP BY service_id, address
HAVING COUNT(*) > 1;

-- 6. 查看同一个 service_id 的所有节点（验证多个 address 是否正常）
-- 替换 <service_id> 为实际的 service_id
-- SELECT * FROM zk_dubbo_service_node WHERE service_id = <service_id> ORDER BY address;


