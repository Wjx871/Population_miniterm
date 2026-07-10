package com.wjx871.population.auth;

import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.RoleLevel;
import java.util.List;

public record UserVO(
        Long userId,
        String username,
        String realName,
        String roleCode,
        String roleName,
        RoleLevel roleLevel,
        DataScope dataScope,
        Long departmentId,
        String departmentName,
        String regionCode,
        List<String> permissions
) {
    public static UserVO from(AuthenticatedUser user) {
        return new UserVO(user.userId(), user.username(), user.realName(), user.roleCode(), user.roleName(),
                user.roleLevel(), user.dataScope(), user.departmentId(), user.departmentName(),
                user.regionCode(), user.permissions());
    }
}
