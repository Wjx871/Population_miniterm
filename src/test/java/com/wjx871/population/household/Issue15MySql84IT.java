package com.wjx871.population.household;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit opt-in MySQL 8.4 gate for Issue #15. */
class Issue15MySql84IT extends Issue15HouseholdDateBoundaryIntegrationTest {

    @Autowired private PlatformTransactionManager transactionManager;

    private static final Pattern URL = Pattern.compile(
            "^jdbc:mysql://127\\.0\\.0\\.1:(?<port>[0-9]+)/"
                    + "(?<schema>population_miniterm_mysql_gate_issue15_[a-z0-9_]+)\\?(?<options>.*)$");

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Set " + key + " for the isolated Issue15 MySQL gate");
        }
        return value;
    }

    private static Matcher guard() {
        Matcher match = URL.matcher(required("MYSQL84_GATE_DB_URL"));
        if (!match.matches() || "3306".equals(match.group("port"))
                || !Pattern.compile("(?:^|&)createDatabaseIfNotExist=false(?:&|$)")
                        .matcher(match.group("options")).find()
                || required("MYSQL84_GATE_DB_USERNAME").equalsIgnoreCase("root")
                || required("MYSQL84_GATE_DB_USERNAME").equals(required("MYSQL84_GATE_ADMIN_USERNAME"))) {
            throw new IllegalStateException(
                    "Issue15 gate requires a dedicated loopback schema, explicit non-3306 port and non-root app user");
        }
        return match;
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry properties) {
        guard();
        properties.add("spring.sql.init.mode", () -> "never");
        properties.add("spring.datasource.url", () -> required("MYSQL84_GATE_DB_URL"));
        properties.add("spring.datasource.username", () -> required("MYSQL84_GATE_DB_USERNAME"));
        properties.add("spring.datasource.password", () -> required("MYSQL84_GATE_DB_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.datasource.hikari.maximum-pool-size", () -> "12");
    }

    @BeforeEach
    void verifyMysqlIdentity() throws Exception {
        Matcher match = guard();
        try (Connection connection = DriverManager.getConnection(required("MYSQL84_GATE_DB_URL"),
                required("MYSQL84_GATE_DB_USERNAME"), required("MYSQL84_GATE_DB_PASSWORD"));
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT @@version,@@port,@@bind_address,@@transaction_isolation,DATABASE(),CURRENT_USER()")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).startsWith("8.4.");
            assertThat(result.getInt(2)).isEqualTo(Integer.parseInt(match.group("port")));
            assertThat(result.getString(3)).isEqualTo("127.0.0.1");
            assertThat(result.getString(4)).isEqualTo("REPEATABLE-READ");
            assertThat(result.getString(5)).isEqualTo(match.group("schema"));
            assertThat(result.getString(6)).startsWith(required("MYSQL84_GATE_DB_USERNAME") + "@");
            System.out.println("ISSUE15 MYSQL identity version=" + result.getString(1) + " port="
                    + result.getInt(2) + " isolation=" + result.getString(4) + " schema=" + result.getString(5)
                    + " user=" + result.getString(6));
        }
    }
    /**
     * Both participants run the real JWT/HTTP controller and service on their transaction thread.
     * The successful request keeps its REQUIRED transaction open until an independent admin
     * connection observes the other HTTP request waiting for that exact household record lock.
     */
    @ParameterizedTest(name = "{0}, updateFirst={1}")
    @CsvSource({"ADD, true", "ADD, false", "EXECUTE_IN, true", "EXECUTE_IN, false"})
    void concurrentDateWritesSerializeOnHousehold(String entry, boolean updateFirst) throws Exception {
        boolean migration = "EXECUTE_IN".equals(entry);
        long householdId = migration ? HOUSEHOLD : EMPTY_HOUSEHOLD;
        if (migration) {
            // The pre-existing head must not be the reason a later establishment date is refused.
            jdbc.update("UPDATE household_member SET join_date=? WHERE household_id=? AND person_id=?",
                    Date.valueOf("2026-02-01"), HOUSEHOLD, HEAD);
        }
        long applicationId = migration ? approvedMigration(CANDIDATE, "2026-01-01") : 0;
        int applicationVersion = migration ? migrationVersion(applicationId) : 0;
        Snapshot before = snapshot();
        Participant writer = new Participant(updateFirst ? "UPDATE" : entry);
        Participant waiter = new Participant(updateFirst ? entry : "UPDATE");
        AtomicReference<Snapshot> committedWinnerState = new AtomicReference<>();
        CountDownLatch waiterTransactionReady = new CountDownLatch(1);
        CountDownLatch writerHttpFinished = new CountDownLatch(1);
        CountDownLatch waiterHttpStarted = new CountDownLatch(1);
        CountDownLatch commitWriter = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<TxCompletion> first = null;
        Future<TxCompletion> second = null;
        LockWait observed;
        TxCompletion firstCompletion;
        TxCompletion secondCompletion;
        try {
            second = pool.submit(() -> httpTransaction(waiter, () -> {
                try {
                    if (!updateFirst) {
                        // A normal RR read precedes the writer INSERT. It still sees no candidate
                        // after the HTTP rejection; the boundary must therefore use a current read.
                        waiter.snapshotCountBefore = candidateMembershipCount(householdId);
                        assertThat(waiter.snapshotCountBefore).isZero();
                    }
                    waiterTransactionReady.countDown();
                    await(writerHttpFinished, "successful HTTP request to finish before commit");
                    waiterHttpStarted.countDown();
                    waiter.response.set(updateFirst
                            ? memberWrite(entry, householdId, applicationId, applicationVersion)
                            : updateHousehold(householdId, "2026-01-02", 0, "household"));
                    if (!updateFirst) {
                        waiter.snapshotCountAfter = candidateMembershipCount(householdId);
                    }
                } finally {
                    waiterTransactionReady.countDown();
                    waiterHttpStarted.countDown();
                }
            }));
            await(waiterTransactionReady, "second Spring transaction and old RR snapshot");
            first = pool.submit(() -> httpTransaction(writer, () -> {
                try {
                    writer.response.set(updateFirst
                            ? updateHousehold(householdId, "2026-01-02", 0, "household")
                            : memberWrite(entry, householdId, applicationId, applicationVersion));
                    assertHttp(writer.response.get(), updateFirst || migration ? 200 : 201);
                    committedWinnerState.set(snapshot());
                } finally {
                    writerHttpFinished.countDown();
                }
                await(commitWriter, "admin-observed household lock wait before writer commit");
            }));
            await(writerHttpFinished, "first HTTP response while its transaction is still open");
            assertThat(writer.response.get()).as("writer HTTP reached its business service").isNotNull();
            assertHttp(writer.response.get(), updateFirst || migration ? 200 : 201);
            assertThat(committedWinnerState.get()).isNotNull();
            await(waiterHttpStarted, "second HTTP request to enter its business transaction");
            assertThat(writer.identity.get()).isNotNull();
            assertThat(waiter.identity.get()).isNotNull();
            assertThat(writer.identity.get().connectionId()).isNotEqualTo(waiter.identity.get().connectionId());
            observed = awaitHouseholdLockWait(writer, waiter, householdId, first, second);
            assertThat(first.isDone()).as("writer transaction remains open during observed wait").isFalse();
            assertThat(second.isDone()).as("second HTTP is blocked in its actual service transaction").isFalse();
            commitWriter.countDown();
            firstCompletion = first.get(20, TimeUnit.SECONDS);
            secondCompletion = second.get(20, TimeUnit.SECONDS);
        } finally {
            commitWriter.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS))
                    .as("bounded HTTP transaction worker termination").isTrue();
        }

        Snapshot after = snapshot();
        int invalidRows = jdbc.queryForObject("SELECT COUNT(*) FROM household_member m "
                + "JOIN household h ON h.household_id=m.household_id WHERE m.join_date<h.establish_date",
                Integer.class);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("case", entry + (updateFirst ? "-update-before-member" : "-member-before-update-old-RR-snapshot"));
        evidence.put("mysqlVersion", jdbc.queryForObject("SELECT @@version", String.class));
        evidence.put("writer", participantEvidence(writer, firstCompletion));
        evidence.put("waiter", participantEvidence(waiter, secondCompletion));
        evidence.put("lockWait", observed);
        evidence.put("householdVersion", householdVersion(householdId));
        evidence.put("establishDate", establishDate(householdId));
        evidence.put("candidateJoinDate", updateFirst ? null : memberDate(CANDIDATE));
        evidence.put("candidateMemberVersion", updateFirst ? null : memberVersion(CANDIDATE));
        evidence.put("invalidMembershipRows", invalidRows);
        evidence.put("failedRequestHasNoPartialBusinessWrites", after.rows().equals(committedWinnerState.get().rows()));
        evidence.put("failedRequestHasNoSuccessfulAudit",
                after.successfulOperations().equals(committedWinnerState.get().successfulOperations()));
        evidence.put("before", before);
        evidence.put("winnerUncommittedState", committedWinnerState.get());
        evidence.put("after", after);
        System.out.println("ISSUE15_CONCURRENCY " + json.writeValueAsString(evidence));

        assertAll(
                () -> assertHttp(writer.response.get(), updateFirst || migration ? 200 : 201),
                () -> assertHttp(waiter.response.get(), 400),
                () -> assertThat(firstCompletion.rolledBack()).as("successful writer committed").isFalse(),
                () -> assertThat(after.rows()).as("only successful writer business rows and versions committed")
                        .isEqualTo(committedWinnerState.get().rows()),
                () -> assertThat(after.successfulOperations()).as("failed request has no successful operation log")
                        .isEqualTo(committedWinnerState.get().successfulOperations()),
                () -> assertThat(invalidRows).as("join_date >= establish_date for every persisted member").isZero(),
                () -> assertThat(establishDate(householdId))
                        .isEqualTo(Date.valueOf(updateFirst ? "2026-01-02" : "2026-01-01")),
                () -> assertThat(householdVersion(householdId)).isEqualTo(updateFirst ? 1 : 0),
                () -> assertThat(successOperations("HOUSEHOLD_UPDATE")).isEqualTo(updateFirst ? 1 : 0),
                () -> assertThat(successOperations(migration ? "MIGRATION_IN_EXECUTE" : "HOUSEHOLD_MEMBER_ADD"))
                        .isEqualTo(updateFirst ? 0 : 1));
        if (updateFirst) {
            assertThat(candidateMembershipCount(householdId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id=?",
                    Integer.class, CANDIDATE)).isZero();
        } else {
            assertAll(
                    () -> assertThat(waiter.snapshotCountBefore).as("RR read view predates writer INSERT").isZero(),
                    () -> assertThat(waiter.snapshotCountAfter).as("same RR snapshot remains old after HTTP rejection").isZero(),
                    () -> assertThat(candidateMembershipCount(householdId)).as("writer membership really committed").isOne(),
                    () -> assertThat(memberDate(CANDIDATE)).isEqualTo(Date.valueOf("2026-01-01")),
                    () -> assertThat(memberVersion(CANDIDATE)).isZero());
        }
        if (migration) {
            assertAll(
                    () -> assertThat(migrationVersion(applicationId)).isEqualTo(applicationVersion + (updateFirst ? 0 : 1)),
                    () -> assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?",
                            String.class, applicationId)).isEqualTo(updateFirst ? "APPROVED" : "COMPLETED"),
                    () -> assertThat(jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?",
                            String.class, applicationId)).isEqualTo(updateFirst ? "APPROVED" : "COMPLETED"),
                    () -> assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_log "
                            + "WHERE application_id=? AND action='EXECUTE'", Integer.class, applicationId))
                            .isEqualTo(updateFirst ? 0 : 1));
            if (!updateFirst) {
                assertSuccessfulMigration(writer.response.get(), applicationId, CANDIDATE,
                        "2026-01-01", applicationVersion, 0);
            }
        }
    }

    private MvcResult memberWrite(String entry, long householdId, long applicationId, int applicationVersion)
            throws Exception {
        return "ADD".equals(entry) ? addMember(householdId, CANDIDATE, "2026-01-01", "household")
                : executeIn(applicationId, applicationVersion, "household");
    }

    private long candidateMembershipCount(long householdId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND person_id=?",
                Long.class, householdId, CANDIDATE);
    }

    private TxCompletion httpTransaction(Participant participant, HttpWork work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(35);
        try {
            transaction.executeWithoutResult(status -> {
                jdbc.execute("SET SESSION innodb_lock_wait_timeout=15");
                participant.identity.set(jdbc.queryForObject(
                        "SELECT CONNECTION_ID(),@@transaction_isolation,@@autocommit",
                        (rs, row) -> new TxIdentity(rs.getLong(1), rs.getString(2), rs.getBoolean(3),
                                TransactionSynchronizationManager.isActualTransactionActive())));
                assertThat(participant.identity.get().isolation()).isEqualTo("REPEATABLE-READ");
                assertThat(participant.identity.get().autoCommit()).isFalse();
                assertThat(participant.identity.get().springTransactionActive()).isTrue();
                try {
                    work.run();
                } catch (Exception failure) {
                    throw new IllegalStateException("HTTP transaction " + participant.role + " failed", failure);
                }
            });
            return new TxCompletion(false);
        } catch (UnexpectedRollbackException rollback) {
            // The real service exception is translated to HTTP 400 by MVC, while the joined
            // REQUIRED transaction still correctly rolls back when this outer boundary exits.
            assertThat(participant.response.get()).as("resolved HTTP business rejection before rollback").isNotNull();
            assertThat(participant.response.get().getResponse().getStatus()).isGreaterThanOrEqualTo(400);
            return new TxCompletion(true);
        }
    }

    private LockWait awaitHouseholdLockWait(Participant writer, Participant waiter, long householdId,
            Future<TxCompletion> first, Future<TxCompletion> second) throws Exception {
        String sql = "SELECT waiting.PROCESSLIST_ID,blocking.PROCESSLIST_ID,requested.OBJECT_NAME,"
                + "requested.INDEX_NAME,requested.LOCK_TYPE,requested.LOCK_MODE,requested.LOCK_DATA "
                + "FROM performance_schema.data_lock_waits waits "
                + "JOIN performance_schema.threads waiting ON waiting.THREAD_ID=waits.REQUESTING_THREAD_ID "
                + "JOIN performance_schema.threads blocking ON blocking.THREAD_ID=waits.BLOCKING_THREAD_ID "
                + "JOIN performance_schema.data_locks requested ON requested.ENGINE=waits.ENGINE "
                + "AND requested.ENGINE_LOCK_ID=waits.REQUESTING_ENGINE_LOCK_ID "
                + "WHERE requested.OBJECT_SCHEMA=? AND requested.OBJECT_NAME='household' "
                + "AND waiting.PROCESSLIST_ID=? AND blocking.PROCESSLIST_ID=?";
        try (Connection admin = DriverManager.getConnection(required("MYSQL84_GATE_DB_URL"),
                required("MYSQL84_GATE_ADMIN_USERNAME"), required("MYSQL84_GATE_ADMIN_PASSWORD"));
                PreparedStatement statement = admin.prepareStatement(sql)) {
            statement.setQueryTimeout(3);
            statement.setString(1, guard().group("schema"));
            statement.setLong(2, waiter.identity.get().connectionId());
            statement.setLong(3, writer.identity.get().connectionId());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        LockWait wait = new LockWait(result.getLong(1), result.getLong(2), result.getString(3),
                                result.getString(4), result.getString(5), result.getString(6), result.getString(7));
                        assertThat(wait.table()).isEqualTo("household");
                        assertThat(wait.index()).isEqualTo("PRIMARY");
                        assertThat(wait.type()).isEqualTo("RECORD");
                        assertThat(wait.data()).isEqualTo(String.valueOf(householdId));
                        return wait;
                    }
                }
                if (first.isDone()) {
                    first.get(1, TimeUnit.SECONDS);
                    throw new AssertionError("Writer transaction ended before the household lock wait was observed");
                }
                if (second.isDone()) {
                    second.get(1, TimeUnit.SECONDS);
                    throw new AssertionError("Second HTTP returned without waiting for the open writer transaction");
                }
                Thread.sleep(25); // Poll pacing only; the observed database wait is the synchronization proof.
            }
        }
        throw new AssertionError("No exact household InnoDB lock wait observed between the two HTTP transaction connections");
    }

    private Map<String, Object> participantEvidence(Participant participant, TxCompletion completion) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", participant.role);
        result.put("transaction", participant.identity.get());
        result.put("httpStatus", participant.response.get().getResponse().getStatus());
        result.put("response", json.readTree(participant.response.get().getResponse().getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8)));
        result.put("rolledBack", completion.rolledBack());
        result.put("oldSnapshotCandidateCountBefore", participant.snapshotCountBefore);
        result.put("oldSnapshotCandidateCountAfter", participant.snapshotCountAfter);
        return result;
    }

    private static void await(CountDownLatch latch, String description) throws InterruptedException {
        assertThat(latch.await(25, TimeUnit.SECONDS)).as(description).isTrue();
    }

    @FunctionalInterface
    private interface HttpWork { void run() throws Exception; }

    private static final class Participant {
        private final String role;
        private final AtomicReference<TxIdentity> identity = new AtomicReference<>();
        private final AtomicReference<MvcResult> response = new AtomicReference<>();
        private volatile Long snapshotCountBefore;
        private volatile Long snapshotCountAfter;
        private Participant(String role) { this.role = role; }
    }

    private record TxIdentity(long connectionId, String isolation, boolean autoCommit,
            boolean springTransactionActive) {}
    private record TxCompletion(boolean rolledBack) {}
    private record LockWait(long waitingConnectionId, long blockingConnectionId, String table, String index,
            String type, String mode, String data) {}
}
