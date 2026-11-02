package com.wjx871.population.mysqlgate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.floating.FloatingResidenceTransactionHook;

/**
 * Explicit MySQL 8.4 gate. This class is intentionally named *IT so the normal H2
 * `mvn clean test` suite never connects to an external or developer database.
 */
@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.hikari.maximum-pool-size=12",
        "population.redis.enabled=false",
        "policy.ai.enabled=false"
})
@AutoConfigureMockMvc
class MySql84CriticalAcceptanceIT {
    private static final Pattern JDBC_URL = Pattern.compile(
            "^jdbc:mysql://127\\.0\\.0\\.1:(?<port>[0-9]+)/(?<database>[A-Za-z0-9_]+)\\?(?<options>.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TEST_DATABASE = Pattern.compile(
            "population_miniterm_mysql_gate_[a-z0-9_]+", Pattern.CASE_INSENSITIVE);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @MockBean FloatingResidenceTransactionHook floatingHook;

    private static GateSettings settings() {
        String url = required("MYSQL84_GATE_DB_URL");
        String appUser = required("MYSQL84_GATE_DB_USERNAME");
        String appPassword = required("MYSQL84_GATE_DB_PASSWORD");
        String adminUser = required("MYSQL84_GATE_ADMIN_USERNAME");
        String adminPassword = required("MYSQL84_GATE_ADMIN_PASSWORD");
        String apiTestPassword = required("MYSQL84_GATE_API_TEST_PASSWORD");
        Matcher match = JDBC_URL.matcher(url);
        if (!match.matches()) throw new IllegalStateException("MYSQL84_GATE_DB_URL must use 127.0.0.1 and an explicit port/database.");
        int port = Integer.parseInt(match.group("port"));
        String database = match.group("database");
        if (port == 3306) throw new IllegalStateException("The MySQL gate refuses the default/course MySQL port 3306.");
        if (!TEST_DATABASE.matcher(database).matches() || database.equalsIgnoreCase("population_miniterm")) {
            throw new IllegalStateException("The MySQL gate requires a named population_miniterm_mysql_gate_* test schema.");
        }
        if (!Pattern.compile("(?:^|&)createDatabaseIfNotExist=false(?:&|$)", Pattern.CASE_INSENSITIVE)
                .matcher(match.group("options")).find()) {
            throw new IllegalStateException("MYSQL84_GATE_DB_URL must set createDatabaseIfNotExist=false.");
        }
        if (appUser.equalsIgnoreCase("root")) throw new IllegalStateException("The application test user must be non-root.");
        if (adminUser.equalsIgnoreCase(appUser)) throw new IllegalStateException("Use a separate isolated-instance admin account for trigger setup.");
        return new GateSettings(url, appUser, appPassword, adminUser, adminPassword, apiTestPassword, port, database);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set " + name + " in the current process environment.");
        return value;
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry properties) {
        GateSettings gate = settings();
        properties.add("spring.datasource.url", gate::url);
        properties.add("spring.datasource.username", gate::appUser);
        properties.add("spring.datasource.password", gate::appPassword);
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @org.junit.jupiter.api.BeforeEach
    void verifyDedicatedMysqlIdentity() {
        org.mockito.Mockito.reset(floatingHook);
        GateSettings gate = settings();
        Map<String, Object> identity = jdbc.queryForMap(
                "SELECT @@version version, @@port port, @@bind_address bind_address, " +
                "@@transaction_isolation isolation_level, DATABASE() database_name, CURRENT_USER() database_user");
        String version = String.valueOf(identity.get("version"));
        assertThat(version).startsWith("8.4.");
        assertThat(((Number) identity.get("port")).intValue()).isEqualTo(gate.port());
        assertThat(String.valueOf(identity.get("bind_address"))).isEqualTo("127.0.0.1");
        assertThat(identity.get("database_name")).isEqualTo(gate.database());
        assertThat(String.valueOf(identity.get("database_user"))).startsWith(gate.appUser() + "@");
        System.out.printf("MYSQL_GATE_INSTANCE version=%s port=%s database=%s isolation=%s user=%s%n",
                version, identity.get("port"), identity.get("database_name"),
                identity.get("isolation_level"), identity.get("database_user"));
        String testPasswordHash = passwordEncoder.encode(settings().apiTestPassword());
        assertThat(jdbc.update("UPDATE sys_user SET password_hash=? WHERE username IN('household','approver')",
                testPasswordHash)).isEqualTo(2);
    }

    @Test
    void freshVerifierAllowsLegitimateHeadlessHouseholdsAndDetectsBrokenHeadRelations() throws Exception {
        long emptyHeadless = createHousehold(null);
        long ordinaryMember = createPerson("MIGRATED_OUT");
        long populatedHeadless = createHousehold(null);
        addMember(populatedHeadless, ordinaryMember, "CHILD");
        long validHead = createPerson("REGISTERED");
        long headedHousehold = createHousehold(validHead);
        addMember(headedHousehold, validHead, "HEAD");

        assertThat(invalidHouseholdHeadCount()).isZero();

        long duplicateHead = createPerson("REGISTERED");
        addMember(headedHousehold, duplicateHead, "HEAD");
        assertThat(invalidHouseholdHeadCount()).isOne();

        long mismatchPerson = createPerson("REGISTERED");
        long mismatchedHousehold = createHousehold(mismatchPerson);
        addMember(mismatchedHousehold, mismatchPerson, "CHILD");
        assertThat(invalidHouseholdHeadCount()).isEqualTo(2);

        jdbc.update("DELETE FROM household_member WHERE household_id IN (?,?,?,?)",
                emptyHeadless, populatedHeadless, headedHousehold, mismatchedHousehold);
        jdbc.update("DELETE FROM household WHERE household_id IN (?,?,?,?)",
                emptyHeadless, populatedHeadless, headedHousehold, mismatchedHousehold);
        jdbc.update("DELETE FROM person WHERE person_id IN (?,?,?,?)", ordinaryMember, validHead, duplicateHead, mismatchPerson);
        assertThat(invalidHouseholdHeadCount()).isZero();
        System.out.println("PASS verifier contract: headless empty/populated households accepted; broken head pointer detected.");
    }

    @Test
    void headHandoverThenFormerHeadReturnKeepsOneActiveHead() throws Exception {
        long formerHead = createPerson("REGISTERED");
        long successor = createPerson("REGISTERED");
        long household = createHousehold(formerHead);
        addMember(household, formerHead, "HEAD");
        addMember(household, successor, "CHILD");
        addResidence(household, formerHead);
        addResidence(household, successor);

        long out = createApprovedOut(formerHead, successor);
        execute("/api/migrations/out/applications/" + out + "/execute", 200,
                token("household"), migrationVersion(out, "migration_out"));
        assertThat(jdbc.queryForObject("SELECT status FROM household_member WHERE household_id=? AND person_id=?",
                String.class, household, formerHead)).isEqualTo("LEFT");
        assertThat(jdbc.queryForObject("SELECT relationship FROM household_member WHERE household_id=? AND person_id=?",
                String.class, household, formerHead)).isEqualTo("HEAD");
        assertHeadInvariant(household, successor, 1);

        long inbound = createApprovedIn(formerHead, household);
        execute("/api/migrations/in/applications/" + inbound + "/execute", 200,
                token("household"), migrationVersion(inbound, "migration_in"));
        assertHeadInvariant(household, successor, 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND person_id=? AND status='ACTIVE' AND relationship<>'HEAD'",
                Integer.class, household, formerHead)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE household_id=? AND person_id=? AND status='ACTIVE'",
                Integer.class, household, formerHead)).isOne();
        assertApplicationCompleted(out);
        assertApplicationCompleted(inbound);
        System.out.println("PASS migration A/B: successor remains the sole ACTIVE HEAD after former head returns.");
    }

    @Test
    void lastMemberLeavesHeadlessHouseholdAndHouseholdCancellationCompletes() throws Exception {
        long member = createPerson("REGISTERED");
        long household = createHousehold(null);
        addMember(household, member, "CHILD");
        addResidence(household, member);

        long out = createApprovedOut(member, null);
        execute("/api/migrations/out/applications/" + out + "/execute", 200,
                token("household"), migrationVersion(out, "migration_out"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND status='ACTIVE'",
                Integer.class, household)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE household_id=? AND status='ACTIVE'",
                Integer.class, household)).isZero();
        assertThat(jdbc.queryForObject("SELECT head_person_id FROM household WHERE household_id=?",
                Long.class, household)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM household WHERE household_id=?",
                String.class, household)).isEqualTo("PENDING_CANCELLATION");

        long cancellation = createHouseholdCancellation(household);
        insertMaterials(cancellation, List.of("CANCELLATION_APPLICATION", "HOUSEHOLD_BOOK", "HOUSEHOLD_CANCELLATION_PROOF"));
        submitAndApprove(cancellation);
        execute("/api/cancellations/household/applications/" + cancellation + "/execute", 200,
                token("household"), cancellationVersion(cancellation));
        assertThat(jdbc.queryForObject("SELECT status FROM household WHERE household_id=?", String.class, household))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_archive WHERE application_id=?", Integer.class, cancellation))
                .isOne();
        assertApplicationCompleted(out);
        assertApplicationCompleted(cancellation);
        System.out.println("PASS headless household: final member out transitioned to PENDING_CANCELLATION and cancellation archived it.");
    }

    @Test
    void closeThenEndorseLocksFloatingBeforePermitWithoutDeadlock() throws Exception {
        GateSettings gate = settings();
        enableStatementErrorHistory(gate);
        String closeToken = "Bearer " + token("household");
        String endorseToken = "Bearer " + token("household");
        for (int run = 1; run <= 10; run++) {
            EndorseScenario scenario = createApprovedEndorsementScenario();
            long deadlocksBefore = mysqlErrorCount(gate, 1213);
            CountDownLatch closeHoldsFloating = new CountDownLatch(1);
            CountDownLatch endorseApplicationLocked = new CountDownLatch(1);
            CountDownLatch releaseClose = new CountDownLatch(1);
            AtomicLong closeConnectionId = new AtomicLong();
            AtomicLong endorseConnectionId = new AtomicLong();
            org.mockito.Mockito.doAnswer(invocation -> {
                closeConnectionId.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                closeHoldsFloating.countDown();
                if (!releaseClose.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("close barrier timed out");
                return null;
            }).when(floatingHook).afterFloatingClose();
            org.mockito.Mockito.doAnswer(invocation -> {
                endorseConnectionId.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                endorseApplicationLocked.countDown();
                return null;
            }).when(floatingHook).afterPermitApplicationLock();

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<MvcResult> closeFuture = pool.submit(() -> mvc.perform(post("/api/floating-populations/" + scenario.floatingId() + "/close")
                        .header("Authorization", closeToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"LEFT_REGION\",\"comment\":\"MySQL 并发合成关闭\",\"version\":0}"))
                        .andReturn());
                assertThat(closeHoldsFloating.await(10, TimeUnit.SECONDS)).isTrue();
                Future<MvcResult> endorseFuture = pool.submit(() -> mvc.perform(post("/api/residence-permits/applications/" + scenario.endorsementApplicationId() + "/endorse")
                        .header("Authorization", endorseToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                        .andReturn());
                assertThat(endorseApplicationLocked.await(10, TimeUnit.SECONDS)).isTrue();
                String lockEvidence = awaitInnoDbCloseEndorseWait(gate, endorseConnectionId.get(), closeConnectionId.get(), 5_000);
                System.out.printf("MYSQL_LOCK_ORDER run=%d closeConnection=%d endorseConnection=%d %s%n",
                        run, closeConnectionId.get(), endorseConnectionId.get(),
                        lockEvidence == null ? "no correlated wait observed" : lockEvidence);
                releaseClose.countDown();

                MvcResult closeResult = closeFuture.get(30, TimeUnit.SECONDS);
                MvcResult endorseResult = endorseFuture.get(30, TimeUnit.SECONDS);
                long deadlocksAfter = mysqlErrorCount(gate, 1213);
                if (deadlocksAfter > deadlocksBefore) {
                    ErrorStatement error = mysqlErrorStatement(gate, 1213, "40001");
                    System.out.printf("MYSQL_DEADLOCK_FAIL run=%d closeHttp=%d endorseHttp=%d errno=%d sqlstate=%s mysqlThread=%d connection=%d%n",
                            run, closeResult.getResponse().getStatus(), endorseResult.getResponse().getStatus(),
                            error.mysqlErrno(), error.sqlState(), error.threadId(), error.processlistId());
                    logLatestDeadlock(gate, run);
                }
                assertThat(closeConnectionId.get()).isPositive().isNotEqualTo(endorseConnectionId.get());
                assertThat(lockEvidence).as("correlated InnoDB wait graph").isNotNull()
                        .contains("requesterGranted=")
                        .doesNotContain("residence_permit/PRIMARY/RECORD");
                assertThat(deadlocksAfter).as("MySQL 1213 count must not increase").isEqualTo(deadlocksBefore);
                assertThat(closeResult.getResponse().getStatus()).isEqualTo(200);
                assertThat(endorseResult.getResponse().getStatus()).isEqualTo(409);
                assertCloseEndorseOutcome(scenario, closeResult.getResponse().getStatus(), endorseResult.getResponse().getStatus());
                System.out.printf("MYSQL_CONCURRENCY_PASS run=%d closeHttp=200 endorseHttp=409 deadlocks=%d isolation=REPEATABLE-READ%n",
                        run, deadlocksAfter - deadlocksBefore);
            } finally {
                releaseClose.countDown();
                pool.shutdownNow();
                assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void sameHouseholdInboundAndLastMemberOutRaceIsReliableAcrossTenRuns() throws Exception {
        String isolation = jdbc.queryForObject("SELECT @@transaction_isolation", String.class);
        assertThat(isolation).isNotBlank();
        for (int run = 1; run <= 10; run++) {
            long head = createPerson("REGISTERED");
            long inboundPerson = createPerson("MIGRATED_OUT");
            long household = createHousehold(head);
            addMember(household, head, "HEAD");
            addResidence(household, head);
            long out = createApprovedOut(head, null);
            long inbound = createApprovedIn(inboundPerson, household);
            enableStatementErrorHistory(settings());
            long deadlocksBefore = mysqlErrorCount(settings(), 1213);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch observerReady = new CountDownLatch(1);
            AtomicBoolean observeLocks = new AtomicBoolean(true);
            ConcurrentLinkedQueue<String> lockWaitSamples = new ConcurrentLinkedQueue<>();
            AtomicReference<String> observerFailure = new AtomicReference<>();
            ExecutorService executor = Executors.newFixedThreadPool(2);
            ExecutorService observerExecutor = Executors.newSingleThreadExecutor();
            Future<?> observer = observerExecutor.submit(() -> captureLockWaits(
                    settings(), settings().database(), observeLocks, observerReady, lockWaitSamples, observerFailure));
            try {
                assertThat(observerReady.await(10, TimeUnit.SECONDS)).isTrue();
                String householdToken = token("household");
                int outVersion = migrationVersion(out, "migration_out");
                int inVersion = migrationVersion(inbound, "migration_in");
                Future<Execution> outFuture = executor.submit(() -> raceExecute(
                        ready, start, "/api/migrations/out/applications/" + out + "/execute",
                        householdToken, outVersion));
                Future<Execution> inFuture = executor.submit(() -> raceExecute(
                        ready, start, "/api/migrations/in/applications/" + inbound + "/execute",
                        householdToken, inVersion));
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                Execution outResult = outFuture.get(30, TimeUnit.SECONDS);
                Execution inResult = inFuture.get(30, TimeUnit.SECONDS);
                observeLocks.set(false);
                observer.get(10, TimeUnit.SECONDS);
                assertThat(outResult.threadId()).isNotEqualTo(inResult.threadId());
                String outcome;
                try {
                    outcome = assertRaceInvariant(household, head, inboundPerson, out, inbound);
                    System.out.printf("ATOMICITY_PASS run=%d outcome=%s household=%d%n", run, outcome, household);
                } catch (AssertionError failure) {
                    System.out.printf("ATOMICITY_FAIL run=%d household=%d%n", run, household);
                    System.out.printf("RELIABILITY_FAIL run=%d reason=database_invariant%n", run);
                    throw failure;
                }
                if (observerFailure.get() != null) {
                    System.out.printf("PFS_LOCK_WAIT_SAMPLER_UNAVAILABLE type=%s%n", observerFailure.get());
                } else if (lockWaitSamples.isEmpty()) {
                    System.out.printf("PFS_LOCK_WAIT_SAMPLES run=%d count=0%n", run);
                } else {
                    for (String sample : lockWaitSamples.stream().distinct().limit(24).toList()) {
                        System.out.printf("PFS_LOCK_WAIT_SAMPLE run=%d %s%n", run, sample);
                    }
                }
                if (observerFailure.get() != null) {
                    System.out.printf("RELIABILITY_FAIL run=%d reason=performance_schema_lock_wait_observer_unavailable type=%s%n",
                            run, observerFailure.get());
                    assertThat(observerFailure.get()).as("performance_schema lock-wait observer must run").isNull();
                }

                boolean hasHttp500 = outResult.httpStatus() == 500 || inResult.httpStatus() == 500;
                boolean reliabilityFailAlreadyPrinted = hasHttp500;
                if (hasHttp500) {
                    long deadlocksAfter = mysqlErrorCount(settings(), 1213);
                    ErrorStatement deadlock = deadlocksAfter > deadlocksBefore
                            ? mysqlErrorStatement(settings(), 1213, "40001") : null;
                    System.out.printf("RELIABILITY_FAIL run=%d http=[%d,%d] mysql1213=%s mysqlState=%s mysqlThread=%s mysqlConnection=%s sql=%s%n",
                            run, outResult.httpStatus(), inResult.httpStatus(), deadlocksAfter > deadlocksBefore,
                            deadlock == null ? "unknown" : deadlock.sqlState(),
                            deadlock == null ? "unknown" : deadlock.threadId(),
                            deadlock == null ? "unknown" : deadlock.processlistId(),
                            deadlock == null ? "unknown" : redactSql(deadlock.sqlText()));
                    if (deadlock != null) logLatestDeadlock(settings(), run);
                }
                try {
                    assertThat(List.of(outResult.httpStatus(), inResult.httpStatus()))
                            .as("HTTP 500 is not a successful business outcome; response bodies: [%s] [%s]",
                                    outResult.responseBody(), inResult.responseBody())
                            .allMatch(status -> status == 200 || status == 409);
                    assertThat(outResult.httpStatus() == 200 && inResult.httpStatus() == 200)
                            .as("both competing transactions must not commit")
                            .isFalse();
                    assertThat(outResult.httpStatus() == 200).isEqualTo("OUT_COMMITTED".equals(outcome));
                    assertThat(inResult.httpStatus() == 200).isEqualTo("IN_COMMITTED".equals(outcome));
                    assertExecutionAudit(out, outResult.httpStatus() == 200);
                    assertExecutionAudit(inbound, inResult.httpStatus() == 200);

                    boolean explicitRetrySucceeded = false;
                    if (outResult.httpStatus() == 409 && inResult.httpStatus() == 409
                            && "ACTIVE".equals(jdbc.queryForObject("SELECT status FROM household WHERE household_id=?", String.class, household))) {
                        int retryVersion = migrationVersion(inbound, "migration_in");
                        execute("/api/migrations/in/applications/" + inbound + "/execute", 200,
                                householdToken, retryVersion);
                        assertRaceInvariant(household, head, inboundPerson, out, inbound);
                        assertExecutionAudit(inbound, true);
                        explicitRetrySucceeded = true;
                    }
                    assertThat(outResult.httpStatus() == 200 || inResult.httpStatus() == 200 || explicitRetrySucceeded)
                            .as("at least one legal execution or explicit retry must make progress")
                            .isTrue();
                    long deadlocksAfterForResponse = mysqlErrorCount(settings(), 1213);
                    if (deadlocksAfterForResponse > deadlocksBefore) {
                        String safeDeadlockMessage = "业务数据正在被并发修改，请刷新后重试";
                        assertThat(List.of(outResult, inResult).stream()
                                .filter(result -> result.httpStatus() == 409)
                                .anyMatch(result -> result.responseBody().contains(safeDeadlockMessage)))
                                .as("a confirmed MySQL 1213/40001 response must be a safe business conflict")
                                .isTrue();
                    }

                    String failureEvidence = "none";
                    long deadlocksAfter = mysqlErrorCount(settings(), 1213);
                    if (deadlocksAfter > deadlocksBefore) {
                        ErrorStatement deadlock = mysqlErrorStatement(settings(), 1213, "40001");
                        failureEvidence = "MYSQL_DEADLOCK_SQLSTATE=" + deadlock.sqlState() + "_ERRNO=" + deadlock.mysqlErrno() + "_THREAD=" + deadlock.threadId();
                    }
                    System.out.printf("RELIABILITY_PASS run=%d isolation=%s workerThreads=[%d,%d] http=[%d,%d] rejected=%s%n",
                            run, isolation, outResult.threadId(), inResult.threadId(), outResult.httpStatus(),
                            inResult.httpStatus(), failureEvidence);
                } catch (AssertionError failure) {
                    if (!reliabilityFailAlreadyPrinted) {
                        System.out.printf("RELIABILITY_FAIL run=%d reason=reliability_assertion%n", run);
                    }
                    throw failure;
                }
            } finally {
                observeLocks.set(false);
                observerExecutor.shutdownNow();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void duplicateOutExecutionIsIdempotentAcrossConcurrentAndExplicitRetry() throws Exception {
        long head = createPerson("REGISTERED");
        long household = createHousehold(head);
        addMember(household, head, "HEAD");
        addResidence(household, head);
        long out = createApprovedOut(head, null);
        String householdToken = token("household");
        int version = migrationVersion(out, "migration_out");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            String path = "/api/migrations/out/applications/" + out + "/execute";
            Future<Execution> first = executor.submit(() -> raceExecute(ready, start, path, householdToken, version));
            Future<Execution> second = executor.submit(() -> raceExecute(ready, start, path, householdToken, version));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Execution firstResult = first.get(30, TimeUnit.SECONDS);
            Execution secondResult = second.get(30, TimeUnit.SECONDS);
            assertThat(firstResult.threadId()).isNotEqualTo(secondResult.threadId());
            assertThat(List.of(firstResult.httpStatus(), secondResult.httpStatus())).containsExactlyInAnyOrder(200, 409);
            assertThat(List.of(firstResult, secondResult).stream().filter(x -> x.httpStatus() == 409)
                    .allMatch(x -> !x.responseBody().contains("SQLSTATE") && !x.responseBody().contains("SQL syntax"))).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, out)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_out WHERE application_id=?", String.class, out)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT status FROM household WHERE household_id=?", String.class, household)).isEqualTo("PENDING_CANCELLATION");
        assertThat(jdbc.queryForObject("SELECT head_person_id FROM household WHERE household_id=?", Long.class, household)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND status='ACTIVE'", Integer.class, household)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE household_id=? AND status='ACTIVE'", Integer.class, household)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence_archive WHERE application_id=?", Integer.class, out)).isOne();
        assertExecutionAudit(out, true);

        MvcResult retry = mvc.perform(post("/api/migrations/out/applications/" + out + "/execute")
                .header("Authorization", "Bearer " + householdToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version + "}")).andReturn();
        assertThat(retry.getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence_archive WHERE application_id=?", Integer.class, out)).isOne();
        assertExecutionAudit(out, true);
        System.out.println("RELIABILITY_PASS duplicate migration-out execution: concurrent [200,409], explicit retry=409, archive/audit=1.");
    }

    @Test
    void correctExplicitRetryAfterVersionConflictCompletesMigrationIn() throws Exception {
        long head = createPerson("REGISTERED");
        long inboundPerson = createPerson("MIGRATED_OUT");
        long household = createHousehold(head);
        addMember(household, head, "HEAD");
        addResidence(household, head);
        long inbound = createApprovedIn(inboundPerson, household);
        String householdToken = token("household");
        int version = migrationVersion(inbound, "migration_in");

        execute("/api/migrations/in/applications/" + inbound + "/execute", 409, householdToken, version + 1);
        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, inbound)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?", String.class, inbound)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT status FROM household WHERE household_id=?", String.class, household)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND status='ACTIVE'", Integer.class, household)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE household_id=? AND status='ACTIVE'", Integer.class, household)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND person_id=? AND status='ACTIVE'", Integer.class, household, inboundPerson)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id=? AND status='ACTIVE'", Integer.class, inboundPerson)).isZero();
        assertExecutionAudit(inbound, false);

        execute("/api/migrations/in/applications/" + inbound + "/execute", 200, householdToken, version);
        assertApplicationCompleted(inbound);
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?", String.class, inbound)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND status='ACTIVE'", Integer.class, household)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE household_id=? AND status='ACTIVE'", Integer.class, household)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM household WHERE household_id=?", String.class, household)).isEqualTo("ACTIVE");
        assertHeadInvariant(household, head, 1);
        assertExecutionAudit(inbound, true);
        System.out.println("RELIABILITY_PASS migration-in retry after stale-version HTTP 409: retry HTTP 200; no first-attempt writes/audit.");
    }

    @Test
    void migrationOutInjectedSqlFailureRollsBackEveryBusinessWrite() throws Exception {
        long head = createPerson("REGISTERED");
        long household = createHousehold(head);
        addMember(household, head, "HEAD");
        addResidence(household, head);
        long out = createApprovedOut(head, null);
        GateSettings gate = settings();
        String trigger = "gate_fail_migration_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        enableStatementErrorHistory(gate);
        long errorsBefore = mysqlErrorCount(gate, 1644);
        try (Connection admin = DriverManager.getConnection(gate.url(), gate.adminUser(), gate.adminPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE TRIGGER " + trigger + " BEFORE UPDATE ON person FOR EACH ROW " +
                    "BEGIN IF NEW.person_id=" + head + " AND NEW.current_status_code='MIGRATED_OUT' THEN " +
                    "SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='synthetic migration failure'; " +
                    "END IF; END");
        }

        try {
            MvcResult result = mvc.perform(post("/api/migrations/out/applications/" + out + "/execute")
                    .header("Authorization", "Bearer " + token("household")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\":" + migrationVersion(out, "migration_out") + "}")).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(500);
            assertThat(result.getResponse().getContentAsString()).contains("服务器处理请求失败")
                    .doesNotContain("synthetic migration failure", "45000", "1644", "UPDATE person");
            assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, out)).isEqualTo("APPROVED");
            assertThat(jdbc.queryForObject("SELECT business_status FROM migration_out WHERE application_id=?", String.class, out)).isEqualTo("APPROVED");
            assertThat(jdbc.queryForObject("SELECT status FROM household WHERE household_id=?", String.class, household)).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("SELECT head_person_id FROM household WHERE household_id=?", Long.class, household)).isEqualTo(head);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND person_id=? AND status='ACTIVE' AND relationship='HEAD'", Integer.class, household, head)).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE household_id=? AND person_id=? AND status='ACTIVE'", Integer.class, household, head)).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence_archive WHERE application_id=?", Integer.class, out)).isZero();
            assertThat(jdbc.queryForObject("SELECT current_status_code FROM person WHERE person_id=?", String.class, head)).isEqualTo("REGISTERED");
            assertExecutionAudit(out, false);
            long errorsAfter = mysqlErrorCount(gate, 1644);
            assertThat(errorsAfter).isGreaterThan(errorsBefore);
            ErrorStatement error = mysqlErrorStatement(gate, 1644, "45000");
            assertThat(error.mysqlErrno()).isEqualTo(1644);
            assertThat(error.sqlState()).isEqualTo("45000");
            System.out.printf("ATOMICITY_PASS migration fault-injection http=%d SQLSTATE=%s MYSQL_ERRNO=%d mysqlThread=%d mysqlConnection=%d archive=0 app=APPROVED household=ACTIVE%n",
                    result.getResponse().getStatus(), error.sqlState(), error.mysqlErrno(), error.threadId(), error.processlistId());
        } finally {
            try (Connection admin = DriverManager.getConnection(gate.url(), gate.adminUser(), gate.adminPassword());
                 Statement statement = admin.createStatement()) {
                statement.execute("DROP TRIGGER IF EXISTS " + trigger);
            }
        }
    }

    @Test
    void mysqlCancellationFailureInjectionRollsBackAllEarlierWrites() throws Exception {
        long head = createPerson("REGISTERED");
        long target = createPerson("REGISTERED");
        long household = createHousehold(head);
        addMember(household, head, "HEAD");
        addMember(household, target, "CHILD");
        addResidence(household, head);
        addResidence(household, target);
        long cancellation = createPersonCancellation(target);
        insertMaterials(cancellation, List.of("APPLICANT_IDENTITY_PROOF", "DEATH_CERTIFICATE", "HOUSEHOLD_BOOK"));
        submitAndApprove(cancellation);

        GateSettings gate = settings();
        String trigger = "gate_fail_cancel_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        enableStatementErrorHistory(gate);
        long errorsBefore = mysqlSignalErrorCount(gate);
        try (Connection admin = DriverManager.getConnection(gate.url(), gate.adminUser(), gate.adminPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE TRIGGER " + trigger + " BEFORE UPDATE ON person FOR EACH ROW " +
                    "BEGIN IF NEW.person_id=" + target + " AND NEW.current_status_code='DECEASED' THEN " +
                    "SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='mysql gate injected cancellation failure'; " +
                    "END IF; END");
        }

        try {
            MvcResult result = mvc.perform(post("/api/cancellations/person/applications/" + cancellation + "/execute")
                    .header("Authorization", "Bearer " + token("household"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\":" + cancellationVersion(cancellation) + "}"))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).isBetween(400, 599);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence_archive WHERE application_id=?", Integer.class, cancellation)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id=? AND status='ACTIVE'", Integer.class, target)).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND person_id=? AND status='ACTIVE'", Integer.class, household, target)).isOne();
            assertThat(jdbc.queryForObject("SELECT current_status_code FROM person WHERE person_id=?", String.class, target)).isEqualTo("REGISTERED");
            assertThat(jdbc.queryForObject("SELECT status FROM household WHERE household_id=?", String.class, household)).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, cancellation)).isEqualTo("APPROVED");
            assertThat(jdbc.queryForObject("SELECT business_status FROM cancellation_record WHERE application_id=?", String.class, cancellation)).isEqualTo("APPROVED");
            long errorsAfter = mysqlSignalErrorCount(gate);
            assertThat(errorsAfter).isGreaterThan(errorsBefore);
            ErrorStatement error = mysqlSignalStatement(gate);
            assertThat(error.mysqlErrno()).isEqualTo(1644);
            assertThat(error.sqlState()).isEqualTo("45000");
            System.out.printf("PASS cancellation rollback: http=%d SQLSTATE=%s MYSQL_ERRNO=%d mysqlThreadId=%d triggerCount=%d->%d%n",
                    result.getResponse().getStatus(), error.sqlState(), error.mysqlErrno(), error.threadId(), errorsBefore, errorsAfter);
        } finally {
            try (Connection admin = DriverManager.getConnection(gate.url(), gate.adminUser(), gate.adminPassword());
                 Statement statement = admin.createStatement()) {
                statement.execute("DROP TRIGGER IF EXISTS " + trigger);
            }
        }
    }

    private Execution raceExecute(CountDownLatch ready, CountDownLatch start, String path, String token, int version) throws Exception {
        long threadId = Thread.currentThread().getId();
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("race barrier timed out");
        MvcResult result = mvc.perform(post(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version + "}"))
                .andReturn();
        return new Execution(threadId, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private void captureLockWaits(GateSettings gate, String schema, AtomicBoolean active, CountDownLatch ready,
                                  ConcurrentLinkedQueue<String> samples, AtomicReference<String> failure) {
        String sql = "SELECT req_thread.PROCESSLIST_ID,req.OBJECT_NAME,req.INDEX_NAME,req.LOCK_TYPE,req.LOCK_MODE," +
                "block_thread.PROCESSLIST_ID,block.OBJECT_NAME,block.INDEX_NAME,block.LOCK_TYPE,block.LOCK_MODE " +
                "FROM performance_schema.data_lock_waits w " +
                "JOIN performance_schema.data_locks req ON req.ENGINE=w.ENGINE AND req.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID " +
                "JOIN performance_schema.data_locks block ON block.ENGINE=w.ENGINE AND block.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID " +
                "LEFT JOIN performance_schema.threads req_thread ON req_thread.THREAD_ID=req.THREAD_ID " +
                "LEFT JOIN performance_schema.threads block_thread ON block_thread.THREAD_ID=block.THREAD_ID " +
                "WHERE req.OBJECT_SCHEMA=?";
        try (Connection connection = DriverManager.getConnection(gate.url(), gate.adminUser(), gate.adminPassword());
             PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, schema);
            ready.countDown();
            while (active.get()) {
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next() && samples.size() < 64) {
                        samples.add("waitConnection=" + rows.getString(1) + " wait=" + rows.getString(2) + "/" +
                                rows.getString(3) + "/" + rows.getString(4) + "/" + rows.getString(5) +
                                " blockedByConnection=" + rows.getString(6) + " blocker=" + rows.getString(7) +
                                "/" + rows.getString(8) + "/" + rows.getString(9) + "/" + rows.getString(10));
                    }
                }
                Thread.sleep(2);
            }
        } catch (Exception exception) {
            failure.compareAndSet(null, exception.getClass().getSimpleName());
            ready.countDown();
        }
    }

    private void logLatestDeadlock(GateSettings gate, int run) {
        String sql = "SHOW ENGINE INNODB STATUS";
        try (Connection connection = DriverManager.getConnection(gate.url(), gate.adminUser(), gate.adminPassword());
             Statement statement = connection.createStatement(); ResultSet row = statement.executeQuery(sql)) {
            if (!row.next()) return;
            String status = row.getString("Status");
            Matcher section = Pattern.compile("LATEST DETECTED DEADLOCK\\R([\\s\\S]*?)(?:\\R------------|\\z)")
                    .matcher(status == null ? "" : status);
            if (!section.find()) return;
            Pattern relevant = Pattern.compile("^\\s*(?:\\*\\*\\* \\([12]\\) TRANSACTION:|\\*\\*\\* \\([12]\\) (?:HOLDS|WAITING)|\\*\\*\\* WE ROLL BACK|MySQL thread id |RECORD LOCKS |TABLE LOCK |SELECT |UPDATE |DELETE )");
            for (String line : section.group(1).split("\\R")) {
                if (!relevant.matcher(line).find()) continue;
                System.out.printf("MYSQL_INNODB_DEADLOCK run=%d %s%n", run, redactSql(line.trim()));
            }
        } catch (Exception exception) {
            System.out.printf("MYSQL_INNODB_DEADLOCK_CAPTURE_UNAVAILABLE type=%s%n", exception.getClass().getSimpleName());
        }
    }

    private String redactSql(String value) {
        if (value == null) return "";
        return value.replaceAll("`[A-Za-z0-9_]+`\\.", "")
                .replaceAll("(?i)(household_id|person_id|application_id|residence_id)=\\d+", "$1=?")
                .replaceAll("(?i)(VALUES\\s*\\()([^)]{0,300})(\\))", "$1?…$3");
    }

    private String assertRaceInvariant(long household, long head, long inboundPerson, long out, long inbound) {
        String householdStatus = jdbc.queryForObject("SELECT status FROM household WHERE household_id=?", String.class, household);
        String outStatus = jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, out);
        String inStatus = jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, inbound);
        String outBusinessStatus = jdbc.queryForObject("SELECT business_status FROM migration_out WHERE application_id=?", String.class, out);
        String inBusinessStatus = jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?", String.class, inbound);
        long activeMembers = jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND status='ACTIVE'", Long.class, household);
        long activeResidences = jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE household_id=? AND status='ACTIVE'", Long.class, household);
        if ("COMPLETED".equals(outStatus)) {
            assertThat(inStatus).isEqualTo("APPROVED");
            assertThat(outBusinessStatus).isEqualTo("COMPLETED");
            assertThat(inBusinessStatus).isEqualTo("APPROVED");
            assertThat(householdStatus).isEqualTo("PENDING_CANCELLATION");
            assertThat(activeMembers).isZero();
            assertThat(activeResidences).isZero();
            assertThat(jdbc.queryForObject("SELECT head_person_id FROM household WHERE household_id=?", Long.class, household)).isNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence_archive WHERE application_id=?", Integer.class, out)).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence_archive WHERE application_id=?", Integer.class, inbound)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND person_id=? AND status='ACTIVE'", Integer.class, household, inboundPerson)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id=? AND status='ACTIVE'", Integer.class, inboundPerson)).isZero();
            assertThat(jdbc.queryForObject("SELECT current_status_code FROM person WHERE person_id=?", String.class, head)).isEqualTo("MIGRATED_OUT");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND person_id=? AND status='LEFT' AND relationship='HEAD'", Integer.class, household, head)).isOne();
            return "OUT_COMMITTED";
        } else if ("COMPLETED".equals(inStatus)) {
            assertThat(outStatus).isEqualTo("APPROVED");
            assertThat(outBusinessStatus).isEqualTo("APPROVED");
            assertThat(inBusinessStatus).isEqualTo("COMPLETED");
            assertThat(householdStatus).isEqualTo("ACTIVE");
            assertThat(activeMembers).isEqualTo(2);
            assertThat(activeResidences).isEqualTo(2);
            assertHeadInvariant(household, head, 1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id=? AND status='ACTIVE'", Integer.class, inboundPerson)).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND person_id=? AND status='ACTIVE'", Integer.class, household, inboundPerson)).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence_archive WHERE application_id=?", Integer.class, out)).isZero();
            assertThat(jdbc.queryForObject("SELECT current_status_code FROM person WHERE person_id=?", String.class, head)).isEqualTo("REGISTERED");
            assertThat(jdbc.queryForObject("SELECT current_status_code FROM person WHERE person_id=?", String.class, inboundPerson)).isEqualTo("REGISTERED");
            return "IN_COMMITTED";
        } else {
            assertThat(outStatus).isEqualTo("APPROVED");
            assertThat(inStatus).isEqualTo("APPROVED");
            assertThat(outBusinessStatus).isEqualTo("APPROVED");
            assertThat(inBusinessStatus).isEqualTo("APPROVED");
            assertThat(householdStatus).isEqualTo("ACTIVE");
            assertThat(activeMembers).isOne();
            assertThat(activeResidences).isOne();
            assertHeadInvariant(household, head, 1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence_archive WHERE application_id=?", Integer.class, out)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id=? AND status='ACTIVE'", Integer.class, inboundPerson)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND person_id=? AND status='ACTIVE'", Integer.class, household, inboundPerson)).isZero();
            assertThat(jdbc.queryForObject("SELECT current_status_code FROM person WHERE person_id=?", String.class, head)).isEqualTo("REGISTERED");
            assertThat(jdbc.queryForObject("SELECT current_status_code FROM person WHERE person_id=?", String.class, inboundPerson)).isEqualTo("MIGRATED_OUT");
            return "BOTH_ROLLED_BACK";
        }
    }

    private void assertExecutionAudit(long applicationId, boolean completed) {
        int expected = completed ? 1 : 0;
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_log WHERE application_id=? AND action='EXECUTE'",
                Integer.class, applicationId)).isEqualTo(expected);
        String pathSuffix = "/api/migrations/" + (jdbc.queryForObject(
                "SELECT COUNT(*) FROM migration_in WHERE application_id=?", Integer.class, applicationId) == 1 ? "in" : "out")
                + "/applications/" + applicationId + "/execute";
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE operation_type IN('MIGRATION_IN_EXECUTE','MIGRATION_OUT_EXECUTE') AND request_path=?",
                Integer.class, pathSuffix)).isEqualTo(expected);
    }

    private void assertHeadInvariant(long household, long expectedHead, long count) {
        assertThat(jdbc.queryForObject("SELECT head_person_id FROM household WHERE household_id=?", Long.class, household)).isEqualTo(expectedHead);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND status='ACTIVE' AND relationship='HEAD'", Long.class, household)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND person_id=? AND status='ACTIVE' AND relationship='HEAD'", Long.class, household, expectedHead)).isOne();
    }

    private EndorseScenario createApprovedEndorsementScenario() {
        long person = createPerson("REGISTERED");
        long owner = userId("population");
        long ownerDepartment = departmentId("population");
        long operator = userId("household");
        long operatorDepartment = departmentId("household");
        long approver = userId("approver");
        long approverDepartment = departmentId("approver");
        long floatingSource = insertSyntheticBusinessApplication("FLOATING_REGISTRATION", person, operator,
                operatorDepartment, "110000", "COMPLETED");
        KeyHolder floatingKey = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO floating_population(registration_no,source_application_id,person_id,source_region_code," +
                    "source_address,current_region_code,current_address,residence_reason_code,residence_proof_type," +
                    "arrival_date,registration_date,eligible_from_date,department_id,operator_id,status,current_flag,version) " +
                    "VALUES(?,?,?,'110000','synthetic source','110000','synthetic current','EMPLOYMENT','EMPLOYER_ACCOMMODATION'," +
                    "DATE_SUB(CURRENT_DATE,INTERVAL 200 DAY),CURRENT_DATE,DATE_SUB(CURRENT_DATE,INTERVAL 20 DAY),?,?,'ACTIVE',1,0)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, "R4-FR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
            statement.setLong(2, floatingSource);
            statement.setLong(3, person);
            statement.setLong(4, operatorDepartment);
            statement.setLong(5, operator);
            return statement;
        }, floatingKey);
        long floating = generatedId(floatingKey);

        long permitSource = insertSyntheticBusinessApplication("RESIDENCE_PERMIT_FIRST_ISSUE", person, operator,
                operatorDepartment, "110000", "COMPLETED");
        KeyHolder permitKey = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO residence_permit(permit_no,person_id,floating_id,source_application_id,issue_region_code," +
                    "issuing_department_id,issuing_authority,issue_date,valid_from,valid_until,status,current_flag,version) " +
                    "VALUES(?,?,?,?,'110000',?,'synthetic authority',DATE_SUB(CURRENT_DATE,INTERVAL 350 DAY)," +
                    "DATE_SUB(CURRENT_DATE,INTERVAL 350 DAY),DATE_ADD(CURRENT_DATE,INTERVAL 15 DAY),'ACTIVE',1,0)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, "R4-RP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
            statement.setLong(2, person);
            statement.setLong(3, floating);
            statement.setLong(4, permitSource);
            statement.setLong(5, operatorDepartment);
            return statement;
        }, permitKey);
        long permit = generatedId(permitKey);
        LocalDate originalValidUntil = jdbc.queryForObject(
                "SELECT valid_until FROM residence_permit WHERE permit_id=?", LocalDate.class, permit);

        long endorsementApplication = insertSyntheticBusinessApplication("RESIDENCE_PERMIT_ENDORSEMENT", person,
                owner, ownerDepartment, "110000", "APPROVED");
        jdbc.update("INSERT INTO sys_approval_request(approval_no,application_id,status,current_approver_id," +
                        "current_department_id,current_region_code,submitted_by,submitted_at,decided_by,decided_at," +
                        "decision_comment,version) VALUES(?,?,'APPROVED',?,?,'110000',?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,?,1)",
                "R4-APR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                endorsementApplication, approver, approverDepartment, owner, approver, "synthetic approved endorsement");
        jdbc.update("INSERT INTO residence_permit_application(application_id,apply_type,floating_id,permit_id,person_id," +
                        "current_region_code,residence_basis_code,business_status,version) " +
                        "VALUES(?,'ENDORSEMENT',?,?,?,'110000','EMPLOYMENT','APPROVED',2)",
                endorsementApplication, floating, permit, person);
        insertPermitMaterial(endorsementApplication, "CURRENT_RESIDENCE_PERMIT", owner, approver);
        insertPermitMaterial(endorsementApplication, "CONTINUED_RESIDENCE_PROOF", owner, approver);
        return new EndorseScenario(person, floating, permit, endorsementApplication, originalValidUntil);
    }

    private long insertSyntheticBusinessApplication(String type, long person, long applicant, long department,
                                                    String region, String status) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO business_application(application_no,business_type,title,applicant_user_id," +
                    "applicant_department_id,applicant_region_code,target_person_id,status,reason,version) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,0)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, "R4-APP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
            statement.setString(2, type);
            statement.setString(3, "synthetic " + type);
            statement.setLong(4, applicant);
            statement.setLong(5, department);
            statement.setString(6, region);
            statement.setLong(7, person);
            statement.setString(8, status);
            statement.setString(9, "synthetic MySQL concurrency fixture");
            return statement;
        }, key);
        return generatedId(key);
    }

    private void insertPermitMaterial(long application, String type, long applicant, long approver) {
        String suffix = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,original_filename," +
                        "stored_filename,storage_path,content_type,file_size,file_sha256,required_flag,verify_status," +
                        "verify_user_id,verified_at,uploaded_by) VALUES(?,?,?,'synthetic.pdf',?,'mysql-gate-synthetic'," +
                        "'application/pdf',1,?,1,'VERIFIED',?,CURRENT_TIMESTAMP,?)",
                application, type, "synthetic " + type, suffix + ".pdf", suffix, approver, applicant);
    }

    private long departmentId(String username) {
        return jdbc.queryForObject("SELECT department_id FROM sys_user WHERE username=? AND status='ENABLED'",
                Long.class, username);
    }

    private String awaitInnoDbCloseEndorseWait(GateSettings gate, long requesterConnection,
                                               long blockerConnection, long timeoutMillis) throws Exception {
        String waitSql = "SELECT req.OBJECT_NAME,req.INDEX_NAME,req.LOCK_TYPE,req.LOCK_MODE,req.LOCK_STATUS," +
                "block.OBJECT_NAME,block.INDEX_NAME,block.LOCK_TYPE,block.LOCK_MODE,block.LOCK_STATUS " +
                "FROM performance_schema.data_lock_waits w " +
                "JOIN performance_schema.data_locks req ON req.ENGINE=w.ENGINE AND req.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID " +
                "JOIN performance_schema.data_locks block ON block.ENGINE=w.ENGINE AND block.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID " +
                "JOIN performance_schema.threads rt ON rt.THREAD_ID=w.REQUESTING_THREAD_ID " +
                "JOIN performance_schema.threads bt ON bt.THREAD_ID=w.BLOCKING_THREAD_ID " +
                "WHERE rt.PROCESSLIST_ID=? AND bt.PROCESSLIST_ID=? AND req.OBJECT_SCHEMA=? LIMIT 1";
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        try (Connection admin = DriverManager.getConnection(gate.url(), gate.adminUser(), gate.adminPassword());
             PreparedStatement waitQuery = admin.prepareStatement(waitSql)) {
            while (System.nanoTime() < deadline) {
                waitQuery.setLong(1, requesterConnection);
                waitQuery.setLong(2, blockerConnection);
                waitQuery.setString(3, gate.database());
                try (ResultSet rows = waitQuery.executeQuery()) {
                    if (rows.next()) {
                        String wait = "request=" + rows.getString(1) + "/" + rows.getString(2) + "/" +
                                rows.getString(3) + "/" + rows.getString(4) + "/" + rows.getString(5) +
                                " blocker=" + rows.getString(6) + "/" + rows.getString(7) + "/" +
                                rows.getString(8) + "/" + rows.getString(9) + "/" + rows.getString(10);
                        return wait + " requesterGranted=" + grantedLocks(admin, requesterConnection, gate.database());
                    }
                }
                Thread.sleep(10);
            }
        }
        return null;
    }

    private String grantedLocks(Connection admin, long connectionId, String schema) throws Exception {
        String sql = "SELECT GROUP_CONCAT(CONCAT(l.OBJECT_NAME,'/',COALESCE(l.INDEX_NAME,''),'/',l.LOCK_TYPE,'/',l.LOCK_MODE) " +
                "ORDER BY l.OBJECT_NAME,l.INDEX_NAME SEPARATOR ';') FROM performance_schema.data_locks l " +
                "JOIN performance_schema.threads t ON t.THREAD_ID=l.THREAD_ID " +
                "WHERE t.PROCESSLIST_ID=? AND l.OBJECT_SCHEMA=? AND l.LOCK_STATUS='GRANTED'";
        try (PreparedStatement statement = admin.prepareStatement(sql)) {
            statement.setLong(1, connectionId);
            statement.setString(2, schema);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getString(1) : "none";
            }
        }
    }

    private void assertCloseEndorseOutcome(EndorseScenario scenario, int closeStatus, int endorseStatus) {
        String floatingStatus = jdbc.queryForObject("SELECT status FROM floating_population WHERE floating_id=?",
                String.class, scenario.floatingId());
        String permitStatus = jdbc.queryForObject("SELECT status FROM residence_permit WHERE permit_id=?",
                String.class, scenario.permitId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence_permit_log WHERE permit_id=? AND action='ENDORSE'",
                Integer.class, scenario.permitId())).isEqualTo(endorseStatus == 200 ? 1 : 0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence_permit_log WHERE permit_id=? AND action='REGISTRATION_CLOSED'",
                Integer.class, scenario.permitId())).isEqualTo(closeStatus == 200 ? 1 : 0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_log WHERE application_id=? AND action='EXECUTE'",
                Integer.class, scenario.endorsementApplicationId())).isEqualTo(endorseStatus == 200 ? 1 : 0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE request_path=? AND operation_type='FLOATING_REGISTRATION_CLOSE'",
                Integer.class, "/api/floating-populations/" + scenario.floatingId() + "/close")).isEqualTo(closeStatus == 200 ? 1 : 0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE request_path=? AND operation_type='RESIDENCE_PERMIT_ENDORSE'",
                Integer.class, "/api/residence-permits/applications/" + scenario.endorsementApplicationId() + "/endorse"))
                .isEqualTo(endorseStatus == 200 ? 1 : 0);
        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?",
                String.class, scenario.endorsementApplicationId())).isEqualTo(endorseStatus == 200 ? "COMPLETED" : "APPROVED");
        if (closeStatus == 200) {
            assertThat(floatingStatus).isEqualTo("LEFT");
            assertThat(permitStatus).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT current_flag FROM floating_population WHERE floating_id=?",
                    Object.class, scenario.floatingId())).isNull();
            assertThat(jdbc.queryForObject("SELECT current_flag FROM residence_permit WHERE permit_id=?",
                    Object.class, scenario.permitId())).isNull();
        } else {
            assertThat(floatingStatus).isEqualTo("ACTIVE");
            assertThat(permitStatus).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("SELECT current_flag FROM floating_population WHERE floating_id=?",
                    Integer.class, scenario.floatingId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT current_flag FROM residence_permit WHERE permit_id=?",
                    Integer.class, scenario.permitId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT valid_until FROM residence_permit WHERE permit_id=?",
                    LocalDate.class, scenario.permitId())).isAfter(scenario.originalValidUntil());
        }
    }

    private int invalidHouseholdHeadCount() throws Exception {
        String sql = Files.readString(Path.of("doc/database/check_backend_v1.sql")).trim();
        return jdbc.queryForObject("SELECT abnormal_count FROM (" + sql.substring(0, sql.length() - 1) +
                ") checks WHERE check_name='invalid_household_head'", Integer.class);
    }

    private long createPerson(String status) {
        String card = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 17);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO person(name,gender,id_card,current_address,status,current_status_code) VALUES(?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, "MySQL gate synthetic " + card.substring(1, 7));
            statement.setString(2, "1");
            statement.setString(3, card);
            statement.setString(4, "synthetic test address");
            statement.setString(5, "正常");
            statement.setString(6, status);
            return statement;
        }, key);
        return generatedId(key);
    }

    private long createHousehold(Long head) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO household(household_no,head_person_id,address,region_code,household_type,establish_date,status,version) VALUES(?,?,?,?,?,?,?,0)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, "GATE-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
            if (head == null) statement.setNull(2, java.sql.Types.BIGINT); else statement.setLong(2, head);
            statement.setString(3, "synthetic household address");
            statement.setString(4, "110101");
            statement.setString(5, "FAMILY");
            statement.setObject(6, LocalDate.of(2020, 1, 1));
            statement.setString(7, "ACTIVE");
            return statement;
        }, key);
        return generatedId(key);
    }

    private void addMember(long household, long person, String relationship) {
        jdbc.update("INSERT INTO household_member(household_id,person_id,relationship,join_date,status,version) VALUES(?,?,?,?,'ACTIVE',0)",
                household, person, relationship, LocalDate.of(2020, 1, 1));
    }

    private void addResidence(long household, long person) {
        long userId = userId("household");
        jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,register_date,start_date,status,created_by,version) VALUES(?,?,?,?,?,? ,?,'ACTIVE',?,0)",
                person, household, "synthetic household address", "110101", "BIRTH",
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 1), userId);
    }

    private long createApprovedOut(long person, Long successor) throws Exception {
        String body = "{\"personId\":" + person + ",\"migrationType\":\"OUTSIDE_CITY\",\"toRegionCode\":\"310000\",\"toAddress\":\"synthetic destination\",\"outDate\":\"2026-11-02\",\"reason\":\"synthetic mysql gate\",\"newHeadPersonId\":" + (successor == null ? "null" : successor) + ",\"title\":\"mysql gate migration out\"}";
        long application = createApplication("/api/migrations/out/applications", body, "household");
        List<String> materials = new ArrayList<>(List.of("IDENTITY_PROOF", "HOUSEHOLD_BOOK"));
        insertMaterials(application, materials);
        submitAndApprove(application);
        return application;
    }

    private long createApprovedIn(long person, long household) throws Exception {
        String body = "{\"personId\":" + person + ",\"migrationType\":\"OUTSIDE_CITY\",\"fromRegionCode\":\"310000\",\"fromAddress\":\"synthetic previous address\",\"toRegionCode\":\"110101\",\"toHouseholdId\":" + household + ",\"inDate\":\"2026-11-02\",\"reason\":\"synthetic mysql gate\",\"title\":\"mysql gate migration in\"}";
        long application = createApplication("/api/migrations/in/applications", body, "household");
        insertMaterials(application, List.of("IDENTITY_PROOF", "HOUSEHOLD_BOOK"));
        submitAndApprove(application);
        return application;
    }

    private long createHouseholdCancellation(long household) throws Exception {
        return createApplication("/api/cancellations/household/applications",
                "{\"householdId\":" + household + ",\"cancelReasonCode\":\"NO_ACTIVE_MEMBERS\",\"eventDate\":\"2026-11-02\",\"title\":\"mysql gate household cancellation\",\"reason\":\"synthetic no members\"}",
                "household");
    }

    private long createPersonCancellation(long person) throws Exception {
        return createApplication("/api/cancellations/person/applications",
                "{\"personId\":" + person + ",\"cancelReasonCode\":\"DEATH\",\"eventDate\":\"2026-11-02\",\"title\":\"mysql gate person cancellation\",\"reason\":\"synthetic failure injection\"}",
                "household");
    }

    private long createApplication(String path, String body, String user) throws Exception {
        MvcResult result = mvc.perform(post(path).header("Authorization", "Bearer " + token(user))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(result.getResponse().getStatus()).as("create API %s: %s", path, result.getResponse().getContentAsString()).isEqualTo(201);
        return json.readTree(result.getResponse().getContentAsString()).path("data").path("applicationId").asLong();
    }

    private void execute(String path, int expected, String token, int version) throws Exception {
        MvcResult result = mvc.perform(post(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version + "}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).as("execute API %s: %s", path,
                result.getResponse().getContentAsString()).isEqualTo(expected);
    }

    private void submitAndApprove(long application) throws Exception {
        MvcResult submitted = mvc.perform(post("/api/applications/" + application + "/submit")
                .header("Authorization", "Bearer " + token("household"))).andReturn();
        assertThat(submitted.getResponse().getStatus()).as("submit application %d: %s", application, submitted.getResponse().getContentAsString()).isEqualTo(200);
        long approval = jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?", Long.class, application);
        MvcResult approved = mvc.perform(post("/api/approvals/" + approval + "/approve")
                .header("Authorization", "Bearer " + token("approver"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"synthetic mysql gate approval\",\"version\":0}"))
                .andReturn();
        assertThat(approved.getResponse().getStatus()).as("approve application %d: %s", application, approved.getResponse().getContentAsString()).isEqualTo(200);
    }

    private void insertMaterials(long application, List<String> types) {
        long applicant = userId("household");
        long approver = userId("approver");
        for (String type : types) {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,original_filename,stored_filename,storage_path,content_type,file_size,file_sha256,required_flag,verify_status,verify_user_id,uploaded_by) VALUES(?,?,?,'synthetic.pdf',?,'mysql-gate-synthetic','application/pdf',1,?,1,'VERIFIED',?,?)",
                    application, type, "synthetic " + type, suffix + ".pdf", suffix + suffix, approver, applicant);
        }
    }

    private int migrationVersion(long application, String detailTable) {
        return jdbc.queryForObject("SELECT version FROM " + detailTable + " WHERE application_id=?", Integer.class, application);
    }

    private int cancellationVersion(long application) {
        return jdbc.queryForObject("SELECT version FROM cancellation_record WHERE application_id=?", Integer.class, application);
    }

    private long userId(String username) {
        return jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username=? AND status='ENABLED'", Long.class, username);
    }

    private String token(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + settings().apiTestPassword() + "\"}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).as("login %s", username).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private void assertApplicationCompleted(long application) {
        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, application)).isEqualTo("COMPLETED");
    }

    private long mysqlSignalErrorCount(GateSettings gate) throws Exception {
        return mysqlErrorCount(gate, 1644);
    }

    private long mysqlErrorCount(GateSettings gate, int errorNumber) throws Exception {
        try (Connection admin = DriverManager.getConnection(gate.url(), gate.adminUser(), gate.adminPassword());
             Statement statement = admin.createStatement();
             var result = statement.executeQuery("SELECT COALESCE(MAX(SUM_ERROR_RAISED),0) FROM performance_schema.events_errors_summary_global_by_error WHERE ERROR_NUMBER=" + errorNumber)) {
            if (!result.next()) throw new IllegalStateException("MySQL error summary query returned no row.");
            return result.getLong(1);
        }
    }

    private void enableStatementErrorHistory(GateSettings gate) throws Exception {
        try (Connection admin = DriverManager.getConnection(gate.url(), gate.adminUser(), gate.adminPassword());
             Statement statement = admin.createStatement()) {
            statement.executeUpdate("UPDATE performance_schema.setup_consumers SET ENABLED='YES' WHERE NAME IN " +
                    "('events_statements_current','events_statements_history','events_statements_history_long')");
            statement.executeUpdate("UPDATE performance_schema.setup_instruments SET ENABLED='YES',TIMED='YES' WHERE NAME LIKE 'statement/%'");
        }
    }

    private ErrorStatement mysqlSignalStatement(GateSettings gate) throws Exception {
        return mysqlErrorStatement(gate, 1644, "45000");
    }

    private ErrorStatement mysqlErrorStatement(GateSettings gate, int errorNumber, String sqlState) throws Exception {
        try (Connection admin = DriverManager.getConnection(gate.url(), gate.adminUser(), gate.adminPassword());
             var statement = admin.prepareStatement("SELECT e.MYSQL_ERRNO,e.RETURNED_SQLSTATE,e.THREAD_ID," +
                     "COALESCE(t.PROCESSLIST_ID,0),e.SQL_TEXT FROM performance_schema.events_statements_history_long e " +
                     "LEFT JOIN performance_schema.threads t ON t.THREAD_ID=e.THREAD_ID " +
                     "WHERE e.CURRENT_SCHEMA=? AND e.MYSQL_ERRNO=? AND e.RETURNED_SQLSTATE=? " +
                     "ORDER BY e.TIMER_END DESC LIMIT 1")) {
            statement.setString(1, gate.database());
            statement.setInt(2, errorNumber);
            statement.setString(3, sqlState);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("MySQL statement history did not retain the injected SQLSTATE/errno.");
                return new ErrorStatement(result.getInt(1), result.getString(2), result.getLong(3),
                        result.getLong(4), result.getString(5));
            }
        }
    }

    private long generatedId(KeyHolder key) {
        Number id = key.getKey();
        assertThat(id).isNotNull();
        return id.longValue();
    }

    private record Execution(long threadId, int httpStatus, String responseBody) {}
    private record EndorseScenario(long personId, long floatingId, long permitId, long endorsementApplicationId,
                                  LocalDate originalValidUntil) {}
    private record ErrorStatement(int mysqlErrno, String sqlState, long threadId, long processlistId, String sqlText) {}
    private record GateSettings(String url, String appUser, String appPassword, String adminUser,
                                String adminPassword, String apiTestPassword, int port, String database) {}
}
