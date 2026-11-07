package com.monitordata.fusion.platform;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * 解析 RFC 3339 时间。要求带偏移量，避免把无时区的本地时间当成 UTC。
 */
public final class ApiTimeParser {

    private ApiTimeParser() {
    }

    public static Instant parse(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ApiErrorCode.RANGE_INVALID, fieldName + " 不能为空");
        }
        String text = value.trim();
        // 只有日期或缺少偏移量的写法一律拒绝，防止时区歧义
        boolean hasOffset = text.endsWith("Z") || text.matches(".*[+-]\\d{2}:?\\d{2}$");
        if (!hasOffset) {
            throw new ApiException(ApiErrorCode.RANGE_INVALID, fieldName + " 必须包含时区偏移");
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ex) {
            throw new ApiException(ApiErrorCode.RANGE_INVALID, fieldName + " 不是合法的 RFC 3339 时间");
        }
    }
}
