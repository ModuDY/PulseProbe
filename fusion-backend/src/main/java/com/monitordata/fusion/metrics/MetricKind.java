package com.monitordata.fusion.metrics;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 指标类型。counter 需要按窗口差分，derived 由口径计算得出。
 */
public enum MetricKind {
    GAUGE("gauge"),
    COUNTER("counter"),
    DERIVED("derived");

    private final String wireValue;

    MetricKind(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
