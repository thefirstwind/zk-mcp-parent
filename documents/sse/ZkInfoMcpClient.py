import requests
import json
import logging
from typing import Optional, List, Dict, Any, Union

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')

class ZkInfoMcpClientError(Exception):
    """MCP客户端自定义异常"""
    pass

class ZkInfoMcpClient:
    def __init__(self, base_url: str = "http://localhost:9091", timeout: int = 30):
        """
        初始化MCP客户端
        
        Args:
            base_url: MCP服务器基础URL
            timeout: 请求超时时间（秒）
        """
        self.base_url = base_url.rstrip('/')
        self.timeout = timeout
        self.logger = logging.getLogger(self.__class__.__name__)
        
        # 验证服务器连接
        self._validate_connection()
    
    def _validate_connection(self):
        """验证与MCP服务器的连接"""
        try:
            response = requests.get(f"{self.base_url}/api/mcp", timeout=5)
            response.raise_for_status()
            self.logger.info(f"成功连接到MCP服务器: {self.base_url}")
        except requests.exceptions.RequestException as e:
            self.logger.error(f"无法连接到MCP服务器 {self.base_url}: {e}")
            raise ZkInfoMcpClientError(f"MCP服务器连接失败: {e}")
    
    def _make_request(self, method: str, url: str, **kwargs) -> Dict[str, Any]:
        """统一的HTTP请求处理"""
        try:
            kwargs.setdefault('timeout', self.timeout)
            response = requests.request(method, url, **kwargs)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.Timeout:
            self.logger.error(f"请求超时: {url}")
            raise ZkInfoMcpClientError("请求超时")
        except requests.exceptions.ConnectionError:
            self.logger.error(f"连接错误: {url}")
            raise ZkInfoMcpClientError("连接错误")
        except requests.exceptions.HTTPError as e:
            self.logger.error(f"HTTP错误 {e.response.status_code}: {url}")
            raise ZkInfoMcpClientError(f"HTTP错误: {e.response.status_code}")
        except json.JSONDecodeError:
            self.logger.error(f"响应不是有效的JSON: {url}")
            raise ZkInfoMcpClientError("响应格式错误")
        except Exception as e:
            self.logger.error(f"未知错误: {e}")
            raise ZkInfoMcpClientError(f"未知错误: {e}")
    
    def get_available_tools(self, application: Optional[str] = None) -> List[Dict[str, Any]]:
        """
        获取可用的MCP工具
        
        Args:
            application: 指定应用名称，为None时获取所有应用的工具
            
        Returns:
            工具列表
            
        Raises:
            ZkInfoMcpClientError: 当请求失败时
        """
        if application:
            if not isinstance(application, str) or not application.strip():
                raise ValueError("应用名称必须是非空字符串")
            url = f"{self.base_url}/api/applications/{application.strip()}/mcp"
        else:
            url = f"{self.base_url}/api/mcp"
        
        self.logger.info(f"获取MCP工具列表: {url}")
        result = self._make_request('GET', url)
        
        tools_count = sum(len(app.get('tools', [])) for app in result) if isinstance(result, list) else 0
        self.logger.info(f"成功获取 {tools_count} 个工具")
        
        return result
    
    def call_tool(self, tool_name: str, args: Optional[List[Any]] = None, timeout: int = 3000) -> Dict[str, Any]:
        """
        同步调用MCP工具
        
        Args:
            tool_name: 工具名称
            args: 参数列表
            timeout: 调用超时时间（毫秒）
            
        Returns:
            调用结果
            
        Raises:
            ZkInfoMcpClientError: 当调用失败时
        """
        if not isinstance(tool_name, str) or not tool_name.strip():
            raise ValueError("工具名称必须是非空字符串")
        
        if timeout <= 0:
            raise ValueError("超时时间必须大于0")
        
        url = f"{self.base_url}/api/mcp/call"
        payload = {
            "toolName": tool_name.strip(),
            "args": args or [],
            "timeout": timeout
        }
        
        self.logger.info(f"调用MCP工具: {tool_name}")
        result = self._make_request('POST', url, json=payload)
        
        if result.get('success'):
            self.logger.info(f"工具调用成功: {tool_name}")
        else:
            self.logger.warning(f"工具调用失败: {tool_name}, 错误: {result.get('error', '未知错误')}")
        
        return result
    
    def call_tool_async(self, tool_name: str, args: Optional[List[Any]] = None, timeout: int = 3000) -> Dict[str, Any]:
        """
        异步调用MCP工具
        
        Args:
            tool_name: 工具名称
            args: 参数列表
            timeout: 调用超时时间（毫秒）
            
        Returns:
            调用结果
            
        Raises:
            ZkInfoMcpClientError: 当调用失败时
        """
        if not isinstance(tool_name, str) or not tool_name.strip():
            raise ValueError("工具名称必须是非空字符串")
        
        if timeout <= 0:
            raise ValueError("超时时间必须大于0")
        
        url = f"{self.base_url}/api/mcp/call-async"
        payload = {
            "toolName": tool_name.strip(),
            "args": args or [],
            "timeout": timeout
        }
        
        self.logger.info(f"异步调用MCP工具: {tool_name}")
        result = self._make_request('POST', url, json=payload)
        
        if result.get('accepted'):
            self.logger.info(f"异步工具调用已接受: {tool_name}")
        else:
            self.logger.warning(f"异步工具调用被拒绝: {tool_name}")
        
        return result
    
    def get_tool_info(self, tool_name: str) -> Optional[Dict[str, Any]]:
        """
        获取指定工具的详细信息
        
        Args:
            tool_name: 工具名称
            
        Returns:
            工具信息，如果未找到返回None
        """
        tools = self.get_available_tools()
        for app in tools:
            for tool in app.get('tools', []):
                if tool.get('name') == tool_name:
                    return tool
        return None

