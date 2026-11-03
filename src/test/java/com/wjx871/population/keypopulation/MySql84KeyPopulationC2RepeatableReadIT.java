package com.wjx871.population.keypopulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

/** Explicit MySQL 8.4 gate for C2 duplicate-in-flight behavior and RR/RC read-view comparison. */
@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.hikari.maximum-pool-size=12",
        "population.redis.enabled=false",
        "policy.ai.enabled=false"
})
@AutoConfigureMockMvc
class MySql84KeyPopulationC2RepeatableReadIT {
    private static final Pattern URL = Pattern.compile(
            "^jdbc:mysql://127\\.0\\.0\\.1:(?<port>[0-9]+)/(?<database>[A-Za-z0-9_]+)\\?(?<options>.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final String OPEN_STATUS_SQL = "'DRAFT','SUBMITTED','UNDER_REVIEW','APPROVED','RETURNED'";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockBean KeyPopulationTransactionHook hook;

    private GateSettings gate;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry properties) {
        GateSettings gate = settings();
        properties.add("spring.datasource.url", gate::url);
        properties.add("spring.datasource.username", gate::appUser);
        properties.add("spring.datasource.password", gate::appPassword);
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    private static GateSettings settings() {
        String url = required("MYSQL84_GATE_DB_URL");
        String appUser = required("MYSQL84_GATE_DB_USERNAME");
        String appPassword = required("MYSQL84_GATE_DB_PASSWORD");
        String adminUser = required("MYSQL84_GATE_ADMIN_USERNAME");
        String adminPassword = required("MYSQL84_GATE_ADMIN_PASSWORD");
        Matcher match = URL.matcher(url);
        if (!match.matches()) throw new IllegalStateException("C2 gate URL must use 127.0.0.1 and explicit port/schema.");
        int port = Integer.parseInt(match.group("port"));
        String database = match.group("database");
        if (port == 3306 || !database.matches("(?i)population_miniterm_mysql_gate_[a-z0-9_]+")
                || !Pattern.compile("(?:^|&)createDatabaseIfNotExist=false(?:&|$)", Pattern.CASE_INSENSITIVE)
                        .matcher(match.group("options")).find()
                || appUser.equalsIgnoreCase("root") || appUser.equalsIgnoreCase(adminUser)) {
            throw new IllegalStateException("C2 gate requires a named loopback schema and separate non-root app/admin users.");
        }
        return new GateSettings(url, appUser, appPassword, adminUser, adminPassword, port, database);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set " + name + " for the explicit MySQL gate.");
        return value;
    }

    @BeforeEach
    void verifyDedicatedMysqlIdentity() {
        reset(hook);
        gate = settings();
        Map<String, Object> identity = jdbc.queryForMap("SELECT @@version version,@@port port,@@bind_address bind_address,"
                + "@@transaction_isolation isolation_level,DATABASE() database_name,CURRENT_USER() database_user");
        assertThat(String.valueOf(identity.get("version"))).startsWith("8.4.");
        assertThat(((Number) identity.get("port")).intValue()).isEqualTo(gate.port());
        assertThat(String.valueOf(identity.get("bind_address"))).isEqualTo("127.0.0.1");
        assertThat(identity.get("database_name")).isEqualTo(gate.database());
        assertThat(String.valueOf(identity.get("database_user"))).startsWith(gate.appUser() + "@");
        assertThat(String.valueOf(identity.get("isolation_level"))).isEqualTo("REPEATABLE-READ");
    }

    @Test
    void samePersonRegisterApplicationsSerializeForTenMySqlRounds() throws Exception {
        String authorization = bearer(token("admin"));
        try (Connection admin = adminConnection()) {
            for (int round = 1; round <= 10; round++) {
                long personId = seedPerson("register-" + round);
                long auditBefore = operationCount("KEY_POPULATION_REGISTER_CREATE");
                RaceResult result = raceCreate(authorization, "/api/key-populations/register-applications",
                        registerBody(personId), personId, null, "REGISTER", auditBefore, admin);
                assertOneSerializedCreation(result, "REGISTER", round, personId, auditBefore);
            }
        }
    }

    @Test
    void sameRecordReleaseApplicationsSerializeForTenMySqlRounds() throws Exception {
        String authorization = bearer(token("admin"));
        try (Connection admin = adminConnection()) {
            for (int round = 1; round <= 10; round++) {
                long personId = seedPerson("release-" + round);
                long recordId = seedActiveRecord(personId);
                long auditBefore = operationCount("KEY_POPULATION_RELEASE_CREATE");
                RaceResult result = raceCreate(authorization, "/api/key-populations/" + recordId + "/release-applications",
                        releaseBody(), personId, recordId, "RELEASE", auditBefore, admin);
                assertOneSerializedCreation(result, "RELEASE", round, personId, auditBefore);
            }
        }
    }

    @Test
    void returnedRegisterAndReleaseApplicationsBlockDuplicates() throws Exception {
        String authorization = bearer(token("admin"));
        long registerPerson = seedPerson("returned-register");
        long registerBeforeAudit = operationCount("KEY_POPULATION_REGISTER_CREATE");
        long registerApplication = insertApplication(registerPerson, null, "KEY_POPULATION_REGISTER", "RETURNED");
        insertKeyApplication(registerApplication, registerPerson, null, "REGISTER", "RETURNED");
        MvcResult register = mvc.perform(post("/api/key-populations/register-applications")
                        .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(registerPerson))).andReturn();
        assertThat(register.getResponse().getStatus()).isEqualTo(409);
        assertThat(count("key_population_application", "person_id", registerPerson)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application WHERE target_person_id=? "
                + "AND business_type='KEY_POPULATION_REGISTER' AND status='RETURNED'", Integer.class, registerPerson)).isOne();
        assertThat(operationCount("KEY_POPULATION_REGISTER_CREATE")).isEqualTo(registerBeforeAudit);

