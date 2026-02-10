package com.pajk.mcpmetainfo.core.controller;

import com.pajk.mcpmetainfo.core.model.Project;
import com.pajk.mcpmetainfo.core.model.ProjectService;
import com.pajk.mcpmetainfo.core.model.VirtualProjectEndpoint;
import com.pajk.mcpmetainfo.core.service.VirtualProjectRegistrationService;
import com.pajk.mcpmetainfo.core.service.VirtualProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VirtualProjectController.class)
public class VirtualProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VirtualProjectService virtualProjectService;

    @MockBean
    private VirtualProjectRegistrationService registrationService;

    @Autowired
    private ObjectMapper objectMapper;

    private VirtualProjectService.VirtualProjectInfo mockProjectInfo;

    @BeforeEach
    void setUp() {
        Project mockProject = new Project();
        mockProject.setId(1L);
        mockProject.setProjectName("test-project");

        VirtualProjectEndpoint mockEndpoint = new VirtualProjectEndpoint();
        mockEndpoint.setEndpointName("test-endpoint");
        mockEndpoint.setEndpointPath("/sse/test");
        // mockEndpoint.setPort(8080); // Port field does not exist in VirtualProjectEndpoint

        mockProjectInfo = new VirtualProjectService.VirtualProjectInfo();
        mockProjectInfo.setProject(mockProject);
        mockProjectInfo.setEndpoint(mockEndpoint);
        mockProjectInfo.setServices(Collections.emptyList());

        // Mock getting all projects to return our test project
        given(virtualProjectService.getAllVirtualProjects())
                .willReturn(List.of(mockProjectInfo));
    }

    @Test
    void testGetProjectByEndpointName() throws Exception {
        mockMvc.perform(get("/api/virtual-projects/test-endpoint"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.name").value("test-project"))
                .andExpect(jsonPath("$.endpoint.endpointName").value("test-endpoint"));
    }

    @Test
    void testGetServicesByEndpointName() throws Exception {
        mockMvc.perform(get("/api/virtual-projects/test-endpoint/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray()); // Expect empty list as set in setUp
    }

    @Test
    void testUpdateServicesByEndpointName() throws Exception {
        VirtualProjectController.UpdateServicesRequest request = new VirtualProjectController.UpdateServicesRequest();
        request.setServices(Collections.emptyList());

        mockMvc.perform(put("/api/virtual-projects/test-endpoint/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.endpointName").value("test-endpoint"));
    }

    @Test
    void testDeleteProjectByEndpointName() throws Exception {
        given(virtualProjectService.deleteVirtualProjectByEndpointName("test-endpoint"))
                .willReturn(true);

        mockMvc.perform(delete("/api/virtual-projects/test-endpoint"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("虚拟项目删除成功"))
                .andExpect(jsonPath("$.deletedFromNacos").value(true));
    }

    @Test
    void testNotFound() throws Exception {
        mockMvc.perform(get("/api/virtual-projects/non-existent-endpoint"))
                .andExpect(status().isNotFound());
    }
}