def main():
    """主函数 - 演示MCP客户端的使用"""
    try:
        # 初始化客户端
        client = ZkInfoMcpClient()
        
        # 1. 获取所有可用工具
        print("=== 获取所有可用工具 ===")
        tools = client.get_available_tools()
        print(f"发现 {sum(len(app.get('tools', [])) for app in tools)} 个工具")
        
        # 2. 获取指定工具信息
        print("\n=== 获取工具信息 ===")
        tool_info = client.get_tool_info("com.zkinfo.demo.service.UserService.getUserById")
        if tool_info:
            print(f"工具描述: {tool_info.get('description')}")
        
        # 3. 调用用户服务 - 获取用户信息
        print("\n=== 获取用户信息 ===")
        user_result = client.call_tool(
            "com.zkinfo.demo.service.UserService.getUserById",
            args=[1]
        )
        if user_result.get('success'):
            user = user_result.get('result', {})
            print(f"用户: {user.get('username')} ({user.get('realName')})")
        else:
            print(f"获取用户失败: {user_result.get('error')}")
        
        # 4. 调用产品服务 - 搜索产品
        print("\n=== 搜索产品 ===")
        product_result = client.call_tool(
            "com.zkinfo.demo.service.ProductService.searchProducts",
            args=["iPhone"]
        )
        if product_result.get('success'):
            products = product_result.get('result', [])
            print(f"找到 {len(products)} 个产品")
            for product in products[:3]:  # 只显示前3个
                print(f"- {product.get('name')}: ¥{product.get('price')}")
        else:
            print(f"搜索产品失败: {product_result.get('error')}")
        
        # 5. 异步调用 - 创建订单
        print("\n=== 异步创建订单 ===")
        async_result = client.call_tool_async(
            "com.zkinfo.demo.service.OrderService.createOrder",
            args=[{
                "userId": 1,
                "productId": 123,
                "quantity": 2
            }],
            timeout=10000
        )
        if async_result.get('accepted'):
            print(f"订单创建请求已接受: {async_result.get('message')}")
        else:
            print(f"订单创建请求被拒绝")
        
        # 6. 错误处理示例 - 调用不存在的工具
        print("\n=== 错误处理示例 ===")
        try:
            error_result = client.call_tool("nonexistent.tool", [])
            print(f"调用结果: {error_result}")
        except Exception as e:
            print(f"捕获异常: {e}")
        
    except ZkInfoMcpClientError as e:
        print(f"MCP客户端错误: {e}")
    except Exception as e:
        print(f"未知错误: {e}")

if __name__ == "__main__":
    main()
