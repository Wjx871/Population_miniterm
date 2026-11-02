package com.wjx871.population.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.wjx871.population.audit.OperationLogService;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.UncategorizedSQLException;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerDeadlockTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(mock(OperationLogService.class));

    @Test
    void confirmedMysqlDeadlockMapsToSafeConflict() {
        SQLException deadlock = new SQLException("private SQL and schema detail", "40001", 1213);
        var response = handler.handleUnexpected(new UncategorizedSQLException("transaction failed", "update ...", deadlock));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo(409);
        assertThat(response.getBody().message()).isEqualTo("业务数据正在被并发修改，请刷新后重试");
        assertThat(response.getBody().message()).doesNotContain("private SQL", "schema");
    }

    @Test
    void onlyExactMysql1213And40001PairIsMappedAsConflict() {
        var lockTimeout = handler.handleUnexpected(new SQLException("lock timeout", "HY000", 1205));
        var serializationOnly = handler.handleUnexpected(new SQLException("other serialization error", "40001", 0));
        var deadlockCodeWithDifferentState = handler.handleUnexpected(new SQLException("different SQL state", "HY000", 1213));

        assertThat(lockTimeout.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(serializationOnly.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(deadlockCodeWithDifferentState.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
