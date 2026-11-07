package com.monitordata.fusion.metrics;

/**
 * 采样范围。只有准确掌握采样比例时才给出 ratio，否则 mode=unknown。
 */
public record SampleScope(Mode mode, Double ratio, String note) {

    public enum Mode {
        FULL("full"),
        SAMPLED("sampled"),
        UNKNOWN("unknown"),
        NOT_APPLICABLE("not_applicable");

        private final String wireValue;

        Mode(String wireValue) {
            this.wireValue = wireValue;
        }

        @com.fasterxml.jackson.annotation.JsonValue
        public String wireValue() {
            return wireValue;
        }
    }

    public static SampleScope notApplicable() {
        return new SampleScope(Mode.NOT_APPLICABLE, null, null);
    }

    public static SampleScope unknown(String note) {
        return new SampleScope(Mode.UNKNOWN, null, note);
    }

    /**
     * 构造采样范围，禁止在比例未知时编造数值。
     */
    public static SampleScope sampled(Double ratio, String note) {
        if (ratio == null) {
            return unknown(note);
        }
        return new SampleScope(Mode.SAMPLED, ratio, note);
    }
}
