package com.example.population.service;

import com.example.population.dto.ApprovalDraftDTO;
import com.example.population.dto.HouseholdCreateDTO;
import com.example.population.dto.MigrationInDTO;
import com.example.population.dto.MigrationOutDTO;
import com.example.population.dto.PersonCreateDTO;
import com.example.population.dto.PersonUpdateDTO;
import com.example.population.entity.BusinessApplication;
import com.example.population.entity.Household;
import com.example.population.entity.MigrationIn;
import com.example.population.entity.MigrationOut;
import com.example.population.entity.Person;
import com.example.population.entity.SysApprovalLog;
import com.example.population.entity.SysApprovalRequest;
import com.example.population.exception.BizException;
import com.example.population.exception.ForbiddenException;
import com.example.population.exception.NotFoundException;
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

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ApprovalGateServiceImpl#approve 单元测试。
 * <p>
 * 重点覆盖 dispatch 分支（PERSON_CREATE / PERSON_UPDATE / HOUSEHOLD_ESTABLISH /
 * MIGRATION_IN / MIGRATION_OUT / 未知类型）以及前后的状态机写库（request + ba + log）。
 * <p>
 * 全部用 Mockito 隔离 DB；材料闸门也是 mock（默认放行）。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalGateServiceApproveTest {

    @Mock private SysApprovalRequestMapper requestMapper;
    @Mock private SysApprovalLogMapper logMapper;
    @Mock private BusinessApplicationMapper businessApplicationMapper;
    @Mock private ApplicationMaterialService applicationMaterialService;
    @Mock private ApplicationContext ctx;
    @Mock private PersonService personService;
    @Mock private HouseholdService householdService;
    @Mock private MigrationInService migrationInService;
    @Mock private MigrationOutService migrationOutService;

    private ApprovalGateServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        service = new ApprovalGateServiceImpl(
                requestMapper, logMapper, businessApplicationMapper,
                objectMapper, applicationMaterialService);
        // 通过反射塞 ctx（构造器里没有 ApplicationContext）
        org.springframework.test.util.ReflectionTestUtils.setField(service, "ctx", ctx);

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

        // 默认让材料闸门放行
        org.mockito.Mockito.lenient()
                .doNothing().when(applicationMaterialService)
                .assertRequiredVerified(any(), anyString());
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    // ---------- 前置校验 ----------

    @Test
    @DisplayName("approve: 未登录 → ForbiddenException")
    void approve_unauthorized() {
        SecurityContext.clear();
        assertThatThrownBy(() -> service.approve(1L, "ok"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("未登录");

        verify(requestMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("approve: approvalId 为 null → BizException(400)")
    void approve_nullId() {
        assertThatThrownBy(() -> service.approve(null, "ok"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("approvalId");
    }

    @Test
    @DisplayName("approve: 审批单不存在 → NotFoundException")
    void approve_notFound() {
        when(requestMapper.selectById(7L)).thenReturn(null);
        assertThatThrownBy(() -> service.approve(7L, "ok"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("7");
    }

    @Test
    @DisplayName("approve: 状态非 PENDING → BizException(409)")
    void approve_nonPending() {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(7L);
        req.setApplicationId(1L);
        req.setStatus("APPROVED");
        when(requestMapper.selectById(7L)).thenReturn(req);

        assertThatThrownBy(() -> service.approve(7L, "ok"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("PENDING");

        verify(personService, never()).createPerson(any());
    }

    @Test
    @DisplayName("approve: 草稿 applicationId 缺材料 → 透传异常（业务落表前挡住）")
    void approve_materialGateRejects() {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(7L);
        req.setApplicationId(1L);
        req.setStatus("PENDING");
        req.setApplyReason("[BT=PERSON_CREATE][APPID=1][REASON=] {}");
        when(requestMapper.selectById(7L)).thenReturn(req);

        doThrow(new BizException(400, "缺少身份证明"))
                .when(applicationMaterialService)
                .assertRequiredVerified(eq(1L), eq("PERSON_CREATE"));

        assertThatThrownBy(() -> service.approve(7L, "ok"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少身份证明");

        verify(personService, never()).createPerson(any());
        // 闸门失败时不能改 request 状态
        verify(requestMapper, never()).updateById(any());
    }

    // ---------- dispatch: PERSON_CREATE ----------

    @Test
    @DisplayName("dispatch: PERSON_CREATE → PersonService.createPerson")
    void approve_personCreate() throws Exception {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(7L);
        req.setApplicationId(1L);
        req.setStatus("PENDING");
        PersonCreateDTO payload = new PersonCreateDTO();
        payload.setApplicationId(1L);
        payload.setName("张三");
        payload.setGenderCode("MALE");
        payload.setIdentityTypeCode("ID_CARD");
        payload.setIdentityNo("110101199001011237");
        payload.setBirthDate(LocalDate.of(1990, 1, 1));
        payload.setEthnicityCode("HAN");
        req.setApplyReason(buildReason("PERSON_CREATE", null, 1L, null, payload));

        when(requestMapper.selectById(7L)).thenReturn(req);

        Person saved = new Person();
        saved.setPersonId(555L);
        when(ctx.getBean(PersonService.class)).thenReturn(personService);
        when(personService.createPerson(any(PersonCreateDTO.class))).thenReturn(saved);

        BusinessApplication ba = new BusinessApplication();
        ba.setApplicationId(1L);
        ba.setStatus("PENDING_APPROVAL");
        when(businessApplicationMapper.selectById(1L)).thenReturn(ba);

        Long landedId = service.approve(7L, "OK");

        assertThat(landedId).isEqualTo(555L);

        // 落地后：request 状态 → APPROVED
        ArgumentCaptor<SysApprovalRequest> reqCap = ArgumentCaptor.forClass(SysApprovalRequest.class);
        verify(requestMapper).updateById(reqCap.capture());
        assertThat(reqCap.getValue().getStatus()).isEqualTo("APPROVED");
        assertThat(reqCap.getValue().getCurrentApproverId()).isEqualTo(99L);
        assertThat(reqCap.getValue().getFinishedAt()).isNotNull();

        // business_application 状态回填
        verify(businessApplicationMapper).updateById(any(BusinessApplication.class));

        // 日志写入
        ArgumentCaptor<SysApprovalLog> logCap = ArgumentCaptor.forClass(SysApprovalLog.class);
        verify(logMapper).insert(logCap.capture());
        SysApprovalLog logRow = logCap.getValue();
        assertThat(logRow.getApprovalId()).isEqualTo(7L);
        assertThat(logRow.getApproverUserId()).isEqualTo(99L);
        assertThat(logRow.getActionCode()).isEqualTo("APPROVE");
        assertThat(logRow.getComment()).isEqualTo("OK");
    }

    // ---------- dispatch: PERSON_UPDATE ----------

    @Test
    @DisplayName("dispatch: PERSON_UPDATE → PersonService.updatePerson（businessId 回传）")
    void approve_personUpdate() throws Exception {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(8L);
        req.setApplicationId(2L);
        req.setStatus("PENDING");
        PersonUpdateDTO payload = new PersonUpdateDTO();
        payload.setName("李四");
        payload.setPhone("13900000000");
        req.setApplyReason(buildReason("PERSON_UPDATE", 555L, 2L, null, payload));

        when(requestMapper.selectById(8L)).thenReturn(req);

        when(ctx.getBean(PersonService.class)).thenReturn(personService);
        when(personService.updatePerson(eq(555L), any(PersonUpdateDTO.class))).thenReturn(true);

        BusinessApplication ba = new BusinessApplication();
        ba.setApplicationId(2L);
        when(businessApplicationMapper.selectById(2L)).thenReturn(ba);

        Long landedId = service.approve(8L, null);  // null comment → 默认"审批通过"

        assertThat(landedId).isEqualTo(555L);

        verify(personService).updatePerson(eq(555L), any(PersonUpdateDTO.class));

        ArgumentCaptor<SysApprovalLog> logCap = ArgumentCaptor.forClass(SysApprovalLog.class);
        verify(logMapper).insert(logCap.capture());
        assertThat(logCap.getValue().getComment()).isEqualTo("审批通过");
    }

    @Test
    @DisplayName("dispatch: PERSON_UPDATE 失败 → 整个事务抛 BizException(500)")
    void approve_personUpdate_fails() throws Exception {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(8L);
        req.setApplicationId(2L);
        req.setStatus("PENDING");
        PersonUpdateDTO payload = new PersonUpdateDTO();
        payload.setName("李四");
        req.setApplyReason(buildReason("PERSON_UPDATE", 555L, 2L, null, payload));

        when(requestMapper.selectById(8L)).thenReturn(req);

        when(ctx.getBean(PersonService.class)).thenReturn(personService);
        when(personService.updatePerson(eq(555L), any(PersonUpdateDTO.class)))
                .thenThrow(new RuntimeException("DB constraint"));

        assertThatThrownBy(() -> service.approve(8L, "ok"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("落地失败");

        // request 状态不应被改写（@Transactional 回滚）
        verify(requestMapper, never()).updateById(any());
        verify(logMapper, never()).insert(any());
    }

    // ---------- dispatch: HOUSEHOLD_ESTABLISH ----------

    @Test
    @DisplayName("dispatch: HOUSEHOLD_ESTABLISH → HouseholdService.establishHousehold")
    void approve_householdEstablish() throws Exception {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(9L);
        req.setApplicationId(3L);
        req.setStatus("PENDING");
        HouseholdCreateDTO payload = new HouseholdCreateDTO();
        payload.setApplicationId(3L);
        payload.setHouseholdNo("H110101001");
        payload.setHouseholdTypeCode("FAMILY");
        payload.setRegisteredAddress("北京市朝阳区某街道");
        payload.setRegionCode("110105");
        payload.setDepartmentId(1L);
        payload.setEstablishDate(LocalDate.of(2026, 7, 1));
        req.setApplyReason(buildReason("HOUSEHOLD_ESTABLISH", null, 3L, null, payload));

        when(requestMapper.selectById(9L)).thenReturn(req);

        when(ctx.getBean(HouseholdService.class)).thenReturn(householdService);
        Household h = new Household();
        h.setHouseholdId(777L);
        when(householdService.establishHousehold(any(HouseholdCreateDTO.class))).thenReturn(h);

        BusinessApplication ba = new BusinessApplication();
        ba.setApplicationId(3L);
        when(businessApplicationMapper.selectById(3L)).thenReturn(ba);

        Long landedId = service.approve(9L, "OK");

        assertThat(landedId).isEqualTo(777L);
        verify(householdService).establishHousehold(any(HouseholdCreateDTO.class));
    }

    @Test
    @DisplayName("dispatch: HOUSEHOLD_ESTABLISH 返回 null → landedId=null（仍走日志）")
    void approve_householdEstablish_nullResult() throws Exception {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(9L);
        req.setApplicationId(3L);
        req.setStatus("PENDING");
        HouseholdCreateDTO payload = new HouseholdCreateDTO();
        payload.setHouseholdNo("H110101001");
        payload.setHouseholdTypeCode("FAMILY");
        req.setApplyReason(buildReason("HOUSEHOLD_ESTABLISH", null, 3L, null, payload));

        when(requestMapper.selectById(9L)).thenReturn(req);

        when(ctx.getBean(HouseholdService.class)).thenReturn(householdService);
        when(householdService.establishHousehold(any(HouseholdCreateDTO.class))).thenReturn(null);

        BusinessApplication ba = new BusinessApplication();
        ba.setApplicationId(3L);
        when(businessApplicationMapper.selectById(3L)).thenReturn(ba);

        Long landedId = service.approve(9L, "OK");
        assertThat(landedId).isNull();

        // 即使 null，仍然写完 request + ba + log
        verify(requestMapper).updateById(any());
        verify(logMapper).insert(any());
    }

    // ---------- dispatch: MIGRATION_IN ----------

    @Test
    @DisplayName("dispatch: MIGRATION_IN → MigrationInService.createMigrationIn")
    void approve_migrationIn() throws Exception {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(10L);
        req.setApplicationId(4L);
        req.setStatus("PENDING");
        MigrationInDTO payload = new MigrationInDTO();
        payload.setApplicationId(4L);
        payload.setPersonId(100L);
        payload.setInTypeCode("CROSS_DISTRICT");
        payload.setToHouseholdId(200L);
        payload.setToRegionCode("110105");
        payload.setInDate(LocalDate.of(2026, 7, 1));
        req.setApplyReason(buildReason("MIGRATION_IN", null, 4L, null, payload));

        when(requestMapper.selectById(10L)).thenReturn(req);

        when(ctx.getBean(MigrationInService.class)).thenReturn(migrationInService);
        MigrationIn in = new MigrationIn();
        in.setInId(888L);
        when(migrationInService.createMigrationIn(any(MigrationInDTO.class))).thenReturn(in);

        BusinessApplication ba = new BusinessApplication();
        ba.setApplicationId(4L);
        when(businessApplicationMapper.selectById(4L)).thenReturn(ba);

        Long landedId = service.approve(10L, "OK");

        assertThat(landedId).isEqualTo(888L);
        verify(migrationInService).createMigrationIn(any(MigrationInDTO.class));
    }

    // ---------- dispatch: MIGRATION_OUT ----------

    @Test
    @DisplayName("dispatch: MIGRATION_OUT → MigrationOutService.createMigrationOut")
    void approve_migrationOut() throws Exception {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(11L);
        req.setApplicationId(5L);
        req.setStatus("PENDING");
        MigrationOutDTO payload = new MigrationOutDTO();
        payload.setApplicationId(5L);
        payload.setPersonId(100L);
        payload.setOutTypeCode("CROSS_DISTRICT");
        payload.setFromHouseholdId(200L);
        payload.setFromRegionCode("110101");
        payload.setToAddress("上海市浦东新区");
        payload.setOutDate(LocalDate.of(2026, 7, 1));
        req.setApplyReason(buildReason("MIGRATION_OUT", null, 5L, null, payload));

        when(requestMapper.selectById(11L)).thenReturn(req);

        when(ctx.getBean(MigrationOutService.class)).thenReturn(migrationOutService);
        MigrationOut out = new MigrationOut();
        out.setOutId(999L);
        when(migrationOutService.createMigrationOut(any(MigrationOutDTO.class))).thenReturn(out);

        BusinessApplication ba = new BusinessApplication();
        ba.setApplicationId(5L);
        when(businessApplicationMapper.selectById(5L)).thenReturn(ba);

        Long landedId = service.approve(11L, "OK");

        assertThat(landedId).isEqualTo(999L);
        verify(migrationOutService).createMigrationOut(any(MigrationOutDTO.class));
    }

    // ---------- dispatch: unknown ----------

    @Test
    @DisplayName("dispatch: 未知业务类型 → BizException(400)")
    void approve_unknownBusinessType() throws Exception {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(12L);
        req.setApplicationId(6L);
        req.setStatus("PENDING");
        // 直接拼一个未知 BT
        req.setApplyReason("[BT=BOGUS_TYPE][PID=][APPID=6][REASON=] {}");

        when(requestMapper.selectById(12L)).thenReturn(req);

        assertThatThrownBy(() -> service.approve(12L, "OK"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未知业务类型");

        verify(requestMapper, never()).updateById(any());
    }

    // ---------- business_application 缺失的容错 ----------

    @Test
    @DisplayName("approve: business_application 缺失（外键孤儿）→ 不报错，但日志照写")
    void approve_missingBusinessApplication() throws Exception {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(7L);
        req.setApplicationId(1L);
        req.setStatus("PENDING");
        PersonCreateDTO payload = new PersonCreateDTO();
        payload.setName("张三");
        payload.setIdentityTypeCode("ID_CARD");
        payload.setIdentityNo("110101199001011237");
        req.setApplyReason(buildReason("PERSON_CREATE", null, 1L, null, payload));

        when(requestMapper.selectById(7L)).thenReturn(req);
        when(ctx.getBean(PersonService.class)).thenReturn(personService);
        Person saved = new Person();
        saved.setPersonId(555L);
        when(personService.createPerson(any(PersonCreateDTO.class))).thenReturn(saved);

        // ba 找不到
        when(businessApplicationMapper.selectById(1L)).thenReturn(null);

        Long landedId = service.approve(7L, "OK");

        assertThat(landedId).isEqualTo(555L);
        // request 仍被改
        verify(requestMapper).updateById(any());
        // ba.updateById 不被调
        verify(businessApplicationMapper, never()).updateById(any());
        // 日志仍写
        verify(logMapper).insert(any());
    }

    // ---------- reject ----------

    @Test
    @DisplayName("reject: 合法 PENDING 单 → 写 status=REJECTED + ba 状态 + log")
    void reject_ok() {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(20L);
        req.setApplicationId(1L);
        req.setStatus("PENDING");
        when(requestMapper.selectById(20L)).thenReturn(req);

        BusinessApplication ba = new BusinessApplication();
        ba.setApplicationId(1L);
        when(businessApplicationMapper.selectById(1L)).thenReturn(ba);

        service.reject(20L, "材料不齐");

        ArgumentCaptor<SysApprovalRequest> reqCap = ArgumentCaptor.forClass(SysApprovalRequest.class);
        verify(requestMapper).updateById(reqCap.capture());
        assertThat(reqCap.getValue().getStatus()).isEqualTo("REJECTED");

        verify(businessApplicationMapper).updateById(any());

        ArgumentCaptor<SysApprovalLog> logCap = ArgumentCaptor.forClass(SysApprovalLog.class);
        verify(logMapper).insert(logCap.capture());
        assertThat(logCap.getValue().getActionCode()).isEqualTo("REJECT");
        assertThat(logCap.getValue().getComment()).isEqualTo("材料不齐");
    }

    @Test
    @DisplayName("reject: 未登录 → ForbiddenException")
    void reject_unauthorized() {
        SecurityContext.clear();
        assertThatThrownBy(() -> service.reject(1L, "x"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("reject: 状态非 PENDING → BizException(409)")
    void reject_nonPending() {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(20L);
        req.setStatus("APPROVED");
        when(requestMapper.selectById(20L)).thenReturn(req);

        assertThatThrownBy(() -> service.reject(20L, "x"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("PENDING");
    }

    // ==================== P0 增量测试 ====================

    @Test
    @DisplayName("P0-3 approve: 申请人 = 审批人 → ForbiddenException（禁止自审）")
    void approve_selfReviewForbidden() throws Exception {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(30L);
        req.setApplicationId(8L);
        req.setStatus("PENDING");
        req.setBusinessType("PERSON_CREATE");
        PersonCreateDTO payload = new PersonCreateDTO();
        payload.setName("张三");
        payload.setIdentityTypeCode("ID_CARD");
        payload.setIdentityNo("110101199001011237");
        req.setApplyReason(buildReason("PERSON_CREATE", null, 8L, null, payload));
        when(requestMapper.selectById(30L)).thenReturn(req);

        BusinessApplication ba = new BusinessApplication();
        ba.setApplicationId(8L);
        ba.setSubmitUserId(99L); // 与当前审批人相同
        when(businessApplicationMapper.selectById(8L)).thenReturn(ba);

        assertThatThrownBy(() -> service.approve(30L, "OK"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("申请人不能审批自己的申请");

        // 不应该触发业务落地
        verify(ctx, never()).getBean(PersonService.class);
    }

    @Test
    @DisplayName("P0-3 reject: 申请人 = 审批人 → ForbiddenException（禁止自驳）")
    void reject_selfReviewForbidden() {
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalId(31L);
        req.setApplicationId(9L);
        req.setStatus("PENDING");
        when(requestMapper.selectById(31L)).thenReturn(req);

        BusinessApplication ba = new BusinessApplication();
        ba.setApplicationId(9L);
        ba.setSubmitUserId(99L);
        when(businessApplicationMapper.selectById(9L)).thenReturn(ba);

        assertThatThrownBy(() -> service.reject(31L, "材料不全"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("申请人不能驳回自己的申请");

        verify(requestMapper, never()).updateById(any());
    }

    // ---------- helpers ----------

    /**
     * 构造 ApprovalGateServiceImpl#buildApplyReason 同格式的字符串：
     *   {@code [BT=...][PID=...][APPID=...][REASON=...]<payloadJson>}
     */
    private String buildReason(String bt, Long businessId, Long appId, String reason, Object payload)
            throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("[BT=").append(bt).append(']');
        if (businessId != null) sb.append("[PID=").append(businessId).append(']');
        if (appId != null) sb.append("[APPID=").append(appId).append(']');
        sb.append("[REASON=").append(reason == null ? "" : reason).append(']');
        sb.append(objectMapper.writeValueAsString(payload));
        return sb.toString();
    }
}