package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import com.pajk.mcpmetainfo.persistence.entity.ProviderInfoEntity;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.GetChildrenBuilder;
import org.apache.curator.framework.api.GetDataBuilder;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ZooKeeperServiceTest {

    @Mock
    private CuratorFramework curatorFramework;

    @Mock
    private ProviderService providerService;

    @Mock
    private ProviderInfoDbService providerInfoDbService;

    @Mock
    private GetChildrenBuilder getChildrenBuilder;

    @Mock
    private GetDataBuilder getDataBuilder;

    @InjectMocks
    private ZooKeeperService zooKeeperService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 使用反射设置私有字段
        ReflectionTestUtils.setField(zooKeeperService, "client", curatorFramework);
        ReflectionTestUtils.setField(zooKeeperService, "providerService", providerService);
        ReflectionTestUtils.setField(zooKeeperService, "providerInfoDbService", providerInfoDbService);
        ReflectionTestUtils.setField(zooKeeperService, "filterApprovedOnly", false);
    }

    @Test
    void testLoadExistingProviders() throws Exception {
        // 设置mock行为
        when(curatorFramework.getChildren()).thenReturn(getChildrenBuilder);
        when(getChildrenBuilder.forPath("/dubbo/com.example.Service1/providers")).thenReturn(Arrays.asList("provider1"));
        
        when(curatorFramework.getData()).thenReturn(getDataBuilder);
        when(getDataBuilder.forPath("/dubbo/com.example.Service1/providers/provider1")).thenReturn("dubbo://127.0.0.1:20880/com.example.Service1".getBytes());
        
        when(providerInfoDbService.findByZkPathAndApprovalStatus(anyString(), eq(ProviderInfoEntity.ApprovalStatus.APPROVED))).thenReturn(Optional.empty());

        // 执行测试
        ReflectionTestUtils.invokeMethod(zooKeeperService, "loadExistingProviders", "/dubbo/com.example.Service1/providers", "com.example.Service1");

        // 验证方法调用
        verify(providerService, times(1)).addProvider(any(ProviderInfo.class));
        verify(providerInfoDbService, times(1)).saveOrUpdateProvider(any(ProviderInfo.class));
    }

    @Test
    void testHandleProviderAdded_ApprovedOnlyFilterEnabled() throws Exception {
        // 设置filterApprovedOnly为true
        ReflectionTestUtils.setField(zooKeeperService, "filterApprovedOnly", true);
        
        // 准备测试数据
        ChildData childData = mock(ChildData.class);
        
        // 设置mock行为
        when(childData.getPath()).thenReturn("/dubbo/com.example.Service/providers/provider1");
        when(providerInfoDbService.findByZkPathAndApprovalStatus(anyString(), eq(ProviderInfoEntity.ApprovalStatus.APPROVED))).thenReturn(Optional.of(new ProviderInfoEntity()));

        // 执行测试
        ReflectionTestUtils.invokeMethod(zooKeeperService, "handleProviderAdded", childData, "com.example.Service");

        // 验证方法调用
        verify(providerService, times(1)).addProvider(any(ProviderInfo.class));
        verify(providerInfoDbService, times(1)).saveOrUpdateProvider(any(ProviderInfo.class));
    }

    @Test
    void testHandleProviderAdded_ApprovedOnlyFilterDisabled() throws Exception {
        // 准备测试数据
        ChildData childData = mock(ChildData.class);
        
        // 设置mock行为
        when(childData.getPath()).thenReturn("/dubbo/com.example.Service/providers/provider1");

        // 执行测试
        ReflectionTestUtils.invokeMethod(zooKeeperService, "handleProviderAdded", childData, "com.example.Service");

        // 验证方法调用
        verify(providerService, times(1)).addProvider(any(ProviderInfo.class));
        verify(providerInfoDbService, times(1)).saveOrUpdateProvider(any(ProviderInfo.class));
    }
}