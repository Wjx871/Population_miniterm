package com.wjx871.population.person.idcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
        String body = mvc.perform(get("/api/persons/idcard-image/" + imageId)
                        .header("Authorization", "Bearer " + loginBearer("viewer")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(SYNTHETIC_ID_CARD, "测试地址");
        assertThat(body).contains(MASKED_ID_CARD);
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
    }

    private JsonNode uploadWithoutSkippingOcr() throws Exception {
        String fileName = "phase14-ocr-" + UUID.randomUUID() + ".jpg";
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, "image/jpeg",
                ("synthetic image " + fileName).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String token = loginBearer("population");
        String body = mvc.perform(multipart("/api/persons/idcard-image")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = json.readTree(body).path("data");
        JsonNode imageId = data.path("imageId");
        assertThat(imageId.isNumber()).isTrue();
        return data;
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
