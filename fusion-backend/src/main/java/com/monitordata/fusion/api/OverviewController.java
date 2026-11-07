package com.monitordata.fusion.api;

import com.monitordata.fusion.assets.Asset;
import com.monitordata.fusion.assets.AssetRegistry;
import com.monitordata.fusion.assets.AssetType;
import com.monitordata.fusion.metrics.MetricCatalog;
import com.monitordata.fusion.metrics.MetricPoint;
import com.monitordata.fusion.metrics.MetricQuery;
import com.monitordata.fusion.metrics.MetricQueryResponse;
import com.monitordata.fusion.metrics.MetricQueryService;
import com.monitordata.fusion.metrics.MetricSeries;
import com.monitordata.fusion.metrics.Quality;
import com.monitordata.fusion.metrics.SampleScope;
import com.monitordata.fusion.platform.ApiTimeParser;
import com.monitordata.fusion.platform.RequestIdFilter;
import com.monitordata.fusion.platform.SourceState;
import com.monitordata.fusion.platform.TimeRange;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/overview：统一总览，允许部分来源失败。
 * 卡片健康度只在有有效样本时判定，缺失一律 unknown，不显示为正常。
 */
@RestController
@RequestMapping("/api/v1/overview")
public class OverviewController {

    private final AssetRegistry assets;
    private final MetricQueryService metricService;

    public OverviewController(AssetRegistry assets, MetricQueryService metricService) {
        this.assets = assets;
        this.metricService = metricService;
    }

    @GetMapping
    public OverviewResponse overview(
            @RequestParam String siteId,
            @RequestParam String environment,
            @RequestParam String start,
            @RequestParam String end) {

        Instant from = ApiTimeParser.parse(start, "start");
        Instant to = ApiTimeParser.parse(end, "end");
        TimeRange range = new TimeRange(from, to);

        // 总览只取最近 5 分钟，避免把整个区间压成单点
        Instant recentStart = to.minus(Duration.ofMinutes(5));
        if (recentStart.isBefore(from)) {
            recentStart = from;
        }
        int step = (int) Math.max(30, Duration.between(recentStart, to).getSeconds() / 30);

        List<Asset> scoped = assets.all().stream()
                .filter(asset -> asset.siteId().equals(siteId))
                .filter(asset -> asset.environment().equals(environment))
                .toList();

        Map<String, List<String>> metricsByAsset = defaultMetrics(scoped);
        List<OverviewCard> cards = new ArrayList<>();
        List<SourceState> sources = new ArrayList<>();
        boolean partial = false;

        for (Map.Entry<String, List<String>> entry : metricsByAsset.entrySet()) {
            Asset asset = assets.require(entry.getKey());
            MetricQuery query = new MetricQuery(
                    List.of(asset.id()), entry.getValue(), new TimeRange(recentStart, to), step, 1000);
            MetricQueryResponse response = metricService.query(query);
            List<OverviewSignal> signals = response.series().stream()
                    .map(OverviewController::toSignal)
                    .toList();
            cards.add(new OverviewCard(asset, healthOf(signals), signals));
            partial = partial || response.partial();
            for (SourceState state : response.sources()) {
                if (sources.stream().noneMatch(existing -> existing.providerId().equals(state.providerId()))) {
                    sources.add(state);
                }
            }
        }

        return new OverviewResponse(RequestIdFilter.currentRequestId(), range, partial,
                List.copyOf(sources), List.copyOf(cards));
    }

    /**
     * 每类资产在总览上展示的指标。刻意保持少量，详细数据走指标查询。
     */
    private static Map<String, List<String>> defaultMetrics(List<Asset> scoped) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Asset asset : scoped) {
            switch (asset.type()) {
                case HOST -> result.put(asset.id(), List.of(
                        MetricCatalog.HOST_CPU_USAGE,
                        MetricCatalog.HOST_MEM_AVAILABLE,
                        MetricCatalog.HOST_SWAP_USED));
                case DATABASE -> result.put(asset.id(), List.of(
                        MetricCatalog.DB_CONNECTIONS,
                        MetricCatalog.DB_THREADS_RUNNING,
                        MetricCatalog.DB_BUFFER_POOL_HIT));
                case CONTAINER -> result.put(asset.id(), List.of(
                        MetricCatalog.CONTAINER_CPU_CORES,
                        MetricCatalog.CONTAINER_MEM_USED));
                case SERVICE, SERVICE_INSTANCE -> result.put(asset.id(), List.of(
                        MetricCatalog.SERVICE_REQUEST_RATE,
                        MetricCatalog.SERVICE_ERROR_RATE,
                        MetricCatalog.SERVICE_P95));
            }
        }
        return result;
    }

    private static OverviewSignal toSignal(MetricSeries series) {
        MetricPoint last = series.points().isEmpty() ? null : series.points().get(series.points().size() - 1);
        Double value = last == null ? null : last.value();
        Quality quality = last == null ? Quality.NO_DATA : last.quality();
        return new OverviewSignal(series.metricId(), series.providerId(), value, series.unit(),
                last == null ? series.sourceCollectedAt() : last.time(), quality,
                series.sampleScope() == null ? SampleScope.unknown("未声明采样范围") : series.sampleScope());
    }

    /**
     * 健康度判定：没有有效样本即 unknown，绝不把缺失当作 healthy。
     */
    private static String healthOf(List<OverviewSignal> signals) {
        boolean anyValid = signals.stream().anyMatch(signal -> signal.quality() == Quality.VALID);
        if (!anyValid) {
            return "unknown";
        }
        for (OverviewSignal signal : signals) {
            if (signal.quality() != Quality.VALID || signal.value() == null) {
                continue;
            }
            double value = signal.value();
            switch (signal.metricId()) {
                case MetricCatalog.HOST_CPU_USAGE -> {
                    if (value >= 90) {
                        return "critical";
                    }
                    if (value >= 75) {
                        return "warning";
                    }
                }
                case MetricCatalog.SERVICE_ERROR_RATE -> {
                    if (value >= 0.05) {
                        return "critical";
                    }
                    if (value >= 0.01) {
                        return "warning";
                    }
                }
                case MetricCatalog.SERVICE_P95 -> {
                    if (value >= 1000) {
                        return "critical";
                    }
                    if (value >= 500) {
                        return "warning";
                    }
                }
                default -> {
                    // 其余指标第一版不参与健康度判定，避免无依据的等级
                }
            }
        }
        return "healthy";
    }

    public record OverviewSignal(
            String metricId,
            String providerId,
            Double value,
            String unit,
            Instant sourceCollectedAt,
            Quality quality,
            SampleScope sampleScope) {
    }

    public record OverviewCard(Asset asset, String health, List<OverviewSignal> signals) {
    }

    public record OverviewResponse(
            String requestId,
            TimeRange range,
            boolean partial,
            List<SourceState> sources,
            List<OverviewCard> cards) {
    }
}
