package com.example.population.controller;

import com.example.population.aspect.LevelGateAspect;
import com.example.population.aspect.PermissionAspect;
import com.example.population.dto.*;
import com.example.population.entity.*;
import com.example.population.exception.BizException;
import com.example.population.exception.ForbiddenException;
import com.example.population.exception.GlobalExceptionHandler;
import com.example.population.exception.NotFoundException;
import com.example.population.service.*;
import com.example.population.util.IdCardValidator;
import com.example.population.util.SecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 全面的 API 测试套件，覆盖所有 Controller 的主要接口。
 * 使用 MockMvc standaloneSetup，脱离 Spring 容器。
 */
class ApiComprehensiveTest {

    // 模拟服务
    private PersonService personService;
    private HouseholdService householdService;
    private MigrationOutService migrationOutService;
    private MigrationInService migrationInService;
    private ApprovalGateService approvalGateService;
    private BusinessApplicationService applicationService;
    private ResidenceRegistrationService registrationService;
    private HouseholdMemberService memberService;
    private CertificateService certificateService;
    private FloatingPopulationService floatingService;
    private KeyPopulationService keyService;
    private CancellationRecordService cancellationService;
    private SysUserService userService;
    private LoginLogService loginLogService;
    private OperationLogService operationLogService;
    private UnifiedSearchService searchService;
    private StatsService statsService;
    private AdminRegionService regionService;
    private SysRoleService roleService;
    private DataDictionaryService dictionaryService;
    private ResidencePermitService permitService;
    private ApplicationMaterialService materialService;
    private SysRolePermissionService rolePermissionService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        // 初始化所有模拟服务
        personService = mock(PersonService.class);
        householdService = mock(HouseholdService.class);
        migrationOutService = mock(MigrationOutService.class);
        migrationInService = mock(MigrationInService.class);
        approvalGateService = mock(ApprovalGateService.class);
        applicationService = mock(BusinessApplicationService.class);
        registrationService = mock(ResidenceRegistrationService.class);
        memberService = mock(HouseholdMemberService.class);
        certificateService = mock(CertificateService.class);
        floatingService = mock(FloatingPopulationService.class);
        keyService = mock(KeyPopulationService.class);
        cancellationService = mock(CancellationRecordService.class);
        userService = mock(SysUserService.class);
        loginLogService = mock(LoginLogService.class);
        operationLogService = mock(OperationLogService.class);
        searchService = mock(UnifiedSearchService.class);
        statsService = mock(StatsService.class);
        regionService = mock(AdminRegionService.class);
        roleService = mock(SysRoleService.class);
        dictionaryService = mock(DataDictionaryService.class);
        permitService = mock(ResidencePermitService.class);
        materialService = mock(ApplicationMaterialService.class);
        rolePermissionService = mock(SysRolePermissionService.class);

        // 创建所有控制器
        PersonController personController = new PersonController(personService, approvalGateService, objectMapper);
        HouseholdController householdController = new HouseholdController(householdService, approvalGateService, objectMapper);
        MigrationOutController migrationOutController = new MigrationOutController(migrationOutService, approvalGateService, objectMapper);
        MigrationInController migrationInController = new MigrationInController(migrationInService, approvalGateService, objectMapper);
        ApprovalGateController approvalController = new ApprovalGateController(approvalGateService);
        BusinessApplicationController appController = new BusinessApplicationController(applicationService);
        ResidenceRegistrationController regController = new ResidenceRegistrationController(registrationService, personService, householdService);
        HouseholdMemberController memberController = new HouseholdMemberController(memberService);
        CertificateController certController = new CertificateController(certificateService);
        FloatingPopulationController floatingController = new FloatingPopulationController(floatingService);
        KeyPopulationController keyController = new KeyPopulationController(keyService);
        CancellationRecordController cancelController = new CancellationRecordController(cancellationService);
        SysUserController userController = new SysUserController(userService);
        LoginLogController loginLogController = new LoginLogController(loginLogService);
        OperationLogController opLogController = new OperationLogController(operationLogService);
        SearchController searchController = new SearchController(searchService);
        StatsController statsController = new StatsController(statsService);
        AdminRegionController regionController = new AdminRegionController(regionService);
        SysRoleController roleController = new SysRoleController(roleService);
        DataDictionaryController dictController = new DataDictionaryController(dictionaryService);
        ResidencePermitController permitController = new ResidencePermitController(permitService);
        ApplicationMaterialController materialController = new ApplicationMaterialController(materialService);
        SysRolePermissionController rolePermController = new SysRolePermissionController(rolePermissionService);

        // 配置 Jackson converter
        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter(objectMapper);

        // 配置 JSR-303 Validator
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        // 构建 MockMvc
        this.mockMvc = MockMvcBuilders
                .standaloneSetup(
                        personController,
                        householdController,
                        migrationOutController,
                        migrationInController,
                        approvalController,
                        appController,
                        regController,
                        memberController,
                        certController,
                        floatingController,
                        keyController,
                        cancelController,
                        userController,
                        loginLogController,
                        opLogController,
                        searchController,
                        statsController,
                        regionController,
                        roleController,
                        dictController,
                        permitController,
                        materialController,
                        rolePermController
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(jacksonConverter)
                .setValidator(validator)
                .build();

        // 清空 SecurityContext
        SecurityContext.clear();
    }

