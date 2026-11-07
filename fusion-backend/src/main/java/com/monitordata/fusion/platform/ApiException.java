package com.monitordata.fusion.platform;

/**
 * 带机器码的业务异常。message 必须是脱敏后的说明，不包含凭证、SQL 参数或请求正文。
 */
public class ApiException extends RuntimeException {

    private final ApiErrorCode code;

    public ApiException(ApiErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiErrorCode code() {
        return code;
    }
}
