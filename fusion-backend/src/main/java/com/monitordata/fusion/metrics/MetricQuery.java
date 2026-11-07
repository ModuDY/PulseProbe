package com.monitordata.fusion.metrics;

import com.monitordata.fusion.platform.ApiErrorCode;
import com.monitordata.fusion.platform.ApiException;
import com.monitordata.fusion.platform.TimeRange;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 有界指标查询。组合数上限 10×20=200，与响应 series 上限一致；
 * 点数超限返回 RANGE_TOO_DENSE，不做隐式降采样。
 */
public record MetricQuery(
        @NotEmpty List<String> assetIds,
        @NotEmpty List<String> metricIds,
        TimeRange range,
        @Min(1) @Max(86400) int stepSeconds,
        @Min(1) @Max(1000) Integer maxPoints) {

    public static final int MAX_ASSETS = 10;
    public static final int MAX_METRICS = 20;
    public static final int MAX_COMBINATIONS = 200;
    public static final int DEFAULT_MAX_POINTS = 1000;

    public MetricQuery {
        if (assetIds == null || assetIds.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_ARGUMENT, "assetIds 不能为空");
        }
        if (metricIds == null || metricIds.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_ARGUMENT, "metricIds 不能为空");
        }
        if (assetIds.size() > MAX_ASSETS) {
            throw new ApiException(ApiErrorCode.COMBINATION_LIMIT_EXCEEDED,
                    "assetIds 最多 " + MAX_ASSETS + " 个");
        }
        if (metricIds.size() > MAX_METRICS) {
            throw new ApiException(ApiErrorCode.COMBINATION_LIMIT_EXCEEDED,
                    "metricIds 最多 " + MAX_METRICS + " 个");
        }
        if ((long) assetIds.size() * metricIds.size() > MAX_COMBINATIONS) {
            throw new ApiException(ApiErrorCode.COMBINATION_LIMIT_EXCEEDED,
                    "资产与指标组合数超过 " + MAX_COMBINATIONS);
        }
        if (range == null) {
            throw new ApiException(ApiErrorCode.RANGE_INVALID, "缺少 range");
        }
        if (stepSeconds < 1) {
            throw new ApiException(ApiErrorCode.INVALID_ARGUMENT, "stepSeconds 必须大于 0");
        }
        int limit = maxPoints == null ? DEFAULT_MAX_POINTS : maxPoints;
        long requestedPoints = (range.seconds() + stepSeconds - 1) / stepSeconds;
        if (requestedPoints > limit) {
            throw new ApiException(ApiErrorCode.RANGE_TOO_DENSE,
                    "按 stepSeconds=" + stepSeconds + " 将产生 " + requestedPoints + " 个点，超过上限 " + limit);
        }
        if (maxPoints == null) {
            maxPoints = DEFAULT_MAX_POINTS;
        }
    }

    public int effectiveMaxPoints() {
        return maxPoints == null ? DEFAULT_MAX_POINTS : maxPoints;
    }

    public int combinationsRequested() {
        return assetIds.size() * metricIds.size();
    }
}
