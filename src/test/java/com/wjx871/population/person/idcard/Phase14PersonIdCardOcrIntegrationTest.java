package com.wjx871.population.person.idcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.assertj.core.api.SoftAssertions;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

/** Phase 14 OCR transport, response validation and persistence behavior using a local HTTP stub. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(properties = {
        "population.person.idcard.ocr.url=http://ocr.test",
        "population.person.idcard.ocr.path=/recognize/idcard",
        "application.upload.dir=./target/test-uploads"
})
@AutoConfigureMockMvc
class Phase14PersonIdCardOcrIntegrationTest {

    private static final String OCR_URL = "http://ocr.test/recognize/idcard";
    private static final String SYNTHETIC_ID_CARD = "110105199001010010";
    private static final String MASKED_ID_CARD = "1101**********0010";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PaddleOcrHttpClient ocrClient;

    private MockRestServiceServer ocrStub;

    @BeforeAll
    void bindLocalOcrStub() {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(ocrClient, "restTemplate");
        ocrStub = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @BeforeEach
    void resetOcrStub() {
        ocrStub.reset();
        jdbc.update("DELETE FROM person_idcard_image WHERE original_filename LIKE 'phase14-ocr-security-%'");
        jdbc.update("DELETE FROM business_application WHERE application_no LIKE 'IDCARD-SEC-%'");
        jdbc.update("DELETE FROM person WHERE id_card=?", SYNTHETIC_ID_CARD);
        jdbc.update("DELETE FROM operation_log WHERE operation_type='PERSON_IDCARD_IMAGE_RECOGNIZED'");
    }

    @AfterEach
    void verifyOcrStub() {
        ocrStub.verify();
    }

    @Test
    void successfulOcrStoresSyntheticFields() throws Exception {
        ocrStub.expect(requestTo(OCR_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "result": {
                            "idCard": "110105199001010010",
                            "name": "合成样例",
                            "birthDate": "1990-01-01",
                            "gender": "F",
                            "ethnicity": "测试",
                            "address": "测试地址",
                            "confidence": 0.99
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        JsonNode upload = uploadWithoutSkippingOcr();
        long imageId = upload.path("imageId").asLong();
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT ocr_status AS status, ocr_idcard_full AS id_card,
                       ocr_idcard_masked AS masked_id, ocr_name AS name,
                       ocr_birth_date AS birth_date, ocr_gender AS gender,
                       ocr_ethnicity AS ethnicity, ocr_address AS address
                FROM person_idcard_image WHERE image_id=?
                """, imageId);

        assertThat(row.get("status")).isEqualTo("SUCCESS");
        assertThat(row.get("id_card")).isEqualTo(SYNTHETIC_ID_CARD);
        assertThat(row.get("masked_id")).isEqualTo(MASKED_ID_CARD);
        assertThat(upload.path("ocrIdcardMasked").asText()).isEqualTo(MASKED_ID_CARD);
        assertThat(upload.path("ocrIdcardFull").asText()).isEqualTo(SYNTHETIC_ID_CARD);
        assertThat(row.get("name")).isEqualTo("合成样例");
        assertThat(((Date) row.get("birth_date")).toLocalDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(row.get("gender")).isEqualTo("F");
        assertThat(row.get("ethnicity")).isEqualTo("测试");
        assertThat(row.get("address")).isEqualTo("测试地址");
    }

    @Test
    void viewerCannotReadUnmaskedOcrFieldsFromImageDetails() throws Exception {
        ocrStub.expect(requestTo(OCR_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "result": {
                            "idCard": "110105199001010010",
                            "name": "合成样例",
                            "birthDate": "1990-01-01",
                            "gender": "F",
                            "ethnicity": "测试",
                            "address": "测试地址",
                            "confidence": 0.99
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        long imageId = uploadWithoutSkippingOcr().path("imageId").asLong();
        var response = mvc.perform(get("/api/persons/idcard-image/" + imageId)
                        .header("Authorization", "Bearer " + loginBearer("viewer")))
                .andReturn();
        assertThat(response.getResponse().getStatus()).isIn(403, 404);
        assertThat(response.getResponse().getContentAsString()).doesNotContain(SYNTHETIC_ID_CARD, "测试地址");
    }

    @Test
    void boundImageUsesPersonScopeAndIndependentSensitivePermission() throws Exception {
        ocrStub.expect(requestTo(OCR_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(syntheticOcrResponse(), MediaType.APPLICATION_JSON));

        JsonNode upload = uploadWithoutSkippingOcr();
        long imageId = upload.path("imageId").asLong();
        assertThat(upload.path("ocrIdcardFull").asText()).isEqualTo(SYNTHETIC_ID_CARD);
        assertThat(upload.path("ocrAddress").asText()).isEqualTo("测试地址");
        createScopedPerson(imageId);

        for (String username : new String[] {"population", "viewer", "approver"}) {
            JsonNode detail = detail(imageId, username);
            assertThat(detail.path("ocrIdcardFull").isNull()).isTrue();
            assertThat(detail.path("ocrIdcardMasked").asText()).isEqualTo(MASKED_ID_CARD);
            assertThat(detail.path("ocrAddress").asText()).isEqualTo("详细地址已隐藏");
        }

        for (String username : new String[] {"household", "admin"}) {
            JsonNode detail = detail(imageId, username);
            assertThat(detail.path("ocrIdcardFull").asText()).isEqualTo(SYNTHETIC_ID_CARD);
            assertThat(detail.path("ocrAddress").asText()).isEqualTo("测试地址");
        }

        var outsideScope = mvc.perform(get("/api/persons/idcard-image/" + imageId)
                        .header("Authorization", "Bearer " + loginBearer("approver-other")))
                .andReturn();
        assertThat(outsideScope.getResponse().getStatus()).isIn(403, 404);
        assertThat(outsideScope.getResponse().getContentAsString()).doesNotContain(SYNTHETIC_ID_CARD, "测试地址");
    }

    @Test
    void duplicateFromAnotherUploaderCannotReadOrChangeOriginalDraftOrFileReference() throws Exception {
        byte[] content = "phase14-ocr-security-cross-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String filename = "phase14-ocr-security-cross-owner.jpg";
        long imageId = uploadedImageId(filename, content, "population", true);
        Map<String, Object> before = imageState(imageId);
        Set<String> filesBefore = uploadFiles();

        var duplicate = uploadImage(filename, content, "approver", false);
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(imageState(imageId)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person_idcard_image WHERE file_sha256=?", Integer.class,
                before.get("file_sha256"))).isOne();
        assertThat(Files.isRegularFile(Path.of(String.valueOf(before.get("storage_path"))))).isTrue();
        assertThat(uploadFiles()).isEqualTo(filesBefore);
    }

    @Test
    void sameUploaderDuplicateOfBoundImageCannotChangeOcrStateOrFileReference() throws Exception {
        ocrStub.expect(requestTo(OCR_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(syntheticOcrResponse(), MediaType.APPLICATION_JSON));

        byte[] content = "phase14-ocr-security-bound-image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String filename = "phase14-ocr-security-bound.jpg";
        long imageId = uploadedImageId(filename, content, "population", false);
        createScopedPerson(imageId);
        Map<String, Object> before = imageState(imageId);
        Set<String> filesBefore = uploadFiles();

        var skippedDuplicate = uploadImage(filename, content, "population", true);
        var retriedDuplicate = uploadImage(filename, content, "population", false);
        assertThat(skippedDuplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(retriedDuplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(imageState(imageId)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person_idcard_image WHERE file_sha256=?", Integer.class,
                before.get("file_sha256"))).isOne();
        assertThat(Files.isRegularFile(Path.of(String.valueOf(before.get("storage_path"))))).isTrue();
        assertThat(uploadFiles()).isEqualTo(filesBefore);
    }

    @Test
    void httpErrorBodyDoesNotLeakIntoUploadDatabaseDetailOrAudit() throws Exception {
        String syntheticId = "SYNTHETIC_IDCARD_TEST_0001";
        String syntheticAddress = "SYNTHETIC_ADDRESS_TEST_ONLY";
        String syntheticKey = "TEST_ONLY_SECRET_KEY_DO_NOT_LEAK";
        String errorBody = "{\"debug\":\"" + syntheticId + " " + syntheticAddress + " " + syntheticKey + "\"}";
        ocrStub.expect(requestTo(OCR_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorBody));

        String filename = "phase14-ocr-security-http-error.jpg";
        var uploadResponse = uploadImage(filename,
                "synthetic HTTP error sample".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "population", false);
        assertThat(uploadResponse.getResponse().getStatus()).isEqualTo(200);
        JsonNode uploadData = json.readTree(uploadResponse.getResponse().getContentAsString()).path("data");
        long imageId = uploadData.path("imageId").asLong();
        String databaseError = jdbc.queryForObject(
                "SELECT ocr_error FROM person_idcard_image WHERE image_id=?", String.class, imageId);
        String detailBody = mvc.perform(get("/api/persons/idcard-image/" + imageId)
                        .header("Authorization", "Bearer " + loginBearer("population")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String auditError = jdbc.queryForObject("""
                SELECT error_message FROM operation_log
                WHERE operation_type='PERSON_IDCARD_IMAGE_RECOGNIZED'
                ORDER BY log_id DESC LIMIT 1
                """, String.class);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(uploadData.path("ocrStatus").asText()).isEqualTo("FAILED");
        softly.assertThat(uploadData.path("ocrError").asText())
                .as("upload response")
                .isEqualTo("OCR_HTTP_502")
                .doesNotContain(syntheticId, syntheticAddress, syntheticKey);
        softly.assertThat(databaseError)
                .as("ocr_error database field")
                .isEqualTo("OCR_HTTP_502")
                .doesNotContain(syntheticId, syntheticAddress, syntheticKey);
        softly.assertThat(detailBody)
                .as("image detail response")
                .doesNotContain(syntheticId, syntheticAddress, syntheticKey);
        softly.assertThat(json.readTree(detailBody).path("data").path("ocrError").asText())
                .as("detail error category")
                .isEqualTo("OCR_HTTP_502");
        softly.assertThat(auditError)
                .as("operation audit error_message")
                .isEqualTo("OCR_HTTP_502")
                .doesNotContain(syntheticId, syntheticAddress, syntheticKey);
        softly.assertAll();
    }

    @Test
    void unavailableOcrKeepsUploadedImageAndMarksRecognitionFailed() throws Exception {
        ocrStub.expect(requestTo(OCR_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new IOException("controlled OCR outage")));

        JsonNode upload = uploadWithoutSkippingOcr();
        long imageId = upload.path("imageId").asLong();
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT ocr_status AS status, ocr_idcard_full AS id_card,
                       file_sha256 AS sha256
                FROM person_idcard_image WHERE image_id=?
                """, imageId);

        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("id_card")).isNull();
        assertThat(row.get("sha256")).isNotNull();
        assertThat(upload.path("ocrStatus").asText()).isEqualTo("FAILED");
        assertThat(upload.path("ocrIdcardFull").isNull()).isTrue();
        assertThat(upload.path("ocrError").asText()).isEqualTo("OCR_UNAVAILABLE");
    }

    @Test
    void invalidOcrPayloadKeepsImageAndMarksRecognitionFailed() throws Exception {
        ocrStub.expect(requestTo(OCR_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"result\":[]}", MediaType.APPLICATION_JSON));

        JsonNode upload = uploadWithoutSkippingOcr();
        long imageId = upload.path("imageId").asLong();
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT ocr_status AS status, ocr_idcard_full AS id_card,
                       file_sha256 AS sha256
                FROM person_idcard_image WHERE image_id=?
                """, imageId);

        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("id_card")).isNull();
        assertThat(row.get("sha256")).isNotNull();
        assertThat(upload.path("ocrStatus").asText()).isEqualTo("FAILED");
        assertThat(upload.path("ocrIdcardFull").isNull()).isTrue();
        assertThat(upload.path("ocrError").asText()).isEqualTo("OCR_INVALID_RESPONSE");
    }

    private JsonNode uploadWithoutSkippingOcr() throws Exception {
        String fileName = "phase14-ocr-" + UUID.randomUUID() + ".jpg";
        byte[] content = ("synthetic image " + fileName).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var response = uploadImage(fileName, content, "population", false);
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = json.readTree(response.getResponse().getContentAsString()).path("data");
        JsonNode imageId = data.path("imageId");
        assertThat(imageId.isNumber()).isTrue();
        return data;
    }

    private org.springframework.test.web.servlet.MvcResult uploadImage(
            String filename, byte[] content, String username, boolean skipOcr) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "image/jpeg", content);
        var request = multipart("/api/persons/idcard-image")
                .file(file)
                .header("Authorization", "Bearer " + loginBearer(username));
        if (skipOcr) request.param("skipOcr", "true");
        return mvc.perform(request).andReturn();
    }

    private long uploadedImageId(String filename, byte[] content, String username, boolean skipOcr) throws Exception {
        var response = uploadImage(filename, content, username, skipOcr);
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(response.getResponse().getContentAsString()).path("data").path("imageId").asLong();
    }

    private JsonNode detail(long imageId, String username) throws Exception {
        String body = mvc.perform(get("/api/persons/idcard-image/" + imageId)
                        .header("Authorization", "Bearer " + loginBearer(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data");
    }

    private void createScopedPerson(long imageId) throws Exception {
        String request = "{\"name\":\"合成样例\",\"gender\":\"M\",\"idCard\":\""
                + SYNTHETIC_ID_CARD + "\",\"birthDate\":\"1990-01-01\",\"idCardImageId\":"
                + imageId + ",\"status\":\"正常\"}";
        String body = mvc.perform(post("/api/persons")
                        .header("Authorization", "Bearer " + loginBearer("population"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long personId = json.readTree(body).path("data").path("personId").asLong();
        jdbc.update("""
                INSERT INTO business_application(
                    application_no,business_type,title,applicant_user_id,applicant_department_id,
                    applicant_region_code,target_person_id,status,reason,version)
                VALUES(?, 'KEY_POPULATION_RELEASE', '影印本范围测试', 2, 2, '110000', ?, 'COMPLETED', '测试', 0)
                """, "IDCARD-SEC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20), personId);
    }

    private Map<String, Object> imageState(long imageId) {
        return jdbc.queryForMap("""
                SELECT user_id, person_id, ocr_status, ocr_idcard_full, ocr_idcard_masked,
                       ocr_name, ocr_birth_date, ocr_address, ocr_error, file_sha256,
                       stored_filename, storage_path
                FROM person_idcard_image WHERE image_id=?
                """, imageId);
    }

    private Set<String> uploadFiles() throws IOException {
        Path directory = Path.of("target/test-uploads").toAbsolutePath();
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    private String syntheticOcrResponse() {
        return """
                {
                  "result": {
                    "idCard": "%s",
                    "name": "合成样例",
                    "birthDate": "1990-01-01",
                    "gender": "M",
                    "ethnicity": "测试",
                    "address": "测试地址",
                    "confidence": 0.99
                  }
                }
                """.formatted(SYNTHETIC_ID_CARD);
    }

    private String loginBearer(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("token").asText();
    }
}
