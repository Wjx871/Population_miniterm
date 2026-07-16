package com.wjx871.population.person.idcard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Phase 14 / V4_013 身份证影印本 + 新增人口门控集成测试。 */
@SpringBootTest
@AutoConfigureMockMvc
class Phase14PersonIdCardImageIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

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
        byte[] body = sampleJpg(tag);
        MockMultipartFile file = new MockMultipartFile("file", tag + ".jpg", "image/jpeg", body);
        String b = mvc.perform(multipart("/api/persons/idcard-image").file(file).param("skipOcr", "true")
                        .header("Authorization", bearer()))
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