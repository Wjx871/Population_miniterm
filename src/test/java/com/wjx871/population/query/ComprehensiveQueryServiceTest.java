package com.wjx871.population.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.SensitiveDataMaskingService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class ComprehensiveQueryServiceTest {
    private final ComprehensiveQueryMapper mapper = mock(ComprehensiveQueryMapper.class);
    private final ComprehensiveQueryService service = new ComprehensiveQueryService(mapper, new SensitiveDataMaskingService());

    @BeforeEach
    void authenticate() {
        AuthenticatedUser user = new AuthenticatedUser(7L, "reviewer", "", "Reviewer", "ENABLED", 1L,
                "ADMIN", "Admin", null, DataScope.ALL, "ENABLED", 10L, "Test", "110000", List.of("population:view"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void searchTrimsFiltersMasksFieldsAndKeepsOnePersonRow() {
        ComprehensivePersonSummaryView row = new ComprehensivePersonSummaryView();
        row.setPersonId(101L);
        row.setMaskedIdentityNo("110101199901010011");
        row.setMaskedPhone("13800138000");
        row.setMaskedPermitNo("JZP-2026-0001");
        row.setCurrentAddress("Test address");
        when(mapper.countSummaries(eq("Alice"), eq("NORMAL"), any(), any(), any(), any(), any())).thenReturn(1L);
        when(mapper.selectSummaries(eq("Alice"), eq("NORMAL"), any(), any(), any(), any(), any(),
                eq("p.person_id"), eq("DESC"), eq(20), eq(0L))).thenReturn(List.of(row));

        var result = service.search("  Alice  ", " NORMAL ", "", "", "", "", 0, 20, "personId,DESC");

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getMaskedIdentityNo()).isEqualTo("1101**********0011");
        assertThat(result.getContent().get(0).getMaskedPhone()).isEqualTo("138****8000");
        assertThat(result.getContent().get(0).getMaskedPermitNo()).isEqualTo("JZP-****0001");
        verify(mapper).selectSummaries(eq("Alice"), eq("NORMAL"), eq(null), eq(null), eq(null), eq(null), any(),
                eq("p.person_id"), eq("DESC"), eq(20), eq(0L));
    }

    @Test
    void searchRejectsUnsupportedSortAndOversizedPage() {
        assertThatThrownBy(() -> service.search(null, null, null, null, null, null, 0, 101, "personId,DESC"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.search(null, null, null, null, null, null, 0, 20, "unknown,ASC"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
