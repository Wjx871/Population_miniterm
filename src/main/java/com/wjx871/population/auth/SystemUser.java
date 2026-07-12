package com.wjx871.population.auth;

import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.RoleLevel;
import java.time.LocalDateTime;
import lombok.Data;

/** Persistence model for the user, role, and department login projection. */
@Data
public class SystemUser {
    private Long userId;
    private String username;
    private String passwordHash;
    private Long roleId;
    private Long departmentId;
    private String realName;
    private String phone;
    private String status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private String roleCode;
    private String roleName;
    private RoleLevel roleLevel;
    private DataScope dataScope;
    private String roleStatus;
    private String departmentName;
    private String regionCode;
}
