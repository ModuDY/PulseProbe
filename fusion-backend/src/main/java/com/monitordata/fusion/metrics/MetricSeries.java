package com.monitordata.fusion.metrics;

import java.time.Instant;
import java.util.List;

/**
 * 归一化指标序列。单位显式给出，点数受 maxPoints 约束。
 */
public record MetricSeries(
        String assetId,
        String metricId,
        String providerId,
        String unit,
        MetricKind kind,
        Instant sourceCollectedAt,
        Quality quality,
        SampleScope sampleScope,
        List<MetricPoint> points) {
}
