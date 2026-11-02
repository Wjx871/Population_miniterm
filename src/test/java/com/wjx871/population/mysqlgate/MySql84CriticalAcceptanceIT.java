package com.wjx871.population.mysqlgate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    void sameHouseholdInboundAndLastMemberOutRaceIsSafeAcrossThreeRuns() throws Exception {
        String isolation = jdbc.queryForObject("SELECT @@transaction_isolation", String.class);
        assertThat(isolation).isNotBlank();
        for (int run = 1; run <= 3; run++) {
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
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
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
                assertThat(List.of(outResult.httpStatus(), inResult.httpStatus())).allMatch(status -> List.of(200, 409, 500).contains(status));
                assertThat(outResult.httpStatus() == 200 && inResult.httpStatus() == 200).isFalse();
                assertThat(outResult.threadId()).isNotEqualTo(inResult.threadId());
                assertRaceInvariant(household, head, inboundPerson, out, inbound);
                String failureEvidence = "HTTP_CONFLICT";
                if (outResult.httpStatus() == 500 || inResult.httpStatus() == 500) {
                    assertThat(mysqlErrorCount(settings(), 1213)).isGreaterThan(deadlocksBefore);
                    ErrorStatement deadlock = mysqlErrorStatement(settings(), 1213, "40001");
                    failureEvidence = "MYSQL_DEADLOCK_SQLSTATE=" + deadlock.sqlState() + "_ERRNO=" + deadlock.mysqlErrno() + "_THREAD=" + deadlock.threadId();
                }
                System.out.printf("PASS migration race run=%d isolation=%s workerThreads=[%d,%d] http=[%d,%d] rejected=%s household=%d%n",
                        run, isolation, outResult.threadId(), inResult.threadId(), outResult.httpStatus(),
                        inResult.httpStatus(), failureEvidence, household);
            } finally {
                executor.shutdownNow();
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

    private void assertRaceInvariant(long household, long head, long inboundPerson, long out, long inbound) {
        String householdStatus = jdbc.queryForObject("SELECT status FROM household WHERE household_id=?", String.class, household);
        String outStatus = jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, out);
        String inStatus = jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, inbound);
        long activeMembers = jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND status='ACTIVE'", Long.class, household);
        long activeResidences = jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE household_id=? AND status='ACTIVE'", Long.class, household);
        if ("COMPLETED".equals(outStatus)) {
            assertThat(inStatus).isEqualTo("APPROVED");
            assertThat(householdStatus).isEqualTo("PENDING_CANCELLATION");
            assertThat(activeMembers).isZero();
            assertThat(activeResidences).isZero();
            assertThat(jdbc.queryForObject("SELECT head_person_id FROM household WHERE household_id=?", Long.class, household)).isNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence_archive WHERE application_id=?", Integer.class, out)).isOne();
        } else if ("COMPLETED".equals(inStatus)) {
            assertThat(outStatus).isEqualTo("APPROVED");
            assertThat(householdStatus).isEqualTo("ACTIVE");
            assertThat(activeMembers).isEqualTo(2);
            assertThat(activeResidences).isEqualTo(2);
            assertHeadInvariant(household, head, 1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id=? AND status='ACTIVE'", Integer.class, inboundPerson)).isOne();
        } else {
            assertThat(outStatus).isEqualTo("APPROVED");
            assertThat(inStatus).isEqualTo("APPROVED");
            assertThat(householdStatus).isEqualTo("ACTIVE");
            assertThat(activeMembers).isOne();
            assertThat(activeResidences).isOne();
            assertHeadInvariant(household, head, 1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence_archive WHERE application_id=?", Integer.class, out)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id=? AND status='ACTIVE'", Integer.class, inboundPerson)).isZero();
        }
    }

    private void assertHeadInvariant(long household, long expectedHead, long count) {
        assertThat(jdbc.queryForObject("SELECT head_person_id FROM household WHERE household_id=?", Long.class, household)).isEqualTo(expectedHead);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND status='ACTIVE' AND relationship='HEAD'", Long.class, household)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? AND person_id=? AND status='ACTIVE' AND relationship='HEAD'", Long.class, household, expectedHead)).isOne();
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
             var statement = admin.prepareStatement("SELECT MYSQL_ERRNO,RETURNED_SQLSTATE,THREAD_ID FROM " +
                     "performance_schema.events_statements_history_long WHERE CURRENT_SCHEMA=? AND MYSQL_ERRNO=? " +
                     "AND RETURNED_SQLSTATE=? ORDER BY TIMER_END DESC LIMIT 1")) {
            statement.setString(1, gate.database());
            statement.setInt(2, errorNumber);
            statement.setString(3, sqlState);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("MySQL statement history did not retain the injected SQLSTATE/errno.");
                return new ErrorStatement(result.getInt(1), result.getString(2), result.getLong(3));
            }
        }
    }

    private long generatedId(KeyHolder key) {
        Number id = key.getKey();
        assertThat(id).isNotNull();
        return id.longValue();
    }

    private record Execution(long threadId, int httpStatus, String responseBody) {}
    private record ErrorStatement(int mysqlErrno, String sqlState, long threadId) {}
    private record GateSettings(String url, String appUser, String appPassword, String adminUser,
                                String adminPassword, String apiTestPassword, int port, String database) {}
}
