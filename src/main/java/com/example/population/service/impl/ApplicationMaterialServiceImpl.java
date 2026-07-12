package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.ApplicationMaterial;
import com.example.population.exception.BizException;
import com.example.population.mapper.ApplicationMaterialMapper;
import com.example.population.service.ApplicationMaterialService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class ApplicationMaterialServiceImpl extends ServiceImpl<ApplicationMaterialMapper, ApplicationMaterial>
        implements ApplicationMaterialService {

    @Override
    public List<ApplicationMaterial> listByApplication(Long applicationId) {
        return this.list(new LambdaQueryWrapper<ApplicationMaterial>()
                .eq(ApplicationMaterial::getApplicationId, applicationId)
                .orderByAsc(ApplicationMaterial::getMaterialId));
    }

    @Override
    public boolean verify(Long materialId, Long verifierId, boolean passed) {
        ApplicationMaterial m = this.getById(materialId);
        if (m == null) {
            return false;
        }
        m.setVerifyStatus(passed ? "VERIFIED" : "REJECTED");
        m.setVerifiedBy(verifierId);
        m.setVerifiedAt(LocalDateTime.now());
        return this.updateById(m);
    }

    /**
     * 按业务类别强制校验"最低必交材料是否齐备且已核验"。
     * <p>
     * 规则来自《数据库设计v4.0_Cursor详细说明.md》§7 业务材料与核验规则。
     * <p>
     * 校验语义：每个 businessType 有两组要求——
     * <ul>
     *   <li>必交组（AND）：组内每项都必须有 VERIFIED 材料</li>
     *   <li>可选组（OR，多选一）：组内任一项有 VERIFIED 即视为通过</li>
     * </ul>
     * 未列入清单的业务类型视为"无最低必交"，直接放行。
     */
    @Override
    public void assertRequiredVerified(Long applicationId, String businessType) {
        if (applicationId == null) {
            throw new BizException(400, "申请主单 ID（applicationId）不能为空，请先创建业务申请并上传材料");
        }

        RequiredSet rules = RequiredSet.of(businessType);
        if (rules.isEmpty()) {
            return;
        }

        List<ApplicationMaterial> materials = this.list(new LambdaQueryWrapper<ApplicationMaterial>()
                .eq(ApplicationMaterial::getApplicationId, applicationId));

        // AND 组：每条都要 VERIFIED
        for (String reqType : rules.must) {
            boolean ok = materials.stream().anyMatch(m ->
                    reqType.equalsIgnoreCase(m.getMaterialTypeCode())
                            && Integer.valueOf(1).equals(m.getRequiredFlag())
                            && "VERIFIED".equalsIgnoreCase(m.getVerifyStatus()));
            if (!ok) {
                throw reject(businessType, applicationId, reqType, null);
            }
        }

        // OR 组：任一 VERIFIED 即通过
        if (!rules.optional.isEmpty()) {
            boolean anyOk = false;
            String firstMissing = null;
            for (String t : rules.optional) {
                boolean ok = materials.stream().anyMatch(m ->
                        t.equalsIgnoreCase(m.getMaterialTypeCode())
                                && "VERIFIED".equalsIgnoreCase(m.getVerifyStatus()));
                if (ok) {
                    anyOk = true;
                    break;
                }
                if (firstMissing == null) firstMissing = t;
            }
            if (!anyOk) {
                throw reject(businessType, applicationId, firstMissing, rules.optional);
            }
        }
    }

    private BizException reject(String businessType, Long applicationId, String missing, List<String> groupOr) {
        StringBuilder sb = new StringBuilder();
        sb.append("业务[").append(businessType).append("]缺少最低必交材料且未核验通过：")
                .append(materialTypeLabel(missing));
        if (groupOr != null && !groupOr.isEmpty()) {
            sb.append("（备选项：");
            for (int i = 0; i < groupOr.size(); i++) {
                if (i > 0) sb.append(" / ");
                sb.append(materialTypeLabel(groupOr.get(i)));
            }
            sb.append("）");
        }
        sb.append("。请先通过业务申请 ID=").append(applicationId)
                .append(" 提交并完成核验。");
        return new BizException(400, sb.toString());
    }

    private static String materialTypeLabel(String code) {
        if (code == null) return "";
        switch (code) {
            case "IDENTITY_DOC": return "身份证明";
            case "HOUSEHOLD_BOOKLET": return "户口簿或户籍证明";
            case "RESIDENCE_PROOF": return "合法稳定住所证明";
            case "RELATIONSHIP_PROOF": return "亲属关系证明";
            case "BIRTH_CERT": return "出生医学证明";
            case "DEATH_CERT": return "死亡证明";
            case "RELEASE_CERT": return "释放证明";
            case "MIGRATION_CERT": return "迁移或准迁证明";
            case "EMPLOYMENT_PROOF": return "就业证明";
            case "ENROLLMENT_PROOF": return "就读证明";
            case "PHOTO": return "本人照片";
            case "SETTLEMENT_ABROAD_PROOF": return "出国定居证明";
            case "OTHER": return "其他材料";
            default: return code;
        }
    }

    /**
     * 业务必交材料模板（最小可用子集，与设计文档第 7 章对齐）。
     * <p>
     * must：AND；任一缺失即拒
     * <p>
     * optional：OR；多选一
     */
    private static final class RequiredSet {
        final List<String> must;
        final List<String> optional;

        private RequiredSet(List<String> must, List<String> optional) {
            this.must = must == null ? Collections.emptyList() : must;
            this.optional = optional == null ? Collections.emptyList() : optional;
        }

        boolean isEmpty() {
            return must.isEmpty() && optional.isEmpty();
        }

        static RequiredSet of(String businessType) {
            if (businessType == null) return new RequiredSet(Collections.emptyList(), Collections.emptyList());
            switch (businessType.toUpperCase()) {
                case "HOUSEHOLD_ESTABLISH":
                case "HOUSEHOLD_CREATE":
                    return new RequiredSet(
                            java.util.Arrays.asList("IDENTITY_DOC"),
                            java.util.Arrays.asList("HOUSEHOLD_BOOKLET", "RESIDENCE_PROOF"));
                case "PERSON_REGISTER":
                case "PERSON_CREATE":
                    return new RequiredSet(
                            java.util.Arrays.asList("IDENTITY_DOC"),
                            Collections.emptyList());
                case "MIGRATION_IN_CROSS_DISTRICT":
                case "MIGRATION_IN_EXTERNAL":
                case "MIGRATION_OUT_CROSS_DISTRICT":
                case "MIGRATION_OUT_EXTERNAL":
                case "MIGRATION_IN":
                case "MIGRATION_OUT":
                    return new RequiredSet(
                            java.util.Arrays.asList("IDENTITY_DOC", "MIGRATION_CERT"),
                            Collections.emptyList());
                default:
                    return new RequiredSet(Collections.emptyList(), Collections.emptyList());
            }
        }
    }
}
