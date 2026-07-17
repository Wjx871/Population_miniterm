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

    /**
     * 行政区划码按层级比较时使用的有效前缀，例如 110000 -> 11、110100 -> 1101。
     * 保持各查询入口与详情鉴权使用同一套区域包含关系。
     */
    public String regionPrefix() {
        return regionCode == null ? null : regionCode.replaceFirst("0+$", "");
    }
}
