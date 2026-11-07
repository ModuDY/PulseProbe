package com.monitordata.fusion.providers;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 能力状态。未经验证的能力必须是 UNVERIFIED，不能当作支持。
 */
public enum CapabilityState {
    SUPPORTED("supported"),
    UNSUPPORTED("unsupported"),
    UNVERIFIED("unverified");

    private final String wireValue;

    CapabilityState(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
