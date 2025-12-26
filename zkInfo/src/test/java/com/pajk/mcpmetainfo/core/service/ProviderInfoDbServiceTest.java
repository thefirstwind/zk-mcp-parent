package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import com.pajk.mcpmetainfo.persistence.entity.ProviderInfoEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProviderInfoDbServiceTest {

    @Mock
    private DubboServiceDbService dubboServiceDbService;

    @Mock
    private InterfaceWhitelistService interfaceWhitelistService;

    @InjectMocks
    private ProviderInfoDbService providerInfoDbService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSaveOrUpdateProvider_NewProvider() {
        // 准备测试数据
        ProviderInfo provider = new ProviderInfo();
        provider.setInterfaceName("com.example.TestService");
        provider.setApplication("test-app");
        provider.setAddress("127.0.0.1:20880");
        provider.setZkPath("/dubbo/com.example.TestService/providers");

        // 设置mock行为
        when(interfaceWhitelistService.isAllowed(anyString())).thenReturn(true);
        doNothing().when(dubboServiceDbService).saveOrUpdateServiceWithNode(any(ProviderInfo.class));

        // 执行测试
        // 注意：saveOrUpdateProvider 现在返回 Object（已废弃），返回 null
        Object result = providerInfoDbService.saveOrUpdateProvider(provider);

        // 验证结果
        // 由于方法已废弃并返回 null，只验证方法被调用
        assertNull(result); // 方法现在返回 null
        
        // 验证方法调用
        verify(interfaceWhitelistService, times(1)).isAllowed("com.example.TestService");
        verify(dubboServiceDbService, times(1)).saveOrUpdateServiceWithNode(any(ProviderInfo.class));
    }

    @Test
    void testSaveOrUpdateProvider_ExistingProvider() {
        // 准备测试数据
        ProviderInfo provider = new ProviderInfo();
        provider.setInterfaceName("com.example.TestService");
        provider.setApplication("test-app-updated");
        provider.setAddress("127.0.0.1:20881");
        provider.setZkPath("/dubbo/com.example.TestService/providers");

        // 设置mock行为
        when(interfaceWhitelistService.isAllowed(anyString())).thenReturn(true);
        doNothing().when(dubboServiceDbService).saveOrUpdateServiceWithNode(any(ProviderInfo.class));

        // 执行测试
        // 注意：saveOrUpdateProvider 现在返回 Object（已废弃），返回 null
        Object result = providerInfoDbService.saveOrUpdateProvider(provider);

        // 验证结果
        // 由于方法已废弃并返回 null，只验证方法被调用
        assertNull(result); // 方法现在返回 null
        
        // 验证方法调用
        verify(interfaceWhitelistService, times(1)).isAllowed("com.example.TestService");
        verify(dubboServiceDbService, times(1)).saveOrUpdateServiceWithNode(any(ProviderInfo.class));
    }

    // 注意：审批相关的方法已移除，审批现在在服务级别进行
    // 这些测试已被移除，因为 Provider 级别的审批功能已被废弃
    // 审批功能现在通过 DubboServiceDbService 和 ServiceApprovalService 处理
}