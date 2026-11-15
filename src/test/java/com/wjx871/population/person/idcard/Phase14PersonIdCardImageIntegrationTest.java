package com.wjx871.population.person.idcard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Phase 14 / V4_013 身份证影印本 + 新增人口门控集成测试。 */
@SpringBootTest(properties = {
        "application.upload.dir=./target/test-idcard-images",
        "population.person.idcard.ocr.baidu-api-key=synthetic-test-key",
        "population.person.idcard.ocr.baidu-secret-key=synthetic-test-secret"
})
@AutoConfigureMockMvc
class Phase14PersonIdCardImageIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PaddleOcrHttpClient ocrClient;

    private static final String ID_CARD_FEMALE = "110105196001020047";
    private static final String ID_CARD_MALE   = "110105199001010010";

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM person_idcard_image WHERE original_filename LIKE 'phase14-%'");
        jdbc.update("DELETE FROM person WHERE id_card IN (?, ?)", ID_CARD_FEMALE, ID_CARD_MALE);
    }

    @Test
    void uploadReturnsSkipImageId() throws Exception {
        byte[] body = sampleJpg("phase14-skip");
        MockMultipartFile file = new MockMultipartFile("file", "phase14-skip.jpg", "image/jpeg", body);
        String b = mvc.perform(multipart("/api/persons/idcard-image").file(file).param("skipOcr", "true")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ocrStatus").value("SKIPPED"))
                .andExpect(jsonPath("$.data.imageId").isNumber())
                .andReturn().getResponse().getContentAsString();
        long imageId = json.readTree(b).path("data").path("imageId").asLong();
        Assertions.assertTrue(jdbc.queryForObject("SELECT file_size FROM person_idcard_image WHERE image_id=?", Long.class, imageId) > 0);
    }

    @Test
    void recognizesBaiduFieldsAndClearsThemWhenOwnerChoosesManualEntry() throws Exception {
        RestTemplate rest = (RestTemplate) ReflectionTestUtils.getField(ocrClient, "restTemplate");
        var originalFactory = rest.getRequestFactory();
        var server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("https://aip.baidubce.com/oauth/2.0/token"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("grant_type=client_credentials")))
                .andRespond(withSuccess("{\"access_token\":\"synthetic-token\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://aip.baidubce.com/rest/2.0/ocr/v1/idcard?access_token=synthetic-token"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("id_card_side=front")))
                .andRespond(withSuccess("""
                        {"image_status":"normal","words_result":{
                          "姓名":{"words":"测试样例"},"公民身份号码":{"words":"110105196001020047"},
                          "性别":{"words":"女"},"出生":{"words":"19600102"},
                          "民族":{"words":"汉"},"住址":{"words":"虚构地址"}
                        }}
                        """, MediaType.APPLICATION_JSON));
        try {
            var buffer = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB), "png", buffer);
            var file = new MockMultipartFile("file", "phase14-ocr.png", "image/png", buffer.toByteArray());
            String authorization = bearer();
            String response = mvc.perform(multipart("/api/persons/idcard-image").file(file)
                            .header("Authorization", authorization))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.data.ocrStatus").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.ocrIdcardFull").value(ID_CARD_FEMALE))
                    .andExpect(jsonPath("$.data.ocrName").value("测试样例"))
                    .andExpect(jsonPath("$.data.ocrBirthDate").value("1960-01-02"))
                    .andExpect(jsonPath("$.data.ocrGender").value("F"))
                    .andExpect(jsonPath("$.data.ocrEthnicity").value("汉族"))
                    .andExpect(jsonPath("$.data.ocrAddress").value("虚构地址"))
                    .andExpect(jsonPath("$.data.ocrConfidence").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            long imageId = json.readTree(response).path("data").path("imageId").asLong();
            mvc.perform(get("/api/persons/idcard-image/{id}", imageId).header("Authorization", authorization))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.ocrIdcardFull").doesNotExist())
                    .andExpect(jsonPath("$.data.ocrAddress").doesNotExist());
            mvc.perform(get("/api/persons/idcard-image/{id}", imageId).header("Authorization", viewerBearer()))
                    .andExpect(status().isNotFound());
            mvc.perform(multipart("/api/persons/idcard-image").file(file).param("skipOcr", "true")
                            .header("Authorization", authorization))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.imageId").value(imageId))
                    .andExpect(jsonPath("$.data.ocrStatus").value("SKIPPED"))
                    .andExpect(jsonPath("$.data.ocrIdcardFull").doesNotExist());
            Assertions.assertNull(jdbc.queryForObject(
                    "SELECT ocr_idcard_full FROM person_idcard_image WHERE image_id=?", String.class, imageId));
            server.verify();
        } finally {
            rest.setRequestFactory(originalFactory);
        }
    }

    @Test
    void createWithoutImageReturns400() throws Exception {
        mvc.perform(post("/api/persons").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"无名\",\"gender\":\"F\",\"idCard\":\"" + ID_CARD_FEMALE + "\",\"status\":\"正常\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("身份证影印本")));
    }

    @Test
    void createWithMissingImageIdReturns400() throws Exception {
        mvc.perform(post("/api/persons").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"无名\",\"gender\":\"F\",\"idCard\":\"" + ID_CARD_FEMALE + "\",\"idCardImageId\":99999999,\"status\":\"正常\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("身份证影印本不存在")));
    }

    @Test
    void reuseSameImageIsRejectedAs409() throws Exception {
        long imageId = upload("phase14-reuse");
        String body1 = personPayload(ID_CARD_FEMALE, "F", imageId);
        mvc.perform(post("/api/persons").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isCreated());
        String body2 = personPayload(ID_CARD_MALE, "M", imageId);
        var resp = mvc.perform(post("/api/persons").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().is(409))
                .andReturn();
        String responseBody = resp.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(responseBody).contains("已被使用");
    }

    @Test
    void createBindsImageToPerson() throws Exception {
        long imageId = upload("phase14-bind");
        String b = mvc.perform(post("/api/persons").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(personPayload(ID_CARD_FEMALE, "F", imageId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long personId = json.readTree(b).path("data").path("personId").asLong();
        Long bound = jdbc.queryForObject("SELECT person_id FROM person_idcard_image WHERE image_id=?", Long.class, imageId);
        Assertions.assertEquals(personId, bound);
    }

    @Test
    void guessedForeignDraftCannotBeReadOrBoundButOwnerCanBindIt() throws Exception {
        long imageId = upload("phase14-owner-only", "population");
        int personCount = jdbc.queryForObject("SELECT COUNT(*) FROM person", Integer.class);

        var read = mvc.perform(get("/api/persons/idcard-image/" + imageId)
                        .header("Authorization", viewerBearer()))
                .andReturn();
        Assertions.assertTrue(read.getResponse().getStatus() == 403 || read.getResponse().getStatus() == 404);

        mvc.perform(post("/api/persons").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(personPayload(ID_CARD_FEMALE, "F", imageId)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/persons").header("Authorization", loginBearer("approver"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(personPayload(ID_CARD_FEMALE, "F", imageId)))
                .andExpect(status().isNotFound());
        Assertions.assertEquals(personCount, jdbc.queryForObject("SELECT COUNT(*) FROM person", Integer.class));
        Assertions.assertNull(jdbc.queryForObject(
                "SELECT person_id FROM person_idcard_image WHERE image_id=?", Long.class, imageId));

        mvc.perform(post("/api/persons").header("Authorization", populationBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(personPayload(ID_CARD_FEMALE, "F", imageId)))
                .andExpect(status().isCreated());
        Assertions.assertNotNull(jdbc.queryForObject(
                "SELECT person_id FROM person_idcard_image WHERE image_id=?", Long.class, imageId));
    }

    @Test
    void updateWithoutImageIdLeavesOriginalBound() throws Exception {
        long imageId = upload("phase14-update");
        String b = mvc.perform(post("/api/persons").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(personPayload(ID_CARD_FEMALE, "F", imageId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long personId = json.readTree(b).path("data").path("personId").asLong();
        mvc.perform(put("/api/persons/" + personId).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"女性改名\",\"gender\":\"F\",\"idCard\":\"" + ID_CARD_FEMALE + "\",\"birthDate\":\"1960-01-02\",\"status\":\"正常\"}"))
                .andExpect(status().isOk());
        Long bound = jdbc.queryForObject("SELECT person_id FROM person_idcard_image WHERE image_id=?", Long.class, imageId);
        Assertions.assertEquals(personId, bound);
    }

    @Test
    void updateWithDifferentImageIdIsRejected() throws Exception {
        long imageId1 = upload("phase14-update1");
        long imageId2 = upload("phase14-update2");
        String b = mvc.perform(post("/api/persons").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(personPayload(ID_CARD_FEMALE, "F", imageId1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long personId = json.readTree(b).path("data").path("personId").asLong();
        mvc.perform(put("/api/persons/" + personId).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"女性改名\",\"gender\":\"F\",\"idCard\":\"" + ID_CARD_FEMALE + "\",\"birthDate\":\"1960-01-02\",\"idCardImageId\":" + imageId2 + ",\"status\":\"正常\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("更换")));
    }

    @Test
    void uploadRejectsOversize() throws Exception {
        byte[] body = new byte[2 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "phase14-big.jpg", "image/jpeg", body);
        mvc.perform(multipart("/api/persons/idcard-image").file(file).param("skipOcr", "true")
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadRejectsUnsupportedType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "phase14-bad.txt", "text/plain", "hello".getBytes());
        mvc.perform(multipart("/api/persons/idcard-image").file(file).param("skipOcr", "true")
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void permissionViewerCannotUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "phase14-viewer.jpg", "image/jpeg", sampleJpg("phase14-viewer"));
        mvc.perform(multipart("/api/persons/idcard-image").file(file).param("skipOcr", "true")
                        .header("Authorization", viewerBearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void permissionHouseholdCannotUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "phase14-household.jpg", "image/jpeg", sampleJpg("phase14-household"));
        mvc.perform(multipart("/api/persons/idcard-image").file(file).param("skipOcr", "true")
                        .header("Authorization", householdBearer()))
                .andExpect(status().isForbidden());
    }

    private long upload(String tag) throws Exception {
        return upload(tag, "admin");
    }

    private long upload(String tag, String username) throws Exception {
        byte[] body = sampleJpg(tag);
        MockMultipartFile file = new MockMultipartFile("file", tag + ".jpg", "image/jpeg", body);
        String b = mvc.perform(multipart("/api/persons/idcard-image").file(file).param("skipOcr", "true")
                        .header("Authorization", loginBearer(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(b).path("data").path("imageId").asLong();
    }

    private static byte[] sampleJpg(String tag) {
        byte[] payload = java.util.Arrays.copyOf(tag.getBytes(java.nio.charset.StandardCharsets.UTF_8), 64);
        return payload;
    }

    private static String personPayload(String idCard, String gender, long imageId) {
        return "{\"name\":\"女性\",\"gender\":\"" + gender + "\",\"idCard\":\"" + idCard + "\",\"idCardImageId\":" + imageId + ",\"status\":\"正常\"}";
    }

    private String bearer() throws Exception {
        return loginBearer("admin");
    }

    private String viewerBearer() throws Exception {
        return loginBearer("viewer");
    }

    private String populationBearer() throws Exception {
        return loginBearer("population");
    }

    private String householdBearer() throws Exception {
        return loginBearer("household");
    }

    private String loginBearer(String username) throws Exception {
        String b = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(b).path("data").path("token").asText();
    }
}
