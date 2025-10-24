package com.zkinfo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Web 页面控制器
 * 
 * 提供 Web 页面的路由和重定向功能，主要用于将用户引导到
 * API 文档页面和系统管理界面。
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Controller
public class WebController {
    
    /**
     * 首页重定向到 Swagger UI
     * 
     * 当用户访问根路径时，自动重定向到 Swagger UI 页面，
     * 方便用户查看和测试 API 接口。
     * 
     * @return 重定向到 Swagger UI 的视图
     */
    @GetMapping("/")
    public RedirectView index() {
        return new RedirectView("/swagger-ui.html");
    }
    
    /**
     * API 文档页面重定向
     * 
     * 提供 /docs 路径的访问入口，重定向到 Swagger UI。
     * 
     * @return 重定向到 Swagger UI 的视图
     */
    @GetMapping("/docs")
    public RedirectView docs() {
        return new RedirectView("/swagger-ui.html");
    }
}
