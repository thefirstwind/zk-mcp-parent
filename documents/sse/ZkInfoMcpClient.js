class ZkInfoMcpClient {
    constructor(baseUrl = 'http://localhost:9091') {
        this.baseUrl = baseUrl;
    }
    
    async getAvailableTools(application = null) {
        const url = application 
            ? `${this.baseUrl}/api/applications/${application}/mcp`
            : `${this.baseUrl}/api/mcp`;
        
        const response = await fetch(url);
        return await response.json();
    }
    
    async callTool(toolName, args = [], timeout = 3000) {
        const response = await fetch(`${this.baseUrl}/api/mcp/call`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                toolName,
                args,
                timeout
            })
        });
        
        return await response.json();
    }
}

// 导出类
module.exports = ZkInfoMcpClient;

// 如果直接运行此文件，则执行示例
if (require.main === module) {
    // 使用示例
    const client = new ZkInfoMcpClient();

    // 获取产品信息
    client.callTool(
        'com.zkinfo.demo.service.ProductService.getProductById',
        [1]
    ).then(result => {
        console.log('产品信息:', result);
    });
}
