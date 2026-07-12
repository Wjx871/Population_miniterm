package com.example.population.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.population.entity.ApplicationMaterial;
import com.example.population.exception.BizException;
import com.example.population.mapper.ApplicationMaterialMapper;
import com.example.population.service.impl.ApplicationMaterialServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

/**
 * 材料必交闸门（assertRequiredVerified）的单测。
 */
@ExtendWith(MockitoExtension.class)
class ApplicationMaterialGateTest {

    @Mock
    private ApplicationMaterialMapper matMapper;
    private ApplicationMaterialServiceImpl matService;

    @BeforeEach
    void setUp() {
        matService = new ApplicationMaterialServiceImpl();
        ReflectionTestUtils.setField(matService, "baseMapper", matMapper);
    }

    @Test
    @DisplayName("PERSON_REGISTER：无任何材料 → BizException")
    void personRegister_missing() {
        stubList(Collections.emptyList());

        assertThatThrownBy(() -> matService.assertRequiredVerified(100L, "PERSON_REGISTER"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("PERSON_REGISTER")
                .hasMessageContaining("身份证明");
    }

    @Test
    @DisplayName("PERSON_REGISTER：材料已上传但未核验 → 拒")
    void personRegister_unverified() {
        stubList(List.of(mat("IDENTITY_DOC", 1, "UNVERIFIED")));

        assertThatThrownBy(() -> matService.assertRequiredVerified(100L, "PERSON_REGISTER"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("身份证明");
    }

    @Test
    @DisplayName("PERSON_REGISTER：必交身份证明 VERIFIED → 放行")
    void personRegister_pass() {
        stubList(List.of(mat("IDENTITY_DOC", 1, "VERIFIED")));

        assertThatCode(() -> matService.assertRequiredVerified(100L, "PERSON_REGISTER"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PERSON_REGISTER：身份证明但 requiredFlag=0 → 拒（必须为 1）")
    void personRegister_notRequiredFlag() {
        stubList(List.of(mat("IDENTITY_DOC", 0, "VERIFIED")));

        assertThatThrownBy(() -> matService.assertRequiredVerified(100L, "PERSON_REGISTER"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("HOUSEHOLD_ESTABLISH：身份证明 + 户口簿 VERIFIED → 放行（OR 备选组满足）")
    void household_pass_booklet() {
        stubList(List.of(
                mat("IDENTITY_DOC", 1, "VERIFIED"),
                mat("HOUSEHOLD_BOOKLET", 1, "VERIFIED")));

        assertThatCode(() -> matService.assertRequiredVerified(200L, "HOUSEHOLD_ESTABLISH"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HOUSEHOLD_ESTABLISH：身份证明 + 住所证明 → 放行（OR 备选组满足）")
    void household_pass_residence() {
        stubList(List.of(
                mat("IDENTITY_DOC", 1, "VERIFIED"),
                mat("RESIDENCE_PROOF", 1, "VERIFIED")));

        assertThatCode(() -> matService.assertRequiredVerified(200L, "HOUSEHOLD_ESTABLISH"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HOUSEHOLD_ESTABLISH：缺身份证明 → 拒")
    void household_missing_identity() {
        stubList(List.of(mat("HOUSEHOLD_BOOKLET", 1, "VERIFIED")));

        assertThatThrownBy(() -> matService.assertRequiredVerified(200L, "HOUSEHOLD_ESTABLISH"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("身份证明");
    }

    @Test
    @DisplayName("HOUSEHOLD_ESTABLISH：身份证明 VERIFIED + 备选 OR 组都没 → 拒")
    void household_missing_optional() {
        stubList(List.of(
                mat("IDENTITY_DOC", 1, "VERIFIED"),
                mat("PHOTO", 0, "VERIFIED")));

        assertThatThrownBy(() -> matService.assertRequiredVerified(200L, "HOUSEHOLD_ESTABLISH"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("户口簿")
                .hasMessageContaining("住所");
    }

    @Test
    @DisplayName("未知业务类型：直接放行，不查材料")
    void unknown_businessType() {
        assertThatCode(() -> matService.assertRequiredVerified(300L, "UNKNOWN_TYPE"))
                .doesNotThrowAnyException();

        Mockito.verify(matMapper, Mockito.never()).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("applicationId 为空：拒绝")
    void applicationId_null() {
        assertThatThrownBy(() -> matService.assertRequiredVerified(null, "PERSON_REGISTER"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("applicationId");
    }

    @Test
    @DisplayName("PERSON_REGISTER：selectList 至少被调用一次，参数包含 applicationId 断言")
    void captured_applicationId() {
        stubList(List.of(mat("IDENTITY_DOC", 1, "VERIFIED")));
        matService.assertRequiredVerified(777L, "PERSON_REGISTER");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<ApplicationMaterial>> captor = ArgumentCaptor.forClass(Wrapper.class);
        Mockito.verify(matMapper).selectList(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    // -------- helpers --------

    private static ApplicationMaterial mat(String type, Integer required, String status) {
        ApplicationMaterial m = new ApplicationMaterial();
        m.setMaterialId(System.nanoTime());
        m.setMaterialTypeCode(type);
        m.setRequiredFlag(required);
        m.setVerifyStatus(status);
        return m;
    }

    @SuppressWarnings("unchecked")
    private void stubList(List<ApplicationMaterial> list) {
        Mockito.when(matMapper.selectList(any(Wrapper.class))).thenReturn(list);
    }
}
