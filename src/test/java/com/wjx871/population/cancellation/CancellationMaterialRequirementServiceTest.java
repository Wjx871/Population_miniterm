package com.wjx871.population.cancellation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.wjx871.population.common.BusinessException;
import com.wjx871.population.material.ApplicationMaterial;
import com.wjx871.population.material.ApplicationMaterialMapper;
import com.wjx871.population.material.MaterialVerifyStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CancellationMaterialRequirementServiceTest {
    private final ApplicationMaterialMapper mapper = Mockito.mock(ApplicationMaterialMapper.class);
    private final CancellationMaterialRequirementService service = new CancellationMaterialRequirementService(mapper);

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