    // ====================================================================
    // PersonController 测试
    // ====================================================================
    @Nested
    @DisplayName("PersonController API 测试")
    class PersonControllerTests {

        @Test
        @DisplayName("GET /api/persons/{id} - 查询成功返回 200")
        void getPerson_success() throws Exception {
            Person p = new Person();
            p.setPersonId(1L);
            p.setName("张三");
            p.setIdentityNo("110101199001011237");
            when(personService.getById(1L)).thenReturn(p);

            mockMvc.perform(get("/api/persons/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.name").value("张三"));
        }

        @Test
        @DisplayName("GET /api/persons/{id} - 不存在返回 null")
        void getPerson_notFound() throws Exception {
            when(personService.getById(999L)).thenReturn(null);

            mockMvc.perform(get("/api/persons/{id}", 999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("POST /api/persons - L3 直接创建成功")
        void createPerson_l3_direct() throws Exception {
            loginAsL3With("person:create", "person:query");

            PersonCreateDTO dto = validPersonDto();
            Person saved = new Person();
            saved.setPersonId(100L);
            when(personService.createPerson(any())).thenReturn(saved);

            mockMvc.perform(post("/api/persons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.directLanding").value(true));
        }

        @Test
        @DisplayName("POST /api/persons - L1/L2 走审批流程")
        void createPerson_l1_approval() throws Exception {
            loginAsL2With("person:query");

            PersonCreateDTO dto = validPersonDto();
            when(approvalGateService.submit(any())).thenReturn(555L);

            mockMvc.perform(post("/api/persons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.directLanding").value(false))
                    .andExpect(jsonPath("$.data.approvalId").value(555));
        }

        @Test
        @DisplayName("POST /api/persons - 缺少必填字段返回 400")
        void createPerson_missingField() throws Exception {
            loginAsL3With("person:create");

            PersonCreateDTO dto = validPersonDto();
            dto.setApplicationId(null);

            mockMvc.perform(post("/api/persons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/persons - 身份证格式错误返回 400")
        void createPerson_invalidIdCard() throws Exception {
            loginAsL3With("person:create");

            PersonCreateDTO dto = validPersonDto();
            dto.setIdentityNo("invalid");
            when(personService.createPerson(any()))
                    .thenThrow(new BizException(400, "身份证号格式或校验位错误"));

            mockMvc.perform(post("/api/persons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("PUT /api/persons/{id} - L3 直接更新")
        void updatePerson_l3_direct() throws Exception {
            loginAsL3With("person:update");

            PersonUpdateDTO dto = new PersonUpdateDTO();
            dto.setName("李四");

            mockMvc.perform(put("/api/persons/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.directLanding").value(true));
        }

        @Test
        @DisplayName("DELETE /api/persons/{id} - 删除成功")
        void deletePerson_success() throws Exception {
            loginAsL3With("person:update");

            mockMvc.perform(delete("/api/persons/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(personService).removeById(1L);
        }

        @Test
        @DisplayName("GET /api/persons/validate/identity - 合法身份证")
        void validateIdentity_valid() throws Exception {
            String validId = "110101199001011237";

            mockMvc.perform(get("/api/persons/validate/identity").param("no", validId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.valid").value(true))
                    .andExpect(jsonPath("$.data.genderCode").value("MALE"));
        }

        @Test
        @DisplayName("GET /api/persons/validate/identity - 非法身份证")
        void validateIdentity_invalid() throws Exception {
            String invalidId = "123456789012345678";

            mockMvc.perform(get("/api/persons/validate/identity").param("no", invalidId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.valid").value(false));
        }
    }

    // ====================================================================
    // HouseholdController 测试
    // ====================================================================
    @Nested
    @DisplayName("HouseholdController API 测试")
    class HouseholdControllerTests {

        @Test
        @DisplayName("GET /api/households/{id} - 查询详情")
        void getHousehold_detail() throws Exception {
            Household h = new Household();
            h.setHouseholdId(1L);
            h.setHouseholdNo("H110101001");
            when(householdService.getDetail(1L)).thenReturn(h);

            mockMvc.perform(get("/api/households/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.householdId").value(1));
        }

        @Test
        @DisplayName("POST /api/households/establish - L3 直接立户")
        void establishHousehold_l3_direct() throws Exception {
            loginAsL3With("household:create", "household:establish");

            HouseholdCreateDTO dto = validHouseholdDto();
            Household h = new Household();
            h.setHouseholdId(100L);
            when(householdService.establishHousehold(any())).thenReturn(h);

            mockMvc.perform(post("/api/households/establish")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.directLanding").value(true));
        }

        @Test
        @DisplayName("POST /api/households/establish - 缺少 applicationId 返回 400")
        void establishHousehold_missingAppId() throws Exception {
            loginAsL3With("household:create", "household:establish");

            HouseholdCreateDTO dto = validHouseholdDto();
            dto.setApplicationId(null);

            mockMvc.perform(post("/api/households/establish")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("PUT /api/households/{id}/head - 更换户主成功")
        void changeHead_success() throws Exception {
            loginAsL3With("household:update");

            mockMvc.perform(put("/api/households/{id}/head", 1L)
                            .param("newHeadPersonId", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(householdService).changeHead(1L, 100L);
        }

        @Test
        @DisplayName("PUT /api/households/{id}/disable - 销户成功")
        void disableHousehold_success() throws Exception {
            loginAsL3With("cancellation:household");

            mockMvc.perform(put("/api/households/{id}/disable", 1L)
                            .param("operatorId", "99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(householdService).disableHousehold(1L, 99L);
        }

        @Test
        @DisplayName("DELETE /api/households/{id} - 禁用接口返回 405")
        void deleteHousehold_disabled() throws Exception {
            mockMvc.perform(delete("/api/households/{id}", 1L))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value(405));
        }
    }

    // ====================================================================
    // MigrationOutController 测试
    // ====================================================================
    @Nested
    @DisplayName("MigrationOutController API 测试")
    class MigrationOutControllerTests {

        @Test
        @DisplayName("POST /api/migration-out - L3 直接创建迁出")
        void createMigrationOut_l3_direct() throws Exception {
            loginAsL3With("migration:out:create");

            MigrationOutDTO dto = validMigrationOutDto();
            MigrationOut m = new MigrationOut();
            m.setOutId(100L);
            when(migrationOutService.createMigrationOut(any())).thenReturn(m);

            mockMvc.perform(post("/api/migration-out")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.directLanding").value(true));
        }

        @Test
        @DisplayName("PUT /api/migration-out/{id}/complete - 办结成功")
        void completeMigrationOut_success() throws Exception {
            loginAsL3With("migration:out:create");
            when(migrationOutService.complete(1L, 99L)).thenReturn(true);

            mockMvc.perform(put("/api/migration-out/{id}/complete", 1L)
                            .param("operatorId", "99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(migrationOutService).complete(1L, 99L);
        }

        @Test
        @DisplayName("PUT /api/migration-out/{id}/complete - 已办结返回 409")
        void completeMigrationOut_alreadyDone() throws Exception {
            loginAsL3With("migration:out:create");
            when(migrationOutService.complete(1L, 99L))
                    .thenThrow(new BizException(409, "该迁出记录已办结"));

            mockMvc.perform(put("/api/migration-out/{id}/complete", 1L)
                            .param("operatorId", "99"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409));
        }

        @Test
        @DisplayName("DELETE /api/migration-out/{id} - 禁用接口返回 405")
        void deleteMigrationOut_disabled() throws Exception {
            mockMvc.perform(delete("/api/migration-out/{id}", 1L))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value(405));
        }
    }

    // ====================================================================
    // MigrationInController 测试
    // ====================================================================
    @Nested
    @DisplayName("MigrationInController API 测试")
    class MigrationInControllerTests {

        @Test
        @DisplayName("POST /api/migration-in - L3 直接创建迁入")
        void createMigrationIn_l3_direct() throws Exception {
            loginAsL3With("migration:in:create");

            MigrationInDTO dto = validMigrationInDto();
            MigrationIn m = new MigrationIn();
            m.setInId(100L);
            when(migrationInService.createMigrationIn(any())).thenReturn(m);

            mockMvc.perform(post("/api/migration-in")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.directLanding").value(true));
        }

        @Test
        @DisplayName("PUT /api/migration-in/{id}/complete - 办结成功")
        void completeMigrationIn_success() throws Exception {
            loginAsL3With("migration:in:create");

            mockMvc.perform(put("/api/migration-in/{id}/complete", 1L)
                            .param("operatorId", "99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(migrationInService).complete(1L, 99L);
        }
    }

    // ====================================================================
    // ApprovalGateController 测试
    // ====================================================================
    @Nested
    @DisplayName("ApprovalGateController API 测试")
    class ApprovalGateControllerTests {

        @Test
        @DisplayName("POST /api/approval-gate/approve/{id} - 审批通过")
        void approve_success() throws Exception {
            loginAsL3();
            when(approvalGateService.approve(1L, "ok")).thenReturn(100L);

            mockMvc.perform(post("/api/approval-gate/approve/{id}", 1L)
                            .param("comment", "ok"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.landedId").value(100));
        }

        @Test
        @DisplayName("POST /api/approval-gate/approve/{id} - 审批单不存在返回 404")
        void approve_notFound() throws Exception {
            loginAsL3();
            when(approvalGateService.approve(999L, null))
                    .thenThrow(new NotFoundException("审批单[999]不存在"));

            mockMvc.perform(post("/api/approval-gate/approve/{id}", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }

        @Test
        @DisplayName("POST /api/approval-gate/reject/{id} - 审批驳回")
        void reject_success() throws Exception {
            loginAsL3();

            mockMvc.perform(post("/api/approval-gate/reject/{id}", 1L)
                            .param("comment", "材料不全"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(approvalGateService).reject(1L, "材料不全");
        }
    }

    // ====================================================================
    // BusinessApplicationController 测试
    // ====================================================================
    @Nested
    @DisplayName("BusinessApplicationController API 测试")
    class BusinessApplicationControllerTests {

        @Test
        @DisplayName("GET /api/business-applications/{id} - 查询详情")
        void getApplication_success() throws Exception {
            BusinessApplication app = new BusinessApplication();
            app.setApplicationId(1L);
            app.setBusinessTypeCode("PERSON_CREATE");
            when(applicationService.getDetail(1L)).thenReturn(app);

            mockMvc.perform(get("/api/business-applications/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.businessTypeCode").value("PERSON_CREATE"));
        }

        @Test
        @DisplayName("PUT /api/business-applications/{id}/submit - 提交申请")
        void submitApplication_success() throws Exception {
            mockMvc.perform(put("/api/business-applications/{id}/submit", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(applicationService).submit(1L);
        }
    }

    // ====================================================================
    // CertificateController 测试
    // ====================================================================
    @Nested
    @DisplayName("CertificateController API 测试")
    class CertificateControllerTests {

        @Test
        @DisplayName("GET /api/certificates/{id} - 查询证件")
        void getCertificate_success() throws Exception {
            Certificate cert = new Certificate();
            cert.setCertificateId(1L);
            cert.setCertificateTypeCode("ID_CARD");
            when(certificateService.getById(1L)).thenReturn(cert);

            mockMvc.perform(get("/api/certificates/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.certificateTypeCode").value("ID_CARD"));
        }

        @Test
        @DisplayName("POST /api/certificates - 创建证件")
        void createCertificate_success() throws Exception {
            CertificateCreateDTO dto = new CertificateCreateDTO();
            dto.setPersonId(1L);
            dto.setCertificateTypeCode("ID_CARD");
            dto.setCertificateNo("110101199001011237");
            dto.setValidUntil(LocalDate.of(2030, 12, 31));

            Certificate cert = new Certificate();
            cert.setCertificateId(100L);
            when(certificateService.createCertificate(any())).thenReturn(cert);

            mockMvc.perform(post("/api/certificates")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("PUT /api/certificates/{id}/cancel - 注销证件")
        void cancelCertificate_success() throws Exception {
            mockMvc.perform(put("/api/certificates/{id}/cancel", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(certificateService).cancelCertificate(1L);
        }

        @Test
        @DisplayName("GET /api/certificates/resolve-status - 自动判定状态")
        void resolveStatus_success() throws Exception {
            when(certificateService.resolveStatus(any(), eq(30))).thenReturn("VALID");

            mockMvc.perform(get("/api/certificates/resolve-status")
                            .param("validUntil", "2030-12-31")
                            .param("warnDays", "30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.status").value("VALID"));
        }
    }

    // ====================================================================
    // KeyPopulationController 测试
    // ====================================================================
    @Nested
    @DisplayName("KeyPopulationController API 测试")
    class KeyPopulationControllerTests {

        @Test
        @DisplayName("GET /api/key-population/{id} - 查询重点人口")
        void getKeyPopulation_success() throws Exception {
            KeyPopulation kp = new KeyPopulation();
            kp.setKeyId(1L);
            kp.setKeyTypeCode("DRUG_USER");
            when(keyService.getById(1L)).thenReturn(kp);

            mockMvc.perform(get("/api/key-population/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.keyTypeCode").value("DRUG_USER"));
        }

        @Test
        @DisplayName("POST /api/key-population - 新增重点登记")
        void createKeyPopulation_success() throws Exception {
            KeyPopulationCreateDTO dto = new KeyPopulationCreateDTO();
            dto.setPersonId(1L);
            dto.setKeyTypeCode("DRUG_USER");
            dto.setManagementLevelCode("LEVEL_A");
            dto.setRegisterApplicationId(1L);
            dto.setRegisterDate(LocalDate.now());

            KeyPopulation kp = new KeyPopulation();
            kp.setKeyId(100L);
            when(keyService.register(any())).thenReturn(kp);

            mockMvc.perform(post("/api/key-population")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("PUT /api/key-population/{id}/release - 解除重点管理")
        void releaseKeyPopulation_success() throws Exception {
            mockMvc.perform(put("/api/key-population/{id}/release", 1L)
                            .param("releaseApplicationId", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(keyService).release(1L, 50L);
        }

        @Test
        @DisplayName("GET /api/key-population/exists-active - 检查是否存在有效登记")
        void existsActive_success() throws Exception {
            when(keyService.existsActiveByPersonAndType(1L, "DRUG_USER")).thenReturn(true);

            mockMvc.perform(get("/api/key-population/exists-active")
                            .param("personId", "1")
                            .param("keyTypeCode", "DRUG_USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.exists").value(true));
        }
    }

    // ====================================================================
    // CancellationRecordController 测试
    // ====================================================================
    @Nested
    @DisplayName("CancellationRecordController API 测试")
    class CancellationRecordControllerTests {

        @Test
        @DisplayName("GET /api/cancellation-records/precheck-person - 人口注销前置校验")
        void precheckPerson_success() throws Exception {
            CancellationRecordService.PrecheckResult result =
                    new CancellationRecordService.PrecheckResult(true, "可以注销", 0L, null);
            when(cancellationService.precheckPerson(1L)).thenReturn(result);

            mockMvc.perform(get("/api/cancellation-records/precheck-person/{personId}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("GET /api/cancellation-records/precheck-household - 家庭户销户前置校验")
        void precheckHousehold_success() throws Exception {
            CancellationRecordService.PrecheckResult result =
                    new CancellationRecordService.PrecheckResult(true, "可以销户", 0L, 0L);
            when(cancellationService.precheckHousehold(1L)).thenReturn(result);

            mockMvc.perform(get("/api/cancellation-records/precheck-household/{householdId}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("PUT /api/cancellation-records/{id}/complete-person - 办结人口注销")
        void completePerson_success() throws Exception {
            loginAsL3With("cancellation:person");

            mockMvc.perform(put("/api/cancellation-records/{id}/complete-person", 1L)
                            .param("operatorId", "99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(cancellationService).completePersonCancellation(1L, 99L);
        }
    }

    // ====================================================================
    // SysUserController 测试
    // ====================================================================
    @Nested
    @DisplayName("SysUserController API 测试")
    class SysUserControllerTests {

        @Test
        @DisplayName("GET /api/sys-users/{id} - 查询用户")
        void getUser_success() throws Exception {
            loginAsL3With("user:query");

            SysUser u = new SysUser();
            u.setUserId(1L);
            u.setUsername("admin");
            u.setPasswordHash(null);
            when(userService.getById(1L)).thenReturn(u);

            mockMvc.perform(get("/api/sys-users/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.username").value("admin"));
        }

        @Test
        @DisplayName("POST /api/sys-users - 创建用户成功")
        void createUser_success() throws Exception {
            loginAsL3With("user:manage");

            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("new_user");
            dto.setPassword("Pass123!");
            dto.setRealName("新用户");
            dto.setRoleId(1L);
            dto.setDepartmentId(1L);
            when(userService.getByUsername(anyString())).thenReturn(null);

            mockMvc.perform(post("/api/sys-users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(userService).save(any(SysUser.class));
        }

        @Test
        @DisplayName("PUT /api/sys-users/{id}/disable - 停用用户")
        void disableUser_success() throws Exception {
            loginAsL3With("user:manage");

            mockMvc.perform(put("/api/sys-users/{id}/disable", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(userService).disableUser(1L);
        }

        @Test
        @DisplayName("PUT /api/sys-users/{id}/password - 重置密码")
        void resetPassword_success() throws Exception {
            loginAsL3With("user:manage");

            mockMvc.perform(put("/api/sys-users/{id}/password", 1L)
                            .param("newPassword", "NewPass123!"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(userService).resetPassword(1L, "NewPass123!");
        }

        @Test
        @DisplayName("DELETE /api/sys-users/{id} - 禁用删除接口返回 405")
        void deleteUser_disabled() throws Exception {
            mockMvc.perform(delete("/api/sys-users/{id}", 1L))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value(405));
        }
    }

    // ====================================================================
    // SearchController 测试
    // ====================================================================
    @Nested
    @DisplayName("SearchController API 测试")
    class SearchControllerTests {

        @Test
        @DisplayName("GET /api/search - 综合查询成功")
        void search_success() throws Exception {
            SearchResultDTO result = new SearchResultDTO();
            when(searchService.unifiedSearch(anyString(), anyInt())).thenReturn(result);

            mockMvc.perform(get("/api/search")
                            .param("keyword", "张三"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("GET /api/search - 关键字为空返回 400")
        void search_emptyKeyword() throws Exception {
            mockMvc.perform(get("/api/search")
                            .param("keyword", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("GET /api/search - 关键字过长返回 400")
        void search_keywordTooLong() throws Exception {
            String longKeyword = "a".repeat(65);

            mockMvc.perform(get("/api/search")
                            .param("keyword", longKeyword))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ====================================================================
    // StatsController 测试
    // ====================================================================
    @Nested
    @DisplayName("StatsController API 测试")
    class StatsControllerTests {

        @Test
        @DisplayName("GET /api/stats/dashboard - 综合数字卡")
        void dashboard_success() throws Exception {
            when(statsService.dashboardCounters()).thenReturn(Map.of("personCount", 1000));

            mockMvc.perform(get("/api/stats/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("GET /api/stats/person/status - 人员档案状态")
        void personStatus_success() throws Exception {
            when(statsService.countByPersonStatus(any(), any()))
                    .thenReturn(List.of(Map.of("status", "ACTIVE", "count", 100)));

            mockMvc.perform(get("/api/stats/person/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("GET /api/stats/person/gender - 人员性别分组")
        void personGender_success() throws Exception {
            when(statsService.countByPersonGender())
                    .thenReturn(List.of(Map.of("gender", "MALE", "count", 50)));

            mockMvc.perform(get("/api/stats/person/gender"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("GET /api/stats/household/type - 户口类型分组")
        void householdType_success() throws Exception {
            when(statsService.countByHouseholdType())
                    .thenReturn(List.of(Map.of("type", "FAMILY", "count", 200)));

            mockMvc.perform(get("/api/stats/household/type"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("GET /api/stats/population/by-region - 区划户籍人口数")
        void populationByRegion_success() throws Exception {
            when(statsService.populationByRegion())
                    .thenReturn(List.of(Map.of("regionCode", "110101", "population", 5000)));

            mockMvc.perform(get("/api/stats/population/by-region"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("GET /api/stats/migration/in - 迁入按月统计")
        void migrationIn_success() throws Exception {
            when(statsService.migrationInByMonth(12))
                    .thenReturn(List.of(Map.of("month", "2026-01", "count", 50)));

            mockMvc.perform(get("/api/stats/migration/in"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("GET /api/stats/key-population/type - 重点人口类型分组")
        void keyPopulationType_success() throws Exception {
            when(statsService.countByKeyPopulationType())
                    .thenReturn(List.of(Map.of("type", "DRUG_USER", "count", 10)));

            mockMvc.perform(get("/api/stats/key-population/type"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ====================================================================
    // AdminRegionController 测试
    // ====================================================================
    @Nested
    @DisplayName("AdminRegionController API 测试")
    class AdminRegionControllerTests {

        @Test
        @DisplayName("GET /api/admin-regions/{code} - 查询区划")
        void getRegion_success() throws Exception {
            AdminRegion r = new AdminRegion();
            r.setRegionCode("110101");
            r.setRegionName("北京市东城区");
            when(regionService.getById("110101")).thenReturn(r);

            mockMvc.perform(get("/api/admin-regions/{code}", "110101"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.regionCode").value("110101"));
        }

        @Test
        @DisplayName("GET /api/admin-regions/same-city - 判断同市")
        void sameCity_success() throws Exception {
            when(regionService.isSameCity("110101", "110102")).thenReturn(true);

            mockMvc.perform(get("/api/admin-regions/same-city")
                            .param("a", "110101")
                            .param("b", "110102"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        @DisplayName("PUT /api/admin-regions/{code}/enabled - 启用/停用区划")
        void updateEnabled_success() throws Exception {
            mockMvc.perform(put("/api/admin-regions/{code}/enabled", "110101")
                            .param("enabled", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(regionService).updateEnabled("110101", 0);
        }
    }

    // ====================================================================
    // SysRoleController 测试
    // ====================================================================
    @Nested
    @DisplayName("SysRoleController API 测试")
    class SysRoleControllerTests {

        @Test
        @DisplayName("GET /api/sys-roles - 查询所有角色")
        void listRoles_success() throws Exception {
            SysRole role = new SysRole();
            role.setRoleId(1L);
            role.setRoleCode("L3_ADMIN");
            when(roleService.list()).thenReturn(List.of(role));

            mockMvc.perform(get("/api/sys-roles"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("POST /api/sys-roles - 创建角色")
        void createRole_success() throws Exception {
            SysRoleCreateDTO dto = new SysRoleCreateDTO();
            dto.setRoleCode("CUSTOM_ROLE");
            dto.setRoleName("自定义角色");

            mockMvc.perform(post("/api/sys-roles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(roleService).save(any(SysRole.class));
        }
    }

    // ====================================================================
    // DataDictionaryController 测试
    // ====================================================================
    @Nested
    @DisplayName("DataDictionaryController API 测试")
    class DataDictionaryControllerTests {

        @Test
        @DisplayName("GET /api/data-dictionaries/label - 获取字典标签")
        void getLabel_success() throws Exception {
            when(dictionaryService.getLabel("GENDER", "MALE")).thenReturn("男");

            mockMvc.perform(get("/api/data-dictionaries/label")
                            .param("type", "GENDER")
                            .param("code", "MALE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.label").value("男"));
        }

        @Test
        @DisplayName("GET /api/data-dictionaries/by-type/{type} - 按类型查询字典")
        void listByType_success() throws Exception {
            DataDictionary dict = new DataDictionary();
            dict.setDictId(1L);
            dict.setDictType("GENDER");
            dict.setDictCode("MALE");
            when(dictionaryService.listByType("GENDER")).thenReturn(List.of(dict));

            mockMvc.perform(get("/api/data-dictionaries/by-type/{type}", "GENDER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ====================================================================
    // ResidenceRegistrationController 测试
    // ====================================================================
    @Nested
    @DisplayName("ResidenceRegistrationController API 测试")
    class ResidenceRegistrationControllerTests {

        @Test
        @DisplayName("GET /api/residence-registrations/by-person/{personId} - 查询个人户籍")
        void getByPerson_success() throws Exception {
            ResidenceRegistration reg = new ResidenceRegistration();
            reg.setRegistrationId(1L);
            reg.setHouseholdId(100L);
            when(registrationService.getByPerson(1L)).thenReturn(reg);

            Person p = new Person();
            p.setPersonId(1L);
            p.setName("张三");
            p.setIdentityNo("110101199001011237");
            when(personService.getById(1L)).thenReturn(p);

            Household h = new Household();
            h.setHouseholdId(100L);
            when(householdService.getById(100L)).thenReturn(h);

            mockMvc.perform(get("/api/residence-registrations/by-person/{personId}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("DELETE /api/residence-registrations/{id} - 禁用删除返回 405")
        void deleteRegistration_disabled() throws Exception {
            mockMvc.perform(delete("/api/residence-registrations/{id}", 1L))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value(405));
        }
    }

    // ====================================================================
    // HouseholdMemberController 测试
    // ====================================================================
    @Nested
    @DisplayName("HouseholdMemberController API 测试")
    class HouseholdMemberControllerTests {

        @Test
        @DisplayName("GET /api/household-members/current/{householdId} - 查询当前成员")
        void listCurrentMembers_success() throws Exception {
            HouseholdMember m = new HouseholdMember();
            m.setMemberId(1L);
            when(memberService.listCurrentMembers(100L)).thenReturn(List.of(m));

            mockMvc.perform(get("/api/household-members/current/{householdId}", 100L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("PUT /api/household-members/{memberId}/leave - 移除成员")
        void leaveMember_success() throws Exception {
            mockMvc.perform(put("/api/household-members/{memberId}/leave", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(memberService).removeMember(1L);
        }

        @Test
        @DisplayName("DELETE /api/household-members/{id} - 禁用删除返回 405")
        void deleteMember_disabled() throws Exception {
            mockMvc.perform(delete("/api/household-members/{id}", 1L))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value(405));
        }
    }

    // ====================================================================
    // FloatingPopulationController 测试
    // ====================================================================
    @Nested
    @DisplayName("FloatingPopulationController API 测试")
    class FloatingPopulationControllerTests {

        @Test
        @DisplayName("GET /api/floating-population/{id} - 查询流动人口")
        void getFloating_success() throws Exception {
            FloatingPopulation fp = new FloatingPopulation();
            fp.setFloatingId(1L);
            fp.setCurrentRegionCode("110101");
            when(floatingService.getById(1L)).thenReturn(fp);

            mockMvc.perform(get("/api/floating-population/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("PUT /api/floating-population/{id}/leave - 离开登记")
        void leaveFloating_success() throws Exception {
            FloatingLeaveDTO dto = new FloatingLeaveDTO();
            dto.setFloatingId(1L);
            dto.setActualLeaveDate(LocalDate.now().minusDays(1));

            FloatingPopulation fp = new FloatingPopulation();
            fp.setFloatingId(1L);
            when(floatingService.leave(eq(1L), any())).thenReturn(fp);

            mockMvc.perform(put("/api/floating-population/{id}/leave", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("DELETE /api/floating-population/{id} - 禁用删除返回 405")
        void deleteFloating_disabled() throws Exception {
            mockMvc.perform(delete("/api/floating-population/{id}", 1L))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value(405));
        }
    }

    // ====================================================================
    // ResidencePermitController 测试
    // ====================================================================
    @Nested
    @DisplayName("ResidencePermitController API 测试")
    class ResidencePermitControllerTests {

        @Test
        @DisplayName("GET /api/residence-permits/{id} - 查询凭证")
        void getPermit_success() throws Exception {
            ResidencePermit p = new ResidencePermit();
            p.setPermitId(1L);
            when(permitService.getById(1L)).thenReturn(p);

            mockMvc.perform(get("/api/residence-permits/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("PUT /api/residence-permits/{id}/cancel - 注销凭证")
        void cancelPermit_success() throws Exception {
            mockMvc.perform(put("/api/residence-permits/{id}/cancel", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(permitService).cancel(1L);
        }
    }

    // ====================================================================
    // ApplicationMaterialController 测试
    // ====================================================================
    @Nested
    @DisplayName("ApplicationMaterialController API 测试")
    class ApplicationMaterialControllerTests {

        @Test
        @DisplayName("GET /api/application-materials/by-application/{applicationId} - 查询申请材料")
        void listByApplication_success() throws Exception {
            ApplicationMaterial m = new ApplicationMaterial();
            m.setMaterialId(1L);
            when(materialService.listByApplication(100L)).thenReturn(List.of(m));

            mockMvc.perform(get("/api/application-materials/by-application/{applicationId}", 100L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("PUT /api/application-materials/{id}/verify - 核验材料")
        void verifyMaterial_success() throws Exception {
            mockMvc.perform(put("/api/application-materials/{id}/verify", 1L)
                            .param("verifierId", "99")
                            .param("passed", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(materialService).verify(1L, 99L, true);
        }
    }

    // ====================================================================
    // SysRolePermissionController 测试
    // ====================================================================
    @Nested
    @DisplayName("SysRolePermissionController API 测试")
    class SysRolePermissionControllerTests {

        @Test
        @DisplayName("GET /api/sys-role-permissions/role/{roleId} - 查询角色权限")
        void listByRole_success() throws Exception {
            when(rolePermissionService.listPermissionIdsByRole(1L))
                    .thenReturn(List.of(1L, 2L, 3L));

            mockMvc.perform(get("/api/sys-role-permissions/role/{roleId}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("PUT /api/sys-role-permissions/role/{roleId} - 分配权限")
        void assignPermissions_success() throws Exception {
            mockMvc.perform(put("/api/sys-role-permissions/role/{roleId}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[1, 2, 3]"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(rolePermissionService).assignPermissions(eq(1L), anyList());
        }
    }

    // ====================================================================
    // LoginLogController 测试
    // ====================================================================
    @Nested
    @DisplayName("LoginLogController API 测试")
    class LoginLogControllerTests {

        @Test
        @DisplayName("GET /api/login-logs/{id} - 查询登录日志")
        void getLoginLog_success() throws Exception {
            LoginLog log = new LoginLog();
            log.setLogId(1L);
            log.setUsername("admin");
            when(loginLogService.getById(1L)).thenReturn(log);

            mockMvc.perform(get("/api/login-logs/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ====================================================================
    // OperationLogController 测试
    // ====================================================================
    @Nested
    @DisplayName("OperationLogController API 测试")
    class OperationLogControllerTests {

        @Test
        @DisplayName("GET /api/operation-logs/{id} - 查询操作日志")
        void getOperationLog_success() throws Exception {
            OperationLog log = new OperationLog();
            log.setLogId(1L);
            log.setModuleName("PERSON");
            when(operationLogService.getById(1L)).thenReturn(log);

            mockMvc.perform(get("/api/operation-logs/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ====================================================================
    // 错误处理测试
    // ====================================================================
    @Nested
    @DisplayName("全局错误处理测试")
    class GlobalExceptionHandlerTests {

        @Test
        @DisplayName("BizException(400) - 返回 400 状态码")
        void bizException_400() throws Exception {
            when(personService.getById(1L)).thenThrow(new BizException(400, "请求参数错误"));

            mockMvc.perform(get("/api/persons/{id}", 1L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("请求参数错误"));
        }

        @Test
        @DisplayName("BizException(404) - 返回 404 状态码")
        void bizException_404() throws Exception {
            when(personService.getById(1L)).thenThrow(new NotFoundException("资源不存在"));

            mockMvc.perform(get("/api/persons/{id}", 1L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }

        @Test
        @DisplayName("BizException(409) - 返回 409 状态码")
        void bizException_409() throws Exception {
            when(personService.getById(1L)).thenThrow(new BizException(409, "数据冲突"));

            mockMvc.perform(get("/api/persons/{id}", 1L))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409));
        }

        @Test
        @DisplayName("RuntimeException - 返回 500 状态码")
        void runtimeException_500() throws Exception {
            when(personService.getById(1L)).thenThrow(new RuntimeException("系统错误"));

            mockMvc.perform(get("/api/persons/{id}", 1L))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500));
        }

        @Test
        @DisplayName("Result JSON 格式验证")
        void resultJsonFormat() throws Exception {
            Person p = new Person();
            p.setPersonId(1L);
            when(personService.getById(1L)).thenReturn(p);

            mockMvc.perform(get("/api/persons/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    // ====================================================================
    // 辅助方法
    // ====================================================================

    private static PersonCreateDTO validPersonDto() {
        PersonCreateDTO dto = new PersonCreateDTO();
        dto.setApplicationId(1L);
        dto.setName("张三");
        dto.setGenderCode("MALE");
        dto.setIdentityTypeCode("ID_CARD");
        dto.setIdentityNo("110101199001011237");
        dto.setBirthDate(LocalDate.of(1990, 1, 1));
        dto.setEthnicityCode("HAN");
        dto.setPhone("13800138000");
        return dto;
    }

    private static HouseholdCreateDTO validHouseholdDto() {
        HouseholdCreateDTO dto = new HouseholdCreateDTO();
        dto.setApplicationId(1L);
        dto.setHouseholdNo("H110101001");
        dto.setHouseholdTypeCode("FAMILY");
        dto.setRegisteredAddress("北京市朝阳区某街道");
        dto.setRegionCode("110105");
        dto.setDepartmentId(1L);
        dto.setEstablishDate(LocalDate.of(2026, 7, 1));
        dto.setStatus("ACTIVE");
        return dto;
    }

    private static MigrationOutDTO validMigrationOutDto() {
        MigrationOutDTO dto = new MigrationOutDTO();
        dto.setApplicationId(1L);
        dto.setPersonId(100L);
        dto.setOutTypeCode("CROSS_DISTRICT");
        dto.setFromHouseholdId(200L);
        dto.setFromRegionCode("110101");
        dto.setToRegionCode("310101");
        dto.setToAddress("上海市黄浦区某街道");
        dto.setOutDate(LocalDate.of(2026, 7, 1));
        return dto;
    }

    private static MigrationInDTO validMigrationInDto() {
        MigrationInDTO dto = new MigrationInDTO();
        dto.setApplicationId(1L);
        dto.setPersonId(100L);
        dto.setInTypeCode("CROSS_DISTRICT");
        dto.setToHouseholdId(200L);
        dto.setToRegionCode("110101");
        dto.setFromRegionCode("310101");
        dto.setFromAddress("上海市黄浦区某街道");
        dto.setInDate(LocalDate.of(2026, 7, 1));
        return dto;
    }

    private static void loginAsL3With(String... perms) {
        Set<String> set = new HashSet<>();
        for (String p : perms) set.add(p);
        SecurityContext.set(
                SecurityContext.builder()
                        .userId(99L)
                        .username("l3_admin")
                        .permissionLevel(3)
                        .roleCode("L3_ADMIN")
                        .dataScopeCode("ALL")
                        .permissionCodes(set)
                        .build());
    }

    private static void loginAsL2With(String... perms) {
        Set<String> set = new HashSet<>();
        for (String p : perms) set.add(p);
        SecurityContext.set(
                SecurityContext.builder()
                        .userId(88L)
                        .username("l2_user")
                        .permissionLevel(2)
                        .roleCode("L2_HANDLE")
                        .dataScopeCode("DEPT")
                        .permissionCodes(set)
                        .build());
    }

    private static void loginAsL3() {
        loginAsL3With("person:query", "person:create", "person:update",
                "household:query", "household:create", "household:update", "household:establish",
                "migration:query", "migration:out:create", "migration:in:create",
                "cancellation:person", "cancellation:household",
                "approval:approve");
    }
}
