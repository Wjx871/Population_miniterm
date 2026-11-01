package com.wjx871.population.migration;

import com.wjx871.population.application.*;
import com.wjx871.population.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MigrationSubmissionValidator implements ApplicationSubmissionValidator {
    private final MigrationMapper mapper;
    private final MigrationMaterialRequirementService rules;

    @Override
    public boolean supports(BusinessType type) {
        return type == BusinessType.MIGRATION_IN || type == BusinessType.MIGRATION_OUT;
    }

    @Override
    public void validate(BusinessApplication application) {
        if (application.getBusinessType() == BusinessType.MIGRATION_IN) {
            MigrationIn migration = mapper.findIn(application.getApplicationId())
                    .orElseThrow(() -> bad("缺少迁入专业详情"));
            rules.validateIn(migration);
            return;
        }

        MigrationOut migration = mapper.findOut(application.getApplicationId())
                .orElseThrow(() -> bad("缺少迁出专业详情"));
        HouseholdSnapshot household = mapper.findHousehold(migration.getFromHouseholdId())
                .orElseThrow(() -> bad("原家庭户不存在"));
        boolean headWithOtherMembers = migration.getPersonId().equals(household.getHeadPersonId())
                && household.getActiveMemberCount() > 1;
        if (headWithOtherMembers) {
            Long newHead = migration.getNewHeadPersonId();
            if (newHead == null || newHead.equals(migration.getPersonId())
                    || !mapper.activeMembers(household.getHouseholdId()).contains(newHead)) {
                throw bad("户主迁出且仍有其他成员时必须指定本户其他有效成员为新户主");
            }
        }
        rules.validateOut(migration, headWithOtherMembers);
    }

    private BusinessException bad(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }
}