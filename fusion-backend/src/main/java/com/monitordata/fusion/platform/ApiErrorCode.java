package com.monitordata.fusion.platform;

import org.springframework.http.HttpStatus;

/**
 * 固定机器码。前端按码分支，不解析文案。与设计文档 §8.1 码表保持一致。
 */
public enum ApiErrorCode {

    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST),
    RANGE_INVALID(HttpStatus.BAD_REQUEST),
    RANGE_TOO_DENSE(HttpStatus.BAD_REQUEST),
    COMBINATION_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_CAPABILITY(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    STATE_CONFLICT(HttpStatus.CONFLICT),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    SOURCE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ApiErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
