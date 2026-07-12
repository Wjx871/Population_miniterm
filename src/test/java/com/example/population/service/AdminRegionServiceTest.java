package com.example.population.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.entity.AdminRegion;
import com.example.population.mapper.AdminRegionMapper;
import com.example.population.service.impl.AdminRegionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AdminRegionServiceImpl 单测。
 *
 * 注意：MyBatis-Plus 的 ServiceImpl 把 baseMapper 放在父类，
 * Mockito @InjectMocks 不会注入该继承字段，
 * 因此用 ReflectionTestUtils.setField 手动注入到父类 baseMapper。
 */
@ExtendWith(MockitoExtension.class)
class AdminRegionServiceTest {

    @Mock
    private AdminRegionMapper baseMapper;

    private AdminRegionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminRegionServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
    }

    private AdminRegion region(String code, String cityCode) {
        AdminRegion r = new AdminRegion();
        r.setRegionCode(code);
        r.setCityCode(cityCode);
        return r;
    }

    // ---------- getCityCodeByRegion ----------

    @Test
    @DisplayName("getCityCodeByRegion: 找到区划,返回其 cityCode")
    void getCityCode_found() {
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(region("11010101", "110100"));
        assertEquals("110100", service.getCityCodeByRegion("11010101"));
    }

    @Test
    @DisplayName("getCityCodeByRegion: 区划不存在返回 null")
    void getCityCode_missing() {
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertNull(service.getCityCodeByRegion("xxxx"));
    }

    // ---------- isSameCity ----------

    @Test
    @DisplayName("isSameCity: a == b 直接 true(不去查 DB)")
    void sameCityEqual() {
        assertTrue(service.isSameCity("11010101", "11010101"));
    }

    @Test
    @DisplayName("isSameCity: 任一为 null/空 → false")
    void sameCityEmptyOrNull() {
        assertFalse(service.isSameCity(null, "11010101"));
        assertFalse(service.isSameCity("11010101", null));
        assertFalse(service.isSameCity("", "11010101"));
        assertFalse(service.isSameCity("11010101", "   "));
    }

    @Test
    @DisplayName("isSameCity: cityCode 相等 → true")
    void sameCityBothFound() {
        when(baseMapper.selectOne(any(Wrapper.class)))
                .thenReturn(region("11010101", "110100"))
                .thenReturn(region("11010102", "110100"));
        assertTrue(service.isSameCity("11010101", "11010102"));
    }

    @Test
    @DisplayName("isSameCity: cityCode 不等 → false")
    void sameCityDifferentCity() {
        when(baseMapper.selectOne(any(Wrapper.class)))
                .thenReturn(region("11010101", "110100"))
                .thenReturn(region("32010101", "320100"));
        assertFalse(service.isSameCity("11010101", "32010101"));
    }

    @Test
    @DisplayName("isSameCity: a 找不到 → false")
    void sameCityAMissing() {
        when(baseMapper.selectOne(any(Wrapper.class)))
                .thenReturn(null)
                .thenReturn(region("11010102", "110100"));
        assertFalse(service.isSameCity("11010101", "11010102"));
    }

    @Test
    @DisplayName("isSameCity: b 找不到 → false")
    void sameCityBMissing() {
        when(baseMapper.selectOne(any(Wrapper.class)))
                .thenReturn(region("11010101", "110100"))
                .thenReturn(null);
        assertFalse(service.isSameCity("11010101", "11010102"));
    }

    // ---------- 简单覆盖 page/listChildren ----------

    @Test
    @DisplayName("page: 调用 baseMapper.selectPage 不抛异常")
    void pageSmoke() {
        IPage<AdminRegion> stub = new Page<>(1, 10);
        when(baseMapper.selectPage(any(), any(Wrapper.class))).thenReturn(stub);
        IPage<AdminRegion> got = service.page(1, 10, "北京", "PROVINCE");
        assertEquals(1, got.getCurrent());
        assertEquals(10, got.getSize());
    }

    @Test
    @DisplayName("listChildren: 走 baseMapper.selectList,过滤 enabled=1")
    void listChildrenSmoke() {
        when(baseMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        assertEquals(0, service.listChildren("110100").size());
    }
}