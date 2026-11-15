package com.wjx871.population.security;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RevocationFailureIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean JdbcTokenRevocationStore store;
    @Test void unavailableRevocationStoreRejectsAuthenticatedRequestWith503() throws Exception {
        when(store.isRevoked(anyString())).thenThrow(new DataAccessResourceFailureException("private database detail"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("身份令牌撤销校验暂不可用，请稍后重试"));
    }
    @Test void failedRevocationWriteCannotReturnLogoutSuccess() throws Exception {
        doThrow(new DataAccessResourceFailureException("private database detail")).when(store).revoke(anyString(), any());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token()))
                .andExpect(status().isServiceUnavailable());
    }
    private String token() {
        return jwt.createToken(new AuthenticatedUser(1L, "admin", "", "Fixture", "ENABLED", 1L,
                "SYSTEM_ADMIN", "Admin", RoleLevel.L3, DataScope.ALL, "ENABLED", 1L, "System", "110000", List.of()));
    }
}
