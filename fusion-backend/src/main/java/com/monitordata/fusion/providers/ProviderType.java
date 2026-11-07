package com.monitordata.fusion.providers;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 上游类型。第一版只有 HertzBeat 与 SkyWalking。
 */
public enum ProviderType {
    HERTZBEAT("hertzbeat"),
    SKYWALKING("skywalking");

    private final String wireValue;

    ProviderType(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
