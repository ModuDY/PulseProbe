package com.monitordata.fusion.platform;

import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Instant;

/**
 * 来源健康状态。state 区分 ok/stale/unavailable/unsupported，避免把未知显示为正常。
 */
public record SourceState(
        String providerId,
        State state,
        Instant sourceCollectedAt,
        Long ageSeconds,
        String code,
        String message) {

    public enum State {
        OK("ok"),
        STALE("stale"),
        UNAVAILABLE("unavailable"),
        UNSUPPORTED("unsupported");

        private final String wireValue;

        State(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }
    }

    public static SourceState ok(String providerId, Instant collectedAt) {
        return new SourceState(providerId, State.OK, collectedAt, ageOf(collectedAt), null, null);
    }

    public static SourceState unavailable(String providerId, String code, String message) {
        return new SourceState(providerId, State.UNAVAILABLE, null, null, code, message);
    }

    public static SourceState unsupported(String providerId, String code, String message) {
        return new SourceState(providerId, State.UNSUPPORTED, null, null, code, message);
    }

    private static Long ageOf(Instant collectedAt) {
        return collectedAt == null ? null : Math.max(0, Instant.now().getEpochSecond() - collectedAt.getEpochSecond());
    }
}
