package com.wjx871.population.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PaginationParameterInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        validate(request, "page", 0);
        validate(request, "size", 1);
        return true;
    }

    private void validate(HttpServletRequest request, String name, int minimum) {
        String raw = request.getParameter(name);
        if (raw == null) return;
        try {
            if (Integer.parseInt(raw) < minimum) {
                throw invalid(name, minimum);
            }
        } catch (NumberFormatException exception) {
            throw invalid(name, minimum);
        }
    }

    private BusinessException invalid(String name, int minimum) {
        return new BusinessException(HttpStatus.BAD_REQUEST, name + " 必须是大于等于 " + minimum + " 的整数");
    }
}
