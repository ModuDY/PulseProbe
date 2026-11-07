package com.monitordata.fusion.assets;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 资产关系类型。结构归属用 parentAssetId，这里只表达可随时间变化的运行期关系。
 */
public enum RelationType {
    RUNS_ON("runs_on"),
    CONNECTS_TO("connects_to"),
    CONTAINS("contains");

    private final String wireValue;

    RelationType(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
