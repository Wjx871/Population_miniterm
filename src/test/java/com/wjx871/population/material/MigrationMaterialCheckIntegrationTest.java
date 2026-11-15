package com.wjx871.population.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.person.idcard.PaddleOcrHttpClient;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(properties = {
        "population.person.idcard.ocr.baidu-api-key=check-synthetic-key",
        "population.person.idcard.ocr.baidu-secret-key=check-synthetic-secret"
})
@AutoConfigureMockMvc
class MigrationMaterialCheckIntegrationTest {
    private static final String ID = "99000020000229001X";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired LocalFileStorageService storage;
    @Autowired PaddleOcrHttpClient ocr;
    private final java.util.List<Long> drafts = new java.util.ArrayList<>();
    private RestTemplate rest;
    private org.springframework.http.client.ClientHttpRequestFactory originalFactory;
    private MockRestServiceServer provider;
    private String auth;

    @BeforeEach
    void useMockProvider() throws Exception {
        auth = bearer("household");
        rest = (RestTemplate) ReflectionTestUtils.getField(ocr, "restTemplate");
        originalFactory = rest.getRequestFactory();
        provider = MockRestServiceServer.bindTo(rest).build();
        ReflectionTestUtils.setField(ocr, "accessToken", null);
    }

    @AfterEach
    void restoreProviderAndCleanup() {
        rest.setRequestFactory(originalFactory);
        ReflectionTestUtils.setField(ocr, "accessToken", null);
        for (Long draft : drafts) {
            jdbc.queryForList("SELECT stored_filename FROM application_material WHERE application_id=?", String.class, draft)
                    .forEach(storage::delete);
            jdbc.update("DELETE FROM application_material WHERE application_id=?", draft);
            jdbc.update("DELETE FROM business_application WHERE application_id=?", draft);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "合 成甲, 0.95, 99000020000229001x, MATCH, MATCH, MATCH",
        "合成乙, 0.95, 99000020000229001X, CONFLICT, CONFLICT, MATCH",
        "合成乙, 0.60, 99000020000229001X, UNCERTAIN, UNCERTAIN, MATCH",
        "合成甲, 0.95, 990000200002290028, CONFLICT, MATCH, CONFLICT",
        "合成甲, 0.95, 990000200002290029, UNCERTAIN, MATCH, UNCERTAIN"
    })
    void comparesValuesAndPreservesDraft(String formName, double confidence, String formId, String summary, String name, String number)
            throws Exception {
        long draft = draft();
        long identity = upload(draft, "IDENTITY_PROOF", 1);
        long book = upload(draft, "HOUSEHOLD_BOOK", 2);
        long form = upload(draft, "APPLICATION_FORM", 3);
        expectIdentityAndBook();
        expect("accurate", """
                {"words_result":[
                {"words":"申请人姓名：%s","probability":{"average":%s},"location":{"left":2,"top":4,"width":20,"height":8}},
                {"words":"身份证号：%s","probability":{"average":0.96}}]}
                """.formatted(formName, confidence, formId));

        mvc.perform(check(draft, new MigrationMaterialCheckService.Selection(identity, book, form), auth))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.status").value(summary))
                .andExpect(jsonPath("$.data.nameStatus").value(name))
                .andExpect(jsonPath("$.data.idCardStatus").value(number))
                .andExpect(jsonPath("$.data.documents[0].idCard.value").value("9900**********001X"))
                .andExpect(jsonPath("$.data.documents[2].materialId").value(form))
                .andExpect(jsonPath("$.data.documents[2].name.location.top").value(4))
                .andExpect(content().string(not(containsString(ID))));
        assertThat(jdbc.queryForList("SELECT verify_status FROM application_material WHERE application_id=?",
                String.class, draft)).containsExactly("PENDING", "PENDING", "PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?",
                String.class, draft)).isEqualTo("DRAFT");
        provider.verify();
    }

    @Test
    void reportsOmittedOptionalFormAndStillComparesSelectedImages() throws Exception {
        long draft = draft();
        long identity = upload(draft, "IDENTITY_PROOF", 1);
        long book = upload(draft, "HOUSEHOLD_BOOK", 2);
        expectIdentityAndBook();
        mvc.perform(check(draft, new MigrationMaterialCheckService.Selection(identity, book, null), auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MISSING"))
                .andExpect(jsonPath("$.data.nameStatus").value("MATCH"))
                .andExpect(jsonPath("$.data.idCardStatus").value("MATCH"))
                .andExpect(jsonPath("$.data.documents[2].materialId").doesNotExist());
        provider.verify();
    }

    @Test
    void checksOwnershipTypeAndDuplicatesBeforeCallingProvider() throws Exception {
        long draft = draft();
        long otherDraft = draft();
        long identity = upload(draft, "IDENTITY_PROOF", 1);
        long duplicate = upload(draft, "HOUSEHOLD_BOOK", 1);
        long otherBook = upload(otherDraft, "HOUSEHOLD_BOOK", 2);
        var duplicatePick = new MigrationMaterialCheckService.Selection(identity, duplicate, null);
        mvc.perform(check(draft, duplicatePick, bearer("population"))).andExpect(status().isForbidden());
        mvc.perform(check(draft, duplicatePick, auth)).andExpect(status().isBadRequest());
        mvc.perform(check(draft, new MigrationMaterialCheckService.Selection(identity, otherBook, null), auth))
                .andExpect(status().isBadRequest());
        mvc.perform(check(draft, new MigrationMaterialCheckService.Selection(duplicate, null, null), auth))
                .andExpect(status().isBadRequest());
        provider.verify();
    }

    private void expectIdentityAndBook() {
        provider.expect(requestTo("https://aip.baidubce.com/oauth/2.0/token"))
                .andRespond(withSuccess("{\"access_token\":\"check-test-token\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        expect("idcard", """
                {"image_status":"normal","words_result":{"姓名":{"words":"合成甲"},
                "公民身份号码":{"words":"%s"},"性别":{"words":"男"},"出生":{"words":"20000229"}}}
                """.formatted(ID));
        expect("household_register", """
                {"words_result":{"Name":{"words":"合成甲"},"CardNo":{"words":"%s"}}}
                """.formatted(ID));
    }

    private void expect(String path, String response) {
        provider.expect(requestTo("https://aip.baidubce.com/rest/2.0/ocr/v1/" + path + "?access_token=check-test-token"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private long draft() {
        String number = "CHECK-" + UUID.randomUUID().toString().substring(0, 16);
        jdbc.update("""
                INSERT INTO business_application(application_no,business_type,title,applicant_user_id,status,reason)
                VALUES(?,'MIGRATION_IN','模拟草稿',6,'DRAFT','材料预核对测试')
                """, number);
        long draft = jdbc.queryForObject("SELECT application_id FROM business_application WHERE application_no=?", Long.class, number);
        drafts.add(draft);
        return draft;
    }

    private long upload(long draft, String type, int color) throws Exception {
        var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, color);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        var file = new MockMultipartFile("file", type + ".png", "image/png", bytes.toByteArray());
        String body = mvc.perform(multipart("/api/applications/" + draft + "/materials").file(file)
                        .param("materialType", type).param("materialName", "合成材料").header("Authorization", auth))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("materialId").asLong();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder check(
            long draft, MigrationMaterialCheckService.Selection selection, String token) throws Exception {
        return post("/api/applications/" + draft + "/materials/consistency-check").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(selection));
    }

    private String bearer(String user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).path("data").path("token").asText();
    }
}
