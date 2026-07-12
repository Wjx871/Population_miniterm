package com.wjx871.population.security;

import com.wjx871.population.audit.OperationLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    private final SecurityResponseWriter responseWriter;
    private final OperationLogService operationLogService;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        AuthenticatedUser user = CurrentUserContext.getUserOrNull();
        operationLogService.record(user == null ? null : user.userId(), "ACCESS_DENIED", "FAILED",
                "权限不足", request);
        responseWriter.write(response, HttpServletResponse.SC_FORBIDDEN, "当前账号无权执行该操作");
    }
}
