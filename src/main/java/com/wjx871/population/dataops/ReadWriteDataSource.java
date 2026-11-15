package com.wjx871.population.dataops;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Connection acquisition fallback only: never replays a SQL statement. */
@Log4j2
public class ReadWriteDataSource extends AbstractDataSource {
    private final DataSource primary;
    private final DataSource replica;
    private final long cooldownNanos;
    private final AtomicLong retryAfter = new AtomicLong();
    private final LongAdder primaryConnections = new LongAdder();
    private final LongAdder replicaConnections = new LongAdder();
    private final LongAdder fallbacks = new LongAdder();

    public ReadWriteDataSource(DataSource primary, DataSource replica, long cooldownMs) {
        this.primary = primary;
        this.replica = replica;
        this.cooldownNanos = Math.multiplyExact(Math.max(0, cooldownMs), 1_000_000L);
    }

    @Override public Connection getConnection() throws SQLException { return acquire(null, null, false); }
    @Override public Connection getConnection(String user, String password) throws SQLException {
        return acquire(user, password, true);
    }
    private Connection acquire(String user, String password, boolean supplied) throws SQLException {
        if (ReadRouteContext.replica() && replica != null) {
            if (retryAfter.get() == 0 || System.nanoTime() >= retryAfter.get()) {
                try {
                    Connection connection = supplied ? replica.getConnection(user, password) : replica.getConnection();
                    replicaConnections.increment();
                    return connection;
                } catch (SQLException ex) {
                    retryAfter.set(System.nanoTime() + cooldownNanos);
                    // Never log connection strings, credentials, or driver exception messages.
                    log.warn("replica_connection_failed action=primary_fallback sqlState={}", ex.getSQLState());
                }
            }
            fallbacks.increment();
        }
        primaryConnections.increment();
        return supplied ? primary.getConnection(user, password) : primary.getConnection();
    }
    public RouteSnapshot snapshot() {
        return new RouteSnapshot(replica != null, primaryConnections.sum(), replicaConnections.sum(), fallbacks.sum());
    }
    public record RouteSnapshot(boolean replicaEnabled, long primaryConnections, long replicaConnections, long fallbacks) {}
}
