curl -X POST http://localhost:9091/api/projects \
  -H "Content-Type: application/json" \
  -d '{
    "projectCode": "TEST_PROJECT_001",
    "projectName": "测试项目1",
    "projectType": "REAL",
    "description": "用于测试的项目",
    "status": "ACTIVE"
  }'

curl -X POST http://localhost:9091/api/projects/1/services \
  -H "Content-Type: application/json" \
  -d '{
    "serviceInterface": "com.zkinfo.demo.service.UserService",
    "serviceVersion": "1.0.0",
    "serviceGroup": "mcp-server",
    "enabled": true
  }'

curl http://localhost:9091/api/projects/1/services | jq


curl -X POST http://localhost:9091/api/filters \
  -H "Content-Type: application/json" \
  -d '{
    "filterType": "PREFIX",
    "filterValue": "test",
    "filterOperator": "EXCLUDE",
    "priority": 10,
    "enabled": true,
    "description": "排除test开头的服务"
  }'


curl http://localhost:9091/api/filters | jq


curl -X POST http://localhost:9091/api/approvals \
  -H "Content-Type: application/json" \
  -d '{
    "serviceInterface": "com.zkinfo.demo.service.OrderService",
    "serviceVersion": "1.0.0",
    "serviceGroup": "mcp-server",
    "applicantId": 1,
    "applicantName": "测试用户",
    "reason": "需要接入MCP系统"
  }'

curl -X PUT http://localhost:9091/api/approvals/1/approve \
  -H "Content-Type: application/json" \
  -d '{
    "approverId": 2,
    "approverName": "管理员",
    "comment": "同意"
  }'


curl -X POST http://localhost:9091/api/projects \
  -H "Content-Type: application/json" \
  -d '{
    "projectCode": "USER_CENTER",
    "projectName": "用户中心项目",
    "projectType": "REAL",
    "description": "用户中心相关服务",
    "ownerId": 1,
    "ownerName": "张三",
    "status": "ACTIVE"
  }'

curl http://localhost:9091/api/projects | jq


curl -X POST http://localhost:9091/api/projects/1/services \
  -H "Content-Type: application/json" \
  -d '{
    "serviceInterface": "com.zkinfo.demo.service.UserService",
    "serviceVersion": "1.0.0",
    "serviceGroup": "mcp-server",
    "priority": 10,
    "enabled": true
  }'

curl -X POST http://localhost:9091/api/projects/1/services \
  -H "Content-Type: application/json" \
  -d '{
    "serviceInterface": "com.zkinfo.demo.service.OrderService",
    "serviceVersion": "1.0.0",
    "serviceGroup": "mcp-server",
    "priority": 5,
    "enabled": true
  }'



curl http://localhost:9091/api/projects/1/services | jq

curl http://localhost:9091/api/projects/1/services | jq 'length'



curl -X POST http://localhost:9091/api/virtual-projects \
  -H "Content-Type: application/json" \
  -d '{
    "name": "数据分析场景",
    "description": "用于数据分析的虚拟项目",
    "endpointName": "data-analysis",
    "services": [
      {
        "serviceInterface": "com.zkinfo.demo.service.UserService",
        "version": "1.0.0",
        "group": "mcp-server",
        "priority": 10
      },
      {
        "serviceInterface": "com.zkinfo.demo.service.OrderService",
        "version": "1.0.0",
        "group": "mcp-server",
        "priority": 10
      },
      {
        "serviceInterface": "com.zkinfo.demo.service.ProductService",
        "version": "1.0.0",
        "group": "mcp-server",
        "priority": 5
      }
    ],
    "autoRegister": true
  }'

curl http://localhost:9091/api/virtual-projects | jq


VIRTUAL_PROJECT_ID=$(curl -s http://localhost:9091/api/virtual-projects | jq -r '.[0].project.id')
echo "虚拟项目ID: $VIRTUAL_PROJECT_ID"

curl http://localhost:9091/api/virtual-projects/$VIRTUAL_PROJECT_ID | jq


curl http://localhost:9091/api/virtual-projects/$VIRTUAL_PROJECT_ID/endpoint | jq




curl http://localhost:9091/api/virtual-projects/$VIRTUAL_PROJECT_ID

curl http://localhost:9091/api/registered-services | jq