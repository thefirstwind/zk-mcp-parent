package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.persistence.mapper.ApprovalLogMapper;
import com.pajk.mcpmetainfo.persistence.mapper.ProviderInfoMapper;
import com.pajk.mcpmetainfo.persistence.entity.ProviderInfoEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ProviderInfoDbServiceTest {

    @Mock
    private ProviderInfoMapper providerInfoMapper;

    @Mock
    private ApprovalLogMapper approvalLogMapper;

    @InjectMocks
    private ProviderInfoDbService providerInfoDbService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSaveOrUpdateProvider_NewProvider() {
        // 准备测试数据
        ProviderInfoEntity provider = new ProviderInfoEntity();
        provider.setInterfaceName("com.example.TestService");
        provider.setApplication("test-app");
        provider.setAddress("127.0.0.1:20880");
        provider.setZkPath("/dubbo/com.example.TestService/providers");
        provider.setServiceId(1L);
        provider.setNodeId(1L);

        // 设置mock行为
        when(providerInfoMapper.findByZkPath(anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        doAnswer(invocation -> {
            ProviderInfoEntity entity = invocation.getArgument(0);
            entity.setId(1L); // 模拟设置ID
            return null; // insert方法通常是void类型，所以返回null是合适的
        }).when(providerInfoMapper).insert(any(ProviderInfoEntity.class));

        // 执行测试
        ProviderInfoEntity result = providerInfoDbService.saveOrUpdateProvider(provider);

        // 验证结果
        assertNotNull(result);
        assertEquals("com.example.TestService", result.getInterfaceName());
        assertEquals("test-app", result.getApplication());
        // 注意：审批状态已移除，现在通过 service_id 关联 zk_dubbo_service 获取
        
        // 验证方法调用
        verify(providerInfoMapper, times(1)).findByZkPath(anyString(), anyString(), anyString(), anyString());
        verify(providerInfoMapper, times(1)).insert(any(ProviderInfoEntity.class));
    }

    @Test
    void testSaveOrUpdateProvider_ExistingProvider() {
        // 准备测试数据
        ProviderInfoEntity existingProvider = new ProviderInfoEntity();
        existingProvider.setId(1L);
        existingProvider.setInterfaceName("com.example.TestService");
        existingProvider.setApplication("test-app");
        existingProvider.setAddress("127.0.0.1:20880");
        existingProvider.setZkPath("/dubbo/com.example.TestService/providers");
        existingProvider.setServiceId(1L);
        existingProvider.setNodeId(1L);
        existingProvider.setUpdatedAt(LocalDateTime.now().minusDays(1));

        ProviderInfoEntity updatedProvider = new ProviderInfoEntity();
        updatedProvider.setInterfaceName("com.example.TestService");
        updatedProvider.setApplication("test-app-updated");
        updatedProvider.setAddress("127.0.0.1:20881");
        updatedProvider.setZkPath("/dubbo/com.example.TestService/providers");
        updatedProvider.setServiceId(1L);
        updatedProvider.setNodeId(1L);

        // 设置mock行为
        when(providerInfoMapper.findByZkPath(anyString(), anyString(), anyString(), anyString())).thenReturn(existingProvider);
        doReturn(1).when(providerInfoMapper).update(any(ProviderInfoEntity.class));

        // 执行测试
        ProviderInfoEntity result = providerInfoDbService.saveOrUpdateProvider(updatedProvider);

        // 验证结果
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("test-app-updated", result.getApplication());
        assertEquals("127.0.0.1:20881", result.getAddress());
        assertTrue(result.getUpdatedAt().isAfter(existingProvider.getUpdatedAt()));
        
        // 验证方法调用
        verify(providerInfoMapper, times(1)).findByZkPath(anyString(), anyString(), anyString(), anyString());
        verify(providerInfoMapper, times(1)).update(any(ProviderInfoEntity.class));
    }

    // 注意：审批相关的方法已移除，审批现在在服务级别进行
    // 这些测试已被移除，因为 Provider 级别的审批功能已被废弃
    // 审批功能现在通过 DubboServiceDbService 和 ServiceApprovalService 处理
}