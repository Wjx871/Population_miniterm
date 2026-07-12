package com.wjx871.population.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Accessor for the authenticated user without leaking SecurityContext details. */
public final class CurrentUserContext {

    private CurrentUserContext() {
    }

    public static AuthenticatedUser requireUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new IllegalStateException("当前请求没有已认证用户");
        }
        return user;
    }

    public static AuthenticatedUser getUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                ? user : null;
    }
}
