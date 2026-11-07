package com.monitordata.fusion.assets;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内存资产登记表。第一版用于跑通契约与页面；持久化到 PostgreSQL 在阶段 2 接入，
 * 届时把本类替换为仓储实现，接口不变。
 */
@Repository
public class AssetRegistry {

    private final Map<String, Asset> assets = new LinkedHashMap<>();
    private final List<AssetRelation> relations = new ArrayList<>();

    public Optional<Asset> find(String assetId) {
        return Optional.ofNullable(assets.get(assetId));
    }

    public Asset require(String assetId) {
        Asset asset = assets.get(assetId);
        if (asset == null) {
            throw new com.monitordata.fusion.platform.ApiException(
                    com.monitordata.fusion.platform.ApiErrorCode.NOT_FOUND, "资产不存在或不在授权范围");
        }
        return asset;
    }

    public Collection<Asset> all() {
        return assets.values();
    }

    public List<AssetRelation> relationsOf(String assetId, Instant at) {
        return relations.stream()
                .filter(relation -> relation.fromAssetId().equals(assetId) || relation.toAssetId().equals(assetId))
                .filter(relation -> relation.effectiveAt(at))
                .toList();
    }

    void put(Asset asset) {
        assets.put(asset.id(), asset);
    }

    void relate(AssetRelation relation) {
        relations.add(relation);
    }
}
