package com.wjx871.population.cancellation;

import com.wjx871.population.common.BusinessException;
import com.wjx871.population.material.ApplicationMaterialMapper;
import com.wjx871.population.material.MaterialVerifyStatus;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CancellationMaterialRequirementService {
    private final ApplicationMaterialMapper mapper;

    /** 校验已上传的材料类型；提交时材料可以尚未完成核验。 */
    public void validate(CancellationRecord record) {
        validateTypes(record, materialTypes(record.getApplicationId(), false), false);
    }

    /** 执行注销前校验所有业务规则所需材料均已核验通过。 */
    public void verified(CancellationRecord record) {
        validateTypes(record, materialTypes(record.getApplicationId(), true), true);
    }

    private Set<String> materialTypes(Long applicationId, boolean verifiedOnly) {
        Set<String> types = new HashSet<>();
        mapper.selectByApplicationId(applicationId).stream()
                .filter(material -> !verifiedOnly || material.getVerifyStatus() == MaterialVerifyStatus.VERIFIED)
                .forEach(material -> types.add(material.getMaterialType()));
        return types;
    }

    private void validateTypes(CancellationRecord record, Set<String> types, boolean verifiedOnly) {
        if (record.getCancelObjectType() == CancelObjectType.HOUSEHOLD) {
            require(types, "CANCELLATION_APPLICATION", verifiedOnly);
            require(types, "HOUSEHOLD_BOOK", verifiedOnly);
            if (!types.contains("HOUSEHOLD_CANCELLATION_PROOF") && !types.contains("HOUSEHOLD_MERGE_PROOF")) {
                fail(verifiedOnly ? "家庭户销户原因证明尚未核验通过" : "缺少家庭户销户原因证明");
            }
            return;
        }

        require(types, "APPLICANT_IDENTITY_PROOF", verifiedOnly);
        // 使用 if-else 而非 switch：避免编译器生成 CancellationMaterialRequirementService$1
        // 合成类；热更新/增量编译丢失该类时会 NoClassDefFoundError → 前端显示「服务器处理请求失败」
        CancelReasonCode reason = record.getCancelReasonCode();
        if (reason == CancelReasonCode.DEATH) {
            require(types, "DEATH_CERTIFICATE", verifiedOnly);
        } else if (reason == CancelReasonCode.DECLARED_DEAD) {
            require(types, "DECLARED_DEAD_JUDGMENT", verifiedOnly);
        } else if (reason == CancelReasonCode.SETTLED_ABROAD) {
            require(types, "SETTLEMENT_ABROAD_PROOF", verifiedOnly);
        } else if (reason == CancelReasonCode.DUPLICATE_REGISTRATION) {
            require(types, "DUPLICATE_REGISTRATION_PROOF", verifiedOnly);
        } else {
            require(types, "CANCELLATION_APPLICATION", verifiedOnly);
        }
        if (reason != CancelReasonCode.DUPLICATE_REGISTRATION) {
            require(types, "HOUSEHOLD_BOOK", verifiedOnly);
        }
    }

    private void require(Set<String> types, String type, boolean verifiedOnly) {
        if (!types.contains(type)) {
            fail(verifiedOnly ? "必需材料尚未全部核验通过：" + type : "缺少必需材料：" + type);
        }
    }

    private void fail(String message) {
        throw new BusinessException(HttpStatus.CONFLICT, message);
    }
}
