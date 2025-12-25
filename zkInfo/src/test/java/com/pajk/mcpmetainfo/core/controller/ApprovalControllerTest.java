package com.pajk.mcpmetainfo.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpmetainfo.persistence.entity.ProviderInfoEntity;
import com.pajk.mcpmetainfo.core.service.ProviderInfoDbService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApprovalController.class)
class ApprovalControllerTest {

    private MockMvc mockMvc;

    @MockBean
    private ProviderInfoDbService providerInfoDbService;

    @InjectMocks
    private ApprovalController approvalController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(approvalController).build();
    }

    // 注意：审批相关的测试已更新，因为审批现在在服务级别进行
    // Provider 级别的审批功能已被废弃，审批功能现在通过 DubboServiceController 和 ServiceApprovalService 处理
    
    @Test
    void testGetPendingProviders_Success() throws Exception {
        // 准备测试数据
        ProviderInfoEntity provider1 = new ProviderInfoEntity();
        provider1.setId(1L);
        provider1.setInterfaceName("com.example.Service1");
        provider1.setApplication("app1");
        provider1.setServiceId(1L);
        provider1.setNodeId(1L);
        
        ProviderInfoEntity provider2 = new ProviderInfoEntity();
        provider2.setId(2L);
        provider2.setInterfaceName("com.example.Service2");
        provider2.setApplication("app2");
        provider2.setServiceId(2L);
        provider2.setNodeId(2L);
        
        List<ProviderInfoEntity> providers = Arrays.asList(provider1, provider2);
        
        // 设置mock行为 - 使用字符串形式的审批状态
        when(providerInfoDbService.findByApprovalStatus("PENDING")).thenReturn(providers);

        // 执行测试并验证结果
        mockMvc.perform(get("/api/approval/pending")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].interfaceName").value("com.example.Service1"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].interfaceName").value("com.example.Service2"));
    }

    @Test
    void testGetPendingProviders_InternalServerError() throws Exception {
        // 设置mock行为 - 使用字符串形式的审批状态
        when(providerInfoDbService.findByApprovalStatus("PENDING")).thenThrow(new RuntimeException("Database error"));

        // 执行测试并验证结果
        mockMvc.perform(get("/api/approval/pending")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testGetApprovedProviders_Success() throws Exception {
        // 准备测试数据
        ProviderInfoEntity provider1 = new ProviderInfoEntity();
        provider1.setId(1L);
        provider1.setInterfaceName("com.example.Service1");
        provider1.setApplication("app1");
        provider1.setServiceId(1L);
        provider1.setNodeId(1L);
        
        List<ProviderInfoEntity> providers = Arrays.asList(provider1);
        
        // 设置mock行为
        when(providerInfoDbService.findApprovedProviders()).thenReturn(providers);

        // 执行测试并验证结果
        mockMvc.perform(get("/api/approval/approved")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].interfaceName").value("com.example.Service1"));
        // 注意：approvalStatus 已移除，现在通过 service_id 关联 zk_dubbo_service 获取
    }

    // 注意：approveProvider 和 rejectProvider 方法已移除，这些测试已被废弃
    // 审批功能现在通过服务级别的 API 处理

    @Test
    void testGetProviderById_Success() throws Exception {
        // 准备测试数据
        ProviderInfoEntity provider = new ProviderInfoEntity();
        provider.setId(1L);
        provider.setInterfaceName("com.example.Service1");
        provider.setApplication("app1");
        provider.setServiceId(1L);
        provider.setNodeId(1L);
        provider.setCreatedAt(LocalDateTime.now());
        provider.setUpdatedAt(LocalDateTime.now());
        
        // 设置mock行为
        when(providerInfoDbService.findById(1L)).thenReturn(provider);

        // 执行测试并验证结果
        mockMvc.perform(get("/api/approval/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.interfaceName").value("com.example.Service1"))
                .andExpect(jsonPath("$.application").value("app1"));
    }

    @Test
    void testGetProviderById_NotFound() throws Exception {
        // 设置mock行为
        when(providerInfoDbService.findById(1L)).thenReturn(null);

        // 执行测试并验证结果
        mockMvc.perform(get("/api/approval/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}