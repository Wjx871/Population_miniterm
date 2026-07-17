package com.wjx871.population.cancellation;

import com.wjx871.population.application.ApplicationApprovalValidator;
import com.wjx871.population.application.BusinessApplication;
import com.wjx871.population.application.BusinessType;
import com.wjx871.population.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Makes the specialised cancellation material rule part of the approval gate. */
@Component
@RequiredArgsConstructor
public class CancellationApprovalValidator implements ApplicationApprovalValidator {
    private final CancellationMapper mapper;
    private final CancellationMaterialRequirementService materials;

    @Override
    public boolean supports(BusinessType businessType) {
        return businessType == BusinessType.PERSON_CANCELLATION
                || businessType == BusinessType.HOUSEHOLD_CANCELLATION;
    }

    @Override
    public void validate(BusinessApplication application) {
        CancellationRecord record = mapper.findByApplication(application.getApplicationId())
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "注销专业详情不存在"));
        materials.verified(record);
    }
}
