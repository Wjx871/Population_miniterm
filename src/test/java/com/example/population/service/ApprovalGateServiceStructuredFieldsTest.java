package com.example.population.service;

import com.example.population.dto.PersonCreateDTO;
import com.example.population.entity.BusinessApplication;
import com.example.population.entity.Person;
import com.example.population.entity.SysApprovalRequest;
import com.example.population.exception.BizException;
import com.example.population.mapper.BusinessApplicationMapper;
import com.example.population.mapper.SysApprovalLogMapper;
import com.example.population.mapper.SysApprovalRequestMapper;
import com.example.population.service.impl.ApprovalGateServiceImpl;
import com.example.population.util.SecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ApprovalGateServiceImpl Sprint 3 测试：验证 P0-6 修复（payload 走独立列、不再字符串拼接）。
 * <p>
 * 关键覆盖：
 * <ul>
 *   <li>submit 写入独立列 businessType / businessId / payloadJson</li>
 *   <li>apply_reason 不再含 {@code [BT=...]} 前缀</li>
 *   <li>approve 直接读独立列，payloadJson 攻击注入无法劫持业务类型</li>
 *   <li>迁移期：旧格式 applyReason 仍可兜底解析</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ApprovalGateServiceStructuredFieldsTest {

    @Mock private SysApprovalRequestMapper requestMapper;
    @Mock private SysApprovalLogMapper logMapper;
    @Mock private BusinessApplicationMapper businessApplicationMapper;
    @Mock private ApplicationMaterialService applicationMaterialService;
    @Mock private ApplicationContext ctx;
    @Mock private PersonService personService;

    private ApprovalGateServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        service = new ApprovalGateServiceImpl(
                requestMapper, logMapper, businessApplicationMapper,
                objectMapper, applicationMaterialService);
        ReflectionTestUtils.setField(service, "ctx", ctx);

        SecurityContext.set(SecurityContext.builder()
                .userId(99L)
                .username("approver")
                .realName("审批员")
                .permissionLevel(3)
                .roleCode("L3_APPROVE")
                .dataScopeCode("ALL")
                .permissionCodes(new HashSet<>(Set.of("approval:approve")))
                .departmentId(1L)
                .build());

        org.mockito.Mockito.lenient()
                .doNothing().when(applicationMaterialService)
                .assertRequiredVerified(any(), anyString());
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    @Test
    @DisplayName("submit: 写入独立列 businessType/businessId/payloadJson；apply_reason 不含 [BT= 前缀")
    void submit_writesStructuredFields() throws Exception {
        PersonCreateDTO dto = new PersonCreateDTO();
        dto.setApplicationId(1L);
        dto.setName("张三");
        dto.setGenderCode("MALE");
        dto.setIdentityTypeCode("ID_CARD");
        dto.setIdentityNo("110101199001011237");
        dto.setBirthDate(LocalDate.of(1990, 1, 1));
        dto.setEthnicityCode("HAN");
        dto.setPhone("13800138000");

        com.example.population.dto.ApprovalDraftDTO draft = new com.example.population.dto.ApprovalDraftDTO();
        draft.setBusinessType("PERSON_CREATE");
        draft.setBusinessId(555L);
        draft.setApplicationId(1L);
        draft.setApplyReason("用户自由文本");
        draft.setPayloadJson(objectMapper.writeValueAsString(dto));

        when(businessApplicationMapper.insert(any(BusinessApplication.class))).thenAnswer(inv -> {
            BusinessApplication arg = inv.getArgument(0);
            arg.setApplicationId(1L);
            return 1;
        });
        when(requestMapper.insert(any(SysApprovalRequest.class))).thenAnswer(inv -> {
            SysApprovalRequest arg = inv.getArgument(0);
            arg.setApprovalId(7L);
            return 1;
        });

        Long approvalId = service.submit(draft);

        assertThat(approvalId).isEqualTo(7L);

        ArgumentCaptor<SysApprovalRequest> reqCap = ArgumentCaptor.forClass(SysApprovalRequest.class);
        verify(requestMapper).insert(reqCap.capture());
        SysApprovalRequest saved = reqCap.getValue();

        // 1. 独立列正确写入
        assertThat(saved.getBusinessType()).isEqualTo("PERSON_CREATE");
        assertThat(saved.getBusinessId()).isEqualTo(555L);
        assertThat(saved.getApplicationId()).isEqualTo(1L);
        assertThat(saved.getPayloadJson()).contains("张三").contains("110101199001011237");

        // 2. apply_reason 不再含字符串拼接标记
        assertThat(saved.getApplyReason()).isEqualTo("用户自由文本");
        assertThat(saved.getApplyReason()).doesNotContain("[BT=");
        assertThat(saved.getApplyReason()).doesNotContain("[REASON=");
    }

    @Test
    @DisplayName("approve: payloadJson 中包含 [BT=PERSON_UPDATE] 不影响 dispatch（payloadJson 是 JSON）")
    void approve_payloadJsonInjectionNoLongerHijacks() throws Exception {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(20L);
        req.setApplicationId(1L);
        req.setStatus("PENDING");
        // 独立列写 PERSON_CREATE（正确），apply_reason 含攻击者注入字符串
        req.setBusinessType("PERSON_CREATE");
        req.setBusinessId(null);
        req.setPayloadJson("{\"name\":\"evil\",\"identityTypeCode\":\"ID_CARD\",\"identityNo\":\"110101199001011237\"}");
        req.setApplyReason("包含 [BT=PERSON_UPDATE] 的恶意文本");

        when(requestMapper.selectById(20L)).thenReturn(req);
        when(ctx.getBean(PersonService.class)).thenReturn(personService);

        Person saved = new Person();
        saved.setPersonId(888L);
        when(personService.createPerson(any(PersonCreateDTO.class))).thenReturn(saved);

        BusinessApplication ba = new BusinessApplication();
        ba.setApplicationId(1L);
        when(businessApplicationMapper.selectById(1L)).thenReturn(ba);

        Long landedId = service.approve(20L, "ok");

        // dispatch 走的是 business_type="PERSON_CREATE"，没有被攻击者字符串劫持成 PERSON_UPDATE
        assertThat(landedId).isEqualTo(888L);
        verify(personService).createPerson(any(PersonCreateDTO.class));
    }

    @Test
    @DisplayName("approve: 业务类型缺失 → BizException（不再从 apply_reason 中猜测）")
    void approve_missingBusinessType_throws() {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(21L);
        req.setApplicationId(1L);
        req.setStatus("PENDING");
        req.setBusinessType(null);
        req.setApplyReason("无任何标记");

        when(requestMapper.selectById(21L)).thenReturn(req);

        assertThatThrownBy(() -> service.approve(21L, "ok"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("业务类型缺失");

        verify(personService, never()).createPerson(any());
    }

    @Test
    @DisplayName("approve: 迁移期兼容 - 旧 apply_reason 含 [BT=...][PID=...] 仍能解析（兜底）")
    void approve_legacyReasonStillWorks() throws Exception {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(22L);
        req.setApplicationId(2L);
        req.setStatus("PENDING");
        // 独立列为空（模拟未迁移的存量数据），apply_reason 用旧格式
        req.setBusinessType(null);
        req.setBusinessId(null);
        req.setPayloadJson(null);
        req.setApplyReason("[BT=PERSON_CREATE][PID=][APPID=2][REASON=老数据]"
                + "{\"name\":\"张三\",\"identityTypeCode\":\"ID_CARD\",\"identityNo\":\"110101199001011237\"}");

        when(requestMapper.selectById(22L)).thenReturn(req);
        when(ctx.getBean(PersonService.class)).thenReturn(personService);

        Person saved = new Person();
        saved.setPersonId(999L);
        when(personService.createPerson(any(PersonCreateDTO.class))).thenReturn(saved);

        BusinessApplication ba = new BusinessApplication();
        ba.setApplicationId(2L);
        when(businessApplicationMapper.selectById(2L)).thenReturn(ba);

        Long landedId = service.approve(22L, "ok");

        assertThat(landedId).isEqualTo(999L);
        verify(personService).createPerson(any(PersonCreateDTO.class));
    }
}