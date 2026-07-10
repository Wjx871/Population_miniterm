package com.wjx871.population.auth;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMapper {
    Optional<SystemUser> selectByUsername(String username);

    List<String> selectPermissionCodesByRoleId(Long roleId);

    int updateLastLogin(@Param("userId") Long userId, @Param("ipAddress") String ipAddress);
}
