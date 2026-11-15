package com.wjx871.population.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.person.idcard.PaddleOcrHttpClient;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
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
        "population.person.idcard.ocr.baidu-api-key=household-synthetic-key",
        "population.person.idcard.ocr.baidu-secret-key=household-synthetic-secret"
})
@AutoConfigureMockMvc
class HouseholdBookOcrIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PaddleOcrHttpClient ocr;

    @Test
    void previewsBothPageTypesAndDoesNotSaveMaterial() throws Exception {
        long draft = migration("DRAFT");
        String auth = bearer("household");
        var rest = (RestTemplate) ReflectionTestUtils.getField(ocr, "restTemplate");
        var originalFactory = rest.getRequestFactory();
        var provider = MockRestServiceServer.bindTo(rest).build();
        provider.expect(requestTo("https://aip.baidubce.com/oauth/2.0/token"))
                .andRespond(withSuccess("{\"access_token\":\"household-test-token\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        expectPage(provider, "homepage", """
                {"words_result":{
                  "HouseholderName":{"words":"合成户主"},"HouseholdNum":{"words":"000123"},
                  "Address":{"words":"虚构演示地址"},"HouseholdType":{"words":"家庭户"},
                  "IssueDate":{"words":"2026年1月1日"}
                }}
                """);
        expectPage(provider, "subpage", """
                {"words_result":{
                  "Name":{"words":"合成成员"},"CardNo":{"words":"000000000000000000"},
                  "Relationship":{"words":"子"},"Belief":{"words":"不需要返回"}
                }}
                """);
        expectPage(provider, "subpage", "{\"error_code\":18,\"error_msg\":\"upstream raw error\"}");
        expectPage(provider, "homepage", "{\"error_code\":282103}");
        try {
            mvc.perform(multipart(url(draft)).file(image()).param("pageType", "homepage").header("Authorization", auth))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.data.page").value("homepage"))
                    .andExpect(jsonPath("$.data.pageNumber").value(1))
                    .andExpect(jsonPath("$.data.fields[1].value").value("合成户主"))
                    .andExpect(jsonPath("$.data.fields[2].value").value("000123"));
            mvc.perform(multipart(url(draft)).file(image()).param("pageType", "subpage").header("Authorization", auth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fields.length()").value(3))
                    .andExpect(jsonPath("$.data.fields[0].value").value("合成成员"))
                    .andExpect(jsonPath("$.data.warnings[0]").value(containsString("未通过校验")));
            mvc.perform(multipart(url(draft)).file(image()).param("pageType", "subpage").header("Authorization", auth))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.message").value(containsString("限流")));
            mvc.perform(multipart(url(draft)).file(image()).param("pageType", "homepage").header("Authorization", auth))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value(containsString("未匹配户口簿模板")));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_material WHERE application_id=?",
                    Integer.class, draft)).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?",
                    String.class, draft)).isEqualTo("DRAFT");
            provider.verify();
        } finally {
            rest.setRequestFactory(originalFactory);
        }
    }

    @Test
    void rejectsOtherOwnersSubmittedDraftsAndNonImagesBeforeRecognition() throws Exception {
        long draft = migration("DRAFT");
        long submitted = migration("SUBMITTED");
        var rest = (RestTemplate) ReflectionTestUtils.getField(ocr, "restTemplate");
        var originalFactory = rest.getRequestFactory();
        var provider = MockRestServiceServer.bindTo(rest).build();
        try {
            mvc.perform(multipart(url(draft)).file(image()).param("pageType", "homepage")
                            .header("Authorization", bearer("population")))
                    .andExpect(status().isForbidden());
            mvc.perform(multipart(url(submitted)).file(image()).param("pageType", "homepage")
                            .header("Authorization", bearer("household")))
                    .andExpect(status().isConflict());
            var pdf = new MockMultipartFile("file", "example.pdf", "application/pdf", new byte[]{1});
            mvc.perform(multipart(url(draft)).file(pdf).param("pageType", "homepage")
                            .header("Authorization", bearer("household")))
                    .andExpect(status().isBadRequest());
            provider.verify();
        } finally {
            rest.setRequestFactory(originalFactory);
        }
    }

    @Test
    void previewsApplicationFormWithLinePositionsAndDoesNotChooseBetweenConflictingFields() throws Exception {
        long draft = migration("DRAFT");
        var rest = (RestTemplate) ReflectionTestUtils.getField(ocr, "restTemplate");
        var originalFactory = rest.getRequestFactory();
        var provider = MockRestServiceServer.bindTo(rest).build();
        // 每次测试单独控制模拟的令牌和请求。
        ReflectionTestUtils.setField(ocr, "accessToken", null);
        provider.expect(requestTo("https://aip.baidubce.com/oauth/2.0/token"))
                .andRespond(withSuccess("{\"access_token\":\"form-test-token\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        provider.expect(requestTo("https://aip.baidubce.com/rest/2.0/ocr/v1/accurate?access_token=form-test-token"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .string(containsString("probability=true")))
                .andRespond(withSuccess("""
                        {"words_result":[
                          {"words":"申请人姓名：测试人员","location":{"left":10,"top":40,"width":120,"height":20},
                           "probability":{"average":0.95}},
                          {"words":"身份证号","location":{"left":10,"top":80,"width":75,"height":20}},
                          {"words":"99000020000229001X","location":{"left":100,"top":80,"width":190,"height":20}},
                          {"words":"迁出地：虚构出发地"},
                          {"words":"迁入地：虚构甲地"},
                          {"words":"迁入地址：虚构乙地"},
                          {"words":"申请日期：2026-11-15"},
                          {"words":"迁移原因：工作","probability":{"average":0.72}},
                          {"words":"手机号码：虚构号码"}
                        ]}
                        """, MediaType.APPLICATION_JSON));
        String formUrl = "/api/applications/" + draft + "/materials/migration-form-ocr";
        try {
            mvc.perform(multipart(formUrl).file(image()).header("Authorization", bearer("population")))
                    .andExpect(status().isForbidden());
            mvc.perform(multipart(formUrl).file(image()).header("Authorization", bearer("household")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.data.page").value("applicationForm"))
                    .andExpect(jsonPath("$.data.fields.length()").value(5))
                    .andExpect(jsonPath("$.data.fields[0].location.top").value(40))
                    .andExpect(jsonPath("$.data.fields[1].location.left").value(100))
                    .andExpect(jsonPath("$.data.warnings[0]").value(containsString("不同的迁入地")))
                    .andExpect(jsonPath("$.data.warnings[1]").value(containsString("置信度较低")));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_material WHERE application_id=?",
                    Integer.class, draft)).isZero();
            provider.verify();
        } finally {
            rest.setRequestFactory(originalFactory);
            ReflectionTestUtils.setField(ocr, "accessToken", null);
        }
    }

    private static void expectPage(MockRestServiceServer provider, String page, String body) {
        provider.expect(requestTo("https://aip.baidubce.com/rest/2.0/ocr/v1/household_register?access_token=household-test-token"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .string(containsString("household_register_side=" + page)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private long migration(String status) {
        String number = "OCR-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO business_application(application_no,business_type,title,applicant_user_id,status,reason)
                VALUES(?,'MIGRATION_IN','户口簿测试',6,?,'测试')
                """, number, status);
        return jdbc.queryForObject("SELECT application_id FROM business_application WHERE application_no=?",
                Long.class, number);
    }

    private String bearer(String user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).path("data").path("token").asText();
    }

    private static MockMultipartFile image() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("file", "household.png", "image/png", bytes.toByteArray());
    }

    private static String url(long id) {
        return "/api/applications/" + id + "/materials/household-ocr";
    }
}
