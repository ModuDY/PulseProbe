package com.monitordata.fusion.assets;

import java.util.Map;

/**
 * 资产。parentAssetId 仅表达稳定结构层级（实例属于服务、容器属于主机），
 * 运行期依赖走 asset_relation 并带有效时间。
 */
public record Asset(
        String id,
        String siteId,
        String environment,
        AssetType type,
        String parentAssetId,
        String name,
        Map<String, String> labels) {
}
