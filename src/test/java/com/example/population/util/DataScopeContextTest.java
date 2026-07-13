package com.example.population.util;

import com.example.population.dto.DataScopeQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0-1 DataScopeQuery.fromCurrentContext() 单元测试。
 * <p>
 * 仅覆盖 ThreadLocal 上下文到过滤条件的转换，不涉及 Service 层实际 SQL。
 */
class DataScopeContextTest {

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("无上下文（未登录 / 系统调用）→ 全 null 的 ALL 过滤，不抛异常")
    void noContext_allScope() {
        DataScopeContext.clear();
        DataScopeQuery q = DataScopeQuery.fromCurrentContext();
        assertThat(q).isNotNull();
        assertThat(q.getDepartmentId()).isNull();
        assertThat(q.getRegionCode()).isNull();
        assertThat(q.getVisibleRegionCodes()).isNull();
        assertThat(q.getSubmitUserId()).isNull();
        assertThat(q.isActive()).isFalse();
    }

    @Test
    @DisplayName("DEPARTMENT 范围 + 有 departmentId → filter 中 departmentId = 当前部门")
    void departmentScope() {
        DataScopeContext.set(DataScopeContext.builder()
                .dataScopeCode("DEPARTMENT")
                .userId(1L)
                .departmentId(10L)
                .build());
        DataScopeQuery q = DataScopeQuery.fromCurrentContext();
        assertThat(q.getDepartmentId()).isEqualTo(10L);
        assertThat(q.getRegionCode()).isNull();
        assertThat(q.isActive()).isTrue();
    }

    @Test
    @DisplayName("REGION 范围 + 有 regionCode → filter 中 regionCode / visibleRegionCodes 都填充")
    void regionScope() {
        Set<String> visible = new HashSet<>(Set.of("110101", "110102", "11010101"));
        DataScopeContext.set(DataScopeContext.builder()
                .dataScopeCode("REGION")
                .userId(1L)
                .departmentId(5L)
                .departmentRegionCode("110101")
                .visibleRegionCodes(visible)
                .build());
        DataScopeQuery q = DataScopeQuery.fromCurrentContext();
        assertThat(q.getVisibleRegionCodes()).containsExactlyInAnyOrder("110101", "110102", "11010101");
        assertThat(q.getRegionCode()).isNull(); // 有可见集合时不重复设精确匹配
        assertThat(q.isActive()).isTrue();
    }

    @Test
    @DisplayName("SELF 范围 → filter 中 submitUserId = 当前 userId")
    void selfScope() {
        DataScopeContext.set(DataScopeContext.builder()
                .dataScopeCode("SELF")
                .userId(42L)
                .build());
        DataScopeQuery q = DataScopeQuery.fromCurrentContext();
        assertThat(q.getSubmitUserId()).isEqualTo(42L);
        assertThat(q.isActive()).isTrue();
    }

    @Test
    @DisplayName("ALL 范围 → filter 全部 null，isActive=false")
    void allScope() {
        DataScopeContext.set(DataScopeContext.builder()
                .dataScopeCode("ALL")
                .userId(1L)
                .departmentId(99L)
                .build());
        DataScopeQuery q = DataScopeQuery.fromCurrentContext();
        assertThat(q.getDepartmentId()).isNull();
        assertThat(q.getSubmitUserId()).isNull();
        assertThat(q.getRegionCode()).isNull();
        assertThat(q.isActive()).isFalse();
    }
}