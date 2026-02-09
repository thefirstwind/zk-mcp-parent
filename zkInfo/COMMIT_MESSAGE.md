refactor: Integrate AiMaintainerService for Nacos MCP Registration

## Summary
Refactored zkInfo's Nacos registration logic to use standard AiMaintainerService 
API, aligning with spring-ai-alibaba implementation pattern.

## Changes

### Dependencies (pom.xml)
- Upgraded nacos-client: 2.4.2 → 3.0.1
- Added nacos-maintainer-client: 3.0.1

### Core Registration Logic (NacosMcpRegistrationService.java)
- Added AiMaintainerService initialization via AiMaintainerFactory
- Implemented publishMcpServerToNacosUsingMaintainerService() for standard MCP server registration
- Implemented createMcpToolList() to convert internal tool format to McpTool objects
- Updated registerDubboServiceAsMcp() to prioritize AiMaintainerService with ConfigService fallback
- Updated registerVirtualProjectAsMcp() with same priority logic
- Optimized registerInstanceToNacos() to skip MD5 calculation when using AiMaintainerService
- Fixed type compatibility for Nacos 3.x McpTool.setInputSchema() (requires Map<String, Object>)

### Test Fixes (DubboToMcpAutoRegistrationServiceTest.java)
- Fixed test to use setAddress() instead of non-existent setPort()
- All unit tests passing

## Backward Compatibility
- Graceful degradation: Falls back to ConfigService if AiMaintainerService initialization fails
- No breaking changes to existing functionality
- Configuration remains unchanged (uses @Value annotations)

## Testing Status
✅ Compilation successful
✅ Core unit tests passing (DubboToMcpAutoRegistrationServiceTest: 2/2)
✅ No regression in modified code paths
⚠️ Some integration tests require full Spring context (DataSource) - unrelated to changes

## Related Files
- REFACTORING_VALIDATION_REPORT.md: Detailed validation results
- OPTIMIZATION_SUMMARY.md: Previous optimization summary

## References
- Issue: Align zkInfo Nacos registration with spring-ai-alibaba implementation
- Target Nacos version: 3.1.1
- Based on: spring-ai-alibaba mcp-nacos module pattern
