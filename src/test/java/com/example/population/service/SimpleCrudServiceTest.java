package com.example.population.service;

import com.example.population.entity.ApplicationMaterial;
import com.example.population.entity.BusinessApplication;
import com.example.population.entity.KeyPopulation;
import com.example.population.entity.ResidencePermit;
import com.example.population.mapper.ApplicationMaterialMapper;
import com.example.population.mapper.BusinessApplicationMapper;
import com.example.population.mapper.KeyPopulationMapper;
import com.example.population.mapper.PersonMapper;
import com.example.population.mapper.ResidencePermitMapper;
import com.example.population.service.impl.ApplicationMaterialServiceImpl;
import com.example.population.service.impl.BusinessApplicationServiceImpl;
import com.example.population.service.impl.KeyPopulationServiceImpl;
import com.example.population.service.impl.ResidencePermitServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 简单 CRUD 包装型 Service 的单测:
 *   - ResidencePermitServiceImpl#cancel
 *   - KeyPopulationServiceImpl#release
 *   - BusinessApplicationServiceImpl#submit / getDetail
 *   - ApplicationMaterialServiceImpl#verify
 *
 * 它们都继承 ServiceImpl,Mokcito @InjectMocks 不注入父类 baseMapper,
 * 所以这里用 ReflectionTestUtils.setField 手动注入。
 */
@ExtendWith(MockitoExtension.class)
class SimpleCrudServiceTest {

    // ---------- ResidencePermit ----------

    @Mock private ResidencePermitMapper permitMapper;
    private ResidencePermitServiceImpl permitService;

    @BeforeEach
    void setUp() {
        permitService = new ResidencePermitServiceImpl();
        ReflectionTestUtils.setField(permitService, "baseMapper", permitMapper);
    }

    @Test
    @DisplayName("ResidencePermit.cancel: 许可不存在 → false")
    void permitCancel_missing() {
        when(permitMapper.selectById(1L)).thenReturn(null);
        assertThat(permitService.cancel(1L)).isFalse();
    }

    @Test
    @DisplayName("ResidencePermit.cancel: 存在 → CANCELLED + cancelDate=今天")
    void permitCancel_ok() {
        ResidencePermit p = new ResidencePermit();
        p.setPermitId(1L);
        p.setPermitStatus("ACTIVE");
        when(permitMapper.selectById(1L)).thenReturn(p);
        when(permitMapper.updateById(any(ResidencePermit.class))).thenReturn(1);

        boolean ok = permitService.cancel(1L);
        assertThat(ok).isTrue();
        assertThat(p.getPermitStatus()).isEqualTo("CANCELLED");
        assertThat(p.getCancelDate()).isEqualTo(LocalDate.now());
    }

    // ---------- KeyPopulation ----------

    @Mock private KeyPopulationMapper keyMapper;
    @Mock private PersonMapper personMapper;
    @Mock private BusinessApplicationMapper businessApplicationMapper;
    @Mock private com.example.population.util.DictionaryValidator dictionaryValidator;
    private KeyPopulationServiceImpl keyService;

    @BeforeEach
    void setUpKey() {
        keyService = new KeyPopulationServiceImpl(personMapper, businessApplicationMapper, dictionaryValidator);
        ReflectionTestUtils.setField(keyService, "baseMapper", keyMapper);
    }

