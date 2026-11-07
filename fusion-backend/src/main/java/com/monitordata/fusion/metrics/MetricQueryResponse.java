package com.monitordata.fusion.metrics;

import com.monitordata.fusion.platform.SourceState;
import com.monitordata.fusion.platform.TimeRange;

import java.util.List;

/**
 * 指标查询响应。combinationsRequested 与 truncated 让截断可见，避免前端误以为覆盖完整。
 */
public record MetricQueryResponse(
        String requestId,
        TimeRange range,
        boolean partial,
        List<SourceState> sources,
        List<MetricSeries> series,
        int combinationsRequested,
        boolean truncated) {
}
