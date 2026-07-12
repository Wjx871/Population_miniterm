package com.wjx871.population.common;

import com.wjx871.population.audit.OperationLogService;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.CurrentUserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Log4j2
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final OperationLogService operationLogService;

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        return response(exception.getStatus(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception,
                                                        HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if ("/api/auth/login".equals(request.getRequestURI())) {
            operationLogService.record(null, "LOGIN_FAILED", "FAILED", message, request);
        }
        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateKeyException exception) {
        return response(HttpStatus.CONFLICT, "数据已存在，请勿重复提交");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception,
                                                         HttpServletRequest request) {
        AuthenticatedUser user = CurrentUserContext.getUserOrNull();
        operationLogService.record(user == null ? null : user.userId(), "ACCESS_DENIED", "FAILED",
                "权限不足", request);
        return response(HttpStatus.FORBIDDEN, "当前账号无权执行该操作");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException exception) {
        return response(HttpStatus.valueOf(exception.getStatusCode().value()), exception.getReason());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "请求方法不受支持");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST, "请求内容格式或枚举值不正确");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "上传文件超过允许的最大大小");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled request exception", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "服务器处理请求失败");
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.fail(status.value(), message));
    }
}
