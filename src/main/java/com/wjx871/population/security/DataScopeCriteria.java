package com.wjx871.population.security;

import java.util.List;
import com.wjx871.population.common.BusinessException;
import org.springframework.http.HttpStatus;

/** Normalized query criteria used by XML mappers for scoped reads. */
public record DataScopeCriteria(
        DataScope dataScope,
        Long userId,
        Long departmentId,
        String regionCode,
        List<String> regionCodes
) {
    public DataScopeCriteria(DataScope dataScope, Long userId, Long departmentId, String regionCode) {
        this(dataScope, userId, departmentId, regionCode,
                regionCode == null ? List.of() : List.of(regionCode));
    }

    public DataScopeCriteria {
        regionCodes = regionCodes == null ? List.of() : List.copyOf(regionCodes);
    }

    public static DataScopeCriteria current() {
        AuthenticatedUser user = CurrentUserContext.requireUser();
        if (user.dataScope() == DataScope.REGION && user.regionCodes().isEmpty()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "用户区划数据范围无法验证");
        }
        return new DataScopeCriteria(user.dataScope(), user.userId(), user.departmentId(), user.regionCode(),
                user.regionCodes());
    }
}
