package com.monitordata.fusion.assets;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 资产类型。service_instance 代表一个服务的单个运行副本，用于把延迟归因到具体副本。
 */
public enum AssetType {
    HOST("host"),
    CONTAINER("container"),
    SERVICE("service"),
    SERVICE_INSTANCE("service_instance"),
    DATABASE("database");

    private final String wireValue;

    AssetType(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    public static AssetType fromWire(String value) {
        for (AssetType type : values()) {
            if (type.wireValue.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unknown asset type: " + value);
    }
}
