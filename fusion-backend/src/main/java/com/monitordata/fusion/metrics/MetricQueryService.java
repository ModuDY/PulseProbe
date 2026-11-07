package com.monitordata.fusion.metrics;

import com.monitordata.fusion.assets.Asset;
import com.monitordata.fusion.assets.AssetRegistry;
import com.monitordata.fusion.platform.RequestIdFilter;
import com.monitordata.fusion.platform.SourceState;
import com.monitordata.fusion.providers.ProviderAdapter;
import com.monitordata.fusion.providers.ProviderRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 融合指标查询。按指标归属路由到唯一适配器，聚合来源状态并显式标记部分失败。
 * 任何缺失都用 quality 表达，不填 0 值。
 */
@Service
public class MetricQueryService {

    private final ProviderRegistry providers;
    private final AssetRegistry assets;

    public MetricQueryService(ProviderRegistry providers, AssetRegistry assets) {
        this.providers = providers;
        this.assets = assets;
    }

    public MetricQueryResponse query(MetricQuery query) {
        // 资产必须存在且在授权范围，否则 404；避免用未知 ID 触发上游查询
        for (String assetId : query.assetIds()) {
            assets.require(assetId);
        }

        List<MetricSeries> series = new ArrayList<>();
        Set<SourceState> sources = new LinkedHashSet<>();
        boolean partial = false;

        for (String assetId : query.assetIds()) {
            Asset asset = assets.require(assetId);
            for (String metricId : query.metricIds()) {
                Optional<Map.Entry<String, ProviderAdapter>> owner = providers.ownerOf(metricId);
                if (owner.isEmpty() || !owner.get().getValue().maps(assetId)) {
                    // 无来源声明该指标，或该来源未映射此资产：能力缺失，不是异常数据
                    series.add(unsupported(assetId, metricId));
                    partial = true;
                    continue;
                }
                ProviderAdapter adapter = owner.get().getValue();
                MetricQuery single = new MetricQuery(List.of(assetId), List.of(metricId),
                        query.range(), query.stepSeconds(), query.maxPoints());
                List<MetricSeries> produced = adapter.queryMetrics(assetId, single);
                if (produced.isEmpty()) {
                    series.add(unsupported(assetId, metricId));
                    partial = true;
                    continue;
                }
                for (MetricSeries metricSeries : produced) {
                    series.add(metricSeries);
                    if (metricSeries.quality() != Quality.VALID) {
                        partial = true;
                    }
                }
                sources.add(adapter.health());
            }
        }

        int requested = query.combinationsRequested();
        boolean truncated = series.size() > MetricQuery.MAX_COMBINATIONS;
        List<MetricSeries> bounded = truncated
                ? List.copyOf(series.subList(0, MetricQuery.MAX_COMBINATIONS))
                : List.copyOf(series);
        if (sources.isEmpty()) {
            sources.addAll(providers.healthOfAll());
        }

        return new MetricQueryResponse(
                RequestIdFilter.currentRequestId(),
                query.range(),
                partial || truncated,
                List.copyOf(sources),
                bounded,
                requested,
                truncated);
    }

    private static MetricSeries unsupported(String assetId, String metricId) {
        MetricKind kind = MetricCatalog.exists(metricId) ? MetricCatalog.require(metricId).kind() : MetricKind.GAUGE;
        String unit = MetricCatalog.exists(metricId) ? MetricCatalog.require(metricId).unit() : "unknown";
        return new MetricSeries(assetId, metricId, null, unit, kind,
                null, Quality.UNSUPPORTED, SampleScope.notApplicable(), List.of());
    }
}
