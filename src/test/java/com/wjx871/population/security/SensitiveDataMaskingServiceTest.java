package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SensitiveDataMaskingServiceTest {
    private final SensitiveDataMaskingService service = new SensitiveDataMaskingService();
    private static final String ADDRESS = "示例市示例区测试路1号";

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addressKeepsItsExistingPermissionBehaviorWhileAlwaysVariantMasks() {
        assertThat(service.address(ADDRESS)).isEqualTo("示例市示例区（详细地址已隐藏）");
        assertThat(service.addressAlways(ADDRESS)).isEqualTo("示例市示例区（详细地址已隐藏）");

        authenticate(true);
        assertThat(service.address(ADDRESS)).isEqualTo(ADDRESS);
        assertThat(service.addressAlways(ADDRESS)).isEqualTo("示例市示例区（详细地址已隐藏）");
    }

    @Test
    void identityPhonePermitAndAuditRegexesRemainRedacted() {
        authenticate(false);
        assertThat(service.identity("110105198001010016")).isEqualTo("1101**********0016");
        assertThat(service.phone("13800138000")).isEqualTo("138****8000");
        assertThat(service.permit("1234567890123456")).isEqualTo("1234****3456");

        String input = "id=110105198001010016 phone=13800138000 Bearer abc.def-123 "
                + "password = s3cret; 密码:汉字秘钥, tail";
        assertThat(service.auditDetail(input)).isEqualTo("id=1101**********0016 phone=138****8000 Bearer *** "
                + "password=***; 密码=***, tail");
        assertThat(service.filename(input)).doesNotContain("110105198001010016", "13800138000", "abc.def-123",
                "s3cret", "汉字秘钥");

        authenticate(true);
        assertThat(service.identity("110105198001010016")).isEqualTo("110105198001010016");
        assertThat(service.phone("13800138000")).isEqualTo("13800138000");
        assertThat(service.permit("1234567890123456")).isEqualTo("1234567890123456");
        assertThat(service.auditDetail(input)).doesNotContain("110105198001010016", "13800138000", "abc.def-123",
                "s3cret", "汉字秘钥");
    }

    private void authenticate(boolean fullSensitiveAccess) {
        List<String> permissions = fullSensitiveAccess ? List.of("sensitive-data:view-full") : List.of();
        AuthenticatedUser user = new AuthenticatedUser(1L, "mask-test", "hash", "测试用户", "ENABLED", 1L,
                "TEST", "测试角色", RoleLevel.L1, DataScope.ALL, "ENABLED", 1L, "测试部门", "110000",
                permissions, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
