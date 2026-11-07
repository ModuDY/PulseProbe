package com.monitordata.fusion.metrics;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 数据质量。缺失与未知必须显式表达，不能替换为 0。
 */
public enum Quality {
    VALID("valid"),
    PARTIAL("partial"),
    NO_DATA("no_data"),
    RESET("reset"),
    STALE("stale"),
    UNKNOWN("unknown"),
    UNSUPPORTED("unsupported");

    private final String wireValue;

    Quality(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
