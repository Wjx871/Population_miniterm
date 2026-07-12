package com.example.population.exception;

import com.example.population.dto.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalExceptionHandler 单元测试。
 * <p>
 * 重点验证 P0-3 修复点：
 *   - HttpMediaTypeNotSupportedException → 415（修复前 500）
 *   - HttpRequestMethodNotSupportedException → 405
 *   - MissingServletRequestParameterException → 400
 *   - MethodArgumentTypeMismatchException → 400
 *   - BizException 的 code 透传到 HTTP 状态码
 *   - DuplicateKeyException → 409
 *   - DataIntegrityViolationException → 400
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("P0-3: HttpMediaTypeNotSupportedException → 415（修复前为 500）")
    void mediaTypeNotSupported_returns415() {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException("application/x-www-form-urlencoded");
        ResponseEntity<Result<Void>> resp = handler.handleMediaTypeNotSupported(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(415);
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException → 405")
    void methodNotSupported_returns405() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("DELETE");
        ResponseEntity<Result<Void>> resp = handler.handleMethodNotSupported(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(resp.getBody().getCode()).isEqualTo(405);
    }

    @Test
    @DisplayName("MissingServletRequestParameterException → 400")
    void missingParam_returns400() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("userId", "Long");
        ResponseEntity<Result<Void>> resp = handler.handleMissingParam(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getCode()).isEqualTo(400);
        assertThat(resp.getBody().getMessage()).contains("userId");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException → 400，且 message 包含字段名")
    void typeMismatch_returns400() throws NoSuchMethodException {
        MethodParameter param = methodParam("setUserId", Long.class);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "userId", param, new NumberFormatException("abc"));

        ResponseEntity<Result<Void>> resp = handler.handleTypeMismatch(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getMessage()).contains("userId");
    }

    @Test
    @DisplayName("BizException(404) → HTTP 404 + code=404")
    void bizException_404_returnsHttp404() {
        ResponseEntity<Result<Void>> resp = handler.handleBiz(new NotFoundException("人口不存在"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("BizException(409) → HTTP 409 + code=409")
    void bizException_409_returnsHttp409() {
        ResponseEntity<Result<Void>> resp = handler.handleBiz(
                new PersonAlreadyHasRegistrationException(7L));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("DuplicateKeyException → 409")
    void duplicateKey_returns409() {
        DuplicateKeyException ex = new DuplicateKeyException("uk_person_identity",
                new RuntimeException("Duplicate entry '110101199001011234' for key 'uk_person_identity'"));
        ResponseEntity<Result<Void>> resp = handler.handleDuplicate(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("DataIntegrityViolationException → 400")
    void dataIntegrity_returns400() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "Cannot add or update a child row", new RuntimeException("foreign key constraint fails"));
        ResponseEntity<Result<Void>> resp = handler.handleIntegrity(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("MethodArgumentNotValidException → 400，message 拼接所有字段错误")
    void methodArgumentNotValid_returns400() throws NoSuchMethodException {
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "dto");
        br.addError(new FieldError("dto", "name", "姓名不能为空"));
        br.addError(new FieldError("dto", "phone", "手机号格式错误"));
        MethodParameter param = methodParam("setName", String.class);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, br);

        Result<Void> resp = handler.handleValidation(ex);

        assertThat(resp.getCode()).isEqualTo(400);
        assertThat(resp.getMessage()).contains("姓名不能为空").contains("手机号格式错误");
    }

    @Test
    @DisplayName("兜底 Exception → 500 + 通用 message（不应泄露堆栈文本）")
    void any_returns500() {
        ResponseEntity<Result<Void>> resp = handler.handleAny(new RuntimeException("stack trace leak"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getMessage()).isEqualTo("服务器内部错误");
    }

    @Test
    @DisplayName("RuntimeException → 500 + message")
    void runtime_returns500() {
        ResponseEntity<Result<Void>> resp = handler.handleRuntime(new RuntimeException("数据库连接失败"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getMessage()).isEqualTo("数据库连接失败");
    }

    // 工具：构造一个 MethodParameter，用于满足异常构造器的需要
    private static MethodParameter methodParam(String methodName, Class<?> argType) throws NoSuchMethodException {
        Method m = Sample.class.getDeclaredMethod(methodName, argType);
        return new MethodParameter(m, 0);
    }

    static class Sample {
        public void setName(String name) {}
        public void setUserId(Long id) {}
    }
}
