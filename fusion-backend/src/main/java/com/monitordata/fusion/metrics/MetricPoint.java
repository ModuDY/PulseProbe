package com.monitordata.fusion.metrics;

import java.time.Instant;

/**
 * 单个数据点。value 可空，quality 说明该点是否可信。
 */
public record MetricPoint(Instant time, Double value, Quality quality) {
}
