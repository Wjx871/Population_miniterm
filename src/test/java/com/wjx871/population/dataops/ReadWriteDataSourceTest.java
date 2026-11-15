package com.wjx871.population.dataops;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;

@Tag("unit")
class ReadWriteDataSourceTest {
    private final DataSource primary = mock(DataSource.class);
    private final DataSource replica = mock(DataSource.class);
    private final Connection primaryConnection = mock(Connection.class);
    @AfterEach void cleanup() { ReadRouteContext.restore(null); }
    @Test void ordinaryConnectionUsesPrimaryEvenWithReplicaConfigured() throws Exception {
        when(primary.getConnection()).thenReturn(primaryConnection);
        assertThat(new ReadWriteDataSource(primary, replica, 5000).getConnection()).isSameAs(primaryConnection);
        verifyNoInteractions(replica);
    }
    @Test void connectionFailureFallsBackAndCooldownAvoidsRepeatedTimeouts() throws Exception {
        when(primary.getConnection()).thenReturn(primaryConnection);
        when(replica.getConnection()).thenThrow(new SQLException("private connection detail", "08001"));
        var routing = new ReadWriteDataSource(primary, replica, 60000);
        ReadRouteContext.set(true);
        assertThat(routing.getConnection()).isSameAs(primaryConnection);
        assertThat(routing.getConnection()).isSameAs(primaryConnection);
        verify(replica, times(1)).getConnection();
        assertThat(routing.snapshot().fallbacks()).isEqualTo(2);
    }
    @Test void successfulReplicaConnectionIsNotReplayedOnPrimary() throws Exception {
        Connection connection = mock(Connection.class);
        when(replica.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenThrow(new SQLException("query failed"));
        ReadRouteContext.set(true);
        var routing = new ReadWriteDataSource(primary, replica, 0);
        assertThatThrownBy(() -> routing.getConnection().createStatement()).isInstanceOf(SQLException.class);
        verifyNoInteractions(primary);
    }
}
