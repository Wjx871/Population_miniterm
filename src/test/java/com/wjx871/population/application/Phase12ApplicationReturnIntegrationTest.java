package com.wjx871.population.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 12: 已批准申请被退回 (RETURNED) 后申请人补正重新提交、撤回 与 二次审批闭环。
 *
 * <p>区别于审批驳回（REJECTED，会改写审批结论），退回仅变更业务申请侧的状态、保留原审批记录。
 * 审批人不持有 application:return 权限，因此即便录入审批人也无法调用该接口。
 */
@SpringBootTest
@AutoConfigureMockMvc
class Phase12ApplicationReturnIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM sys_approval_log");
        jdbc.update("DELETE FROM sys_approval_request");
        jdbc.update("DELETE FROM application_material");
        jdbc.update("DELETE FROM business_application");
        jdbc.update("DELETE FROM operation_log");
    }

    @Test
    void approverCannotReturnApplication() throws Exception {
        long id = approvedApplication("population");
        mvc.perform(post("/api/applications/" + id + "/return")
                .header("Authorization", bearer(token("approver")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"审批人无权退回\",\"version\":3}"))
            .andExpect(status().isForbidden());
        assertThat(statusOf(id)).isEqualTo("APPROVED");
    }

    @Test
    void householdExecutorReturnsApprovedApplicationAndKeepsApprovalLog() throws Exception {
        long id = approvedApplication("population");
        long approval = approvalId(id);
        // 退回前：approval.status = APPROVED，application.status = APPROVED
        assertThat(statusOf(id)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM sys_approval_request WHERE approval_id = ?", String.class, approval))
            .isEqualTo("APPROVED");

        mvc.perform(post("/api/applications/" + id + "/return")
                .header("Authorization", bearer(token("household")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"材料经复核发现关键字段缺失，请重新补充。\",\"version\":2}"))
            .andExpect(status().isOk());

        // 退回后：application.status = RETURNED，但审批记录仍为 APPROVED，结论不被改写
        assertThat(statusOf(id)).isEqualTo("RETURNED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM sys_approval_request WHERE approval_id = ?", String.class, approval))
            .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_approval_log WHERE application_id = ? AND action = 'RETURN'",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE operation_type = 'APPLICATION_RETURN'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_approval_log WHERE application_id = ? AND action IN ('SUBMIT','APPROVE')",
                Integer.class, id)).isEqualTo(2);
    }

    @Test
    void returnRequiresNonBlankComment() throws Exception {
        long id = approvedApplication("population");
        mvc.perform(post("/api/applications/" + id + "/return")
                .header("Authorization", bearer(token("household")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"\",\"version\":2}"))
            .andExpect(status().isBadRequest());
        assertThat(statusOf(id)).isEqualTo("APPROVED");
    }

    @Test
    void returnFailsOnDraftOrRejectedOrCompletedApplication() throws Exception {
        // 草稿直接调用退回：被状态机拦截为 409，因为尚未进入审批。
        long draft = createApplication("population");
        mvc.perform(post("/api/applications/" + draft + "/return")
                .header("Authorization", bearer(token("household")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"草稿不允许退回\",\"version\":0}"))
            .andExpect(status().isConflict());

        // 已被驳回的申请不能退回：审批结论已生效，不能再走「执行人退回」路径。
        long rejected = rejectedApplication("population");
        mvc.perform(post("/api/applications/" + rejected + "/return")
                .header("Authorization", bearer(token("household")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"驳回后不允许再退回\",\"version\":1}"))
            .andExpect(status().isConflict());
    }

    @Test
    void applicantResubmitsReturnedApplicationAndApprovalResumes() throws Exception {
        long id = approvedApplication("population");
        // 退回
        mvc.perform(post("/api/applications/" + id + "/return")
                .header("Authorization", bearer(token("household")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"需补充居住证明\",\"version\":2}"))
            .andExpect(status().isOk());
        assertThat(statusOf(id)).isEqualTo("RETURNED");

        // 申请人在 RETURNED 状态下重新提交：status 回到 UNDER_REVIEW，需要走二次审批
        mvc.perform(post("/api/applications/" + id + "/submit")
                .header("Authorization", bearer(token("population"))))
            .andExpect(status().isOk());
        assertThat(statusOf(id)).isEqualTo("UNDER_REVIEW");
        assertThat(jdbc.queryForObject(
                "SELECT operation_type FROM operation_log WHERE operation_type IN ('APPLICATION_SUBMIT','APPLICATION_RESUBMIT') ORDER BY log_id DESC LIMIT 1",
                String.class)).isEqualTo("APPLICATION_RESUBMIT");
    }

    @Test
    void applicantWithdrawsReturnedApplication() throws Exception {
        long id = approvedApplication("population");
        mvc.perform(post("/api/applications/" + id + "/return")
                .header("Authorization", bearer(token("household")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"不再受理\",\"version\":2}"))
            .andExpect(status().isOk());
        assertThat(statusOf(id)).isEqualTo("RETURNED");

        // 申请人在 RETURNED 状态下放弃：直接撤回即可，无需先撤回审批单。
        mvc.perform(post("/api/applications/" + id + "/withdraw")
                .header("Authorization", bearer(token("population"))))
            .andExpect(status().isOk());
        assertThat(statusOf(id)).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject(
                "SELECT operation_type FROM operation_log WHERE operation_type LIKE 'APPLICATION_WITHDRAW%' ORDER BY log_id DESC LIMIT 1",
                String.class)).isEqualTo("APPLICATION_WITHDRAW_AFTER_RETURN");
    }

    @Test
    void returnOptimisticLockingPreventsDuplicateReturn() throws Exception {
        long id = approvedApplication("population");
        // 版本号不一致应返回 409，避免并发退回。
        mvc.perform(post("/api/applications/" + id + "/return")
                .header("Authorization", bearer(token("admin")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"版本不一致测试\",\"version\":99}"))
            .andExpect(status().isConflict());
        assertThat(statusOf(id)).isEqualTo("APPROVED");
    }

    // ---------- helpers ----------

    /** 创建草稿 → 提交流程 → 通过审批 → 拿到 APPROVED 状态的申请，version=2。 */
    private long approvedApplication(String applicant) throws Exception {
        long id = createApplication(applicant);
        uploadRequired(id, applicant);
        submit(id, applicant);
        long approval = approvalId(id);
        long material = jdbc.queryForObject(
                "SELECT material_id FROM application_material WHERE application_id = ?", Long.class, id);
        verify(material, "VERIFIED", "approver");
        approve(approval, 0, "approver", 200);
        assertThat(statusOf(id)).isEqualTo("APPROVED");
        Integer v = jdbc.queryForObject(
                "SELECT version FROM business_application WHERE application_id = ?", Integer.class, id);
        assertThat(v).isEqualTo(2);
        return id;
    }

    private long rejectedApplication(String applicant) throws Exception {
        long id = createApplication(applicant);
        uploadRequired(id, applicant);
        submit(id, applicant);
        long approval = approvalId(id);
        mvc.perform(post("/api/approvals/" + approval + "/reject")
                .header("Authorization", bearer(token("approver")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"材料不达标\",\"version\":0}"))
            .andExpect(status().isOk());
        assertThat(statusOf(id)).isEqualTo("REJECTED");
        return id;
    }

    private long createApplication(String user) throws Exception {
        String body = mvc.perform(post("/api/applications")
                .header("Authorization", bearer(token(user)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("applicationId").asLong();
    }

    private void uploadRequired(long id, String user) throws Exception {
        mvc.perform(multipart("/api/applications/" + id + "/materials")
                .file(new MockMultipartFile("file", "proof.pdf", "application/pdf",
                        "%PDF-test".getBytes(StandardCharsets.UTF_8)))
                .param("materialType", "PROOF")
                .param("materialName", "证明材料")
                .param("requiredFlag", "true")
                .header("Authorization", bearer(token(user))))
            .andExpect(status().isCreated());
    }

    private void submit(long id, String user) throws Exception {
        mvc.perform(post("/api/applications/" + id + "/submit")
                .header("Authorization", bearer(token(user))))
            .andExpect(status().isOk());
    }

    private void approve(long id, int version, String user, int expected) throws Exception {
        mvc.perform(post("/api/approvals/" + id + "/approve")
                .header("Authorization", bearer(token(user)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"同意\",\"version\":" + version + "}"))
            .andExpect(status().is(expected));
    }

    private void verify(long material, String result, String user) throws Exception {
        mvc.perform(post("/api/materials/" + material + "/verify")
                .header("Authorization", bearer(token(user)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"" + result + "\",\"comment\":\"有效\"}"))
            .andExpect(status().isOk());
    }

    private long approvalId(long app) {
        return jdbc.queryForObject(
                "SELECT approval_id FROM sys_approval_request WHERE application_id = ?", Long.class, app);
    }

    private String statusOf(long id) {
        return jdbc.queryForObject(
                "SELECT status FROM business_application WHERE application_id = ?", String.class, id);
    }

    private String token(String user) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"123456\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("token").asText();
    }

    private String bearer(String t) { return "Bearer " + t; }

    private String createJson() {
        return "{\"businessType\":\"GENERAL_SERVICE\",\"title\":\"通用业务申请\",\"reason\":\"课程申请\"}";
    }
}
