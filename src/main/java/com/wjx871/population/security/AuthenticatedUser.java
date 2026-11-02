package com.wjx871.population.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Immutable authenticated user used by Spring Security and data-scope queries. */
public record AuthenticatedUser(
        Long userId,
        String username,
        String passwordHash,
        String realName,
        String userStatus,
        Long roleId,
        String roleCode,
        String roleName,
        RoleLevel roleLevel,
        DataScope dataScope,
        String roleStatus,
        Long departmentId,
        String departmentName,
        String regionCode,
        List<String> permissions,
        List<String> regionCodes
) implements UserDetails {

    public AuthenticatedUser(Long userId, String username, String passwordHash, String realName, String userStatus,
            Long roleId, String roleCode, String roleName, RoleLevel roleLevel, DataScope dataScope,
            String roleStatus, Long departmentId, String departmentName, String regionCode,
            List<String> permissions) {
        this(userId, username, passwordHash, realName, userStatus, roleId, roleCode, roleName, roleLevel,
                dataScope, roleStatus, departmentId, departmentName, regionCode, permissions, List.of());
    }

    public AuthenticatedUser {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        regionCodes = regionCodes == null ? List.of() : List.copyOf(regionCodes);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream().map(SimpleGrantedAuthority::new).toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return "ENABLED".equals(userStatus) && "ENABLED".equals(roleStatus);
    }
}
