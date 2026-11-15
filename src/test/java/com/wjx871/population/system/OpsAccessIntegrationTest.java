package com.wjx871.population.system;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpsAccessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void anonymousMetricsRequestIsDeniedAndStillTraced() throws Exception {
        mvc.perform(get("/api/ops/cache").header("X-Trace-Id", "auth-failure-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Trace-Id", "auth-failure-test"));
    }

    @Test void viewerCannotReadOperationsMetrics() throws Exception {
        mvc.perform(get("/api/ops/cache").header("Authorization", "Bearer " + token("viewer")))
                .andExpect(status().isForbidden());
    }
    @Test void brokerMonitoringRequiresOperationsPermission() throws Exception {
        mvc.perform(get("/api/ops/rabbitmq")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ops/rabbitmq").header("Authorization", "Bearer " + token("viewer")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ops/rabbitmq").header("Authorization", "Bearer " + token("admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test void routingMetricsAreProtectedAndDoNotExposeConnectionConfiguration() throws Exception {
        mvc.perform(get("/api/ops/database-routing")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ops/database-routing").header("Authorization", "Bearer " + token("viewer")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ops/database-routing").header("Authorization", "Bearer " + token("admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.primaryConnections").isNumber())
                .andExpect(jsonPath("$.data.password").doesNotExist()).andExpect(jsonPath("$.data.url").doesNotExist());
    }

    @Test void adminCanReadCountersAndInstanceScope() throws Exception {
        mvc.perform(get("/api/ops/cache").header("Authorization", "Bearer " + token("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("process_since_start"))
                .andExpect(jsonPath("$.data.counters.hits").isNumber())
                .andExpect(jsonPath("$.data.counters.readErrors").isNumber())
                .andExpect(header().string("X-Instance-Id", "local"));
    }
    @Test void runtimeMetricsRequireAdminAndExposeUnits() throws Exception {
        mvc.perform(get("/api/ops/runtime")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ops/runtime").header("Authorization", "Bearer " + token("viewer")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ops/runtime").header("Authorization", "Bearer " + token("admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.heapUsedBytes").isNumber())
                .andExpect(jsonPath("$.data.requests.totalRequests").isNumber())
                .andExpect(jsonPath("$.data.connectionPools.primary.maximum").isNumber());
    }

    private String token(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("username", username, "password", "123456"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("token").asText();
    }
}
