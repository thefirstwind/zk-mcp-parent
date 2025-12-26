nacos接口

curl -X GET 'http://127.0.0.1:8848/nacos/v3/client/cs/config?dataId=9ea35be0-b045-48be-b123-0ed3e088ca2d-1.0.1-mcp-tools.json&groupName=mcp-tools'

# 注册实例
curl -X POST "http://127.0.0.1:8848/nacos/v3/client/ns/instance" -d "serviceName=virtual-data-analysis2&ip=127.0.0.1&port=9091"

# 续约实例
curl -X POST "http://127.0.0.1:8848/nacos/v3/client/ns/instance" -d "serviceName=test1&ip=127.0.0.1&port=3306&heartBeat=true"


curl -X DELETE "http://127.0.0.1:8848/nacos/v3/client/ns/instance?serviceName=virtual-data-analysis2&groupName=mcp-server"
curl -X DELETE "http://127.0.0.1:8848/nacos/v3/client/ns/instance?serviceName=virtual-data-analysis&ip=127.0.0.1&port=9091&groupName=mcp-server&ephemeral=false"

curl -X DELETE "http://127.0.0.1:8848/nacos/v3/client/ns/instance?serviceName=virtual-data-analysis2&ip=127.0.0.1&port=9091&groupName=mcp-server&ephemeral=false"



curl -X DELETE "http://127.0.0.1:8848/nacos/v3/client/ns/instance?serviceName=virtual-data-analysis&ip=127.0.0.1&port=9091&groupName=mcp-server&ephemeral=false"


curl -X DELETE "http://127.0.0.1:8848/nacos/v3/client/ns/instance?serviceName=virtual-test-virtual-node-1766660243&ip=192.168.0.101&port=9091&groupName=mcp-server&ephemeral=false"



virtual-test-virtual-node-1766659982
curl -X DELETE "http://127.0.0.1:8848/nacos/v3/client/ns/instance?serviceName=virtual-test-virtual-node-1766663870&ip=127.0.0.1&port=9091&groupName=mcp-server&ephemeral=false"
