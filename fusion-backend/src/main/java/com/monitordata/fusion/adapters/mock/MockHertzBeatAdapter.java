package com.monitordata.fusion.adapters.mock;

import com.monitordata.fusion.assets.Asset;
import com.monitordata.fusion.assets.AssetRegistry;
import com.monitordata.fusion.assets.AssetSeeder;
import com.monitordata.fusion.assets.AssetType;
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
 * HertzBeat 的 mock 适配器。只负责主机、容器与数据库状态类指标。
 * 真实实现应替换为 1.9.x 的 /api/observability/** 查询，本类仅用于打通契约。
 */
@Component
public class MockHertzBeatAdapter implements ProviderAdapter {

    public static final String PROVIDER_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";

    private final AssetRegistry registry;

    public MockHertzBeatAdapter(AssetRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ProviderType type() {
        return ProviderType.HERTZBEAT;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public List<DiscoveredAsset> discoverAssets() {
        return List.of(
                new DiscoveredAsset("hb-host-app", "host", "app-host-01"),
                new DiscoveredAsset("hb-host-db", "host", "db-host-01"),
                new DiscoveredAsset("hb-mariadb", "mariadb", "maria-business"));
    }

    @Override
    public Map<String, CapabilityState> capabilities() {
        Map<String, CapabilityState> capabilities = new HashMap<>();
        capabilities.put("metrics.query", CapabilityState.UNVERIFIED);
        capabilities.put("metrics.history", CapabilityState.UNVERIFIED);
        capabilities.put("alerts.push", CapabilityState.UNVERIFIED);
        capabilities.put("sql.aggregation", CapabilityState.UNSUPPORTED);
        return capabilities;
    }

    @Override
    public Set<String> ownedMetricIds() {
        return Set.of(
                MetricCatalog.HOST_CPU_USAGE,
                MetricCatalog.HOST_MEM_AVAILABLE,
                MetricCatalog.HOST_SWAP_USED,
                MetricCatalog.HOST_DISK_READ_LATENCY,
                MetricCatalog.CONTAINER_CPU_CORES,
                MetricCatalog.CONTAINER_MEM_USED,
                MetricCatalog.DB_CONNECTIONS,
                MetricCatalog.DB_THREADS_RUNNING,
                MetricCatalog.DB_BUFFER_POOL_HIT,
                MetricCatalog.DB_LOCK_WAITS,
                MetricCatalog.PROBE_HTTP_DURATION);
    }

    @Override
    public SourceState health() {
        return SourceState.ok(PROVIDER_ID, Instant.now().minusSeconds(5));
    }

    @Override
    public boolean maps(String assetId) {
        return registry.find(assetId)
                .map(asset -> asset.type() == AssetType.HOST
                        || asset.type() == AssetType.CONTAINER
                        || asset.type() == AssetType.DATABASE)
                .orElse(false);
    }

    @Override
    public List<MetricSeries> queryMetrics(String assetId, MetricQuery query) {
        Optional<Asset> assetOpt = registry.find(assetId);
        if (assetOpt.isEmpty()) {
            return List.of();
        }
        Asset asset = assetOpt.get();
        List<MetricSeries> result = new ArrayList<>();
        for (String metricId : query.metricIds()) {
            if (!MetricCatalog.exists(metricId)) {
                result.add(unsupported(assetId, metricId, query));
                continue;
            }
            List<MetricPoint> points = switch (asset.type()) {
                case HOST -> hostPoints(metricId, asset, query);
                case CONTAINER -> containerPoints(metricId, asset, query);
                case DATABASE -> databasePoints(metricId, asset, query);
                default -> List.of();
            };
            if (points.isEmpty()) {
                result.add(unsupported(assetId, metricId, query));
            } else {
                result.add(new MetricSeries(assetId, metricId, PROVIDER_ID,
                        MetricCatalog.require(metricId).unit(), MetricCatalog.require(metricId).kind(),
                        Instant.now().minusSeconds(5), Quality.VALID, SampleScope.notApplicable(), points));
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
            case "hb-host-app" -> Optional.of(AssetSeeder.APP_HOST);
            case "hb-host-db" -> Optional.of(AssetSeeder.DB_HOST);
            case "hb-mariadb" -> Optional.of(AssetSeeder.MARIADB);
            default -> Optional.empty();
        };
    }

    private List<MetricPoint> hostPoints(String metricId, Asset asset, MetricQuery query) {
        long seed = Math.abs(asset.id().hashCode() * 31L + metricId.hashCode());
        return switch (metricId) {
            case MetricCatalog.HOST_CPU_USAGE ->
                    SyntheticSeries.generate(seed, query.range().start(), query.range().end(),
                            query.stepSeconds(), 22.0, 12.0, 0);
            case MetricCatalog.HOST_MEM_AVAILABLE ->
                    SyntheticSeries.generate(seed, query.range().start(), query.range().end(),
                            query.stepSeconds(), 4.6e9, 3.0e8, 0);
            case MetricCatalog.HOST_SWAP_USED ->
                    SyntheticSeries.generate(seed, query.range().start(), query.range().end(),
                            query.stepSeconds(), 2.0e9, 1.0e7, 10);
            case MetricCatalog.HOST_DISK_READ_LATENCY ->
                    SyntheticSeries.generate(seed, query.range().start(), query.range().end(),
                            query.stepSeconds(), 6.0, 3.0, 0);
            default -> List.of();
        };
    }

    private List<MetricPoint> containerPoints(String metricId, Asset asset, MetricQuery query) {
        long seed = Math.abs(asset.id().hashCode() * 31L + metricId.hashCode());
        return switch (metricId) {
            case MetricCatalog.CONTAINER_CPU_CORES ->
                    SyntheticSeries.generate(seed, query.range().start(), query.range().end(),
                            query.stepSeconds(), 0.8, 0.5, 0);
            case MetricCatalog.CONTAINER_MEM_USED ->
                    SyntheticSeries.generate(seed, query.range().start(), query.range().end(),
                            query.stepSeconds(), 1.5e9, 1.0e8, 0);
            default -> List.of();
        };
    }

    private List<MetricPoint> databasePoints(String metricId, Asset asset, MetricQuery query) {
        long seed = Math.abs(asset.id().hashCode() * 31L + metricId.hashCode());
        return switch (metricId) {
            case MetricCatalog.DB_CONNECTIONS ->
                    SyntheticSeries.generate(seed, query.range().start(), query.range().end(),
                            query.stepSeconds(), 12.0, 4.0, 0);
            case MetricCatalog.DB_THREADS_RUNNING ->
                    SyntheticSeries.generate(seed, query.range().start(), query.range().end(),
                            query.stepSeconds(), 3.0, 2.0, 0);
            case MetricCatalog.DB_BUFFER_POOL_HIT ->
                    SyntheticSeries.generate(seed, query.range().start(), query.range().end(),
                            query.stepSeconds(), 98.1, 0.4, 0);
            case MetricCatalog.DB_LOCK_WAITS ->
                    SyntheticSeries.generate(seed, query.range().start(), query.range().end(),
                            query.stepSeconds(), 0.0, 1.0, 20);
            default -> List.of();
        };
    }

    private MetricSeries unsupported(String assetId, String metricId, MetricQuery query) {
        MetricKind kind = MetricCatalog.exists(metricId) ? MetricCatalog.require(metricId).kind() : MetricKind.GAUGE;
        String unit = MetricCatalog.exists(metricId) ? MetricCatalog.require(metricId).unit() : "unknown";
        return new MetricSeries(assetId, metricId, PROVIDER_ID, unit, kind,
                null, Quality.UNSUPPORTED, SampleScope.notApplicable(), List.of());
    }
}
