package com.monitordata.fusion.platform;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * 统一错误响应：只输出机器码与脱敏说明，不返回堆栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        ErrorResponse body = new ErrorResponse(
                RequestIdFilter.currentRequestId(), ex.code().name(), ex.getMessage(), List.of());
        return ResponseEntity.status(ex.code().status()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(GlobalExceptionHandler::describe)
                .orElse("请求参数不合法");
        ErrorResponse body = new ErrorResponse(
                RequestIdFilter.currentRequestId(), ApiErrorCode.INVALID_ARGUMENT.name(), detail, List.of());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * 请求体反序列化失败。record 构造器里的校验异常会被 Jackson 包装，
     * 这里沿 cause 链取回原始机器码，避免把 400 类错误降级成 500。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof ApiException apiException) {
                return handleApi(apiException);
            }
            cause = cause.getCause();
        }
        ErrorResponse body = new ErrorResponse(
                RequestIdFilter.currentRequestId(), ApiErrorCode.INVALID_ARGUMENT.name(),
                "请求体格式不合法", List.of());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        ErrorResponse body = new ErrorResponse(
                RequestIdFilter.currentRequestId(), ApiErrorCode.INTERNAL_ERROR.name(), "服务内部错误", List.of());
        return ResponseEntity.status(ApiErrorCode.INTERNAL_ERROR.status()).body(body);
    }

    private static String describe(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
