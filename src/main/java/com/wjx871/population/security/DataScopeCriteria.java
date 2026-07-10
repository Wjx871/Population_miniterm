package com.wjx871.population.security;

/** Normalized query criteria used by XML mappers for scoped reads. */
public record DataScopeCriteria(
        DataScope dataScope,
        Long userId,
        Long departmentId,
        String regionCode
) {
    public static DataScopeCriteria current() {
        AuthenticatedUser user = CurrentUserContext.requireUser();
        return new DataScopeCriteria(user.dataScope(), user.userId(), user.departmentId(), user.regionCode());
    }
}
