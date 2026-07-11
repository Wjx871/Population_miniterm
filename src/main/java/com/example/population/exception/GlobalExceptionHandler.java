package com.example.population.exception;

import com.example.population.dto.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.error(HttpStatus.BAD_REQUEST.value(), msg);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.error(HttpStatus.BAD_REQUEST.value(), msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException ex) {
        return Result.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArg(IllegalArgumentException ex) {
        return Result.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    }

    /**
     * 业务异常：子类包含 NotFoundException / DuplicateException / 户籍注销前置冲突等。
     * 用 BizException.code 作为 HTTP 响应码（默认 400，子类可重写）。
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException ex) {
        log.warn("业务异常 [{}] {}", ex.getCode(), ex.getMessage());
        return Result.error(ex.getCode(), ex.getMessage());
    }

    /**
     * DB 唯一键冲突。翻译为 DuplicateException，避免泄露 DB 内部信息。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicate(DuplicateKeyException ex) {
        String msg = ex.getMostSpecificCause() == null
                ? "数据重复，违反唯一约束"
                : "数据重复: " + ex.getMostSpecificCause().getMessage();
        log.warn("唯一键冲突: {}", msg);
        return Result.error(HttpStatus.CONFLICT.value(), msg);
    }

    /**
     * 数据完整性违规（外键、CHECK、NOT NULL 等）。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleIntegrity(DataIntegrityViolationException ex) {
        String msg = ex.getMostSpecificCause() == null
                ? "数据完整性校验失败"
                : ex.getMostSpecificCause().getMessage();
        log.warn("数据完整性违规: {}", msg);
        return Result.error(HttpStatus.BAD_REQUEST.value(), msg);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<Void>> handleRuntime(RuntimeException ex) {
        log.error("运行时异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ex.getMessage() == null ? "服务器内部错误" : ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleAny(Exception ex) {
        log.error("未知异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error("服务器内部错误"));
    }
}
