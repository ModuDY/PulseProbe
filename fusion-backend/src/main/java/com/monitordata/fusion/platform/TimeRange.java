package com.monitordata.fusion.platform;

import java.time.Duration;
import java.time.Instant;

/**
 * 闭开区间 [start, end)。内部统一 UTC，输出时按需转时区。
 */
public record TimeRange(Instant start, Instant end) {

    public TimeRange {
        if (start == null || end == null) {
            throw new ApiException(ApiErrorCode.RANGE_INVALID, "时间区间缺少 start 或 end");
        }
        if (!start.isBefore(end)) {
            throw new ApiException(ApiErrorCode.RANGE_INVALID, "start 必须早于 end");
        }
    }

    public long seconds() {
        return Duration.between(start, end).getSeconds();
    }
}
