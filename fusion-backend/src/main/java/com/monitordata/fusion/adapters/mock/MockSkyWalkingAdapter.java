package com.monitordata.fusion.adapters.mock;

import com.monitordata.fusion.assets.AssetRegistry;
import com.monitordata.fusion.assets.AssetSeeder;
import com.monitordata.fusion.metrics.MetricCatalog;
import com.monitordata.fusion.metrics.MetricKind;
import com.monitordata.fusion.metrics.MetricPoint;
import com.monitordata.fusion.metrics.MetricQuery;
import com.monitordata.fusion.metrics.MetricSeries;
import com.monitordata.fusion.metrics.Quality;
import com.monitordata.fusion.metrics.SampleScope;
import com.monitordata.fusion.platform.SourceState;
import com.monitordata.fusion.providers.CapabilityState;
import com.monitordata.fusion.providers.ProviderAdapter;
import com.monitordata.fusion.providers.ProviderType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * SkyWalking 的 mock 适配器。只负责服务级指标。
 * 真实实现应替换为固定版本 OAP GraphQL 契约，查询模板在服务端固定。
 */
@Component
public class MockSkyWalkingAdapter implements ProviderAdapter {

    public static final String PROVIDER_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

    private final AssetRegistry registry;

    public MockSkyWalkingAdapter(AssetRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ProviderType type() {
        return ProviderType.SKYWALKING;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public List<DiscoveredAsset> discoverAssets() {
        return List.of(
                new DiscoveredAsset("sw-service::zing-icu-system", "service", "zing-icu-system"),
                new DiscoveredAsset("sw-instance::zing-icu-server", "service_instance", "zing-icu-server"));
    }

    @Override
    public Map<String, CapabilityState> capabilities() {
        Map<String, CapabilityState> capabilities = new HashMap<>();
        capabilities.put("metrics.query", CapabilityState.UNVERIFIED);
        capabilities.put("endpoints.list", CapabilityState.UNVERIFIED);
        capabilities.put("traces.query", CapabilityState.UNVERIFIED);
        capabilities.put("traces.samplingRatio", CapabilityState.UNVERIFIED);
        capabilities.put("jvm.metrics", CapabilityState.UNVERIFIED);
        capabilities.put("connectionPool.metrics", CapabilityState.UNSUPPORTED);
        return capabilities;
    }

    @Override
    public Set<String> ownedMetricIds() {
        return Set.of(
                MetricCatalog.SERVICE_REQUEST_RATE,
                MetricCatalog.SERVICE_ERROR_RATE,
                MetricCatalog.SERVICE_P95);
    }

    @Override
    public SourceState health() {
        return SourceState.ok(PROVIDER_ID, Instant.now().minusSeconds(3));
    }

    @Override
    public boolean maps(String assetId) {
        return registry.find(assetId)
                .map(asset -> asset.type() == com.monitordata.fusion.assets.AssetType.SERVICE
                        || asset.type() == com.monitordata.fusion.assets.AssetType.SERVICE_INSTANCE)
                .orElse(false);
    }

    @Override
    public List<MetricSeries> queryMetrics(String assetId, MetricQuery query) {
        if (registry.find(assetId).isEmpty()) {
            return List.of();
        }
        List<MetricSeries> result = new ArrayList<>();
        for (String metricId : query.metricIds()) {
            List<MetricPoint> points = switch (metricId) {
                case MetricCatalog.SERVICE_REQUEST_RATE -> SyntheticSeries.generate(
                        seed(assetId, metricId), query.range().start(), query.range().end(),
                        query.stepSeconds(), 12.0, 5.0, 0);
                case MetricCatalog.SERVICE_ERROR_RATE -> SyntheticSeries.generate(
                        seed(assetId, metricId), query.range().start(), query.range().end(),
                        query.stepSeconds(), 0.012, 0.008, 0);
                case MetricCatalog.SERVICE_P95 -> SyntheticSeries.generate(
                        seed(assetId, metricId), query.range().start(), query.range().end(),
                        query.stepSeconds(), 320.0, 180.0, 0);
                default -> List.of();
            };
            if (points.isEmpty()) {
                result.add(new MetricSeries(assetId, metricId, PROVIDER_ID, unitOrUnknown(metricId),
                        kindOrGauge(metricId), null, Quality.UNSUPPORTED, SampleScope.notApplicable(), List.of()));
            } else {
                result.add(new MetricSeries(assetId, metricId, PROVIDER_ID,
                        MetricCatalog.require(metricId).unit(), MetricCatalog.require(metricId).kind(),
                        Instant.now().minusSeconds(3),
                        // 采样比例尚未验证，按 unknown 展示，不编造比例
                        Quality.VALID, SampleScope.unknown("采样比例未验证"), points));
            }
        }
        return result;
    }

    @Override
    public Optional<String> resolveAssetId(String externalType, String externalId) {
        if (externalId == null) {
            return Optional.empty();
        }
        return switch (externalId) {
            case "sw-service::zing-icu-system" -> Optional.of(AssetSeeder.APP_SERVICE);
            case "sw-instance::zing-icu-server" -> Optional.of(AssetSeeder.APP_INSTANCE);
            default -> Optional.empty();
        };
    }

    private static long seed(String assetId, String metricId) {
        return Math.abs(assetId.hashCode() * 31L + metricId.hashCode());
    }

    private static String unitOrUnknown(String metricId) {
        return MetricCatalog.exists(metricId) ? MetricCatalog.require(metricId).unit() : "unknown";
    }

    private static MetricKind kindOrGauge(String metricId) {
        return MetricCatalog.exists(metricId) ? MetricCatalog.require(metricId).kind() : MetricKind.GAUGE;
    }
}
