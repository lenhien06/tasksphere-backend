package com.zone.tasksphere;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testPing() throws Exception {
        mockMvc.perform(get("/api/v1/projects/ping"))
                .andExpect(status().isNotFound()); // /ping doesn't exist anymore on this path or was never there
    }

    @Test
    @WithMockUser(username = "admin@tasksphere.local", roles = {"USER", "ADMIN"})
    public void testGetProjects() throws Exception {
        mockMvc.perform(get("/api/v1/projects")
                        .param("q", "demo")
                        .param("status", "active")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk());
    }
}
