package com.example.population.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.population.dto.DataScopeQuery;
import com.example.population.entity.Household;
import com.example.population.entity.Person;
import lombok.experimental.UtilityClass;

import java.util.Set;

/**
 * 数据范围过滤工具：把 {@link DataScopeQuery} 转成 MyBatis-Plus LambdaQueryWrapper 的额外条件。
 * <p>
 * 设计文档 §6：查询接口统一注入数据范围过滤，禁止前端传入任意部门或区划绕过权限。
 *
 * <p>使用示例（人口主档 + JOIN household 拿 regionCode/departmentId）：
 *
 * <pre>
 * if (ds != null) {
 *     applyPersonScope(w, ds);
 * }
 * </pre>
 */
@UtilityClass
public class DataScopeHelper {

    /**
     * SELF 范围通常作用于业务单而非人口主档；此处保留扩展
     * if (ds.getSubmitUserId() != null) { ... }
     */
    public void applyPersonScope(LambdaQueryWrapper<Person> w, DataScopeQuery ds) {
        if (ds == null || !ds.isActive()) return;

        if (ds.getDepartmentId() != null) {
            // 人口主档 → 当前户籍 → 户的 departmentId
            String sql = "SELECT 1 FROM residence_registration r "
                    + "INNER JOIN household h ON h.household_id = r.household_id "
                    + "WHERE r.person_id = person.person_id "
                    + "AND h.department_id = " + ds.getDepartmentId();
            w.and(w2 -> w2.exists(sql));
        }

        if (ds.getVisibleRegionCodes() != null && !ds.getVisibleRegionCodes().isEmpty()) {
            String inList = "'" + String.join("','", ds.getVisibleRegionCodes()) + "'";
            String sql = "SELECT 1 FROM residence_registration r "
                    + "INNER JOIN household h ON h.household_id = r.household_id "
                    + "WHERE r.person_id = person.person_id "
                    + "AND h.region_code IN (" + inList + ")";
            // 用 and(...) 包一层，避免与其他 OR 冲突
            w.and(w2 -> w2.exists(sql));
        } else if (ds.getRegionCode() != null) {
            String sql = "SELECT 1 FROM residence_registration r "
                    + "INNER JOIN household h ON h.household_id = r.household_id "
                    + "WHERE r.person_id = person.person_id "
                    + "AND h.region_code = '" + escape(ds.getRegionCode()) + "'";
            w.and(w2 -> w2.exists(sql));
        }
    }

    /**
     * 应用于 Household 的过滤。
     */
    public void applyHouseholdScope(LambdaQueryWrapper<Household> w, DataScopeQuery ds) {
        if (ds == null || !ds.isActive()) return;

        if (ds.getDepartmentId() != null) {
            w.eq(Household::getDepartmentId, ds.getDepartmentId());
        }
        if (ds.getVisibleRegionCodes() != null && !ds.getVisibleRegionCodes().isEmpty()) {
            w.in(Household::getRegionCode, ds.getVisibleRegionCodes());
        } else if (ds.getRegionCode() != null) {
            w.eq(Household::getRegionCode, ds.getRegionCode());
        }
    }

    /**
     * 应用于 BusinessApplication / MigrationIn / MigrationOut 等业务表的过滤。
     * <p>
     * 业务表普遍带 handling_department_id / submit_user_id / region_code 字段。
     * <p>
     * 通过 fn 回调让调用方传入对应表的字段表达式，wrapper 类型擦除由调用方负责。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void applyBusinessScope(LambdaQueryWrapper w, DataScopeQuery ds,
                                   java.util.function.Function<LambdaQueryWrapper, LambdaQueryWrapper> deptFn,
                                   java.util.function.Function<LambdaQueryWrapper, LambdaQueryWrapper> regionFn,
                                   java.util.function.Function<LambdaQueryWrapper, LambdaQueryWrapper> submitFn) {
        if (ds == null || !ds.isActive()) return;
        if (ds.getDepartmentId() != null && deptFn != null) {
            deptFn.apply(w);
        }
        if (ds.getRegionCode() != null && regionFn != null) {
            regionFn.apply(w);
        } else if (ds.getVisibleRegionCodes() != null && !ds.getVisibleRegionCodes().isEmpty() && regionFn != null) {
            regionFn.apply(w);
        }
        if (ds.getSubmitUserId() != null && submitFn != null) {
            submitFn.apply(w);
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
}