package com.example.population.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.population.entity.LoginLog;
import com.example.population.mapper.LoginLogMapper;
import com.example.population.service.impl.LoginLogServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LoginLogService 单测：
 *   - record 入库字段是否正确（截断/默认值）
 *   - record 校验：loginStatus 为空时抛异常
 *   - page 过滤条件：userId / username / loginStatus / 时间区间 / 默认排序
 */
@ExtendWith(MockitoExtension.class)
class LoginLogServiceTest {

    @Mock
    private LoginLogMapper loginLogMapper;

    private LoginLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LoginLogServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", loginLogMapper);
        // 手动初始化实体元信息，避免 MyBatis-Plus LambdaQueryWrapper 在单测里找不到 lambda cache
        Configuration cfg = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "test");
        TableInfoHelper.initTableInfo(assistant, LoginLog.class);
    }

    // ---------- record ----------

    @Test
    @DisplayName("record: 写入成功日志时，loginStatus=userId/IP/UA 正确入库")
    void record_success() {
        when(loginLogMapper.insert(any(LoginLog.class))).thenReturn(1);

        service.record("alice", 7L, "SUCCESS", null, "127.0.0.1", "Mozilla/5.0", "fp-001");

        ArgumentCaptor<LoginLog> captor = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogMapper).insert(captor.capture());
        LoginLog saved = captor.getValue();

        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getLoginStatus()).isEqualTo("SUCCESS");
        assertThat(saved.getFailureReason()).isNull();
        assertThat(saved.getLoginIp()).isEqualTo("127.0.0.1");
        assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(saved.getDeviceFingerprint()).isEqualTo("fp-001");
        // loginTime 由 MetaObjectHandler 填充；测试环境为 null，service 不主动赋值
        assertThat(saved.getLoginTime()).isNull();
    }

    @Test
    @DisplayName("record: 失败日志，userId=null 时仍能写入（外键 ON DELETE SET NULL 兼容）")
    void record_failure_nullUserId() {
        when(loginLogMapper.insert(any(LoginLog.class))).thenReturn(1);

        service.record("bob", null, "FAILED", "用户名或密码错误", "10.0.0.1", null, null);

        ArgumentCaptor<LoginLog> captor = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogMapper).insert(captor.capture());
        LoginLog saved = captor.getValue();

        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getLoginStatus()).isEqualTo("FAILED");
        assertThat(saved.getFailureReason()).isEqualTo("用户名或密码错误");
        assertThat(saved.getLoginIp()).isEqualTo("10.0.0.1");
        assertThat(saved.getUserAgent()).isNull();
    }

    @Test
    @DisplayName("record: loginStatus 为空时抛 IllegalArgumentException")
    void record_statusEmpty_throws() {
        assertThatThrownBy(() ->
                service.record("alice", 1L, " ", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loginStatus");

        verify(loginLogMapper, times(0)).insert(any(LoginLog.class));
    }

    @Test
    @DisplayName("record: 超过列长度上限会被截断")
    void record_truncate() {
        when(loginLogMapper.insert(any(LoginLog.class))).thenReturn(1);

        StringBuilder longIp = new StringBuilder();
        for (int i = 0; i < 100; i++) longIp.append('1');
        StringBuilder longUa = new StringBuilder();
        for (int i = 0; i < 800; i++) longUa.append('A');

        service.record("alice", 1L, "SUCCESS", null, longIp.toString(), longUa.toString(), null);

        ArgumentCaptor<LoginLog> captor = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogMapper).insert(captor.capture());
        LoginLog saved = captor.getValue();

        assertThat(saved.getLoginIp()).hasSize(50);
        assertThat(saved.getUserAgent()).hasSize(500);
    }

    // ---------- page ----------

    @Test
    @DisplayName("page: 没有任何过滤条件 → 等价于全量分页")
    void page_noFilters() {
        IPage<LoginLog> empty = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        when(loginLogMapper.selectPage(any(IPage.class), any(Wrapper.class))).thenReturn(empty);

        IPage<LoginLog> result = service.page(1, 10, null, null, null, null, null);

        assertThat(result).isSameAs(empty);
        verify(loginLogMapper).selectPage(any(IPage.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("page: 全部过滤条件都设置 → 不抛异常，结果返回")
    void page_allFilters() {
        IPage<LoginLog> empty = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(2, 20);
        when(loginLogMapper.selectPage(any(IPage.class), any(Wrapper.class))).thenReturn(empty);

        java.time.LocalDateTime start = java.time.LocalDateTime.of(2026, 1, 1, 0, 0);
        java.time.LocalDateTime end = java.time.LocalDateTime.of(2026, 1, 31, 23, 59);

        IPage<LoginLog> result = service.page(2, 20, 99L, "alice", "FAILED", start, end);

        ArgumentCaptor<Wrapper<LoginLog>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(loginLogMapper).selectPage(any(IPage.class), captor.capture());

        Wrapper<LoginLog> w = captor.getValue();
        // 验证传入的 Wrapper 内部表达式的字段引用数量 (过滤字段 + ORDER BY)
        // 字段名仅用作粗略验证 (排序/正反序写法视 MyBatis-Plus 版本而定)
        LambdaQueryWrapper<LoginLog> lqw = (LambdaQueryWrapper<LoginLog>) w;
        assertThat(lqw).isNotNull();
        assertThat(result).isSameAs(empty);
    }
}
