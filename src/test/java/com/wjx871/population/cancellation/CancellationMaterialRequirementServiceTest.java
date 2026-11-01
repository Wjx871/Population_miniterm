package com.wjx871.population.cancellation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.wjx871.population.common.BusinessException;
import com.wjx871.population.material.ApplicationMaterial;
import com.wjx871.population.material.ApplicationMaterialMapper;
import com.wjx871.population.material.MaterialVerifyStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CancellationMaterialRequirementServiceTest {
    @Mock
    private ApplicationMaterialMapper mapper;

    @InjectMocks
    private CancellationMaterialRequirementService service;

    @ParameterizedTest
    @CsvSource({
            "DEATH, DEATH_CERTIFICATE",
            "DECLARED_DEAD, DECLARED_DEAD_JUDGMENT",
            "SETTLED_ABROAD, SETTLEMENT_ABROAD_PROOF",
            "OTHER_APPROVED, CANCELLATION_APPLICATION"
    })
    void personCancellationRequiresReasonSpecificProof(CancelReasonCode reason, String proofType) {
        CancellationRecord record = personRecord(reason);
        when(mapper.selectByApplicationId(42L)).thenReturn(List.of(
                material("APPLICANT_IDENTITY_PROOF", true, MaterialVerifyStatus.VERIFIED),
                material("HOUSEHOLD_BOOK", true, MaterialVerifyStatus.VERIFIED)));

        assertThatThrownBy(() -> service.verified(record))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(proofType);
        verify(mapper).selectByApplicationId(42L);
    }

    @ParameterizedTest
    @CsvSource({
            "DEATH, DEATH_CERTIFICATE",
            "DECLARED_DEAD, DECLARED_DEAD_JUDGMENT",
            "SETTLED_ABROAD, SETTLEMENT_ABROAD_PROOF",
            "OTHER_APPROVED, CANCELLATION_APPLICATION"
    })
    void verifiedPersonMaterialsAllowExecution(CancelReasonCode reason, String proofType) {
        when(mapper.selectByApplicationId(42L)).thenReturn(List.of(
                material("APPLICANT_IDENTITY_PROOF", true, MaterialVerifyStatus.VERIFIED),
                material("HOUSEHOLD_BOOK", true, MaterialVerifyStatus.VERIFIED),
                material(proofType, true, MaterialVerifyStatus.VERIFIED)));

        assertThatCode(() -> service.verified(personRecord(reason))).doesNotThrowAnyException();
    }

    @Test
    void duplicateRegistrationDoesNotRequireHouseholdBook() {
        when(mapper.selectByApplicationId(42L)).thenReturn(List.of(
                material("APPLICANT_IDENTITY_PROOF", true, MaterialVerifyStatus.VERIFIED),
                material("DUPLICATE_REGISTRATION_PROOF", true, MaterialVerifyStatus.VERIFIED)));

        assertThatCode(() -> service.verified(personRecord(CancelReasonCode.DUPLICATE_REGISTRATION)))
                .doesNotThrowAnyException();
    }

    @Test
    void pendingReasonProofBlocksExecutionEvenWhenOtherMaterialsAreVerified() {
        when(mapper.selectByApplicationId(42L)).thenReturn(List.of(
                material("CANCELLATION_APPLICATION", true, MaterialVerifyStatus.VERIFIED),
                material("HOUSEHOLD_BOOK", true, MaterialVerifyStatus.VERIFIED),
                material("HOUSEHOLD_CANCELLATION_PROOF", true, MaterialVerifyStatus.PENDING)));

        assertThatCode(() -> service.validate(householdRecord())).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.verified(householdRecord()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("家庭户销户原因证明尚未核验通过");
    }

    @Test
    void missingHouseholdReasonProofBlocksSubmission() {
        when(mapper.selectByApplicationId(42L)).thenReturn(List.of(
                material("CANCELLATION_APPLICATION", true, MaterialVerifyStatus.PENDING),
                material("HOUSEHOLD_BOOK", true, MaterialVerifyStatus.PENDING)));

        assertThatThrownBy(() -> service.validate(householdRecord()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少家庭户销户原因证明");
    }

    private CancellationRecord personRecord(CancelReasonCode reason) {
        CancellationRecord record = new CancellationRecord();
        record.setApplicationId(42L);
        record.setCancelObjectType(CancelObjectType.PERSON);
        record.setCancelReasonCode(reason);
        return record;
    }

    @Test
    void householdReasonProofIsAcceptedOnSubmitEvenWhenItsRequiredFlagIsFalse() {
        CancellationRecord record = householdRecord();
        when(mapper.selectByApplicationId(42L)).thenReturn(List.of(
                material("CANCELLATION_APPLICATION", false, MaterialVerifyStatus.PENDING),
                material("HOUSEHOLD_BOOK", true, MaterialVerifyStatus.PENDING),
                material("HOUSEHOLD_CANCELLATION_PROOF", false, MaterialVerifyStatus.PENDING)));

        assertThatCode(() -> service.validate(record)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.verified(record))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("核验通过");
    }

    @Test
    void verifiedHouseholdReasonProofAllowsExecution() {
        CancellationRecord record = householdRecord();
        when(mapper.selectByApplicationId(42L)).thenReturn(List.of(
                material("CANCELLATION_APPLICATION", true, MaterialVerifyStatus.VERIFIED),
                material("HOUSEHOLD_BOOK", true, MaterialVerifyStatus.VERIFIED),
                material("HOUSEHOLD_MERGE_PROOF", false, MaterialVerifyStatus.VERIFIED)));

        assertThatCode(() -> service.verified(record)).doesNotThrowAnyException();
    }

    private CancellationRecord householdRecord() {
        CancellationRecord record = new CancellationRecord();
        record.setApplicationId(42L);
        record.setCancelObjectType(CancelObjectType.HOUSEHOLD);
        record.setCancelReasonCode(CancelReasonCode.NO_ACTIVE_MEMBERS);
        return record;
    }

    private ApplicationMaterial material(String type, boolean required, MaterialVerifyStatus verifyStatus) {
        ApplicationMaterial material = new ApplicationMaterial();
        material.setMaterialType(type);
        material.setRequiredFlag(required);
        material.setVerifyStatus(verifyStatus);
        return material;
    }
}
