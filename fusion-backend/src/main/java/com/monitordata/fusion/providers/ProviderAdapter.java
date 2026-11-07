package com.monitordata.fusion.providers;

import com.monitordata.fusion.metrics.MetricQuery;
import com.monitordata.fusion.metrics.MetricSeries;
import com.monitordata.fusion.platform.SourceState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 上游适配器 SPI。所有实现必须遵守：
 * 1. 不返回伪造的 0 值，缺失用 quality=no_data/unknown 表达；
 * 2. 不把上游 ID 与名称拼接，external id 视为不透明字符串；
 * 3. 单次调用受超时预算约束，失败时返回明确的来源状态而非抛裸异常。
 */
public interface ProviderAdapter {

    /**
     * 适配器对应的上游类型。
     */
    ProviderType type();

    /**
     * 来源稳定 ID。真实实现由 provider 表提供。
     */
    String providerId();

    /**
     * 上游对象发现。key 为上游 external id，value 为可读名称。
     */
    List<DiscoveredAsset> discoverAssets();

    /**
     * 能力发现。未经阶段 0 验证的能力必须返回 UNVERIFIED。
     */
    Map<String, CapabilityState> capabilities();

    /**
     * 该适配器负责的指标 ID 集合。融合层据此路由查询，不靠名称猜测归属。
     */
    java.util.Set<String> ownedMetricIds();

    /**
     * 当前来源健康状态。
     */
    SourceState health();

    /**
     * 查询单个资产的一组指标。适配器自行持有 provider_asset_mapping，
     * 融合层不参与 upstream external id 的拼接。
     *
     * @return 该适配器负责的序列；不支持的组合返回 quality=unsupported 的序列
     */
    List<MetricSeries> queryMetrics(String assetId, MetricQuery query);

    /**
     * 按上游 external id 反查已登记资产。
     */
    Optional<String> resolveAssetId(String externalType, String externalId);

    /**
     * 该适配器是否已映射此资产。未映射时返回空序列，不臆造数据。
     */
    boolean maps(String assetId);

    /**
     * 上游对象的最小描述，用于资产映射。
     */
    record DiscoveredAsset(String externalId, String externalType, String name) {
    }
}