    @Test
    @DisplayName("KeyPopulation.release: 不存在 → NotFoundException")
    void keyRelease_missing() {
        when(keyMapper.selectById(1L)).thenReturn(null);
        // release 严格校验：找不到记录时直接抛 NotFoundException，
        // 与其它 Service（如 HouseholdServiceImpl.changeHead）保持一致。
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> keyService.release(1L, 99L))
                .isInstanceOf(com.example.population.exception.NotFoundException.class)
                .hasMessageContaining("重点记录[1]");
    }

    @Test
    @DisplayName("KeyPopulation.release: 存在 → RELEASED + releaseApplicationId + manageEndDate=今天")
    void keyRelease_ok() {
        KeyPopulation k = new KeyPopulation();
        k.setKeyId(1L);
        k.setStatus("ACTIVE");
        when(keyMapper.selectById(1L)).thenReturn(k);
        // release() 还会校验解除申请单是否存在（releaseApplicationId 不为空时）
        when(businessApplicationMapper.selectById(99L)).thenReturn(new com.example.population.entity.BusinessApplication());
        when(keyMapper.updateById(any(KeyPopulation.class))).thenReturn(1);

        boolean ok = keyService.release(1L, 99L);
        assertThat(ok).isTrue();
        assertThat(k.getStatus()).isEqualTo("RELEASED");
        assertThat(k.getReleaseApplicationId()).isEqualTo(99L);
        assertThat(k.getManageEndDate()).isEqualTo(LocalDate.now());
    }

    // ---------- BusinessApplication ----------

    @Mock private BusinessApplicationMapper appMapper;
    private BusinessApplicationServiceImpl appService;

    @BeforeEach
    void setUpApp() {
        appService = new BusinessApplicationServiceImpl();
        ReflectionTestUtils.setField(appService, "baseMapper", appMapper);
    }

    @Test
    @DisplayName("BusinessApplication.submit: 不存在 → false")
    void appSubmit_missing() {
        when(appMapper.selectById(1L)).thenReturn(null);
        assertThat(appService.submit(1L)).isFalse();
    }

    @Test
    @DisplayName("BusinessApplication.submit: 存在 → SUBMITTED + PENDING_APPROVAL + submittedAt")
    void appSubmit_ok() {
        BusinessApplication app = new BusinessApplication();
        app.setApplicationId(1L);
        app.setStatus("DRAFT");
        when(appMapper.selectById(1L)).thenReturn(app);
        when(appMapper.updateById(any(BusinessApplication.class))).thenReturn(1);

        boolean ok = appService.submit(1L);
        assertThat(ok).isTrue();
        assertThat(app.getStatus()).isEqualTo("SUBMITTED");
        assertThat(app.getCurrentStep()).isEqualTo("PENDING_APPROVAL");
        assertThat(app.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("BusinessApplication.getDetail: 等价于 selectById")
    void appGetDetail() {
        BusinessApplication app = new BusinessApplication();
        app.setApplicationId(1L);
        when(appMapper.selectById(1L)).thenReturn(app);
        assertThat(appService.getDetail(1L)).isSameAs(app);
    }

    // ---------- ApplicationMaterial ----------

    @Mock private ApplicationMaterialMapper matMapper;
    private ApplicationMaterialServiceImpl matService;

    @BeforeEach
    void setUpMat() {
        matService = new ApplicationMaterialServiceImpl();
        ReflectionTestUtils.setField(matService, "baseMapper", matMapper);
    }

    @Test
    @DisplayName("ApplicationMaterial.verify: 不存在 → false")
    void matVerify_missing() {
        when(matMapper.selectById(1L)).thenReturn(null);
        assertThat(matService.verify(1L, 9L, true)).isFalse();
    }

    @Test
    @DisplayName("ApplicationMaterial.verify(passed=true) → VERIFIED + verifierId + verifiedAt")
    void matVerify_pass() {
        ApplicationMaterial m = new ApplicationMaterial();
        m.setMaterialId(1L);
        when(matMapper.selectById(1L)).thenReturn(m);
        when(matMapper.updateById(any(ApplicationMaterial.class))).thenReturn(1);

        boolean ok = matService.verify(1L, 9L, true);
        assertThat(ok).isTrue();
        assertThat(m.getVerifyStatus()).isEqualTo("VERIFIED");
        assertThat(m.getVerifiedBy()).isEqualTo(9L);
        assertThat(m.getVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("ApplicationMaterial.verify(passed=false) → REJECTED + verifierId + verifiedAt")
    void matVerify_reject() {
        ApplicationMaterial m = new ApplicationMaterial();
        m.setMaterialId(1L);
        when(matMapper.selectById(1L)).thenReturn(m);
        when(matMapper.updateById(any(ApplicationMaterial.class))).thenReturn(1);

        boolean ok = matService.verify(1L, 9L, false);
        assertThat(ok).isTrue();
        assertThat(m.getVerifyStatus()).isEqualTo("REJECTED");
        assertThat(m.getVerifiedBy()).isEqualTo(9L);
        assertThat(m.getVerifiedAt()).isNotNull();
    }
}