package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.population.dto.SearchResultDTO;
import com.example.population.entity.Household;
import com.example.population.entity.Person;
import com.example.population.exception.BizException;
import com.example.population.service.impl.UnifiedSearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * §2.2.9 UnifiedSearchServiceImpl 单测。
 *
 * <p>覆盖关键校验和 7 个 bucket 的 fan-out：
 * <ul>
 *   <li>keyword 为空 / 超长 → 拒绝</li>
 *   <li>每个 bucket 走对应 service.page() / searchByCertNo()，limit 钳制到 HARD_LIMIT_PER_SOURCE 内</li>
 *   <li>当 total > limit 时标记 limited=true</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UnifiedSearchServiceTest {

    @Mock private PersonService personService;
    @Mock private HouseholdService householdService;
    @Mock private CertificateService certificateService;
    @Mock private FloatingPopulationService floatingPopulationService;
    @Mock private KeyPopulationService keyPopulationService;
    @Mock private MigrationInService migrationInService;
    @Mock private MigrationOutService migrationOutService;

    private UnifiedSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UnifiedSearchServiceImpl(personService, householdService,
                certificateService, floatingPopulationService, keyPopulationService,
                migrationInService, migrationOutService);
    }

    @Test
    @DisplayName("keyword 为空 → BizException")
    void blankKeyword_throws() {
        assertThatThrownBy(() -> service.unifiedSearch("", 10))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("关键字");
        assertThatThrownBy(() -> service.unifiedSearch("   ", 10))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("keyword 超长 64 → BizException（不发起任何下游查询）")
    void overlongKeyword_throws() {
        String tooLong = "x".repeat(65);
        assertThatThrownBy(() -> service.unifiedSearch(tooLong, 10))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过长");

        // 下游不应该被调用
        verify(personService, never()).findByName(any());
        verify(householdService, never()).page(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("正常 keyword → 7 个 bucket 全部 fan-out")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void normalKeyword_dispatchAllBuckets() {
        // mock 各 service 返回（用 any() matchers，避免混用 eq()/isNull() 导致 Mockito 不识别）
        Person p = new Person();
        p.setPersonId(1L);
        when(personService.findByName(anyString())).thenReturn(List.of(p));

        IPage householdPage = org.mockito.Mockito.mock(IPage.class);
        Household hh = new Household();
        when(householdPage.getTotal()).thenReturn(2L);
        when(householdPage.getRecords()).thenReturn(List.of(hh));
        when(householdService.page(anyLong(), anyLong(), any(), any(), any())).thenReturn(householdPage);

        IPage certPage = org.mockito.Mockito.mock(IPage.class);
        when(certPage.getTotal()).thenReturn(0L);
        when(certPage.getRecords()).thenReturn(Collections.emptyList());
        when(certificateService.searchByCertNo(anyString(), anyLong(), anyLong())).thenReturn(certPage);

        IPage fpPage = org.mockito.Mockito.mock(IPage.class);
        when(fpPage.getTotal()).thenReturn(0L);
        when(fpPage.getRecords()).thenReturn(Collections.emptyList());
        when(floatingPopulationService.page(anyLong(), anyLong(), any(), any(), any(), any())).thenReturn(fpPage);

        IPage keyPage = org.mockito.Mockito.mock(IPage.class);
        when(keyPage.getTotal()).thenReturn(0L);
        when(keyPage.getRecords()).thenReturn(Collections.emptyList());
        when(keyPopulationService.page(anyLong(), anyLong(), any(), any(), any(), any(), any())).thenReturn(keyPage);

        IPage miPage = org.mockito.Mockito.mock(IPage.class);
        when(miPage.getTotal()).thenReturn(0L);
        when(miPage.getRecords()).thenReturn(Collections.emptyList());
        when(migrationInService.page(anyLong(), anyLong(), any(), any(), any(), any(), any())).thenReturn(miPage);

        IPage moPage = org.mockito.Mockito.mock(IPage.class);
        when(moPage.getTotal()).thenReturn(0L);
        when(moPage.getRecords()).thenReturn(Collections.emptyList());
        when(migrationOutService.page(anyLong(), anyLong(), any(), any(), any(), any(), any())).thenReturn(moPage);

        SearchResultDTO out = service.unifiedSearch("张三", 10);

        assertThat(out).isNotNull();
        assertThat(out.getKeyword()).isEqualTo("张三");
        assertThat(out.getPersonTotal()).isEqualTo(1);
        assertThat(out.getHouseholdTotal()).isEqualTo(2);
        assertThat(out.getPersons()).hasSize(1);
        assertThat(out.getHouseholds()).hasSize(1);
        // 全部桶被调用
        verify(personService).findByName(anyString());
        verify(householdService).page(eq(1L), eq(10L), eq("张三"), any(), any());
        verify(certificateService).searchByCertNo(eq("张三"), eq(1L), eq(10L));
    }

    @Test
    @DisplayName("limitPerSource 钳制到 HARD_LIMIT_PER_SOURCE=50")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void limitClampedTo50() {
        when(personService.findByName(anyString())).thenReturn(Collections.emptyList());

        IPage emptyPage = org.mockito.Mockito.mock(IPage.class);
        when(emptyPage.getTotal()).thenReturn(0L);
        when(emptyPage.getRecords()).thenReturn(Collections.emptyList());
        when(householdService.page(eq(1L), eq(50L), any(), any(), any())).thenReturn(emptyPage);
        when(certificateService.searchByCertNo(anyString(), eq(1L), eq(50L))).thenReturn(emptyPage);
        when(floatingPopulationService.page(eq(1L), eq(50L), any(), any(), any(), any())).thenReturn(emptyPage);
        when(keyPopulationService.page(eq(1L), eq(50L), any(), any(), any(), any(), any())).thenReturn(emptyPage);
        when(migrationInService.page(eq(1L), eq(50L), any(), any(), any(), any(), any())).thenReturn(emptyPage);
        when(migrationOutService.page(eq(1L), eq(50L), any(), any(), any(), any(), any())).thenReturn(emptyPage);

        service.unifiedSearch("a", 999);

        verify(householdService).page(eq(1L), eq(50L), eq("a"), any(), any());
        verify(certificateService).searchByCertNo(eq("a"), eq(1L), eq(50L));
    }

    @Test
    @DisplayName("total > size 时 limited=true")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void totalExceedsLimit_marksLimited() {
        when(personService.findByName(anyString())).thenReturn(Collections.emptyList());

        IPage hhPage = org.mockito.Mockito.mock(IPage.class);
        when(hhPage.getTotal()).thenReturn(20L);
        when(hhPage.getRecords()).thenReturn(List.of(new Household(), new Household()));
        when(householdService.page(eq(1L), eq(5L), any(), any(), any())).thenReturn(hhPage);

        IPage emptyPage = org.mockito.Mockito.mock(IPage.class);
        when(emptyPage.getTotal()).thenReturn(0L);
        when(emptyPage.getRecords()).thenReturn(Collections.emptyList());
        when(certificateService.searchByCertNo(anyString(), anyLong(), anyLong())).thenReturn(emptyPage);
        when(floatingPopulationService.page(eq(1L), eq(5L), any(), any(), any(), any())).thenReturn(emptyPage);
        when(keyPopulationService.page(eq(1L), eq(5L), any(), any(), any(), any(), any())).thenReturn(emptyPage);
        when(migrationInService.page(eq(1L), eq(5L), any(), any(), any(), any(), any())).thenReturn(emptyPage);
        when(migrationOutService.page(eq(1L), eq(5L), any(), any(), any(), any(), any())).thenReturn(emptyPage);

        SearchResultDTO out = service.unifiedSearch("h", 5);
        assertThat(out.isLimited()).isTrue();
        assertThat(out.getHouseholdTotal()).isEqualTo(20);
    }
}