package com.littlenote.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.littlenote.backend.service.DatabaseHealthService;

class DatabaseControllerTest {

    @Test
    void dbTestEndpointReturnsConnectedStatus() throws Exception {
        DatabaseController controller = new DatabaseController(new TestDatabaseHealthService());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/db-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database").value("connected"));
    }

    private static class TestDatabaseHealthService extends DatabaseHealthService {
        private TestDatabaseHealthService() {
            super(null);
        }

        @Override
        public Map<String, String> testConnection() {
            return Map.of("database", "connected");
        }
    }
}
