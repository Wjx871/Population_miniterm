package com.wjx871.population.s2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.s2.aggregation.PopulationMetricsCalculator;
import com.wjx871.population.s2.aggregation.PopulationMetricsClient;
import com.wjx871.population.s2.aggregation.PopulationMetricsRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "population.s2.remote.enabled=true")
@AutoConfigureMockMvc
@Transactional
class S2StatisticsApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private PopulationMetricsClient remoteClient;

    @Test
    void anonymousAndInsufficientPermissionRequestsNeverReachTheRemoteClient() throws Exception {
        mockMvc.perform(get("/api/s2/statistics/aggregate"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/s2/statistics/aggregate")
                        .header("Authorization", bearer(tokenFor("viewer"))))
                .andExpect(status().isForbidden());
        verify(remoteClient, never()).aggregate(any());
    }

    @Test
    void authorizedRequestUsesScopedStatisticsAndPropagatesTraceId() throws Exception {
        when(remoteClient.aggregate(any())).thenAnswer(invocation ->
                new PopulationMetricsCalculator().calculate(invocation.getArgument(0)));
        String traceId = "http-s2-trace-001";
        ensureStatisticsViewPermission();

        var response = mockMvc.perform(get("/api/s2/statistics/aggregate")
                        .header("X-Trace-Id", traceId)
                        .header("Authorization", bearer(tokenFor("admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.traceId").value(traceId))
                .andExpect(jsonPath("$.data.residentCount").value(0))
                .andReturn().getResponse();

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(traceId);
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.path("data").path("floatingRegistrationToResidentRatio").isNull()).isTrue();
        ArgumentCaptor<PopulationMetricsRequest> requestCaptor = ArgumentCaptor.forClass(PopulationMetricsRequest.class);
        verify(remoteClient).aggregate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getTraceId()).isEqualTo(traceId);
        assertThat(requestCaptor.getValue().getResidentCount()).isZero();
        assertThat(requestCaptor.getValue().getFloatingRegistrationCount()).isZero();
        assertThat(requestCaptor.getValue().getActiveKeyPopulationCount()).isZero();
        assertThat(requestCaptor.getValue().getActiveHouseholdCount()).isZero();
    }

    private void ensureStatisticsViewPermission() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_permission WHERE permission_code='statistics:view'", Integer.class);
        if (count == null || count == 0) {
            Long nextId = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(permission_id),0)+1 FROM sys_permission", Long.class);
            jdbcTemplate.update("INSERT INTO sys_permission(permission_id,permission_code,permission_name,module_name,permission_type,status) "
                            + "VALUES(?, 'statistics:view', '查看统计', 'STATISTICS', 'API', 'ENABLED')",
                    nextId);
        }
        jdbcTemplate.update("INSERT INTO sys_role_permission(role_id,permission_id) "
                        + "SELECT 3, permission_id FROM sys_permission WHERE permission_code='statistics:view' "
                        + "AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=3 AND rp.permission_id=sys_permission.permission_id)");
    }

    private String tokenFor(String username) throws Exception {
        String json = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).path("data").path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