        long releasePerson = seedPerson("returned-release");
        long recordId = seedActiveRecord(releasePerson);
        long releaseBeforeAudit = operationCount("KEY_POPULATION_RELEASE_CREATE");
        long releaseApplication = insertApplication(releasePerson, null, "KEY_POPULATION_RELEASE", "RETURNED");
        insertKeyApplication(releaseApplication, releasePerson, recordId, "RELEASE", "RETURNED");
        MvcResult release = mvc.perform(post("/api/key-populations/" + recordId + "/release-applications")
                        .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                        .content(releaseBody())).andReturn();
        assertThat(release.getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM key_population_application WHERE record_id=? AND operation_type='RELEASE'",
                Integer.class, recordId)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application WHERE target_person_id=? "
                + "AND business_type='KEY_POPULATION_RELEASE' AND status='RETURNED'", Integer.class, releasePerson)).isOne();
        assertThat(operationCount("KEY_POPULATION_RELEASE_CREATE")).isEqualTo(releaseBeforeAudit);
    }

    @Test
    void earlierConsistentReadShowsRrSnapshotBoundaryAndRcControl() throws Exception {
        SnapshotResult rr = compareReadView(Connection.TRANSACTION_REPEATABLE_READ, "REPEATABLE-READ");
        assertThat(rr.lockWaitObserved()).isTrue();
        assertThat(rr.firstRead()).isZero();
        assertThat(rr.countAfterLock()).as("a consistent RR read view created before lock wait stays old").isZero();
        assertThat(rr.committedOpenRows()).isOne();

        SnapshotResult rc = compareReadView(Connection.TRANSACTION_READ_COMMITTED, "READ-COMMITTED");
        assertThat(rc.lockWaitObserved()).isTrue();
        assertThat(rc.firstRead()).isZero();
        assertThat(rc.countAfterLock()).as("RC sees the row committed while the transaction waited").isOne();
        assertThat(rc.committedOpenRows()).isOne();
    }

    private RaceResult raceCreate(String authorization, String path, String body, long personId, Long recordId,
                                  String operation, long auditBefore, Connection admin) throws Exception {
        reset(hook);
        CountDownLatch firstAtCheck = new CountDownLatch(1);
        CountDownLatch secondAtCheck = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger checkNumber = new AtomicInteger();
        doAnswer(invocation -> {
            if (checkNumber.getAndIncrement() == 0) {
                firstAtCheck.countDown();
                if (!releaseFirst.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("C2 gate release timeout");
            } else {
                secondAtCheck.countDown();
            }
            return null;
        }).when(hook).afterOpenApplicationCheck();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<HttpOutcome> first = null;
        Future<HttpOutcome> second = null;
        try {
            first = pool.submit(() -> postOutcome(path, body, authorization));
            assertThat(firstAtCheck.await(10, TimeUnit.SECONDS)).as("first transaction reached duplicate-check barrier").isTrue();
            second = pool.submit(() -> postOutcome(path, body, authorization));
            LockWait wait = awaitLockWait(admin, Duration.ofSeconds(8));
            boolean secondPassedCheckBeforeFirstCommit = secondAtCheck.getCount() == 0;
            releaseFirst.countDown();
            HttpOutcome firstResult = first.get(20, TimeUnit.SECONDS);
            HttpOutcome secondResult = second.get(20, TimeUnit.SECONDS);
            long details = operation.equals("REGISTER")
                    ? jdbc.queryForObject("SELECT COUNT(*) FROM key_population_application WHERE person_id=? "
                            + "AND population_type='OTHER' AND operation_type='REGISTER'", Long.class, personId)
                    : jdbc.queryForObject("SELECT COUNT(*) FROM key_population_application WHERE record_id=? AND operation_type='RELEASE'",
                            Long.class, recordId);
            long businessApps = jdbc.queryForObject("SELECT COUNT(*) FROM business_application WHERE target_person_id=? "
                    + "AND business_type=?", Long.class, personId,
                    operation.equals("REGISTER") ? "KEY_POPULATION_REGISTER" : "KEY_POPULATION_RELEASE");
            long audit = operationCount("KEY_POPULATION_" + operation + "_CREATE");
            String result = String.format(
                    "C2_RACE operation=%s person=%d isolation=REPEATABLE-READ connections=%s lockWait=%s secondPassedCheckBeforeCommit=%s statuses=[%d,%d] sqlStates=[%s,%s] mysqlErrors=[%s,%s] details=%d businessApps=%d auditDelta=%d%n",
                    operation, personId, wait == null ? "unobserved" : wait.waitingConnection() + "/" + wait.blockingConnection(),
                    wait != null, secondPassedCheckBeforeFirstCommit,
                    firstResult.status(), secondResult.status(), firstResult.sqlState(), secondResult.sqlState(),
                    firstResult.mysqlError(), secondResult.mysqlError(), details, businessApps,
                    audit - auditBefore);
            System.out.print(result);
            return new RaceResult(firstResult, secondResult, wait, secondPassedCheckBeforeFirstCommit,
                    details, businessApps, audit);
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            reset(hook);
        }
    }

    private void assertOneSerializedCreation(RaceResult result, String operation, int round, long personId, long auditBefore) {
        assertThat(result.lockWait()).as("round " + round + " must show the competing transaction waiting on MySQL row locks").isNotNull();
        assertThat(result.lockWait().waitingConnection()).isNotEqualTo(result.lockWait().blockingConnection());
        assertThat(result.secondPassedCheck()).as("second request must not pass the duplicate check before the first commit").isFalse();
        assertThat(List.of(result.first().status(), result.second().status())).containsExactlyInAnyOrder(201, 409);
        assertThat(result.first().status()).isNotEqualTo(500);
        assertThat(result.second().status()).isNotEqualTo(500);
        assertThat(result.details()).isOne();
        assertThat(result.businessApps()).isOne();
        assertThat(result.audit()).isEqualTo(auditBefore + 1);
    }

    private SnapshotResult compareReadView(int isolation, String label) throws Exception {
        long personId = seedPerson("snapshot-" + label);
        String applicationNo = "R4C2X" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = adminConnection(); Connection first = appConnection(); Connection second = appConnection()) {
            first.setTransactionIsolation(isolation);
            second.setTransactionIsolation(isolation);
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            long firstId = connectionId(first);
            long secondId = connectionId(second);
            assertThat(firstId).isNotEqualTo(secondId);
            assertThat(transactionIsolation(second)).isEqualTo(label);
            lockPerson(first, personId);
            int firstRead = openRegisterCount(second, personId);
            assertThat(firstRead).isZero();

            ExecutorService pool = Executors.newSingleThreadExecutor();
            Future<?> waitForPerson = pool.submit(() -> {
                try { lockPerson(second, personId); }
                catch (SQLException e) { throw new RuntimeException(e); }
            });
            try {
                LockWait wait = awaitLockWait(admin, Duration.ofSeconds(8));
                insertOpenRegister(first, applicationNo, personId);
                first.commit();
                waitForPerson.get(10, TimeUnit.SECONDS);
                int countAfterLock = openRegisterCount(second, personId);
                second.rollback();
                long committed = jdbc.queryForObject("SELECT COUNT(*) FROM key_population_application d JOIN business_application a "
                        + "ON a.application_id=d.application_id WHERE d.person_id=? AND d.operation_type='REGISTER' "
                        + "AND d.population_type='OTHER' AND a.status IN(" + OPEN_STATUS_SQL + ")", Long.class, personId);
                System.out.printf("C2_READ_VIEW isolation=%s connections=%d/%d lockWait=%s before=%d after=%d committed=%d sqlState=none mysqlError=none%n",
                        label, firstId, secondId, wait != null, firstRead, countAfterLock, committed);
                return new SnapshotResult(label, firstId, secondId, wait != null, firstRead, countAfterLock, committed);
            } finally {
                pool.shutdownNow();
                pool.awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }

    private HttpOutcome postOutcome(String path, String body, String authorization) throws Exception {
        MvcResult result = mvc.perform(post(path).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        SQLException sql = findSqlException(result.getResolvedException());
        return new HttpOutcome(result.getResponse().getStatus(), sql == null ? "none" : sql.getSQLState(),
                sql == null ? "none" : String.valueOf(sql.getErrorCode()));
    }

    private SQLException findSqlException(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql) return sql;
        }
        return null;
    }

    private LockWait awaitLockWait(Connection admin, Duration timeout) throws Exception {
        String sql = "SELECT waiting.PROCESSLIST_ID,blocking.PROCESSLIST_ID "
                + "FROM performance_schema.data_lock_waits w "
                + "JOIN performance_schema.threads waiting ON waiting.THREAD_ID=w.REQUESTING_THREAD_ID "
                + "JOIN performance_schema.threads blocking ON blocking.THREAD_ID=w.BLOCKING_THREAD_ID "
                + "WHERE waiting.PROCESSLIST_ID IS NOT NULL AND blocking.PROCESSLIST_ID IS NOT NULL LIMIT 1";
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try (Statement statement = admin.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
                if (rs.next()) return new LockWait(rs.getLong(1), rs.getLong(2));
            }
            Thread.sleep(20);
        }
        return null;
    }

    private Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(gate.url(), gate.adminUser(), gate.adminPassword());
    }

    private Connection appConnection() throws SQLException {
        return DriverManager.getConnection(gate.url(), gate.appUser(), gate.appPassword());
    }

    private long connectionId(Connection connection) throws SQLException {
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT CONNECTION_ID()")) {
            rs.next(); return rs.getLong(1);
        }
    }

    private String transactionIsolation(Connection connection) throws SQLException {
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT @@transaction_isolation")) {
            rs.next(); return rs.getString(1);
        }
    }

    private void lockPerson(Connection connection, long personId) throws SQLException {
        try (PreparedStatement s = connection.prepareStatement("SELECT person_id FROM person WHERE person_id=? FOR UPDATE")) {
            s.setLong(1, personId);
            try (ResultSet rs = s.executeQuery()) { if (!rs.next()) throw new SQLException("Synthetic person missing"); }
        }
    }

    private int openRegisterCount(Connection connection, long personId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM key_population_application d JOIN business_application a "
                + "ON a.application_id=d.application_id WHERE d.person_id=? AND d.population_type='OTHER' "
                + "AND d.operation_type='REGISTER' AND a.status IN(" + OPEN_STATUS_SQL + ")";
        try (PreparedStatement s = connection.prepareStatement(sql)) {
            s.setLong(1, personId);
            try (ResultSet rs = s.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private void insertOpenRegister(Connection connection, String applicationNo, long personId) throws SQLException {
        String sql = "INSERT INTO business_application(application_no,business_type,title,applicant_user_id,applicant_department_id,"
                + "applicant_region_code,target_person_id,status,reason) VALUES(?,?,?,?,?,?,?,'DRAFT','synthetic snapshot test')";
        long appId;
        try (PreparedStatement s = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1, applicationNo); s.setString(2, "KEY_POPULATION_REGISTER"); s.setString(3, "C2 snapshot " + applicationNo);
            s.setLong(4, 1); s.setLong(5, 3); s.setString(6, "110105"); s.setLong(7, personId);
            s.executeUpdate();
            try (ResultSet keys = s.getGeneratedKeys()) { keys.next(); appId = keys.getLong(1); }
        }
        try (PreparedStatement s = connection.prepareStatement("INSERT INTO key_population_application(application_id,operation_type,"
                + "record_id,person_id,population_type,attention_level,reason,event_date,responsible_department_id,responsible_user_id,"
                + "business_status,version) VALUES(?,'REGISTER',NULL,?,'OTHER','MEDIUM','synthetic snapshot test',?,3,1,'DRAFT',0)")) {
            s.setLong(1, appId); s.setLong(2, personId); s.setDate(3, java.sql.Date.valueOf(LocalDate.now().minusDays(1)));
            s.executeUpdate();
        }
    }

    private long seedPerson(String marker) {
        String idCard = "R4C2" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        jdbc.update("INSERT INTO person(name,gender,id_card,birth_date,ethnicity,current_address,status,current_status_code,"
                        + "initial_scope_user_id,initial_scope_department_id,initial_scope_region_code) "
                        + "VALUES(?, 'M', ?, DATE '1990-01-01','HAN','C2 synthetic address','正常','REGISTERED',1,3,'110105')",
                "C2 synthetic " + marker, idCard);
        long personId = jdbc.queryForObject("SELECT person_id FROM person WHERE id_card=?", Long.class, idCard);
        insertScopeApplication(personId, "R4C2S" + UUID.randomUUID().toString().replace("-", ""));
        return personId;
    }

    private void insertScopeApplication(long personId, String applicationNo) {
        jdbc.update("INSERT INTO business_application(application_no,business_type,title,applicant_user_id,applicant_department_id,"
                        + "applicant_region_code,target_person_id,status,reason,completed_at) VALUES(?,'GENERAL_SERVICE',? ,1,3,"
                        + "'110105',?,'COMPLETED','synthetic C2 data-scope seed',CURRENT_TIMESTAMP)",
                applicationNo, "C2 scope seed " + applicationNo, personId);
    }

    private long seedActiveRecord(long personId) {
        jdbc.update("INSERT INTO key_population(person_id,key_type,management_level,register_reason,register_date,"
                        + "responsible_department_id,responsible_user_id,status,version) "
                        + "VALUES(?,'OTHER','MEDIUM','synthetic C2 active record',DATE '2026-01-01',3,1,'ACTIVE',0)", personId);
        return jdbc.queryForObject("SELECT key_id FROM key_population WHERE person_id=? AND key_type='OTHER' AND status='ACTIVE'",
                Long.class, personId);
    }

    private long insertApplication(long personId, Long recordId, String type, String status) {
        String no = "R4C2A" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO business_application(application_no,business_type,title,applicant_user_id,applicant_department_id,"
                        + "applicant_region_code,target_person_id,status,reason) VALUES(?,?,?,?,?,?,?,?,'synthetic returned seed')",
                no, type, "C2 returned seed " + no, 1, 3, "110105", personId, status);
        return jdbc.queryForObject("SELECT application_id FROM business_application WHERE application_no=?", Long.class, no);
    }

    private void insertKeyApplication(long appId, long personId, Long recordId, String operation, String status) {
        jdbc.update("INSERT INTO key_population_application(application_id,operation_type,record_id,person_id,population_type,"
                        + "attention_level,reason,event_date,responsible_department_id,responsible_user_id,business_status,version) "
                        + "VALUES(?,?,?,?,?,'MEDIUM','synthetic returned seed',?,3,1,?,0)",
                appId, operation, recordId, personId, "OTHER", LocalDate.now().minusDays(1), status);
    }

    private String registerBody(long personId) {
        return "{\"personId\":" + personId + ",\"populationType\":\"OTHER\",\"attentionLevel\":\"MEDIUM\","
                + "\"registerReason\":\"C2 synthetic registration\",\"registerDate\":\"" + LocalDate.now().minusDays(1)
                + "\",\"title\":\"C2 synthetic register\"}";
    }

    private String releaseBody() {
        return "{\"releaseReason\":\"C2 synthetic release\",\"releaseDate\":\"" + LocalDate.now().minusDays(1)
                + "\",\"title\":\"C2 synthetic release\"}";
    }

    private String token(String user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", user, "password", "123456"))))
                .andReturn().getResponse().getContentAsString();
        JsonNode root = json.readTree(body);
        assertThat(root.path("data").path("token").asText()).isNotBlank();
        return root.path("data").path("token").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }
    private long operationCount(String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE operation_type=?", Long.class, type);
    }
    private long count(String table, String column, long value) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?", Long.class, value);
    }

    private record GateSettings(String url, String appUser, String appPassword, String adminUser, String adminPassword,
                                int port, String database) {}
    private record HttpOutcome(int status, String sqlState, String mysqlError) {}
    private record LockWait(long waitingConnection, long blockingConnection) {}
    private record RaceResult(HttpOutcome first, HttpOutcome second, LockWait lockWait, boolean secondPassedCheck,
                              long details, long businessApps, long audit) {}
    private record SnapshotResult(String isolation, long firstConnection, long secondConnection, boolean lockWaitObserved,
                                  int firstRead, int countAfterLock, long committedOpenRows) {}
}
