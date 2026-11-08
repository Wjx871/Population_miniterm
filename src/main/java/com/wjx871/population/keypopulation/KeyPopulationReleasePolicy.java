package com.wjx871.population.keypopulation;

import com.wjx871.population.application.*;
import com.wjx871.population.auth.AuthService;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.security.*;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** The same record-based management boundary applies at submit, approval and explicit execution. */
@Component
@RequiredArgsConstructor
class KeyPopulationReleasePolicy implements ApplicationApprovalValidator {
    private final KeyPopulationMapper mapper;
    private final AuthService auth;

    @Override public boolean supports(BusinessType type) { return type == BusinessType.KEY_POPULATION_RELEASE; }

    void validateSubmission(BusinessApplication application, KeyPopulationApplication detail) {
        requireIdentity(application, detail);
        verifyRecord(detail, mapper.findManagedRecord(detail.getRecordId(), DataScopeCriteria.current()).orElseThrow(this::denied));
    }

    @Override public void validate(BusinessApplication application) {
        KeyPopulationApplication detail = mapper.findApplication(application.getApplicationId()).orElseThrow(this::denied);
        requireIdentity(application, detail);
        requireCurrentApplicant(application, detail);
    }

    KeyPopulationRecord lockForExecution(BusinessApplication application, KeyPopulationApplication detail) {
        requireIdentity(application, detail);
        KeyPopulationRecord record = mapper.lockManagedRecord(detail.getRecordId(), DataScopeCriteria.current()).orElseThrow(this::denied);
        verifyRecord(detail, record);
        requireCurrentApplicant(application, detail);
        return record;
    }

    private void requireCurrentApplicant(BusinessApplication application, KeyPopulationApplication detail) {
        AuthenticatedUser applicant = auth.loadUserById(application.getApplicantUserId());
        if (!applicant.permissions().contains("key-population:apply") || !applicant.permissions().contains("application:submit")) throw denied();
        DataScopeCriteria scope = new DataScopeCriteria(applicant.dataScope(), applicant.userId(), applicant.departmentId(), applicant.regionCode(), applicant.regionCodes());
        verifyRecord(detail, mapper.findManagedRecord(detail.getRecordId(), scope).orElseThrow(this::denied));
    }

    private void requireIdentity(BusinessApplication application, KeyPopulationApplication detail) {
        if (application.getBusinessType() != BusinessType.KEY_POPULATION_RELEASE
                || detail.getOperationType() != KeyOperationType.RELEASE || detail.getRecordId() == null
                || !Objects.equals(application.getApplicationId(), detail.getApplicationId())
                || !Objects.equals(application.getTargetPersonId(), detail.getPersonId())) throw denied();
    }

    private void verifyRecord(KeyPopulationApplication detail, KeyPopulationRecord record) {
        if (!Objects.equals(detail.getRecordId(), record.getRecordId())
                || !Objects.equals(detail.getPersonId(), record.getPersonId())
                || !Objects.equals(detail.getPopulationType(), record.getPopulationType())) throw denied();
    }

    private BusinessException denied() { return new BusinessException(HttpStatus.FORBIDDEN, "解除申请记录关联或当前管理范围无法验证"); }
}
