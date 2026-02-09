package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DubboToMcpAutoRegistrationServiceTest {

    @Mock
    private ProviderService providerService;

    @Mock
    private NacosMcpRegistrationService nacosMcpRegistrationService;

    @Mock
    private ServiceCollectionFilterService filterService;

    @InjectMocks
    private DubboToMcpAutoRegistrationService autoRegistrationService;

    private Set<String> registeredServices;

    @BeforeEach
    void setUp() {
        // Access private field registeredServices using reflection
        registeredServices = ConcurrentHashMap.newKeySet();
        ReflectionTestUtils.setField(autoRegistrationService, "registeredServices", registeredServices);
    }

    @Test
    void testHandleProviderRemoved_WhenLastProviderRemoved_ShouldMarkOffline() {
        // Arrange
        String interfaceName = "com.example.DemoService";
        String version = "1.0.0";
        String group = "default";
        String serviceKey = interfaceName + ":" + version + ":" + group;

        // Pre-condition: Service is registered
        registeredServices.add(serviceKey);

        ProviderInfo removedProvider = new ProviderInfo();
        removedProvider.setInterfaceName(interfaceName);
        removedProvider.setVersion(version);
        removedProvider.setGroup(group);
        removedProvider.setAddress("192.168.1.1:20880");

        // Mock: No providers remaining for this service
        when(providerService.getAllProviders()).thenReturn(Collections.emptyList());

        // Act
        autoRegistrationService.handleProviderRemoved(removedProvider);

        // Assert
        // Verify that updateServiceStatus is called with online=false
        verify(nacosMcpRegistrationService).updateServiceStatus(
            eq(interfaceName),
            eq(version),
            eq(false)
        );
        
        // Ensure checkAndUpdateService is NOT called
        // (Since checkAndUpdateService calls registerDubboServiceAsMcp, we can verify that isn't called)
        verify(nacosMcpRegistrationService, never()).registerDubboServiceAsMcp(anyString(), anyString(), anyList());
    }

    @Test
    void testHandleProviderRemoved_WhenProvidersRemain_ShouldUpdateService() {
        // Arrange
        String interfaceName = "com.example.DemoService";
        String version = "1.0.0";
        String group = "default";
        String serviceKey = interfaceName + ":" + version + ":" + group;

        // Pre-condition: Service is registered
        registeredServices.add(serviceKey);

        ProviderInfo removedProvider = new ProviderInfo();
        removedProvider.setInterfaceName(interfaceName);
        removedProvider.setVersion(version);
        removedProvider.setGroup(group);
        removedProvider.setAddress("192.168.1.1:20880"); // Removed node

        ProviderInfo remainingProvider = new ProviderInfo();
        remainingProvider.setInterfaceName(interfaceName);
        remainingProvider.setVersion(version);
        remainingProvider.setGroup(group);
        remainingProvider.setAddress("192.168.1.2:20880"); // Remaining node
        remainingProvider.setOnline(true);

        // Mock: One provider remains
        when(providerService.getAllProviders()).thenReturn(Collections.singletonList(remainingProvider));

        // Act
        autoRegistrationService.handleProviderRemoved(removedProvider);

        // Assert
        // Verify that checkAndUpdateService calls registerDubboServiceAsMcp (update)
        verify(nacosMcpRegistrationService).registerDubboServiceAsMcp(
            eq(interfaceName),
            eq(version),
            anyList()
        );
        
        // Ensure updateServiceStatus (offline) is NOT called
        verify(nacosMcpRegistrationService, never()).updateServiceStatus(anyString(), anyString(), anyBoolean());
    }
}
