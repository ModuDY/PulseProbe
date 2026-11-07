package com.monitordata.fusion.assets;

import java.time.Instant;

/**
 * 带有效时间的资产关系。validTo 为空表示仍然有效。
 */
public record AssetRelation(
        String fromAssetId,
        String toAssetId,
        RelationType type,
        Instant validFrom,
        Instant validTo) {

    public boolean effectiveAt(Instant instant) {
        boolean started = !validFrom.isAfter(instant);
        boolean notEnded = validTo == null || validTo.isAfter(instant);
        return started && notEnded;
    }
}
