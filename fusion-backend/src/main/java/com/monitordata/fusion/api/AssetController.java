package com.monitordata.fusion.api;

import com.monitordata.fusion.assets.Asset;
import com.monitordata.fusion.assets.AssetRegistry;
import com.monitordata.fusion.assets.AssetRelation;
import com.monitordata.fusion.assets.AssetType;
import com.monitordata.fusion.platform.ApiErrorCode;
import com.monitordata.fusion.platform.ApiException;
import com.monitordata.fusion.platform.ApiTimeParser;
import com.monitordata.fusion.platform.RequestIdFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/v1/assets 与 /api/v1/assets/{assetId}/relations。
 */
@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private static final int MAX_LIMIT = 100;

    private final AssetRegistry registry;

    public AssetController(AssetRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public AssetsResponse list(
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer limit) {

        int effectiveLimit = limit == null ? 20 : limit;
        if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT) {
            throw new ApiException(ApiErrorCode.INVALID_ARGUMENT, "limit 取值 1～" + MAX_LIMIT);
        }
        AssetType typeFilter = null;
        if (type != null && !type.isBlank()) {
            try {
                typeFilter = AssetType.fromWire(type);
            } catch (IllegalArgumentException ex) {
                throw new ApiException(ApiErrorCode.INVALID_ARGUMENT, "type 不在允许的资产类型内");
            }
        }

        final AssetType finalType = typeFilter;
        List<Asset> matched = registry.all().stream()
                .filter(asset -> siteId == null || siteId.isBlank() || asset.siteId().equals(siteId))
                .filter(asset -> environment == null || environment.isBlank()
                        || asset.environment().equals(environment))
                .filter(asset -> finalType == null || asset.type() == finalType)
                .limit(effectiveLimit)
                .toList();

        return new AssetsResponse(RequestIdFilter.currentRequestId(), matched, null);
    }

    @GetMapping("/{assetId}/relations")
    public RelationsResponse relations(
            @PathVariable String assetId,
            @RequestParam String at) {
        registry.require(assetId);
        Instant instant = ApiTimeParser.parse(at, "at");
        List<AssetRelation> relations = registry.relationsOf(assetId, instant);
        return new RelationsResponse(RequestIdFilter.currentRequestId(), relations);
    }

    public record AssetsResponse(String requestId, List<Asset> items, String nextCursor) {
    }

    public record RelationsResponse(String requestId, List<AssetRelation> items) {
    }
}
