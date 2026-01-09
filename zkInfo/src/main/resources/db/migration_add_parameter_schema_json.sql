use mcp_bridge;

-- Add structured JSON schema column for nested complex parameters (used for MCP tool inputSchema generation)
ALTER TABLE `zk_dubbo_method_parameter`
  ADD COLUMN `parameter_schema_json` TEXT COMMENT '参数结构化Schema（JSON，支持嵌套对象/集合/Map，用于生成MCP inputSchema）' AFTER `parameter_description`;

